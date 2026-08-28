# Код-ревью проекта Merchant Portal (MP)

**Дата:** 14 августа 2026
**Ревизия:** `3f890de` (ветка `main`)
**Объём:** backend ~5 000 строк Java (модули `common`, `auth`, `directory`, `pbl`), frontend ~18 700 строк TS/TSX

---

## Резюме

Проект — Gradle-монорепо на Java 21 / Spring Boot 3.2.5 с четырьмя backend-модулями и React-SPA. Архитектурный каркас выбран разумно, доменная модель платежей продумана (SMS/DMS, single/multiple links, частичные возвраты, optimistic locking, circuit breaker), есть Liquibase-миграции и интеграционные тесты с проверками RBAC.

Но между заявленным в `technical_handover.md` («готов к передаче Заказчику», «прошёл цикл отладки, сборки и тестирования») и фактическим состоянием кода — большой разрыв. Найдено **9 блокеров**, из которых четыре — прямые нарушения изоляции данных между мерчантами и утечка PII/платёжных данных, а два — риск потери денег на возвратах.

Отдельно: **JWT-ключ подписи лежит в репозитории в открытом виде** (и в истории git), **дефолтный админ `admin@millikart.az` / `admin123` заводится Liquibase-миграцией прямо в проде**, а Spring Security сконфигурирован как `anyRequest().permitAll()`.

Для платёжной системы, работающей с картами, в текущем виде выпускать нельзя.

| Приоритет | Кол-во | Смысл |
|:---|:---|:---|
| 🔴 P0 — блокеры | 9 | Утечка данных, потеря денег, компрометация. Правим до любого релиза |
| 🟠 P1 — важно | 15 | Ломает бизнес-сценарии или прод-развёртывание |
| 🟡 P2 — средне | 12 | Техдолг, производительность, эксплуатация |
| ⚪ P3 — мелочи | 10 | Гигиена репозитория и кода |

---

## 1. Что это за система

Пять компонентов:

| Модуль | Порт | Назначение | Строк |
|:---|:---|:---|:---|
| `common` | — | JWT, Spring Security, GlobalExceptionHandler, Caffeine-кэш, валидатор паролей | ~600 |
| `auth` | 8081 | Логин, пользователи, RBAC | ~700 |
| `directory` | 8082 | Компании, терминалы, журнал аудита | ~1 100 |
| `pbl` | 8080 | Pay-By-Link, транзакции, интеграция с TXPG | ~2 600 |
| `frontend` | 3000 | React 18 + TS + Vite + MUI | ~18 700 |

Роли: `SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR`.
Все три сервиса ходят в **одну** базу PostgreSQL и делят таблицы `companies` / `terminals` (это зафиксировано в `problems.md` как осознанное допущение).

---

## 2. 🔴 Блокеры (P0)

### P0-1. `GET /api/v1/transactions` возвращает транзакции ВСЕХ компаний

> ✅ **Исправлено 15.08.2026.** `listTransactions` проверяет роль по `READ_ROLES` и фильтрует выборку
> по терминалам компании (`TransactionRepository.findByLink_TerminalIdIn`); `SYSTEM_ADMIN` и `AUDITOR`
> читают глобально (`findAllBy`). Заодно закрыт P2-4 (N+1 через `@EntityGraph`) и выровнена семантика
> `AUDITOR` между `pbl` и `directory` (решение Р-1 в `fix_plan.md`). Добавлено 7 интеграционных тестов
> `listTransactions_*`. Текст находки ниже оставлен как есть — это снимок ревью от 14.08.2026.

`pbl/src/main/java/az/millikart/pbl/service/PaymentLinkService.java:425-439`

```java
@Transactional(readOnly = true)
public PagedResponse<TransactionResponse> listTransactions(Pageable pageable, UserPrincipal principal) {
    Page<Transaction> page = transactionRepository.findAll(pageable);   // <-- principal не используется
    ...
}
```

Параметр `principal` принимается и **нигде не проверяется**. Любой аутентифицированный пользователь — включая `COMPANY_EMPLOYEE` чужой компании — получает все транзакции системы. В `TransactionResponse` отдаются: имя, e-mail и телефон плательщика, маскированный номер карты, RRN, approval code, IP-адрес клиента, User-Agent, `providerOrderId`.

Это главный экран портала (`frontend/src/app/App.tsx:116` дёргает именно его). То есть утечка происходит при обычном использовании, а не в результате атаки.

**Чинить:** отфильтровать по `companyId` через `terminalRepository.findAllByCompanyId(...)`, как это уже сделано в `list()` для payment-links (строки 204-228). Добавить интеграционный тест на изоляцию — сейчас его нет.

---

### P0-2. `GET /api/v1/transactions/{providerOrderId}/status` доступен без аутентификации

> ✅ **Исправлено 15.08.2026.** Путь убран из whitelist `JwtAuthFilter`; эндпоинт требует JWT и роль
> из `READ_ROLES` с проверкой компании терминала (`checkAndStatusUpdate(identifier, principal)`).
> Страница чека больше не опрашивает API: `redirect.html` рендерится Thymeleaf'ом на сервере,
> без JS, за один заход к провайдеру (решения Р-3 и Р-4 в `fix_plan.md`). Транзакция ищется по
> `merchantRid` из пути (`refreshByMerchantRid`), query-параметры провайдера `ID`/`PASSWORD`/`STATUS`
> не объявлены в контроллере — это заодно закрывает P1-4. На публичную страницу уходит узкий
> `PaymentReceiptView` вместо `TransactionResponse`: без IP, User-Agent, `providerOrderId`, карты,
> RRN и approval code. Добавлено 9 интеграционных тестов (`checkStatus_*`, `redirectPage_*`).
> Текст находки ниже оставлен как есть — это снимок ревью от 14.08.2026.

`common/src/main/java/az/millikart/common/security/JwtAuthFilter.java:131-133`

```java
if (path.startsWith("/api/v1/transactions/") && path.endsWith("/status")) {
    return false;   // публичный эндпоинт
}
```

`pbl/.../controller/TransactionController.java:44-47` — метод не принимает `principal` и не проверяет ничего.

Последствия:
1. **Утечка PII и платёжных данных без авторизации.** `providerOrderId` — числовой идентификатор от шлюза, перебирается тривиально. Ответ — тот же полный `TransactionResponse` с картой, RRN, IP.
2. **Изменение состояния.** Метод называется `checkAndStatusUpdate` — он ходит в TXPG и пишет в БД. Неаутентифицированный запрос порождает исходящий вызов к эквайеру → усилитель DoS и способ «прогреть» произвольные заказы.

**Чинить:** убрать из whitelist. Если публичный статус нужен клиенту-плательщику — сделать отдельный минимальный DTO (только `status`), доступ по неугадываемому `merchantRid` (UUID), с rate limit и без побочных эффектов.

---

### P0-3. Кэш обходит проверку прав — межтенантная утечка терминалов и компаний

`directory/.../service/TerminalService.java:104-115`

```java
@Cacheable(value = "terminals", key = "#id")
public TerminalResponse getTerminal(Integer id, UserPrincipal principal) {
    ...
    validateReadAccessToCompany(terminal.getCompanyId(), actorRole, actorCompanyId);  // внутри кэшируемого метода
    return mapToResponse(terminal);
}
```

Ключ кэша — только `#id`, а проверка доступа выполняется **внутри** тела метода. При попадании в кэш тело не выполняется вообще.

Сценарий: админ компании А открывает терминал №5 → результат лёг в кэш. В течение следующих 15 минут (`CacheConfig.java:21`) сотрудник компании Б запрашивает `/api/v1/terminals/5` → **получает данные чужого терминала** без единой проверки.

Ровно та же ошибка в `CompanyService.getCompany` (`CompanyService.java:86-99`).

**Чинить:** вынести кэширование на уровень репозитория/приватного метода без проверок, а `validateAccess` оставить в некэшируемом публичном методе. Либо включить в ключ `companyId` актора.

---

### P0-4. `GET /api/v1/payment-links/{id}/transactions` проверяет несуществующие роли

`pbl/.../service/PaymentLinkService.java:441-448`

```java
validateAccess(link.getTerminalId(), principal.getRole(), principal.getCompanyId(),
        List.of("SYSTEM_ADMIN", "MERCHANT_ADMIN", "MERCHANT_USER"));
```

Ролей `MERCHANT_ADMIN` и `MERCHANT_USER` в системе не существует — везде используются `COMPANY_HEAD` / `COMPANY_MANAGER` / `COMPANY_EMPLOYEE` / `AUDITOR`. Эндпоинт возвращает 403 всем, кроме `SYSTEM_ADMIN`. Его вызывает `PayByLinkDetailPage` — экран деталей ссылки не работает ни у одного мерчанта.

Признак того, что список ролей нигде не централизован: это **строковые литералы**, разбросанные по семи файлам. Опечатка не ловится ни компилятором, ни тестами.

**Чинить:** `enum Role` в `common`, все проверки — через него.

> ✅ **Исправлено 16.08.2026.** Введён `common/.../security/Role.java` (пять значений + `fromValue`,
> который никогда не бросает и сравнивает строго по точному совпадению). Все проверки ролей в
> `UserService`, `JwtAuthFilter`, `CompanyService`, `TerminalService`, `AuditLogService` и
> `PaymentLinkService` переведены на enum; наборы ролей — `EnumSet` (`READ_ROLES`,
> `LINK_WRITE_ROLES`, `REFUND_ROLES`). `getTransactionsByLinkId` проверяется по `READ_ROLES`,
> как и `get(UUID, principal)` для самой ссылки, и принимает `principal` целиком, поэтому
> `principal == null` больше не даёт NPE. Строковых литералов ролей в main-коде backend не
> осталось. Покрыто `RoleTest` (7 методов) и четырьмя тестами `getLinkTransactions_*`.

---

### P0-5. Секрет подписи JWT лежит в репозитории (и в истории git)

`auth/src/main/resources/application.yaml:26`, `directory/.../application.yaml:25`, плюс дефолт в коде `common/.../JwtProvider.java:22`:

```yaml
pbl:
  security:
    jwt:
      secret: dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBob3NwaGF0ZS1wYmw=
```

- Ни в одном из трёх сервисов **нет подстановки из переменной окружения** (`${JWT_SECRET:...}`), в отличие от `pbl.security.api-token`, где она есть.
- Тот же ключ захардкожен как значение по умолчанию в `JwtProvider`, то есть даже удаление из yaml не спасает.
- `git log -S` показывает, что ключ попал в историю в коммите `f6a7a7d`. Коммит `8b76975 «Secret removed»` его **не удалил** — он и сейчас в HEAD.

Кто угодно с доступом к репозиторию подписывает себе токен с `role: SYSTEM_ADMIN` и произвольным `companyId` — и получает полный доступ ко всем трём сервисам.

**Чинить:** новый ключ, только через env/Vault; ротация; переписывание истории git (`git filter-repo`) или, если репозиторий приватный и уже утёк — считать ключ скомпрометированным навсегда.

---

### P0-6. Дефолтный админ `admin@millikart.az` / `admin123` создаётся в проде миграцией

`auth/src/main/resources/db/changelog/changes/002-user-directory-schema.xml`

```xml
<insert tableName="users">
    <column name="username" value="admin@millikart.az"/>
    <!-- BCrypt hash of "admin123" -->
    <column name="password_hash" value="$2a$10$C641UbM6EBi7lBaebQ4eo./gv2YJypc66lsKJzB2uM7q/Qoj1bSVO"/>
    <column name="role" value="SYSTEM_ADMIN"/>
```

Пароль указан в комментарии рядом с хешем. Это прод-changelog, не тестовый. Форсированной смены пароля при первом входе нет.

Отдельная ирония: `admin123` нарушает собственную парольную политику проекта — `PasswordConstraintValidator` требует 12 символов, верхний/нижний регистр, цифру и спецсимвол со ссылкой на PCI-DSS v4.0. Политика применяется только к паролям, заводимым через API.

**Чинить:** убрать сид из прод-changelog; заводить первого админа отдельной процедурой с обязательной сменой пароля.

---

### P0-7. Возврат помечается успешным без ретраев-защиты, ретраи не идемпотентны

`pbl/.../provider/TxpgAcquiringClient.java:150-154`

```java
@CircuitBreaker(name = "acquiring")
@Retry(name = "acquiring")          // maxAttempts: 3, waitDuration: 500ms
public Map<String, Object> refund(...)
```

`refund` и `completeDms` — **не идемпотентные** POST-операции к эквайеру, обёрнутые в автоматический retry. При таймауте чтения (10 с, `RestTemplateConfig.java:26`) запрос мог уже пройти на стороне TXPG. Retry выполнит его ещё раз — и ещё раз. Приложение запишет **один** возврат, эквайер проведёт **до трёх**.

Идемпотентного ключа нет ни в `RefundRequest`, ни в теле запроса к провайдеру.

**Чинить:** снять `@Retry` с `refund`/`completeDms` (оставить только на `getOrderStatus` и `createEcomOrder`), либо ввести идемпотентный ключ, поддерживаемый провайдером. Плюс — сохранять «попытку возврата» в БД до вызова и сверять по `getOrderStatus` после.

---

### P0-8. `completeDms` не передаёт сумму провайдеру, но принимает её от клиента

`pbl/.../provider/TxpgAcquiringClient.java:109-123`

```java
public Map<String, Object> completeDms(String providerOrderId, String password,
                                       String login, String terminalPassword, BigDecimal amount) {
    ...
    Map<String, Object> tran = new HashMap<>();
    tran.put("phase", "Clearing");     // amount никуда не кладётся
    Map<String, Object> body = new HashMap<>();
    body.put("tran", tran);
```

`amount` логируется и выбрасывается. API и UI создают впечатление, что частичный capture возможен — по факту всегда клирится полная авторизованная сумма. Мерчант, авторизовавший 1500 ₼ и «захвативший» 500 ₼, спишет с карты 1500 ₼.

Дополнительно (`PaymentLinkService.java:252-257`): capture разрешён для транзакции уже в статусе `SUCCESS` — повторный клиринг того же заказа. И нигде не проверяется, что `request.amount() <= transaction.getAmount()`.

**Чинить:** передавать `amount` в `tran`, запретить capture для `SUCCESS`, валидировать сумму против авторизованной.

> ✅ **Исправлено 17.08.2026.** Запрет capture для `SUCCESS` — вместе с P0-7. Остаток закрыт
> в тот же день, после подтверждения MilliKart, что `phase: "Clearing"` принимает `amount`:
> тело стало `{"tran": {"phase": "Clearing", "amount": "500.00"}}`, сумма валидируется против
> авторизованной (и на третий знак после запятой).
>
> Оказалось, что «передать поле» недостаточно: потолок возврата считался от `transaction.amount`,
> то есть от **авторизованной** суммы. При частичном capture это дыра в другую сторону —
> авторизовали 1500, захватили 500, вернуть можно 1500. Поэтому добавлена колонка
> `transactions.captured_amount` (nullable — у SMS стадии capture нет), а потолок и переход
> в `REFUNDED` считаются от `PaymentLinkService.refundableBase(tx)` = `capturedAmount` ?: `amount`.
> `transaction.amount` после capture не меняется, новых значений в `TransactionStatus` не заведено.
> Попутно `formatAmount` (`setScale(2, UNNECESSARY).toPlainString()`) убрал `amount.toString()`
> из `refund`: научная нотация вида `1E+3` до провайдера больше не доходит.

---

### P0-9. Пароли терминала и заказа уходят в логи и в URL

Три места:

1. `TxpgAcquiringClient.java:110-114, 121` — пароль заказа кладётся в **query-параметр** URL, и URL целиком логируется на уровне INFO:
   ```java
   .queryParam("password", password)
   ...
   log.info("PROVIDER REQ [completeDms] -> POST URL: {}, ...", url);
   ```
   То же в `refund` (155-168) и `getOrderStatus` (202-211). Query-строки оседают ещё и в логах любого прокси между сервисом и эквайером.

2. `OpenLinkController.java:40` + `OpenLinkService.java:157` — редирект-URL с паролем заказа логируется целиком:
   ```java
   return response.order().hppUrl() + "?id=" + ... + "&password=" + response.order().password();
   log.info("Redirecting customer to HPP URL: {}", redirectUrl);
   ```

3. `OpenLinkService.java:146-151` — пароль заказа сохраняется в `provider_response` (JSON-колонка) в открытом виде, в дополнение к отдельной колонке `provider_password`.

Пароли эквайринговых терминалов, по `problems.md`, и так хранятся в БД plain text — но логи обычно уходят в централизованный сборщик с более широким кругом доступа, чем БД.

**Чинить:** пароли — только в заголовках/теле, логировать URL без query-строки, маскировать перед логированием, убрать пароль из `providerResponse`.

> ✅ **Исправлено 19.08.2026** — логи и хранение; отправка не тронута (Р-25). Пункт ошибочно
> считался закрытым с 17.08.2026 и был найден открытым при сверке плана 18.08.2026. К трём
> местам добавилось четвёртое, появившееся по ходу P1-8a: `refreshStatus` сохранял в
> `provider_response` payload заказа целиком, а он по §5.8.3 содержит `password` — после каждого
> опроса; и пятое — `getOrderStatus` писал в INFO тело ответа целиком, то есть тот же `password`.
> Сделано: `ProviderPayloads.urlForLog` (адрес без query-строки — вся, а не один параметр:
> `replaceQueryParam` добавил бы `password=***` туда, где пароля не было) во всех логах адресов
> `TxpgAcquiringClient` и в `OpenLinkController`; `ProviderPayloads.withoutSecrets` (копия payload'а
> без `password`) на каждой записи чужого payload'а в `provider_response` (`refreshStatus`, обе
> ветки; сырой ответ `exec-tran` в `completeDms`/`refund`) и в логах тел; ключ `password` убран из
> `providerResponse` при создании транзакции; `EcomCreateOrderResponse.Order.toString()` печатает
> `password=***`. Пароль остался ровно в двух местах: колонка `provider_password` и redirect-URL
> плательщика. «Пароли только в заголовках/теле» — не сделано намеренно: `exec-tran` по §5.5-5.7
> и по коллекции Postman пароля в адресе не требует, но capture и возврат — денежные пути, и без
> прогона на стенде параметр не убирается; `GET /order/{id}` требует его в адресе по §5.8.
> Записано в `problems.md` §5.

---

## 3. 🟠 Важно (P1)

### P1-1. Spring Security фактически отключён — вся авторизация на одном фильтре

`common/.../SecurityConfig.java:28-36`

```java
http.csrf(disable).cors(disable)
    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
    .addFilterBefore(traceIdFilter, ...)
    .addFilterBefore(jwtAuthFilter, ...);
```

`permitAll()` на всё. Единственный барьер — `JwtAuthFilter.requiresAuthentication()`, который защищает **только** пути, начинающиеся с `/api/v1/`. Всё остальное открыто:

- `/actuator/health` с `show-details: always`, `/actuator/metrics`, `/actuator/info` — публично во всех трёх сервисах;
- `/swagger-ui.html` и `/v3/api-docs` — публичная карта всего API.

`@EnableMethodSecurity(prePostEnabled = true)` включён, но `@PreAuthorize` **не используется нигде** (проверено grep'ом по всему репозиторию) — вся авторизация живёт в ручных `if`-ах внутри сервисов.

### P1-2. Auth-сервис ломается в зависимости от порядка старта сервисов

`auth/.../002-user-directory-schema.xml` создаёт таблицу `companies` **без `<preConditions>`**, тогда как `directory/.../003-directory-schema.xml` — с проверкой `<not><tableExists/></not>`. Три сервиса пишут в одну БД и делят одну таблицу `DATABASECHANGELOG`.

Если `directory` стартует раньше `auth`, changeset `2-auth-core` упадёт на «table companies already exists», и `auth` не поднимется. Порядок старта нигде не зафиксирован (compose/манифестов в проекте нет вообще).

### P1-3. Нет асинхронного callback от провайдера — статусы могут зависать навсегда

DTO `PaymentCallbackRequest.java` есть, а **контроллера, который его принимает, нет** ни в одном из трёх контроллеров `pbl`. Синхронизация статуса держится на двух вещах:
- браузер клиента возвращается на `/api/v1/payment-links/redirect/{tx}`;
- кто-то вручную дёргает `/status`.

Если плательщик закрыл вкладку после оплаты — транзакция навсегда остаётся `PENDING`. `PaymentLinkScheduler` умеет только помечать просроченные ссылки `EXPIRED` (`PaymentLinkScheduler.java:32-42`), реконсиляции транзакций нет.

В тестовом конфиге при этом лежит `callback-secret: ""` — конфиг для несуществующей функции.

### P1-4. Redirect-эндпоинт не проверяет подпись провайдера

> ✅ **Исправлено 15.08.2026** вместе с P0-2. `redirectPage` больше не объявляет `ID`, `PASSWORD`
> и `STATUS`: транзакция определяется исключительно по `merchantRid` из пути, поэтому подделать
> её выбор через query-параметры нельзя, а подпись подделывать нечего — данные провайдера не
> используются вовсе. Покрыто тестом `redirectPage_ignoresProviderIdQueryParam`.
> Асинхронного callback от провайдера по-прежнему нет — это отдельная задача P1-3, и подпись
> нужно будет проверять именно там.

`OpenLinkController.java:58-80` принимает `ID`, `PASSWORD`, `STATUS` из query-параметров и **игнорирует** их (кроме `ID`), никакой верификации подписи callback'а нет. Эндпоинт публичный. Любой может вызвать `/api/v1/payment-links/redirect/x?ID=<любой>` и заставить сервис сходить в TXPG за произвольным заказом.

### P1-5. Гонка при открытии ссылки — двойная оплата single-use ссылки

> ✅ **Исправлено 17.08.2026** связкой с P1-6 и P1-7. `openAndBuildRedirect` — один
> `@Transactional`-метод, который начинается с `PaymentLinkRepository.findWithLockById`
> (`SELECT … FOR UPDATE`); четыре фазы и `TransactionTemplate` из сервиса убраны. Блокировка
> держится и на время HTTP-вызова к эквайеру — осознанный компромисс, записанный javadoc'ом:
> конкурировать за неё могут только одновременные открытия **одной и той же** ссылки.
> Сериализации оказалось мало: второй вызов иначе гасил только что созданный `PENDING`
> и заводил второй живой заказ у эквайера, отменить который нечем. Поэтому `PENDING`,
> появившийся, пока запрос стоял в очереди за блокировкой, считается вторым одновременным
> кликом и получает 409, а не новую сессию. Главный тест —
> `OpenLinkConcurrencyTest.concurrentOpens_ofSingleUseLink_createExactlyOneTransaction`.

`OpenLinkService.openAndBuildRedirect` разбит на четыре фазы. Валидация (фаза 1, проверка «уже оплачена / лимит исчерпан») выполняется в **отдельной** транзакции, затем идёт HTTP-вызов к эквайеру, и только потом (фаза 4, снова отдельная транзакция) пишется `Transaction`.

Два одновременных открытия single-use ссылки оба проходят валидацию → создаются два заказа у провайдера → возможны две оплаты. Поле `@Version` на `PaymentLink` (`PaymentLink.java:48-50`) существует, но в этом пути не используется — блокировка не берётся.

### P1-6. Открытие ссылки заново «убивает» уже авторизованную DMS-транзакцию

> ✅ **Исправлено 17.08.2026.** Гасится только `PENDING`. `AUTHORIZED` при этом считается
> занятым слотом наравне с `SUCCESS` (`countByLinkIdAndStatusIn`), иначе одноразовую ссылку
> можно было бы открыть заново, пока по ней висят деньги. Отказы различимы: «уже оплачена»
> и «есть авторизованный платёж, ожидающий подтверждения». Известное ограничение: операции
> Void у `AcquiringClient` нет, поэтому брошенный холд блокирует ссылку до снятия холда банком
> (`AGENTS.md` §10). Главный тест — `reopen_withAuthorizedTransaction_doesNotMarkItFailed`.

`OpenLinkService.java:96-107`

```java
findFirstByLinkIdAndStatusInOrderByCreatedAtDesc(id, List.of(PENDING, AUTHORIZED));
...
activeTx.setStatus(TransactionStatus.FAILED);
```

В список попадает `AUTHORIZED` — это транзакции с реально захолдированными на карте деньгами, ждущие клиринга. Локально она помечается `FAILED`, у эквайера холд остаётся. Деньги зависают у клиента, у мерчанта нет способа их снять или отменить через портал.

### P1-7. Ошибка +1 при подсчёте успешных платежей

> ✅ **Исправлено 17.08.2026.** `+ 1` убран: Hibernate делает auto-flush перед JPQL-запросом,
> поэтому текущая транзакция уже учтена — ровно так же, как в `completeDms`, где прибавки
> никогда не было. Подтверждено на рантайме (ревью предупреждало, что вывод про auto-flush
> нужно проверить тестом): регрессионный
> `refreshStatus_onMultiUseLink_doesNotCompleteAfterFirstPayment` со старым кодом краснеет —
> ссылка с `maxPayments = 2` закрывается после первой оплаты.

`PaymentLinkService.java:398-409`

```java
tx.setStatus(TransactionStatus.SUCCESS);
long successCount = transactionRepository.countByLinkIdAndStatus(link.getId(), SUCCESS) + 1;  // "including current"
```

`tx` — managed-сущность внутри `@Transactional`. Hibernate делает auto-flush перед JPQL-запросом, поэтому текущая транзакция **уже посчитана**. Ручное `+1` считает её второй раз. Multiple-ссылка с `maxPayments = 2` закроется после первой оплаты.

### P1-8. Статус транзакции выставляется по факту «провайдер не вернул errorCode»

`PaymentLinkService.java:285` (`completeDms`) и `:344-351` (`refund`) выставляют `SUCCESS` / `REFUNDED` безусловно, если из клиента не прилетело исключение. Клиент (`TxpgAcquiringClient.checkAndThrowIfErrorCode`, строки 238-247) проверяет **только** наличие ключа `errorCode` в ответе. Если TXPG на HTTP 200 вернёт отказ в другом виде (например `tran.status = "Declined"`), система запишет успех.

В `refund` при отсутствии `refundId` в ответе провайдера генерируется **локальный случайный** идентификатор (`:342`) — то есть в БД окажется несуществующий у эквайера номер возврата.

### P1-9. Ссылки создаются без срока жизни

> ✅ **Исправлено 18.08.2026.** `CreatePaymentLinkRequest` получил необязательный `expiresAt`,
> `PaymentLinkService.create` выставляет срок **всегда**: не передан — `now + pbl.link.default-ttl`
> (24 часа, ровно тот отсчёт, который уже показывал интерфейс); передан — проверяется приватным
> `validateExpiresAt` (в прошлом → 400 «expiresAt must be in the future»; дальше
> `pbl.link.max-ttl` — 90 дней — → 400 с указанием предельной даты). Тот же метод применяется
> в `update`, но потолок там считается **от `created_at` ссылки**, иначе цепочкой PATCH'ей срок
> продлевался бы бесконечно. `PaymentLinkResponse` получил `expiresAt` (он был только
> в `PaymentLinkSummaryResponse`, из-за чего фронтенд выдумывал «сейчас + 24 часа»).
> `PaymentLinkScheduler` не тронут — ему просто нечего было просрочивать. Миграции нет:
> боевых данных нет, а `NULL` в `expires_at` корректно означает «без срока».
> Покрыто десятью тестами `PaymentLinkIntegrationTest`; проверено обратно снятием проверки
> в `update` и подменой точки отсчёта потолка на `Instant.now()`.

`CreatePaymentLinkRequest` не содержит `expiresAt`, и `PaymentLinkService.create()` (`:89-105`) его не выставляет. Все ссылки бессрочны, пока кто-то не пришлёт PATCH. `technical_handover.md` заявляет «ссылки с ограниченным сроком жизни (TTL)» — по факту TTL нет, а `PaymentLinkScheduler` просто некого просрочивать.

### P1-10. `pbl.base-url` и адреса провайдера захардкожены на localhost/тест

`pbl/src/main/resources/application.yaml`

```yaml
pbl:
  base-url: http://localhost:8080/          # без ${...} — ссылки для клиентов будут вести на localhost
  provider:
    gateway-base-url: https://test.millikart.az:8083/
    api-base-url: http://test.millikart.az:8000/     # ПЛАЙН HTTP
```

Три проблемы сразу: (1) `base-url` без env-подстановки попадает в тело каждой сгенерированной ссылки (`PaymentLinkMapper.buildOpenLink`); (2) адреса **тестового** контура зашиты в прод-конфиг; (3) `api-base-url` — **обычный HTTP**, а логин/пароль терминала уходят туда через Basic Auth, то есть в открытом виде по сети.

### P1-11. Пароль БД в открытом виде во всех трёх конфигах

`auth`, `directory`, `pbl` — `spring.datasource.password: password`, без `${DB_PASSWORD:...}`. Плюс `username: postgres` — подключение суперпользователем.

### P1-12. Нет refresh-токена, logout и отзыва токенов

`AuthController` содержит **только** `/login`. `technical_handover.md` (§2.2, §4.1) заявляет «Login / Refresh / Logout» и «выдачу пар JWT (Access & Refresh)» — этого нет. Токен живёт 24 часа, отозвать его нельзя: заблокированный или удалённый пользователь работает до истечения срока.

Плюс `LoginResponse.java` возвращает жёстко зашитое `expiresIn = 86400` (`AuthService.java:92`), не связанное с `pbl.security.jwt.expiration-ms` — в тестовом профиле (1 час) значения расходятся вчетверо.

### P1-13. Фронтенд: роль по умолчанию `SYSTEM_ADMIN` и отсутствие ролевых guard'ов

`frontend/src/app/context/AuthContext.tsx:46` — `role: role || 'SYSTEM_ADMIN'`. Если бэкенд не вернул роль, пользователь молча становится системным администратором (fail-open вместо fail-closed).

`frontend/src/app/routes.tsx:26-32` — `ProtectedRoute` проверяет только факт логина. Маршруты `/users`, `/audit-logs`, `/terminals`, `/settings` открыты любому вошедшему. Единственная ролевая проверка на весь фронт — в `CompaniesPage.tsx:44`.

Само по себе это не дыра (бэкенд проверяет права), но в сочетании с P0-1 и P0-3 — усугубляет.

### P1-14. Фронтенд не собирается для прода без ручной обвязки

`frontend/src/app/api/client.ts:3-7` — axios создаётся **без `baseURL`**. Разводка по трём микросервисам живёт в dev-прокси `vite.config.ts:27-58` (`8081` / `8082` / `8080`), которого в `vite build` не существует. `import.meta.env` / `VITE_*` не используется нигде, файлов `.env*` нет.

Плюс: **TypeScript не установлен** (нет ни в `dependencies`, ни в `devDependencies`), `build` — это просто `vite build`, который типы не проверяет. То есть типы в проекте ни разу не были проверены компилятором.

---

### P1-15. `COMPANY_EMPLOYEE` может удалять терминалы своей компании

`directory/src/main/java/az/millikart/directory/service/TerminalService.java:202-213`

```java
private void validateWriteAccessToCompany(String targetCompanyId, String actorRole, String actorCompanyId) {
    if ("AUDITOR".equals(actorRole)) throw new InvalidStateException("Access denied: AUDITOR is read-only");
    if ("SYSTEM_ADMIN".equals(actorRole)) return;
    if (targetCompanyId != null && targetCompanyId.equals(actorCompanyId)) return;   // роль больше не проверяется
    throw new InvalidStateException("Access denied");
}
```

Метод отсекает по роли **только `AUDITOR`**. Дальше достаточно совпадения `companyId` — роль
не участвует в решении вообще. Следствия:

1. `COMPANY_EMPLOYEE` может создавать, редактировать и **удалять** (`DELETE /api/v1/terminals/{id}`)
   эквайринговые терминалы своей компании, включая смену `login`/`password`. По ролевой модели
   (`.agents/workflows/mp.md`, `technical_handover.md` §4.1) рядовому сотруднику доступны только
   платёжные ссылки и DMS.
2. Пользователь с **любой** строкой в поле `role` — включая опечатку или неизвестное значение —
   пройдёт эту проверку, если `companyId` совпал. Валидации роли при создании пользователя нет
   (`CreateUserRequest.role` — просто `@NotBlank String`), так что такие пользователи заводятся штатно.

Удаление терминала жёсткое (`terminalRepository.delete`, строка 179), а на `payment_links.terminal_id`
стоит FK — то есть на терминале с историей запрос упадёт 500-й ошибкой, а на свежесозданном
пройдёт молча.

**Чинить:** внести список разрешённых ролей в `validateWriteAccessToCompany` явным параметром
(как это сделано в `PaymentLinkService.validateAccess`), ввести `enum Role` и валидировать роль
при создании/обновлении пользователя.

---

## 4. 🟡 Средне (P2)

| # | Что | Где |
|:---|:---|:---|
| P2-1 | ✅ **Исправлено 22.08.2026** (Р-44, Р-45). Было: `findAll()` без пагинации в `listUsers`, `listCompanies`, `listTerminals` (`listAuditLogs` закрыт 21.08.2026 в P2-2). Стало: все три принимают `Pageable` и отдают общий `PagedResponse` из `common`; `page`/`size` с умолчаниями `0`/`20` и потолком `200`, значения **приводятся**, а не отвергаются; сортировка обязательно с уникальным довеском (`username, id`; `name, id`) — без него запись с неуникальным именем попадает на обе соседние страницы или ни на одну; фильтр soft-deleted переехал из `stream().filter` в запрос (`findAllByStatusNot`), иначе страницы короче `size`, а `totalElements` считает невидимое. Ролевые правила не менялись. Р-44: интерфейс сделан вместе с API — `UsersPage`/`CompaniesPage`/`TerminalsPage` получили `TablePagination` с перезапросом; клиентский поиск остался, но подписан «ищет по текущей странице». Р-45: выпадающие списки терминалов переведены на новый лёгкий `GET /api/v1/terminals/options` | `UserService`, `CompanyService`, `TerminalService`, `TerminalOptionResponse`, три страницы фронтенда |
| P2-2 | ✅ **Исправлено 21.08.2026** (вместе с P2-5, P2-6). Было: `audit_logs` **без единого индекса**, фильтрация для `COMPANY_HEAD` в памяти Java (`logs.stream().filter(...)`) после загрузки всех логов компании. Стало: changeset 004 — индексы `(company_id, created_at desc)`, `(entity_type, entity_id)`, `(created_at desc)`; фильтрация/пагинация/порядок в запросе (`IgnoreCase`-финдеры, `createdAt DESC`, `Pageable` + общий `PagedResponse` из `common`) | `004-audit-log-ip-and-indexes.xml`, `AuditLogService.listAuditLogs`, `AuditLogRepository` |
| P2-3 | ✅ **Исправлено 22.08.2026.** Было шире, чем «нет индекса на `link_id`»: на `transactions` был ровно один индекс, и не покрыты **два** горячих набора запросов — по `link_id` (пять методов репозитория, из них три-четыре на **каждое открытие ссылки плательщиком**) и выборка фоновой сверки по `(status, created_at)`, которая идёт каждые две минуты круглосуточно независимо от нагрузки. Стало: changeset `pbl/007-transaction-indexes.xml` — `idx_transactions_link_status` `(link_id, status)`, `idx_transactions_link_created` `(link_id, created_at desc)`, `idx_transactions_status_created` `(status, created_at)`, каждый под своим `<not><indexExists/></not>`. Больше ни одного: каждый индекс замедляет вставку, а строка пишется на каждом платеже. `link_id` — внешний ключ, а PostgreSQL внешние ключи не индексирует, поэтому без индекса страдало и удаление родительских строк | `007-transaction-indexes.xml`, `TransactionIndexSchemaTest` |
| P2-4 | ✅ **Исправлено 15.08.2026** (вместе с P0-1). Было: N+1 при листинге транзакций — `tx.getLink()` LAZY, дёргался для каждой строки в маппере. Стало: `@EntityGraph(attributePaths = "link")` на обоих методах листинга | `TransactionRepository.java`, `PaymentLinkService.mapToTransactionResponse` |
| P2-5 | ✅ **Исправлено 21.08.2026** (Р-35). Было: `logAction` — `@Transactional` (REQUIRED), запись аудита откатывалась вместе с бизнес-операцией; отказы не логировались вообще. Стало — заметьте, **не** «просто `REQUIRES_NEW`» (он записал бы действие, которое не состоялось, — живой пример P2-8): успех пишется после коммита (`AuditEvent` → `AuditLogWriter` `AFTER_COMMIT` → `recordSuccess`), отказ — сразу в своей транзакции (`logDenied`, `outcome = DENIED`) во всех точках отказа `directory`; свою транзакцию обе записи открывают `TransactionTemplate`'ом, а не аннотацией — из `listAuditLogs` вызов идёт внутри того же класса, прокси не участвует, и с аннотацией запись об отказе в чтении журнала терялась; сбой записи журнала не роняет ни операцию, ни код отказа (ERROR `AUDIT_WRITE_FAILED`) | `AuditLogWriter`, `AuditLogService`, `CompanyService`, `TerminalService` |
| P2-6 | ✅ **Исправлено 21.08.2026** (Р-36). Было: в `AuditLog` нет поля с IP, хотя `technical_handover.md` §4.4 заявляет фиксацию IP. Стало: `client_ip varchar(45)` + `outcome`; адрес кладёт `ClientIpFilter` (`common`) в `ClientIpHolder` через `ClientIp.resolve` — заголовкам верим только от доверенного прокси; вне запроса адрес пуст, это штатно. §4.4 сверен с кодом | `AuditLog.java`, `ClientIpFilter`, `ClientIpHolder` |
| P2-7 | Роли — строковые литералы в 7 файлах, без enum и без валидации при создании пользователя. Можно завести пользователя с ролью `"ADMIN"` или `"Company_Head"` — он просто не получит никаких прав, тихо | `UserService.java`, `CompanyService.java`, `TerminalService.java`, `PaymentLinkService.java` |
| P2-8 | ✅ **Исправлено 21.08.2026** (Р-37…Р-40). Было: жёсткое удаление терминала при живом FK `fk_payment_links_terminal` → 500 «Unexpected server error» вместо 409; статуса у терминала не было, поэтому «вывести из работы, не удаляя» тоже было нельзя. Стало: удаление убрано целиком (`DELETE` → 405), у терминала `status` `ACTIVE`/`BLOCKED` через существующий `PATCH`; блокировка переводит `ACTIVE`-ссылки терминала в `SUSPENDED` одним `UPDATE` в той же транзакции (`PaymentLinkStatusRepository`), разблокировка возвращает их в `ACTIVE`, а просроченные за время блокировки — в `EXPIRED`; открытие ссылки плательщиком проверяет терминал **после** захвата блокировки строки, иначе блокировка, случившаяся в этот момент, пропустила бы платёж. Возвраты, capture DMS и сверка статуса намеренно не проверяют статус терминала (Р-38) | `TerminalService`, `PaymentLinkStatusRepository`, `OpenLinkService`, `PaymentLinkService`, `005-terminal-status.xml` ×2 |
| P2-9 | ✅ **Исправлено 20.08.2026** (Р-31, Р-32). Было: `update()` менял `amount` у ссылки с уже идущими платежами и воскрешал `EXPIRED`/`COMPLETED` в `ACTIVE`; заодно `maxPayments` опускался ниже числа прошедших платежей. Стало: `AMOUNT_LOCKING_STATUSES` (`PENDING`, `AUTHORIZED`, `SUCCESS`, `PARTIALLY_REFUNDED`, `REFUNDED`) запирает сумму — проверка запросом `existsByLinkIdAndStatusIn`; `ALLOWED_STATUS_TRANSITIONS` разрешает только `ACTIVE`/`EXPIRED → CANCELED` и `CANCELED → ACTIVE` со сроком в будущем; `maxPayments` не ниже числа `SUCCESS`; правка пишется одной строкой `log.info` | `PaymentLinkService.update`, `TransactionRepository` |
| P2-10 | `extractClientIp` безусловно доверяет `X-Forwarded-For` — подделывается любым клиентом, если перед сервисом нет доверенного прокси | `OpenLinkController.java:46-56` |
| P2-11 | Фронтенд: ~7 200 строк мёртвого кода (~40%) — `src/App.tsx` (шаблон Vite), `src/api/client.ts` (**второй axios-клиент, логирующий тела запросов, включая пароли, в консоль**), 48 неиспользуемых shadcn-компонентов, страницы `ReportsPage`/`NotificationsPage`/`POSTransactionListPage` вне роутера | `frontend/src/` |
| P2-12 | ✅ **Исправлено 20.08.2026** (Р-30). Было шире, чем «нули в статистике»: `App.tsx:147` клал статус как `String(t.status \|\| 'APPROVED').toUpperCase() as any`, а `TransactionStatus` перечислял одиннадцать значений из трёх чужих словарей — без `SUCCESS` и `AUTHORIZED` вовсе. Из-за этого блок статистики всегда показывал нули, фильтр по статусу не находил ничего, цвета и подписи в таблице не срабатывали, а `PayByLinkDetailPage:303` переводил настоящий `FAILED` обратно в выдуманный `3d-failed`. Стало: в типе ровно шесть значений бэкенда, `parseTransactionStatus` / `parsePaymentMethod` на границе (незнакомое → `null` + предупреждение, никакой подстановки), `as any` снят — расхождение теперь ловит `tsc -b` | `types/transaction.ts`, `App.tsx`, `StatsOverview.tsx`, `TransactionTable.tsx`, `FilterPanel.tsx`, `TransactionDetailPage.tsx`, `PayByLinkDetailPage.tsx`, `HomePage.tsx`, `statusColors.ts`, `mockData.ts`, `i18n/translations.ts` |

---

## 5. ⚪ Мелочи (P3)

- **`build/` и `.gradle/` закоммичены в git**: 108 `.class`-файлов и 26 служебных файлов Gradle под версионным контролем. `.gitignore` содержит только `/​.gradle/` и `/build/` — корневые пути, поэтому модульные `auth/build/`, `common/build/` и т.д. не игнорируются. Нужно `**/build/` и `**/.gradle/` + `git rm -r --cached`.

  > ✅ **Исправлено 24.08.2026 (P3-3).** `.gitignore` переписан (`build/` и `.gradle/` без ведущего слэша — действуют на любой глубине), 166 файлов сборки и кэша Gradle раскоммичены (`git rm -r --cached`), включая два jar по 63 МБ из июльской сборки. История не переписывалась — jar'ы остаются в `.git` и в клоне, это отдельное решение (см. P0-5: от переписывания истории отказались).
- **`.idea/` закоммичен**, включая `pbl/.idea/dataSources/*.xml` с описанием подключения к БД.

  > ✅ **Исправлено 24.08.2026 (P3-3).** `.idea/` раскоммичен; отслеживаемым оставлен один `checkstyle-idea.xml` (общая настройка команды) — в `.gitignore` это `.idea/*` + `!.idea/checkstyle-idea.xml`, именно `.idea/*`, а не `.idea/`: исключение внутри исключённого каталога не действует. `pbl/.idea/dataSources` к этому моменту уже не отслеживался; проверено, что в раскоммиченных файлах реальных секретов не было (`db-forest-config.xml` — только UUID).
- **Дублирующиеся Gradle-обёртки**: `gradle-wrapper.properties` лежит в корне и ещё в трёх модулях; `auth/gradlew.bat`, `directory/gradlew.bat` — копии корневого. Остатки от объединения отдельных проектов.

  > ✅ **Исправлено 24.08.2026 (P3-3).** Обёртки в `auth/`, `directory/`, `pbl/` (12 файлов) удалены с диска и из git — `settings.gradle` подключает модули из корня, ими не пользовалось ничто (проверено по скриптам, workflow и документам: везде корневой `./gradlew :модуль:задача`). Корневые `gradlew`/`gradlew.bat`/`gradle/wrapper/` остались; `./gradlew cleanTest test` после удаления зелёный. Заодно добавлен `.gitattributes` (`* text=auto eol=lf`, `*.bat eol=crlf`, бинарные типы) и индекс нормализован — `gradlew.bat` перестал показывать фантомное изменение всех 164 строк из-за CRLF/LF.
- **`directory/settings.gradle`** с `rootProject.name = 'directory'` внутри подпроекта монорепо — сбивает с толку и мешает IDE.
- **`directory/build.gradle:8`** объявляет `springBootVersion = '3.1.0'`, который нигде не используется (реальная версия — 3.2.5 из корня).
- **`RestTemplate` bean** объявлен, но не используется (везде `RestClient`) — `RestTemplateConfig.java:14-20`.
- **`PaymentLink.currentPaymentsCount`** помечен в коде как legacy («count is computed dynamically»), но остаётся `nullable = false` и обновляется в трёх местах непоследовательно.

  > ✅ **Исправлено 24.08.2026 (P2-16, Р-49, Р-50).** Поле перестало быть legacy и получило смысл: «сколько раз ссылкой воспользовались», по `TransactionStatus.PAID_STATUSES` (`SUCCESS` + `REFUNDED` + `PARTIALLY_REFUNDED`). Колонка при каждом расчёте (`completeDms`, `refreshStatus`) получает то же число, что уходит в ответ API; `refund()` её не трогает и не должен — возврат не выводит статус из набора, база и ответ сходятся сами (сторож `columnAndResponse_agreeThroughPaymentsAndRefunds`). Заодно возврат перестал освобождать слот использования, а карточка получила `refundedPaymentsCount`. Подробности — `AGENTS.md` §10, P2-16; `problems.md` §16.
- **`ConflictException`** объявлен и обрабатывается в `GlobalExceptionHandler`, но не бросается нигде.
- **`InvalidStateException` → 403 FORBIDDEN** — неочевидный маппинг: класс называется «неверное состояние», а используется для отказа в доступе.
- **`README.md`** — две строки `# merchant-portal-mk`. `frontend/package.json` называется `@figma/my-make-file`.

  > ✅ **Частично исправлено 24.08.2026 (P3-3).** README заменён: что за система, четыре модуля с портами, запуск (переменные окружения без значений, со ссылкой на новый `.env.example`), тесты, ссылки на документы. Имя `@figma/my-make-file` в `frontend/package.json` **не** менялось — в ТЗ P3-3 этого шага не было; пункт остаётся на нём.
- **Перечисление аккаунтов**: `AuthService.java:46` возвращает `"User account is " + status` — раскрывает существование пользователя и его статус до проверки пароля (при этом сам ответ на неверный пароль — корректно обезличенный).

  > ✅ **Исправлено 19.08.2026 (P3-Auth).** Пароль проверяется первым; несуществующий пользователь и неверный пароль дают одинаковые код и тело, и одинаковое время — для несуществующего пользователя пароль сравнивается с фиксированным bcrypt-хэшем-заглушкой, иначе разницу в ~80 мс видно и при одинаковых текстах. Статус аккаунта в ответе больше не называется («Account is not active. Please contact your administrator.»). Остаточная утечка — верный пароль для заблокированного аккаунта отличим — принята сознательно, `problems.md` §7.

- **Нет rate limiting по IP** на `/api/v1/auth/login` — есть только блокировка аккаунта после 6 попыток (`AuthService.java:66`), что само по себе даёт вектор блокировки чужих аккаунтов.

  > ✅ **Исправлено 19.08.2026 (P3-Auth).** `LoginRateLimiter` (Caffeine, в памяти — Р-27): 10 неудачных попыток на адрес за 15 минут, проверка до обращения к базе и до bcrypt, превышение — 429 с `Retry-After`. Считаются только неудачи, успешный вход обнуляет счётчик адреса. Блокировка аккаунта оставлена как была (6/30 минут, PCI-DSS 8.3.4, Р-28). Вместе с этим закрыт P2-10: адрес берётся через `ClientIp.resolve` и только от доверенного прокси — без этого лимит обходился одним заголовком.

---

## 6. Расхождения документации и кода

`technical_handover.md` описывает систему точнее, чем она есть. Перед сдачей заказчику это стоит выровнять — иначе приёмка пройдёт по документу, а эксплуатация столкнётся с другим продуктом.

| Заявлено в документе | Фактически |
|:---|:---|
| «выдачу и валидацию **пар** JWT (Access & Refresh)», «Login / Refresh / Logout» | Только `/login`. Refresh-токена, logout и отзыва нет |
| «Строгая изоляция данных (Multi-tenancy): пользователи видят только ресурсы своей компании» | Нарушено в трёх местах: P0-1, P0-2, P0-3 |
| «фиксация каждого мутирующего действия … с указанием **IP-адреса**» | В `AuditLog` нет поля для IP. Отказы в доступе не логируются |
| «ссылки с ограниченным **сроком жизни (TTL)**» | `expiresAt` нельзя задать при создании; все ссылки бессрочны |
| «отмена предавторизации (**Void**)» | Метода void/отмены холда нет ни в `AcquiringClient`, ни в контроллерах |
| «Liquibase — автоматическое версионирование миграций» | Три сервиса пишут в одну БД через одну таблицу `DATABASECHANGELOG`, с конфликтом на `companies` (P1-2) |
| «Успешно прошёл цикл отладки, сборки и **модульного тестирования**» | Юнит-тестов нет — только 3 интеграционных теста (~20 методов). Фронтенд-тестов нет вообще, TypeScript даже не установлен |
| «Готов к передаче Заказчику» | 9 блокеров, из них 4 — утечка данных и 2 — риск потери денег |

Справедливости ради: `problems.md` честно фиксирует часть техдолга (общая БД, plain-text пароли терминалов, отсутствие Docker/CI) — это правильный документ, его стоит расширить остальным.

---

## 7. Что сделано хорошо

Чтобы картина была честной:

- **Доменная модель платежей** продумана: SMS/DMS, single/multiple использование, частичные возвраты с накоплением `refundedAmount`, `@Version` для оптимистичной блокировки, статусная машина через enum'ы.
- **Resilience4j** (circuit breaker + retry) на интеграции с эквайером — редко встречается на этой стадии проекта.
- **Обработка ошибок провайдера** в `TxpgAcquiringClient` аккуратная: разделены HTTP-ошибки, ошибки соединения и бизнес-отказы, есть `extractErrorDescription`.
- **`GlobalExceptionHandler`** централизован и покрывает разумный набор исключений, включая `OptimisticLockingFailureException`.
- **Блокировка аккаунта** после 6 неудачных попыток на 30 минут со ссылкой на PCI-DSS 8.3.4 — сделано корректно, с обнулением счётчика при успехе.
- **`PasswordConstraintValidator`** реализует политику PCI-DSS v4.0 добросовестно.
- **`TraceIdFilter`** с MDC и `X-Trace-Id` — правильный задел под распределённую трассировку.
- **Интеграционные тесты** `PaymentLinkIntegrationTest` (13 методов) реально проверяют RBAC: 401 без токена, 403 для аудитора, 403 при чужой компании, разграничение refund между employee и head.
- **Liquibase с `ddl-auto: validate`** вместо `update` — правильный выбор.
- **`open-in-view: false`** во всех сервисах.
- **Маскирование пароля терминала** в `TerminalService.mapToResponse` (`"********"`).

---

## 8. План действий

**Спринт 0 — до любого выхода на прод (блокеры):**

1. P0-1, P0-2, P0-4 — закрыть три дыры в изоляции данных `pbl`; добавить интеграционные тесты на межтенантный доступ для каждого эндпоинта.
2. P0-3 — переделать кэширование в `directory` (проверка прав вне кэша).
3. P0-5 — ротировать JWT-ключ, вынести в env, вычистить историю git.
4. P0-6 — убрать дефолтного админа из прод-миграции.
5. P0-7, P0-8 — снять `@Retry` с денежных операций, передавать `amount` в clearing, запретить повторный capture.
6. ✅ P0-9 — сделано 19.08.2026 (логи и хранение): адреса логируются без query-строки, пароля нет ни в логах, ни в `provider_response`. Из URL к эквайеру пароль пока не убран (Р-25, `problems.md` §5).

**Спринт 1 — работоспособность:**

7. P1-1 — привести Spring Security в нормальный вид: `authenticated()` по умолчанию, `@PreAuthorize` на контроллерах, закрыть actuator и swagger.
8. P1-3, P1-4 — реализовать callback-эндпоинт с проверкой подписи + фоновую реконсиляцию зависших `PENDING`.
9. ✅ P1-5, P1-6, P1-7 — сделано 17.08.2026: одна транзакция + пессимистичная блокировка ссылки, `AUTHORIZED` не гасится и занимает слот, `+1` убран.
10. P1-10, P1-11 — вынести все URL и пароли в env, перевести `api-base-url` на HTTPS.
11. ✅ P1-14 — сделано 18.08.2026: TypeScript 7 установлен, `tsc -b` перед `vite build`, `VITE_API_BASE_URL`, мёртвый код фронта удалён.
11a. P1-15 — ограничить запись в терминалы по роли (сейчас проверяется только `companyId`).
12. P0-4 / P2-7 — ввести `enum Role`, убрать строковые литералы.

**Спринт 2 — техдолг:**

12a. ✅ P3-Auth (+P2-10) — сделано 19.08.2026: перечисление аккаунтов устранено, лимит попыток входа по адресу, адрес клиента — только через `ClientIp.resolve` от доверенного прокси.

12b. ✅ P2-9 — сделано 20.08.2026: сумма ссылки заморожена с первой же попытки оплаты, `ACTIVE` достижим только из `CANCELED` и только со сроком в будущем, `maxPayments` не опускается ниже числа прошедших платежей, правка ссылки видна в логе одной строкой.

12c. ✅ P2-12 — сделано 20.08.2026: во фронтовом `TransactionStatus` остались только шесть значений бэкенда, разбор на границе вместо `as any` и `|| 'APPROVED'`, статистика и фильтр по статусу заработали. Расхождение словарей теперь не проходит `tsc -b`.

13. ✅ Пагинация на всех листингах, индексы на `audit_logs` и `transactions` — закрыто
    22.08.2026. 21.08.2026: индексы `audit_logs` и пагинация `listAuditLogs` (P2-2);
    22.08.2026: три индекса на `transactions` (P2-3 — не только `link_id`, но и `(status,
    created_at)` для фоновой сверки) и пагинация последних трёх листингов вместе с экранами
    (P2-1, Р-44), плюс лёгкий `GET /api/v1/terminals/options` для выпадающих списков (Р-45).
    Непагинированных листингов в проекте не осталось.
14. ✅ Аудит — сделано 21.08.2026 (P2-5, P2-6; Р-35, Р-36), с поправкой к формулировке:
    не «в `REQUIRES_NEW`» в лоб, а успех — после коммита (`AFTER_COMMIT` + `REQUIRES_NEW`),
    отказы — сразу в своей транзакции, поле IP — через `ClientIpHolder`.
14a. ✅ P2-8 — сделано 21.08.2026 (Р-37…Р-40): терминалы блокируются, а не удаляются; ссылки
    заблокированного терминала приостанавливаются и возвращаются при разблокировке; обслуживание
    уже начатых платежей (возврат, capture, сверка) блокировка не трогает.
14b. ✅ P2-14 — сделано 22.08.2026 (Р-41…Р-43): машинерия журнала переехала в `common`, аудит
    появился в `auth` (учётные записи и входы, включая неудачные) и в `pbl` (ссылки, списания
    холдов, возвраты и операции с неизвестным исходом); журнал — только на дозапись.
15. Docker-образы, `docker-compose.yml`, CI — как и планировалось в `problems.md`.
16. ✅ Чистка репозитория — закрыта в два приёма: мёртвый фронтенд-код удалён 18.08.2026 (P1-14); `build/`, `.gradle/`, `.idea/` раскоммичены, дублирующие обёртки удалены, `.gitattributes` и README добавлены 24.08.2026 (P3-3). Хвосты (`directory/settings.gradle`, `springBootVersion` в `directory/build.gradle`, имя `frontend/package.json`) остаются в §5 открытыми.
17. Обновить `technical_handover.md` под реальность.

---

## Приложение: как проводилось ревью

- Прочитан **весь** backend-код: `common`, `auth`, `directory`, `pbl` (все `.java`, `application.yaml`, `build.gradle`, Liquibase changelogs) — около 5 000 строк.
- Фронтенд проанализирован по ключевым файлам (аутентификация, роутинг, API-слой, страницы, `package.json`, `vite.config.ts`); директория `components/ui/` (shadcn-boilerplate) и `dist/` не разбирались построчно.
- Проверены: история git на предмет секретов, состав индекса git, наличие Docker/CI, соответствие Liquibase-схем сущностям JPA, покрытие тестами.
- **Сборка и прогон тестов не выполнялись** — среда ревью не имела доступа к Gradle-кэшу и сети для загрузки зависимостей. Все выводы получены статическим анализом кода; выводы о поведении в рантайме (особенно P1-7 про auto-flush Hibernate и P0-3 про кэш) стоит подтвердить тестом перед исправлением.
