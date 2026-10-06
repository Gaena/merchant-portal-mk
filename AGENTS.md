# AGENTS.md — контекст проекта Merchant Portal (MP)

> Правила, инварианты и грабли для AI-агентов и разработчиков. Если код и этот файл расходятся,
> прав код, а файл правится в том же изменении.
> Ссылки вида «Р-37» ведут в реестр решений `project_docs/decisions.md`, вида «P2-8» — в архив
> истории работ `project_docs/archive/fix_plan.md`. Истории в этом файле нет: только то, что действует сейчас.

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
├── settings.gradle     # include 'common', 'auth', 'pbl', 'directory', 'ecom', 'txpg-client'
├── common/             # библиотека: security (JWT), журнал аудита, исключения, общие DTO, поиск;
│                       #   testFixtures — контейнер PostgreSQL для тестов
├── txpg-client/        # библиотека: клиент API провайдера (TXPG); testFixtures — двойник эквайера
├── auth/               # :8081 — вход, refresh и logout, пользователи
├── directory/          # :8082 — компании, терминалы, чтение журнала аудита, сверка статусов терминалов
├── pbl/                # :8080 — платёжные ссылки, транзакции, статистика по ссылкам, TXPG, кнопка «Тест»
├── ecom/               # :8083 — выписка провайдера, сводка главной, слепки терминалов и логинов (чтение TXPG)
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
| `plan.md` | открытые задачи | задача заведена — строка; сделана — строка удаляется (Р-105) |
| `modules/auth.md`, `directory.md`, `pay-by-link.md`, `ecom.md` | контракты API: запросы, ответы, отказы | изменился эндпоинт, его ответ или отказы |
| `guides/application_description.md` | обзор архитектуры: модули и связи, схема БД (ER и миграции), фоновые процессы, интеграция с TXPG | новая таблица, колонка, миграция, планировщик, связь между сервисами |
| `guides/deployment_guide.md` | установка и эксплуатация: переменные (полный перечень — §20.1), systemd, nginx, порты | новая переменная, порт, маршрут, сервис |
| `guides/admin_guide.md` | руководство системного администратора: заведение компаний, терминалов и пользователей, связь базы портала с базой провайдера простым языком | изменились экран или правила заведения, сверка терминалов со справочником провайдера |
| `guides/technical_handover.md` | техпаспорт для заказчика; правила и словарь событий аудита (§4.4) | новое событие аудита или видимая заказчику функция |
| `external/TXPG-client-side-integration.md` | контракт MilliKart «Client side integration» v0.1.3 | внешний документ, не правится |
| `external/NON-PSP Ecom.postman_collection.json` | вызовы API шлюза MilliKart на тестовом стенде (не наш `ecom`) | внешний документ, не правится |
| `archive/fix_plan.md`, `archive/code_review.md` | история работ до 29.09.2026 и ревью от 14.08.2026 | заморожены |

Журнала работ нет (Р-105): что сделано и почему — в сообщении коммита, принятое решение — в
`decisions.md`. Правила и известные ограничения — здесь, в §10. Postman-коллекции сервисов:
`auth/Auth.postman_collection.json`, `directory/Directory.postman_collection.json`,
`pbl/Pay-By-Link.postman_collection.json`, `ecom/Ecom.postman_collection.json`; токен берётся запросом
Login из Auth-коллекции. Контракт эквайера — источник словаря статусов заказа
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
| Resilience4j | 2.2.0 — на вызовах клиента провайдера | `txpg-client/build.gradle` |
| SpringDoc OpenAPI | 2.5.0 | `common/build.gradle` |
| Caffeine | 3.1.8 — только счётчики лимита входа (`LoginRateLimiter`) | `common/build.gradle` |
| Liquibase | из BOM | — |
| PostgreSQL | **16** — в проде и в тестовом контейнере (Р-73); часть тестов на H2 (§11) | `project_docs/guides/deployment_guide.md` §4.5, `PostgresTestContainer` |
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
| axios | `^1.7.9` · recharts 2.15.2 · xlsx `^0.18.5` · qrcode-generator 2.0.4 |
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
./gradlew :pbl:test             # тесты одного модуля (:common, :txpg-client, :auth, :directory, :ecom)
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
`auth`, `directory` и `pbl` стартуют в любом порядке (P1-2): каждый changeset общей таблицы обложен
своим `<preConditions>`. `ecom` на пустой базе — только после `directory` или `pbl`: `terminals` и
`audit_logs` он не создаёт (`project_docs/guides/application_description.md` §4.2).

⚠ **Без переменных окружения сервис не стартует, и дефолтов у секретов нет.** `DB_PASSWORD` и
`JWT_SECRET` нужны всем четырём сервисам, причём `JWT_SECRET` — байт в байт одинаковый, иначе
токен от `auth` не проходит в остальных. `pbl` дополнительно требует `PBL_BASE_URL`,
`PROVIDER_GATEWAY_BASE_URL` и `PROVIDER_API_BASE_URL` (P1-10; те же два — и `ecom`, Р-124), `RECEIPT_PROVIDER_NAME`
и `RECEIPT_PROVIDER_TAX_ID` (реквизиты провайдера на чеке, Р-130), `ecom` —
`ECOM_TXPG_URL`, `ECOM_TXPG_USERNAME` и `ECOM_TXPG_PASSWORD`; `directory`, `pbl` и `ecom` —
`CREDENTIALS_ENCRYPTION_KEY`, одно значение на все три (ключ AES-256 паролей компаний к провайдеру, Р-93). Полный перечень —
`project_docs/guides/deployment_guide.md` §20.1 (шаблон — `.env.example`), запись в `mp.env` — там же, §8.3,
процедура первого запуска — §20.

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
   | `users`, `refresh_tokens`, `password_history` | `auth` | — |
   | `user_terminals` | `auth` (создаёт и пишет), `directory` и `pbl` (создают, если ещё нет) | терминалы сотрудника (Р-131): `auth` читает `terminals`, чтобы проверить компанию терминала; `directory` и `pbl` строят по ним скоуп сотрудника, `directory` снимает назначения терминала, перенесённого в другую компанию |
   | `companies` | `auth` (создаёт) + `directory` (дополняет), `pbl` (создаёт, если ещё нет, и добавляет колонки кредов) | `auth` читает название нативным запросом для поиска пользователей; `pbl` и `ecom` — креды компании к провайдеру (Р-93, Р-124); `ecom` — логин компании для скоупа выписки (Р-97) |
   | `terminals` | `directory` (создаёт и дополняет), `pbl` (создаёт, если ещё нет; `terminal_rid`, если ещё нет), `ecom` (`status_source`, `merchant_rid`, если ещё нет); внешний ключ на `companies` — `auth` | `pbl` читает напрямую, минуя REST; `ecom` — терминал мерчанта заказа выписки (Р-124) |
   | `payment_links`, `transactions`, `transaction_refunds` | `pbl` | `directory` меняет статусы ссылок нативным запросом при блокировке терминала (Р-39); `ecom` ищет в `transactions` операцию портала по номеру заказа выписки (Р-124) |
   | `audit_logs` | `directory`, `auth`, `pbl` — каждый с преконтролями, создаёт стартовавший первым | пишут все четыре сервиса через `common`; читает `directory` (`GET /audit-logs`) |
   | `provider_terminals` | `ecom` | `directory` читает нативным запросом: справочник формы и заведение терминала (Р-93), сверка статуса, названия, логина и номера |
   | `provider_logins` | `ecom` | `directory` читает нативным запросом: проверка логина компании (Р-94), список свободных логинов (Р-95), мерчант терминала (Р-96); `ecom` строит по нему скоуп выписки (Р-97) |

3. **Разделение ответственности:** `auth` не управляет компаниями и терминалами; `directory` не
   выдаёт JWT; к API эквайера — только через `txpg-client` (подключают `pbl` и `ecom`), к базе провайдера —
   только `ecom`, и только на чтение. HTTP между сервисами нет: общее — через общую базу.
   `txpg-client`, как и `common`, о сервисах не знает и бинов не объявляет (Р-122).
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
   | нет обязательного параметра, параметр не того типа (`?page=x`, не-UUID в пути) | 400, с именем параметра, без значения (Р-103) |
   | `UnauthorizedException` | 401 |
   | `InvalidStateException` | **403** (используется как «доступ запрещён») |
   | `ResourceNotFoundException` | 404 |
   | `ConflictException`, `OptimisticLockingFailureException` | 409 |
   | `DataIntegrityViolationException` — ограничение базы: гонка «проверил — вставил», значение шире колонки | 400 при SQLState класса 22 (длина, формат), иначе 409; текст драйвера — ни в ответ, ни в лог |
   | `HttpMediaTypeNotAcceptableException` — клиент не принимает JSON (`Accept`) | **406** без тела: метод уже выполнен, 500 звал бы повторить |
   | `PaymentOutcomeUnknownException` | **502** |
   | `CallNotPermittedException` — открыт circuit breaker к эквайеру, только `pbl` (`AcquirerUnavailableHandler`) | **503**: вызов не ушёл, денег не двигал (Р-103) |

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
| `PUBLIC_API` | `/api/v1/auth/**` (`/login`, `/refresh`, `/logout`, `/change-password`), `/api/v1/payment-links/*/open`, `/api/v1/payment-links/redirect/**` | ничем — публичны по смыслу |
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
  стало бы администратором (`RoleTest.fromValue_isCaseSensitive`, не удалять);
- **токен без claim `role` или `sub` — 401** в `JwtAuthFilter`, умолчаний не подставлять: роль по умолчанию
  дала бы права, которых в токене нет, а логин `system` подписал бы журнал именем автоматических действий.

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
| `POST/PATCH/DELETE /companies`, `GET /companies/provider-logins` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /terminals`, `/terminals/{id}`, `/terminals/options` | ✅ все | ✅ своя | ✅ своя | ✅ назначенные | ✅ все |
| `POST /terminals` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `PATCH /terminals` | ✅ | ✅ своя | ✅ своя | ❌ | ❌ |
| креды компании к провайдеру: задать и сменить в `POST/PATCH /companies`, логин в ответе | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /acquiring/terminal-checks/{terminalId}` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /audit-logs` | ✅ все | ✅ своя | ✅ своя | ❌ | ✅ все |
| `POST/PATCH /payment-links` | ✅ | ✅ | ✅ | ✅ назначенные | ❌ |
| `GET /payment-links`, `/{id}`, `/{id}/transactions` | ✅ все | ✅ своя | ✅ своя | ✅ назначенные | ✅ все |
| `GET /transactions`, `/{id}`, `/{id}/status`, `GET /dashboard/summary` | ✅ все | ✅ своя | ✅ своя | ✅ назначенные | ✅ все |
| `POST /transactions/{id}/complete` | ✅ | ✅ | ✅ | ✅ назначенные | ❌ |
| `POST /transactions/{id}/refund` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `POST /transactions/{id}/resolve-outcome` (Р-123) | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /ecom/transactions` и вложенные | ✅ мерчанты всех наших компаний | ✅ своя | ✅ своя | ✅ своя | ✅ мерчанты всех наших компаний |
| `POST /ecom/transactions/{orderId}/refund` (Р-125) | ✅ мерчанты всех наших компаний | ✅ своя | ✅ своя | ❌ | ❌ |
| `POST /ecom/transactions/{orderId}/complete` (Р-125) | ✅ мерчанты всех наших компаний | ✅ своя | ✅ своя | ✅ своя | ❌ |
| `POST /ecom/transactions/{orderId}/resolve-outcome` (Р-125) | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /ecom/provider-terminals`, `POST …/sync` | ✅ | ❌ | ❌ | ❌ | ❌ |

- Правка терминалов — `TerminalService.TERMINAL_WRITE_ROLES`; роль проверяется **до** `companyId`,
  поэтому `COMPANY_EMPLOYEE`, `AUDITOR` и нераспознанная роль получают 403 и на свои терминалы.
  Заводит терминал только `SYSTEM_ADMIN`, выбором из справочника провайдера (Р-93).
  Терминал, выключенный синхронизацией с провайдером, вручную не включает никто (Р-66).
- **AUDITOR — глобальный читатель во всех сервисах** (Р-1); в `pbl` — через
  `PaymentLinkService.isGlobalReader`. На запись это не влияет.
- Скоуп компании в `pbl` идёт через её терминалы — только `TerminalScope`; роль из `READ_ROLES` без `companyId`
  получает пустую страницу, а не отказ.
- **Сотрудник (`COMPANY_EMPLOYEE`) — только назначенные терминалы** (Р-131, `user_terminals`): «назначенные» в
  матрице. Правило — `TerminalScope` в `pbl` (списки, статистика, ворота `validateAccess`) и
  `TerminalService.employeeScope` в `directory`. Назначения читаются из базы на каждом запросе, а не из токена;
  терминал, ушедший в другую компанию, из скоупа выпадает сам. Пустой список — пустой ответ, а не «вся
  компания». Соседний терминал своей компании — 403 (`/status` — 404, как чужой) с отказом в журнале под
  компанией сотрудника. В `ecom` скоуп строит только
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
     ОДНА транзакция целиком (P1-5); отказ (400, 403, 409) её коммитит — найденное опросом остаётся (Р-113):
       1. findWithLockById(id)                [SELECT … FOR UPDATE NOWAIT на строке ссылки; занята → 409]
       2. терминал и статус/срок ссылки
       3. прошлые PENDING-попытки сверяются с эквайером (refreshStatus): у многоразовой — последняя, у
          одноразовой — ещё и все за 30 минут (Р-112). Оплаченная займёт слот, неоплаченная остаётся
          PENDING до сверки; создана уже после начала запроса — это второй одновременный клик → 409
       4. лимит: слот занимают PAID_STATUSES и AUTHORIZED (P1-6); все заняты — сначала опрашивается
          последний холд: снятый банком без списания становится FAILED и слот освобождает. Одноразовая
          с живым заказом (Preparing) или со свежей попыткой, о которой эквайер не ответил, → 409 (Р-112)
       5. POST в TXPG /order?terminalRid=… → providerOrderId + password   [HTTP, блокировка удерживается]
       6. Transaction(status=PENDING); пароль — в provider_password, в provider_response
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
| `PartPaid`, `Cancelled`, `Canceled`, `Refused`, `Closed` | `SETTLED_OTHER` | не меняется, WARN, `FAILED` по таймауту запрещён; исключение — холд, снятый банком без списания (`Closed` после `Authorized`, `ProviderOrderDetails.isReleasedAuthorization`): `FAILED` с причиной «Authorization released by the acquirer without capture» | §5.8.8, Р-75 |
| всё остальное, `null`, не-строка | `UNKNOWN` | не меняется, WARN, `FAILED` по таймауту запрещён | — |

**Три суммы у транзакции** (P0-8) — не путать:

| Поле | Что значит | Когда заполняется |
|:---|:---|:---|
| `amount` | авторизованная сумма, история операции | всегда, после capture **не меняется** |
| `capturedAmount` | сколько реально списано с карты при клиринге | только DMS-capture; у SMS `null` |
| `refundedAmount` | сколько уже возвращено | при возвратах, по умолчанию `0` |

Потолок возврата — `refundableBase(tx)` = `capturedAmount`, а при `null` (SMS) — `amount`.
Частичный capture остаётся в статусе `SUCCESS`; отдельного статуса под него нет.

**Провайдер:** `AcquiringClient` с **единственной** реализацией — `TxpgAcquiringClient` в модуле
`txpg-client` (Р-122); бин с адресами объявляют `pbl` и `ecom` (свои `AcquiringClientConfig`), ссылку в заказ переводит
`ProviderOrders`. Стаба в боевой сборке нет, тестовый двойник — в testFixtures модуля (§11).
`@CircuitBreaker(name="acquiring")` висит на четырёх боевых методах, `@Retry(name="acquiring")` —
только на `createEcomOrder` и `getOrderStatus`: повтор остальных превращается в деньги (P0-7).
`checkOrderCreation` (кнопка «Тест») — без обоих (Р-70). Параметры — в
`pbl/src/main/resources/application.yaml`, в аннотациях их нет. Все вызовы — с логином и паролем
**компании** терминала, не терминала (Р-93): их отдаёт `ProviderCredentialsService.forTerminal`, у
компании без кредов — 400 до провайдера. Заказ создаётся на терминале провайдера:
`POST /order?terminalRid=<terminals.terminal_rid>`; терминал без номера — 400 до провайдера (Р-96). Клиент
одноразовой ссылки уходит в `order.tdsPresetAreq` (`cardholderName`, `email`, `mobilePhone` как `cc` и
`subscriber`) только заполненными полями; у многоразовой клиента нет.

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
транзакций в `PENDING` с `createdAt` между `now - give-up-age` и `now - min-age`, давно не опрашиваемые
первыми (`last_reconciled_at NULLS FIRST`, Р-110): отметка ставится до опроса и в своей транзакции, иначе
строки, которые сверка закрыть не может, занимали бы весь пакет каждый проход. Каждую — в своей
транзакции (`REQUIRES_NEW`) и под замком её ссылки: занятая — до следующего прохода (Р-109). `FAILED` по
`max-age` — **только** при `NON_FINAL`; `UNKNOWN` и
`SETTLED_OTHER` остаются `PENDING` для человека. `AUTHORIZED` сверка не трогает: живой холд —
легитимное состояние покоя. Параметры — `pbl.reconciliation.*`.

**Планировщики** (их шесть: по два в `pbl` и `auth`, по одному в `ecom` и `directory`) — таблица с
расписаниями и выключателями в `project_docs/guides/application_description.md` §9. В тестах все
выключены (§11).

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
  В каждой строке — MDC: `traceId` (запрос — `TraceIdFilter`; прогон планировщика — `SchedulerRun.start`
  первой строкой `@Scheduled`-метода), `clientIp` (`ClientIpFilter`), `user` (`JwtAuthFilter`); сообщения
  эти поля не повторяют. Уровни (Р-98): ERROR — только то, что требует человека сейчас (исход неизвестен,
  сбой, потерянная запись журнала); ожидаемый отказ — истёкший токен, отказ провайдера, 4xx — не ERROR и
  без стектрейса; чтения и тела ответов провайдера — DEBUG; событие, повторяющееся каждый проход
  планировщика, — один раз, а не на каждый проход. Стектрейс одного сбоя печатается один раз.
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
- UI — **только MUI**. Tailwind и shadcn/Radix удалены (14.09.2026) — не добавляй ни того,
  ни другого; стили — `sx` и `CssBaseline`, своих CSS-файлов нет.
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
  `cancelConfirmTitle`/`Text`, `refundAction`/`refundTitle`/`confirmRefund`,
  а диалоги рядом дублировали их английским текстом прямо в JSX.
- Алиас `@` → `frontend/src` (`vite.config.ts:23`).

---

## 9. Фронтенд

**Точка входа:** `src/main.tsx` → `src/app/App.tsx`, живо всё дерево `src/app/**`.
**Перед правкой файла проверь, что он достижим из `routes.tsx`.** Удалённый мёртвый код (второй
axios-клиент, shadcn/Radix, Tailwind, страницы на моках, страница списка операций `/transactions`
с `FilterPanel`/`StatsOverview`/`TransactionTable`, OTP-диалог на входе) и зависимости генератора
не возвращать.

**Роутер создаётся один раз, на уровне модуля** (`routes.tsx` экспортирует `router`). Общего
состояния данных в `App.tsx` нет: каждая страница грузит своё. Пересоздание `createBrowserRouter`
в `useMemo` от состояния оставляло по `popstate`-listener'у на каждый рендер.

**Маршруты** (`src/app/routes.tsx`): `/login`, `/` (HomePage),
`/transactions/ecommerce`, `/transactions/ecommerce/:orderId`, `/transactions/:id`, `/pay-by-link`,
`/pay-by-link/:id`, `/companies`,
`/terminals`, `/users`, `/audit-logs`, `/settings`, `*` (`NotFoundPage`). На `/` и `/login` стоит
`errorElement` (`RouteErrorPage`). Списка операций портала нет (Р-65): операции показываются на
главной и под ссылками, карточка `/transactions/:id` грузит себя сама (`GET /transactions/{id}`)
и после возврата или списания перечитывает.

**Авторизация во фронтенде:**

| Файл | Что делает |
|:---|:---|
| `src/app/auth/session.ts` | хранилище: access-токен — модульная переменная (**только память**), refresh — `localStorage['mp_refresh_token']`, момент последнего действия пользователя — `localStorage['mp_last_activity']` (общий для вкладок), профиль `{ email, role, companyId? }` (email и companyId — из claims JWT, роль — из поля `role` ответа через `parseRole`); `applyLoginResponse` fail-closed, `clearSession`, подписка для `useSyncExternalStore`; событие `storage` гасит сессию в других вкладках при выходе |
| `src/app/auth/idle.ts` | выход по простою (PCI DSS 8.2.8, Р-99): 15 минут без ввода — `logout` и сообщение на форме входа. Действием считается только ввод пользователя, не запросы к API. Пока пользователь работает, пара обновляется, если старше 5 минут: сервер гасит refresh-токен через 20 минут без обновления (`AUTH_REFRESH_TTL`) |
| `src/app/api/client.ts` | request-интерсептор берёт токен из памяти (к `/api/v1/auth/*` не прикладывает); response-интерсептор на 401: `/login`, `/refresh`, `/logout` — не трогать; уже повторяли — `clearSession`; иначе `refreshSession()` (**single-flight**, один промис на все параллельные 401) и повтор запроса. 401 от `/refresh` и нераспознанная роль сбрасывают сессию, сетевая ошибка — нет |
| `src/app/context/AuthContext.tsx` | `AuthProvider`: при загрузке с refresh-токеном показывает загрузку и зовёт `/refresh` (сессия восстанавливается без формы логина) — только если последнее действие было меньше 15 минут назад, иначе гасит токен (`POST /logout`) и показывает форму входа; `login` → `applyLoginResponse` (пароль, заданный не владельцем, — `AuthError('PASSWORD_CHANGE_REQUIRED')`, и форма входа просит сменить его: `changePassword`, Р-100); `logout` — сброс состояния сразу, `POST /logout` вдогонку (ошибка логируется). `isAuthenticated` — по access-токену в памяти |
| `src/app/auth/routeAccess.ts` | **единственная** раскладка «маршрут → роли» (`/users`: `SYSTEM_ADMIN`, `COMPANY_HEAD`; `/companies`: `SYSTEM_ADMIN`, `AUDITOR`; `/audit-logs`: `SYSTEM_ADMIN`, `AUDITOR`, `COMPANY_HEAD`, `COMPANY_MANAGER`; остальное — всем вошедшим). Читают `RoleRoute` и `Sidebar` |
| `src/app/auth/actionAccess.ts` | роли **действий**, зеркало констант сервисов: `TERMINAL_CREATE_ROLES` и `TERMINAL_WRITE_ROLES` (`TerminalService`). Кнопка видна ровно тогда, когда бэкенд её примет (Р-62); меняешь набор на бэкенде — меняй и здесь. Возврата и списания здесь нет: их кнопки решает сервер (`actions` операции, Р-123) |
| `src/app/auth/guards.tsx` | `ProtectedRoute` (→ `/login`, адрес кладётся в `state.from`), `PublicOnlyRoute` (→ `state.from` или `/`; `returnPathFrom` принимает только свой относительный путь), `RoleRoute` (роль не подходит → `ForbiddenPage`, не редирект и не белый экран) |

Клиентские guard'ы — **UX, а не безопасность**: права проверяет бэкенд. Эндпоинта `/me` нет —
вместо имени показывается email (меню аватара). Шапка показывает, кто вошёл: название компании и роль, у
`SYSTEM_ADMIN` и `AUDITOR` — только роль. Название — одиночный `GET /companies/{companyId}` по claim токена
(`hooks/useCompanyName.ts`, им же читает `SettingsPage`); не загрузилось — только роль. Роль словами — только
`roleLabel` из словаря.

**Выдуманных данных на экранах нет — и не возвращать** (Р-48). Поле, которого нет в API, не
показывается: ни прочерком с выдуманным значением, ни «умолчанием». Полей, которых API по ссылке не
отдаёт (адрес возврата, заметка, стадия DMS, карта, номер операции, адрес плательщика, «отправлено по
почте»), в `PaymentLink` нет; карта и стадия DMS — у операций ссылки, списание — с карточки операции.
`HomePage` ничего не считает сама: сводку отдаёт `GET /api/v1/ecom/dashboard/summary`, последние заказы —
первая страница выписки за тот же период с `size: 10`. На `SettingsPage` живы только название
компании (одиночный `GET /api/v1/companies/{id}` по `companyId` из токена) и язык. Удалённые метрики,
вкладки настроек и сочинённую историю не возвращать (P3-6, P3-7).

**Главная — весь эквайринг компании** (Р-91): оплаты картой по всем мерчантам логина компании по выписке
провайдера (`project_docs/modules/ecom.md` §2.8, Р-97), у `SYSTEM_ADMIN` и `AUDITOR` — по мерчантам логинов всех компаний. Цифры совпадают с
вкладкой E-commerce за тот же период: те же заказы (по дате создания) и те же правила денег. Каждое открытие
главной — проход по периоду на боевой базе шлюза; период по умолчанию — 7 дней. Общие куски обеих панелей —
`components/DashboardParts.tsx`.

**Статистика оплат по ссылкам — вкладка «Статистика» страницы Pay by Link** (`components/LinkPaymentsStats.tsx`,
Р-91), сводка `GET /api/v1/dashboard/summary` в `pbl`. У страницы две вкладки: «Ссылки» (по умолчанию) и
«Статистика»; вкладка — в адресе (`/pay-by-link?tab=stats`), переключение заменяет запись истории. Статистика
монтируется только на своей вкладке — сводку не запрашивают, пока её не открыли; период хранит страница,
кнопка создания ссылки — в шапке над вкладками. Деньги — всегда с валютой. Колонки `currency` у `transactions` нет,
она на `payment_links`, поэтому сводного числа поверх валют нет: каждая валюта — свой блок. Выручка —
`COALESCE(captured_amount, amount)` по `PAID_STATUSES` платежей, **созданных** в окне, минус возвраты,
**проведённые** в окне (`transaction_refunds.refunded_at`, Р-89): возврат по старому платежу уменьшает
сегодняшнюю выручку и не переписывает прошлые дни. Строка в `transaction_refunds` пишется в
`PaymentLinkService.refund` вместе со свидетельством в `mpRefunds`, с тем же моментом.

В статистике по ссылкам период выбирается (сегодня, 7, 30, 90 дней; потолок сводки — 92), разбивка статусов
подписана «попытки оплаты»: каждое открытие ссылки — операция, брошенная становится `FAILED`. Графика по
часам нет, ссылки разложены по статусу. Воронка ссылок и время до оплаты (Р-128) — по ссылкам, **созданным**
в периоде, с их попытками в любое время: недавний период дорастает. «Карта отправлена» — отметка
`transactions.card_submitted`, её ставит только опрос статуса и не снимает; время до оплаты — одноразовые
ссылки, от создания до начала оплаченной попытки, медиана, а не среднее. Терминал без логина и имени подписывается прочерком, а не номером
(Р-81) — это правило `terminalLabel` для всех экранов.

**Идентификаторы и терминал на экранах** (Р-58, Р-59). Номер заказа провайдера и `ridByMerchant` —
первыми, внутренний UUID — вторым. Терминал подписывается номером у провайдера (`terminalRid`, Р-96), у
заведённых до него — логином; имя — пояснением; порядок живёт
только в `utils/terminals.ts`, разбор операции из ответа — только в `utils/mapTransaction.ts`.
`merchantReference` на экранах не показывается (Р-60).

**Вкладка E-commerce — выписка сервиса `ecom`, а не операции портала** (Р-65, Р-80). Свои запросы и
разбор — `utils/ecom.ts`, восемь статусов — `types/ecom.ts` (к шести статусам ссылок добавлены
`PARTIALLY_PAID` и `CANCELED`), своя карточка заказа по номеру у провайдера — `components/EcomOrderDetails.tsx`
(Р-120): из выписки — панелью поверх неё (`?order=` в адресе, «Назад» браузера её закрывает), с главной и по
прямой ссылке — страницей `/transactions/ecommerce/:orderId`. Переходом на страницу из выписки её не открывать:
выписка потеряет фильтры и строки и снова пройдёт по базе шлюза. Общий список операций
из `App` в неё не передаётся; фильтровать и считать итоги на экране нельзя — это делает сервер.
Фильтры статуса и типа оплаты (Р-87) — тоже серверные: тип — условие в SQL по парам
`EcomOperationKind`, статус сервис отбирает после сборки заказа с потолком `status-scan-limit`, поэтому
страница с фильтром по статусу бывает короче размера или пустой, но с курсором — экран пишет «в
просмотренных заказах нет, показать ещё», а не «ничего нет». Разбивка итогов по статусам — только цифры,
не кнопки: фильтр статуса стоит в форме.
**Фильтры выписки уходят в запрос только по «Применить» или Enter** (Р-88): на экране черновик, запросы
строятся из применённой копии. Не возвращать применение на каждое изменение поля — отмена в браузере
не останавливает запрос на боевой базе шлюза. Сразу применяется только размер страницы.

**Форма заведения терминала** (Р-80, Р-93, Р-96): только `SYSTEM_ADMIN`, выбором из терминалов мерчантов
логина выбранной компании (`GET /api/v1/terminals/provider-terminals?companyId=`); название, логин и номер
терминала приходят из справочника, пароля у терминала нет. Креды к провайдеру — у
компании: логин и пароль в форме её создания, правка — окном «Редактировать компанию» вместе с названием
и статусом, с подтверждением; переключателя статуса в списке нет.
Логин не вводится, а выбирается из свободных логинов справочника (`GET /companies/provider-logins`,
Р-95), рядом — «Обновить справочник»; ручного ввода нет, серверная проверка (Р-94) остаётся.

**Списки терминалов — два разных запроса.** Экрану управления — постраничный `GET /api/v1/terminals`;
всем, кому нужна подпись или выпадающий список, — лёгкий `GET /api/v1/terminals/options` со всеми
терминалами, включая заблокированные (форма ссылки сама берёт только `ACTIVE`).

**Опасные действия — только через подтверждение.** Удаление, смена статуса, блокировка и
разблокировка, сохранение правки терминала и компании, смена кредов компании к провайдеру, отмена ссылки,
списание холда и возврат не уходят на сервер по клику: сначала окно, где названы **эта** запись и
последствия именно этого действия.
Окно одно — `app/components/ConfirmDialog.tsx` (§10). Денежные вызовы живут только внутри окон:

```bash
cd frontend/src
grep -rn "status: 'CANCELED'\|/refund\|/complete" app/pages/*.tsx app/components/*.tsx   # три строки, все внутри окон
grep -rn "autoFocus" app/pages/*.tsx                                 # ничего — правило внутри компонента
```

Окна-формы (создание пользователя, компании, терминала и ссылки, правка терминала, «поделиться
ссылкой») — отдельные, к подтверждениям не относятся.

**Прокси на бэкенд** (`vite.config.ts`, только dev): `/api/v1/auth`, `/api/v1/users` → 8081 ·
`/api/v1/companies`, `/api/v1/terminals`, `/api/v1/audit-logs` → 8082 · `/api/v1/payment-links`,
`/api/v1/transactions`, `/api/v1/dashboard`, `/api/v1/acquiring` → 8080 · `/api/v1/ecom` → 8083.
Новый префикс — сюда и в конфиг nginx (`project_docs/guides/deployment_guide.md` §11).

**Моков нет.** Форматтеры сумм и дат — `utils/format.ts` (бывший `mockData.ts`). `formatCurrency`
без валюты печатает число без знака валюты, а не AZN: у операции портала валюта приходит со
ссылки, у заказа выписки — сырая колонка провайдера и бывает пустой.

**Компания в формах** (терминалы, пользователи): выбирает только `SYSTEM_ADMIN`; остальные роли
работают в своей компании из claim `companyId` токена, списка `GET /companies` у них нет (403),
своя компания читается одиночным `GET /companies/{id}`. Правка терминала шлёт в PATCH только
изменившиеся поля — иначе журнал аудита пишет «Name changed from X to X».

**Правка пользователя** (Р-90) — окно формы и `ConfirmDialog` со списком изменений, PATCH только с
изменившимися полями; открывается кликом по строке, удаление — кнопкой в том же окне, со своим подтверждением.
Строка кликабельна и кнопка удаления видна по правилам `UserService.validateWriteAccess`
(администратор — всем, руководитель — себе и ролям `COMPANY_MANAGER`/`COMPANY_EMPLOYEE`); удалить себя и
поменять себе роль или статус со страницы нельзя. Компанию меняет только администратор, роли в списках
создания и правки — только выдаваемые. Себя страница узнаёт по логину (`sub` токена): id пользователя
фронтенд не хранит.

**Форма ссылки** шлёт `expiresAt` из выбранного срока (1 ч … 30 дней, потолок бэкенда 90) и
клиента только заполненными полями (`null` вместо пустых) и только у одноразовой ссылки — у
многоразовой блока «Клиент» нет, бэкенд её клиента отвергает (Р-96). Телефон — только азербайджанский
(`+994…`, `994…`, `0…` и 9 цифр), проверка — зеркало `CustomerPhone`. Полей «redirect URL», «заметка»,
«отправить письмо» на форме нет — бэкенд их не принимает и писем не шлёт. QR-код адреса ссылки —
`components/LinkQrCode.tsx` на карточке и в окне «Поделиться» (Р-127): строится в браузере, всегда чёрным
по белому и с белым полем — иначе его не читают сканеры. Список ссылок — серверная
страница с серверным фильтром по статусу; поиска нет, пока его нет в `GET /payment-links`.

**Кнопки возврата и списания на карточке операции решает сервер** (Р-123): ответ `GET /transactions/{id}`
несёт `actions` — видна ли кнопка, активна ли, код причины и потолок суммы. Экран показывает и переводит
причину (`transactions.detail.moneyReasons`), своих правил и своей арифметики денег не держит: сумма
возврата — `actions.refund.maxAmount`. Неподтверждённую операцию тоже помнит сервер (`actions.unresolved`);
итог отмечает только `SYSTEM_ADMIN`.

---

## 10. Грабли — прочитать до первой правки

### Известные ограничения

Осознанные и не исправленные. Разборы — в `project_docs/decisions.md`, в архиве
`project_docs/archive/fix_plan.md` и в истории git. Задачи, которые решено сделать, — `project_docs/plan.md`.

- **Общая база и суперпользователь.** Все сервисы в одной базе и ходят в неё под `postgres`; есть
  кросс-модульные нативные запросы (`auth`, `pbl` и `ecom` → `companies`, `directory` → `payment_links`,
  `provider_terminals` и `provider_logins`). Изоляции между сервисами нет, и права на журнал аудита (Р-42,
  `project_docs/guides/deployment_guide.md` §5.1a) пока ни на что не влияют. `DATABASECHANGELOG` одна на всех —
  поэтому id changeset'ов несут имя модуля.
- **Терминалы без номера у провайдера платежей не принимают** (Р-96): `terminals.terminal_rid` пуст у
  заведённых до Р-96 без справочника — создание ссылки, открытие и «Тест» по ним дают 400. Логин
  терминала остаётся в базе: на нём держится сверка статусов.
- **`directory` читает `provider_terminals.terminal_rid`** — колонку добавляет `ecom` (004): обновлять
  `ecom` вместе с `directory` или раньше, иначе заведение терминала и сверка падают на запросе.
- **Без `ecom` компанию не завести, а выписку не увидеть** (Р-94, Р-97): логин компании проверяется по
  слепку логинов мультимерчантов, который снимает только `ecom`; нет слепка или он пуст — отказ. По тому же
  слепку строится скоуп выписки и главной: пустой слепок — пустая выписка у всех, связи, изменённые у
  провайдера, видны со следующей синхронизацией.
- **Ключ шифрования паролей компаний не ротируется.** Новый `CREDENTIALS_ENCRYPTION_KEY` делает
  сохранённые пароли нечитаемыми (`CredentialCipher.decrypt` — 500): пароли компаний вводят заново.
- **Void нет.** Снять DMS-холд `AcquiringClient` не умеет: брошенный `AUTHORIZED` занимает слот
  ссылки, пока банк не отпустит холд сам. Гасить его локально нельзя.
- **Контракт TXPG не описывает DMS.** Статусы `Authorized` и `Cleared` взяты из исходного кода и
  контрактом не подтверждены; какой `description` у записей холда и клиринга в `order.trans[]` —
  неизвестно, поэтому `Purchase` при выборе записи — предпочтение, а не фильтр. В базе шлюза DMS
  пишется двумя словарями: `Purchase`/`Auth` и `Purchase`/`Clearing` на стенде, `Authorization`/`Auth`
  и `Capture`/`Charge` на контуре из `test.env`, который устроен как прод (заказ 1003, Р-86). Второй
  словарь в контракте не описан; `clearamt` у `Capture`/`Charge` положительный. Новая пара списания —
  только в `EcomOperationKind.CAPTURE_SIGNS`, SQL выписки строится из него.
- **Пароль заказа уходит в адресе** `GET /order/{id}` (Р-25): там его требует контракт. Списание и возврат
  (`exec-tran`) идут по номеру заказа и кредам компании, без пароля (Р-121).
- **Возвраты и реверсалы, сделанные мимо портала, не отражаются.** `SETTLED_OTHER` оставляет
  `PENDING` для ручного разбора, а по уже `SUCCESS` внешний возврат не замечается вовсе: суммы из
  `order.trans[]` не читаются.
- **Вход.** Верный пароль к заблокированному аккаунту отличим от неверного (принято при P3-Auth);
  лимит попыток по адресу стоит только там, где проверяется пароль: `/api/v1/auth/login` и
  `/api/v1/auth/change-password`.
- **Правки платёжной ссылки не версионируются** — остаются запись `UPDATE` в журнале и строка в логе.
- **Живая сессия держит одноразовую ссылку до 10 минут** (Р-112): плательщик, закрывший платёжную страницу,
  получает 409, пока провайдер не закроет заказ, — повторно страницу заказа провайдер не открывает. У
  многоразовой сессии слот не занимают: одновременные оплаты последнего слота превышают `maxPayments` на
  число этих оплат.
- **Шаг «карта отправлена» воронки ссылок на стенде не проверен** (Р-128). Признак — запись в `order.trans[]`
  ответа провайдера; что брошенный до ввода карты заказ приходит без записей, контракт не описывает. Если
  провайдер пишет запись уже при открытии страницы, шаг совпадёт с «открыта».
- **Возвраты до P1-8b сводка не вычитает.** У них нет записи в `mpRefunds`, поэтому миграция
  `pbl/010` не перенесла их в `transaction_refunds`, а дату им не выдумываем (Р-48). `hourlyTotals`
  сводка считает, но экран его не рисует.
- **Главная нагружает боевую базу шлюза** (Р-91): каждое открытие — проход по периоду (сводка) и страница
  выписки (последние заказы). Кэша нет; возвраты на главной — по заказам периода, как в выписке, а не по
  дате возврата, как в статистике по ссылкам.
- **Ручная блокировка терминала в MilliKart не уходит**: в портале он «не выпускает платежи», у
  провайдера — работает. Обратное направление есть — синхронизация (Р-66).
- **Статус компании ничего не останавливает.** `INACTIVE` не проверяет ни один сервис; чтобы
  остановить платежи компании, блокируют её терминалы.
- **Карточка ссылки** не показывает карту, номер транзакции и адрес плательщика (полей нет в API).
- **Английский текст в JSX** остался на `PayByLinkDetailPage`, `TransactionDetailPage`,
  `CompaniesPage` и `AuditLogsPage` рядом с переведёнными диалогами.
- **Неподтверждённую операцию разрешает человек** (Р-123): `SYSTEM_ADMIN` сверяет её с провайдером и
  отмечает «прошла» или «не прошла». «Прошла» записывается без идентификаторов эквайера — их неоткуда
  взять. Сам портал исход не выясняет: `/status` по операции в терминальном статусе эквайера не спрашивает.
- **Поиска по платёжным ссылкам нет**: `GET /payment-links` принимает только `terminal` и `status`;
  прежний клиентский поиск искал по одной странице и снят.
- **SQL `ecom` из портала на Oracle провайдера ещё не исполнялся** (Р-74…Р-79, Р-97). Выписка проверена
  тестами на выгрузке стенда (114 заказов во всех статусах, с возвратами и реверсалами) и 15.09.2026
  исполнена на локальном `oracle-free` со схемой `TXPG`, собранной по SQL провайдера, и той же
  выгрузкой; запрос справочника терминалов — там же, вручную, скоуп по мерчантам (Р-97) — там же
  25.09.2026. Chargeback не разбирается.
- **Потолок возврата заказа выписки — по базе шлюза** (Р-125): если `ecom` читает реплику с отставанием, сразу
  после возврата потолок ещё прежний. Лишнее отвергнет провайдер — его потолок свой; строка попытки защищает
  только от одновременных возвратов.
- **Выписка администратора — не больше 1000 мерчантов** (Р-97). Скоуп уходит в Oracle списком
  `m.rid in (...)`, а Oracle принимает в списке не больше 1000 значений (ORA-01795): у `SYSTEM_ADMIN` и
  `AUDITOR` в списке мерчанты всех компаний, и за этим порогом их выписка и главная падают.
- **Тесты.** Часть интеграционных тестов по-прежнему на H2 (§11).
- **Гигиена, до которой не дошли:** `directory/settings.gradle` с собственным `rootProject.name`,
  мёртвая `springBootVersion` в `directory/build.gradle`, неиспользуемый бин `RestTemplate` в `pbl`.
  Docker-образов, `docker-compose.yml` и конфигурации CI в репозитории нет.

### Тонкости, на которых легко ошибиться

**Деньги и эквайер**

- **Денежную операцию не повторять, пока исход неизвестен.** На `completeDms` и `refund` нет
  `@Retry`; 502 значит «неизвестно», и возврат поверх, возможно, ушедших денег — двойной возврат. Запрет
  держит сервер (Р-123): строка `money_operation_attempts` пишется до отправки эквайеру своей транзакцией,
  удаляется вместе с записью итога, а без итога остаётся — и второй возврат или списание той же операции
  получает 409. Строка «идёт» старше 5 минут (`MoneyOperationAttempt.STALE_AFTER`) читается как неизвестный
  исход: сервис упал посреди вызова.
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
    txpg-client/src/main/java/az/millikart/txpg/TxpgAcquiringClient.java
  grep -n '"password", response.order().password()' \
    pbl/src/main/java/az/millikart/pbl/service/OpenLinkService.java
  ```
- **`rrn` и `approvalCode` — в `order.trans[]` (или `order.lastTran`), маска карты — в
  `order.srcToken.displayName`**; ключа `cardNumberMasked` в контракте нет. Читать через
  `ProviderOrderDetails.read`; `regTime` сравнивать как строку, не парсить (P1-16).
- **Чек плательщика — публичная страница** (`redirect.html`, Р-130): открывается по `ridByMerchant` без входа.
  На нём только реквизиты закона (ст. 17.1) и правил ЦБ АР № 12/3 (п. 14.1, 15.5); собирает их только
  `PaymentReceipts`. Первых шести цифр карты, пароля заказа, IP и user agent плательщика там нет и не будет;
  строки без значения не выводятся, умолчаний нет (Р-48). Тексты — азербайджанский, под ним английский.
- **Асинхронного callback от TXPG нет и не будет** (Р-7). Статус дожимается страницей возврата
  (один заход), ручным `/status` и фоновой сверкой — эндпоинт не изобретать.
- **Hibernate auto-flush.** Считаешь после изменения managed-сущности — она уже посчитана: flush
  перед JPQL сбрасывает изменение в БД. Прибавки `+ 1` в `refreshStatus` и `completeDms` быть не
  должно (P1-7).
- **Отказ открытия ссылки коммитит её транзакцию** (`noRollbackFor`, Р-113): до отказа в ней пишется только
  то, что должно его пережить, — опрос эквайера, `EXPIRED`, `COMPLETED`. Опрос в `REQUIRES_NEW` не годится:
  оплата правит строку ссылки, которую держит открытие, и он ждал бы сам себя. Свой отказ — наследником
  `BusinessException`, `InvalidStateException` или `ConflictException` и не из транзакционного прокси: иначе
  транзакция помечена на откат, и вместо отказа клиент получит 500 (`UnexpectedRollbackException`).
- **Открытие ссылки держит блокировку строки на время HTTP-вызова к эквайеру** (P1-5) — осознанно.
  Блокировка берётся с `NOWAIT` (Р-85; таймаут ожидания Hibernate на PostgreSQL не рисует): занятая
  ссылка — сразу 409, а не очередь из соединений пула. Её же берут правка ссылки (PATCH: без замка она не
  видит незакоммиченную попытку и меняет сумму под идущей оплатой, обход Р-31), списание, возврат и опрос
  статуса (`/status`, страница возврата, сверка) — до чтения транзакции (Р-109): опрос без замка записал бы снимок,
  прочитанный до подтверждённого списания, поверх него. Списание и возврат берут его дважды (Р-123): на
  проверку со строкой попытки (`NOWAIT`) и на запись итога по перечитанной операции (`findWithWaitingLockById`:
  после ушедших денег отказывать нельзя); вызов эквайера между ними — без транзакции и замка, операцию держит
  строка попытки. `@Version` на `transactions` не заводить: конфликт
  на коммите откатил бы списание, уже подтверждённое эквайером. Статус терминала читается **после**
  `findWithLockById`: чтение до блокировки оставит невидимой блокировку терминала, закоммиченную под
  чужим замком.
- **Сумма ссылки заморожена после первой попытки оплаты, `ACTIVE` достижим только из `CANCELED`**
  (P2-9, Р-31, Р-32). `AMOUNT_LOCKING_STATUSES` перечисляет статусы, которые правку **запрещают**:
  новое значение `TransactionStatus` по умолчанию запрещает. Переходы — только из
  `ALLOWED_STATUS_TRANSITIONS`; `expiresAt` применяется до `status`.
- **Срок жизни ссылки считается от `created_at`** (P1-9): потолок `pbl.link.max-ttl` в `update`
  меряется от создания, иначе цепочкой PATCH'ей срок продлевается бесконечно.
- **«Платёж состоялся?» — только `TransactionStatus.PAID_STATUSES`** (`SUCCESS`, `REFUNDED`,
  `PARTIALLY_REFUNDED`; Р-49). Им считаются дата оплаты и `currentPaymentsCount`. Возврат не освобождает
  слот и не пересчитывает `COMPLETED`. Слот — `TransactionStatus.SLOT_OCCUPYING_STATUSES`: те же плюс
  `AUTHORIZED` (P1-6); по нему открытие считает свободные слоты, и ниже него не опускается `maxPayments`.
- **Кнопка «Тест» заводит у провайдера настоящий заказ** (Р-70) — с кредами компании терминала (Р-93)
  и только у заведённого терминала. Без `@Retry` и без общего
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
- **Массовый UPDATE `payment_links` мимо Hibernate поднимает `version`** (`PaymentLinkStatusRepository` в
  `directory`, истечение в `pbl`): иначе `pbl`, прочитавший ссылку раньше, сохранит её целиком и вернёт
  прежний статус, и `@Version` этого не заметит. Новый такой UPDATE — с `version = COALESCE(version, 0) + 1`.
- **Статусы терминалов синхронизируются с провайдером** (Р-66). Неудачный опрос и пустой слепок не
  применяются; терминал гасится после трёх пропаданий подряд — любой работающий, и разблокированный
  вручную тоже; ручную блокировку (`BLOCKED` + `MANUAL`) сверка не снимает никогда; выключенный
  синхронизацией включает только она. Отсутствие строки в слепке — «не знаем»,
  а не «выключен»; терминал без `merchant_rid` сверка не касается. Актор в журнале — `system`. Полная таблица
  переходов — `project_docs/modules/directory.md` §3.2. Транзакция — на терминал, журнал — событием после её
  коммита; строка терминала версионирована (`@Version`, Р-115): правку во время прохода сверка не затирает.
  Сохраняя терминал, бери прочитанную из базы сущность: собранная заново с тем же номером без версии — это
  вставка, а не правка.
- **Логин компании — только активный мультимерчант из слепка `provider_logins`** (Р-94):
  `MultiMerchantSys/<login>`, `Active`, хотя бы одна связь `Active` с мерчантом. Проверка — только при
  сохранении (заведение, смена логина); сохранённые логины слепок не трогает. Слепок — все логины
  `MultiMerchantSys` провайдера, а не только наших компаний: новая компания проверяется до сохранения.
- **Креды к провайдеру — у компании, пароля у терминала нет** (Р-93). Пароль компании — только
  шифротекст `CredentialCipher` (AES-256-GCM) и наружу не выходит никогда: ни в ответе, ни в логе, ни
  в журнале; логин в ответе — только `SYSTEM_ADMIN`. `CredentialCipher` — не `@Component`: бин объявляют
  только `directory`, `pbl` и `ecom`, иначе `auth` не стартовал бы без ключа.
- **Название терминала из справочника — провайдера** (Р-67, Р-116): `PATCH` с другим `name` у терминала с
  `merchant_rid` — 400, иначе сверка вернула бы название через 15 минут. Ответ несёт `providerLinked`, окно
  правки по нему гасит поле названия. Терминал без справочника переименовывается.
- **`GET /api/v1/terminals/options` отдаёт и заблокированные терминалы** (Р-45): по снятому с
  обслуживания терминалу подпись старых платежей должна остаться. Фильтрует потребитель.
- **`merchantRid` и `ridByMerchant` — разные идентификаторы** (Р-69). `merchantRid` — мерчант у
  провайдера (`terminals.merchant_rid`), `ridByMerchant` — платёж у мерчанта
  (`transactions.rid_by_merchant`). Пустой `ridByMerchant` из базы провайдера ничем не подменять.
  Третий — `terminalRid` (`terminals.terminal_rid`, `txpg.terminal.rid`, Р-96): номер терминала у провайдера,
  с ним создаётся заказ; у мультимерчанта он и отличает терминалы одной компании.
- **Терминал компании — только мерчанта её логина** (Р-96): заведение и перенос в другую компанию сверяют
  `merchantRid` с активными связями логина компании в `provider_logins`, смена логина компании — мерчантов
  всех её терминалов с активными связями нового логина; правило одно —
  `ProviderLoginSnapshotRepository.activeMerchantRidsOf`. Терминал без `merchantRid` не переносится и при
  смене логина не сверяется. «Один терминал провайдера — одна компания» остаётся.

**`ecom`**

- **Читает чужую базу и только читает** (Р-65). Деньги по заказу выписки двигает API провайдера через
  `txpg-client`, а не запись в базу шлюза (Р-124). Наша PostgreSQL помечена `@Primary` — ей достаются
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
- **Статус заказа — по статусу провайдера, а не по деньгам** (Р-92, требование заказчика). `FullyPaid` —
  успех без сверки суммы, SMS — только статус провайдера, DMS — по деньгам, пока в истории есть
  `clearamt`; `Closed` — по предыдущему статусу. Словарь — только `EcomStatusResolver.byProviderStatus`,
  сверка точная; незнакомое слово — статус по деньгам, а не отказ (Р-20).
- **Справочник терминалов: одна строка на мерчанта, ключ — `merchant.rid`** (Р-79). Не `terminal.rid`:
  по мерчанту выписка находит заказы; `terminal.rid` — отдельная колонка (Р-96). Только логины
  `TerminalSys`: логин `TerminalUser` того же терминала делал мерчанта неоднозначным навсегда. Разные логины у одного мерчанта не применяются — он не
  обновляется и не гаснет. Фильтр `Active` у логина и терминала и есть то, что гасит наш терминал:
  снимешь его — выключенные у провайдера останутся живыми у нас.
- **Синхронизации слепков идут по одной** (Р-119): `sync()` обоих сервисов — `synchronized`, транзакция —
  `TransactionTemplate` внутри замка. `@Transactional` на методе не возвращать: коммит после снятия замка
  снова пускает второй проход к незакоммиченному слепку. Замок в памяти — `ecom` работает одним экземпляром.
- **`Authorized` не значит «денег нет»** (Р-76). При мультиклиринге заказ остаётся `Authorized` и
  после списания, поэтому фильтр Р-71 пропускает `Authorized` с одобренным списанием. Признак списания
  в SQL (`finishedOrdersOnly`) и в `EcomOperationKind.CAPTURE` один — меняются вместе.
- **`getLowIdForTime` не получает будущего времени** — на нём функция не работает (провайдер). Вызов
  только через `lowIdForTime`, где аргумент прижат к `sysdate` базы, а не к нашим часам и поясу;
  верхняя граница окна — только у периодов, закончившихся больше суток назад.
- **Колонки — только из SQL провайдера.** Отсутствующая в схеме колонка роняет всю выписку: каждая
  ссылка `алиас.колонка` во всех запросах к шлюзу сверяется с белым списком `TxpgColumns` (тесты
  репозиториев `ecom`); новая колонка — сначала туда, с источником. `terminalid` у операций, `ridbypmo`,
  `srcemail`, `srcmobile`, `getHighIdForTime` в SQL провайдера нет. `tran.ridbyacq` — только строкой.
  Даты шлюза — местное время `ecom.txpg.zone`: в параметры уходит `LocalDateTime`, не `Instant`.
- **Скоуп — только `EcomScopeService`:** компания → её логин мультимерчанта → активные связи логина в
  `provider_logins` (Р-97). Терминалы портала в скоупе не участвуют. Пустой список — пустая выписка;
  ветки «мерчантов нет, значит показать всё» быть не должно. В SQL скоуп — во всех трёх запросах
  (`merchantScope`): `m.rid in (:merchant_rids)` при `m.id = o.merchantid and m.id = tr.merchantid` в
  join — снимешь второе, и в заказ своего мерчанта попадёт операция чужого. Фильтр пользователя сужает
  этот же список; фильтр из одних чужих мерчантов — пустая выписка без запроса к шлюзу.
- **`o.password` из схемы шлюза не попадает никуда** — ни в выписку, ни в экспорт, ни в логи.
  Период обязателен и ограничен, страница — курсорная (номер последнего заказа страницы).
- **Возврат и списание заказа выписки** (Р-125) — `EcomMoneyOperationService`. Правила — только
  `EcomMoneyActions`: выключенная кнопка — тот же отказ, и к провайдеру он не идёт. Своей строки у заказа в
  нашей базе нет, поэтому замок — строка `provider_order_attempts`, и только вставкой
  (`ProviderOrderAttempt` — `Persistable`): `merge` второй попытки тихо перезаписал бы первую. Вызов провайдера —
  без транзакции; подтверждённый — строка удаляется, деньги у нас не пишутся (их покажет выписка). Неизвестный
  исход снимается сам, когда выписка показывает одобренную операцию того же вида и суммы после отправки
  (`ProviderOrderAttemptService.open`), иначе — `SYSTEM_ADMIN`. Заказ, заведённый порталом, `ecom` не проводит:
  его операция — в `pbl` (`portalTransactionId`).

**Журнал аудита**

- **Как писать.** Успех — `eventPublisher.publishEvent(AuditEvent.of(...))` внутри бизнес-транзакции:
  запись ляжет после коммита (Р-35). Отказ — `auditLogService.logDenied(...)` прямо перед `throw`;
  неизвестный исход денежной операции — `logUnresolved(...)`; оба в своей транзакции. Внутри
  HTTP-запроса любая запись журнала ложится в конце запроса (`AuditOutbox`, Р-85): запись посреди
  транзакции брала второе соединение из пула, и десять одновременных отказов вешали сервис.
  `recordSuccess` напрямую зовут только там, где откатывать нечего: проверка терминала. Ошибку записи
  журнала не пробрасывать.
  Машинерия одна на проект — `az.millikart.common.audit` (Р-41).
- **`entityType` и `action` — только константы `AuditEntity` / `AuditAction`** (P3-2). Новое
  значение — сначала в словарь и в таблицу `project_docs/guides/technical_handover.md` §4.4, потом в код.
  Соглашения: `entityId` у `AUTH` — всегда логин; `"ALL"` — действие над списком; `"NEW"` — отказ
  в заведении терминала, у которого ещё нет номера; смена статуса —
  `BLOCK`/`UNBLOCK`, а не текст в `UPDATE`; `companyId` у отказа — компания актора, кроме отказа,
  который сам выдал бы чужое: статус заказа чужой компании пишется без компании (Р-114); актор
  автоматических действий — `system`.
- **В журнал не попадают пароли, токены и их части.** В `details` входа — только категория отказа;
  логин неудачного входа — недоверенный ввод, обрезается по ширине колонки.
- **Лимит входа пишет в журнал один раз за окно** (`count == maxFailures`) и в базу не ходит: запись
  на каждую отбитую попытку сделала бы защиту усилителем нагрузки. Попытка держит место в лимите адреса и
  свой логин от `LoginRateLimiter.begin` до конца проверки пароля (Р-118): иначе залп проходит проверку
  целиком, а попытки в один логин ждут друг друга на `FOR UPDATE`, держа соединения пула. Счёт в памяти
  одного экземпляра `auth`: у нескольких экземпляров в логин идёт по попытке на каждый.
- **Журнал нельзя править из приложения** (Р-42). У `AuditLogRepository` только `save`, у
  `AuditLogQueryRepository` только `findAll(Specification, Pageable)` — `JpaSpecificationExecutor` не
  наследовать, в нём `delete(Specification)`. `AuditLog` — `@Immutable`, билдер не задаёт `id`: `save`
  записи с существующим `id` сделал бы `merge` и переписал её. Сторожат `AuditLogAppendOnlyTest` и
  `AuditLogQueryRepositoryReadOnlyTest`.

**Spring и ошибки**

- **Ошибка клиента не должна выглядеть как сбой сервера.** Всё, что не перечислено в
  `GlobalExceptionHandler`, падает в `handleUnexpected` — 500 и ERROR со стектрейсом. Добавляя
  эндпоинт, проверь, что Spring бросит на кривом запросе и есть ли обработчик. Строковое поле тела,
  которое ложится в колонку, — с `@Size` по её ширине: `handleDataIntegrity` превратит отказ базы в 400/409,
  но только после всей работы запроса. Где запись идёт после похода к провайдеру (`User-Agent` на `/open`),
  значение обрезается, а не отвергается.
- **Ввод клиента в лог — только проверенный.** Сообщение Jackson о кривом теле цитирует его кусок —
  пароль без кавычек, поэтому `GlobalExceptionHandler` пишет только вид ошибки, место и имя поля;
  `ex.getMessage()` разбора тела в лог не класть. `traceId` из заголовка — только `[A-Za-z0-9._-]{1,64}`:
  поля строки лога позиционные, и значение со скобками подделало бы адрес и пользователя.
- **Внутри транзакции второе соединение из пула не брать** (`dataSource.getConnection()`): при десятке
  одновременных транзакций каждая ждёт `connectionTimeout` (Р-85). JDBC рядом с JPA — через `DataSourceUtils`
  или `JdbcTemplate`, они берут соединение транзакции; есть ли чужая таблица (`payment_links`, `provider_*`) —
  только `SharedTables` в `directory`.
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
  контроллере, `toLikePattern` в запросе, у каждого `LIKE` — `ESCAPE '!'`. Регистр понижает база с обеих
  сторон — `lower(колонка) LIKE lower(:pattern)`: Java и libc понижают «İ» по-разному (SEARCH-CASE). В JPQL —
  `lower(cast(:pattern as string))`: пустой поиск внутри `lower()` Hibernate шлёт как `bytea`, и PostgreSQL
  отвечает 500. Голый `LIKE '%' || :q || '%'` — дыра: ввод `%` возвращает всю таблицу. Скоуп роли — условие запроса
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
- **Кнопка действия видна ровно тогда, когда бэкенд его примет** (Р-62) — для действий, которые решает роль
  (`auth/actionAccess.ts`). Кнопки возврата и списания видны по смыслу и активны по правилам сервера (Р-123):
  правила — только `MoneyActions` в `pbl`, а `refund` и `completeDms` проверяют то же самое; меняешь одно —
  меняй и другое.
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
| `directory` | `SharedSchemaMigrationTest`, `DirectoryListPaginationTest`, `AuditLogQueryTest`, `TerminalBlockingIntegrationTest` |
| `pbl` | `TransactionIndexSchemaTest`, `OpenLinkConcurrencyTest`, `MoneyOperationsIntegrationTest`, `TerminalBlockedIntegrationTest`, `TransactionReconciliationIntegrationTest`, `DashboardSummaryTest`, `PaymentLinkRefundUsageTest` |

Остальные тесты с базой на H2 намеренно: там база просто хранилище.

**Модули тестируются параллельно** (`org.gradle.parallel` в `gradle.properties`): у каждого своя JVM, своя H2
и свой контейнер. Тест не должен занимать фиксированный порт, писать общий файл или ждать базы другого
модуля; чужие каталоги репозитория — только читать.

**`ecom` в Spring-тестах** (`EcomApplicationIntegrationTest`) — на H2, база шлюза — вторая пустая H2: SQL
выписки и справочников написан под Oracle провайдера и здесь не исполняется. Общие таблицы создаёт
настоящий changelog `directory` до старта контекста (`DirectorySchemaInitializer`, подключён в тестовом
yaml), как в проде, где `ecom` стартует после `directory`. Так же в `directory`: `payment_links` и слепки
`provider_*` создают changelog'и `pbl` и `ecom` (`SharedDatabaseSchema`). Рукописных копий чужого DDL в
тестах нет — переименованная у владельца колонка должна ломать тесты, а не прод.

**Контейнер:**
- Объявлен один раз — `common` testFixtures, `PostgresTestContainer`: статичный, один на JVM. Образ
  `postgres:16-alpine` прибит и совпадает с продовой версией: меняется одна — меняется и другая
  (`project_docs/guides/deployment_guide.md` §4.5).
  Spring-тест на нём — только `@PostgresIntegrationTest` (там же: `@SpringBootTest`, `@AutoConfigureMockMvc`,
  контейнер), чтобы все такие классы делили один контекст; тестам без Spring контейнер доступен через
  `instance()`, и в `SharedSchemaMigrationTest` каждый метод работает в своей схеме.
- Контексту отдаётся только адрес (`JdbcConnectionDetails`), не сам контейнер: контейнер-бин Spring Boot
  останавливает при закрытии контекста, и одна закрывшаяся конфигурация погасила бы базу остальным.
  `withReuse` не ставить: база делилась бы между модулями и прогонами.
- Класс, оставляющий строки в чужих таблицах общей базы (`payment_links` в `directory`), убирает их в
  `@AfterEach`; журнал «ломают» переименованием таблицы, а не `DROP` с рукописным восстановлением.
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
одинаковый во всех модулях; ключ шифрования кредов — один в `directory`, `pbl` и `CredentialCipherTest`.
BCrypt в тестах `auth` — стоимость 4 (`mp.security.bcrypt-strength`), в проде — умолчание 10, ниже не
опускать (`ProductionConfigurationTest`); хэш-заглушка неизвестного логина считается тем же кодировщиком.
Все шесть планировщиков в тестах выключены своими флагами: контекст живёт весь прогон, и задача по
расписанию сработала бы посреди чужого теста. Тесты зовут сервисы (или метод планировщика) напрямую; новый
планировщик — только с выключателем, иначе упадёт `noTaskRunsByTheClockInTests` модуля.

**Провайдер в тестах.** Боевая реализация одна, подмена — только явная:
- `@Import(StubAcquirerConfig.class)` даёт `@Primary`-мок, делегирующий двойнику `StubAcquiringClient`;
  тест переопределяет нужный метод через `doReturn(...)`. **Сбрасывать мок в `@BeforeEach` приходится
  самим:** бин объявлен конфигурацией, а не `@MockBean`, и слушатель Spring его не чистит.
- `@MockBean AcquiringClient` — где нужен отказ или задержка провайдера (`MoneyOperationsIntegrationTest`,
  `TransactionReconciliationIntegrationTest`, `OpenLinkConcurrencyTest`).
- Двойник `StubAcquiringClient` — в testFixtures `txpg-client`, конфигурация `StubAcquirerConfig` — в
  `pbl/src/test/java/.../provider/`; в `src/main` их не возвращать — там это клиент, который отвечает
  «оплачено», не спросив эквайера. Сам `TxpgAcquiringClient` проверяется на управляемом HTTP в
  `TxpgAcquiringClientTest` модуля (`MockRestServiceServer`), перевод ссылки в заказ — `ProviderOrdersTest`.

**Фикстуры** для листингов создаются прямо через репозитории, минуя провайдера. Тесты гонок
(`OpenLinkConcurrencyTest`, `RefreshTokenConcurrencyTest`) зовут сервисы не через MockMvc и без
`@Transactional` на тесте: потоки должны видеть коммиты и блокировки друг друга.

**Контекст Spring кэшируется по конфигурации класса.** Свои `properties`, `@MockBean`/`@SpyBean`, лишний или
недостающий `@Import`/`@AutoConfigureMockMvc`, `@Nested` со своим `@SpringBootTest` — это ещё один контекст с
миграциями. Сначала — встать на существующий: нужное состояние задать данными в базе (состарить
`rotated_at`), журнал сломать переименованием таблицы, свойство — в тестовый yaml, если других
потребителей у него нет.

**Не покрыто:** идемпотентность возвратов на уровне хранилища — таблицы `refunds` и ключей
идемпотентности нет (Р-12), 502 перекладывает сверку на человека. Фронтенд-тестов нет вовсе.

Что покрывал каждый тестовый класс на 12.09.2026, — снимок в архиве `project_docs/archive/fix_plan.md`
(«Тесты: что покрывает каждый класс»), он не ведётся. Правило: любое изменение backend-кода
сопровождается зелёным `./gradlew test`.

---

## 12. Правила работы для AI-агента

1. **Согласовывать изменения.** Перед правкой исходников или конфигов: описать, что и зачем
   меняется, какие файлы затрагиваются, — и дождаться явного подтверждения.
2. **Обновлять документацию в том же изменении — по таблице §2, у каждой темы одно место.** Новое
   правило или известное ограничение — сюда, в §10; новое решение — строка в `decisions.md`; новая
   задача — строка в `plan.md`, сделанная — строка оттуда удаляется, а что сделано и почему — в
   сообщении коммита (журнала нет, Р-105); изменился эндпоинт — модульный документ в `modules/` и
   Postman-коллекция; новая таблица, колонка или миграция — `guides/application_description.md`; новая
   переменная, порт или маршрут — `guides/deployment_guide.md`; новое событие аудита — таблица
   `guides/technical_handover.md` §4.4; изменились экраны или правила заведения компаний, терминалов,
   пользователей — `guides/admin_guide.md`. Документы описывают то, что есть сейчас: «раньше», «с такого-то
   числа», «больше не» в них не пишутся. Один и тот же факт в двух документах не записывать — во втором
   ставить ссылку.
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
   grep -rn "localStorage" frontend/src               # только session.ts (refresh, простой) и LanguageContext
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
