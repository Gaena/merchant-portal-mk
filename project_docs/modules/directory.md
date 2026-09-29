# Модуль Управления Справочниками (Directory Service)

> Контракты API сервиса `directory`. Сверено с кодом 29.09.2026. Схема таблиц —
> `../guides/application_description.md` §4, роли и матрица доступа — `../../AGENTS.md` §6, переменные
> окружения — `../guides/deployment_guide.md` §20.1.

## 1. Архитектура и Структура
-   **Стек**: Spring Boot, модуль `directory` Gradle-монорепозитория, порт `8082`.
-   **База данных**: общая PostgreSQL. Схему `companies` и `terminals` ведёт вместе с другими сервисами
    (владение таблицами — `../../AGENTS.md` §5 п. 2); журнал `audit_logs` пишут все сервисы, читает только
    этот (§3.3). Нативными запросами сервис меняет статусы в `payment_links` (модуль `pbl`) и читает слепки
    модуля `ecom` — `provider_terminals` и `provider_logins`.
-   **Связь с другими модулями**:
    -   `directory` предоставляет эндпоинты компаний, терминалов и журнала аудита.
    -   `auth` читает `companies`, проверяя компанию пользователя.
    -   `pbl` читает `terminals` и креды компании к провайдеру из `companies` (Р-93) для платежей через MilliKart.
    -   `ecom` снимает слепки `provider_terminals` и `provider_logins`. По первому `directory` сверяет статусы
        терминалов (§3.2, «Синхронизация статусов с провайдером»), по второму проверяет логин компании (§3.1)
        и отбирает мерчантов для терминалов компании (§3.2).
-   **Postman**: `../../directory/Directory.postman_collection.json` (§4).

---

## 2. База данных

### 2.1. Таблицы `companies` и `terminals`
Схема обеих таблиц — включая креды компании к провайдеру (Р-93) и номера терминала (Р-81, Р-96) —
`../guides/application_description.md` §4.1, миграции — §4.3.

---

## 3. API Контракты (Эндпоинты `directory`)

Все эндпоинты — только с access-токеном от `auth` (`Authorization: Bearer …`, `auth.md` §4.1); без него или с
негодным — `401 Missing or invalid Authorization header` / `Invalid or expired JWT token`. Ответ об ошибке —
`ErrorResponse { timestamp, status, error, message, path }`. Ненайденная компания или терминал — `400` с
текстом, а не `404`. Общие отказы:

| Код | `message` | Когда |
|:---|:---|:---|
| `400` | `Invalid request payload format or parameter value` | битый JSON или значение не того типа в теле (например, `status` терминала не `ACTIVE`/`BLOCKED`) |
| `400` | `Parameter '<имя>' has an invalid value` | нечисловой `id` терминала в пути, нечисловой `page` или `size`; значение в ответ не попадает |
| `415` | `Content-Type … is not supported by this endpoint; send application/json` | тело не в JSON |
| `405` | `Method DELETE is not supported for this endpoint; use …` и заголовок `Allow` | `DELETE /api/v1/terminals/{id}`: терминалы не удаляются (§3.2) |

**Постраничные списки** (`GET /companies`, `GET /terminals`, `GET /audit-logs`, P2-1): `page` (по умолчанию `0`),
`size` (по умолчанию `20`, потолок `200`); значения приводятся, а не отклоняются — `?page=-1&size=0` отвечает
`200`. Ответ — `PagedResponse` `{"content": [...], "totalElements", "totalPages", "size", "number"}`. `search`
(P3-1) — подстрока без учёта регистра, `%` и `_` ищутся буквально, пустая строка — то же, что отсутствие
параметра, длиннее 100 символов — обрезается; `totalElements` считает отфильтрованное. Сортировка
заканчивается уникальной колонкой, иначе запись попадает на две страницы или ни на одну.

### 3.1. Управление Компаниями (Companies CRUD)

`CompanyResponse` — `id`, `name`, `status`, `providerLogin`, `createdBy`, `createdAt`, `updatedBy`, `updatedAt`.
**Пароля в ответах нет никогда**; `providerLogin` заполнен только для `SYSTEM_ADMIN`, остальным — `null`.
`createdAt`/`updatedAt` старых записей без дат — `null`.

-   `POST /api/v1/companies` — Создать компанию.  
    *Доступ*: Только `SYSTEM_ADMIN`.  
    *Запрос* (все поля обязательны): `{"id": "comp-01", "name": "MilliKart LLC", "providerLogin":
    "MultiMerchantSys/merchant@company.com", "providerPassword": "…"}`. Логин — целиком, с префиксом
    владельца, сохраняется как пришёл; пароль ложится шифротекстом (Р-93).  
    **Логин — только активный мультимерчант** (Р-94): `MultiMerchantSys/<login>`, и в слепке логинов
    `provider_logins` (`ecom.md` §3.3) он `Active` и связан хотя бы с одним мерчантом связью `Active`.
    Проверяется только при сохранении — заведении или смене логина; уже сохранённые логины слепок не трогает.  
    *Ответ `201`*: `CompanyResponse`, `status: ACTIVE`.  
    *Отказы*:

    | Код | `message` | Когда | Журнал |
    |:---|:---|:---|:---|
    | `400` | `Company ID is required`, `Company name is required`, `Provider login is required`, `Provider password is required` | не прошла валидация | — |
    | `403` | `Access denied: Only SYSTEM_ADMIN can create companies` | не администратор | `COMPANY` / `CREATE` / `DENIED` |
    | `400` | `Company with ID '<id>' already exists` | `id` занят, в том числе удалённой компанией | — |
    | `400` | `Provider login must be a multimerchant login: MultiMerchantSys/<login>` | логин без префикса `MultiMerchantSys/` | — |
    | `400` | `The provider login list has not been synchronised yet; refresh the provider directory and try again` | слепка нет (`ecom` не развёрнут) или он пуст | — |
    | `400` | `Provider login <login> is not in the synchronised list of multimerchant logins` | логина нет в слепке | — |
    | `400` | `Provider login <login> is not active at the provider` | логин не `Active` | — |
    | `400` | `Provider login <login> has no active merchants at the provider` | нет активной связи с мерчантом | — |
    | `409` | `Provider login is already used by another company` | логин у другой компании; удалённые компании его не освобождают | — |

    *Журнал*: `COMPANY` / `CREATE` `Created company: <name>, provider login <login>`.
-   `GET /api/v1/companies` — Получить список компаний (постранично).  
    *Доступ*: `SYSTEM_ADMIN` и `AUDITOR`; остальным — `403 Access denied: Only SYSTEM_ADMIN or AUDITOR can view
    all companies` и запись `COMPANY` / `LIST` / `DENIED`.  
    *Параметры*: `page`, `size`, `search` — по `name` и `id`.  
    *Порядок*: по `name`, при совпадении — по `id`.  
    *Ответ*: `PagedResponse` из `CompanyResponse`. Компании со статусом `DELETED` не входят ни в `content`, ни
    в `totalElements` — фильтр выполняется в запросе.
-   `GET /api/v1/companies/provider-logins` — свободные логины мультимерчантов для формы компании (Р-95).  
    *Доступ*: только `SYSTEM_ADMIN`; остальным — `403 Access denied` с записью `COMPANY` / `LIST` / `DENIED`.  
    *Ответ*: `[{"login": "MultiMerchantSys/bazarstore@company.com", "merchants": ["BazarStore Genclik",
    "BazarStore PortBaku"]}]`, по логину. В списке — логины из слепка `provider_logins`, которые пройдут
    проверку при сохранении (`Active` и хотя бы одна связь `Active` с мерчантом), и которых нет ни у одной
    компании, включая удалённые. `merchants` — названия мерчантов активных связей. Слепка нет или пуст —
    пустой список. Список — подсказка форме: `POST` и `PATCH` проверяют логин сами.
-   `GET /api/v1/companies/{id}` — Детали компании.  
    *Доступ*: `SYSTEM_ADMIN` и `AUDITOR` — любой; остальные роли — только своей компании.  
    *Ответ `200`*: `CompanyResponse`.  
    *Отказы*: `400 Company not found` — нет такой или она удалена; `403 Access denied` — чужая компания, запись
    `COMPANY` / `READ` / `DENIED`.
-   `PATCH /api/v1/companies/{id}` — Редактировать компанию.  
    *Доступ*: Только `SYSTEM_ADMIN`.  
    *Запрос* (все поля необязательны, пустое — «не менять»): `{"name", "status", "providerLogin",
    "providerPassword"}`. Новый пароль ложится шифротекстом; прочитать прежний нельзя. Новый логин
    проверяется по слепку так же, как при создании; тот же логин проверку не запускает.  
    *Статус*: только `ACTIVE` или `INACTIVE`, сравнение точное. `DELETED` ставит только `DELETE`. `INACTIVE`
    платежей не останавливает (`../../AGENTS.md` §10): их останавливает блокировка терминалов.  
    *Ответ `200`*: `CompanyResponse`.  
    *Отказы*:

    | Код | `message` | Когда | Журнал |
    |:---|:---|:---|:---|
    | `403` | `Access denied: Only SYSTEM_ADMIN can update companies` | не администратор | `COMPANY` / `UPDATE` / `DENIED` |
    | `400` | `Company not found` | нет такой или она удалена | — |
    | `400` | `Company status must be ACTIVE or INACTIVE` | иной `status` | — |
    | `400` | те же, что у `POST`, для нового логина | новый логин не проходит проверку по слепку | — |
    | `409` | `Provider login is already used by another company` | новый логин занят | — |

    *Журнал*: `COMPANY` / `UPDATE` с перечнем изменений — `Name changed from 'X' to 'Y'.`, `Status changed
    from …`, `Provider login changed from 'X' to 'Y'.`, `Provider password changed.` (без значения пароля);
    смена статуса пишет ещё `BLOCK`/`UNBLOCK`. В перечень попадают только поля, значение которых изменилось;
    PATCH без изменений отвечает `200` и не пишет ничего, `updatedAt` не меняется (Р-108).
-   `DELETE /api/v1/companies/{id}` — мягкое удаление: `status = DELETED`.  
    *Доступ*: Только `SYSTEM_ADMIN`.  
    *Ответ `204`*. Терминалы и пользователи компании не трогаются, её логин к провайдеру остаётся занятым.
    Повторный `DELETE` удалённой компании проходит и пишет запись ещё раз.  
    *Отказы*: `403 Access denied: Only SYSTEM_ADMIN can delete companies` с записью `COMPANY` / `DELETE` /
    `DENIED`; `400 Company not found` — такой компании нет.  
    *Журнал*: `COMPANY` / `DELETE` `Soft deleted company`.

### 3.2. Управление Терминалами (Terminals CRUD)

Заводит терминал только `SYSTEM_ADMIN` (Р-93). Правка разрешена ролям из
`TerminalService.TERMINAL_WRITE_ROLES` = `SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`; руководитель и
менеджер — только терминалы своей компании. Роль проверяется **до** `companyId`, поэтому `COMPANY_EMPLOYEE`,
`AUDITOR` и любая нераспознанная роль получают `403` даже на терминалы своей компании (P1-15). Чтение шире:
свои терминалы видят все компанейские роли, включая `COMPANY_EMPLOYEE`, а `SYSTEM_ADMIN` и `AUDITOR` — все.

`TerminalResponse` — `id`, `name`, `login`, `terminalRid`, `companyId`, `status`, `createdBy`, `createdAt`,
`updatedBy`, `updatedAt`:

-   `id` — внутренний номер терминала в портале из последовательности `terminals_id_seq` (Р-81), а не номер у
    провайдера;
-   `login` — логин терминала у провайдера в виде `TerminalSys/<login>`, из справочника. К провайдеру с ним не
    ходят (Р-93), выписка его не читает (Р-97); подписывает терминал без номера. Меняет его только сверка
    (ниже), находя терминал по `merchant_rid`;
-   `terminalRid` — номер терминала у провайдера (Р-96): с ним `pbl` создаёт заказ, им терминал подписан на
    экранах. Пусто у терминалов, заведённых до Р-96 без справочника: платежи по ним — `400`;
-   `status` — `ACTIVE` или `BLOCKED`; `createdAt`/`updatedAt` старых записей без дат — `null`.

Мерчант провайдера (`merchant_rid`, Р-69) в ответ не входит.

-   `POST /api/v1/terminals` — Создать терминал.  
    *Доступ*: только `SYSTEM_ADMIN`.  
    *Запрос*: `{"companyId": "comp-01", "merchantRid": "E1120020"}` — оба поля обязательны. Терминал
    выбирается из справочника провайдера `provider_terminals` (`ecom.md` §3): `name`, `login` и `terminalRid`
    берутся из строки с этим `merchantRid`, пароля у терминала нет — к провайдеру ходят с кредами компании.
    **Мерчант должен быть связан с логином компании** (Р-96): активная связь в слепке `provider_logins`
    (`ecom.md` §3.3); форме список таких терминалов отдаёт `GET /api/v1/terminals/provider-terminals`.
    Номер терминала выдаёт база; присланный `id` игнорируется.  
    *Ответ `201`*: `TerminalResponse`, `status: ACTIVE`.  
    *Отказы*:

    | Код | `message` | Когда | Журнал |
    |:---|:---|:---|:---|
    | `400` | `Company ID is required`, `Provider terminal (merchantRid) is required` | не прошла валидация | — |
    | `403` | `Access denied: AUDITOR is read-only` / `Access denied` | аудитор / прочие не-администраторы | `TERMINAL` / `CREATE` / `DENIED`, `entityId` = `NEW` |
    | `400` | `Company with ID '<id>' not found` | компании нет или она удалена | — |
    | `400` | `Provider terminal <rid> is already linked to terminal <id>` | мерчант уже заведён: один терминал провайдера — одна компания | — |
    | `400` | `Provider terminal <rid> is not in the synchronised list` | мерчанта нет в справочнике | — |
    | `400` | `Provider terminal <rid> has no name, login or terminal number in the synchronised list` | в строке справочника нет названия, логина или номера терминала | — |
    | `400` | `Provider terminal <rid> is not active at the provider` | строка справочника неактивна | — |
    | `400` | `Provider terminal <rid> does not belong to the multimerchant login of company <id>` | мерчант не связан с логином компании | — |

    *Журнал*: `TERMINAL` / `CREATE` `Created terminal: <name> for company <id>`.
-   `GET /api/v1/terminals/provider-terminals?companyId=…` — терминалы провайдера для формы заведения (Р-96).  
    *Доступ*: только `SYSTEM_ADMIN`; остальным — `403 Access denied` с записью `TERMINAL` / `LIST` / `DENIED`.  
    *Ответ*: `[{"rid": "223456789054323", "title": "BazarStore PortBaku", "login": "BS00003",
    "terminalRid": "BS00003"}]`, по названию; `rid` — `merchantRid` для `POST`, `login` — как в справочнике,
    без префикса. Только активные строки справочника с номером терминала, чей мерчант связан активной связью с
    логином компании, и которые ещё не заведены ни в одной компании. У компании без логина мультимерчанта и
    при пустом справочнике — пустой список.  
    *Отказы*: `400 companyId is required`; `400 Company with ID '<id>' not found` — компании нет или она удалена.
-   `GET /api/v1/terminals` — Список терминалов (постранично).  
    *Доступ*: `SYSTEM_ADMIN` и `AUDITOR` — все; `COMPANY_HEAD`/`COMPANY_MANAGER`/`COMPANY_EMPLOYEE` — только
    своей компании. Отказы с записью `TERMINAL` / `LIST` / `DENIED`: роль компании без `companyId` — `403 Access
    denied: User not assigned to a company`; нераспознанная роль — `403 Access denied`.  
    *Параметры*: `page`, `size`, `search` — по `name`, `login`, `id` (как тексту), `companyId` и **названию
    компании**. Поиск работает **внутри** ролевого скоупа: компания видит только свои терминалы, что бы ни искала.  
    *Порядок*: по `name`, при совпадении — по `id`.  
    *Ответ*: `PagedResponse` из `TerminalResponse`, заблокированные терминалы — тоже.
-   `GET /api/v1/terminals/options` — **лёгкий список терминалов** для выпадающих списков и подписей (Р-45).  
    *Доступ и отказы*: те же, что у `GET /api/v1/terminals`.  
    *Ответ*: голый массив без пагинации, по `name`, затем `id`:
    ```json
    [ {"id": 1001, "name": "Main Terminal", "login": "TerminalSys/BS00003", "terminalRid": "BS00003", "status": "ACTIVE"} ]
    ```
    Отдаёт и `BLOCKED`: подпись старых платежей по заблокированному терминалу должна остаться, фильтрует потребитель (Р-45).
-   `GET /api/v1/terminals/{id}` — Детали терминала.  
    *Доступ*: `SYSTEM_ADMIN`, `AUDITOR`, любая роль из компании терминала (в т.ч. `COMPANY_EMPLOYEE`).  
    *Ответ `200`*: `TerminalResponse`.  
    *Отказы*: `400 Terminal not found`; `403 Access denied` — чужой терминал, запись `TERMINAL` / `READ` / `DENIED`.
-   `PATCH /api/v1/terminals/{id}` — Редактировать терминал **и его статус**.  
    *Доступ*: `SYSTEM_ADMIN`, `COMPANY_HEAD`/`COMPANY_MANAGER` (своей компании).  
    *Запрос* (все поля необязательны, пустое — «не менять»): `{"name": "...", "companyId": "...", "status":
    "ACTIVE" | "BLOCKED"}`. Логин не правится — его меняет только сверка со справочником; `login` и
    `password` в теле игнорируются.  
    *Компания*: переносит терминал в другую компанию **только `SYSTEM_ADMIN`** — для руководителя и менеджера
    чужая целевая компания — отказ в доступе. Терминал переходит только в компанию, с логином мультимерчанта
    которой его мерчант (`merchant_rid`) активно связан в `provider_logins`, — то же правило, что при заведении
    (Р-96, Р-97); терминал без `merchant_rid` не переносится вовсе. Та же компания в запросе — не перенос.  
    *Статус*: блокировка и разблокировка — только этим полем (Р-37). Тот же статус — не изменение: ссылки и
    `status_source` не трогаются. Фактическая смена ставит `status_source = MANUAL`: ручную блокировку сверка
    не снимает. `BLOCKED` переводит `ACTIVE`-ссылки терминала в `SUSPENDED` (Р-39); `ACTIVE` возвращает
    `SUSPENDED` в `ACTIVE`, а тем, чей срок истёк за время блокировки, ставит `EXPIRED` (Р-40) — в той же
    транзакции. Блокировка запрещает только новые платежи и в MilliKart не уходит — `../../AGENTS.md` §10
    («Терминалы»).  
    *Ответ `200`*: `TerminalResponse`.  
    *Отказы*:

    | Код | `message` | Когда | Журнал |
    |:---|:---|:---|:---|
    | `400` | `Terminal not found` | нет такого терминала | — |
    | `403` | `Access denied: AUDITOR is read-only` / `Access denied` | аудитор / роль без права записи или чужой терминал | `TERMINAL` / `UPDATE` / `DENIED` |
    | `403` | `Access denied` | не администратор переносит терминал в другую компанию | `TERMINAL` / `UPDATE` / `DENIED` |
    | `400` | `Company with ID '<id>' not found` | целевой компании нет или она удалена | — |
    | `400` | `Terminal <id> cannot be moved to company <id>: its provider merchant is not linked to the multimerchant login of that company` | мерчант не связан с логином целевой компании или у терминала нет `merchant_rid` | — |
    | `403` | `Terminal <id> is out of service at the provider and will be unblocked automatically once the provider brings it back` | ручное включение терминала, выключенного сверкой (`status_source = PROVIDER`, Р-66) | `TERMINAL` / `UNBLOCK` / `DENIED` |

    *Журнал*: `TERMINAL` / `UPDATE` с перечнем изменений (`Name changed from 'X' to 'Y'.`, `CompanyId changed
    from 'X' to 'Y'.`, смена статуса); смена статуса — ещё `BLOCK` `Blocked terminal <id>, suspended N links`
    или `UNBLOCK` `Unblocked terminal <id>, resumed N links, expired M links`. В перечень попадают только
    изменившиеся поля; PATCH без изменений отвечает `200` и не пишет ничего, `updatedAt` не меняется (Р-108).
-   `DELETE /api/v1/terminals/{id}` — нет: `405` (§3). Терминалы не удаляются, а блокируются (Р-37): на них
    ссылаются платёжные ссылки.

#### Синхронизация статусов с провайдером (Р-66)

Сервис `ecom` каждые 15 минут обновляет справочник терминалов провайдера (`provider_terminals`, `ecom.md` §3),
а `directory` по расписанию `directory.terminal-reconciliation.cron` (`DIRECTORY_TERMINAL_RECONCILIATION_CRON`,
по умолчанию `0 */15 * * * *`) сверяет с ним `terminals` по `merchant_rid`. Выключается
`directory.terminal-reconciliation.enabled=false` (`DIRECTORY_TERMINAL_RECONCILIATION_ENABLED`, по умолчанию
`true`) — там, где `ecom` не развёрнут.

| Наш терминал | У провайдера | Что происходит |
|:---|:---|:---|
| `ACTIVE` | неактивен | `BLOCKED`, `status_source = PROVIDER`, активные ссылки → `SUSPENDED` |
| `BLOCKED`, `status_source = PROVIDER` | снова активен | `ACTIVE`, ссылки возвращаются, просроченные → `EXPIRED` |
| `BLOCKED`, `status_source = MANUAL` | любое | статус не трогается — это решение клиента |
| без `merchant_rid` или не найден в справочнике | — | не трогается: отсутствие строки — «не знаем» |

Пустой справочник (синхронизация ещё не проходила или `ecom` не установлен) не применяется вовсе.
Строку справочника неактивной делает `ecom`: терминал выключен у провайдера или три обновления подряд не
приходил. Название, логин и номер терминала (`terminal_rid`, Р-96) сверка переносит к нам у любого терминала,
найденного в справочнике по `merchant_rid`, и пишет `TERMINAL` / `UPDATE` от `system`. Смены статуса пишутся
как `TERMINAL` / `BLOCK` или `UNBLOCK` с исполнителем `system`.

### 3.3. Журнал аудита (Audit Logs)

-   `GET /api/v1/audit-logs` — журнал всех четырёх сервисов; читает его только этот эндпоинт.  
    *Доступ*: `SYSTEM_ADMIN` и `AUDITOR` — записи всех компаний; `COMPANY_HEAD`/`COMPANY_MANAGER` —
    только записи с `companyId` своей компании. Отказы с записью `AUDIT_LOG` / `LIST` / `DENIED`: руководитель
    или менеджер без компании — `403 Access denied: User not assigned to a company`; остальные роли —
    `403 Access denied`.  
    *Параметры* (все необязательные и независимые): `entityType` (регистр не важен), `entityId` (без учёта
    регистра), `search` — подстрока по `performedBy`, `action`, `entityId` и `details`; `outcome` (`SUCCESS` /
    `DENIED` / `UNRESOLVED`, неизвестное значение — как отсутствие фильтра); `from` и `to` — граница по
    `createdAt`, ISO-instant (`2026-08-24T10:15:30Z`) или дата (`2026-08-24` — весь день по UTC), неразбираемое
    значение — как отсутствие фильтра; `page`, `size`.  
    *Порядок*: от новых к старым — `createdAt DESC`, при равенстве `id DESC`.  
    *Ответ*: `PagedResponse`:
    ```json
    {
      "content": [
        {
          "id": "…", "entityType": "COMPANY", "entityId": "comp-01", "action": "CREATE",
          "performedBy": "admin@millikart.az", "companyId": "comp-01",
          "details": "Created company: MilliKart LLC, provider login MultiMerchantSys/merchant@company.com",
          "clientIp": "203.0.113.9", "outcome": "SUCCESS", "createdAt": "…"
        }
      ],
      "totalElements": 1, "totalPages": 1, "size": 20, "number": 0
    }
    ```
    `outcome`: `SUCCESS` — действие выполнено; `DENIED` — отказ в доступе; `UNRESOLVED` — денежная операция,
    исход которой эквайер не подтвердил. `clientIp` — адрес клиента через доверенный прокси, `null` у действий
    вне HTTP-запроса. Журнал только пополняется. Словарь `entityType`/`action`, что лежит в `entityId`,
    `companyId` и `details` каждого события и когда запись ложится — `../guides/technical_handover.md` §4.4.

---

## 4. Коллекция Postman
- **[Directory.postman_collection.json](../../directory/Directory.postman_collection.json)** — все эндпоинты
  сервиса. Access-токен берётся запросом `Login` из коллекции `auth` (`auth.md` §5) и кладётся в переменную
  коллекции `authToken`. `Create Terminal` сохраняет номер созданного терминала в `terminalId`.
