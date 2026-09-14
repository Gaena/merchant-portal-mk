# AGENTS.md — контекст проекта Merchant Portal (MP)

> Правила, инварианты и грабли для AI-агентов и разработчиков. Если код и этот файл расходятся,
> прав код, а файл правится в том же изменении.
> Ссылки вида «P2-8» ведут в историю работ `project_docs/fix_plan.md`, вида «Р-37» — в реестр решений
> `project_docs/decisions.md`. Истории в этом файле нет: только то, что действует сейчас.

---

## 1. Что это

Платёжный портал мерчанта для MilliKart: платёжные ссылки (Pay-by-Link, одно- и двухстадийные
платежи, возвраты), компании и эквайринговые терминалы, учётные записи с ролями, журнал аудита
и вкладка E-commerce с выпиской провайдера.

Gradle-монорепо: четыре Spring Boot-сервиса, общая библиотека `common` и React SPA. Все сервисы
работают с **одной** базой PostgreSQL и делят часть таблиц (§5) — осознанное допущение, см. §10
«Известные ограничения». `ecom` вдобавок читает схему шлюза провайдера (Oracle) и только читает.

---

## 2. Карта репозитория

```
mp/
├── build.gradle        # Java 21, Spring Boot 3.2.5 (apply false), общие настройки subprojects
├── settings.gradle     # include 'common', 'auth', 'pbl', 'directory', 'ecom'
├── common/             # библиотека: security (JWT), журнал аудита, исключения, общие DTO, поиск;
│                       #   testFixtures — контейнер PostgreSQL для тестов
├── auth/               # :8081 — вход, refresh и logout, пользователи
├── directory/          # :8082 — компании, терминалы, чтение журнала аудита, сверка статусов терминалов
├── pbl/                # :8080 — платёжные ссылки, транзакции, сводка главной, TXPG, кнопка «Тест»
├── ecom/               # :8083 — выписка провайдера и слепок его терминалов (чтение схемы TXPG)
├── frontend/           # :3000 (dev) — React SPA на Vite
├── project_docs/       # документация проекта, таблица ниже
├── .env.example        # шаблон переменных окружения (реальный .env — в .gitignore)
├── AGENTS.md           # этот файл
└── README.md           # что это, запуск, список документов
```

Документы в `project_docs/` — у каждой темы одно место:

| Файл | Что в нём | Когда править |
|:---|:---|:---|
| `decisions.md` | реестр решений Р-NN | принято решение — строка в конец таблицы |
| `fix_plan.md` | история: очередь спринтов, журнал, описания закрытых задач | задача закрыта — запись в конец |
| `auth.md`, `directory.md`, `pay-by-link.md`, `ecom.md` | контракты API: запросы, ответы, отказы | изменился эндпоинт, его ответ или отказы |
| `application_description.md` | обзор архитектуры: модули и связи, схема БД (ER и миграции), фоновые процессы, интеграция с TXPG | новая таблица, колонка, миграция, планировщик, связь между сервисами |
| `deployment_guide.md` | установка и эксплуатация: переменные, systemd, nginx, порты | новая переменная, порт, маршрут, сервис |
| `technical_handover.md` | техпаспорт для заказчика; словарь событий аудита (§4.4) | новое событие аудита или видимая заказчику функция |
| `code_review.md` | ревью от 14.08.2026 | заморожен, все пункты закрыты |
| `TXPG-client-side-integration.md` | контракт MilliKart «Client side integration» v0.1.3 | внешний документ, не правится |

Правила и известные ограничения — здесь, в §10. Postman-коллекции: `auth/Auth.postman_collection.json`,
`directory/Directory.postman_collection.json`, `pbl/Pay-By-Link.postman_collection.json`,
`pbl/NON-PSP Ecom.postman_collection.json`. Контракт эквайера — источник словаря статусов заказа
(§5.8.8) и ответов `exec-tran` (§5.5–5.7); он описывает только SMS, `Order_DMS` в нём нет.

---

## 3. Стек — точные версии

**Backend**

| Что | Версия | Где задано |
|:---|:---|:---|
| Java | 21 (toolchain) | `build.gradle` |
| Spring Boot | **3.2.5** | `build.gradle` |
| Spring Security | 6.x (из BOM) | — |
| JJWT | 0.11.5 | `common/build.gradle` |
| Resilience4j | 2.2.0 (только `pbl`) | `pbl/build.gradle` |
| SpringDoc OpenAPI | 2.5.0 | `common/build.gradle` |
| Caffeine | 3.1.8 — только счётчики лимита входа (`LoginRateLimiter`) | `common/build.gradle` |
| Liquibase | из BOM | — |
| PostgreSQL | **16** — в проде и в тестовом контейнере (Р-73); часть тестов на H2 (§11) | `project_docs/deployment_guide.md` §4.5, `PostgresTestContainer` |
| Oracle JDBC | `ojdbc11` 23.4, только `ecom` | `ecom/build.gradle` |
| Testcontainers | 1.20.6 — выше BOM Boot, причина в §11 | `build.gradle` |
| Gradle wrapper | 8.5 | `gradle/wrapper/gradle-wrapper.properties` |
| Lombok | 1.18.30 | во всех модулях |

**Frontend**

| Что | Версия |
|:---|:---|
| React | 18.3.1 |
| **Vite** | **6.3.5** |
| MUI | 7.3.5 (`@mui/lab` 7.0.1-beta.19 — единственная beta с peer ровно `^7.3.5`; обновлять парой) |
| **react-router** | **7.13.0** (пакет `react-router`, НЕ `react-router-dom`) |
| Tailwind CSS | 4.1.12 — подключён, классы только в двух файлах; конвенция — MUI, новых классов не добавлять |
| axios | `^1.7.9` · recharts 2.15.2 · xlsx `^0.18.5` |
| **TypeScript** | **7.0.2**. `strict` выключен **явно** в `tsconfig.app.json` (TS ≥ 7 включает его по умолчанию) |
| oxlint | 1.16.0, конфиг `.oxlintrc.json` |

> **Кэша в проекте нет.** `common/.../config/CacheConfig.java` с `@EnableCaching` жив, но ни одного
> `@Cacheable` / `@CacheEvict` нет: они стояли поверх проверок прав и сняты вместе с дырой (P0-3, Р-9).
> Не восстанавливай. `directory/build.gradle` объявляет `springBootVersion = '3.1.0'` — мёртвая
> переменная, версия берётся из корневого `build.gradle`.

---

## 4. Команды

```bash
# --- Backend (из корня) ---
./gradlew build                 # сборка всех модулей
./gradlew test                  # все тесты; нужен запущенный Docker (§11)
./gradlew :pbl:test             # тесты одного модуля (:common, :auth, :directory, :ecom)
./gradlew :auth:bootRun         # запуск сервиса: auth 8081, directory 8082, pbl 8080, ecom 8083

# --- Frontend ---
cd frontend
npm ci                          # или npm install
npm run dev                     # localhost:3000, проксирует /api/v1/* на сервисы (§9)
npm run typecheck               # tsc -b (оба проекта: src и vite.config.ts)
npm run lint                    # oxlint
npm run build                   # tsc -b && vite build → dist/ (типы проверяются ДО сборки)
npm run preview                 # отдать dist/ локально
```

`npm run build` **падает на ошибках типов** — это гейт, а не совет. Тестов на фронтенде нет,
поэтому после правок обязательны `npm run typecheck` и проверка в браузере.

Адрес API в собранном фронтенде — `VITE_API_BASE_URL` (шаблон `frontend/.env.example`). Пусто по
умолчанию: запросы идут относительно origin — в dev их разводит прокси Vite, в проде nginx на том
же домене. Значение зашивается в бандл при сборке, в рантайме не читается.

**Перед запуском нужна PostgreSQL** (`DB_URL`, `DB_USERNAME` — с дефолтами на локальную базу
`postgres`). Схему создаёт Liquibase на старте (`ddl-auto: validate`, миграции не отключать).
Порядок старта сервисов значения не имеет (P1-2): каждый changeset общей таблицы обложен своим
`<preConditions>`.

⚠ **Без переменных окружения сервис не стартует, и дефолтов у секретов нет.** `DB_PASSWORD` и
`JWT_SECRET` нужны всем четырём сервисам, причём `JWT_SECRET` — байт в байт одинаковый, иначе
токен от `auth` не проходит в остальных. `pbl` дополнительно требует `PBL_BASE_URL`,
`PBL_PROVIDER_GATEWAY_BASE_URL` и `PBL_PROVIDER_API_BASE_URL` (P1-10), `ecom` — `ECOM_TXPG_URL`,
`ECOM_TXPG_USERNAME` и `ECOM_TXPG_PASSWORD`. Полный перечень — `.env.example` и
`project_docs/deployment_guide.md` §8.3, процедура первого запуска — там же, §20.

```bash
export DB_PASSWORD='...'
export JWT_SECRET="$(openssl rand -base64 48)"   # одно значение на все сервисы
./gradlew :auth:bootRun
```

**Дефолт у секрета не заводить ни при каких обстоятельствах:** именно дефолт в `JwtProvider` держал
ключ подписи в репозитории (P0-5). `JwtProvider` отказывается стартовать на пустом ключе, на ключе
короче 32 байт и на скомпрометированном ключе из истории git. Отсутствие переменной объясняет
`MissingSecretFailureAnalyzer`: `@ConfigurationProperties` нерезолвнутый `${...}` ошибкой не считает
и передаёт драйверу как текст.

Порты: `auth` 8081, `directory` 8082 и `ecom` 8083 заданы в `application.yaml`; у `pbl`
`server.port` не задан — 8080 получается дефолтом Spring Boot. Публичный адрес `pbl`
(`PBL_BASE_URL`) с портом не связан и должен совпадать с тем, что видит браузер плательщика.

---

## 5. Архитектурные инварианты

Не нарушать без явного обсуждения:

1. **`common` не знает о конкретных сервисах.** Туда идут JWT, Spring Security, журнал аудита,
   исключения, валидаторы, поиск по спискам. Никакой доменной логики.
2. **Владение таблицами:**

   | Таблица | Схему пишет | Кто ещё читает или пишет |
   |:---|:---|:---|
   | `users`, `refresh_tokens` | `auth` | — |
   | `companies` | `auth` (создаёт) + `directory` (дополняет) | `auth` читает название нативным запросом для поиска пользователей |
   | `terminals` | `directory` (создаёт и дополняет), `pbl` (создаёт, если ещё нет), `ecom` (`status_source`, `merchant_rid`, если ещё нет) | `pbl` читает напрямую, минуя REST; `ecom` — для скоупа выписки |
   | `payment_links`, `transactions` | `pbl` | `directory` меняет статусы ссылок нативным запросом при блокировке терминала (Р-39) |
   | `audit_logs` | `directory`, `auth`, `pbl` — каждый с преконтролями, создаёт стартовавший первым | пишут все четыре сервиса через `common` |
   | `provider_terminals` | `ecom` | `directory` читает нативным запросом для сверки статусов терминалов |

3. **Разделение ответственности:** `auth` не управляет компаниями и терминалами; `directory` не
   выдаёт JWT; к API эквайера ходит только `pbl`, к базе провайдера — только `ecom`, и только на
   чтение. HTTP между сервисами нет: общее — через общую базу.
4. **JWT симметричный (HS256)**, один секрет на все сервисы. Claims: `sub` = email, `userId`, `role`,
   `companyId`. Access-токен проверяется **stateless** во всех сервисах; чёрного списка нет и заводить
   его нельзя — запрос в базу на каждый вызов убьёт модель. Отзыв делается через refresh-токены
   (`auth`, P1-12): в базе только SHA-256, ротация с окном снисхождения, гашение цепочки при
   logout, краже и блокировке. Access-токен живёт **15 минут** — это и есть верхняя граница доступа
   после logout, блокировки или удаления.
5. **Все ответы об ошибках** идут через `common.exception.GlobalExceptionHandler` в формате
   `ErrorResponse { timestamp, status, error, message, path }`. Новые исключения — наследники
   из `common.exception`, не `ResponseStatusException`.

   | Исключение | HTTP |
   |:---|:---|
   | `BusinessException` | 400 |
   | `UnauthorizedException` | 401 |
   | `InvalidStateException` | **403** (используется как «доступ запрещён») |
   | `ResourceNotFoundException` | 404 |
   | `ConflictException`, `OptimisticLockingFailureException` | 409 |
   | `PaymentOutcomeUnknownException` | **502** |

   `PaymentOutcomeUnknownException` — единственное исключение со смыслом «не знаем, выполнилась
   операция или нет» (таймаут, обрыв, 5xx, неподтверждённый ответ на денежном вызове). Не
   наследник `BusinessException` намеренно: 400 читается мерчантом как «отказ, повторяй», а
   повторный возврат — это двойной возврат. Обработчик пишет ERROR с маркером
   `PAYMENT_OUTCOME_UNKNOWN` для мониторинга.

---

## 6. Безопасность

**Модель — «по умолчанию запрещено»** (P1-1). `SecurityConfig` заканчивается
`anyRequest().authenticated()`: новый эндпоинт закрыт с момента, как его написали, и остаётся
закрытым, пока его не откроют осознанно.

**Список публичных путей живёт ровно в одном месте:** `common/.../security/PublicEndpoints.java`.
Его читают оба слоя — `JwtAuthFilter` и `SecurityConfig`. Второй копии нет и заводить её нельзя:
расхождение между слоями — это и есть дыра.

| Группа | Пути | Чем защищены |
|:---|:---|:---|
| `PUBLIC_API` | `/api/v1/auth/**` (`/login`, `/refresh`, `/logout`), `/api/v1/payment-links/*/open`, `/api/v1/payment-links/redirect/**` | ничем — публичны по смыслу |
| `INFRASTRUCTURE` | `/actuator/**` | привязкой management-порта к `127.0.0.1`, не токеном |
| `SWAGGER` | `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml` | флагом `springdoc.api-docs.enabled` (по умолчанию **выключен**) |

`SWAGGER` разрешается **условно**: матчеры в `SecurityConfig` добавляются, только когда флаг
включён, и `JwtAuthFilter` пропускает эти пути при том же условии. Шаблон
`/api/v1/payment-links/*/open` — **ровно один сегмент** на месте id, как в маппинге.

**Actuator** — на отдельном порту, привязанном к `127.0.0.1`: auth 9081, directory 9082, pbl 9080,
ecom 9083. На рабочем порту `/actuator/**` отдаёт 404; на management-порту отвечает без токена —
иначе сломались бы пробы. `show-details: always` намеренно: детали видны только с самой машины.

**Единый формат отказа.** `SecurityErrorResponder` реализует и `AuthenticationEntryPoint`, и
`AccessDeniedHandler`, им же пользуется `JwtAuthFilter`: 401 от фильтра и от Spring Security — одна
структура `ErrorResponse`. Отказ вошедшему пользователю — **403**. Несуществующий путь для
вошедшего — **404** (`GlobalExceptionHandler.handleNoHandler`).

`@EnableMethodSecurity` включён, но `@PreAuthorize` **не используется нигде** — вся авторизация в
ручных проверках внутри сервисов через `UserPrincipal`. Не считай аннотацию работающим вторым слоем.

Статический fallback-токен `pbl.security.api-token` даёт роль `SYSTEM_ADMIN` без пароля. Значения по
умолчанию у него нет, он выключен везде, включая тестовые профили; включённый флаг с пустым токеном
роняет старт. Не возвращай дефолт — известный статический токен это бэкдор.

CSRF и CORS выключены осознанно: токен приходит в заголовке, а фронтенд обслуживается с того же
origin через nginx. Обоснование — в комментариях `SecurityConfig`, не «чини» их наугад.

### Роли

`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR` —
**`enum Role` в `common/.../security/Role.java`**. В main-коде строковых литералов ролей нет;
проверки — сравнение с константами, наборы ролей — `EnumSet`.

На границе системы (claim `role` в JWT, колонка `users.role`) роль — строка, и разбор только
`Role.fromValue(String)`:

- **никогда не бросает** — неизвестное значение даёт `Optional.empty()`, и вызывающий обязан
  отказать. `Role.valueOf(...)` на этих данных нельзя: аккуратный 403 превратится в 500;
- **сравнение строго точное**, без `equalsIgnoreCase` и `trim`: иначе значение `system_admin` в базе
  стало бы администратором (`RoleTest.fromValue_isCaseSensitive`, не удалять).

`UserPrincipal.getRole()` → `Role`, **nullable** (роль не распознана); `getRawRole()` — сырая строка
для логов. Статические `UserPrincipal.getRole(principal)` / `getRawRole(principal)` /
`getCompanyId(principal)` null-безопасны — пользуйся ими. Валидации роли в `CreateUserRequest` /
`UpdateUserRequest` нет: пользователь с мусорной ролью безопасен, ему везде отказывают.

Во фронтенде зеркало — `src/app/types/role.ts` (`parseRole`, строго, как `Role.fromValue`):
нераспознанная роль = вход не состоялся (fail-closed). Раскладка «маршрут → роли» — только
`src/app/auth/routeAccess.ts`.

### Фактическая матрица доступа (по коду)

| Эндпоинт | SYSTEM_ADMIN | COMPANY_HEAD | COMPANY_MANAGER | COMPANY_EMPLOYEE | AUDITOR |
|:---|:---:|:---:|:---:|:---:|:---:|
| `POST/GET/PATCH/DELETE /users` | ✅ все | ✅ своя компания | ❌ | ❌ | ❌ |
| `GET /companies` (список) | ✅ | ❌ | ❌ | ❌ | ✅ |
| `GET /companies/{id}` | ✅ | ✅ своя | ✅ своя | ✅ своя | ✅ |
| `POST/PATCH/DELETE /companies` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /terminals`, `/terminals/{id}`, `/terminals/options` | ✅ все | ✅ своя | ✅ своя | ✅ своя | ✅ все |
| `POST/PATCH /terminals` | ✅ | ✅ своя | ✅ своя | ❌ | ❌ |
| пароль терминала: `GET /terminals/{id}/password`, смена в `PATCH` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /acquiring/terminal-checks`, `…/{terminalId}` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /audit-logs` | ✅ все | ✅ своя | ✅ своя | ❌ | ✅ все |
| `POST/PATCH /payment-links` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /payment-links`, `/{id}`, `/{id}/transactions` | ✅ все | ✅ своя | ✅ своя | ✅ своя | ✅ все |
| `GET /transactions`, `/{id}`, `/{id}/status`, `GET /dashboard/summary` | ✅ все | ✅ своя | ✅ своя | ✅ своя | ✅ все |
| `POST /transactions/{id}/complete` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `POST /transactions/{id}/refund` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `GET /ecom/transactions` и вложенные | ✅ все наши терминалы | ✅ своя | ✅ своя | ✅ своя | ✅ все наши терминалы |
| `GET /ecom/provider-terminals`, `POST …/sync` | ✅ | ❌ | ❌ | ❌ | ❌ |

- Запись терминалов — `TerminalService.TERMINAL_WRITE_ROLES`; роль проверяется **до** `companyId`,
  поэтому `COMPANY_EMPLOYEE`, `AUDITOR` и нераспознанная роль получают 403 и на свои терминалы.
  Терминал, выключенный синхронизацией с провайдером, вручную не включает никто (Р-66).
- **AUDITOR — глобальный читатель во всех сервисах** (Р-1); в `pbl` — через
  `PaymentLinkService.isGlobalReader`. На запись это не влияет.
- Скоуп компании в `pbl` идёт через её терминалы (`terminalRepository.findAllByCompanyId`); роль из
  `READ_ROLES` без `companyId` получает пустую страницу, а не отказ. В `ecom` скоуп строит только
  `EcomScopeService`, и без компании там 403 с записью в журнал.
- `DELETE /terminals` нет (405): терминалы блокируются (§10).

---

## 7. Ключевые потоки `pbl`

**Создание и оплата ссылки:**

```
POST /api/v1/payment-links            → PaymentLink(status=ACTIVE), возвращается link
     expiresAt не передан → now + pbl.link.default-ttl (24 ч); передан → проверка
     «в будущем» и «не дальше pbl.link.max-ttl (90 д) от created_at», иначе 400 (P1-9)
GET  /api/v1/payment-links/{id}/open  → (публично) OpenLinkService.openAndBuildRedirect
     ОДНА транзакция целиком (с 17.08.2026, P1-5):
       1. findWithLockById(id)                         [SELECT … FOR UPDATE на строке ссылки]
       2. проверка статуса/срока/лимита; слот занимают SUCCESS и AUTHORIZED (P1-6)
       3. САМАЯ СВЕЖАЯ PENDING → FAILED (findFirst…, ровно одна);
          если она создана уже после начала запроса — это второй одновременный клик → 409
       4. POST в TXPG /order → providerOrderId + password   [HTTP, блокировка удерживается]
       5. Transaction(status=PENDING); пароль — в provider_password, в provider_response
          его нет (P0-9: {hppUrl, id, status})
     → 302 на hppUrl?id=…&password=…  (в лог — только адрес, ProviderPayloads.urlForLog)
плательщик платит на HPP
GET  /api/v1/payment-links/redirect/{tx}  → refreshByRidByMerchant(tx) → Thymeleaf redirect.html
       {tx} — наш ridByMerchant (случайный UUID). Query-параметры провайдера ID/PASSWORD/STATUS
       не объявлены и игнорируются. Один заход к провайдеру, без опроса и без JS на странице.
```

**Статусы:**
`PaymentLinkStatus`: `ACTIVE` → `EXPIRED` | `COMPLETED` | `CANCELED`; `SUSPENDED` — пока терминал
заблокирован (Р-39, Р-40).
`TransactionStatus`: `PENDING` → `AUTHORIZED` (DMS) → `SUCCESS` → `PARTIALLY_REFUNDED` → `REFUNDED`; `FAILED`.

**Маппинг статусов TXPG** (`ProviderOrderStatus.classify`, P1-8a):

| Статус эквайера | `ProviderOrderOutcome` | Локально | Откуда |
|:---|:---|:---|:---|
| `FullyPaid`, `Cleared` | `PAID` | `SUCCESS` | §5.8.8 / старый код (DMS) |
| `Authorized` | `AUTHORIZED` | `AUTHORIZED` | старый код (DMS) |
| `Rejected`, `Expired`, `Failed`, `Declined` | `FAILED_FINAL` | `FAILED` | §5.8.8 / старый код |
| `Preparing` | `NON_FINAL` | не меняется; **единственный**, кого сверка гасит по `max-age` | §5.1, §5.8.3 |
| `PartPaid`, `Cancelled`, `Canceled`, `Refused`, `Closed` | `SETTLED_OTHER` | не меняется, WARN, `FAILED` по таймауту запрещён | §5.8.8 |
| всё остальное, `null`, не-строка | `UNKNOWN` | не меняется, WARN, `FAILED` по таймауту запрещён | — |

**Три суммы у транзакции** (P0-8) — не путать:

| Поле | Что значит | Когда заполняется |
|:---|:---|:---|
| `amount` | авторизованная сумма, история операции | всегда, после capture **не меняется** |
| `capturedAmount` | сколько реально списано с карты при клиринге | только DMS-capture; у SMS `null` |
| `refundedAmount` | сколько уже возвращено | при возвратах, по умолчанию `0` |

Потолок возврата — `refundableBase(tx)` = `capturedAmount`, а при `null` (SMS) — `amount`.
Частичный capture остаётся в статусе `SUCCESS`; отдельного статуса под него нет.

**Провайдер:** `AcquiringClient` с **единственной** реализацией — `TxpgAcquiringClient`, обычный
`@Component`; стаба в боевой сборке нет, тестовый двойник живёт в тестовых исходниках (§11).
`@CircuitBreaker(name="acquiring")` висит на четырёх боевых методах, `@Retry(name="acquiring")` —
только на `createEcomOrder` и `getOrderStatus`: повтор остальных превращается в деньги (P0-7).
`checkTerminalCredentials` (кнопка «Тест») — без обоих (Р-70). Параметры — в
`pbl/src/main/resources/application.yaml`, в аннотациях их нет.

**Классификация сбоев денежных вызовов** (`completeDms`, `refund`) —
`TxpgAcquiringClient.classifyMoneyOperationFailure`:

| Что случилось | Вывод | Исключение | HTTP |
|:---|:---|:---|:---:|
| `errorCode` в ответе 200 | шлюз отказал | `BusinessException` | 400 |
| 200 без `tran.match.ridByPmo` (P1-8b) | **исход неизвестен** | `PaymentOutcomeUnknownException` | 502 |
| `HttpStatusCodeException` 4xx | шлюз отказал | `BusinessException` | 400 |
| `HttpStatusCodeException` 5xx | **исход неизвестен** | `PaymentOutcomeUnknownException` | 502 |
| `ResourceAccessException` (таймаут, connection refused) | **исход неизвестен** | `PaymentOutcomeUnknownException` | 502 |
| любое другое исключение | **исход неизвестен** | `PaymentOutcomeUnknownException` | 502 |

Дефолт — «неизвестно», намеренно: принять отказ за неизвестность стоит одной ручной проверки
статуса, а неизвестность за отказ — денег держателя карты.

**Подтверждение денежных операций** (P1-8b, Р-23). Признак успеха — `tran.match.ridByPmo` в ответе
`exec-tran`; без него результата нет, тело целиком уходит в ERROR-лог (`NO CONFIRMATION`) и летит
`PaymentOutcomeUnknownException`. След операции пишется в `providerResponse`: `mpCapture` после
клиринга и список `mpRefunds`. Причина отказа читается `ProviderDeclineReason.extract` только при
`FAILED_FINAL` и отдаётся как `TransactionResponse.failureReason` (Р-24).

**Карточка транзакции.** `cardNumberMasked`, `rrn` и `approvalCode` читаются на лету из
`providerResponse` разборщиком `ProviderOrderDetails.read` (P1-16); история статусов
`TransactionResponse.statusHistory` собирается `PaymentLinkService.statusHistoryOf` из записанного:
`createdAt`, `mpCapture.at`, каждое `mpRefunds[i].at` и текущий статус (Р-63).

**Сверка зависших `PENDING`** (`TransactionReconciliationService`, P1-3). Берёт до `batch-size`
транзакций в `PENDING` с `createdAt` между `now - give-up-age` и `now - min-age`, каждую — в своей
транзакции (`REQUIRES_NEW`). `FAILED` по `max-age` — **только** при `NON_FINAL`; `UNKNOWN` и
`SETTLED_OTHER` остаются `PENDING` для человека. `AUTHORIZED` сверка не трогает: живой холд —
легитимное состояние покоя. Параметры — `pbl.reconciliation.*`.

**Планировщики** (их пять: два в `pbl`, по одному в `auth`, `ecom` и `directory`) — таблица с
расписаниями и выключателями в `project_docs/application_description.md` §9. В тестовых профилях
`pbl` и `auth` сверка и уборка выключены — тесты вызывают сервисы напрямую.

---

## 8. Конвенции кода

**Backend**

- Пакеты: `az.millikart.<module>.{controller,service,repository,domain,dto,provider}`.
- DTO — **Java `record`**, не классы. Валидация — Jakarta-аннотации прямо в record.
- Сущности — Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`,
  timestamps через `@CreationTimestamp` / `@UpdateTimestamp`.
- Контроллер тонкий: принимает `@AuthenticationPrincipal UserPrincipal principal`
  и сразу передаёт в сервис. Проверки прав — **в сервисе**, не в контроллере.
- Транзакции: `@Transactional` на методах сервиса; в `pbl` местами `TransactionTemplate`
  там, где нужен контроль границ вокруг HTTP-вызовов.
- Логирование: `private static final Logger log = LoggerFactory.getLogger(X.class)`, SLF4J-плейсхолдеры.
  `TraceIdFilter` кладёт `traceId` в MDC — он есть в каждой строке лога.
- Миграции: новый файл в `<module>/src/main/resources/db/changelog/changes/`,
  подключается через `db.changelog-master.xml`. Существующие changeset'ы **не редактировать**.
- **Комментарии (P3-4, 24.08.2026): только `//`, по-русски, максимум 4 строки на блок.**
  Javadoc-блоков `/** */`, HTML-разметки (`<p>`, `<ul>`, `<strong>`) и тегов
  `{@code}` / `{@link}` / `@param` / `@return` в java-коде нет — checkstyle не подключён,
  javadoc никто не генерирует, а разметка съедала треть файла. Комментарий пишется, только если
  он: (1) запрещает с последствием — «не делай X, сломается Y»; (2) объясняет, почему сделано
  не очевидным способом; (3) фиксирует инвариант, невидимый в типе; (4) ссылается на решение
  (`P2-8`, `Р-37`) — хвостом в скобках. Пересказ кода, пересказ имени поля и
  риторика — не пишутся. **Не влезает в 4 строки — значит факт крупный: ему место в §10, а в
  коде остаётся одна строка со ссылкой.** В тестах правило мягче: «зачем этот тест и какой баг
  он ловит» остаётся всегда, режется пересказ шагов; имена тестовых методов не трогать — на них
  ссылается §11.

**Frontend**

- Импорты роутера — из `react-router` (v7), не из `react-router-dom`.
- UI — **только MUI**. Tailwind подключён, но живых классов два (§3); shadcn/Radix удалены —
  не добавляй ни того, ни другого.
- API — единственный клиент `src/app/api/client.ts` (`baseURL` из `VITE_API_BASE_URL`, §4).
  Второго клиента с логированием тел запросов больше нет — не заводить. Токены — только через
  `src/app/auth/session.ts`: access в памяти, refresh в `localStorage` (`mp_refresh_token`);
  access-токен в `localStorage` не класть ни под каким ключом (§9).
- Типы обязаны проходить `npm run typecheck`. `as any` / `@ts-ignore` / `!` для подавления —
  только с комментарием, почему иначе никак. `strict` пока выключен (§3), но код под него писать.
- Локализация — самописный словарь `src/app/i18n/translations.ts` (en/az/ru),
  доступ через `useLanguage()`. Новый текст — обязательно во все три языка.
  Подстановок внутрь фразы (`{amount}`, `%s`) словарь **не умеет**, и заводить их не надо:
  что именно подтверждают — короткий код, id, сумму — выносится отдельной рамкой под текстом
  диалога, а сама фраза остаётся целой (P3-5a). Перед тем как заводить ключ, проверь, нет ли
  готового: до P3-5a в словаре простаивали на трёх языках `cancelLinkAction`,
  `cancelConfirmTitle`/`Text`, `finalizeDMS`, `refundAction`/`refundTitle`/`confirmRefund`,
  а диалоги рядом дублировали их английским текстом прямо в JSX.
- Алиас `@` → `frontend/src` (`vite.config.ts:23`).

---

## 9. Фронтенд

**Точка входа:** `src/main.tsx` → `src/app/App.tsx`, живо всё дерево `src/app/**`.
**Перед правкой файла проверь, что он достижим из `routes.tsx`.** Удалённый мёртвый код (второй
axios-клиент, shadcn/Radix, страницы на моках) и зависимости генератора не возвращать. Не
используются, но лежат: `components/figma/ImageWithFallback.tsx`, `src/index.css`,
`src/assets/*`, `frontend/default_shadcn_theme.css` — кандидаты на уборку.

**Маршруты** (`src/app/routes.tsx`): `/login`, `/` (HomePage), `/transactions`,
`/transactions/ecommerce`, `/transactions/ecommerce/:orderId`, `/transactions/:id`, `/pay-by-link`,
`/pay-by-link/:id`, `/companies`,
`/terminals`, `/users`, `/audit-logs`, `/settings`, `*` (`NotFoundPage`). На `/` и `/login` стоит
`errorElement` (`RouteErrorPage`).

**Авторизация во фронтенде:**

| Файл | Что делает |
|:---|:---|
| `src/app/auth/session.ts` | хранилище: access-токен — модульная переменная (**только память**), refresh — `localStorage['mp_refresh_token']`, профиль `{ email, role, companyId? }` (email и companyId — из claims JWT, роль — из поля `role` ответа через `parseRole`); `applyLoginResponse` fail-closed, `clearSession`, подписка для `useSyncExternalStore`; событие `storage` гасит сессию в других вкладках при выходе |
| `src/app/api/client.ts` | request-интерсептор берёт токен из памяти (к `/api/v1/auth/*` не прикладывает); response-интерсептор на 401: `/login`, `/refresh`, `/logout` — не трогать; уже повторяли — `clearSession`; иначе `refreshSession()` (**single-flight**, один промис на все параллельные 401) и повтор запроса. 401 от `/refresh` и нераспознанная роль сбрасывают сессию, сетевая ошибка — нет |
| `src/app/context/AuthContext.tsx` | `AuthProvider`: при загрузке с refresh-токеном показывает загрузку и зовёт `/refresh` (сессия восстанавливается без формы логина); `login` → `applyLoginResponse`; `logout` — сброс состояния сразу, `POST /logout` вдогонку (ошибка логируется). `isAuthenticated` — по access-токену в памяти |
| `src/app/auth/routeAccess.ts` | **единственная** раскладка «маршрут → роли» (`/users`: `SYSTEM_ADMIN`, `COMPANY_HEAD`; `/companies`: `SYSTEM_ADMIN`, `AUDITOR`; `/audit-logs`: `SYSTEM_ADMIN`, `AUDITOR`, `COMPANY_HEAD`, `COMPANY_MANAGER`; остальное — всем вошедшим). Читают `RoleRoute` и `Sidebar` |
| `src/app/auth/guards.tsx` | `ProtectedRoute` (→ `/login`), `PublicOnlyRoute` (→ `/`), `RoleRoute` (роль не подходит → `ForbiddenPage`, не редирект и не белый экран) |

Клиентские guard'ы — **UX, а не безопасность**: права проверяет бэкенд. Эндпоинта `/me` нет —
вместо имени показывается email.

**Выдуманных данных на экранах нет — и не возвращать** (Р-48). Поле, которого нет в API, не
показывается: ни прочерком с выдуманным значением, ни «умолчанием». Поля `PaymentLink`, которых API
не отдаёт (`redirectUrl`, `note`, `dmsStatus`, `finalizedAt`, `cardNetwork`, `cardLast4`,
`transactionId`, `payerIp`, `sentVia`), помечены в `utils/payByLinkData.ts` как всегда `undefined`.
`HomePage` ничего не считает сама: сводку отдаёт `GET /api/v1/dashboard/summary`, последние операции —
первая страница `/api/v1/transactions` с явным `size: 10`. На `SettingsPage` живы только название
компании (одиночный `GET /api/v1/companies/{id}` по `companyId` из токена) и язык. Удалённые метрики,
вкладки настроек и сочинённую историю не возвращать (P3-6, P3-7).

**Деньги на главной — всегда с валютой.** Колонки `currency` у `transactions` нет, она на
`payment_links`, поэтому сводного числа поверх валют нет: каждая валюта — свой блок. Выручка —
`COALESCE(captured_amount, amount)` по `PAID_STATUSES` минус возвраты.

**Идентификаторы и терминал на экранах** (Р-58, Р-59). Номер заказа провайдера и `ridByMerchant` —
первыми, внутренний UUID — вторым. Терминал подписывается логином, имя — пояснением; порядок живёт
только в `utils/terminals.ts`, разбор операции из ответа — только в `utils/mapTransaction.ts`.
`merchantReference` на экранах не показывается (Р-60).

**Вкладка E-commerce — выписка сервиса `ecom`, а не операции портала** (Р-65, Р-80). Свои запросы и
разбор — `utils/ecom.ts`, восемь статусов — `types/ecom.ts` (к шести статусам ссылок добавлены
`PARTIALLY_PAID` и `CANCELED`), своя карточка заказа по номеру у провайдера. Общий список операций
из `App` в неё не передаётся; фильтровать и считать итоги на экране нельзя — это делает сервер.

**Форма заведения терминала** (Р-80): `SYSTEM_ADMIN` выбирает терминал из справочника провайдера и
вводит пароль (`merchantRid`); остальным справочник недоступен, у них ручной ввод названия и логина.

**Списки терминалов — два разных запроса.** Экрану управления — постраничный `GET /api/v1/terminals`;
всем, кому нужна подпись или выпадающий список, — лёгкий `GET /api/v1/terminals/options` со всеми
терминалами, включая заблокированные (форма ссылки сама берёт только `ACTIVE`).

**Опасные действия — только через подтверждение.** Удаление, смена статуса, блокировка и
разблокировка, сохранение правки терминала, отмена ссылки, списание холда и возврат не уходят на
сервер по клику: сначала окно, где названы **эта** запись и последствия именно этого действия.
Окно одно — `app/components/ConfirmDialog.tsx` (§10). Денежные вызовы живут только внутри окон:

```bash
cd frontend/src
grep -rn "status: 'CANCELED'\|/refund\|/complete" app/pages/*.tsx   # пять строк, все внутри окон
grep -rn "autoFocus" app/pages/*.tsx                                 # ничего — правило внутри компонента
```

Окна-формы (создание пользователя, компании, терминала и ссылки, правка терминала, «поделиться
ссылкой») — отдельные, к подтверждениям не относятся.

**Прокси на бэкенд** (`vite.config.ts`, только dev): `/api/v1/auth`, `/api/v1/users` → 8081 ·
`/api/v1/companies`, `/api/v1/terminals`, `/api/v1/audit-logs` → 8082 · `/api/v1/payment-links`,
`/api/v1/transactions`, `/api/v1/dashboard`, `/api/v1/acquiring` → 8080 · `/api/v1/ecom` → 8083.
Новый префикс — сюда и в конфиг nginx (`project_docs/deployment_guide.md` §11).

**Что на моках:** блок 2FA на `LoginPage` — диалог мёртв, а подпись «Secured with 2-Factor
Authentication» под формой — ложное утверждение о безопасности. Всё остальное — реальный API.
`utils/mockData.ts` вопреки названию содержит живые форматтеры — не удалять.

---

## 10. Грабли — прочитать до первой правки

### Известные ограничения

Осознанные и не исправленные на 13.09.2026. Разборы — в `project_docs/fix_plan.md` и в истории git
(`problems.md`, удалён 13.09.2026).

- **Общая база и суперпользователь.** Все сервисы в одной базе и ходят в неё под `postgres`; есть
  кросс-модульные нативные запросы (`auth` → `companies`, `directory` → `payment_links` и
  `provider_terminals`). Изоляции между сервисами нет, и права на журнал аудита (Р-42,
  `project_docs/deployment_guide.md` §5.1a) пока ни на что не влияют. `DATABASECHANGELOG` одна на всех —
  поэтому id changeset'ов несут имя модуля.
- **Пароли терминалов хранятся открытым текстом** (`terminals.password`).
- **Void нет.** Снять DMS-холд `AcquiringClient` не умеет: брошенный `AUTHORIZED` занимает слот
  ссылки, пока банк не отпустит холд сам. Гасить его локально нельзя.
- **Контракт TXPG не описывает DMS.** Статусы `Authorized` и `Cleared` взяты из исходного кода и
  контрактом не подтверждены; какой `description` у записей холда и клиринга в `order.trans[]` —
  неизвестно, поэтому `Purchase` при выборе записи — предпочтение, а не фильтр.
- **Пароль заказа уходит в адресе** `exec-tran` и `GET /order/{id}` (Р-25). Из `exec-tran` его можно
  убрать только после прогона на тестовом стенде.
- **Возвраты и реверсалы, сделанные мимо портала, не отражаются.** `SETTLED_OTHER` оставляет
  `PENDING` для ручного разбора, а по уже `SUCCESS` внешний возврат не замечается вовсе: суммы из
  `order.trans[]` не читаются.
- **Вход.** Верный пароль к заблокированному аккаунту отличим от неверного (принято при P3-Auth);
  лимит попыток по адресу стоит только на `/api/v1/auth/login`.
- **Правки платёжной ссылки не версионируются** — остаются запись `UPDATE` в журнале и строка в логе.
- **Ручная блокировка терминала в MilliKart не уходит**: в портале он «не выпускает платежи», у
  провайдера — работает. Обратное направление есть — синхронизация (Р-66).
- **Статус компании ничего не останавливает.** `INACTIVE` не проверяет ни один сервис; чтобы
  остановить платежи компании, блокируют её терминалы.
- **`pbl` отдаёт `page`/`size` в `PageRequest.of` без проверки** в `GET /transactions` и
  `GET /payment-links`: `?size=0` даёт 500, потолка нет.
- **Карточка ссылки** не показывает карту, номер транзакции и адрес плательщика (полей нет в API);
  **карточка операции** рисует строку комиссии с нулём — поля `fee` в системе нет.
- **Английский текст в JSX** остался на `PayByLinkPage`, `PayByLinkDetailPage` и
  `TransactionDetailPage` рядом с переведёнными диалогами.
- **SQL `ecom` из портала на Oracle провайдера ещё не исполнялся** (Р-74…Р-79, Р-83). Выписка проверена
  тестами на выгрузке стенда (114 заказов во всех статусах, с возвратами и реверсалами) и 15.09.2026
  исполнена на локальном `oracle-free` со схемой `TXPG`, собранной по SQL провайдера, и той же
  выгрузкой; запрос справочника терминалов — там же, вручную. Chargeback не разбирается.
- **Скоуп выписки по логину доверяет логину терминала** (Р-83). Логин в `terminals` не уникален, а у
  терминала, заведённого вручную, его вводит человек: совпавший с чужим логин открывает компании
  платежи чужого мерчанта, и два наших терминала с одним логином видят одну выписку.
- **Тесты.** Часть интеграционных тестов по-прежнему на H2 (§11).
- **Гигиена, до которой не дошли:** `directory/settings.gradle` с собственным `rootProject.name`,
  мёртвая `springBootVersion` в `directory/build.gradle`, неиспользуемый бин `RestTemplate` в `pbl`.
  Docker-образов, `docker-compose.yml` и конфигурации CI в репозитории нет.

### Тонкости, на которых легко ошибиться

**Деньги и эквайер**

- **Денежную операцию не повторять, пока исход неизвестен.** На `completeDms` и `refund` нет
  `@Retry`; 502 значит «неизвестно», и возврат поверх, возможно, ушедших денег — двойной возврат.
- **Успех capture и возврата подтверждает `tran.match.ridByPmo`, а не отсутствие `errorCode`**
  (P1-8b). Значения из чужого payload — только через `ProviderPayloads.scalarText` (строка или
  число → текст, структура → «нет»): второй `asText`/`String.valueOf` не заводить — так однажды `{}`
  прошёл за подтверждение.
- **Незнакомый статус заказа эквайера никогда не приводит к `FAILED`** (P1-8a, Р-20). Новое слово от
  TXPG — в `ProviderOrderStatus` с указанием источника; не приводи статус к нижнему регистру и не
  обрезай пробелы — незнакомая форма это незнакомый статус.
- **`provider_response` — чужой payload:** в базу и в лог — только через
  `ProviderPayloads.withoutSecrets`, адреса к эквайеру и redirect плательщика — только через
  `ProviderPayloads.urlForLog` (P0-9). Пароль заказа живёт в `provider_password` и в redirect
  плательщика, больше нигде; новый DTO с секретом — сразу с маскирующим `toString()`. Проверка
  (должна молчать):
  ```bash
  grep -nE 'log\.(info|debug|warn|error)\(.*URL: \{\}", url' \
    pbl/src/main/java/az/millikart/pbl/provider/TxpgAcquiringClient.java
  grep -n '"password", response.order().password()' \
    pbl/src/main/java/az/millikart/pbl/service/OpenLinkService.java
  ```
- **`rrn` и `approvalCode` — в `order.trans[]` (или `order.lastTran`), маска карты — в
  `order.srcToken.displayName`**; ключа `cardNumberMasked` в контракте нет. Читать через
  `ProviderOrderDetails.read`; `regTime` сравнивать как строку, не парсить (P1-16).
- **Асинхронного callback от TXPG нет и не будет** (Р-7). Статус дожимается страницей возврата
  (один заход), ручным `/status` и фоновой сверкой — эндпоинт не изобретать.
- **Hibernate auto-flush.** Считаешь после изменения managed-сущности — она уже посчитана: flush
  перед JPQL сбрасывает изменение в БД. Прибавки `+ 1` в `refreshStatus` и `completeDms` быть не
  должно (P1-7).
- **Открытие ссылки держит блокировку строки на время HTTP-вызова к эквайеру** (P1-5) — осознанно.
  Статус терминала читается **после** `findWithLockById`: чтение до блокировки оставит невидимой
  блокировку, случившуюся, пока открытие ждало лок.
- **Сумма ссылки заморожена после первой попытки оплаты, `ACTIVE` достижим только из `CANCELED`**
  (P2-9, Р-31, Р-32). `AMOUNT_LOCKING_STATUSES` перечисляет статусы, которые правку **запрещают**:
  новое значение `TransactionStatus` по умолчанию запрещает. Переходы — только из
  `ALLOWED_STATUS_TRANSITIONS`; `expiresAt` применяется до `status`.
- **Срок жизни ссылки считается от `created_at`** (P1-9): потолок `pbl.link.max-ttl` в `update`
  меряется от создания, иначе цепочкой PATCH'ей срок продлевается бесконечно.
- **«Платёж состоялся?» — только `TransactionStatus.PAID_STATUSES`** (`SUCCESS`, `REFUNDED`,
  `PARTIALLY_REFUNDED`; Р-49). Им считаются дата оплаты, `currentPaymentsCount`, занятые слоты и
  запрет понижать `maxPayments`. Возврат не освобождает слот и не пересчитывает `COMPLETED`;
  `AUTHORIZED` в набор не входит, слот он занимает отдельным основанием (P1-6).
- **Кнопка «Тест» заводит у провайдера настоящий заказ** (Р-70). Без `@Retry` и без общего
  `@CircuitBreaker`: иначе проверки администратора закрыли бы приём платежей всем. Исход
  классифицируется по коду в теле (`InvalidLogin`), а не по HTTP-статусу. Выписка обязана отсекать
  незавершённые заказы (Р-71), иначе каждая проверка появится у мерчанта строкой.

**Терминалы**

- **Номер терминала выдаёт база** (Р-81): последовательность `terminals_id_seq`, номер берёт
  `TerminalRepository.nextId` при заведении. Не `@GeneratedValue`: тесты и сверка сохраняют терминал с
  заданным номером, а у сгенерированного ключа `save` такой номер молча заменил бы новым.
- **Терминалы не удаляются — блокируются** (P2-8, Р-37): `DELETE` отвечает 405, вывод из работы —
  `PATCH` со `status: BLOCKED`. На терминал ссылаются `payment_links`, удалить его нельзя в принципе.
- **Блокировка запрещает только НОВЫЕ платежи** (Р-38). Проверок статуса терминала не должно быть в
  `refund`, `completeDms` и `refreshStatus`: иначе холд провисит на карте, клиент не получит возврат,
  а `PENDING` не дожмётся. Проверка — только в создании ссылки и `openAndBuildRedirect`.
- **Статусы терминалов синхронизируются с провайдером** (Р-66). Неудачный опрос и пустой слепок не
  применяются; терминал гасится после трёх пропаданий подряд; `status_source = MANUAL` не трогается
  никогда; выключенный синхронизацией включает только она. Отсутствие строки в слепке — «не знаем»,
  а не «выключен»; терминал без `merchant_rid` сверка не касается. Актор в журнале — `system`. Полная таблица
  переходов — `project_docs/directory.md` §3.2.
- **Пароль терминала — один путь наружу** (Р-64). `TerminalResponse.password` всегда `"********"`;
  настоящий отдаёт только `GET /api/v1/terminals/{id}/password` для `SYSTEM_ADMIN`, с записью `READ`
  в журнале без самого ключа. В `TerminalOptionResponse` пароля нет вовсе — не маскирован, а
  отсутствует.
- **`GET /api/v1/terminals/options` отдаёт и заблокированные терминалы** (Р-45): по снятому с
  обслуживания терминалу подпись старых платежей должна остаться. Фильтрует потребитель.
- **`merchantRid` и `ridByMerchant` — разные идентификаторы** (Р-69). `merchantRid` — мерчант у
  провайдера (`terminals.merchant_rid`), `ridByMerchant` — платёж у мерчанта
  (`transactions.rid_by_merchant`). Пустой `ridByMerchant` из базы провайдера ничем не подменять.

**`ecom`**

- **Читает чужую базу и только читает** (Р-65). Наша PostgreSQL помечена `@Primary` — ей достаются
  JPA, Liquibase и всё, что просит `DataSource` без уточнения; снимешь `@Primary`, и миграции однажды
  уедут в чужую базу. Пул к шлюзу маленький и read-only, транзакционного менеджера у него нет.
- **Строка выписки — заказ со всей историей** (Р-74). Страница выбирается номерами заказов, операции
  склеивает `EcomOrderAssembler`: запрос «операции, первые N» дал бы N операций, а не N заказов. Токен
  — подзапросом: две карты одного покупателя задвоили бы операции.
- **Деньги — по `phase` и `clearamt`, не по `trantype`** (Р-75). У всех покупок `trantype = Purchase`, и
  сумма `tranamt` считает авторизацию и списание одного платежа дважды: на выгрузке стенда 770 AZN
  вместо 357, а снятые холды выходили успешными. Реверсал — не возврат (Р-77): реверсал холда денег не
  двигает, реверсал покупки уменьшает списанное. Правила денег — только `EcomOrderAssembler.money`,
  итоги периода идут через него же; второй набор правил в SQL не заводить.
- **Справочник терминалов: одна строка на мерчанта, ключ — `merchant.rid`** (Р-79). Не `terminal.rid`:
  по мерчанту выписка находит заказы. Разные логины у одного мерчанта не применяются — он не
  обновляется и не гаснет. Фильтр `Active` у логина и терминала и есть то, что гасит наш терминал:
  снимешь его — выключенные у провайдера останутся живыми у нас.
- **`Authorized` не значит «денег нет»** (Р-76). При мультиклиринге заказ остаётся `Authorized` и
  после списания, поэтому фильтр Р-71 пропускает `Authorized` с одобренным списанием. Признак списания
  в SQL (`finishedOrdersOnly`) и в `EcomOperationKind.CAPTURE` один — меняются вместе.
- **`getLowIdForTime` не получает будущего времени** — на нём функция не работает (провайдер). Вызов
  только через `lowIdForTime`, где аргумент прижат к `sysdate` базы, а не к нашим часам и поясу;
  верхняя граница окна — только у периодов, закончившихся больше суток назад.
- **Колонки — только из SQL провайдера.** Отсутствующая в схеме колонка роняет всю выписку:
  `terminalid`, `ridbypmo`, `srcemail`, `srcmobile`, `getHighIdForTime` не читаются, это проверяет
  `TxpgTransactionRepositoryTest`. `tran.ridbyacq` — только строкой. Даты шлюза — местное время
  `ecom.txpg.zone`: в параметры уходит `LocalDateTime`, не `Instant`.
- **Скоуп — только `EcomScopeService`:** компания → её терминалы → их логины (Р-83). Пустой список —
  пустая выписка; ветки «терминалов нет, значит показать всё» быть не должно. В SQL скоуп — во всех
  трёх запросах (`loginScope`): `tr.merchantid` из `login` с `ownerkind = 'TerminalSys'` и
  `m.id = tr.merchantid` в join; без `ownerkind` найдётся логин другого владельца. `merchantRids`
  фильтра (`null` — фильтра нет) только сужает выбор заказов; фильтр из одних чужих мерчантов —
  пустая выписка без запроса к шлюзу.
- **`o.password` из схемы шлюза не попадает никуда** — ни в выписку, ни в экспорт, ни в логи.
  Период обязателен и ограничен, страница — курсорная (номер последнего заказа страницы).

**Журнал аудита**

- **Как писать.** Успех — `eventPublisher.publishEvent(AuditEvent.of(...))` внутри бизнес-транзакции:
  запись ляжет после коммита (Р-35). Отказ — `auditLogService.logDenied(...)` прямо перед `throw`;
  неизвестный исход денежной операции — `logUnresolved(...)`; оба синхронно, в своей транзакции.
  `recordSuccess` напрямую зовут только там, где откатывать нечего: чтение пароля терминала,
  проверка терминала, сверка статусов из планировщика. Ошибку записи журнала не пробрасывать.
  Машинерия одна на проект — `az.millikart.common.audit` (Р-41).
- **`entityType` и `action` — только константы `AuditEntity` / `AuditAction`** (P3-2). Новое
  значение — сначала в словарь и в таблицу `project_docs/technical_handover.md` §4.4, потом в код.
  Соглашения: `entityId` у `AUTH` — всегда логин; `"ALL"` — действие над списком; `"NEW"` — отказ
  в заведении терминала, у которого ещё нет номера; смена статуса —
  `BLOCK`/`UNBLOCK`, а не текст в `UPDATE`; `companyId` у отказа — компания актора; актор
  автоматических действий — `system`.
- **В журнал не попадают пароли, токены и их части.** В `details` входа — только категория отказа;
  логин неудачного входа — недоверенный ввод, обрезается по ширине колонки.
- **Лимит входа пишет в журнал один раз за окно** (`count == maxFailures`) и в базу не ходит: запись
  на каждую отбитую попытку сделала бы защиту усилителем нагрузки.

**Spring и ошибки**

- **Ошибка клиента не должна выглядеть как сбой сервера.** Всё, что не перечислено в
  `GlobalExceptionHandler`, падает в `handleUnexpected` — 500 и ERROR со стектрейсом. Добавляя
  эндпоинт, проверь, что Spring бросит на кривом запросе и есть ли обработчик.
- **`@Transactional` не работает при вызове изнутри того же класса** — вызов идёт мимо прокси.
  Транзакционность, которая обязана соблюдаться при любом вызове, — через `TransactionTemplate`.
- **Два `@ConditionalOnProperty` на реализациях одного интерфейса — не переключатель:** значение вне
  `havingValue` не выбирает ничего, и контекст падает жалобой на бин. Нужен выбор — `@Bean`-фабрика с
  `if` и отказом на непонятном значении.
- **`@SpringBootApplication(scanBasePackages = "az.millikart")` не расширяет поиск сущностей и
  репозиториев.** В каждом сервисе явные `@EntityScan` и `@EnableJpaRepositories` — и свой пакет, и
  `az.millikart.common.audit`. Симптом пропуска — `Not a managed type` на старте.
- **`InvalidStateException` = 403.** Для отказа в доступе бросай его, не `UnauthorizedException`.

**Листинги, поиск, адрес клиента**

- **Все листинги постраничны** (P2-1). Новый — сразу с `Pageable` и `PagedResponse` из
  `az.millikart.common.dto`, второго `PagedResponse` не заводить. `page`/`size` **приводятся**
  (`page ≥ 0`, `size` 1…200), а не отвергаются.
- **Сортировка страницы заканчивается уникальной колонкой** (`name, id`), иначе запись попадает на
  две страницы или ни на одну. **Фильтр «удалённых» — в запросе**, не после чтения страницы.
- **Дата оплаты в списке ссылок — один запрос на страницу** (`findLastPaidAtByLinkIds`), счётчики
  платежей в `PaymentLinkSummaryResponse` не добавлять — N+1 (P2-15, P2-16).
- **Поисковая строка — только через `SearchTerms`** (`common/search`, P3-1): `normalize` в
  контроллере, `toLikePattern` в запросе, у каждого `LIKE` — `ESCAPE '!'`. Голый
  `LIKE '%' || :q || '%'` — дыра: ввод `%` возвращает всю таблицу. Скоуп роли — условие запроса
  рядом с поиском, никогда не пост-фильтр.
- **Адрес клиента — только `ClientIp.resolve(request, trustedProxies.addresses())`**, в коде сервисов —
  `ClientIpHolder` (P3-Auth, Р-29, Р-36). Заголовкам верим только от адресов из `mp.trusted-proxies`;
  настоящий адрес nginx дописывает в **конец** `X-Forwarded-For`. Проверка (должна молчать):
  ```bash
  grep -rn 'getHeader("X-Forwarded-For")\|getHeader("X-Real-IP")' \
    --include='*.java' auth common directory pbl ecom | grep -v /build/ | grep -v ClientIp.java
  ```

**Фронтенд**

- **Новое окно подтверждения — только `ConfirmDialog`** (P3-5b). Четыре правила безопасного окна
  живут только в нём: не закрывать во время запроса, гасить обе кнопки, `autoFocus` на безопасной,
  опасная — `contained`. Содержимое окна передаётся `children`.
- **Словари бэкенда во фронтенде — только настоящие значения, и разбор на границе** (P2-12, P2-13):
  `parseTransactionStatus`, `parsePaymentMethod`, `parseLinkStatus` и соседи. Не добавлять значения
  «на будущее», не подставлять умолчание вместо незнакомого, не глушить расхождение через `as any`.
  Незнакомое значение → `null`. Проверка (должна молчать):
  ```bash
  grep -rnE "===? *'(APPROVED|DECLINED|3d-failed|success|pending|canceled|cancelled|paid|active|expired|completed|sms|dms|single|multiple)'" \
    frontend/src --include='*.ts' --include='*.tsx'
  ```
- **Кнопка действия видна ровно тогда, когда бэкенд его примет** (Р-62). Правила возврата повторены в
  `isRefundable` и `refundableLeftOf` (`TransactionDetailPage.tsx`) — меняешь правило на бэкенде,
  меняй и там.
- **Отказ денежной операции показывается в самом окне, и «отказано» отличается от «неизвестно»**
  (Р-61). Разбор один — `utils/moneyOperationError.ts`; 5xx и отсутствие ответа — неизвестность, и
  повтор до проверки статуса закрыт. Кнопка проверки статуса показывает полученный ответ.

---

## 11. Тесты

**Где идут.** По умолчанию — H2 в режиме `MODE=PostgreSQL`. Там, где вопрос теста в поведении
СУБД, — настоящая PostgreSQL 16 в контейнере (Р-68), та же версия, что в проде (Р-73); таким тестам
нужен запущенный Docker:

| Модуль | Тесты на PostgreSQL |
|:---|:---|
| `auth` | `RefreshTokenConcurrencyTest`, `UserListPaginationTest` |
| `directory` | `AuditLogSchemaTest`, `SharedSchemaMigrationTest`, `DirectoryListPaginationTest`, `AuditLogQueryTest`, `TerminalBlockingIntegrationTest` |
| `pbl` | `TransactionIndexSchemaTest`, `OpenLinkConcurrencyTest`, `MoneyOperationsIntegrationTest`, `TerminalBlockedIntegrationTest`, `TransactionReconciliationIntegrationTest`, `DashboardSummaryTest`, `PaymentLinkRefundUsageTest` |

Остальные тесты с базой на H2 намеренно: там база просто хранилище.

**Контейнер:**
- Объявлен один раз — `common` testFixtures, `PostgresTestContainer`: статичный, один на JVM. Образ
  `postgres:16-alpine` прибит и совпадает с продовой версией: меняется одна — меняется и другая
  (`project_docs/deployment_guide.md` §4.5).
  Подключается `@Import(PostgresTestContainer.class)` рядом с `@SpringBootTest`; тестам без Spring
  доступен через `instance()`, и в `SharedSchemaMigrationTest` каждый метод работает в своей схеме.
- Помечен `@TestConfiguration`, **а не `@Configuration`**: сервисы сканируют `az.millikart` целиком,
  и обычная конфигурация молча перевела бы на контейнер все тесты разом.
- Имена таблиц в метаданных JDBC — в нижнем регистре (H2 складывает в верхний).
- Время, которое тест пишет в базу мимо Hibernate, — в UTC (`LocalDateTime.ofInstant(..., UTC)`):
  `Timestamp.from` кодирует его в поясе JVM.
- `TIMESTAMPADD` — функция H2, у PostgreSQL её нет; сдвиг времени —
  `created_at + CAST(? AS double precision) * INTERVAL '1 second'`. `PaymentLinkIntegrationTest`
  его ещё содержит и потому остаётся на H2.
- Testcontainers 1.20.6 задан выше BOM Spring Boot 3.2.5, а тестовой задаче передаётся
  `api.version=1.40`: 1.19.x ходит в Docker по API 1.32, Docker Engine 29 отвечает на это 400, и
  снаружи это выглядит как «Could not find a valid Docker environment». Обновится Boot — обе строки
  можно снять.

**Тестовые профили.** `src/test/resources/application.yaml` **полностью заменяет** боевой, а не
дополняет, поэтому повторяет его значимые места: management-порт задан, `springdoc.*.enabled: false`,
fallback-токен выключен. Ключ подписи — `test-only-jwt-secret-not-used-anywhere-else-0123456789`,
одинаковый во всех модулях. Сверка `PENDING` в `pbl` и уборка refresh-токенов в `auth` в тестах
выключены — тесты вызывают сервисы напрямую.

**Провайдер в тестах.** Боевая реализация одна, подмена — только явная:
- `@Import(StubAcquirerConfig.class)` даёт `@Primary`-мок, делегирующий двойнику `StubAcquiringClient`;
  тест переопределяет нужный метод через `doReturn(...)`. **Сбрасывать мок в `@BeforeEach` приходится
  самим:** бин объявлен конфигурацией, а не `@MockBean`, и слушатель Spring его не чистит.
- `@MockBean AcquiringClient` — где нужен отказ или задержка провайдера (`MoneyOperationsIntegrationTest`,
  `TransactionReconciliationIntegrationTest`, `OpenLinkConcurrencyTest`).
- Двойник и конфигурация лежат в `pbl/src/test/java/.../provider/`; в `src/main` их не возвращать —
  там это клиент, который отвечает «оплачено», не спросив эквайера. Сам `TxpgAcquiringClient`
  проверяется на управляемом HTTP в `TxpgAcquiringClientTest` (`MockRestServiceServer`).

**Фикстуры** для листингов создаются прямо через репозитории, минуя провайдера. Тесты гонок
(`OpenLinkConcurrencyTest`, `RefreshTokenConcurrencyTest`) — без MockMvc и без `@Transactional` на
тесте: потоки должны видеть коммиты и блокировки друг друга.

**Не покрыто:** идемпотентность возвратов на уровне хранилища — таблицы `refunds` и ключей
идемпотентности нет (Р-12), 502 перекладывает сверку на человека. Фронтенд-тестов нет вовсе.

Что покрывает каждый тестовый класс, — снимок в `project_docs/fix_plan.md` («Тесты: что покрывает
каждый класс»). Правило: любое изменение backend-кода сопровождается зелёным `./gradlew test`.

---

## 12. Правила работы для AI-агента

1. **Согласовывать изменения.** Перед правкой исходников или конфигов: описать, что и зачем
   меняется, какие файлы затрагиваются, — и дождаться явного подтверждения.
2. **Обновлять документацию в том же изменении — по таблице §2, у каждой темы одно место.** Новое
   правило или известное ограничение — сюда, в §10; новое решение — строка в `decisions.md`; закрытая
   задача — запись в конец `fix_plan.md`; изменился эндпоинт — модульный документ и Postman-коллекция;
   новая таблица, колонка или миграция — `application_description.md`; новая переменная, порт или
   маршрут — `deployment_guide.md`; новое событие аудита — таблица `technical_handover.md` §4.4.
   Один и тот же факт в двух документах не записывать — во втором ставить ссылку.
3. **Проверять тестами.** `./gradlew test` после любой правки backend (нужен Docker, §11).
4. **Не расширять поверхность атаки.** Новый публичный путь — это правка
   `PublicEndpoints` и только её: список читают оба слоя, второй копии быть не должно.
   Не «чинить» `anyRequest().authenticated()` обратно в `permitAll()`,
   не вешать `@Cacheable` поверх проверок прав, не класть секреты в yaml,
   не логировать URL и тела с паролями — адрес к эквайеру и redirect-URL только через
   `ProviderPayloads.urlForLog`, чужой payload в лог и в `provider_response` только через
   `ProviderPayloads.withoutSecrets` (P0-9, §10). Секрет в конфиге — только `${ENV_VAR}` и **без дефолта**:
   ```bash
   grep -rn 'password:\|secret:\|api-token:' --include='*.yaml' auth common directory pbl ecom \
     | grep -v /build/ | grep -v /test/ | grep -v '\${'
   ```
   должен не находить ничего.
5. **Фронтенд: перед правкой убедись, что файл достижим из `routes.tsx`** (§9), а после правки
   прогони `npm run typecheck`. Access-токен — только в памяти (`auth/session.ts`), в `localStorage`
   не класть; роль — только через `parseRole`, без значения по умолчанию; новый закрытый маршрут —
   строка в `auth/routeAccess.ts`. Проверка:
   ```bash
   grep -rn "|| 'SYSTEM_ADMIN'" frontend/src          # ничего
   grep -rn "localStorage" frontend/src               # только session.ts (refresh) и LanguageContext
   ```
6. **Не редактировать применённые Liquibase changeset'ы** — только новые файлы. Исключения делались
   дважды (P0-6, P1-2) и оба раза согласовывались явно: боевых установок не было, база
   пересоздавалась. `runOnChange` и `validCheckSum` не использовать: они маскируют расхождение, а не
   устраняют его.
7. **Не коммитить `build/`, `.gradle/`, `.idea/`** — они в `.gitignore` (P3-3); из `.idea/`
   отслеживается только общий `checkstyle-idea.xml`.
8. **Роли — только через `enum Role`** (§6). Никаких строковых литералов ролей в main-коде:
   ```bash
   grep -rn '"SYSTEM_ADMIN"\|"COMPANY_HEAD"\|"COMPANY_MANAGER"\|"COMPANY_EMPLOYEE"\|"AUDITOR"' \
     --include='*.java' auth common directory pbl ecom | grep -v /build/ | grep -v /test/
   ```
   должен не находить ничего. `Role.valueOf(` — тоже нигде, только `Role.fromValue(`. Наборы ролей —
   `EnumSet`; в `pbl` они уже есть (`PaymentLinkService.READ_ROLES`, `LINK_WRITE_ROLES`,
   `REFUND_ROLES`, `isGlobalReader(role)`) — переиспользуй. Роль может быть `null` — проверка обязана
   приводить к отказу, а не к NPE.
