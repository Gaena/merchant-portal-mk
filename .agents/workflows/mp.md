---
description: Рабочий процесс (Workflow) и контекст проекта MP (Multi-Project: auth, directory, pbl) для AI-агента. Покрывает архитектуру, текущий статус, структуру БД, RBAC и инструкции по сборке/тестированию.
---

# MP (Multi-Project) — Руководство и Статус Проекта для AI-Агента

> ⚠ **Сверено с кодом 17.08.2026 (ревизия `3f890de` + незакоммиченные правки P0-1, P0-2, P1-3, P0-4, P0-3, P1-15). Источник правды — корневой [`AGENTS.md`](../../AGENTS.md).**
> Ниже — детализация по модулям. Список известных багов и ограничений: [`code_review.md`](../../code_review.md).


Данный документ представляет собой единый источник правды (Single Source of Truth) по текущему состоянию, архитектуре и правилам разработки проекта **MP**.

---

## 1. Обзор Проекта и Архитектурный Контекст

Проект **MP** — это микросервисная платформа для электронной коммерции и платежных сервисов (Pay-By-Link, справочники компаний/терминалов, аутентификация/авторизация).

- **Технологический стек**:
  - **Сборщик**: Gradle Multi-Module (Root `mp`, подпроекты `:common`, `:auth`, `:directory`, `:pbl`)
  - **Язык / Фреймворк**: Java 21, Spring Boot **3.2.5** (`build.gradle:3`; переменная `springBootVersion = 3.1.0` в `directory/build.gradle` — мёртвая, не используется)
  - **База данных**: PostgreSQL (общие таблицы в рамках одной схемы/БД), H2 (для интеграционных тестов), Liquibase
  - **Безопасность**: Единый модуль `:common` для JWT (`HS256`) проверки подписи, фильтрации и Stateless RBAC

---

## 2. Текущий Статус Модулей Проекта

Все 4 модуля (`:common`, `auth`, `directory`, `pbl`) покрыты преимущественно **интеграционными** тестами; единственный юнит-тест — `RoleTest` в `common` (7 методов, 25 запусков): `AuthIntegrationTest` (5), `DirectoryIntegrationTest` (12), `PaymentLinkIntegrationTest` (33), `TransactionReconciliationIntegrationTest` (8), три `contextLoads()`. Всего `./gradlew test` — 68 методов / 86 запусков.

⚠ **«Готов» ≠ «готов к проду».** По коду ревью нашло 9 блокеров; на 17.08.2026 закрыты восемь (P0-1 — изоляция листинга транзакций, P0-2 — публичный `GET /api/v1/transactions/{id}/status` с PII, P0-4 — `enum Role`, P0-3 — кэш поверх проверок доступа в `directory`, P0-7 — риск двойного возврата и двойного списания: `@Retry` снят с денежных операций, неизвестный исход отделён от отказа и даёт 502, P0-8 — частичный capture с потолком возврата от захваченной суммы, P0-5 + P0-6 — секреты вынесены в окружение и дефолтный админ убран). Открыт один — P0-9, пароли в логах и в query-параметрах URL. Полный разбор — [`code_review.md`](../../code_review.md), статусы — [`fix_plan.md`](../../fix_plan.md), краткая выжимка — в разделе «Грабли» корневого `AGENTS.md`. Не воспроизводи эти паттерны в новом коде.

### 2.0. `common` — Общий Модуль Безопасности и Исключений
- **Функционал**:
  - Единая реализация генерации и валидации HMAC-SHA256 подписи JWT (`JwtProvider`).
  - Централизованный фильтр аутентификации `JwtAuthFilter` с поддержкой fallback static tokens.
  - Централизованная иерархия ошибок (`BusinessException`, `ResourceNotFoundException`, `InvalidStateException`, `ConflictException`, `UnauthorizedException`) и `GlobalExceptionHandler`.

### 2.1. `auth` — Сервис Аутентификации и Пользователей (Порт 8081)

- **Файл документации**: [auth.md](file:///Users/salayevim/IdeaProjects/mp/auth/auth.md)
- **Postman Коллекция**: [Auth.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/auth/Auth.postman_collection.json)
- **Функционал**:
  - Вход пользователей (`POST /api/v1/auth/login`) и генерация JWT с claims: `userId`, `role`, `companyId`;
    вместе с access-токеном выдаётся refresh-токен (непрозрачная строка, в БД только SHA-256, таблица `refresh_tokens`).
  - Обновление пары (`POST /api/v1/auth/refresh`, ротация с окном снисхождения; повтор заменённого токена за окном
    гасит всю цепочку — маркер `REFRESH_TOKEN_REUSE`) и выход (`POST /api/v1/auth/logout`, гасит цепочку, всегда 204) — P1-12, 18.08.2026.
  - Управление пользователями (CRUD `POST/GET/PATCH/DELETE /api/v1/users`); блокировка/удаление гасят все refresh-токены пользователя.
  - Ограничение видимости и действий в зависимости от ролей (`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR`).
- **Статус**: Функционал реализован, `AuthIntegrationTest` зелёный.
  ⚠ Access-токен живёт 24 ч и при logout/блокировке **не отзывается** (stateless-проверка, чёрного списка нет намеренно);
  полный отзыв наступит после сокращения `JWT_EXPIRATION_MS`, когда фронтенд научится звать `/refresh` (P1-13).
  ⚠ `COMPANY_MANAGER` и `AUDITOR` получают 403 на всех эндпоинтах `/api/v1/users` (доступ только у `SYSTEM_ADMIN` и `COMPANY_HEAD`).

### 2.2. `directory` — Сервис Справочников (Порт 8082)
- **Файл документации**: [directory.md](file:///Users/salayevim/IdeaProjects/mp/directory/directory.md)
- **Postman Коллекция**: [Directory.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/directory/Directory.postman_collection.json)
- **Функционал**:
  - Управление компаниями (`/api/v1/companies`).
  - Управление терминалами MilliKart (`/api/v1/terminals`).
  - Система логирования аудита (`/api/v1/audit-logs`, `AuditLog`).
- **Статус**: Функционал реализован, `DirectoryIntegrationTest` зелёный.
  ✅ 17.08.2026 закрыт P0-3: `@Cacheable`/`@CacheEvict` сняты с `getTerminal`/`getCompany` и их `update`/`delete` целиком (решение Р-9). Раньше ключом был только `#id`, а проверка прав стояла внутри тела метода — при попадании в кэш она не отрабатывала (межтенантная утечка на 15 минут TTL). Кэша в `directory` больше нет; `CacheConfig` в `common` оставлен под будущий кэш в `pbl`. **Не вешай `@Cacheable` на метод с проверкой доступа внутри.**
  ✅ 17.08.2026 закрыт P1-15: `validateWriteAccessToCompany` теперь сначала проверяет роль по `TERMINAL_WRITE_ROLES` (`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`) и только потом `companyId`. `COMPANY_EMPLOYEE`, `AUDITOR` и нераспознанная роль получают 403 на create/update/delete терминалов; чтение своих терминалов сотруднику осталось.

### 2.3. `pbl` — Сервис Платежных Ссылок Pay-By-Link (Порт 8080/8083)
- **Файл документации**: [pay-by-link.md](file:///Users/salayevim/IdeaProjects/mp/pbl/pay-by-link.md)
- **Postman Коллекция**: [Pay-By-Link.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/pbl/Pay-By-Link.postman_collection.json), [NON-PSP Ecom.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/pbl/NON-PSP%20Ecom.postman_collection.json)
- **Функционал**:
  - Создание, редактирование, просмотр, листинг ссылок на оплату (`payment_links`).
  - Открытие платежной страницы (`GET /api/v1/payment-links/{id}/open`), динамическая генерация сессий в эквайринге MilliKart (TXPG) без кэширования старого HPP URL (согласно требованиям Millikart).
  - Фиксация IP-адреса и устройства/User-Agent покупателя в таблице `transactions` при каждом открытии ссылки.
  - Запрос списка транзакций конкретной ссылки (`GET /api/v1/payment-links/{id}/transactions`).
  - Обработка редиректов и генерация онлайн-чека на Thymeleaf (`redirect.html`).
  - Отслеживание транзакций (`transactions`), завершение двухстадийных платежей DMS (`/complete`), проведение возвратов (`/refund`).
  - Интеграционные клиенты: `TxpgAcquiringClient` (боевой MilliKart) и `StubAcquiringClient` (заглушка для локальной отладки/тестирования).
  - Листинг транзакций (`GET /api/v1/transactions`) с пагинацией и ограничением по компании через терминалы (`TransactionRepository.findByLink_TerminalIdIn`); `SYSTEM_ADMIN` и `AUDITOR` видят все компании.
- **Статус**: Функционал реализован, тесты `pbl` зелёные (`PaymentLinkIntegrationTest` — 33 метода, `TransactionReconciliationIntegrationTest` — 8).
  ✅ 15.08.2026 закрыт P0-1: `GET /api/v1/transactions` больше не отдаёт транзакции чужих компаний, снят N+1 на `link`.
  ✅ 15.08.2026 закрыт P0-2: `GET /api/v1/transactions/{identifier}/status` под JWT и проверкой компании; страница возврата `redirect.html` рендерится на сервере (без JS и без опроса), транзакция ищется по `merchantRid` из пути. Заодно закрыт P1-4.
  ✅ 16.08.2026 закрыт P1-3: фоновая сверка зависших `PENDING` (`TransactionReconciliationService` + `TransactionReconciliationScheduler`, cron `0 */2 * * * *`, настройки `pbl.reconciliation.*`). `FAILED` ставится только если эквайер ответил нефинальным статусом и транзакция старше `max-age`; недоступность шлюза статус не меняет. `AUTHORIZED` сверка не трогает.
  ✅ 16.08.2026 закрыт P0-4: роли вынесены в `enum Role` (`common/.../security/Role.java`), строковых литералов ролей в main-коде backend не осталось. `GET /api/v1/payment-links/{id}/transactions` проверялся против несуществующих `MERCHANT_ADMIN`/`MERCHANT_USER` и отдавал 403 всем, кроме `SYSTEM_ADMIN`; теперь права те же, что у чтения самой ссылки (`READ_ROLES`). Наборы ролей — `EnumSet`, разбор строки — `Role.fromValue` (не бросает, точное совпадение регистра).
  ✅ 17.08.2026 закрыт P0-8: `completeDms` передаёт сумму провайдеру (`{"tran": {"phase": "Clearing", "amount": "500.00"}}`), частичный capture работает. Захваченная сумма хранится отдельно — `transactions.captured_amount` (nullable; у SMS capture не бывает), `amount` остаётся авторизованной. Потолок возврата считается от захваченной суммы (`PaymentLinkService.refundableBase`), иначе частичный capture позволял бы вернуть деньги, которые с карты не списывались. Сумма выше авторизованной и третий знак после запятой → 400.
  ⚠ Асинхронного callback от TXPG нет и не будет (решение Р-7): в коллекции задокументированы только `POST /order`, `GET /order/{id}`, `POST /order/{id}/exec-tran`. Заготовки `PaymentCallbackRequest` и `callback-secret` удалены 16.08.2026 — эндпоинт не изобретать. Статус дожимается опросом: страница возврата (один заход), ручной `/status` мерчантом и фоновая сверка.
  ✅ 18.08.2026 закрыт P1-9: у ссылки есть срок жизни. `CreatePaymentLinkRequest.expiresAt` необязателен: не передан — ссылка живёт `pbl.link.default-ttl` (24 часа), передан — проверяется «в будущем» и «не дальше `pbl.link.max-ttl` (90 дней) от `created_at`», иначе 400. В `update` действуют те же ограничения, и потолок считается **от даты создания ссылки** — иначе цепочкой PATCH'ей срок продлевался бы бесконечно. `PaymentLinkResponse` получил `expiresAt` (был только в summary, из-за чего фронтенд подставлял «сейчас + 24 часа»). `PaymentLinkScheduler` не менялся — ему просто нечего было просрочивать. `NULL` в `expires_at` по-прежнему означает «без срока».

---

## 3. Общая Схема Базы Данных и Взаимосвязи

Все микросервисы разделяют общую предметную область PostgreSQL:

| Таблица | Управляющий модуль | Читающие/использующие модули | Назначение |
|---|---|---|---|
| `users` | `auth` | `directory`, `pbl` | Пользователи системы, хэши паролей, роли, связь с компанией |
| `refresh_tokens` | `auth` | — | Refresh-токены (SHA-256 токена, `family_id` цепочки ротации, `rotated_at`, `revoked_at`) — P1-12 |
| `companies` | `directory` | `auth`, `pbl` | Компании (мерчанты) |
| `terminals` | `directory` | `pbl` | Учетные данные терминалов эквайринга MilliKart |
| `payment_links` | `pbl` | — | Сформированные платежные ссылки (SMS/DMS, Single/Multiple) |
| `transactions` | `pbl` | — | Попытки оплаты и транзакции по платежным ссылкам |
| `audit_logs` | `directory` | — | Логи аудита действий с объектами |

---

## 4. Матрица Ролей и Разграничения Доступа (RBAC)

JWT-токен подписывается симметричным ключом (`HS256`). Содержит: `sub` (email), `userId`, `role`, `companyId`.

| Роль | Права доступа |
|---|---|
| `SYSTEM_ADMIN` | Полный доступ ко всем компаниям, терминалам, пользователям, ссылкам и возвратам. |
| `COMPANY_HEAD` | Полное управление своей компанией (`companyId`): пользователи компании, терминалы, ссылки, DMS, возвраты. |
| `COMPANY_MANAGER` | Управление терминалами своей компании, создание/изменение платежных ссылок, проведение DMS и возвратов. |
| `COMPANY_EMPLOYEE` | Создание и просмотр платежных ссылок, проведение DMS, просмотр терминалов своей компании. **Возвраты и запись терминалов запрещены (403 Forbidden)** — с 17.08.2026, см. §2.2. |
| `AUDITOR` | Просмотр (Read-Only) данных всех компаний. **Любые записи/модификации запрещены (403 Forbidden)**. |

> **Правила видимости логов аудита (`/api/v1/audit-logs`)**:
> - `SYSTEM_ADMIN` и `AUDITOR`: видят все логи аудита всей системы.
> - `COMPANY_HEAD` и `COMPANY_MANAGER`: видят логи аудита строго в рамках своей компании (`companyId`).

> **Глобальные читатели в `pbl` (с 15.08.2026)**: `SYSTEM_ADMIN` и `AUDITOR` — единая проверка
> `PaymentLinkService.isGlobalReader(role)`, применяется в `validateAccess`, `list()`, `listTransactions()`
> и (с 16.08.2026, P0-4) `getTransactionsByLinkId()`.
> Раньше аудитор в `pbl` ограничивался своей компанией и при `companyId == null` получал 403/пустую страницу —
> эта асимметрия с `directory` устранена. На запись семантика не изменилась: `AUDITOR` отсекается
> проверкой `allowedRoles` до всех остальных проверок.

---

## 5. Команды Сборки и Тестирования для Агента

При совершении любых изменений АГЕНТ ОБЯЗАН запускать проверку.

**Перед запуском нужна PostgreSQL** на `localhost:5432` (`postgres`/`password`). Схему накатывает Liquibase.
**Порядок запуска сервисов значения не имеет** (с 17.08.2026, P1-2): каждый changeset, создающий общую таблицу, обложен собственным `<preConditions>` — по одному объекту на changeset. Межсервисный ключ `fk_terminals_company` стоит под `onFail="CONTINUE"` и создаётся на том запуске `auth`, когда таблица `terminals` уже существует. Подробности — корневой `AGENTS.md` §4 и §10.

### 5.1. Полная прогонка тестов всех модулей
```bash
./gradlew test
```

### 5.2. Сборка и прогонка тестов отдельного модуля
```bash
# Модуль Auth
./gradlew :auth:test

# Модуль Directory
./gradlew :directory:test

# Модуль PBL
./gradlew :pbl:test
```

### 5.3. Запуск сервисов локально
```bash
# Запуск Auth (порт 8081)
./gradlew :auth:bootRun

# Запуск Directory (порт 8082)
./gradlew :directory:bootRun

# Запуск PBL (порт 8080)
./gradlew :pbl:bootRun
```

---

## 6. Правила разработки для AI-Агента

1. **Не нарушать контракты API**: При внесении изменений в контроллеры или DTO проверять совместимость с документацией ([auth.md](file:///Users/salayevim/IdeaProjects/mp/auth/auth.md), [directory.md](file:///Users/salayevim/IdeaProjects/mp/directory/directory.md), [pay-by-link.md](file:///Users/salayevim/IdeaProjects/mp/pbl/pay-by-link.md)) и Postman-коллекциями.
2. **Проверять тесты**: Любое изменение в коде должно сопровождаться успешным прогоном `./gradlew test`.
3. **Поддерживать разделение ответственности**:
   - `auth` не управляет компаниями/терминалами.
   - `directory` не выдает JWT токены.
   - `pbl` валидирует JWT токены и терминалы, но не создает компании или пользователей.
4. **Обновлять коллекции Postman**: Если меняется эндпоинт или структура запроса/ответа, обновлять соответственный `.postman_collection.json`.
