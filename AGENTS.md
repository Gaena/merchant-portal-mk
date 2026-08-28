# AGENTS.md — контекст проекта Merchant Portal (MP)

> Единый источник правды для AI-агентов и новых разработчиков.
> Сверено с кодом на ревизии `3f890de` (14.08.2026) плюс правки P0-1 и P0-2 от 15.08.2026
> (закрытие утечки транзакций между компаниями и закрытие публичного `/status`
> с переводом страницы чека на серверный рендер), P1-3 от 16.08.2026
> (фоновая сверка зависших `PENDING`), P0-4 от 16.08.2026 (`enum Role` вместо строковых
> литералов), P0-3 + P1-15 от 17.08.2026 (снятие кэша поверх проверок прав и роли
> на запись терминалов), P0-7 + P0-8 от 17.08.2026 (безопасность денежных операций
> при сбоях шлюза; частичный capture передаётся эквайеру, потолок возврата считается
> от захваченной суммы), P0-5 + P0-6 + P1-11 от 17.08.2026 (секреты вынесены в окружение,
> дефолтный админ убран), P1-1 от 17.08.2026 (Spring Security включён по-настоящему:
> «по умолчанию запрещено», actuator на локальном management-порту, swagger за флагом),
> P1-2 от 17.08.2026 (миграции больше не зависят от порядка запуска сервисов)
> и P1-5 + P1-6 + P1-7 от 17.08.2026 (открытие ссылки — одна транзакция под пессимистичной
> блокировкой, авторизованный холд не гасится и занимает слот, успешный платёж считается
> один раз) и P1-9 от 18.08.2026 (у ссылки есть срок жизни: 24 часа по умолчанию,
> потолок 90 дней от создания, `expiresAt` в запросе создания и в полном ответе)
> и P1-12 от 18.08.2026 (refresh-токены с ротацией, `/refresh` и `/logout`, отзыв при
> блокировке/удалении; access-токен по-прежнему живёт 24 ч и при выходе не отзывается)
> и P1-14 от 18.08.2026 (фронтенд: TypeScript установлен, `tsc -b` стоит гейтом перед
> `vite build`, `VITE_API_BASE_URL`, ≈7 600 строк мёртвого кода удалены)
> и P1-13 от 18.08.2026 (фронтенд: access-токен только в памяти, refresh в `localStorage`,
> single-flight обновление по 401, восстановление сессии при загрузке, ролевые guard'ы,
> fail-closed роль; TTL access-токена сокращён до 15 минут во всех трёх сервисах);
> все они ещё не закоммичены.
> Если код и этот файл расходятся — **прав код**, а файл нужно исправить в том же PR.

---

## 1. Что это

Платёжный портал мерчанта для MilliKart: генерация платёжных ссылок (Pay-By-Link),
управление компаниями и эквайринговыми терминалами, проведение и возврат транзакций,
журнал аудита.

Gradle-монорепо: три Spring Boot микросервиса + общая библиотека + React SPA.
Все три сервиса работают с **одной** базой PostgreSQL и делят таблицы `companies` и `terminals`
(осознанное допущение, зафиксировано в `problems.md`).

---

## 2. Карта репозитория

```
mp/
├── build.gradle            # java 21, Spring Boot 3.2.5 (apply false), общие настройки subprojects
├── settings.gradle         # include 'common', 'auth', 'pbl', 'directory'
├── common/                 # библиотека (java-library), без main-класса
├── auth/                   # :8081  — логин, пользователи
├── directory/              # :8082  — компании, терминалы, аудит
├── pbl/                    # :8080  — платёжные ссылки, транзакции, TXPG
├── frontend/               # :3000  — React SPA (Vite)
│
├── .env.example            # шаблон переменных окружения (реальный .env — в .gitignore)
├── AGENTS.md               # этот файл
├── code_review.md          # код-ревью от 14.08.2026: 9 блокеров, 15 важных (P0-1, P0-2 закрыты 15.08.2026, P1-3 и P0-4 — 16.08.2026, P0-3, P1-15, P0-7, P0-8, P0-5, P0-6, P1-11, P1-1 и P1-2 — 17.08.2026, P1-9, P1-12 и P1-14 — 18.08.2026)
├── fix_plan.md             # рабочий трекер устранения находок ревью: решения, статусы, журнал
├── problems.md             # осознанный техдолг и планы
├── application_description.md   # детальная архитектура, DTO, схемы БД, карта API
├── deployment_guide.md          # сборка, развёртывание, конфигурация
├── technical_handover.md        # техпаспорт для заказчика (⚠ см. §10 — расходится с кодом)
├── implementation_plan.md       # ⚠ относится к ДРУГОМУ проекту (m10 converter, NCLS-1476) — к MP отношения не имеет
└── README.md                    # две строки, содержания нет
```

`pbl/HELP.md` — сгенерированная Spring Initializr заглушка, не читать.

Дополнительная документация по модулям:
`auth/auth.md`, `directory/directory.md`, `pbl/pay-by-link.md`.
Контракт эквайера: `pbl/TXPG-client-side-integration.md` (MilliKart «Client side integration»,
v0.1.3, 28.12.2023) — источник словаря статусов заказа (§5.8.8) и ответов `exec-tran` (§5.5-5.7);
описывает только SMS, `Order_DMS` в нём нет.
Postman-коллекции: `auth/Auth.postman_collection.json`, `directory/Directory.postman_collection.json`,
`pbl/Pay-By-Link.postman_collection.json`, `pbl/NON-PSP Ecom.postman_collection.json`.

---

## 3. Стек — точные версии

**Backend**

| Что | Версия | Где задано |
|:---|:---|:---|
| Java | 21 (toolchain) | `build.gradle:22` |
| Spring Boot | **3.2.5** | `build.gradle:3` |
| Spring Security | 6.x (из BOM) | — |
| JJWT | 0.11.5 | `common/build.gradle:21` |
| Caffeine | 3.1.8 (⚠ **не активен**, см. ниже) | `common/build.gradle:18` |
| Resilience4j | 2.2.0 (только `pbl`) | `pbl/build.gradle:11` |
| SpringDoc OpenAPI | 2.5.0 | `common/build.gradle:17` |
| Liquibase | из BOM | — |
| PostgreSQL | runtime; H2 в тестах | — |
| Gradle wrapper | 8.5 | `gradle/wrapper/gradle-wrapper.properties` |
| Lombok | 1.18.30 | во всех модулях |

**Frontend**

| Что | Версия |
|:---|:---|
| React | 18.3.1 |
| **Vite** | **6.3.5** |
| MUI | 7.3.5 (`@mui/lab` 7.0.1-beta.19 — единственная beta, чей peer ровно `^7.3.5`; при обновлении MUI обновлять парой) |
| **react-router** | **7.13.0** (пакет `react-router`, НЕ `react-router-dom`) |
| Tailwind CSS | 4.1.12 (подключён, но Tailwind-классы есть только в двух файлах — см. ниже) |
| axios | `^1.7.9` (в lock — 1.18.1) · recharts 2.15.2 · xlsx `^0.18.5` |
| **TypeScript** | **7.0.2** (с 18.08.2026, P1-14). `strict` выключен **явно** в `tsconfig.app.json` — TS ≥ 7 включает его по умолчанию; включение — отдельная задача |
| oxlint | 1.16.0, конфиг `.oxlintrc.json` (`npm run lint`) |

Tailwind подключён по-настоящему: vite-плагин (`vite.config.ts:20`) + `src/styles/tailwind.css`
с `@import 'tailwindcss'` и `tw-animate-css`. В коде используется всего в двух файлах
(`components/StatsOverview.tsx:165` и `components/figma/ImageWithFallback.tsx:17,20`; последний
файл сам ниоткуда не импортируется, оставлен намеренно). Конвенция — MUI; новых Tailwind-классов
не добавлять. shadcn/Radix и остальные зависимости генератора удалены 18.08.2026 (P1-14) —
не возвращать.

> `directory/build.gradle:8` объявляет `springBootVersion = '3.1.0'` — это мёртвая переменная,
> нигде не используется. Реальная версия берётся из корневого `build.gradle`. Не ориентируйся на неё.

> **Кэширования в проекте фактически нет** (с 17.08.2026, P0-3 / решение Р-9). Caffeine и
> `spring-boot-starter-cache` остались в `common/build.gradle`, `common/.../config/CacheConfig.java`
> с `@EnableCaching` и кэшами `terminals`/`companies` жив, но **ни одного `@Cacheable` / `@CacheEvict`
> в коде больше нет**: они стояли только на `TerminalService.getTerminal` и `CompanyService.getCompany`
> в `directory` и были сняты вместе с дырой. Не считай кэш работающим и не восстанавливай аннотации —
> причина в §10.

---

## 4. Команды

```bash
# --- Backend (из корня) ---
./gradlew build                 # сборка всех модулей
./gradlew test                  # все тесты
./gradlew :auth:test            # тесты одного модуля (:directory, :pbl)
./gradlew :auth:bootRun         # запуск сервиса (8081 / 8082 / 8080)

# --- Frontend ---
cd frontend
npm ci                          # или npm install
npm run dev                     # localhost:3000, проксирует /api/v1/* на бэкенд
npm run typecheck               # tsc -b (оба проекта: src и vite.config.ts)
npm run lint                    # oxlint, конфиг .oxlintrc.json (сейчас 0 ошибок, ~78 предупреждений)
npm run build                   # tsc -b && vite build → dist/ (типы проверяются ДО сборки)
npm run preview                 # отдать dist/ локально
```

`npm run build` **падает на ошибках типов** — это гейт, а не совет. Тестов на фронтенде нет,
поэтому после правок обязательны `npm run typecheck` и ручная проверка в браузере.

Адрес API в собранном фронтенде — `VITE_API_BASE_URL` (шаблон `frontend/.env.example`,
реальный `frontend/.env` игнорируется корневым `.gitignore`). Пусто по умолчанию: запросы
идут относительно origin — в dev их разводит прокси Vite, в проде — nginx на том же домене.
Значение зашивается в бандл **на этапе сборки** (`import.meta.env`), не читается в рантайме.

Порты: `auth` 8081 и `directory` 8082 заданы явно в `application.yaml`; у `pbl` `server.port`
**не задан** — 8080 получается как дефолт Spring Boot. Публичный адрес `pbl` (`pbl.base-url`)
с портом никак не связан: он приходит из `PBL_BASE_URL` (P1-10) и должен совпадать с тем,
что видит браузер плательщика — локально `http://localhost:8080/`, в проде `https://домен/`
за nginx.

**Перед запуском backend нужна PostgreSQL** на `localhost:5432`, база `postgres`,
пользователь `postgres` (адрес и логин настраиваются через `DB_URL` / `DB_USERNAME` — у них есть
дефолты). Схему создаёт Liquibase на старте (`ddl-auto: validate`, миграции не отключать).

⚠ **Без переменных окружения сервис не стартует.** `DB_PASSWORD` и `JWT_SECRET` **не имеют
значений по умолчанию** (P0-5 / P1-11, 17.08.2026), и `JWT_SECRET` обязан совпадать во всех трёх
сервисах — иначе токен, выданный `auth`, не проходит проверку в `directory` и `pbl`.
`pbl` дополнительно требует три адреса (P1-10, 18.08.2026): `PBL_BASE_URL`,
`PBL_PROVIDER_GATEWAY_BASE_URL`, `PBL_PROVIDER_API_BASE_URL` — тоже без дефолтов, см. §10 (P1-10).
Шаблон — `.env.example`, процедура — `deployment_guide.md` §20.

```bash
export DB_PASSWORD='...'
export JWT_SECRET="$(openssl rand -base64 48)"   # одно значение на все три сервиса
./gradlew :auth:bootRun

# для pbl — ещё три адреса; локальные значения ниже, боевые выдаёт MilliKart
export PBL_BASE_URL='http://localhost:8080/'
export PBL_PROVIDER_GATEWAY_BASE_URL='https://<шлюз тестового стенда>/'
export PBL_PROVIDER_API_BASE_URL='http://<api тестового стенда>/'   # http → WARN на старте, не ошибка
./gradlew :pbl:bootRun
```

Секретов в yaml больше нет — только `${ENV_VAR}`. **Дефолт у секрета не заводить ни при каких
обстоятельствах:** именно дефолт в `JwtProvider` держал публичный ключ подписи в репозитории.
`JwtProvider` отказывается стартовать на пустом ключе, на ключе короче 32 байт и на
скомпрометированном ключе из истории git (сверка по SHA-256, литерала в исходниках нет).
Отсутствие переменной объясняет `MissingSecretFailureAnalyzer` — он нужен потому, что
`@ConfigurationProperties` (а значит и `spring.datasource.password`) нерезолвнутый `${...}`
**не считает ошибкой** и передаёт драйверу как текст; `@Value` — считает.
Тестовый ключ — `test-only-jwt-secret-not-used-anywhere-else-0123456789`, одинаковый
в трёх `src/test/resources/application.yaml`.

**Порядок старта значения не имеет** (с 17.08.2026, P1-2). Каждый changeset, создающий общую
таблицу, обложен собственным `<preConditions>` — по одному объекту на changeset, — поэтому любой
из трёх сервисов может подняться первым. Межсервисные ключи (`fk_terminals_company`) стоят под
`onFail="CONTINUE"`: если чужой таблицы ещё нет, changeset **не** записывается в
`DATABASECHANGELOG` и повторяет попытку на следующем старте. Подробности — §10.

Docker-образов, `docker-compose.yml` и CI в проекте нет.

---

## 5. Архитектурные инварианты

Не нарушать без явного обсуждения:

1. **`common` не знает о конкретных сервисах.** Туда идут только JWT, Spring Security,
   исключения, валидаторы, кэш-конфиг. Никакой доменной логики.
2. **Владение таблицами:**

   | Таблица | Владелец (пишет схему) | Читают |
   |:---|:---|:---|
   | `users` | `auth` | — |
   | `refresh_tokens` | `auth` (с 18.08.2026, P1-12) | — |
   | `companies` | `auth` (создаёт) + `directory` (дополняет аудит-колонками) | `auth`, `directory` |
   | `terminals` | `directory` (создаёт + дополняет) и `pbl` (создаёт, если ещё нет — `001-initial-schema.xml:8-31`) | `pbl` читает напрямую, минуя REST |
   | `payment_links`, `transactions` | `pbl` | — |
   | `audit_logs` | `directory` | — |

3. **Разделение ответственности:** `auth` не управляет компаниями/терминалами;
   `directory` не выдаёт JWT; `pbl` валидирует JWT и читает терминалы, но не создаёт
   компании и пользователей.
4. **JWT симметричный (HS256)**, один общий секрет на все три сервиса.
   Claims: `sub` = email, `userId`, `role`, `companyId`. Access-токен проверяется **stateless**
   (подпись + срок) во всех трёх сервисах; чёрного списка нет и заводить его нельзя — запрос
   в базу на каждый вызов убьёт модель. Отзыв делается через refresh-токены (`auth`, P1-12):
   непрозрачная случайная строка, в базе только SHA-256, ротация с окном снисхождения,
   гашение цепочки при logout/краже/блокировке. Access-токен живёт **15 минут**
   (`JWT_EXPIRATION_MS=900000`, с 18.08.2026, P1-13) — это и есть верхняя граница, сколько после
   logout/блокировки/удаления пользователь ещё имеет доступ; фронтенд обновляет токен сам — см. §9, §10.
5. **Все ответы об ошибках** идут через `common.exception.GlobalExceptionHandler` в формате
   `ErrorResponse { timestamp, status, error, message, path }`. Новые исключения —
   наследники из `common.exception`, не `ResponseStatusException`.

   | Исключение | HTTP |
   |:---|:---|
   | `BusinessException` | 400 |
   | `UnauthorizedException` | 401 |
   | `InvalidStateException` | **403** (используется как «доступ запрещён») |
   | `ResourceNotFoundException` | 404 |
   | `ConflictException`, `OptimisticLockingFailureException` | 409 |
   | `PaymentOutcomeUnknownException` | **502** (с 17.08.2026, P0-7) |

   `PaymentOutcomeUnknownException` — единственное исключение, которое означает «мы не знаем,
   выполнилась операция или нет» (таймаут, обрыв, 5xx на денежном вызове). Не наследник
   `BusinessException` намеренно: 400 читается мерчантом как «отказ, повторяй», а повторный
   возврат — это двойной возврат. Обработчик пишет ERROR с маркером `PAYMENT_OUTCOME_UNKNOWN`
   для мониторинга. Подробнее — §7 и §10.

---

## 6. Безопасность — как всё устроено на самом деле

**Модель — «по умолчанию запрещено»** (с 17.08.2026, P1-1). `SecurityConfig` заканчивается
`anyRequest().authenticated()`, поэтому новый эндпоинт закрыт с момента, как его написали,
и остаётся закрытым, пока его не откроют осознанно. Раньше здесь стоял `anyRequest().permitAll()`,
и вся защита держалась на разборе префикса внутри `JwtAuthFilter` — из-за чего всё, что
не начиналось с `/api/v1/`, было публичным.

**Список публичных путей живёт ровно в одном месте:**
`common/.../security/PublicEndpoints.java`. Его читают оба слоя — `JwtAuthFilter`
(решает, разбирать ли токен) и `SecurityConfig` (решает, пускать ли без аутентификации).
Второй копии списка нет и заводить её нельзя: расхождение между слоями — это и есть дыра.

| Группа | Пути | Чем защищены |
|:---|:---|:---|
| `PUBLIC_API` | `/api/v1/auth/**` (`/login`, `/refresh`, `/logout`), `/api/v1/payment-links/*/open`, `/api/v1/payment-links/redirect/**` | ничем — публичны по смыслу |
| `INFRASTRUCTURE` | `/actuator/**` | привязкой management-порта к `127.0.0.1`, не токеном |
| `SWAGGER` | `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml` | флагом `springdoc.api-docs.enabled` (по умолчанию **выключен**) |

`SWAGGER` разрешается **условно**: матчеры в `SecurityConfig` добавляются, только когда флаг
включён, и `JwtAuthFilter` пропускает эти пути при том же условии. Поэтому `isPublic(path)`
покрывает только `PUBLIC_API` + `INFRASTRUCTURE`, а springdoc отдельным `isSwagger(path)`.

Шаблон `/api/v1/payment-links/*/open` — **ровно один сегмент** на месте id, как в маппинге
`@GetMapping("/{id}/open")`. Прежняя проверка `path.endsWith("/open")` под префиксом
`/api/v1/payment-links/` подходила пути любой вложенности; `/api/v1/payment-links/a/b/open`
теперь даёт 401, а не проходит.

`/api/v1/transactions/*/status` был публичным до 15.08.2026 — закрыт в P0-2, см. §10.

**Actuator** вынесен на отдельный порт (`management.server.port`: auth 9081, directory 9082,
pbl 9080), привязанный к `127.0.0.1`. На рабочем порту `/actuator/**` отдаёт 404; на
management-порту отвечает без токена — иначе сломались бы пробы, у которых токена нет.
`show-details: always` оставлен намеренно: детали видны только с самой машины.
Матчер `/actuator/**` в основной цепочке оставлен на случай конфигурации, где management-порт
совпадает с рабочим.

**Единый формат отказа.** `SecurityErrorResponder` реализует и `AuthenticationEntryPoint`,
и `AccessDeniedHandler`, и им же пользуется `JwtAuthFilter`. Клиент не должен различать,
кем он отклонён: 401 от фильтра и 401 от Spring Security — байт в байт одна структура
`ErrorResponse`. Отказ аутентифицированному пользователю — **403**, а не 401 (см. §5:
403 = «доступ запрещён» во всём проекте).
Запрос к несуществующему пути аутентифицированным пользователем даёт **404**
(`GlobalExceptionHandler.handleNoHandler`); до P1-1 такой запрос падал в `handleUnexpected`
и возвращал 500 с ERROR в логе.

`@EnableMethodSecurity` включён, но `@PreAuthorize` **не используется нигде** —
вся авторизация в ручных `if`-ах внутри сервисов, через `UserPrincipal.getRole()`/`getCompanyId()`.
Читай эту аннотацию как «доступна», а не как «работающий второй слой».

Статический fallback-токен `pbl.security.api-token` даёт роль `SYSTEM_ADMIN` без пароля
(`JwtAuthFilter`). С 17.08.2026 у него **нет значения по умолчанию** и он выключен везде,
включая тестовые профили; включённый флаг с пустым токеном роняет старт. Не возвращай дефолт —
известный статический токен это бэкдор, а не удобство.

CSRF и CORS выключены осознанно, обоснование — в комментариях `SecurityConfig`: токен приходит
в заголовке, а не в куке (CSRF не применим), фронтенд обслуживается с того же origin через nginx
(CORS не нужен). Не «чини» их наугад.

### Роли

`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR` —
**`enum Role` в `common/.../security/Role.java`** (с 16.08.2026, P0-4). В main-коде backend
строковых литералов ролей больше нет; все проверки — сравнение с константами enum'а,
наборы ролей — `EnumSet`.

Роль остаётся строкой **на границе системы**: claim `role` в JWT и колонка `users.role`
(`varchar(50)`) не ограничены ничем. Разбор — только `Role.fromValue(String)`:

- **никогда не бросает** — неизвестное значение даёт `Optional.empty()`, и вызывающий обязан
  отказать в доступе. `Role.valueOf(...)` на этих данных использовать нельзя: он превратит
  аккуратный 403 в 500;
- **сравнение строго по точному совпадению**, без `equalsIgnoreCase` и без `trim`.
  Регистронезависимый разбор выдал бы права администратора пользователю со значением
  `system_admin` в БД — это повышение привилегий, а не удобство. Тест
  `RoleTest.fromValue_isCaseSensitive` стоит именно на этом, не удалять.

`UserPrincipal` хранит оба представления: `getRole()` → `Role` (**nullable** — `null`, если
значение не распознано) и `getRawRole()` → сырая строка для логов и текстов ошибок.
Статические `UserPrincipal.getRole(principal)` / `getRawRole(principal)` / `getCompanyId(principal)`
null-безопасны — пользуйся ими, а не `principal.getRole()` напрямую.
`getAuthorities()` по-прежнему строит `ROLE_` + сырая строка (для `@PreAuthorize`, который нигде
не используется).

Ещё не покрыто enum'ом: миграция `002-user-directory-schema.xml` и
валидация роли в `CreateUserRequest` / `UpdateUserRequest` (её по-прежнему нет — пользователь
с мусорной ролью безопасен, ему везде отказывают). Во фронтенде с 18.08.2026 (P1-13) есть
зеркало enum'а — `src/app/types/role.ts` (`ROLES`, `type Role`, `parseRole` — строгое сравнение,
как `Role.fromValue`): роль из ответа `/login`/`/refresh` проходит через `parseRole`, нераспознанная
роль = вход не состоялся (fail-closed; раньше подставлялся `SYSTEM_ADMIN`). Литералы ролей
остались в 4 файлах — `types/dto.ts` (union в `UserDto`), `UsersPage` (select формы),
`SettingsPage` (`canEditCompany`) и `CompaniesPage` (`isAdmin`); раскладка «маршрут → роли» — только
`src/app/auth/routeAccess.ts`.

### Фактическая матрица доступа (по коду, не по `technical_handover.md`)

| Эндпоинт | SYSTEM_ADMIN | COMPANY_HEAD | COMPANY_MANAGER | COMPANY_EMPLOYEE | AUDITOR |
|:---|:---:|:---:|:---:|:---:|:---:|
| `POST/GET/PATCH/DELETE /users` | ✅ все | ✅ своя компания | ❌ | ❌ | ❌ |
| `GET /companies` (список) | ✅ | ❌ | ❌ | ❌ | ✅ |
| `GET /companies/{id}` | ✅ | ✅ своя | ✅ своя | ✅ своя | ✅ |
| `POST/PATCH/DELETE /companies` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /terminals` | ✅ все | ✅ своя | ✅ своя | ✅ своя | ✅ все |
| `POST/PATCH/DELETE /terminals` | ✅ | ✅ своя | ✅ своя | ❌ | ❌ |
| `GET /audit-logs` | ✅ все | ✅ своя | ✅ своя | ❌ | ✅ все |
| `POST/PATCH /payment-links` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `GET /payment-links`, `/{id}` | ✅ все | ✅ своя | ✅ своя | ✅ своя | ✅ все |
| `GET /payment-links/{id}/transactions` | ✅ все | ✅ своя | ✅ своя | ✅ своя | ✅ все |
| `POST /transactions/{id}/complete` | ✅ | ✅ | ✅ | ✅ | ❌ |
| `POST /transactions/{id}/refund` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `GET /transactions` (список) | ✅ все | ✅ своя | ✅ своя | ✅ своя | ✅ все |

Запись терминалов (с 17.08.2026, P1-15) идёт через `TerminalService.TERMINAL_WRITE_ROLES`
(`EnumSet.of(SYSTEM_ADMIN, COMPANY_HEAD, COMPANY_MANAGER)`): роль проверяется **до** `companyId`,
поэтому `COMPANY_EMPLOYEE`, `AUDITOR` и нераспознанная роль получают 403 даже на терминалы своей
компании. На чтение (`GET /terminals`, `GET /terminals/{id}`) права не менялись: `COMPANY_EMPLOYEE`
свои терминалы по-прежнему видит.

**AUDITOR — глобальный читатель во всех трёх сервисах** (с 15.08.2026, решение Р-1 в `fix_plan.md`).
Раньше `pbl` был исключением: `validateAccess` пропускал мимо проверки компании только
`SYSTEM_ADMIN`, и у типового системного аудитора с `companyId == null` `GET /payment-links/{id}`
возвращал 403, а `GET /payment-links` — пустую страницу. Теперь обе роли проходят через
`PaymentLinkService.isGlobalReader` — и в `validateAccess`, и в `list()`, и в
`listTransactions()`, и в `getTransactionsByLinkId()`. На запись это не влияет: `AUDITOR` не входит
в `LINK_WRITE_ROLES` / `REFUND_ROLES` у `create`, `update`, `completeDms`, `refund` и отсекается
раньше, на проверке роли.

Ограничение по компании в `pbl` работает через терминалы:
`terminalRepository.findAllByCompanyId(companyId)` → список `allowedTerminals` → фильтр по `terminal_id`
(`PaymentLinkRepository.search` для ссылок, `TransactionRepository.findByLink_TerminalIdIn` для транзакций).
Роль вне `READ_ROLES` (то есть только нераспознанная роль — `READ_ROLES` = `EnumSet.allOf(Role.class)`)
получает 403; роль из `READ_ROLES`, но без `companyId` — пустую страницу, а не отказ.

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
GET  /api/v1/payment-links/redirect/{tx}  → refreshByMerchantRid(tx) → Thymeleaf redirect.html
       {tx} — наш merchantRid (случайный UUID). Query-параметры провайдера ID/PASSWORD/STATUS
       не объявлены и игнорируются. Один заход к провайдеру, без опроса и без JS на странице.
```

**Статусы:**
`PaymentLinkStatus`: `ACTIVE` → `EXPIRED` | `COMPLETED` | `CANCELED`
`TransactionStatus`: `PENDING` → `AUTHORIZED` (DMS) → `SUCCESS` → `PARTIALLY_REFUNDED` → `REFUNDED`; `FAILED`

**Маппинг статусов TXPG** (с 19.08.2026, P1-8a — `ProviderOrderStatus.classify`, отдельный
тип без Spring; `refreshStatus` возвращает `StatusRefresh(transaction, outcome)`):

| Статус эквайера | `ProviderOrderOutcome` | Локально | Откуда |
|:---|:---|:---|:---|
| `FullyPaid`, `Cleared` | `PAID` | `SUCCESS` | §5.8.8 / старый код (DMS) |
| `Authorized` | `AUTHORIZED` | `AUTHORIZED` | старый код (DMS) |
| `Rejected`, `Expired`, `Failed`, `Declined` | `FAILED_FINAL` | `FAILED` | §5.8.8 / старый код |
| `Preparing` | `NON_FINAL` | не меняется; **единственный**, кого сверка гасит по `max-age` | §5.1, §5.8.3 |
| `PartPaid`, `Cancelled`, `Canceled`, `Refused`, `Closed` | `SETTLED_OTHER` | не меняется, WARN, `FAILED` по таймауту запрещён | §5.8.8 |
| всё остальное, `null`, не-строка | `UNKNOWN` | не меняется, WARN, `FAILED` по таймауту запрещён | — |

Сравнение точное и регистрозависимое (как `Role.fromValue`). Словарь — из
`pbl/TXPG-client-side-integration.md` §5.8.8; DMS (`Order_DMS`) в контракте не описан вовсе,
`Authorized`/`Cleared` держатся на исходном коде (см. `problems.md`). В `providerResponse` пишутся
маркеры `mpStatusOutcome` (всегда) и `mpProviderStatus` (сырая строка, для `UNKNOWN`/`SETTLED_OTHER`);
сам payload заказа ложится туда через `ProviderPayloads.withoutSecrets` — без `password` (P0-9).

**Три суммы у транзакции** (с 17.08.2026, P0-8) — не путать:

| Поле | Что значит | Когда заполняется |
|:---|:---|:---|
| `amount` | авторизованная сумма, история операции | всегда, после capture **не меняется** |
| `capturedAmount` | сколько реально списано с карты при клиринге | только DMS-capture; у SMS `null` |
| `refundedAmount` | сколько уже возвращено | при возвратах, по умолчанию `0` |

Потолок возврата — `refundableBase(tx)` = `capturedAmount`, а при `null` (SMS) — `amount`.
Частичный capture остаётся в статусе `SUCCESS`; отдельного статуса под него нет.

**Провайдер:** `AcquiringClient` с **единственной** реализацией — `TxpgAcquiringClient`,
обычный `@Component`. Выбирать нечего, флага `pbl.provider.stub` нет.

Стаб был убран 20.08.2026: доступ к тестовому стенду MilliKart есть всегда, поэтому локальный
запуск ходит на стенд через `PBL_PROVIDER_*`, как и прод — просто по другим адресам. Заодно исчез
целый класс отказов: пока выбор делал флаг, значение вроде пустой строки (а `--pbl.provider.stub`
без `=true` доезжает до Spring именно пустым) не выбирало ни одной реализации, и сервис падал
жалобой на отсутствующий бин `AcquiringClient`, ни словом не упоминая флаг.

Двойник для тестов остался, но живёт в тестовых исходниках (`StubAcquiringClient` +
`StubAcquirerConfig`, §11) и из работающего сервиса недостижим. Это и было главным в правке:
раньше боевая сборка содержала клиент, который отвечает «оплачено», не спросив эквайера.

`@CircuitBreaker(name="acquiring")` висит на **всех четырёх** методах `TxpgAcquiringClient`.
`@Retry(name="acquiring")` — только на `createEcomOrder` и `getOrderStatus` (с 17.08.2026, P0-7):
это единственные два вызова, повтор которых не превращается в деньги. На `completeDms` и `refund`
ретрая нет и быть не должно — см. §10. Параметры — в `pbl/src/main/resources/application.yaml:63-75`
(`maxAttempts: 3`, `waitDuration: 500ms`), в самих аннотациях их нет.

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
статуса, а неизвестность за отказ — денег держателя карты. `getOrderStatus` и `createEcomOrder`
классификацию не используют: там по-прежнему всё сводится к `BusinessException`, и это верно —
чтение и создание заказа денег не двигают.

**Подтверждение денежных операций** (с 19.08.2026, P1-8b, решение Р-23). «Нет `errorCode`» —
не «прошло». `completeDms` и `refund` возвращают `MoneyOperationResult`
(`approvalCode`, `tranActionId`, `ridByPmo`, `raw`), и `TxpgAcquiringClient.requireConfirmation`
собирает его из ответа `exec-tran` (контракт §5.5-5.7:
`{"tran":{"approvalCode","match":{"tranActionId","ridByPmo"}}}`). Признак успеха —
`tran.match.ridByPmo`, идентификатор операции в ядре ПЦ (§5.8.8): без него результата нет,
тело ответа целиком уходит в ERROR-лог (`NO CONFIRMATION`) и летит
`PaymentOutcomeUnknownException`; `approvalCode`/`tranActionId` без `ridByPmo` — WARN, не отказ.
Разбор защитный (`tran`/`match` не карты → «не подтверждено», не `ClassCastException`;
идентификатор — только строка или число: объект/список/boolean на месте `ridByPmo` — тоже
«не подтверждено», иначе `String.valueOf` дал бы непустое `"{}"` и прошёл за подтверждение). Тестовый двойник отвечает той же формой. В `providerResponse` сервис пишет след:
`mpCapture` (объект) после clearing и список `mpRefunds` (по записи на каждый частичный
возврат) — оба вида `{tranActionId, ridByPmo, approvalCode, amount, at}`; `RefundResponse`
отдаёт `refundId` = `tranActionId`, `acquirerReference` = `ridByPmo` (на 200 никогда не пуст),
`approvalCode`. Причина отказа (`custAttrs`, §5.8.7 — `DeclineDescription`, иначе
`PmoDeclineDescription`, иначе `PmoResultCode` кроме `Approved`) читается
`ProviderDeclineReason.extract` только при `FAILED_FINAL`, хранится под `mpDeclineReason` и
отдаётся как `TransactionResponse.failureReason` (Р-24; показ на фронте — отдельно).

**Карточка транзакции: маска карты, RRN, код одобрения** (с 19.08.2026, P1-16, решение Р-26).
`TransactionResponse.cardNumberMasked` / `rrn` / `approvalCode` читаются **на лету** из
`providerResponse` разборщиком `ProviderOrderDetails.read` (тот же пакет, без Spring, как
`ProviderOrderStatus` и `ProviderDeclineReason`) — и в `/status`, и в листинге, поэтому список и
карточка показывают одно и то же. Где что лежит по контракту:

| Поле ответа | Откуда | Раздел |
|:---|:---|:---|
| `cardNumberMasked` | `order.srcToken.displayName`, как есть (`426863******3689`); последние 4 цифры отрезает фронт | §5.8.4, §5.8.6 |
| `rrn`, `approvalCode` | запись **покупки** в `order.trans[]`; если списка нет — `order.lastTran` | §5.8.5–5.8.6; §5.8.3 |

Ключа `cardNumberMasked` в payload'е эквайера **нет вовсе** — это имя нашего DTO; до P1-16 все три
поля читались из корня `order` и всегда были пустыми. Выбор записи в `trans[]`: кандидаты — карты
с `isReversal` не `true`, `description` не `Refund` и без `Void`; если среди них есть
`description == "Purchase"` — только они; из оставшихся — самая ранняя по `regTime`
(строка `yyyy-MM-dd HH:mm:ss`, сравнивается как текст — сортируется как время; **не** парсить в
`LocalDateTime`, неожиданный формат уронит карточку). `Purchase` — предпочтение, а не фильтр:
DMS в контракте не описан, и какой `description` у холда/клиринга — неизвестно (`problems.md` §4).
Ничего не нашлось — оба поля `null` и DEBUG-строка. Отдельных колонок нет: `refreshStatus` выходит
на терминальных статусах, payload оплаченной транзакции больше не перезаписывается (capture и
возвраты кладут своё **поверх**, `mpCapture`/`mpRefunds`), так что чтение на лету стабильно.
`withoutSecrets` убирает только `password` верхнего уровня — вложенный `srcToken` в колонке
остаётся, и это допустимо: в нём уже маскированный `displayName`, бренд и срок, PAN/CVV эквайер
не отдаёт. Правило «что такое значение» одно на весь пакет — `ProviderPayloads.scalarText`
(`String`/`Number` → текст, пустое/структура/boolean → `null`): им же `requireConfirmation`
читает `ridByPmo`; второй копии быть не должно.

**Планировщики:**
- `PaymentLinkScheduler` — cron `0 */5 * * * *`, помечает просроченные ссылки `EXPIRED`
  (`expiresAt IS NOT NULL AND expiresAt < now`). С P1-9 срок есть у каждой новой ссылки,
  поэтому ему наконец есть что просрочивать.
- `TransactionReconciliationScheduler` — cron `0 */2 * * * *`, только вызов
  `TransactionReconciliationService.reconcilePendingTransactions()`. Выключается через
  `pbl.reconciliation.enabled` (`@ConditionalOnProperty`), в тестовом профиле выключен.
- (в `auth`, с 18.08.2026, P1-12) `RefreshTokenCleanupScheduler` — cron `0 30 3 * * *`, только вызов
  `RefreshTokenService.deleteExpired(now)`; тот же приём: `auth.refresh.cleanup-enabled`,
  в тестовом профиле выключен.

**Сверка зависших `PENDING`** (P1-3, 16.08.2026; граница — P1-8a, 19.08.2026).
`TransactionReconciliationService` берёт до `batch-size` (50) транзакций в `PENDING` с `createdAt`
**между** `now - give-up-age` (7 дней) и `now - min-age` (2 мин), по возрастанию `createdAt`,
и на каждую вызывает `PaymentLinkService.reconcileOne(id, maxAge)` — та идёт в собственной
транзакции (`REQUIRES_NEW`), поэтому сбой на одной записи не рушит батч. Внутри переиспользуется
приватный `refreshStatus`. `AUTHORIZED` сверка не трогает: живой DMS-холд — легитимное состояние
покоя (снятие протухших холдов упирается в отсутствие Void у `AcquiringClient` — отдельная задача,
см. §10). `FAILED` по `max-age` — **только** при `outcome == NON_FINAL`; `UNKNOWN` и
`SETTLED_OTHER` остаются `PENDING` навсегда, поэтому и нужна верхняя граница: старше
`give-up-age` запись живая, но автоматика её не трогает — нужен человек; их количество пишется
в итоговую строку лога каждого прогона. Параметры — `pbl.reconciliation.*`.

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
  (`P2-8`, `Р-37`, `problems.md §6`) — хвостом в скобках. Пересказ кода, пересказ имени поля и
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

## 9. Фронтенд: что живое, а что нет

**Точка входа:** `src/main.tsx` → `src/app/App.tsx`. Живо всё дерево `src/app/**`.

**Мёртвый код удалён 18.08.2026 (P1-14)** — 57 файлов, ≈7 600 строк из ≈19 100 (≈40% фронта):
стартовый шаблон Vite (`src/App.tsx`, `src/App.css`), второй axios-клиент с логированием
тел запросов и паролей (`src/api/client.ts`), дубль типов (`src/types/index.ts`), shadcn-boilerplate
(`src/app/components/ui/**`, 48 файлов) и пять страниц/компонентов вне роутера на моках
(`ReportsPage`, `NotificationsPage`, `POSTransactionListPage`, `POSFilterPanel`, `POSTransactionTable`).
Вместе с ними из `package.json` ушли 48 зависимостей генератора (Radix, cmdk, vaul, lucide,
date-fns, sonner и т.д.). Сейчас во фронтенде ≈11 500 строк, все под `tsc -b`.
Каждый файл проверялся grep'ом на импорты перед удалением — **не возвращать**.

**Что осталось спорного (не удалено намеренно, кандидаты на отдельную уборку):**

| Файл | Что это |
|:---|:---|
| `src/app/components/figma/ImageWithFallback.tsx` | ниоткуда не импортируется; один из двух файлов с Tailwind-классами. Оставлен по решению P1-14 |
| `src/index.css` | не импортируется (живой — `src/styles/index.css`); дублирует `@import "tailwindcss"` |
| `src/assets/{react,vite}.svg`, `hero.png` | использовались только удалённым `src/App.tsx` |
| ~~`src/app/utils/payByLinkData.ts` (мок-генератор `generateLinks`)~~ | удалён 21.08.2026 (P2-13); в файле остались словари, разборщики и форматтеры |
| `frontend/default_shadcn_theme.css` | тема генератора в корне `frontend/`, никем не читается |

**Перед правкой файла проверь, что он достижим из `routes.tsx`.**

`src/app/utils/payByLinkData.ts` больше не ловушка: 21.08.2026 (P2-13) из него удалён
мок-генератор `generateLinks` вместе с висячими импортами на обеих страницах, а также
`merchantTerminals` и поля `PaymentLink`, которые заполнял только он. Осталось то, чем
страницы пользуются на самом деле: словари (`LINK_STATUSES`, `LINK_USAGE_TYPES`,
`PAYMENT_TYPES`), разборщики (`parseLinkStatus`, `parseLinkUsageType`, `parsePaymentType`),
цвета (`getLinkStatusColors`) и форматтеры. Поля, которые разметка читает, а API не отдаёт
(`redirectUrl`, `note`, `dmsStatus`, `finalizedAt`, `cardNetwork`, `cardLast4`,
`transactionId`, `payerIp`, `sentVia`), помечены в интерфейсе как **всегда `undefined`
до появления поля в API** — ветки под ними на экран не попадают. `paidAt` был в этом списке
до 22.08.2026: с P2-15 его заполняет `lastPaidAt` из API.

**Маршруты** (`src/app/routes.tsx`): `/login`, `/` (HomePage), `/transactions`,
`/transactions/ecommerce`, `/transactions/:id`, `/pay-by-link`, `/pay-by-link/:id`,
`/companies`, `/terminals`, `/users`, `/audit-logs`, `/settings`, `*` (404, `NotFoundPage`,
с 18.08.2026, P1-13). Маршрутов `/reports`, `/notifications`, `/home` **нет** — вход ведёт на `/`.
На `/` и `/login` стоит `errorElement` (`RouteErrorPage`): исключение при рендере страницы
показывает страницу ошибки, а не белый экран. `createRouter(props: AppRouterProps)` типизирован.

**Авторизация во фронтенде** (с 18.08.2026, P1-13):

| Файл | Что делает |
|:---|:---|
| `src/app/auth/session.ts` | хранилище: access-токен — модульная переменная (**только память**), refresh — `localStorage['mp_refresh_token']`, профиль `{ email, role, companyId? }` (email и companyId — из claims JWT, роль — из поля `role` ответа через `parseRole`); `applyLoginResponse` fail-closed, `clearSession`, подписка для `useSyncExternalStore`; событие `storage` гасит сессию в других вкладках при выходе |
| `src/app/api/client.ts` | request-интерсептор берёт токен из памяти (к `/api/v1/auth/*` не прикладывает); response-интерсептор на 401: `/login`, `/refresh`, `/logout` — не трогать; уже повторяли — `clearSession`; иначе `refreshSession()` (**single-flight**, один промис на все параллельные 401) и повтор запроса. 401 от `/refresh` и нераспознанная роль сбрасывают сессию, сетевая ошибка — нет |
| `src/app/context/AuthContext.tsx` | `AuthProvider`: при загрузке с refresh-токеном показывает загрузку и зовёт `/refresh` (сессия восстанавливается без формы логина); `login` → `applyLoginResponse`; `logout` — сброс состояния сразу, `POST /logout` вдогонку (ошибка логируется). `isAuthenticated` — по access-токену в памяти |
| `src/app/auth/routeAccess.ts` | **единственная** раскладка «маршрут → роли» (`/users`: `SYSTEM_ADMIN`, `COMPANY_HEAD`; `/companies`: `SYSTEM_ADMIN`, `AUDITOR`; `/audit-logs`: `SYSTEM_ADMIN`, `AUDITOR`, `COMPANY_HEAD`, `COMPANY_MANAGER`; остальное — всем вошедшим). Читают `RoleRoute` и `Sidebar` |
| `src/app/auth/guards.tsx` | `ProtectedRoute` (→ `/login`), `PublicOnlyRoute` (→ `/`), `RoleRoute` (роль не подходит → `ForbiddenPage`, не редирект и не белый экран) |

Клиентские guard'ы — **UX, а не безопасность**: права проверяет бэкенд (`validateAccess` в сервисах),
guard'ы лишь прячут то, что вернёт 403. Эндпоинта профиля (`/me`) нет: `fullName` во фронтенде
больше не выдумывается из email — показывается сам email; настоящее имя — отдельная задача.

**На карточке ссылки не осталось выдуманных данных** (с 22.08.2026, P2-15, Р-48). Таблица
связанных операций показывает **только** ответ `/payment-links/{id}/transactions`; построитель
«запасных» строк, сочинявший транзакцию с картой по умолчанию и номером из короткого кода
ссылки, удалён — вместе с событием «Customer Redirected» («оплата + 3 секунды»), подстановкой
адреса и User-Agent за незаписанные и строками блока Payment Details, за которыми нет полей API
(номер транзакции, карта, адрес плательщика). **Не возвращать**: всё это было безвредно ровно до
дня, когда заработала дата оплаты. Поля `PaymentLink`, которых API по-прежнему не отдаёт
(`redirectUrl`, `note`, `dmsStatus`, `finalizedAt`, `cardNetwork`, `cardLast4`, `transactionId`,
`payerIp`, `sentVia`), так и помечены в `payByLinkData.ts` — ветки под ними на экран не попадают.
`paidAt` из этого списка вышел: его заполняет `lastPaidAt` из API.

**Постраничные экраны** (с 22.08.2026, P2-1 + Р-44): `UsersPage`, `CompaniesPage`, `TerminalsPage`
получили `TablePagination` и перезапрашивают данные при смене страницы и размера — как
`PayByLinkPage` до них. Поиск и фильтры на этих трёх страницах остались клиентскими и потому
видят **только загруженную страницу**; под полем поиска это написано (`common.searchOnPage`).
Серверного поиска по трём справочникам нет — `problems.md` §14. Создание и удаление строки не
правят массив локально, а перечитывают страницу: сортирует и режет на страницы сервер.

**Опасные действия на трёх справочных экранах — только через подтверждение**
(компании — с 24.08.2026, терминалы и пользователи — с 25.08.2026). Ни одна корзина, ни один
переключатель и ни одна кнопка «Сохранить» не уходят на сервер по клику: сначала окно, в котором
названы **эта** запись (имя, id, логин) и последствия именно этого действия.

| Экран | Что спрашивает | Что сказано в окне |
|:---|:---|:---|
| `CompaniesPage` | удаление, смена статуса | Удаление необратимо из портала (мягкое, `updateCompany` на удалённой отвечает «Company not found»). `INACTIVE` — пометка, которая **ничего не останавливает** (`problems.md` §21): вход, создание ссылок и приём платежей продолжают работать, для остановки платежей блокируют терминалы |
| `TerminalsPage` | блокировка (**была с P2-8**), разблокировка, сохранение правки | У блокировки и разблокировки считается, скольких ссылок это коснётся, — активных перед блокировкой, приостановленных перед разблокировкой (`GET /payment-links?terminal=&status=&size=1`, `totalElements`). У правки — построчный список того, что изменится: в одной форме лежат логин, пароль и **компания-владелец**. Изменений нет — запрос не уходит вовсе |
| `UsersPage` | удаление | Учётная запись перестаёт работать сразу (`deleteUser` гасит все refresh-токены), запись остаётся в базе скрытой, восстановить или отредактировать её из портала нельзя |

**Три текста, а не один общий** — на каждом экране: последствия действий различаются настолько,
что общая формулировка обещала бы то, чего система не делает. Различается только **содержимое**
окна; сама рамка с 25.08.2026 одна на всех (`ConfirmDialog`, P3-5b).

**Деньги — то же правило, четырьмя окнами дальше** (с 25.08.2026, P3-5a). Все пять вызовов,
после которых деньги двигаются или ссылка гаснет, живут только внутри `<Dialog>`:

```bash
cd frontend/src
grep -rn "status: 'CANCELED'\|/refund\|/complete" app/pages/*.tsx
```

даёт пять строк — `PayByLinkPage` (отмена из списка), `PayByLinkDetailPage` (отмена, списание
холда), `TransactionDetailPage` (возврат, списание холда), и у каждой кнопка-инициатор внутри
окна. Окно называет **эту** ссылку или транзакцию рамкой с коротким кодом/id и суммой, обе
кнопки гаснут на время запроса, фокус при открытии — на безопасной (`autoFocus`), закрыть
кликом мимо во время запроса нельзя. Формулировка совпадает с тем, что делает бэкенд: кнопка
и окно возврата говорят «возврат», а не «отмена транзакции», потому что зовётся
`POST /transactions/{id}/refund` и в журнал идёт `REFUND`.

**Окно подтверждения одно — `app/components/ConfirmDialog.tsx`** (с 25.08.2026, P3-5b; закрыто
отклонение №1 из Р-55). Девять собственных диалогов в шести файлах сведены в один компонент:
все девять работали правильно, но правила P3-5 были записаны в девяти местах, и десятый диалог
написали бы, забыв одно из них. Четыре правила теперь невозможно забыть, потому что их негде
не написать:

1. `onClose` игнорируется, пока `busy`, — окно не закрыть кликом мимо во время запроса;
2. кнопка отказа **первая** и с `autoFocus` — Enter не подтверждает опасное действие;
3. обе кнопки `disabled={busy}` — второй клик ушёл бы вторым `DELETE`;
4. подтверждение — `contained` и цветное, заголовок жирный.

Всё, чем окна различаются по содержимому (карточка объекта, `Alert`, список меняемых полей),
приходит в `children`, поэтому **ни один из девяти не потребовал особого случая внутри
компонента**, и он остался в 96 строк. Из пропсов варьируются `confirmColor` (встречаются все
четыре — `error`, `warning`, `success`, `primary`), `confirmIcon`, `cancelLabel` (`common.cancel`,
`payByLink.keepLink`, `transactions.detail.keepTransaction`) и `maxWidth`. `CompaniesPage`
остался **одним** вызовом на два действия: заголовок, вопрос, цвет и надпись считаются
из `pending.kind`.

```bash
cd frontend/src
# 15 <DialogActions> в pages было: 9 подтверждений + 6 форм. Осталось 6 — только формы.
grep -rc "<DialogActions" app/pages/*.tsx | awk -F: '{s+=$2} END{print s}'    # 6
grep -rn "autoFocus" app/pages/*.tsx                                          # 0 — правило внутри компонента
```

**Окна-формы сюда не относятся** — создание пользователя, компании, терминала и ссылки, правка
терминала, «поделиться ссылкой». Их шесть, у них другая задача и другая структура, и сводить
их с подтверждениями незачем.

**Изменения пользователя на экране нет вовсе.** `PATCH /api/v1/users/{id}` в API есть — смена
роли, компании, блокировка, пароль, — и всё это журналируется поимённо (P2-14), но на
`UsersPage` из мутаций только создание и удаление. Подтверждать там нечего, потому что менять
нечем; если экран правки появится, окно нужно и ему.

**Списки терминалов — два разных запроса.** Экрану управления терминалами нужна полная
постраничная карточка (`GET /api/v1/terminals`); всем, кому нужно лишь имя в выпадающем списке
или в таблице, — лёгкий `GET /api/v1/terminals/options` (Р-45): `PayByLinkPage` (берёт из ответа
только `ACTIVE`), `App.tsx` и `TransactionDetailPage` (берут **все**, включая заблокированные, —
иначе у старого платежа пропадёт имя терминала). Врезок со списками в `SettingsPage`, бравших
одну страницу по потолку (`size: 200`), больше нет — удалены 24.08.2026 (P3-6) вместе с остальной
неработавшей частью страницы; списки живут на своих экранах.

**Прокси на бэкенд** (`vite.config.ts`, только dev):
`/api/v1/auth`, `/api/v1/users` → `:8081` · `/api/v1/companies`, `/api/v1/terminals`,
`/api/v1/audit-logs` → `:8082` · `/api/v1/payment-links`, `/api/v1/transactions` → `:8080`.

**Что на моках:** блок 2FA на `LoginPage` (диалог мёртв — `setOtpDialogOpen(true)`
не вызывается; подпись «Secured with 2-Factor Authentication» под формой входа — то же ложное
утверждение о безопасности, что вычищено из настроек) и `terminalRids` из `utils/mockData.ts`
в фильтре терминалов. Всё остальное — реальный API.

**`HomePage` ничего не считает** (с 24.08.2026, P3-7; было 818 строк, стало 435). Сводку отдаёт
`GET /api/v1/dashboard/summary` (`pbl`, `DashboardService`), последние операции — обычная первая
страница `/api/v1/transactions` с **явным** `size: 10`. До P3-7 страница тянула транзакции и
ссылки **без параметров пагинации**, то есть по двадцать строк умолчания контроллеров, сводила
их в браузере и подписывала «All system transactions» и «Real…». **Не возвращать** удалённое:
константу «Avg Processing Speed 1.2s», доли 60/40 от числа транзакций при отсутствии ссылок
нужного типа, бейджи `+100% Live`/`Active`/`Live Avg`, подстановку адреса и браузера вместо
незаписанных `clientIp`/`userAgent`, сочинение `statusHistory` из двух событий с одним временем
при переходе на карточку, недельный тренд с группировкой по **названию** дня недели и график,
мешавший в одних осях тип платежа с типом использования.

**Деньги на главной — всегда с валютой.** Колонки `currency` у `transactions` нет: она на
`payment_links`, и `CreatePaymentLinkRequest` принимает любой трёхбуквенный код. Поэтому в ответе
сводки нет ни одного сводного числа поверх валют, а на экране каждая валюта — свой блок карточек
и свой график. Выручка — `netAmount = сумма COALESCE(captured_amount, amount) по PAID_STATUSES
минус возвраты`: `amount` после частичного списания остаётся авторизованной суммой, а
`REFUNDED`/`PARTIALLY_REFUNDED` деньги получали и из выручки выпадать не должны.

**`SettingsPage` — плоская страница из двух живых контролов** (с 24.08.2026, P3-6; было 1357
строк, стало 185). Живы ровно: **название компании** — читается одиночным
`GET /api/v1/companies/{id}` по claim'у `companyId` из токена (не списком: `GET /api/v1/companies`
разрешён только `SYSTEM_ADMIN` и `AUDITOR`, остальным он отвечал 403 **и писал отказ в журнал
аудита** при каждом открытии страницы) и сохраняется `PATCH`'ем; **язык интерфейса**
(`setLanguage`). Поле названия редактируемо только у `SYSTEM_ADMIN` — `updateCompany` пускает
только его; у кого нет `companyId` (системные администратор и аудитор), карточка не показывается
вовсе. Кнопка «Сохранить» показывает зелёную полосу **только на 2xx**, отказ — текстом бэкенда.
**Не возвращать** удалённое: вкладки Security (2FA, тайм-аут сессии, «активные сессии»), Payment
(девять переключателей, включая «3-D Secure обязателен»), API & Webhooks (ключ, вебхуки на домен
`api.acmecorp.com` из шаблона), поля Account (телефон, адрес, ИНН, контактное лицо) и Display
(тема, форматы даты и времени, часовой пояс, валюта, строк на странице) — ни одного из этих полей
нет ни в API, ни в базе, а три утверждения вкладки Security были ложными утверждениями
**о безопасности**. Осиротевшие ключи `settings.*` из `translations.ts` удалены тем же заходом
во всех трёх языках.

`utils/mockData.ts` вопреки названию содержит **живые** форматтеры
(`formatCurrency`, `formatDateTime`, `getStatusLabel`) — их не удалять.

---

## 10. Грабли — прочитать до первой правки

Полный разбор: `code_review.md`. Здесь — то, что чаще всего ломает работу.

### Открытые блокеры (не «исправлено», а «есть прямо сейчас»)

Нумерация — по ID из `code_review.md` / `fix_plan.md`, чтобы не «ехала» при закрытии пунктов.

- Сейчас — ни одного: P0-9 закрыт 19.08.2026, вместе с ним закрыт и последний пункт спринта 1 —
  P1-16 (см. ниже). Что остаётся вне нашего контроля — пароль заказа в адресе запроса к эквайеру
  (Р-25) — записано в `problems.md` §5; суммы возвратов, сделанных вне портала, сервис не читает
  (`SETTLED_OTHER` — ручной разбор) — `problems.md` §6. Принятые размены по входу (P3-Auth,
  19.08.2026): верный пароль для заблокированного аккаунта отличим от неверного — `problems.md` §7;
  лимит попыток не распространяется на `/auth/refresh` — `problems.md` §8. Блокировка терминала
  действует только в портале, в MilliKart он продолжает работать (P2-8, 21.08.2026) —
  `problems.md` §12. Все три сервиса ходят в базу суперпользователем, поэтому права на журнал
  аудита (Р-42) пока ни на что не влияют — `problems.md` §13.

### Закрытые блокеры

- **P1-13 (18.08.2026). Фронтенд: авторизация — роли, маршруты, хранение и обновление токена.**
  Было: `role || 'SYSTEM_ADMIN'` в `AuthContext` и `Header` (fail-open), профиль с `id: 1` и
  `fullName` из email, `JSON.parse(localStorage)` без `try/catch` (белый экран при мусоре
  в хранилище), access-токен в `localStorage`, `isAuthenticated: !!token` без срока, `ProtectedRoute`
  только по факту входа (`/users`, `/audit-logs` открыты всем), `navigate('/home')` в несуществующий
  маршрут, ни `errorElement`, ни `*`. Что сделано — фронтенд плюс одна переменная бэкенда:
  1. `types/role.ts` — union из пяти ролей и `parseRole` (строго, как `Role.fromValue`).
  2. `auth/session.ts` — access **только в памяти**, refresh — `localStorage['mp_refresh_token']`,
     профиль `{ email, role, companyId? }` (email/companyId из claims JWT — эндпоинта `/me` нет);
     нераспознанная роль или ответ без токенов → `AuthError`, сессия не создаётся. Легаси-ключи
     `token`/`user` прежней версии сносятся при загрузке; событие `storage` (в т.ч. `clear()`)
     гасит сессию в остальных вкладках.
  3. `api/client.ts` — 401 → single-flight `refreshSession()` → повтор запроса; `/login`, `/refresh`,
     `/logout` исключены (иначе цикл); повторный 401 → `clearSession`. Сетевая ошибка `/refresh`
     сессию не сбрасывает. **Поколение сессии** (`getSessionGeneration`): ответ `/refresh`, ушедшего
     до выхода, не применяется, а его свежий refresh-токен гасится через `/logout` — иначе
     только что вышедший пользователь оказывался бы снова внутри. Отклонённый вход/refresh
     (нераспознанная роль) тоже гасит выпущенный сервером refresh-токен. В консоль ошибки
     пишутся через `describeError` (статус + путь), а не объектом `AxiosError` — в нём тело
     запроса с refresh-токеном.
  4. `AuthContext` — восстановление при загрузке через `/refresh` с экраном загрузки;
     `logout` = сброс сразу + `POST /logout` вдогонку (ошибка логируется).
  5. `auth/routeAccess.ts` + `RoleRoute` + фильтр сайдбара из одного списка; `ForbiddenPage`,
     `NotFoundPage` (`*`), `RouteErrorPage` (`errorElement`), `PublicOnlyRoute` → `/`, вход → `/`;
     `createRouter(props: AppRouterProps)` вместо `any`.
  6. `App.tsx`: список транзакций запрашивается **после** появления сессии (`AppShell` под
     `AuthProvider`), а не при первом рендере до логина.
  7. Бэкенд — только конфиг: `expiration-ms: ${JWT_EXPIRATION_MS:900000}` в `auth`, `directory`, `pbl`.

  Следствие для отзыва: заблокированный/удалённый/вышедший пользователь теряет доступ не позже чем
  через 15 минут (следующий `/refresh` отклонён → сессия сброшена → страница входа); при
  перезагрузке вкладки — сразу. Не сделано намеренно: httpOnly-cookie (правки бэкенда — Set-Cookie,
  CSRF, SameSite), `/me` для настоящего `fullName`, упреждающее обновление по `expiresIn`
  (обновление реактивное, по 401), `strict`. Регистр статусов (P2-12) закрыт 20.08.2026 — см. ниже.
  Проверено: `npm run typecheck`, `npm run build`, `./gradlew test`, ручной прогон в браузере
  (вход, перезагрузка, `/users` под `COMPANY_EMPLOYEE`/`SYSTEM_ADMIN`, 404, выход, повтор refresh
  после выхода → 401, обновление по истечении с `JWT_EXPIRATION_MS=60000`).

- **P1-12 (18.08.2026). Refresh-токены, выход и отзыв доступа — частично.** `AuthController`
  содержал только `/login`; заблокированный или удалённый пользователь работал до истечения
  access-токена (24 ч), «выход» был стиранием `localStorage` при живом токене, а `expiresIn`
  в ответе логина был литералом `86400`, не связанным с `pbl.security.jwt.expiration-ms`
  (в тестовом профиле расходился вчетверо). Что сделано — только бэкенд:

  1. **Таблица `refresh_tokens`** (`auth/003-refresh-tokens.xml`, четыре changeset'а по
     конвенции P1-2). Хранится **SHA-256 токена**, не токен: 256 бит случайности перебирать
     нечего, а поиск по значению должен быть индексируемым — BCrypt здесь неуместен
     (обоснование в javadoc `RefreshTokenService`, не удаляй). FK на `users` с
     `ON DELETE CASCADE` — иначе `userRepository.deleteAll()` в фикстурах тестов ронял бы FK.
  2. **Токен — непрозрачная случайная строка** (32 байта `SecureRandom`, base64url), не JWT.
  3. **`POST /api/v1/auth/refresh`** — ротация: новый access + новый refresh **в той же
     цепочке** (`family_id`), старый получает `rotated_at`. Порядок проверок в
     `AuthService.refresh` фиксирован и прокомментирован по шагам: неизвестен → просрочен →
     цепочка отозвана → уже заменён (в окне `rotation-grace` = гонка вкладок, обслуживаем;
     за окном = **кража**, гасим всю цепочку, `ERROR` с маркером **`REFRESH_TOKEN_REUSE`**) →
     пользователь не `ACTIVE` (гасим цепочку). Любой отказ — один и тот же
     `401 "Invalid refresh token"`. `rotated_at` **не сдвигается** при повторе внутри окна —
     иначе украденный токен можно держать живым, дёргая его чаще окна.
     Метод — `@Transactional(noRollbackFor = UnauthorizedException.class)`: отзыв цепочки
     обязан пережить 401. **Не убирай `noRollbackFor`** — на этом стоят
     `refresh_withRotatedTokenAfterGrace_revokesWholeFamily` и `refresh_forBlockedUser_returns401AndRevokes`.
     Повтор ротированного токена по **уже погашенной** цепочке за окном тоже пишет ERROR
     с маркером (без повторного отзыва) — мониторинг не должен слепнуть оттого, что пользователь
     успел выйти раньше.
     **Гонка «refresh против отзыва» закрыта на уровне запросов, не «упрощай» это обратно:**
     ротация — условный `UPDATE … SET rotated_at = COALESCE(rotated_at, :now) WHERE id = :id AND
     revoked_at IS NULL` (`RefreshTokenRepository.markRotatedIfLive`; 0 строк → 401), а не save
     прочитанной сущности — иначе dirty-check писал бы `revoked_at = NULL` поверх logout'а,
     случившегося между чтением и коммитом. Отзыв цепочки/пользователя сначала берёт
     `SELECT … FOR UPDATE` (`lockFamily` / `lockAllForUser`) и лишь потом делает bulk UPDATE:
     так он дожидается идущего refresh и видит вставленного им преемника. На этом стоят три
     теста `RefreshTokenConcurrencyTest` (проверено обратно: без блокировки краснеет второй,
     с dirty-check вместо условного UPDATE — оба).
  4. **`POST /api/v1/auth/logout`** — гасит **всю цепочку**, отвечает **204 всегда** (неизвестный
     токен, пустое тело — тоже 204): иначе эндпоинт становится оракулом существования токенов.
  5. **`UserService`** гасит все refresh-токены пользователя при `status` ≠ `ACTIVE`
     и при `deleteUser`. Без этого шаг «пользователь не ACTIVE» в `refresh` — не защита:
     заблокированный просто продолжал бы обновляться, пока не предъявит токен.
  6. **`expiresIn` считается из `JwtProvider.getExpirationMs()`** (новый геттер в `common`);
     литерала `86400` в `auth/src/main/java` больше нет.
  7. **Уборка** — `RefreshTokenCleanupScheduler` (тонкий, как `TransactionReconciliationScheduler`),
     `@EnableScheduling` на `AuthApplication`, флаг `auth.refresh.cleanup-enabled`
     (в тестовом профиле `false`).

  **Ограничение, о котором надо помнить: access-токен при выходе не отзывается.** Он проверяется
  stateless (подпись + срок), чёрного списка нет намеренно (см. §5, инвариант 4). После
  logout/блокировки/удаления уже выданный access-токен работает до `exp`. С 18.08.2026 (P1-13)
  это **15 минут**: фронтенд научился звать `/refresh`, и `JWT_EXPIRATION_MS` сокращён до `900000`
  во всех трёх `application.yaml` (до этого — 24 ч, и короткий токен выкидывал бы пользователя).
  Ни `PublicEndpoints`, ни `JwtAuthFilter`, ни `SecurityConfig` не менялись: новые пути
  попадают под уже существующий `/api/v1/auth/**`.
  Настройки — `auth.refresh.*` (`AUTH_REFRESH_TTL` P30D, `AUTH_REFRESH_ROTATION_GRACE` PT10S,
  `AUTH_REFRESH_CLEANUP_ENABLED`, `AUTH_REFRESH_CLEANUP_CRON`). Покрыто пятнадцатью тестами
  `RefreshTokenIntegrationTest` и тремя `RefreshTokenConcurrencyTest` (§11).

  Известные ограничения схемы, принятые осознанно (не дефекты, а следствия ТЗ; кандидаты
  в отдельные задачи): (а) повтор в окне снисхождения **раздваивает** цепочку — обе ветки
  живут дальше независимо, и старый токен больше никто не предъявит, так что для этой цепочки
  детектор кражи не сработает никогда; (б) срок refresh-токена скользящий (30 дней от последнего
  обновления), абсолютного потолка сессии нет; (в) смена пароля через `PATCH /users/{id}` refresh-токены
  **не** гасит — только смена статуса и удаление, как в ТЗ.

- **P1-9 (18.08.2026). У ссылки есть срок жизни, и он ограничен сверху.**
  `CreatePaymentLinkRequest` не имел поля `expiresAt`, а `create` его не выставлял: все ссылки
  создавались с `expires_at = NULL`, `PaymentLinkScheduler` исправно искал просроченные и не
  находил ничего. При этом `PaymentLinkResponse` — в отличие от `PaymentLinkSummaryResponse` —
  поля тоже не отдавал, и фронтенд (`PayByLinkPage.tsx:297`) подставлял «сейчас + 24 часа»
  и рисовал по нему обратный отсчёт для ссылки, которая не истекает никогда.

  1. **Срок выставляется всегда.** Не передан `expiresAt` → `Instant.now().plus(default-ttl)`,
     по умолчанию **24 часа** (совпадает с тем, что уже показывал интерфейс).
  2. **Мерчант может задать свой срок** — при создании и через PATCH. Обе проверки —
     в приватном `PaymentLinkService.validateExpiresAt`, обе дают `BusinessException` → 400:
     срок в прошлом или «прямо сейчас», и срок дальше потолка. Не `@Future` на DTO намеренно:
     вторая граница всё равно живёт в сервисе, а сообщение должно называть предельную дату.
  3. **Потолок — `pbl.link.max-ttl`, по умолчанию 90 дней, и он считается от `created_at`
     ссылки, а не от момента запроса.** В `update` это принципиально: от «сейчас» его можно
     было бы сдвигать каждым PATCH'ем ещё на 90 дней, и потолок перестал бы что-либо значить.
     **Не «упрощай» это до `Instant.now()`** — на этом стоит
     `updateLink_onAnOldLink_cannotExtendBeyondMaxTtlFromCreation`.
  4. **`PaymentLinkResponse.expiresAt`** добавлен рядом с `createdAt`.
  5. Настройки — `pbl.link.default-ttl` / `pbl.link.max-ttl` (`PBL_LINK_DEFAULT_TTL` /
     `PBL_LINK_MAX_TTL`), формат `Duration` ISO-8601. В тестовом профиле те же значения.

  Миграции нет и не нужно: боевых данных нет, а `NULL` в `expires_at` корректно означает
  «без срока» и для планировщика, и для проверки при открытии. `PaymentLinkScheduler` не тронут —
  он и раньше умел всё, что нужно, ему просто нечего было просрочивать.
  Фронтенд не тронут: запасной вариант `new Date(Date.now() + 86400000)`
  в `PayByLinkPage.tsx:214,297` и `PayByLinkDetailPage.tsx:420` стал мёртвым кодом; его снятие —
  отдельная уборка (в P1-14 и P1-13 намеренно не трогали: это правка поведения вне их объёма).
  Покрыто десятью тестами `PaymentLinkIntegrationTest` (§11).

- **P1-5 + P1-6 + P1-7 (17.08.2026). Открытие ссылки: одна транзакция под блокировкой, холд
  неприкосновенен, платёж считается один раз.** Три дефекта в одном методе и вокруг него,
  поэтому чинились связкой.

  1. **`openAndBuildRedirect` — один `@Transactional`-метод**, начинающийся с
     `PaymentLinkRepository.findWithLockById` (`SELECT … FOR UPDATE`, hint
     `jakarta.persistence.lock.timeout = 10000`). Четырёх фаз и `TransactionTemplate`
     в `OpenLinkService` больше нет. Было: проверка «уже оплачена / лимит исчерпан» в одной
     транзакции, HTTP-вызов вне транзакции, вставка `Transaction` в третьей — два одновременных
     открытия одноразовой ссылки проходили проверку вместе, оба заводили заказ у эквайера,
     и ссылку можно было оплатить дважды. `@Version` на `PaymentLink` в этом пути не участвовал
     и не участвует: он защищает конкурирующие апдейты, а не окно между проверкой и вставкой.
  2. **Блокировка удерживается на время вызова эквайера** (3 с соединение + 10 с чтение,
     `RestTemplateConfig:24-26`) — компромисс записан javadoc'ом на методе. Конкурировать
     за неё могут только одновременные открытия **одной и той же** ссылки, что и требуется
     сериализовать; другие ссылки и другие таблицы блокировка не трогает. Второй компромисс
     там же: если провайдер ответил успешно, а коммит упал, у эквайера останется неоплаченный
     заказ без локальной записи — он истечёт сам, и это лучше двойной оплаты.
  3. **Сериализации оказалось недостаточно, и это стоит понимать.** Под блокировкой второй
     вызов честно гасил только что созданный `PENDING` и заводил **второй живой заказ**
     у эквайера — отменить первый нечем (Void нет). Поэтому у гашения появилось условие:
     `PENDING`, созданный **после** начала текущего запроса (то есть пока тот стоял в очереди
     за блокировкой), — это второй одновременный клик, а не брошенная сессия; такой запрос
     получает `ConflictException` → **409**. Последовательное переоткрытие (`createdAt` раньше
     начала запроса) работает как раньше: гасим `PENDING`, выдаём новую сессию.
  4. **`AUTHORIZED` не гасится никогда** (P1-6) — в списке гашения остался только `PENDING`.
     Это деньги, реально захолдированные на карте: локальный `FAILED` не убирал холд у эквайера,
     деньги зависали у держателя карты, а мерчант не мог ни захватить их, ни отменить, потому
     что в портале транзакция читалась как неуспешная. Вместо этого холд **занимает слот**:
     лимит считается по `countByLinkIdAndStatusIn(id, EnumSet.of(SUCCESS, AUTHORIZED))`.
     Отказы различимы намеренно — «Single-use payment link has already been used» и
     «Payment link has an authorized payment awaiting capture» это разные ситуации для мерчанта.
  5. **`+ 1` в `refreshStatus` убран** (P1-7). Hibernate делает auto-flush перед JPQL-запросом,
     поэтому только что переведённая в `SUCCESS` транзакция уже учтена подсчётом, а прибавка
     считала её второй раз: многоразовая ссылка с `maxPayments = 2` закрывалась после первой
     оплаты. В `completeDms` (`:346`) такой же подсчёт всегда шёл без прибавки — это была опечатка.

  Известное ограничение, отдельная задача: **операции Void у `AcquiringClient` нет** (в коллекции
  TXPG она есть — `phase: "Single"`, `voidKind: "Full"`, запрос Reversal в
  `NON-PSP Ecom.postman_collection.json`). Пока её нет, брошенный авторизованный холд блокирует
  одноразовую ссылку, пока банк не снимет холд сам. Не «чини» это возвратом гашения `AUTHORIZED`.

  Покрыто `OpenLinkConcurrencyTest` (2) и шестью новыми тестами в `PaymentLinkIntegrationTest`;
  проверено обратно по каждому дефекту отдельно (§11).

- **P1-2 (17.08.2026). Миграции не зависят от порядка запуска сервисов.**
  Требования «поднимай `auth` первым» больше нет, и `fk_terminals_company` больше не теряется.
  Было две ошибки, зеркальные друг другу:

  1. `2-auth-core` создавал `companies`, `users` и FK **без единого `<preConditions>`**, поэтому
     стартовавший раньше `directory` (у которого условия были) ронял `auth` на
     «table companies already exists».
  2. `2-auth-terminal-fk` стоял под `onFail="MARK_RAN"`, а таблицу `terminals` создаёт другой
     сервис. По рекомендованному порядку `auth` стартовал первым, `terminals` ещё не было,
     changeset уходил в MARK_RAN — то есть записывался в `DATABASECHANGELOG` как выполненный —
     и внешний ключ **не создавался никогда**. Следование нашей же инструкции тихо теряло
     ограничение целостности.

  Стало:

  1. **Один changeset — один объект, у каждого своё условие.** `2-auth-core` разбит на
     `2-auth-companies`, `2-auth-users` и `2-auth-users-company-fk`. Условие на весь блок ставить
     нельзя: с уже существующей `companies` и отсутствующей `users` весь changeset ушёл бы
     в MARK_RAN, `users` не создалась бы, и сервис упал бы на `ddl-auto: validate`.
  2. **`onFail="CONTINUE"` там, где changeset зависит от таблицы чужого сервиса**
     (`2-auth-terminal-fk`, `2-auth-users-company-fk`). CONTINUE **не пишет** строку
     в `DATABASECHANGELOG` и повторяет проверку на следующем старте — это и означает
     «попробуй позже». MARK_RAN означает «считай сделанным» и здесь просто неверен по смыслу.
     **Не «унифицируй» это обратно на MARK_RAN ради единообразия** — комментарий в changeset'е
     стоит ровно за этим.
  3. **Идемпотентность в `pbl`:** `002-add-indexes.xml` разбит на три changeset'а с
     `<not><indexExists .../></not>`, `003-add-client-ip-and-user-agent.xml` — на два с
     `<not><columnExists .../></not>`, у `004-add-captured-amount.xml` добавлено такое же условие.
     Межсервисного конфликта там нет (таблицами владеет только `pbl`), это защита от базы,
     восстановленной без `DATABASECHANGELOG`.

  `directory/003-directory-schema.xml` и `pbl/001-initial-schema.xml` не трогали — условия там
  уже стояли. Проверено на H2 (`MODE=PostgreSQL`), что `foreignKeyConstraintExists` и `indexExists`
  работают в обеих ветках; для `foreignKeyConstraintExists` указан `foreignKeyTableName`.
  Покрыто `MigrationOrderTest` (5 методов, §11) — единственным тестом, который гоняет Liquibase
  вручную на отдельной базе. Проверено обратно: со старым changelog краснеют
  `authMigrations_whenCompaniesAlreadyExists_succeed` и
  `terminalFk_isCreatedOnALaterRun_whenTerminalsAppears`.

- **P1-1 (17.08.2026). Spring Security включён по-настоящему; actuator и swagger закрыты.**
  Было `anyRequest().permitAll()` — Spring Security пропускал всё, единственным барьером был
  разбор префикса `/api/v1/` внутри `JwtAuthFilter`, поэтому наружу смотрели `/actuator/health`
  с `show-details: always`, `/actuator/metrics`, `/actuator/info`, `/swagger-ui.html` и
  `/v3/api-docs` во всех трёх сервисах. Стало (подробности — §6):

  1. **`anyRequest().authenticated()`** — модель перевернулась на «по умолчанию запрещено».
  2. **Список публичных путей — только в `PublicEndpoints`**, читают его оба слоя.
     **Не заводи вторую копию** и не добавляй пути «по месту» ни в фильтр, ни в конфиг.
  3. **Actuator — на отдельном порту, привязанном к `127.0.0.1`** (9081/9082/9080).
  4. **Swagger — за флагом `SWAGGER_ENABLED`, по умолчанию выключен.**
  5. **Отказы одного формата:** `SecurityErrorResponder` обслуживает и фильтр, и Spring Security.

  Два слоя защищают одно и то же намеренно — это не дублирование, которое надо «упростить»:
  откат любого одного из них ловится тестами по отдельности (эксперименты в `fix_plan.md`).
  Заодно `NoResourceFoundException` перестал превращаться в 500: несуществующий путь —
  это 404, а не «Unexpected server error» с ERROR в логе.

  Сознательное ужесточение: `/api/v1/payment-links/*/open` вместо «любой путь, оканчивающийся
  на `/open`». Покрыто `SecurityBoundaryIntegrationTest` во всех трёх модулях,
  `SpringSecurityLayerIntegrationTest`, `ManagementPortIntegrationTest` и `PublicEndpointsTest`.

- **P0-5 + P0-6 + P1-11 (17.08.2026). Секретов в репозитории больше нет, дефолтного админа тоже.**

  1. **Ключ подписи JWT — только из `JWT_SECRET`, без дефолта.** Дефолт снят из
     `JwtProvider` и литералы убраны из `auth/…/application.yaml` и `directory/…/application.yaml`;
     в `pbl` секрет добавлен явно (раньше он молча брал дефолт провайдера). Конструктор
     `JwtProvider` отказывается стартовать на пустом ключе, на ключе короче 32 байт (HS256
     подписывает 256-битным хешем) и на скомпрометированном ключе из истории git. Последняя
     проверка идёт **по SHA-256** (`d291951…`) — чтобы литерал не вернулся в исходники вместе
     с проверкой на него. Историю git не переписывали: после ротации старый ключ бесполезен,
     а `filter-repo` сломал бы все клоны. Главный тест —
     `JwtProviderTest.constructor_compromisedSecret_throws`.
  2. **Пароль БД — из `DB_PASSWORD`, без дефолта** (P1-11). `DB_URL` и `DB_USERNAME` дефолты
     сохранили: это не секреты, и они упрощают локальный запуск.
  3. **Статический токен `pbl.security.api-token` — из `PBL_API_TOKEN`, без дефолта.**
     Значение `pbl-secret-token` было в yaml `pbl`, в `@Value` у `JwtAuthFilter` и в тестовом
     профиле, а токен даёт `SYSTEM_ADMIN` без пароля. Флаг по умолчанию `false`; включённый флаг
     с пустым токеном — отказ стартовать.
  4. **Сид админа удалён из `002-user-directory-schema.xml`.** Changeset правили **прямо**,
     не новым файлом: боевых установок нет, БД пересоздаётся, а `<insert>` с паролем в
     комментарии рядом с хешем нельзя оставлять в истории миграций. Это единственное
     согласованное исключение из §12.6. Вместо сида —
     `auth/.../bootstrap/AdminBootstrapRunner.java`: `@ConditionalOnProperty` на
     `auth.bootstrap.enabled`, работает **только на пустой таблице `users`**, читает
     `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD`, гоняет пароль через тот же
     `PasswordConstraintValidator`, что и обычных пользователей, и пишет факт создания
     в WARN без пароля. Нет переменных или слабый пароль — `IllegalStateException`, сервис
     не стартует.

  Тонкость, на которой легко обжечься: **`@ConfigurationProperties` не считает нерезолвнутый
  `${...}` ошибкой.** `spring.datasource.password: ${DB_PASSWORD}` без переменной уезжает
  в драйвер как строка `${DB_PASSWORD}`, и сервис падает много позже — на «password
  authentication failed», где про причину не сказано ни слова. `@Value` (это `JWT_SECRET`
  и `PBL_API_TOKEN`) на том же месте бросает сразу. Оба случая переводит в инструкцию
  `common/.../security/MissingSecretFailureAnalyzer.java`.

  `pbl.base-url` и адреса провайдера вынесены в окружение следом (P1-10, 18.08.2026, см. ниже);
  тот же `MissingSecretFailureAnalyzer` объясняет теперь и их отсутствие — отдельным текстом,
  потому что причина у адресов другая. Что осталось: флага «сменить пароль при первом входе»
  нет — нужна колонка в БД и правка флоу логина, отдельная задача.

- **P1-10 (18.08.2026). Адреса `pbl` — из окружения, HTTP у эквайера — предупреждение.**
  Было: `pbl.base-url: http://localhost:8080/` литералом в боевом yaml, без `${}`. Это значение
  уходит эквайеру как `hppRedirectUrl` (`OpenLinkService`), поэтому в проде TXPG вернул бы
  плательщика на `localhost` — сервис при этом стартует, ссылки создаются, ошибок нет, просто
  ни один платёж не завершается. Там же литералами стояли адреса тестового стенда, причём
  `api-base-url` по голому HTTP, а по нему уходит Basic-авторизация с логином и паролем терминала.
  У `TxpgAcquiringClient` вдобавок были свои дефолты в `@Value`, **противоречившие** yaml
  (`:8083` против `:8000`, `/api/order/…` против `/order/…`): пропади ключ из конфига — клиент
  молча ушёл бы на другой хост. Что сделано:
  1. `pbl.base-url`, `pbl.provider.gateway-base-url`, `pbl.provider.api-base-url` —
     `${PBL_BASE_URL}`, `${PBL_PROVIDER_GATEWAY_BASE_URL}`, `${PBL_PROVIDER_API_BASE_URL}`
     **без дефолтов** (Р-16, Р-18): не задано — не стартует, как `JWT_SECRET`. Дефолт на тестовый
     стенд хуже несостоявшегося старта: деньги не двигаются, а портал показывает «оплачено».
     Пути (`create-order-path` и два других) дефолты сохранили — это константы протокола, в yaml
     они взяты в кавычки из-за `{orderId}`. У `TxpgAcquiringClient` встроенные дефолты сняты:
     **дефолт живёт в одном месте — в yaml.**
  2. `pbl/.../config/UrlConfigurationCheck.java` — `@Component`, вся работа в конструкторе.
     Пустое значение, пробел/перевод строки по краям (значение используется консьюмерами
     **как есть**, `@Value` не триммит — хвостовой пробел превратил бы каждый URL в
     `https://host/%20/order`), не-URI, относительный путь, хост, который `java.net.URI` не
     читает (незаменённый `ВАШ_ДОМЕН`, подчёркивание), схема кроме `http`/`https` →
     `IllegalStateException` с `HOW_TO_FIX` в стиле `JwtProvider` (имя переменной, что получили,
     пример). Не-HTTPS у любого адреса провайдера → **WARN в рамке** с адресом, переменной и
     явным «по этому каналу уходит Basic-авторизация с логином и паролем терминала»; не-HTTPS
     у `pbl.base-url` → WARN, кроме хостов `localhost`, `127.0.0.1` и `[::1]` (штатный локальный
     запуск — предупреждение на каждом старте разработчика приучает предупреждения игнорировать).
     **Старт из-за HTTP не блокируется, и флага-исключения вида `PBL_ALLOW_INSECURE_PROVIDER`
     нет и не будет** (Р-17): HTTPS — на стороне MilliKart, запретом мы бы просто не дали сервису
     работать с тем, что есть. Слэши не нормализуются — `UriComponentsBuilder` схлопывает `//`
     сам, `base-url` с завершающим слэшем уже работает.
  3. `MissingSecretFailureAnalyzer` знает три новые переменные; `description()` разведён на два
     текста — секреты («дефолт — так ключ и попал в репозиторий») и адреса («дефолт — это прод,
     который отправляет плательщиков на localhost или деньги тестовому эквайеру»). Имя класса
     и `spring.factories` не трогали.
  4. Тесты: `UrlConfigurationCheckTest` (`pbl`, юнит, лог через `ListAppender`) и
     `ConfigurationExternalizationTest` (`pbl`, без Spring) — сторож от повторного захардкоживания:
     читает боевой yaml с диска, запрещает подстроки `millikart.az` и `localhost:8080` и требует,
     чтобы три адреса были ровно `${VAR}` без `:дефолта` внутри плейсхолдера.
     Со стабом провайдера (`pbl.provider.stub=true`) адреса эквайера никем не читались, но
     были обязательны по-прежнему. С 20.08.2026 это неактуально: стаба нет, адреса читает
     единственный клиент, и локальный запуск ходит на тестовый стенд (§7).
     Тестовый `application.yaml` **не менялся**: там литеральные `example.com`-значения нарочно,
     чтобы тесты не зависели от окружения машины.
  5. Документы: `.env.example` (три переменные в «ОБЯЗАТЕЛЬНЫЕ», пути — в «НЕОБЯЗАТЕЛЬНЫЕ»),
     `deployment_guide.md` §8.1–8.3 и §20 (`ВАШ_ДОМЕН` больше не правится в yaml — задаётся
     переменной; `[!CAUTION]` про то, что неверный `PBL_BASE_URL` не роняет сервис, а ломает платежи).
     Попутно в yaml-блок `pbl` в §8.1 добавлен пропущенный блок `pbl.reconciliation`: гайд
     подключает файл через `--spring.config.location=file:…` (он **заменяет** встроенный yaml),
     а `TransactionReconciliationService` читает `min-age`/`max-age`/`batch-size` без дефолтов —
     установка строго по гайду падала бы на `Could not resolve placeholder 'pbl.reconciliation.min-age'`.

  Чего **не** делали и не надо: не меняли сами адреса тестового стенда (переезд эквайера на HTTPS —
  разговор с MilliKart), не трогали `frontend/.env.example` и `VITE_API_BASE_URL`, не выносили
  `pbl.provider.stub` в окружение, не добавляли `server.port` в yaml `pbl`. (Флаг после этого
  не переехал в окружение, а исчез вовсе — 20.08.2026, §7.)

- **P0-8 (17.08.2026, остаток). Частичный capture работает, и он не открыл дыру в возвратах.**
  MilliKart подтвердили, что `phase: "Clearing"` принимает `amount` (Р-11 разблокировано), и
  `TxpgAcquiringClient.completeDms` теперь шлёт
  `{"tran": {"phase": "Clearing", "amount": "500.00"}}` вместо тела без суммы.

  Само по себе это было бы дырой в другую сторону, поэтому задача больше, чем одно поле:

  1. **Захваченная сумма хранится отдельно** — `transactions.captured_amount`
     (миграция `004-add-captured-amount.xml`, **nullable**) и `Transaction.capturedAmount`
     **без** `@Builder.Default`. `null` = «capture не выполнялся»; у SMS-платежей стадии capture
     нет вовсе, и колонка остаётся пустой. `transaction.amount` после capture **не меняется** —
     там авторизованная сумма, это история операции. Новых значений в `TransactionStatus` не
     появилось: частичный capture — это по-прежнему `SUCCESS`, просто на меньшую сумму.
  2. **Потолок возврата считается от захваченной суммы** —
     `PaymentLinkService.refundableBase(tx)`: `capturedAmount`, а если он пуст (SMS) — `amount`.
     Раньше оба места (проверка потолка и переход в `REFUNDED`) смотрели на `amount`, и после
     включения частичного capture мерчант мог вернуть 1500 из авторизованных, реально списав 500:
     1000 никогда не уходили с карты. **Не возвращай `transaction.getAmount()` в эти две точки** —
     ровно на этом стоит `refund_afterPartialCapture_cannotExceedCapturedAmount`.
  3. **Валидация суммы capture** в `completeDms`: больше авторизованной — отказ
     (`capture amount exceeds the authorized amount`); больше двух знаков после запятой — отказ.
     Ноль и отрицательные отсекает `@Positive` на DTO. WARN-заглушка про открытый вопрос
     с MilliKart убрана.
  4. **Формат суммы для провайдера** — `TxpgAcquiringClient.formatAmount`:
     `setScale(2, RoundingMode.UNNECESSARY).toPlainString()`, и в `completeDms`, и в `refund`.
     Было `amount.toString()`, а он даёт научную нотацию (`new BigDecimal("1E+3").toString()`
     → `"1E+3"`), причём суммы приходят из JSON с произвольной точностью. `UNNECESSARY` выбран
     сознательно — молча округлять деньги нельзя; третий знак отсекается валидацией в сервисе
     (в `refund` — той же), поэтому до клиента исключение не доходит.
  5. **`TransactionResponse.capturedAmount`** — иначе мерчант не видит, сколько реально списано.

  Покрыто восемью новыми тестами `MoneyOperationsIntegrationTest` и двумя
  `TxpgAcquiringClientTest` (тело Clearing и формат суммы на живом клиенте).

- **P0-7 + часть P0-8 (17.08.2026). Денежные операции больше не проводятся вслепую.**
  Три отдельные дыры, закрытые одной задачей:

  1. **`@Retry` снят с `completeDms` и `refund`.** Это неидемпотентные операции: при таймауте
     чтения (10 сек, `RestTemplateConfig:26`) запрос мог уже исполниться на стороне TXPG,
     а `maxAttempts: 3` отправлял его ещё дважды. `@CircuitBreaker` оставлен — он не
     переотправляет запрос, а только размыкает цепь. **Не вешай `@Retry` на денежные вызовы.**
  2. **Неизвестный исход отделён от отказа.** Раньше и таймаут, и 5xx, и явный отказ шлюза
     сваливались в `BusinessException` → 400, мерчант читал это как «не прошло» и повторял
     возврат руками — двойной возврат без всякого retry. Теперь неопределённость даёт
     `PaymentOutcomeUnknownException` → 502 с текстом «проверьте статус перед повтором»
     и ERROR-маркером `PAYMENT_OUTCOME_UNKNOWN` в логе. Таблица классификации — в §7.
     **Не сводить эти два случая обратно в один `catch`** и не наследовать новое исключение
     от `BusinessException`.
  3. **Повторный capture запрещён.** `PaymentLinkService.completeDms` принимал транзакцию
     в статусе `SUCCESS` — то есть позволял захватить один и тот же заказ дважды. Теперь
     `SUCCESS` → отказ `Transaction has already been captured`; `AUTHORIZED` → как раньше;
     `PENDING` → **не отказ сразу**: вызывается приватный `refreshStatus`, и решение принимается
     по свежему ответу эквайера. `PENDING` нельзя просто выкинуть из допустимых — страница
     плательщика опрашивает эквайер лишь один раз (P0-2), поэтому холд, поставленный после
     этого опроса, у нас ещё числится `PENDING`, и легитимный capture сломался бы.
     `PaymentOutcomeUnknownException` из `refreshStatus` пробрасывается наверх намеренно:
     капчурить по статусу, который не удалось прочитать, нельзя.

  Заодно `refund` перестал выдумывать `refundId`: раньше при отсутствии поля в ответе провайдера
  в финансовый ответ уходил сгенерированный `REF-XXXXXXXX`, которого нет ни в одной системе.
  Теперь — `null` и WARN. **Не возвращай фабрикацию.**

  Что осталось незакрытым: идемпотентных ключей и таблицы `refunds` по-прежнему нет
  (решение Р-12, отдельная задача) — 502 говорит «проверь руками», а не «система разберётся
  сама». Сумма Clearing передаётся с 17.08.2026 — см. запись P0-8 выше.

  Покрыто десятью тестами `MoneyOperationsIntegrationTest` и восемью `TxpgAcquiringClientTest`.

- **P0-3 (17.08.2026). Кэша поверх проверок прав больше нет — кэша в `directory` нет вообще.**
  `@Cacheable` / `@CacheEvict` сняты с `TerminalService.getTerminal`/`updateTerminal`/`deleteTerminal`
  и `CompanyService.getCompany`/`updateCompany`/`deleteCompany` (решение Р-9). Кэш не чинили, а убрали:
  это выборка одной строки по PK на редких админских эндпоинтах, а таблицу `terminals` по id
  действительно долбит `pbl` — которому кэш в памяти `directory` не доставался никак.
  `common/.../config/CacheConfig.java` оставлен под будущий кэш в `pbl`; в его javadoc записано,
  почему он сейчас пустой.
  **Никогда не ставь `@Cacheable` на метод, внутри которого есть проверка доступа:** при попадании
  в кэш тело не выполняется, вместе с ним не выполняется и проверка — сотрудник чужой компании
  получал данные терминала (включая логин) на все 15 минут TTL.
  Покрыто `getTerminal_afterAnotherCompanyFetchedIt_stillReturns403` и
  `getCompany_afterAdminFetchedIt_stillReturns403ForForeignCompany` — оба краснеют, если вернуть аннотацию.

- **P1-15 (17.08.2026). `COMPANY_EMPLOYEE` больше не пишет терминалы.**
  `TerminalService.validateWriteAccessToCompany` раньше отсекала по роли только `AUDITOR`, дальше
  хватало совпадения `companyId` — поэтому рядовой сотрудник (и любая нераспознанная роль) создавал,
  менял и **удалял терминалы своей компании**, включая смену `login`/`password`. Теперь роль
  проверяется первой, по `TERMINAL_WRITE_ROLES` = `EnumSet.of(SYSTEM_ADMIN, COMPANY_HEAD, COMPANY_MANAGER)`
  (см. §6). Совпадение `companyId` само по себе прав на запись не даёт.
  Чтение (`validateReadAccessToCompany`) не тронуто.
  Покрыто пятью тестами `createTerminal_*` / `updateTerminal_asEmployee_returns403` /
  `deleteTerminal_asEmployee_returns403` и двумя тестами на чтение сотрудником.

- **P0-1 (15.08.2026). `GET /api/v1/transactions` больше не отдаёт транзакции всех компаний.**
  `PaymentLinkService.listTransactions` проверяет роль по `READ_ROLES` и фильтрует по терминалам
  компании; `SYSTEM_ADMIN` и `AUDITOR` читают глобально. Заодно снят N+1 на ленивом `link`
  (`@EntityGraph` в `TransactionRepository`) и выровнена семантика `AUDITOR` (см. §6).
  Покрыто семью тестами `listTransactions_*` в `PaymentLinkIntegrationTest`.
  Если правишь этот метод — не откати изоляцию.

- **P0-2 (15.08.2026). `GET /api/v1/transactions/{identifier}/status` больше не публичный.**
  Путь из whitelist `JwtAuthFilter` убран; метод требует JWT и роль из `READ_ROLES` с проверкой
  компании терминала (`PaymentLinkService.checkAndStatusUpdate(identifier, principal)`).
  Страница плательщика `redirect.html` рендерится Thymeleaf'ом целиком на сервере: JS,
  опроса и обращений к `/api/` в ней нет. Транзакция ищется по `{tx}` из пути (`merchantRid`,
  случайный UUID) через `refreshByMerchantRid`; query-параметры провайдера `ID`/`PASSWORD`/`STATUS`
  не объявлены в контроллере, поэтому не попадают ни в логику, ни в логи (заодно закрыт P1-4).
  На публичную страницу уходит только `PaymentReceiptView` — **не** `TransactionResponse`:
  без `clientIp`, `userAgent`, `providerOrderId`, маскированной карты, RRN и approval code.
  Покрыто девятью тестами `checkStatus_*` / `redirectPage_*`.
  Если правишь эти места — не верни публичный доступ и не расширяй `PaymentReceiptView`.

- **P0-4 (16.08.2026). Роли — `enum Role`, `GET /payment-links/{id}/transactions` починен.**
  `getTransactionsByLinkId` проверялся против `MERCHANT_ADMIN` / `MERCHANT_USER` — ролей, которых
  в системе нет, — и отдавал 403 всем, кроме `SYSTEM_ADMIN`. На `EnumSet` такой список не пишется,
  поэтому баг закрылся вместе с рефакторингом: теперь `READ_ROLES`, права совпадают с чтением
  самой ссылки. Заодно метод принимает `principal` целиком, а не `principal.getRole()` /
  `principal.getCompanyId()` — при `principal == null` там был NPE.
  Правила разбора роли и nullable-семантика `getRole()` — в §6. Покрыто `RoleTest` (7 методов)
  и четырьмя тестами `getLinkTransactions_*`.
  **Не заводи строковые литералы ролей обратно** — за этим следит критерий в §12.8.

- **P1-3 (16.08.2026). Зависшие `PENDING` больше не висят вечно.** Фоновая сверка
  `TransactionReconciliationService` + `TransactionReconciliationScheduler` (см. §7).
  Ключевой инвариант: `FAILED` ставится **только** когда эквайер реально ответил и ответ был
  нефинальным, а транзакция старше `max-age`. Если опрос бросил исключение — статус не меняется,
  пишется WARN, запись ждёт следующего прогона. **Не сводить эти два случая в один `catch`:**
  иначе суточная недоступность шлюза массово пометит `FAILED` реально оплаченные транзакции.
  Ссылка при этом не трогается — single-use остаётся `ACTIVE`, чтобы покупатель мог повторить.
  Покрыто восемью тестами `TransactionReconciliationIntegrationTest`.

- **P1-8a (19.08.2026). Словарь статусов заказа — из контракта; незнакомый статус не молчит
  и не даёт `FAILED`.** Было: цепочка строковых сравнений в `refreshStatus`, из которых в
  контракте эквайера (`pbl/TXPG-client-side-integration.md` §5.8.8) есть только `FullyPaid` и
  `Rejected`; `Expired`, `Closed`, `PartPaid`, `Cancelled` (в коде — `Canceled`, одна `l`) и
  `Refused` отсутствовали; незнакомое слово проваливалось молча, оставалось `PENDING`, и через
  `max-age` сверка гасила его как «брошено плательщиком» — полный возврат `Refused` превращался
  в `FAILED`. Сделано: `ProviderOrderStatus.classify(Object)` → `ProviderOrderOutcome`
  (таблица в §7), `refreshStatus` без жёсткого каста (`status` числом → `UNKNOWN`, не
  `ClassCastException`), `orderDetails == null` → `UNKNOWN` с WARN, маркеры
  `mpStatusOutcome`/`mpProviderStatus` в `providerResponse`, верхняя граница выборки
  `pbl.reconciliation.give-up-age`.
  **Инвариант (Р-20): незнакомый статус заказа никогда не приводит к `FAILED`.** Таймаут-переход
  в `FAILED` разрешён только при `NON_FINAL` (`Preparing`). Цена ошибок несимметрична: лишний
  `FAILED` закрывает оплаченный платёж как неуспешный, лишний `PENDING` — оставляет запись
  человеку. Источник словаря — §5.8.8, и он назван «MAJOR status field values», то есть может
  быть неполным; **DMS (`Order_DMS`) в контракте не описан вовсе** — `Authorized`/`Cleared`
  держатся на исходном коде и не удаляются, чтобы не сломать DMS-поток. Сравнение точное,
  регистрозависимое, без `trim`. `REFUNDED` по `Refused` не ставится: суммы мы не знаем,
  её даёт `order.trans[]` — отдельная задача, `problems.md` §6 (P1-16 по Р-26 читает оттуда только
  `rrn`/`approvalCode`). Покрыто `ProviderOrderStatusTest` (без Spring),
  девятью новыми тестами `TransactionReconciliationIntegrationTest` и
  `completeDms_pendingAndProviderSaysUnknownStatus_returns400AndDoesNotCapture`.

- **P1-8b (19.08.2026). Успех capture/возврата подтверждает эквайер, а не отсутствие ошибки.**
  Было: `completeDms` и `refund` считали операцию удавшейся, если клиент не бросил исключение,
  а клиент смотрел ровно на наличие `errorCode`; `refund` читал из ответа `refundId`, которого
  в контракте нет нигде (номер возврата всегда `null`), и не сохранял ответ эквайера вообще;
  причина отказа из `custAttrs` выбрасывалась. Сделано: `AcquiringClient.completeDms/refund` →
  `MoneyOperationResult`; `TxpgAcquiringClient.requireConfirmation` требует
  `tran.match.ridByPmo` (§5.5-5.7, §5.8.8), иначе ERROR с телом целиком и
  `PaymentOutcomeUnknownException` (502, Р-23) — не `BusinessException`, потому что нечитаемый
  ответ не доказывает, что деньги не ушли; `classifyMoneyOperationFailure` пропускает её без
  обёртки; `checkAndThrowIfErrorCode` не тронут и идёт первым. Тестовый двойник отвечает формой контракта,
  выдуманный `REF-XXXXXXXX` убран. В `providerResponse` — `mpCapture` и список `mpRefunds`
  (см. §7); `RefundResponse` = `refundId` (`tranActionId`), `acquirerReference` (`ridByPmo`),
  `approvalCode`. `ProviderDeclineReason.extract` (§5.8.7, `Approved` пропускается — §5.8.3
  показывает его и у успешного заказа) вызывается в `refreshStatus` только при `FAILED_FINAL`,
  пишет `mpDeclineReason`, наружу — `TransactionResponse.failureReason` (Р-24).
  **Принятый риск Р-23:** если живой шлюз отвечает на `exec-tran` иначе, чем §5.5-5.7,
  capture и возвраты будут давать 502 до сверки на тестовом стенде — по одной ERROR-строке
  `NO CONFIRMATION ... Full body: {...}` расхождение видно сразу. `rrn` и маска карты из
  `order.trans[]`/`srcToken` — не здесь, это P1-16 (закрыт 19.08.2026); суммы внешних возвратов из
  `order.trans[]` — `problems.md` §6. Покрыто `TxpgAcquiringClientTest` (+10),
  `MoneyOperationsIntegrationTest` (+5), `ProviderDeclineReasonTest` (без Spring, 15) и двумя
  `checkStatus_*` в `PaymentLinkIntegrationTest`.

- **P0-9 (19.08.2026). Пароль заказа не попадает ни в логи, ни в JSON-колонку.** Блокер из
  `code_review.md`, ошибочно считавшийся закрытым; найден при сверке плана 18.08.2026. Было
  четыре утечки плюс одна потенциальная: (1) адрес запроса к эквайеру с `?password=` писался в
  INFO целиком в `completeDms`, `refund`, `getOrderStatus`; (2) `OpenLinkController:40` логировал
  redirect-URL плательщика вместе с `&password=`; (3) `OpenLinkService` клал пароль в
  `provider_response` при создании транзакции, хотя для него есть колонка `provider_password`;
  (4) после P1-8a `refreshStatus` сохранял в `provider_response` payload заказа целиком, а он по
  §5.8.3 содержит `password` — то есть пароль писался в колонку после **каждого** опроса;
  (5) `log.debug("PROVIDER RESP BODY [createEcomOrder]: {}", response)` печатал рекорд с паролем.
  Плюс шестая, которой в плане не было: `getOrderStatus` писал в INFO **тело ответа целиком**
  (`Response: {}`), а это конверт `{"order": {…, "password": …}}`. Сделано:
  `az.millikart.pbl.provider.ProviderPayloads` (без Spring) с двумя функциями —
  `urlForLog(url)` (адрес без query-строки: `UriComponentsBuilder...replaceQuery(null)`, не
  `replaceQueryParam`, который **добавил** бы `password=***` туда, где пароля не было; `null`/`""`
  возвращаются как есть, неразбираемый адрес → заглушка `<unparseable url>`, не сам адрес) и
  `withoutSecrets(payload)` (копия без ключа `password` по верхнему уровню — хранимая карта это
  объект `order`, и пароль в нём лежит сверху; вход не меняется, `null` → `null`). Через них:
  все четыре лога запроса `TxpgAcquiringClient`, лог ответа `getOrderStatus` (теперь логируется
  сам `order`, и после проверки `errorCode`, так что отказ больше не объявляется `SUCCESS`), логи
  ответов `completeDms`/`refund` и ERROR `NO CONFIRMATION ... Full body` из P1-8b (пароля там по
  контракту нет, но это сырой чужой payload), `OpenLinkController` (redirect без query),
  `PaymentLinkService.refreshStatus` (обе ветки — свежий payload и прежний `providerResponse` при
  пустом ответе, в старых записях пароль мог остаться), `completeDms`/`refund` (сырой ответ
  `exec-tran` — `MoneyOperationResult.raw` остаётся сырым, чистится при записи).
  `EcomCreateOrderResponse.Order.toString()` переопределён: `password=***` (а `null` — как
  `null`), так что любой будущий `log.debug(response)` безопасен. Из `providerResponse` при
  создании транзакции ключ `password` убран (`hppUrl`, `id`, `status` остались).
  **Отправка не тронута (Р-25):** `queryParam("password", …)` по-прежнему в трёх местах, пароль
  по-прежнему в redirect-URL плательщика — без него страница оплаты не откроется. Миграции для
  уже записанных `provider_response` нет (боевых данных нет, база пересоздаётся, Р-14).
  Покрыто `ProviderPayloadsTest` (без Spring, 14/21), `TxpgAcquiringClientTest` (+11: все четыре
  вызова при захвате логов на TRACE — ни одной строки с паролем, включая сообщения исключений
  Spring при таймауте и 5xx, а пароль при этом **есть** в URL запроса; `toString()` рекорда) и
  четырьмя тестами `PaymentLinkIntegrationTest` (открытие: `provider_password` заполнен, в сырой
  колонке `provider_response` нет `password`; redirect несёт пароль, а root-логгер — нет; опрос с
  `password` в payload'е → в колонке его нет, остальное и маркеры на месте; пустой ответ поверх
  «старой» записи с паролем → пароль не копируется вперёд). Проверено обратно: с `urlForLog` и
  `withoutSecrets`, возвращающими вход, краснеют ровно восемь тестов клиента.

- **P1-16 (19.08.2026). Маска карты, RRN и код одобрения в карточке транзакции.** Было:
  `PaymentLinkService.mapToTransactionResponse` читал `cardNumberMasked`, `rrn`, `approvalCode` из
  корня `providerResponse`, то есть из объекта `order`, где их по контракту нет (а ключа
  `cardNumberMasked` нет нигде — имя выдумано); мерчант видел пустыми ровно те три поля, по которым
  платёж сверяют с выпиской, хотя фронт (`types/transaction.ts`, переводы, `cardLast4` из маски)
  был готов. Сделано: `az.millikart.pbl.provider.ProviderOrderDetails.read(order)` →
  `TransactionFacts(maskedCard, rrn, approvalCode)` (без Spring): маска —
  `order.srcToken.displayName` (§5.8.4) как есть; `rrn`/`approvalCode` — с записи покупки в
  `order.trans[]` (§5.8.5–5.8.6), иначе `order.lastTran` (§5.8.3); выбор записи — не
  `isReversal`, `description` не `Refund`/без `Void`, предпочтение `Purchase`, самая ранняя по
  `regTime` (строковое сравнение, намеренно — см. §7); разбор защитный на каждом шаге
  (`trans` не список, элементы не карты, `srcToken` не карта, значения не строки, `null`-payload —
  ничего не бросает). `TxpgAcquiringClient.asText` вынесен в `ProviderPayloads.scalarText` и
  используется в обоих местах — правило скаляра одно. Только бэкенд (Р-26): ни `clearDay`, ни тип
  карты, ни колонок/миграции, ни правок фронта; форматирование маски и последние 4 цифры — на
  фронте. Сверх ТЗ, только текст: комментарии в `refreshStatus`/`reconcileOne` и в
  `TransactionReconciliationIntegrationTest`, обещавшие, что суммы внешних возвратов из
  `order.trans[]` прочитает P1-16, теперь ссылаются на `problems.md` §6 — P1-16 этого не делает.
  Покрыто `ProviderOrderDetailsTest` (без Spring, 22), `ProviderPayloadsTest` (+4 метода / +15
  запусков на `scalarText`) и двумя тестами `PaymentLinkIntegrationTest` (payload §5.8.6 → все три
  поля в `/status`, повторное чтение без опроса эквайера; те же три поля в `GET /transactions`,
  равные ответу `/status`). Проверено обратно: со старым чтением из корня краснеют ровно эти два.

- **P3-Auth + P2-10 (19.08.2026). Вход больше не рассказывает, кто у нас есть, и считается по IP.**
  Было: `AuthService.login` проверял статус аккаунта и блокировку **до** пароля и называл причину
  («User account is blocked», «Account is locked…»), а несуществующий пользователь отваливался
  вообще без вызова bcrypt — разница ~80 мс, по которой существование определяется даже при
  одинаковых текстах. Лимита попыток по адресу не было нигде, поэтому блокировка аккаунта после
  6 неудач работала как оружие: зная email мерчанта, любой выключал его за минуту и повторял
  сколько нужно. А `OpenLinkController.extractClientIp` брал **первый** элемент `X-Forwarded-For` —
  единственный, который nginx никогда не пишет, а клиент всегда может (P2-10), так что любой лимит
  по адресу обходился одним заголовком. Сделано:
  1. `az.millikart.common.web.ClientIp.resolve(request, trustedProxies)` — единственный источник
     адреса клиента: пир не в списке доверенных → заголовки игнорируются целиком; доверенный пир →
     `X-Real-IP`, иначе **последний** элемент `X-Forwarded-For` (`$proxy_add_x_forwarded_for`
     дописывает настоящий адрес в конец); значение обязано разбираться как IP-литерал разумной
     длины, иначе берётся адрес пира. `InetAddress.getByName` зовётся только на строке, которая
     физически не может оказаться именем, — иначе заголовок превращал бы вход в DNS-запрос.
     Список — `TrustedProxies` (`mp.trusted-proxies`, `${TRUSTED_PROXIES:127.0.0.1,::1}`); пустой
     означает «заголовкам не верим никогда».
  2. `az.millikart.auth.security.LoginRateLimiter` — Caffeine `адрес → неудачи`,
     `expireAfterWrite(window)`, настройки `auth.login.rate-limit.{enabled,max-failures,window}`
     (10 / PT15M). Считаются только неудачные попытки, успешный вход обнуляет счётчик адреса
     (иначе офис за одним NAT выключает себе вход к обеду). Проверка — **первой**, до базы и до
     bcrypt. В памяти процесса (Р-27): перезапуск обнуляет, это размен, а не недосмотр.
  3. `TooManyRequestsException` + обработчик в `GlobalExceptionHandler` → **429** с `Retry-After`
     в секундах; тело обычное, без «сколько попыток осталось».
  4. Порядок в `login`: лимит → поиск пользователя (нет — сравнение с фиксированным
     bcrypt-хэшем-заглушкой и общая ошибка) → пароль → неверен: счётчик аккаунта (6/30 мин, Р-28,
     не тронуто) + счётчик адреса + **общая** ошибка → верен и заблокирован: про блокировку
     со временем → верен и статус не `ACTIVE`: «учётная запись неактивна», без названия статуса →
     успех: оба счётчика сброшены. Попытки, сделанные **во время** действующей блокировки, счётчик
     аккаунта не наращивают и блокировку не продлевают — иначе её можно было бы держать вечно.
  5. Логи входа (успех и неудача) теперь несут разрешённый адрес и нормализованный `cleanEmail`
     (раньше в разных строках было по-разному: то `cleanEmail`, то `request.username()`).
  6. `pbl`: `extractClientIp` удалён, `OpenLinkController` зовёт `ClientIp.resolve` — это и есть
     P2-10; в `transactions.client_ip` пишется то же значение, что и раньше, но уже не выбранное
     плательщиком.
  Остаточная утечка (принята, `problems.md` §7): верный пароль для заблокированного аккаунта
  отличим от неверного. `/auth/refresh` намеренно не лимитируется — `problems.md` §8.
  Покрыто `ClientIpTest` (`common`, без Spring, 9), `LoginRateLimiterTest` (`auth`, без Spring, 6)
  и девятью новыми методами `AuthIntegrationTest`. Существующий
  `testAccountLockout_After6FailedAttempts` подправлен в одной точке: 7-я попытка с **неверным**
  паролем теперь получает общий отказ, а не «Account is locked» (в этом и была утечка); что
  блокировка реально наступила, тест проверяет по строке в базе и вторым тестом — с верным паролем.


- **P2-9 (20.08.2026). Правка ссылки не переписывает цену задним числом и не воскрешает мёртвую
  ссылку.** Было: `update` менял `amount` без единой проверки — в том числе пока плательщик стоит
  на странице оплаты с прежней суммой, и у многоразовой ссылки, по которой уже прошли платежи.
  В отчётах оставалась одна ссылка с одной текущей суммой и транзакции на разные, и восстановить,
  кто на что соглашался, было нечем. Проверка статуса пропускала любой `ACTIVE`, поэтому
  просроченная и исчерпавшая лимит ссылка воскресали одним `PATCH` — срок (P1-9) и `maxPayments`
  переставали что-либо значить. Заодно `maxPayments` опускался ниже числа уже прошедших платежей:
  «использовано 3 из 2». Сделано:
  1. **Сумма заморожена, если по ссылке есть транзакция в `AMOUNT_LOCKING_STATUSES`** (Р-31):
     `PENDING`, `AUTHORIZED`, `SUCCESS`, `PARTIALLY_REFUNDED`, `REFUNDED` → 400. Набор выписан
     перечислением, а **не** как `!= FAILED`: новый статус в `TransactionStatus` должен по
     умолчанию запрещать правку, а не разрешать её молча. `PENDING` считается — это платёж,
     идущий прямо сейчас. Спрашивается у базы (`TransactionRepository.existsByLinkIdAndStatusIn`),
     а не выборкой в память.
  2. **Сумма, равная текущей, — не изменение**, и до проверки не доходит: портал шлёт PATCH со
     всей формой, и поле, которого не трогали, не должно ронять запрос. Сравнение — `compareTo`,
     не `equals` (`100.0` и `100.00` — одна и та же сумма).
  3. **Таблица переходов `ALLOWED_STATUS_TRANSITIONS`** (Р-32) вместо «только `ACTIVE`
     или `CANCELED`»: `ACTIVE → CANCELED`, `EXPIRED → CANCELED` (мерчант убирает мусор из списка),
     `CANCELED → ACTIVE`. Всё остальное — 400 с указанием текущего и запрошенного статуса.
     `COMPLETED` не покидает своё состояние вовсе, включая отмену: лимит исчерпан, и это факт,
     а не настройка. Установка того же статуса, что уже стоит, — не ошибка, а отсутствие изменения.
  4. **Снятие отмены требует срока в будущем.** Иначе вернули бы `ACTIVE`-ссылку, которую нельзя
     открыть, — до следующего прохода планировщика. Отказ подсказывает передать `expiresAt` тем же
     запросом, и это работает: `expiresAt` применяется в методе **раньше** статуса, проверка
     смотрит на уже обновлённое значение. **Порядок полей в `update` — рабочий, а не косметика.**
     `expires_at = NULL` (ссылки до 18.08.2026) по-прежнему значит «без срока» и реактивации
     не мешает — ровно как при открытии.
  5. **`maxPayments` не ниже числа `SUCCESS`-транзакций** — 400 с обоими числами. Равное числу
     прошедших платежей — можно: так ссылка закрывается на том, что уже собрала.
  6. **Одна строка `log.info` на правку** вместо россыпи `log.debug` по полям: перечисляет
     изменившиеся поля со старым и новым значением, по ней восстанавливается одна правка целиком.
     Персональные данные плательщика (имя, email, телефон) — только именами полей, без значений.

  Отдельной таблицы истории изменений ссылки не заводили — `problems.md` §9. Фронтенд не тронут:
  форма редактирования получает 400 и показывает текст ошибки.
  Покрыто тринадцатью методами `PaymentLinkIntegrationTest` (§11).


### Тонкости, на которых легко ошибиться

- **Новое окно подтверждения — только `ConfirmDialog`** (`app/components/ConfirmDialog.tsx`,
  P3-5b). Свой `<Dialog>` под подтверждение заводить не надо: четыре правила безопасного
  окна (не закрывать во время запроса, гасить обе кнопки, `autoFocus` на безопасной,
  `contained` на опасной) живут только в нём, и в собственном окне их забудут. Что окно
  показывает — карточку, `Alert`, список — передаётся `children`. Окна-**форм** это не
  касается: у них другая задача, их шесть, и они остались собственными.
- **Hibernate auto-flush.** Считаешь после изменения managed-сущности — она уже посчитана:
  flush перед JPQL-запросом сбрасывает изменение в БД. Именно на этом стоял P1-7
  (`refreshStatus` прибавлял `+ 1` к тому, что уже учтено). Ни в `refreshStatus`, ни
  в `completeDms` прибавки быть не должно.
- **Открытие ссылки держит блокировку строки на время HTTP-вызова к эквайеру** (P1-5, до 13 с
  в худшем случае). Это осознанно — см. javadoc `openAndBuildRedirect` и §10.
- **Отменить холд мы не умеем — у `AcquiringClient` нет Void.** Поэтому брошенный `AUTHORIZED`
  занимает слот и блокирует одноразовую ссылку, пока банк не снимет холд сам (§10). Гасить его
  локально нельзя: деньги на карте от этого не освобождаются.
- **Срок жизни ссылки считается от `created_at`, а не от «сейчас»** (P1-9, §10). Потолок
  `pbl.link.max-ttl` в `update` меряется от даты создания ссылки — иначе цепочкой PATCH'ей
  срок продлевается бесконечно. `NULL` в `expires_at` по-прежнему означает «без срока»
  и остаётся у ссылок, созданных до 18.08.2026.
- **Ошибка клиента не должна выглядеть как сбой сервера.** Всё, что не перечислено в
  `GlobalExceptionHandler`, падает в `handleUnexpected` — 500 «Unexpected server error» плюс ERROR
  со стектрейсом. Так дважды получалось на ровном месте: опечатка в URL (закрыто `handleNoHandler`,
  404) и неверный метод или `Content-Type` (закрыто 20.08.2026, 405 с `Allow` и 415). Добавляя
  эндпоинт или контракт, спроси, какое исключение Spring бросит на кривом запросе, и есть ли на
  него обработчик: ERROR в логе должен означать, что сломались мы.
- **Два `@ConditionalOnProperty` на двух реализациях одного интерфейса — не переключатель.**
  Они дополняют друг друга ровно для тех значений, что выписаны в `havingValue`; всё остальное
  не выбирает ничего, и контекст падает жалобой на отсутствующий бин, а не на флаг. На этом
  сгорел `AcquiringClient` (§7), и починился он не более умным условием, а тем, что реализация
  осталась одна. Если такой выбор всё же понадобится — `@Bean`-фабрика с обычным `if` и отказом
  на непонятном значении, но не `@ConditionalOnMissingBean`: на сканируемых `@Component` его
  исход зависит от порядка сканирования.
- **Сумма ссылки заморожена после первой же попытки оплаты, а `ACTIVE` достижим только
  из `CANCELED`** (P2-9, §10). `AMOUNT_LOCKING_STATUSES` перечисляет статусы, которые правку
  **запрещают**, а не те, которые разрешают: добавил значение в `TransactionStatus` — оно по
  умолчанию запрещает, и это осознанно. Порядок полей в `update` рабочий: `expiresAt` применяется
  до `status`, иначе один PATCH с новым сроком и `ACTIVE` перестанет проходить. Переходы —
  только из `ALLOWED_STATUS_TRANSITIONS`, второй такой проверки в коде быть не должно.
- **`provider_response` — это чужой payload, и класть его в базу или в лог можно только через
  `ProviderPayloads.withoutSecrets`** (P0-9). Объект `order` из §5.8.3 несёт `password` на верхнем
  уровне, и любой новый `putAll(orderDetails)` или `log.info("{}", body)` в обход этой функции —
  новая утечка. То же с адресами: URL к эквайеру и redirect-URL плательщика логируются **только**
  через `ProviderPayloads.urlForLog` (вся query-строка отбрасывается; `replaceQueryParam` не
  использовать — он добавляет параметр там, где его не было). Рекорд
  `EcomCreateOrderResponse.Order` маскирует пароль в `toString()`; новый DTO с секретом — сразу
  с таким же переопределением. Сам пароль живёт в колонке `provider_password` и в redirect
  плательщика — больше нигде. Проверка (должна молчать):
  ```bash
  grep -nE 'log\.(info|debug|warn|error)\(.*URL: \{\}", url' \
    pbl/src/main/java/az/millikart/pbl/provider/TxpgAcquiringClient.java
  grep -n '"password", response.order().password()' \
    pbl/src/main/java/az/millikart/pbl/service/OpenLinkService.java
  ```
- **Незнакомый статус заказа эквайера никогда не приводит к `FAILED`** (P1-8a, Р-20). Новое
  слово от TXPG — не в `if`-цепочку, а в `ProviderOrderStatus` с указанием источника
  (`pbl/TXPG-client-side-integration.md` §5.8.8; DMS там не описан). Не приводи статус
  к нижнему регистру и не обрезай пробелы — незнакомая форма это незнакомый статус.
- **`rrn` и `approvalCode` живут в `order.trans[]` (или `order.lastTran`), маска карты — в
  `order.srcToken.displayName`; в корне `order` их нет, а ключа `cardNumberMasked` в контракте нет
  вообще** (P1-16, §7). Читай через `ProviderOrderDetails.read`, не через `resp.get(...)`.
  Значения из чужого payload'а — только через `ProviderPayloads.scalarText` (строка/число → текст,
  структура → «нет»): второй `asText`/`String.valueOf(...)` не заводить — именно так `{}` однажды
  прошёл за подтверждение денежной операции. `regTime` сравнивать как строку, не парсить.
  Проверка (должна молчать):
  ```bash
  grep -rn '"cardNumberMasked"' pbl/src/main --include='*.java'
  grep -n 'private static String asText' pbl/src/main/java/az/millikart/pbl/provider/TxpgAcquiringClient.java
  ```
- **Асинхронного callback от TXPG нет и не будет** (решение Р-7 от 16.08.2026): в
  `NON-PSP Ecom.postman_collection.json` задокументированы только `POST /order`,
  `GET /order/{id}` и `POST /order/{id}/exec-tran`. Заготовки `PaymentCallbackRequest`
  и `pbl.provider.callback-secret` удалены — **не заводи их обратно и не изобретай эндпоинт.**
  Статус дожимается опросом: страница возврата (один заход), ручной `/status` мерчантом
  и фоновая сверка (P1-3, см. §7).
- **Терминалы не удаляются — блокируются** (P2-8, Р-37). `DELETE /api/v1/terminals/{id}` отвечает
  405; вывод из работы — `PATCH` с `status: BLOCKED`. Не возвращай удаление ни под каким видом:
  терминал, через который прошёл платёж, нельзя удалить в принципе — на него ссылаются
  `payment_links`, и попытка даёт 500 при фиксации транзакции, а не понятный отказ.
- **Блокировка терминала запрещает только НОВЫЕ платежи** (Р-38). Проверок статуса терминала
  **не должно быть** в `refund`, `completeDms` и `refreshStatus` (включая фоновую сверку) — это
  обслуживание уже начатых платежей: иначе холд провисит на карте клиента до отпускания банком,
  клиент не получит возврат, а незавершённый платёж останется `PENDING` навсегда. В javadoc всех
  трёх это написано прямо; сторож — три теста в `TerminalBlockedIntegrationTest`. Проверка стоит
  ровно там, где платёж **начинается**: создание ссылки и `openAndBuildRedirect`.
- **Статус терминала в `openAndBuildRedirect` читается ПОСЛЕ `findWithLockById`.** Чтение до
  блокировки строки создаёт окно: блокировка, случившаяся, пока открытие стоит в очереди за
  локом, останется невидимой, и платёж стартует на выключённом терминале. Ловится
  `terminalBlockedWhileTheOpenWaitsForTheLock_stopsThePayment` — переносить проверку выше нельзя.
- **Блокировка локальна для портала.** В MilliKart терминал продолжает работать: у
  `AcquiringClient` нет ни выключения терминала, ни чтения его состояния. «Заблокирован» значит
  «портал больше не выпускает по нему платежи», не «терминал выключен» (`problems.md` §12).
- **Все листинги постраничны — непагинированных в проекте не осталось** (P2-1, 22.08.2026):
  ссылки, транзакции, журнал аудита, `listUsers`, `listCompanies`, `listTerminals`. Добавляешь
  новый листинг — сразу с `Pageable` и `PagedResponse` из `az.millikart.common.dto`; **второго
  `PagedResponse` в проекте быть не должно.** Умолчания одни на всех: `page=0`, `size=20`,
  потолок 200, значения **приводятся**, а не отвергаются (`PageRequest.of` бросает на `page < 0`
  и `size < 1`, и это доходит до клиента как 500). Два исключения, пока не закрытые:
  `TransactionController.list` и `PaymentLinkController.list` в `pbl` отдают сырые `page`/`size`
  прямо в `PageRequest.of` — `?size=0` там 500, а размер страницы ничем не ограничен
  (`problems.md` §15). Новые листинги так не делать.
- **Сортировка страницы обязана заканчиваться уникальной колонкой.** `username, id` у
  пользователей, `name, id` у компаний и терминалов, и так далее. Сортировка по неуникальному
  ключу оставляет базе право расположить две одинаковые записи по-разному в ответе на соседние
  запросы: запись тогда попадает на обе страницы сразу или не попадает ни на одну. Сторожа —
  `companiesWithTheSameName_appearExactlyOnce_acrossPages` и его брат про терминалы.
- **Фильтр по «удалённым» — в запросе, а не после него.** `listUsers` и `listCompanies` прячут
  soft-deleted строки через `findAllByStatusNot(...)`. Отсеивать их из уже прочитанной страницы
  нельзя: страницы выйдут короче `size`, а `totalElements` будет считать то, чего вызывающий
  не увидит.
- **На вопрос «платёж состоялся?» отвечает один набор — `TransactionStatus.PAID_STATUSES`**
  (P2-15, P2-16): `SUCCESS`, `REFUNDED`, `PARTIALLY_REFUNDED`. Возвращённый платёж — это
  состоявшийся платёж (Р-49): деньги были заплачены, а потом отданы обратно; ссылкой
  воспользовались. Этим набором считаются дата оплаты, `currentPaymentsCount` вместе с колонкой
  `current_payments_count`, занятые слоты (`OpenLinkService` добавляет к нему `AUTHORIZED` —
  отдельное основание P1-6) и запрет понижать `maxPayments`. Не «оптимизируй» набор обратно до
  одного `SUCCESS`: после возврата освободится слот, и ссылка соберёт за жизнь больше платежей,
  чем разрешено, а частичный возврат (вернули 1 из 100) сотрёт платёж целиком. `AUTHORIZED` в
  набор не входит намеренно: холд — зарезервированные деньги, а не взятые. Возврат не
  пересчитывает и статус ссылки: `COMPLETED` остаётся `COMPLETED` — иначе возврат открывал бы
  ссылку заново.
- **Дата оплаты в списке — один запрос на страницу, никогда на строку** (P2-15).
  `toSummary` — проекция самой ссылки, список строится без единого похода в транзакции;
  `findLastPaidAtByLinkIds` (`GROUP BY`) сохраняет это свойство, а поиск «в лоб» вернул бы
  N+1, снятый в P2-4. Пустую страницу запросом не трогать вовсе — `IN ()` не SQL. Сторож —
  `listOfTwentyLinks_resolvesDatesWithASingleQuery`: он считает и вызовы репозитория, и
  реальные подготовленные запросы Hibernate, и краснеет обоими способами. По той же причине
  счётчики (`currentPaymentsCount`, `refundedPaymentsCount`) в `PaymentLinkSummaryResponse`
  не добавлять вовсе (P2-16) — они есть только в одиночном ответе.
- **Отсутствующих данных о платеже на карточке ссылки не подставлять** (Р-48, P2-15). API по
  ссылке не отдаёт ни номера карты, ни идентификатора транзакции, ни адреса плательщика, ни
  стадии DMS. Поле, которого нет, **не показывается** — ни прочерком с выдуманным значением
  рядом, ни «значением по умолчанию». Однажды здесь уже стоял построитель, сочинявший целую
  транзакцию, и он был безвреден ровно до того дня, когда заработало поле, за которым он
  прятался. Настоящие операции по ссылке — в `/payment-links/{id}/transactions`, и таблица
  на карточке показывает только их.
- **`GET /api/v1/terminals/options` отдаёт заблокированные терминалы, и это не недосмотр**
  (Р-45). Лёгкий список (`id`, `name`, `status`) кормит двух потребителей с разными нуждами:
  форме создания ссылки нужны только `ACTIVE` — она и фильтрует; экрану транзакций и карточке
  транзакции нужны **все**, потому что они подставляют имя терминала в старые платежи, и по
  снятому с обслуживания терминалу имя должно остаться. Отфильтруешь на сервере — на экране
  транзакций вместо имён появятся `Terminal #N`. Учётные данные терминала в
  `TerminalOptionResponse` не входят вовсе (не замаскированы — их там нет), сторож —
  `options_carryNoTerminalCredentials`.
- **Журнал аудита: успех пишется только после коммита, отказ — сразу и в своей транзакции**
  (Р-35, P2-5). Новое аудируемое действие в `directory` — это две строки: успех —
  `eventPublisher.publishEvent(AuditEvent.of(...))` внутри бизнес-транзакции (писать в
  репозиторий напрямую или вешать `REQUIRES_NEW` на запись успеха нельзя — журнал зафиксирует
  действие, которое откатилось); отказ — `auditLogService.logDenied(...)` непосредственно
  перед `throw`. Ошибку записи журнала не пробрасывать: успех ловится в `AuditLogWriter`,
  отказ — внутри `logDenied` (оба пишут ERROR с маркером `AUDIT_WRITE_FAILED`); ни
  бизнес-операция, ни код отказа от журнала меняться не должны.
- **Чтобы записать действие в журнал, сервис публикует `AuditEvent` — и всё** (Р-41). Машинерия
  одна на проект, в `az.millikart.common.audit`; второй копии быть не должно. Успех —
  `eventPublisher.publishEvent(AuditEvent.of(...))` внутри бизнес-транзакции. Отказ —
  `auditLogService.logDenied(...)` прямо перед `throw`. Операция, чей исход неизвестен и чья
  транзакция откатится (`PaymentOutcomeUnknownException`), — `logUnresolved(...)`, тоже
  синхронно: после отката записывать будет нечему и некому — и с P3-2 такая запись выходит с
  `outcome = UNRESOLVED`, а не `SUCCESS`.
- **`entityType` и `action` журнала — только константы `AuditEntity` / `AuditAction`
  (`common/audit`), никаких строковых литералов** (P3-2). Новое значение сначала добавляется в
  словарь и в таблицу `technical_handover.md` §4.4, потом используется; значение мимо словаря
  журнал запишет, но с WARN-маркером `AUDIT_OUTSIDE_DICTIONARY` — валидация нарочно не бросает
  исключений, аудит не имеет права уронить операцию из-за опечатки. Соглашения, которые легко
  нарушить по незнанию: `entityId` у `AUTH` — **всегда логин** (не UUID, не IP — адрес живёт в
  `client_ip`); `entityId = "ALL"` — «действие над списком» у LIST-отказов; смена статуса — свои
  `BLOCK`/`UNBLOCK` у пользователя, терминала **и компании**, а не текст в `UPDATE`; действия
  `ACCESS` больше нет — отказ по терминалу и в `pbl` пишется как `TERMINAL`/`READ`; logout
  журналируется только когда реально отозвал токены. Приёмочные grep'ы — в
  `fix_plan.md`, запись P3-2.
- **`@SpringBootApplication(scanBasePackages = "az.millikart")` не расширяет поиск сущностей и
  репозиториев.** Компоненты — да, JPA — нет: базовый пакет для `@Entity` и репозиториев берётся
  от класса приложения. Поэтому в каждом из трёх сервисов стоят явные `@EntityScan` и
  `@EnableJpaRepositories`, и в них перечислены **и свой пакет, и `az.millikart.common.audit`**:
  объявив их, вы теряете умолчание, и собственные сущности сервиса перестают находиться. Симптом
  — `Not a managed type` на старте.
- **В журнал не попадают пароли, токены и их части.** `details` пишут места, где секрет в
  области видимости, — в первую очередь вход: там в записи только категория отказа («no such
  account», «wrong password»), никогда сам ввод. Логин в `performedBy` на неудачном входе — это
  недоверенный ввод: он обрезается по ширине колонки и нигде не интерпретируется.
- **Лимит входа по IP пишет в журнал ровно один раз за окно** — в точке `count == maxFailures`
  (`LoginRateLimiter.recordFailure` возвращает `true` только там). Лимитер намеренно не ходит в
  базу: запись на каждую отбитую попытку вернула бы туда неограниченный поток вставок, и защита
  превратилась бы в усилитель нагрузки.
- **`@Transactional` не работает при вызове изнутри того же класса.** Аннотацию применяет
  прокси, а вызов `this.method(...)` мимо него и идёт: транзакционные настройки метода —
  включая `REQUIRES_NEW` — просто не применяются, и код молча выполняется в транзакции
  вызывающего. Именно так терялась запись об отказе в `AuditLogService.listAuditLogs`
  (`readOnly`-транзакция + откат от `InvalidStateException`), причём все тесты были зелёные —
  ни один не проверял этот путь. Поэтому обе записи журнала открывают транзакцию
  `TransactionTemplate`'ом (`ownTransaction`), а не аннотацией. Если пишешь метод, чья
  транзакционность обязана соблюдаться при любом способе вызова, — шаблон, а не аннотация.
- **Адрес клиента в коде сервисов — из `ClientIpHolder`, не из заголовков и не из
  `HttpServletRequest`** (Р-36). Держатель наполняет `ClientIpFilter` через `ClientIp.resolve`
  и чистит в `finally`; протаскивать `clientIp` параметром через сигнатуры сервисов не надо.
- **Поисковая строка списков проходит только через `SearchTerms`** (`common/search`, P3-1):
  контроллер — `normalize` (trim, пустое → `null`, обрезка до 100), запрос — `toLikePattern`
  (нижний регистр + экранирование `%`/`_`/`!`), и каждый `LIKE` по такому паттерну обязан
  сказать `ESCAPE '!'` (Criteria — `SearchTerms.LIKE_ESCAPE`). Голый `LIKE '%' || :q || '%'`
  по пользовательскому вводу — это дыра: ввод `%` возвращает всю таблицу. Escape-символ — `!`,
  а не бэкслеш, нарочно: паттерн скармливается и JPQL, и Criteria, и нативному SQL на H2 и
  PostgreSQL, где бэкслеш требует собственного экранирования на нескольких слоях. Скоуп роли —
  всегда условие запроса рядом с поиском, никогда не пост-фильтр: поиск не имеет права находить
  чужое. И никаких `pg_trgm`/GIN под эти LIKE — решение в javadoc `SearchTerms`.
  Вне запроса `get()` возвращает `null` — это штатный ответ, не ошибка.
- **Адрес клиента — только через `ClientIp.resolve(request, trustedProxies.addresses())`**
  (P3-Auth, P2-10). Напрямую из `X-Forwarded-For` / `X-Real-IP` — никогда: их пишет тот, кто
  прислал запрос. Первый элемент `X-Forwarded-For` — клиентский, настоящий адрес nginx дописывает
  в **конец** (`$proxy_add_x_forwarded_for`) и кладёт в `X-Real-IP`; заголовкам верим только от
  адресов из `mp.trusted-proxies`. Адрес — ключ лимитера входа и содержимое
  `transactions.client_ip`, так что «взять из заголовка» = отдать и то, и другое вызывающему.
  Проверка (должна молчать):
  ```bash
  grep -rn 'getHeader("X-Forwarded-For")\|getHeader("X-Real-IP")' \
    --include='*.java' auth common directory pbl | grep -v /build/ | grep -v ClientIp.java
  ```
- **`InvalidStateException` = 403.** Для отказа в доступе бросай именно его, не `UnauthorizedException`.
- **Словари бэкенда во фронтенде — только настоящие значения, и разбор на границе**
  (P2-12, Р-30; P2-13, Р-33).
  `TransactionStatus` и `PaymentMethod` в `frontend/src/app/types/transaction.ts` перечисляют ровно
  то, что отдаёт `pbl` (`TransactionStatus.java` — шесть значений, `PaymentType.java` — два), а
  ответ разбирается `parseTransactionStatus`/`parsePaymentMethod`, как роль разбирается `parseRole`.
  То же самое у платёжной **ссылки** с 21.08.2026: `LINK_STATUSES` (`ACTIVE`, `EXPIRED`,
  `COMPLETED`, `CANCELED` — `PaymentLinkStatus.java`), `LINK_USAGE_TYPES`, `PAYMENT_TYPES`
  в `utils/payByLinkData.ts` и `parseLinkStatus`/`parseLinkUsageType`/`parsePaymentType`.
  Три правила, каждое из которых уже ломалось: **не добавлять значения «на будущее»** (пока в типе
  лежал чужой словарь, `tsc` не мог отличить настоящий статус от выдуманного, и статистика тихо
  показывала нули); **не подставлять значение по умолчанию вместо незнакомого** (`|| 'APPROVED'`
  превращал любой нераспознанный статус в «успешный»); **не глушить расхождение через `as any`** —
  ради этого гейта `tsc -b` и стоит перед `vite build` (P1-14). Незнакомое значение → `null`,
  показывается как есть и пишется в консоль. Подписи статусов — `Record<TransactionStatus, string>`
  и `Record<LinkStatus, string>` в `i18n/translations.ts`: лишний ключ или недостающий язык
  не проходят `tsc`. Проверка (должна молчать, исключений по `paybylink` больше нет):
  ```bash
  grep -rnE "===? *'(APPROVED|DECLINED|3d-failed|success|pending|canceled|cancelled|paid|active|expired|completed|sms|dms|single|multiple)'" \
    frontend/src --include='*.ts' --include='*.tsx'
  ```

- **P2-12 (20.08.2026). Фронтенд говорит на статусах, которые бэкенд присылает.** Было:
  `App.tsx:147` клал статус как `String(t.status || 'APPROVED').toUpperCase() as any`, а тип
  `TransactionStatus` перечислял одиннадцать значений из трёх словарей сразу (`APPROVED`,
  `DECLINED`, `CANCELED`, `success`, `pending`, `canceled`, `3d-failed`) — **без `SUCCESS`
  и `AUTHORIZED`, то есть без двух самых частых реальных статусов**. `as any` не давал
  компилятору это заметить. Следствия: `StatsOverview` считал выручку и все счётчики по
  `'success'`/`'pending'`/`'canceled'`/`'3d-failed'` и **на обоих экранах транзакций всегда
  показывал нули**; фильтр по статусу (точное сравнение) не находил ничего; `case 'APPROVED'`
  и `case 'DECLINED'` в `TransactionTable` не срабатывали; `PayByLinkDetailPage:303` переводил
  настоящий `FAILED` **обратно** в выдуманный `3d-failed`. Сделано (Р-30 — чиним причину,
  а не сравнения):
  1. **В типе ровно шесть значений** — `TRANSACTION_STATUSES` в `types/transaction.ts`,
     зеркало `pbl/.../domain/TransactionStatus.java`. Значений «на будущее» там быть не должно:
     пока они есть, `tsc` не может отличить настоящий статус от выдуманного.
     `PaymentMethod` сведён к `SMS`/`DMS` (`PaymentType.java`); `mit`, `cit` и нижний регистр
     не приходили никогда.
  2. **`parseTransactionStatus` / `parsePaymentMethod` на границе** — зеркало `parseRole`
     (P1-13): строгое сравнение, без приведения регистра, никогда не бросает. Незнакомое
     значение → `null` **и предупреждение в консоль с исходной строкой**, а не подстановка.
     Прежний `|| 'APPROVED'` молча превращал любой нераспознанный статус в «успешный» —
     это и была причина, а не опечатка в сравнении. Строгое сравнение корректно: бэкенд
     отдаёт `tx.getStatus().name()`, то есть всегда верхний регистр.
  3. **`as any` снят** — и `tsc -b` (гейт из P1-14) сам показал все места, где сравнивали
     с несуществующим статусом. Сравнение с `'APPROVED'` или `'3d-failed'` теперь не проходит
     сборку (`TS2367: no overlap`).
  4. **Неизвестный статус виден, а не спрятан:** `Transaction.statusRaw` хранит исходное
     значение, в таблице такая строка показывается серым (`colorSchemes.neutral`) с этим самым
     значением. Строка не выпадает из списка и не притворяется успешной.
  5. **Счётчики `StatsOverview`:** выручка по `SUCCESS`; успешные — `SUCCESS`, ожидающие —
     `PENDING` + `AUTHORIZED` (холд по DMS для мерчанта тоже «в процессе»), неуспешные —
     `FAILED`, возвращённые — `REFUNDED` + `PARTIALLY_REFUNDED`. Карточка «Canceled» убрана:
     такого статуса у транзакции нет. Заодно убраны зашитые в код `trend: '+12.5%'` и `'+8.2%'`
     (выглядели как настоящая аналитика) и неверная подпись `subtitle: 'Last 10 minutes'` —
     компонент получает уже отфильтрованный список.
  6. **Подписи — из переводов:** `transactions.statuses` в `i18n/translations.ts` объявлен как
     `Record<TransactionStatus, string>`, поэтому новый статус потребует подписи во всех трёх
     языках, а лишний ключ не пройдёт сборку.
  7. Обратный перевод в `3d-failed` в `PayByLinkDetailPage` удалён целиком; `HomePage` перестал
     перебирать чужие словари (`APPROVED`, `COMPLETED`, `PROCESSING`, `INIT`, `DECLINED`,
     `ERROR`, `3D-FAILED`, `CANCELED`, `EXPIRED`) и разбирает статус один раз на транзакцию.
     Мёртвые `getStatusColor`/`getStatusIcon` в `TransactionTable` и `TransactionDetailPage`
     (не вызывались; карты только под нижний регистр) удалены.

  Проверено: `npm run typecheck`, `npm run lint`, `npm run build` — зелёные; `as any` в правленых
  местах не осталось. Тестов на фронтенде нет — проверка ручная, в браузере. Бэкенд не тронут.

- **P2-13 (21.08.2026). Отмена ссылки больше не «удаётся» всегда, а словарь статусов ссылки
  сведён с бэкендом.** Начиналось как продолжение P2-12 (`problems.md` §10), внутри оказалась
  настоящая ошибка. Было:
  1. `PayByLinkPage.handleCancel` в ветке `catch` делал **ровно то же, что в `try`** — ставил
     строке `cancelled` и показывал сообщение об отмене, только с припиской `(local)`. Отказ
     бэкенда, нехватка прав, оборванная сеть — на экране всё равно «отменена», а ссылка живая
     и по ней платят. После P2-9 (20.08.2026) самый частый путь сюда открылся: завершённую
     ссылку бэкенд отменять отказывается (400, `COMPLETED` не покидает своё состояние).
  2. `LinkStatus` перечислял шесть значений в нижнем регистре
     (`active|paid|completed|expired|cancelled|canceled`), из которых бэкенд не присылает
     ни одного: `PaymentLinkStatus` — это `ACTIVE`, `EXPIRED`, `COMPLETED`, `CANCELED`.
     `paid` не существовал никогда, `cancelled`/`canceled` дублировали друг друга.
  3. Отсюда — мёртвые ветки: проверка `link.status === 'cancelled'` (две `l`) против маппинга,
     клавшего `canceled` (одна), — пометка об отменённой ссылке на детальной странице
     **не показывалась никогда**; блоки под `status === 'paid'` не показывались вообще.
  4. `(l.status || 'active').toLowerCase()` в трёх местах — нераспознанный статус молча
     становился «активна», то есть «по ссылке можно платить». Тот же `|| 'APPROVED'`, что
     убрали в P2-12.

  Сделано (Р-33 — один словарь; Р-34 — состояние берём с сервера):
  1. **Словари как у бэкенда:** `LINK_STATUSES` / `LINK_USAGE_TYPES` / `PAYMENT_TYPES`
     в `utils/payByLinkData.ts` (`ACTIVE|EXPIRED|COMPLETED|CANCELED`, `SINGLE|MULTIPLE`,
     `SMS|DMS`), типы выводятся из массивов. Значений «на будущее» нет.
  2. **`parseLinkStatus` / `parseLinkUsageType` / `parsePaymentType` на границе** — зеркало
     `parseTransactionStatus`: строгое сравнение, незнакомое значение → `null` и предупреждение
     в консоль с исходной строкой. Ссылка с нераспознанным статусом показывается серым, как есть
     (`PaymentLink.statusRaw`), и **действий по ней не предлагается** — ни отмены, ни шаринга.
     Ручные `toLowerCase()`/`toUpperCase()` при чтении и отправке убраны: после разбора значение
     уже в нужном виде.
  3. **`handleCancel` по Р-34** на обеих страницах: локальной правки состояния нет вовсе,
     при отказе показывается `ErrorResponse.message` от бэкенда, и в `finally` — перечитывание
     с сервера в обоих случаях. Заодно `fetchPaymentLinks` перестал очищать список при сбое
     запроса: упавшее перечитывание не должно стирать строки, о судьбе которых мы ничего
     не узнали.
  4. **Мёртвый груз убран:** `generateLinks`, `merchantTerminals` и поля `PaymentLink`,
     которые заполнял только генератор (`terminalRid`, `paymentMethod`). Поля, которые читает
     разметка, оставлены с пометкой «всегда `undefined` до появления поля в API».
  5. **Ветки приведены к реальности:** `=== 'cancelled'` → `=== 'CANCELED'` (пометка об отмене
     наконец видна); блоки под `status === 'paid'` переведены на условие по наличию самой даты
     оплаты (`link.paidAt`); в ленте событий у отмены больше нет выдуманного времени
     («создано + 30 минут») — момента отмены бэкенд не отдаёт.
  6. **Подписи — из переводов:** `payByLink.statuses` объявлен как `Record<LinkStatus, string>`,
     ключ `paid` убран, все четыре статуса есть на трёх языках; добавлен `linkCancelFailed`.
     Фильтр по статусу на списке — все четыре значения, включая `CANCELED`.

  Не тронуто намеренно: локальный словарь связанных транзакций на детальной странице
  (`LinkedTxn.status`, `txnStatusConfig`) — это не статусы ссылки; по ТЗ P2-13 его правка
  в задачу не входила. Он по-прежнему мёртв (построитель запасных строк возвращает пустой
  список, так как `paidAt` из API не приходит) и остаётся кандидатом на уборку.
  *(Убран 22.08.2026 в P2-15 вместе с самим построителем — Р-48: с приходом `lastPaidAt`
  он перестал быть мёртвым и начал бы рисовать выдуманные транзакции.)*

  Проверено: `npm run typecheck`, `npm run lint` (60 предупреждений против 71 до правки,
  0 ошибок), `npm run build` — зелёные. Тестов на фронтенде нет — проверка ручная, в браузере.
  Бэкенд не тронут.

- **P2-2 + P2-5 + P2-6 (21.08.2026). Журнал аудита: не теряет записи, не выдумывает их,
  фиксирует IP и отказы, фильтрует и листает в базе.** Было: `logAction` с `@Transactional`
  (REQUIRED) присоединялся к транзакции вызывающего — откат операции уносил и запись о ней;
  простое `REQUIRES_NEW` дало бы обратное — «удалил терминал 5» при неудавшемся удалении
  (`logAction` звался после `save/delete`, но до конца транзакции — живой пример P2-8);
  отказы в доступе не писались вовсе; IP-адреса в `AuditLog` не было (хотя
  `technical_handover.md` §4.4 его заявлял); `audit_logs` — без единого индекса; фильтр по
  `entityType`/`entityId` для компанейских ролей — `stream().filter` в памяти после загрузки
  всех логов компании; `findAll()` без пагинации и без заданного порядка. Сделано (Р-35, Р-36):
  1. **Успех — после коммита.** Сервисы публикуют `AuditEvent` (`ApplicationEventPublisher`)
     внутри бизнес-транзакции; `AuditLogWriter` — `@TransactionalEventListener(AFTER_COMMIT,
     fallbackExecution = true)` — вызывает `AuditLogService.recordSuccess`. Откатившееся
     действие в журнал не попадает вовсе. `fallbackExecution` — чтобы событие, опубликованное
     вне транзакции (прямой вызов сервиса, планировщик), записалось, а не потерялось молча.
  2. **Своя транзакция — `TransactionTemplate`, а не `@Transactional(REQUIRES_NEW)`.** Это
     отступление от буквы ТЗ, и оно вынужденное: аннотацию применяет прокси, а из
     `listAuditLogs` (там журналируется отказ в чтении самого журнала) вызов идёт внутри того же
     класса — прокси в нём не участвует. С аннотацией запись об отказе присоединялась к
     `readOnly`-транзакции листинга и уходила вместе с её откатом: терялась ровно та запись,
     которую больше некому написать. Нашлось ревью после «зелёных» тестов, воспроизведено
     тестами `deniedListBy*_isRecorded`. Шаблон делает границу транзакции свойством метода,
     а не способа вызова. **Не возвращай аннотацию.**
  3. **Катч вокруг записи, а не внутри.** `persist` не сбрасывается до коммита, поэтому сбой
     записи всплывает при коммите — после тела метода, если граница транзакции на аннотации.
     У успеха катч держит `AuditLogWriter` (ERROR с маркером `AUDIT_WRITE_FAILED`, операция
     отвечает 201). У отказа катч внутри `logDenied`: там запись идёт **до** `throw`, и
     вылетевшее исключение подменило бы законный 403 на 500.
  4. **Отказ — сразу и синхронно.** `AuditLogService.logDenied` зовётся прямо в точке отказа
     перед `throw` — во всех местах отказа `directory`: ролевые проверки `CompanyService`
     (create/update/delete/list/validateAccess),
     `validateWriteAccessToCompany`/`validateReadAccessToCompany` и ролевые ветки
     `TerminalService`, отказ в самом `listAuditLogs`. Запись переживает откат отказанной
     операции (`outcome = DENIED`); через событие так нельзя — `AFTER_COMMIT` для
     откатившейся транзакции не срабатывает. Строки режутся по ширине колонок (`clip`):
     `details` варчар на 4000, а в него уходит имя компании из тела запроса, у которого выше
     по стеку нет ограничения длины — без обрезки любой аутентифицированный пользователь
     отключал бы себе журналирование отказов, дополнив поле до переполнения.
     `companyId` записи об отказе — **компания актора, а не названная в запросе**: журнал
     режется по этой колонке, иначе в чужой обзор аудита пишется произвольный текст.
  4. **IP и исход в схеме** — changeset `004-audit-log-ip-and-indexes.xml`: `client_ip
     varchar(45)` (влезает IPv6), `outcome varchar(16) not null default 'SUCCESS'` (бэкфилл
     честен: до колонки писались только успехи). Адрес кладёт `ClientIpFilter` (`common`,
     `@Order(HIGHEST_PRECEDENCE)`, очистка держателя и MDC в `finally`) в `ClientIpHolder`
     и MDC-ключ `clientIp`; в событие адрес попадает **в момент публикации**
     (`AuditEvent.of`). Разрешение — только `ClientIp.resolve`, подделке заголовком не
     поддаётся. Вне запроса держатель пуст, `clientIp = null` — штатно.
  5. **Три индекса**: `(company_id, created_at desc)`, `(entity_type, entity_id)`,
     `(created_at desc)`. Фильтрация и пагинация — в базе, порядок всегда `createdAt DESC`
     задан явно. `entityType` сравнивается **точно**, значение приводится к верхнему регистру
     в сервисе, `entityId` — `IgnoreCase`: вместе это даёт прежний результат
     `equalsIgnoreCase`, но оставляет предикат по `entity_type` обычным равенством. Полный
     `IgnoreCase` по обеим колонкам компилируется в `upper(entity_type) = upper(?)`, и
     индекс по сырым колонкам такой запрос обслужить уже не может — то есть заведённый той же
     задачей индекс оказался бы мёртвым. **Храни `entityType` в верхнем регистре.**
     Контроллер — `page`/`size` с умолчаниями `0`/`20`, как у транзакций `pbl`, но со
     срезкой: `size` в `[1, 200]`, `page` не меньше нуля. `PageRequest.of` на `size=0`
     бросает `IllegalArgumentException`, у которого обработчика нет, — опечатка в параметре
     возвращала бы 500 со стектрейсом, а неограниченный `size` втягивал бы в память весь
     вечно растущий журнал. Ответ — `PagedResponse<AuditLogResponse>` (+`clientIp`,
     +`outcome`). `PagedResponse` переехал из `pbl` в `az.millikart.common.dto` — он один на
     проект. Ролевые правила не менялись.
  6. Фронтенд `AuditLogsPage` разбирает `content` пагинированного ответа (без своей
     пагинации — первые 200; P2-1 закрыл три справочных экрана, журнал в его ТЗ не входил,
     так что это по-прежнему открыто — `problems.md` §14). `%X{clientIp:--}` добавлен в шаблон
     `logback-spring.xml`: без него адрес разрешался бы для каждого запроса и не показывался
     бы ни в одной строке лога.

  Покрыто `AuditLogIntegrationTest` (14: запись после коммита с IP и исходом; откат через
  `TransactionTemplate` и откат на сбое коммита (перелив varchar) → записи нет; отказ пишется
  и переживает откат; **отказ в чтении самого журнала пишется** — оба branch'а `listAuditLogs`;
  переполняющий ввод не превращает 403 в 500 и запись всё равно есть; запись об отказе
  подшита под компанию актора, а не под названную в запросе; падение журнала не ломает ни
  успешную операцию (201), ни отказ (403) — маркер в ERROR у обоих; доверенный/недоверенный
  прокси; прямой вызов без запроса), `AuditLogQueryTest` (6: паритет фильтра с прежней
  фильтрацией в памяти, изоляция компаний при точном `entityId`, непересекающиеся страницы и
  верный `totalElements`, порядок от новых к старым, срезка `page`/`size`, `AUDITOR` видит
  всё), `AuditLogSchemaTest` (1: три индекса читаются из метаданных схемы, `created_at` в них —
  DESC), `ClientIpFilterTest` (3, `common`: держатель и MDC заполняются и чистятся, в том
  числе при исключении; недоверенный пир — заголовок игнорируется).
  `DirectoryIntegrationTest` переведён на `$.content[...]` и порядок «новые первыми».

  Пять из этих тестов написаны **после** первого «зелёного» прогона: многоагентное ревью
  нашло потерю записи об отказе на self-invocation (п. 2) и переполнение `details` (п. 4),
  оба воспроизвелись тестами до правки. Мораль для следующей задачи: зелёный прогон
  подтверждает только то, что тесты покрывают.

- **P2-8 (21.08.2026). Терминалы блокируются, а не удаляются; ссылки заблокированного
  терминала приостанавливаются.** Было: `deleteTerminal` звал `terminalRepository.delete`, и при
  живой платёжной ссылке база отказывала по `fk_payment_links_terminal` — не в момент вызова, а
  при фиксации транзакции, поэтому наружу шёл **500 «Unexpected server error»**. Система
  сработала правильно (отказалась разорвать связь платежей с терминалом), а отчиталась как о
  собственной поломке. Глубже: терминал, через который прошёл хоть один платёж, удалить нельзя
  было **никогда**, а статуса у него не было — то есть и «вывести из работы, не удаляя» тоже.
  Сделано (Р-37 … Р-40):
  1. **Удаления нет вовсе** (Р-37). `deleteTerminal` и `@DeleteMapping` удалены; путь остался,
     поэтому `DELETE /api/v1/terminals/{id}` отвечает **405**, а не 404 — ресурс существует,
     глагол не поддерживается. Статус меняется существующим `PATCH` (`{"status": "BLOCKED"}`),
     отдельных `/block` и `/unblock` нет и заводить не надо.
  2. **Колонка `terminals.status` добавляется двумя changeset'ами** — `directory/005-` и
     `pbl/005-terminal-status.xml`, каждый под `not columnExists`. Таблицу создаёт тот сервис,
     который стартовал первым (P1-2), и колонка нужна обоим: добавь её в один changelog — и
     второй сервис создаст таблицу без неё, а потом сам же не поднимется на `ddl-auto: validate`.
     Оба порядка старта проверяются `TerminalStatusMigrationTest`.
  3. **Блокировка запрещает только новые платежи** (Р-38) — см. отдельную «грабельку» ниже.
  4. **Ссылки терминала едут вместе с ним** (Р-39, Р-40): при блокировке `ACTIVE` → `SUSPENDED`,
     при разблокировке `SUSPENDED` → `ACTIVE`, а тем, у кого срок истёк за время блокировки, —
     `EXPIRED`. `EXPIRED`, `COMPLETED` и `CANCELED` не трогаются: это факты о том, что со ссылкой
     уже случилось, и, перекрасив их, разблокировка не знала бы, чем они были. Всё — нативными
     `UPDATE`'ами (`PaymentLinkStatusRepository`) в **той же транзакции**, что и смена статуса
     терминала: заблокированный терминал с живыми ссылками не должен существовать и мгновения.
     В `CANCELED` переводить нельзя — снятие отмены разрешено (Р-32), и мерчант поштучно вернул
     бы ссылки на выключённый терминал.
  5. **`directory` пишет в чужую таблицу осознанно.** `payment_links` принадлежит `pbl`, шины
     сообщений в проекте нет, сервисы стартуют независимо, а обратная зависимость уже была
     (`pbl` читает `terminals`). Поэтому — узкий нативный доступ без второго JPA-отображения
     `PaymentLink`; альтернативы разобраны в javadoc `PaymentLinkStatusRepository`. Отсутствие
     таблицы `payment_links` (база, где `pbl` ещё не мигрировал) — штатный случай: ссылок нет,
     двигать нечего, WARN и блокировка проходит. Раньше это был бы 500 при первом развёртывании.
  6. **`SUSPENDED` мерчанту не принадлежит.** В таблице переходов P2-9 он недостижим и
     непокидаем; оба отказа имеют собственный текст со ссылкой на терминал — «cannot be changed
     from SUSPENDED to ACTIVE» отправило бы искать неисправность в ссылке.
  7. **Фронтенд**: кнопка удаления → блокировать/разблокировать, столбец статуса, приглушённые
     строки заблокированных, подтверждение с числом приостанавливаемых ссылок (считается через
     `GET /api/v1/payment-links?terminal=X&status=ACTIVE&size=1`), в форме создания ссылки —
     только активные терминалы, `SUSPENDED` в `LINK_STATUSES` и переводах трёх языков.

  Отступление от ТЗ: пункт 6 просил ограничить `ACTIVE`-терминалами «список терминалов,
  доступных мерчанту (`PaymentLinkService:386`)», но по этой ссылке — фильтр видимости **ссылок**,
  и ограничение там спрятало бы от мерчанта именно `SUSPENDED` ссылки, которые пункты 5 и 8
  требуют показывать. Сделано по смыслу: заблокированный терминал не предлагается там, где
  начинается новый платёж; видимость ссылок не тронута.

  Покрыто `TerminalBlockingIntegrationTest` (9), `TerminalStatusMigrationTest` (5),
  `PaymentLinkStatusRepositoryTest` (1) в `directory` и `TerminalBlockedIntegrationTest` (13)
  в `pbl` — включая три теста Р-38 и тест на гонку, проверенный обратно.

- **P2-14 (22.08.2026). Журнал аудита во всех трёх сервисах.** Было: журнал жил в `directory`,
  и `auth` до него не дотягивался — **заведение пользователя, смена роли и блокировка учётной
  записи не фиксировались нигде**; в `pbl` аудита не было вовсе, то есть возврат денег, самое
  интересное для проверяющего действие, следа не оставлял. Сделано (Р-41 … Р-43):
  1. **Машинерия переехала в `az.millikart.common.audit`** — `AuditLog`, `AuditOutcome`,
     `AuditEvent`, `AuditLogService`, `AuditLogWriter` и репозиторий записи. Копировать её в два
     сервиса было нельзя: в ней четыре неочевидных места (запись после коммита, своя транзакция
     шаблоном, обрезка значений, глотание ошибок), и в одном из них уже находилась ошибка (P2-2).
     Контракт для сервиса — «опубликуй `AuditEvent`, остальное наше дело».
     В `directory` осталась **читающая** сторона: `AuditLogQueryService`,
     `AuditLogQueryRepository`, `AuditLogResponse`, контроллер — журнал показывает только он.
  2. **`@EntityScan` и `@EnableJpaRepositories` во всех трёх приложениях** — см. «грабли» ниже.
  3. **Таблицу создаёт любой из трёх.** В `auth` (`004-audit-logs.xml`) и `pbl`
     (`006-audit-logs.xml`) добавлены changeset'ы, создающие `audit_logs` сразу в финальном виде
     под `not tableExists`. Пять changeset'ов `directory/004` после этого находят каждый свой
     объект на месте и помечаются выполненными — ровно то, ради чего они были разбиты по одному
     на колонку и индекс.
  4. **`auth` пишет:** заведение пользователя (включая первого администратора из
     `AdminBootstrapRunner`, `performedBy = system`), изменение — с указанием, что именно
     изменилось (`role COMPANY_EMPLOYEE -> SYSTEM_ADMIN`), блокировку и разблокировку, смену
     пароля, успешный вход, неудачный вход (`DENIED`, с категорией причины), срабатывание
     блокировки после шестой неудачи и гашение цепочки refresh-токенов при повторном
     использовании — признак кражи токена.
  5. **`pbl` пишет:** создание, изменение и отмену ссылки, списание холда DMS и возврат — с
     суммой, валютой и идентификаторами эквайера (`ridByPmo`, `tranActionId`, `approvalCode`), —
     и отказы в доступе к чужим терминалам.
  6. **`PaymentOutcomeUnknownException` пишется синхронно** (`logUnresolved`). Это тот самый
     случай, ради которого стоит быть внимательным: локально не коммитится ничего, событие после
     коммита не сработало бы никогда, а запись — единственное свидетельство, что операция вообще
     была, и по ней человек потом разбирается, ушли деньги или нет.
  7. **Журнал только на дозапись** (Р-42): у репозитория записи ровно один метод `save`, у
     сущности нет сеттеров, читающий репозиторий не умеет ни того ни другого. В
     `deployment_guide.md` §5.1a — роль `mp_app` с `INSERT`/`SELECT` на `audit_logs` и без
     `UPDATE`/`DELETE`. **Пока сервисы ходят под `postgres`, эти права ни на что не влияют** —
     открытая проблема, `problems.md` §13.

  Покрыто `AuthAuditIntegrationTest` (10), `PblAuditIntegrationTest` (7),
  `AuditLogAppendOnlyTest` (3, `common`) и тремя новыми методами `SharedSchemaMigrationTest`.

- **P2-3 + P2-1 (22.08.2026). Индексы на `transactions`; последние три листинга —
  постранично.** Было: на `transactions`, самой быстрорастущей таблице системы, ровно один
  индекс — `idx_transactions_provider_order`. Не покрыты два набора запросов, и оба горячие:
  (а) пять методов репозитория фильтруют по `link_id`, из них три-четыре выполняются **на каждое
  открытие ссылки плательщиком** (`OpenLinkService`), плюс по одному при списании холда DMS,
  правке ссылки, просмотре карточки и каждом опросе статуса — каждый был полным проходом по
  таблице, и время платежа росло с числом транзакций **всех** мерчантов; (б) выборка фоновой
  сверки (`findByStatusAndCreatedAtBetweenOrderByCreatedAtAsc`), которая идёт каждые две минуты
  круглосуточно, независимо от нагрузки, и тоже читала таблицу целиком. Плюс `listUsers`,
  `listCompanies` и `listTerminals` возвращали `List` без ограничения. Сделано (Р-44, Р-45):
  1. **Три индекса, changeset `pbl/007-transaction-indexes.xml`**, каждый под своим
     `<not><indexExists/></not>`: `idx_transactions_link_status` `(link_id, status)` — счётчики
     попыток, а по префиксу и любой запрос только по `link_id`; `idx_transactions_link_created`
     `(link_id, created_at DESC)` — попытки одной ссылки и «самая свежая»;
     `idx_transactions_status_created` `(status, created_at)` — фоновая сверка. Больше ни одного:
     каждый индекс замедляет вставку, а строка пишется на каждом платеже. `link_id` — внешний
     ключ, а **PostgreSQL внешние ключи автоматически не индексирует**: без индекса страдало ещё
     и удаление родительских строк.
  2. **Три листинга приняли `Pageable`** и отдают `PagedResponse` из `common` — как журнал
     аудита. Умолчания `page=0`, `size=20`, потолок 200, значения приводятся, а не отвергаются.
     Сортировка обязательно с уникальным довеском (`username, id`; `name, id`) — без него
     запись с неуникальным именем может попасть на обе соседние страницы или ни на одну.
     Фильтр soft-deleted переехал из `stream().filter` в запрос (`findAllByStatusNot`), иначе
     страницы выходили бы короче `size`, а `totalElements` считал бы невидимое.
     **Ролевые правила не менялись ни в одном из трёх.**
  3. **`GET /api/v1/terminals/options`** (Р-45) — `id`, `name`, `status`, без пагинации и без
     учётных данных терминала. Отдаёт **и заблокированные**: фильтрует потребитель, а не сервер
     (см. «грабли»). На него переведены форма создания ссылки, экран транзакций и карточка
     транзакции; врезки `SettingsPage` брали одну страницу по потолку — сами врезки удалены
     24.08.2026 (P3-6).
  4. **Р-44: API и интерфейс — вместе.** Разбить только API значило бы молча обрезать списки до
     первой страницы. `UsersPage`, `CompaniesPage`, `TerminalsPage` получили `TablePagination`
     с перезапросом при смене страницы и размера; клиентский поиск остался, но подписан —
     `common.searchOnPage`, «ищет по текущей странице» (серверного поиска по этим трём спискам
     нет, это отдельная работа — `problems.md` §14).

  Покрыто `TransactionIndexSchemaTest` (1, `pbl`), `UserListPaginationTest` (6, `auth`) и
  `DirectoryListPaginationTest` (14, `directory`).

- **P2-15 (22.08.2026). Дата оплаты ссылки — и удаление генератора выдуманных транзакций.**
  Было: `PaymentLinkResponse` отдавал `createdAt` и `expiresAt` и ничего про платёж. Во
  фронтовом типе поле `paidAt` объявлено, но маппинг его не заполнял, поэтому у **оплаченной**
  ссылки в списке показывался её срок действия, на карточке не было ни поля «когда оплачено»,
  ни строки в ленте событий, а таблица связанных операций была пуста всегда. Сделано
  (Р-46 … Р-48):
  1. **Сначала удалён генератор выдуманных данных (Р-48), до включения поля.** На карточке
     стоял построитель «запасных» строк, который при наличии даты оплаты сочинял транзакцию:
     платёжную карту по умолчанию, идентификатор из короткого кода ссылки, пары
     SMS / DMS-Auth / DMS-Capture, выведенные из полей ссылки, а не из платежей. Он не был
     виден **только** потому, что дата оплаты не приходила: включи поле — и на карточке
     платёжной ссылки, рядом с настоящими операциями, появилась бы несуществующая транзакция
     с выдуманным номером карты. Вместе с ним убраны: событие «Customer Redirected» с отметкой
     «оплата + 3 секунды», подстановка `127.0.0.1` и правдоподобного User-Agent вместо
     незаписанных, строки «Transaction ID» / «Payment Method» / «Payer IP» в блоке
     Payment Details (ни одного из трёх полей API по ссылке не отдаёт) и ветка «иначе» у
     стадии DMS, которая объявляла бы captured любой DMS-платёж с датой. Таблица связанных
     операций теперь показывает **только** ответ `/payment-links/{id}/transactions`.
  2. **`PAID_STATUSES`** в `PaymentLinkService`: `SUCCESS` + `REFUNDED` +
     `PARTIALLY_REFUNDED`. Возврат не создаёт отдельной строки, а переписывает статус самой
     транзакции, поэтому поиск только по `SUCCESS` терял бы дату оплаты у возвращённой ссылки.
     `AUTHORIZED` в набор не входит: холд — это резерв, а не взятые деньги.
  3. **`Instant lastPaidAt`** в `PaymentLinkResponse` и `PaymentLinkSummaryResponse`. Имя
     говорит, что у многоразовой ссылки это **последний** платёж (Р-46). Одиночные ответы —
     один дополнительный запрос; **список — один пакетный запрос на страницу**
     (`findLastPaidAtByLinkIds`, `GROUP BY`), пустая страница не выполняет его вовсе.
  4. **Фронтенд:** маппинг заполняет `paidAt` из `lastPaidAt` в обоих местах; в списке
     «Оплачена» с датой показывается **только у `COMPLETED`** (Р-47) — у активной многоразовой
     ссылки с тремя платежами из пяти важнее, сколько ей осталось жить; подпись переведена на
     три языка (`payByLink.paid`).

  Покрыто `PaymentLinkLastPaidAtTest` (10, `pbl`).

- **P2-16 (24.08.2026). Возврат не отменяет использование ссылки.** Было: «сколько раз ссылкой
  воспользовались» считалось как `countByLinkIdAndStatus(linkId, SUCCESS)`, а `refund()`
  переписывает статус самой транзакции на `REFUNDED`/`PARTIALLY_REFUNDED`, второй строки не
  создавая. После возврата платёж исчезал из всей арифметики использования: цифра на карточке
  уменьшалась, слот освобождался — многоразовая ссылка ниже лимита собирала за жизнь на платёж
  больше разрешённого («разрешено 3, прошло 2, один вернули → примет ещё 2»), а частично
  возвращённый платёж (вернули 1 из 100) переставал считаться целиком, хотя 99 остались у
  мерчанта. Сделано (Р-49, Р-50):
  1. **`PAID_STATUSES` переехал в `TransactionStatus`** (`SUCCESS` + `REFUNDED` +
     `PARTIALLY_REFUNDED`) и стал единственным ответом на вопрос «платёж состоялся?». Им
     считаются: `currentPaymentsCount` и колонка `current_payments_count` (одно число — база и
     ответ не расходятся), `lastPaidAt` (P2-15), занятые слоты и проверки открытия в
     `OpenLinkService`, запрет понижать `maxPayments` (P2-9). Одиночный
     `countByLinkIdAndStatus` удалён из `TransactionRepository` совсем — дотянуться до счёта
     по голому `SUCCESS` больше нечем.
  2. **`SLOT_OCCUPYING_STATUSES` выводится из `PAID_STATUSES` + `AUTHORIZED`**, а не выписан
     заново — наборы не могут разъехаться; живой холд занимает слот по своему отдельному
     основанию (P1-6), и оно не потеряно.
  3. **Смысл `currentPaymentsCount` изменился, имя — нет** (поле уже в API): теперь это
     «сколько раз ссылкой воспользовались», а не «сколько платежей сейчас числится в
     `SUCCESS`». Возврат цифру не уменьшает; `COMPLETED` при возврате не пересчитывается —
     возврат отдаёт деньги, а не выдаёт новую попытку оплаты (`refund()` ссылку вообще не
     трогает, и колонка остаётся верной сама собой).
  4. **Новое поле `refundedPaymentsCount`** (`REFUNDED` + `PARTIALLY_REFUNDED` — полные и
     частичные вместе) — только в `PaymentLinkResponse`. В списочный
     `PaymentLinkSummaryResponse` счётчики не добавлены намеренно: список строится без походов
     в транзакции, счётчик на строку вернул бы N+1, снятый в P2-15. Оба счётчика одиночного
     ответа ложатся в индекс `idx_transactions_link_status` (P2-3).
  5. **Фронтенд:** на карточке рядом с «использовано N из M» — «из них возвращено: K», и
     только когда K > 0; подпись на трёх языках (`payByLinkDetail.summary.refundedOfUsed`).
     В списке ничего не изменилось — списочный ответ счётчиков не несёт.

  Покрыто `PaymentLinkRefundUsageTest` (10, `pbl`).

- **P3-2 (24.08.2026). Единый словарь событий аудита и таблица для приёмки.** Было: ~40 мест
  записи в трёх сервисах со словарём, который разъехался, — `entityId` у `AUTH` значил три
  разные вещи (UUID / логин / IP), один и тот же отказ в доступе к терминалу писался как `READ`
  в `directory` и как `ACCESS` в `pbl`, константы были приватными у каждого сервиса
  (`directory` вообще писал литералами), logout не журналировался, блокировка компании пряталась
  в `UPDATE`, а операция с неизвестным исходом у эквайера писалась как `SUCCESS`. Сделано:
  1. **`AuditEntity` и `AuditAction` в `common/audit`** — финальные классы строковых констант
     (не enum: колонки строковые, enum падал бы на чтении значения вне списка). Все ~40 вызовов
     переведены; приватные `USER_ENTITY`/`AUTH_ENTITY`/`LINK_ENTITY`/`TRANSACTION_ENTITY` и все
     литералы удалены. Действие `ACCESS` из словаря исчезло — оба вызова в `pbl` пишут `READ`.
  2. **`entityId` у `AUTH` — всегда логин**: успешный вход пишет логин вместо UUID, `RATE_LIMIT`
     — логин вместо IP (адрес и так в `client_ip`), `TOKEN_REUSE` — логин владельца токена
     (доставая его по `userId` — ради этой строки запрос оправдан).
  3. **Дыры закрыты**: logout пишет `AUTH`/`LOGOUT`/`SUCCESS` — но только когда токен нашёлся и
     был отозван (`revoked > 0`), иначе 204-камуфляж превращал бы журнал в спам-канал; смена
     статуса компании — отдельные `COMPANY`/`BLOCK`|`UNBLOCK` ровно как у пользователя и
     терминала (общий `UPDATE` остался для остальных полей); `AuditOutcome` получил третье
     значение **`UNRESOLVED`**, и `logUnresolved` пишет его вместо `SUCCESS`.
  4. **Валидация без исключений**: значение вне словаря пишется как есть, но в лог уходит WARN
     с маркером `AUDIT_OUTSIDE_DICTIONARY` (`AuditLogService.warnIfOutsideDictionary`, все три
     пути записи). Аудит не имеет права уронить бизнес-операцию из-за опечатки.
  5. **Таблица для приёмки** — `technical_handover.md` §4.4: 30 строк, по одной на каждое
     реальное сочетание `entityType`/`action`, плюс соглашения (`entityId = "ALL"` у LIST-отказов,
     асимметрия `companyId` успех/отказ, успешные LIST/READ не журналируются намеренно,
     append-only, поведение при откатах). Обновлены `directory.md` §3.3 (три значения `outcome`),
     `auth.md` §4.1.2 (logout журналируется) и юнион в `frontend/src/app/types/dto.ts`.
  Покрыто: `AuthAuditIntegrationTest` (+2: logout пишется / не пишется),
  `AuditLogIntegrationTest` (+2: `COMPANY/BLOCK|UNBLOCK` и эхо-статус без события; значение вне
  словаря пишется как есть + WARN с маркером), обновлены проверки в `PblAuditIntegrationTest`
  (`READ` вместо `ACCESS`, `UNRESOLVED` у capture/refund с неизвестным исходом).

- **P3-1 (24.08.2026). Серверный поиск по спискам и пагинация журнала аудита.** Было: поле
  поиска на `UsersPage`/`CompaniesPage`/`TerminalsPage` фильтровало **уже загруженную страницу**
  (~20 строк) — человек с пятой страницы не находился; `AuditLogsPage` пагинации не имела вовсе
  и тянула `size: 200` — за 200-й записью журнал молча заканчивался; фильтр журнала по одному
  `entityType` без `entityId` молча игнорировался; сортировка журнала не имела уникального
  довеска. Сделано (закрыт `problems.md` §14, кроме врезок `SettingsPage` — те удалены
  целиком 24.08.2026, P3-6):
  1. **`SearchTerms` в `common/search`** — единственное место нормализации (`trim`, пустое →
     нет фильтра, обрезка до 100) и экранирования LIKE (`!` как escape: `%` и `_` ищутся
     буквально). Решение «никакого `pg_trgm`/GIN, `LIKE '%…%'` на текущих объёмах — доли
     миллисекунды» записано в javadoc класса, чтобы отсутствие индекса читалось как решение.
  2. **users** (`auth`): `search` по `username`/`full_name`/`company_id` и **названию компании**
     — нативный `@Query` с `LEFT JOIN companies` (таблица `directory`; осознанный кросс-модульный
     долг по образцу `PaymentLinkStatusRepository`, записан в `problems.md` §18) + отдельный
     `countQuery`; `role` — серверный фильтр, неизвестная роль = нет фильтра. `COMPANY_HEAD` без
     компании получает пустую страницу явно — `null`-скоуп в запросе значил бы «все».
  3. **companies / terminals** (`directory`): `search` в JPQL (компании — `name`/`id`; терминалы —
     `name`/`login`/`id` как текст/`company_id`/название компании через `join ... on` внутри
     модуля). Скоуп — условие запроса, поиском не обходится.
  4. **Журнал**: `entityType`/`entityId` независимы (D.1); новые `search`/`outcome`/`from`/`to`
     (даты — instant или день по UTC, мусор = нет фильтра); сортировка `createdAt DESC, id DESC`
     (D.3 — равные времена больше не гоняют строки между страницами); фильтры собраны
     `Specification`-ами (`AuditLogQueryRepository` расширяет `JpaSpecificationExecutor` — только
     чтение, Р-42 не тронут; derived-методы удалены). Индекс `idx_audit_logs_created` оставлен
     по одному `created_at` — решение записано комментарием у сортировки (D.4).
  5. **Фронт**: строка поиска уходит на сервер с debounce 300 мс (`useDebounced`), любой фильтр
     сбрасывает `setPage(0)`, устаревшие ответы отменяются `AbortController` (гонка «ив»/«ива»);
     `filteredUsers`/`filteredCompanies`/`filteredTerminals`/`filteredLogs` и подпись
     `common.searchOnPage` удалены целиком; `roleFilter` и `entityTypeFilter` — серверные.
     `AuditLogsPage`: `TablePagination` (10/20/50/100, по умолчанию 20) вместо `size: 200`,
     в выпадающем списке появились `AUTH` и `AUDIT_LOG`, добавлены колонки «Результат» (DENIED —
     `error`, UNRESOLVED — `warning`) и «IP», фильтры по результату и датам; ключи — на три языка.
     `/api/v1/terminals/options` не тронут.
  Покрыто: `UserListPaginationTest` (+9), `DirectoryListPaginationTest` (+7),
  `AuditLogQueryTest` (+7) — везде «находит за пределами первой страницы», регистр, пустая
  строка, литеральные `%`/`_` (с ловушкой на неэкранированный паттерн), скоуп не обходится,
  `totalElements` считает отфильтрованное; плюс регрессы D.1 и D.3 (пять записей на один
  `created_at` при `size=2` не дублируются и не теряются). Фронтовых тестов нет — тестовой
  инфраструктуры во `frontend/` нет, и ради двух тестов она не заводилась (по ТЗ).

- **P3-1a (24.08.2026). Тест на ветку «COMPANY_HEAD без компании не видит никого».** Было:
  защита в `UserService.listUsers` (P3-1 перевернул смысл `null`: раньше `company_id = NULL`
  не матчил никого, теперь `:companyId IS NULL` в нативном запросе значит «все») не была
  покрыта — существующие тесты гоняют руководителя **с** компанией и прошли бы и без ветки.
  Сделано: `companyHeadWithoutCompany_seesNobody_notEveryone` в `UserListPaginationTest` —
  200 с пустой страницей (не 403 — ровно поведение до P3-1), сеются пользователи `comp-01`,
  `comp-02` и **без компании вовсе** (пустота — не «NULL = NULL совпал», а «запрос не
  выполнялся»), `search` и `role` тоже не становятся обходом. Доказательный критерий проверен
  руками: с временно убранной веткой тест красный, с веткой — зелёный; у ветки в
  `UserService` — ссылка на сторожащий тест.

- **P3-3 (24.08.2026). Гигиена репозитория.** Было: отслеживалось 438 файлов, из них 178 мусора
  — `build/` (110, включая два jar по 63 МБ июльской сборки), `.gradle/` (56), `.idea/` (12 из
  13) и 12 файлов дублирующих gradle-обёрток в `auth/`/`directory/`/`pbl/`; `.gitignore` из двух
  строк с ведущими слэшами (якорят к корню — потому модульные каталоги и отслеживались);
  `.gitattributes` не было, и `gradlew.bat` показывал фантомный diff всех 164 строк (CRLF/LF);
  README из двух одинаковых строк. Сделано: `.gitattributes` (первым — `* text=auto eol=lf`,
  `*.bat`/`*.cmd` `eol=crlf`, бинарные типы) + `git add --renormalize`; `.gitignore` переписан
  (`build/`/`.gradle/` **без** ведущего слэша; `.idea/*` с возвратом `!checkstyle-idea.xml` —
  тонкости записаны комментариями прямо в файле); мусор раскоммичен `git rm -r --cached` (на
  диске остался), модульные обёртки удалены и с диска — ими не пользовалось ничто (проверено по
  скриптам, `.agents/` и документам); README заменён на настоящий (модули, порты, запуск,
  тесты, карта документов), добавлен `.env.example` без значений. Отслеживается 309 файлов
  (248 из прежних + 2 новых + 59 файлов прошлых спринтов, прежде не добавленных в индекс —
  застейджены попутно `git add -A`, без него `--renormalize` падал на отслеживаемых, но
  удалённых с диска файлах). История НЕ переписывалась (оба jar остаются в `.git` — то же
  решение, что по ключу JWT), секреты этой задачей НЕ чистились (проверено, что в
  раскоммиченном их и не было), коммитов НЕ делалось — порядок коммитов за заказчиком.
  Проверено прогоном: `./gradlew cleanTest test` — 621 зелёный, `npm run build` — чисто.


- **P3-4 (24.08.2026). Комментарии в java сжаты вдвое.** Было: 2723 строки комментариев на 6433
  строки кода в `main` (30%) и 1932 на 9655 в тестах (17%); 2334 из 2723 — блочные `/** */`,
  279 строк одной только HTML-разметки, 38 тегов `@param`/`@return`, пересказывающих имя поля;
  в 20 файлах из 121 комментариев было больше, чем кода. Разросся текст, а не смысл. Сделано:
  форма — только `//`, по-русски, максимум 4 строки на блок (правило записано в §8); оставлены
  четыре категории (запрет с последствием, «почему не очевидным способом», инвариант вне типа,
  ссылка на решение), убраны пересказ кода и имени поля, разметка, риторика, дубли и история
  правок, не объясняющая текущий код. Итог: `main` 2723 → **1082** (14,4%), тесты 1932 → **1085**
  (10,1%). По модулям (main): `common` 639 → 248, `auth` 454 → 202, `directory` 306 → 123,
  `pbl` 1324 → 509.
  1. **Ни одной строки кода не изменено** — доказано построчно: из каждого файла вырезаются
     комментарии (с учётом строковых литералов, чтобы `"http://…"` осталось кодом) и результат
     сличается с эталоном. Эталон — **индекс git**: после P3-3 весь код был застейджен, поэтому
     индекс и есть состояние до P3-4 (`HEAD` для этого не годится — в дереве лежит несколько
     спринтов незакоммиченного). 177 файлов, расхождений нет; строк с `@Test` — 502, как было.
  2. **Тринадцать фактов из таблицы ТЗ проверены поимённо** (17 grep-проверок): `PAID_STATUSES`
     не сводить к `SUCCESS` и почему `AUTHORIZED` вне набора; `SLOT_OCCUPYING_STATUSES` выведен
     из него, а не переписан; в `SearchTerms` — почему `!`, а не бэкслеш, почему такой порядок
     замен и почему индекса нет; запрет `ACCESS`; «не enum»; «не `JpaRepository`»; warn вместо
     исключения; синхронная запись при откате; ветка руководителя без компании; `revoked > 0`
     в logout; защита `looksLikeLiteral` от DNS-запроса; непустой `ridByPmo`; «деньги могли
     уйти» в обоих денежных путях `pbl`.
  3. **Тонкость приёмки:** буквальный `grep -rc "/\*\*" */src/main/java` даёт один файл —
     `PublicEndpoints`, где `/**` это Ant-пути в **коде** (`"/api/v1/auth/**"`), трогать которые
     нельзя. Строгий grep по началу строки (`^\s*/\*`, `^\s*\*/`, `^\s*\* `) даёт **0** и в
     `main`, и в тестах: блочных комментариев не осталось нигде.
  Фронтенд (там 5%), доки, Liquibase-changelog'и, `.gitignore` и `.gitattributes` не тронуты —
  проверено `git diff` по путям.

- **P3-6 (24.08.2026). `SettingsPage`: удалены настройки, которых нет.** Было: 1357 строк, из них
  работали три вещи — название компании, email и язык. Остальное — константы в `useState` и
  `handleSave`, который только зажигал зелёную полосу (`setSaved(true)`, `setTimeout`). На приёмке
  это читалось как заявленная функциональность, и три утверждения были утверждениями
  **о безопасности**, все три ложные: «двухфакторная аутентификация включена» (её нет),
  «тайм-аут сессии 30 минут» (access-токен живёт по конфигу, не по этому полю), «3-D Secure
  обязателен» (переключатель ни на что не влиял). Сделано:
  1. **Удалены вкладки** Security, Payment, API & Webhooks (в последней в коде был зашит домен
     `api.acmecorp.com` из шаблона генератора), выдуманные поля Account (телефон, адрес, ИНН,
     контактное лицо — в `CompanyResponse` только `id`, `name`, `status` и четыре поля аудита)
     и Display (тема, форматы даты и времени, часовой пояс, валюта, строк на странице,
     компактный вид, метки времени — ни одно нигде не применялось).
  2. **Удалён мёртвый код:** панели `terminals` и `companies` (вкладок к ним не было, `setCurrentTab`
     звался только из списка пяти вкладок — попасть внутрь было нельзя, а внутри жила кнопка
     удаления терминала, запрещённого с P2-8), диалоги терминала/компании/пользователя,
     закомментированный блок `notifications` на 113 строк, `handleSave`, все состояния мок-настроек
     и три запроса, чей результат никуда не выводился (`/terminals`, `/users`, `/audit-logs`).
  3. **Вкладок больше нет:** живых контрола осталось два, страница стала плоской. 1357 → **185** строк.
  4. **Название компании читается одиночным `GET /api/v1/companies/{id}`** по claim'у `companyId`,
     а не страницей списка: `GET /api/v1/companies` разрешён только `SYSTEM_ADMIN` и `AUDITOR`,
     остальным ролям он отвечал 403 **и писал `DENIED` в журнал аудита** при каждом открытии
     настроек, после чего в поле оставалась константа `'MilliKart Merchant'`. Заодно закрылся
     хвост `problems.md` §14 (врезки на `size: 200` без пагинации).
  5. **Кнопка «Сохранить» перестала врать** (сверх ТЗ, Р-55): зелёная полоса — только на 2xx,
     отказ — текстом бэкенда; поле редактируемо лишь у `SYSTEM_ADMIN` (`updateCompany` пускает
     только его), пустое имя не отправляется (бэкенд молча игнорирует `isBlank` и отвечает 200).
     У кого нет `companyId`, карточка не показывается: прежний код брал `list[0]` — первую
     компанию по алфавиту — и `PATCH` переименовывал **чужую** компанию.
  6. **i18n:** блок `settings` в `translations.ts` переписан во всех трёх языках — остались
     девять ключей, которые страница действительно читает; `settings.security.*`,
     `settings.payment.*`, `settings.api.*`, `settings.notifications.*`, `settings.tabs.*`
     и ключи удалённых полей удалены.
  Проверено: `npm run build` (`tsc -b` поймал бы ссылку на удалённый ключ), `oxlint` — чисто,
  четыре grep'а приёмки — пусто. Бэкенд не тронут (`git status` по модулям — без изменений).

- **P3-7 (24.08.2026). Дашборд считает база, а не браузер.** Было: `HomePage` запрашивала
  `/api/v1/transactions` и `/api/v1/payment-links` **без `page` и `size`**, получала по двадцать
  строк умолчания контроллеров, сводила их в браузере и подписывала «All system transactions»,
  «Real-time calculation», «Real Revenue Trend». Плюс выдуманные числа и неверная арифметика:
  константа «1.2s», доли 60/40 от числа транзакций, подстановки адреса и браузера, сочинённая
  история статусов при переходе на карточку, тренд по названию дня недели, сложение разных валют,
  выручка как `SUM(amount)` по `SUCCESS` — мимо `captured_amount` и мимо `PAID_STATUSES`. Сделано:
  1. **`GET /api/v1/dashboard/summary`** (`DashboardController`, `DashboardService`,
     `DashboardRepository`). Окно `from`/`to`, по умолчанию семь календарных суток; `from > to`
     и окно больше 92 дней — **400**, а не зажим. **Три** группировки на всю страницу: часовая
     `(сутки, час, валюта, статус)`, по терминалам и по ссылкам. Итоги, разбивка по статусам,
     посуточная выручка и распределение по часам сворачиваются из первой — независимые запросы
     могли бы разойтись, и сумма по дням не сошлась бы с итогом.
  2. **Правила доступа — те же, что у `listTransactions`, и из тех же наборов:** `READ_ROLES` и
     `isGlobalReader` в `PaymentLinkService` стали `public` ради этого (§12, п. 8), своей копии
     правил у сводки нет. Нет `companyId` или у компании нет терминалов — **нули и 200**, как
     у списка. Отказ в журнал не пишется: `listTransactions` не пишет, а строки те же.
  3. **Часовой пояс — `pbl.dashboard.zone`, по умолчанию `Asia/Baku`** (Р-56). Возвращается
     в ответе, чтобы подпись на экране не выдумывалась фронтендом. **Время в SQL не трогается
     вовсе:** база дробит строки не крупнее часа, сутки и час в поясе отчёта сервис вычисляет
     из `MIN(created_at)`. Первая версия сдвигала колонку прямо в запросе и **падала на
     PostgreSQL**, пройдя все тесты на H2, — `problems.md` §19.
  4. **`GET /api/v1/transactions/{id}`** — чтения транзакции по id **не было вовсе**, при том что
     `TransactionDetailPage` его уже звал и молча получал отказ. Без него нельзя было убрать
     сочинённый `statusHistory`: карточке нечем открыться. Без обращения к эквайеру.
  5. **У списка транзакций появился порядок.** `PageRequest.of(page, size)` шёл **без сортировки**
     — база отдавала строки как ей удобно, и «последние операции» были просто какими-то.
     Теперь `createdAt DESC, id DESC` (довесок по id — по той же причине, что в P2-1).
  6. **Индекс `idx_transactions_created` (changeset `008`).** Ни один из трёх индексов P2-3 не
     обслуживает отбор по диапазону `created_at` без фильтра по статусу.
  Проверено: `DashboardSummaryTest` (17), `./gradlew test` зелёный, `npm run build` и `oxlint`
  чисто, семь grep'ов приёмки — пусто.

- **P3-5a (25.08.2026). Отмена ссылки из списка спрашивала не больше, чем промах мышью.** Было:
  иконка «Cancel link» в строке таблицы (`PayByLinkPage.tsx:645`) звала `handleCancel(link.id)`
  напрямую — ссылка гасла без вопроса, хотя на её карточке подтверждение было с самого начала.
  Пробел нашёлся при проверке P3-5 (Р-55) и в её объём не входил. Сверх того пять денежных
  диалогов были написаны по-английски мимо `translations.ts`, а окно возврата средств называлось
  отменой транзакции. Сделано:
  1. **Отмена из списка идёт через диалог** — `cancelTarget: PaymentLink | null` вместо голого
     вызова. Держим ссылку, а не её id: окну нужны `shortCode` и сумма. В таблице из двадцати
     похожих строк только они и отличают ту, по которой промахнулись, от нужной.
  2. **Список и карточка показывают слово в слово одно окно** — заголовок, вопрос, рамка с
     коротким кодом и суммой, «Оставить ссылку» / «Отменить ссылку». Общего `ConfirmDialog`
     по-прежнему нет (отклонение №1 из Р-55 не закрыто, решение за заказчиком) — сведены тексты
     и поведение, не код.
  3. **Ни одной строки в коде у пяти денежных диалогов.** Задействованы уже переведённые и до
     сих пор простаивавшие ключи: `payByLink.cancelLinkAction` (подсказка иконки),
     `cancelConfirmTitle`/`cancelConfirmText`, `payByLinkDetail.cancelLink`/`finalizeDMS`,
     `transactions.detail.refundAction`/`refundTitle`/`confirmRefund`. Заведены на три языка
     восемь новых: `payByLink.keepLink` и `transactions.detail.completeAction`/`completeTitle`/
     `captureExplains`/`captureAmount`/`confirmCapture`/`refundQuestion`/`keepTransaction`.
  4. **Окно возврата говорит о возврате.** Кнопка звалась `Cancel & Refund`, окно —
     `Cancel Transaction` («cancel this transaction», про возврат ни слова), а бэкенд зовёт
     `POST /transactions/{id}/refund` и пишет в журнал `REFUND`. Теперь кнопка, заголовок,
     текст и журнал говорят одно слово.
  5. **Сумма ушла из фразы в рамку** у обоих окон списания холда — подстановки внутрь
     предложения словарь не умеет, а рамка совпала с той, что уже была на карточке транзакции.
  6. **Подтвердить дважды нельзя:** `*Busy` у всех четырёх окон гасит обе кнопки на время
     запроса и не даёт закрыть окно кликом мимо. Фокус при открытии — на безопасной кнопке
     (`autoFocus`), Enter по инерции ничего не списывает и не гасит.
  Бэкенд не тронут: 638 тестов, все зелёные (`./gradlew test` — `UP-TO-DATE`). `npm run build`
  (`tsc -b` поймал бы пропуск ключа в любом из трёх языков) и `oxlint` — чисто, три grep'а
  приёмки сошлись.

- **P3-5b (25.08.2026). Правила безопасного диалога были записаны девять раз.** Было: девять
  окон подтверждения в шести файлах (`UsersPage` — удаление; `CompaniesPage` — одно окно на
  удаление и статус; `TerminalsPage` — статус и правка; `PayByLinkPage` — отмена ссылки;
  `PayByLinkDetailPage` — отмена и списание холда; `TransactionDetailPage` — возврат и списание
  холда). Каждое работало правильно, и проблема была не в них: три правила P3-5 (не закрывать
  окно во время запроса, гасить обе кнопки, держать фокус на безопасной) жили в девяти копиях,
  и десятый диалог написали бы, забыв одно из трёх, — а он бы «работал». Это уже было видно:
  `autoFocus` стоял в **пяти** окнах из девяти (P3-5a расставил его в денежных), а в `UsersPage`,
  `CompaniesPage` и обоих окнах `TerminalsPage` Enter подтверждал опасное действие. Сделано:
  1. **`app/components/ConfirmDialog.tsx`, 96 строк** — четыре правила записаны один раз, с
     комментарием, почему они здесь, а не на страницах. `onClose` игнорируется, пока `busy`;
     кнопка отказа первая и с `autoFocus`; обе `disabled={busy}`; подтверждение `contained`
     и цветное, заголовок жирный.
  2. **Всё, что различается, ушло в `children`** — карточка объекта, `Alert`, список меняемых
     полей, как есть, страницами. Ни один из девяти не потребовал особого случая внутри
     компонента; из пропсов варьируются `confirmColor`, `confirmIcon`, `cancelLabel` и
     `maxWidth`, все с умолчаниями (`error`, `common.cancel`, `sm`).
  3. **Переезд по одному, поведение сохранено.** `Alert` терминала с вычисляемым severity и
     числом затронутых ссылок; список меняемых полей у правки (логин и пароль — «Login updated» /
     «Password updated», **без значений**); `CompaniesPage` остался **одним** вызовом на два
     действия — заголовок, вопрос, цвет и надпись считаются из `pending.kind`; `shortCode`
     и суммы в рамках; сумма возврата на `error.light`; заголовки возврата из P3-5a не откатились.
     У правки терминала `busy` как не было, так и нет — окно его не заводит, а получает пропсом,
     и без него ведёт себя ровно как раньше.
  4. **Единственное намеренное изменение поведения — `autoFocus` в четырёх окнах**, где его
     не было. Enter больше нигде не подтверждает опасное действие.
  5. **Два английских ключа заведены на три языка** — `payByLink.shareDialogTitle`
     («Share Payment Link») и `terminals.registerAction` («Register Terminal»). Оба в формах,
     не в подтверждениях; заодно соседний `Cancel` переведён существующим `common.cancel`.
  Сверх ТЗ выровнены две мелочи оформления, которые иначе пришлось бы держать пропсами:
  отступы `DialogActions` (`px: 3, pb: 2.5, gap: 1` — вариант P3-5a) и `variant="outlined"`
  у кнопки отказа. Раньше у Users/Companies/Terminals было `p: 2.5` и текстовая кнопка.
  Бэкенд не тронут ни строкой — 638 тестов зелёные. `npm run build` (`tsc -b` + `vite`) чистый,
  `oxlint` — 0 ошибок, новых неиспользуемых импортов нет, шесть grep'ов приёмки сошлись.

### `technical_handover.md` описывает не то, что в коде

Не опирайся на него как на спецификацию. Заявлено, но **не реализовано**:
Void (отмена предавторизации);
строгая изоляция данных между компаниями; юнит-тесты;
кэширование справочников через Caffeine (`:75`, `:96`) — снято 17.08.2026 вместе с P0-3, см. §3.
IP-адрес в `AuditLog` с 21.08.2026 реализован (P2-6), §4.4 сверен с кодом. С 22.08.2026 журнал
покрывает все три сервиса (P2-14): учётные записи и входы в `auth`, ссылки и деньги в `pbl`.
С 24.08.2026 §4.4 содержит таблицу словаря событий (P3-2) — 30 строк, по одной на каждое
сочетание `entityType`/`action`; число строк обязано совпадать с кодом, при добавлении события
таблица правится в том же изменении.
Чего он по-прежнему не даёт — защиты от правки в самой базе: сервисы ходят под суперпользователем
(`problems.md` §13).
Refresh-токен, logout и отзыв (§2.2, §4.1) с 18.08.2026 реализованы (P1-12 + P1-13):
механизм есть, фронтенд обновляется сам, отзыв access-токена «сразу» ограничен его сроком в 15 минут
(чёрного списка нет намеренно).

---

## 11. Тесты

Большинство — интеграционные; юнит-тесты (первый появился с P0-4) помечены в таблице:

| Тест | Методов | Что покрывает |
|:---|:---|:---|
| `PublicEndpointsTest` (`common`, юнит) | 7 | P1-1: единственный список публичных путей — какие пути пускаются без токена, что `*/open` теперь ровно один сегмент, что похожие префиксы (`/api/v1/authentication`) не проходят, что springdoc отделён от `isPublic` |
| `SecurityBoundaryIntegrationTest` (`auth`, `directory`, `pbl`) | 13 + 10 + 14 | P1-1: неизвестный путь без токена → 401 и с токеном → 404, actuator на рабочем порту → 404, swagger при выключенном флаге, публичные пути продолжают работать, одинаковая структура 401 от фильтра и от Spring Security, ужесточение шаблона `/open`. Плюс (`auth`, 20.08.2026) неверный метод и неверный `Content-Type`: `GET /api/v1/auth/login` → 405 с заголовком `Allow: POST` и словом POST в тексте, форма вместо JSON → 415 с упоминанием `application/json`, а на защищённом пути неверный метод без токена по-прежнему даёт 401 — 405 не должен опережать аутентификацию и рассказывать анониму, какие глаголы у эндпоинта есть |
| `SwaggerEnabledIntegrationTest` (`auth`, `directory`, `pbl`) | 4 + 4 + 4 | P1-1: при `springdoc.api-docs.enabled=true` документация открывается без токена, всё остальное по-прежнему 401 |
| `SpringSecurityLayerIntegrationTest` (`pbl`) | 4 | P1-1: `JwtAuthFilter` подменён сквозным, отвечает только Spring Security — регрессия `anyRequest().permitAll()` ловится именно здесь |
| `ManagementPortIntegrationTest` (`pbl`) | 6 | P1-1: единственный тест на живом Tomcat — `/actuator/health` и `/metrics` на management-порту отвечают **без токена** и с деталями, на рабочем порту их нет, deny-by-default работает и в реальном контейнере |
| `ClientIpTest` (`common`, юнит) | 9 | P3-Auth, P2-10: доверенный пир + `X-Real-IP` → он; без него — **последний** элемент `X-Forwarded-For` (`9.9.9.9, 203.0.113.7` → `203.0.113.7`), а не первый; недоверенный пир с подделанными заголовками → адрес пира; заголовков нет → адрес пира; мусор в `X-Real-IP` (пусто, пробелы, 500 символов, не-адрес, `1.2.3.4.5`, адрес с хвостом) → адрес пира, но `X-Forwarded-For` при этом всё ещё читается; `::1` и `0:0:0:0:0:0:0:1` — один и тот же доверенный адрес; пустой список (и `null`) → заголовкам не верим никогда |
| `ClientIpFilterTest` (`common`, юнит) | 3 | P2-6, Р-36: держатель и MDC-ключ `clientIp` заполнены во время запроса и **очищены после** — в том числе когда цепочка бросила исключение (пул потоков переиспользует потоки, незачищенный держатель отдал бы чужой адрес следующему запросу); заголовок от недоверенного пира игнорируется — в держателе адрес пира |
| `LoginRateLimiterTest` (`auth`, юнит) | 6 | P3-Auth: `max-failures` неудач проходят, следующая — `TooManyRequestsException` с положительным `Retry-After` не больше окна; успешный вход обнуляет счётчик адреса; по истечении окна счётчик пуст (Caffeine на подставном `Ticker`, без `sleep`); разные адреса независимы; выключенный флаг не считает и не отказывает; `max-failures` < 1, нулевое и `null`-окно → отказ на старте |
| `RoleTest` (`common`, юнит) | 7 | `Role.fromValue`: пять известных значений, неизвестные, чувствительность к регистру, отсутствие `trim`, null/blank, «никогда не бросает» на мусорных строках |
| `JwtProviderTest` (`common`, юнит) | 7 | P0-5: отказ на пустом/null/коротком ключе, отказ на скомпрометированном ключе из истории git, граница 32 байта, round-trip `generateToken`/`validateAndGetClaims`, чужая подпись |
| `AdminBootstrapRunnerTest` (`auth`, юнит) | 5 | P0-6: создание `SYSTEM_ADMIN` на пустой базе, пропуск на непустой, отказ на `admin123`, отказ без переменных, отказ на логине не-email |
| `AdminBootstrapIntegrationTest` (`auth`) | 2 | P0-6: миграция никого не сидит, флаг поднимает ровно одного админа, и он может войти |
| `MigrationOrderTest` (`auth`, без Spring) | 5 | P1-2: changelog `auth` на базе, где `companies` уже создана `directory`; пустая база; `fk_terminals_company` появляется на следующем прогоне, когда возник `terminals`; двойной прогон; база, восстановленная без `DATABASECHANGELOG` |
| `AuthIntegrationTest` | 14 | логин, неверный пароль, валидация email, CRUD пользователей, блокировка после 6 попыток (плюс: 7-я попытка с неверным паролем даёт общий отказ, блокировка видна в базе и не продлевается). P3-Auth: несуществующий пользователь и неверный пароль — **побайтово одинаковый** ответ (сравниваются тела без `timestamp` и коды); заблокированный аккаунт с неверным паролем → тот же общий ответ, статуса в теле нет; с верным — «Account is not active», тоже без статуса; аккаунт под блокировкой: неверный пароль → общий ответ, верный → про блокировку со временем; 10 неудач с одного адреса → 429 + `Retry-After`; то же для **несуществующих** пользователей (лимит срабатывает до обращения к базе); подделанный `X-Forwarded-For` от одного пира не создаёт новых счётчиков; успешный вход возвращает адресу полный лимит. Каждый тест ходит со своего `remoteAddr` — это и изоляция от общего контекста, и причина, по которой заголовки в тесте 19 не считаются доверенными. С 22.08.2026 листинг пользователей проверяется по `$.content` и `$.totalElements` (P2-1) |
| `RefreshTokenConcurrencyTest` (`auth`) | 3 | P1-12: два потока против одной цепочки через `@SpyBean RefreshTokenService` (пауза в `find` / в `issue`): logout, закоммиченный между чтением токена и его ротацией, → refresh отклонён, преемник не выпущен, отзыв не затёрт; logout, пришедший, когда refresh уже держит строку, → ждёт и гасит и преемника; условный `markRotated` не трогает отозванный токен и не сдвигает `rotated_at` |
| `RefreshTokenIntegrationTest` (`auth`) | 15 | P1-12: логин отдаёт refresh-токен и `expiresIn` из настройки (в тестовом профиле 1 ч, не 86400); в базе только SHA-256; ротация даёт новую пару в той же цепочке; повтор в окне снисхождения обслуживается; **главный** — повтор за окном гасит всю цепочку, ERROR `REFRESH_TOKEN_REUSE`, новый токен тоже мёртв (`@Nested WithZeroGrace`, `rotation-grace=PT0S` отдельным контекстом); refresh после logout → 401, logout не трогает другие сессии, logout неизвестного токена/пустого тела → 204; неактивный пользователь → 401 + отзыв (и реактивация не воскрешает); блокировка и удаление через API гасят все токены; просроченный и неизвестный → 401, пустое поле → 400; оба эндпоинта без `Authorization`; уборка удаляет только просроченные |
| `TerminalBlockingIntegrationTest` (`directory`) | 9 | P2-8 (Р-37, Р-39, Р-40): блокировка переводит `ACTIVE`-ссылки терминала в `SUSPENDED`, а `EXPIRED`/`COMPLETED`/`CANCELED` и ссылки чужого терминала не трогает; разблокировка возвращает `SUSPENDED` в `ACTIVE` (в том числе ссылки без срока), а те, у кого срок истёк за время блокировки, — в `EXPIRED`; повторная установка того же статуса не трогает ссылки вовсе (проверяется в обе стороны); сбой обновления ссылок (`@SpyBean`) откатывает и саму блокировку — одна транзакция; блокировка и разблокировка пишутся в журнал действиями `BLOCK`/`UNBLOCK` с числом ссылок в `details`; `COMPANY_EMPLOYEE` получает 403 и не меняет ничего. `payment_links` в базе этого модуля нет — таблицу создаёт прогон **настоящего** changelog'а `pbl` (`SharedDatabaseSchema`), иначе межмодульный SQL не на чем проверять |
| `SharedSchemaMigrationTest` (`directory`, без Spring) | 8 | P2-8 и P2-14: общие объекты создаёт тот сервис, который стартовал первым, и любой порядок обязан работать — pbl первым, directory первым, повторные прогоны в обоих порядках, база без `DATABASECHANGELOG`, живая база с `terminals` без колонки (существующий терминал получает `ACTIVE`). Единственное место, где changelog'и двух сервисов встречаются: в остальных тестах каждый модуль поднимает свою H2, поэтому такой дефект иначе не виден (ср. `MigrationOrderTest` для `auth`) |
| `PaymentLinkStatusRepositoryTest` (`directory`, без Spring) | 1 | P2-8: блокировка терминала на базе, где `pbl` никогда не мигрировал (нет `payment_links`) — все три обновления возвращают 0 и не бросают. Первое развёртывание, где `directory` поднялся первым; раньше это был бы 500 у администратора |
| `TerminalBlockedIntegrationTest` (`pbl`) | 13 | P2-8 со стороны `pbl`: открытие `SUSPENDED` ссылки и открытие ссылки на заблокированном терминале — отказ без обращения к эквайеру и без записи транзакции, текст отказа не упоминает терминал; **гонка** — терминал блокируется, пока открытие стоит в очереди за блокировкой строки ссылки, платёж не стартует (проверено обратно: проверка выше `findWithLockById` роняет именно этот тест); создание ссылки на заблокированном терминале — 400, на активном — 201; мерчант не выставляет `SUSPENDED` и не снимает его (400 с текстом про терминал), `CANCELED` из `SUSPENDED` тоже нельзя; приостановленные ссылки остаются видимыми мерчанту в списке. **Три теста Р-38**: возврат, списание холда DMS и опрос статуса на заблокированном терминале проходят и доводят транзакцию до конца — краснеют, если кто-нибудь «наведёт порядок» и добавит проверку статуса в `refund`, `completeDms` или `refreshStatus` |
| `DirectoryIntegrationTest` | 13 | жизненный цикл компании + аудит, терминалы + RBAC; отсутствие кэша поверх проверок прав (P0-3); роли на запись терминалов (P1-15). С 21.08.2026 проверки журнала ходят по `$.content[...]` и ждут порядок «новые первыми» (P2-2), а вместо удаления терминала проверяется блокировка (P2-8) — плюс отдельный тест на то, что `DELETE /api/v1/terminals/{id}` отвечает 405 и терминал остаётся на месте. С 22.08.2026 листинг терминалов проверяется по `$.content` и `$.totalElements` (P2-1) |
| `AuthAuditIntegrationTest` (`auth`) | 12 | P2-14: заведение пользователя (роль в `details`, IP заполнен), смена роли (`role COMPANY_EMPLOYEE -> SYSTEM_ADMIN` — «обновлён» здесь бесполезно), блокировка и разблокировка отдельными действиями, смена пароля; успешный вход (`performedBy` — вошедший, `entityId` — **логин, не UUID**, P3-2), неудачный вход (`DENIED` + категория причины) — и **проверка, что введённого пароля нет ни в одной записи**, не только в этой; неудачный вход несуществующего пользователя (`companyId` пуст); блокировка после шестой неудачи отдельной записью; **20 попыток при пороге 10 оставляют одну запись о превышении лимита, а не двадцать** — с P3-2 в `entityId` записи логин, адрес в `client_ip`; P3-2: logout с живым токеном пишет `AUTH`/`LOGOUT`/`SUCCESS` с логином, logout с незнакомым токеном и повторный logout того же токена не пишут **ничего** (204-камуфляж не должен быть спам-каналом); вход не ломается при полностью недоступном журнале (таблица снесена на время попытки) |
| `PblAuditIntegrationTest` (`pbl`) | 7 | P2-14: возврат и списание холда — сумма, валюта и идентификаторы эквайера в `details`; **исход неизвестен (502) → запись есть, хотя транзакция откатилась и ни возврат, ни статус не применились** — та самая запись, по которой потом сверяют с эквайером, и она единственная в базе, с P3-2 её `outcome = UNRESOLVED`, а не `SUCCESS` (проверяется у capture и у refund); отказ в доступе к транзакции чужой компании (`DENIED`, подшит под компанию актора, с P3-2 — действие `TERMINAL`/`READ`, как в `directory`, а не приватное `ACCESS`); создание и отмена ссылки; возврат проходит при снесённой таблице журнала |
| `AuditLogAppendOnlyTest` (`common`, юнит) | 3 | Р-42: у репозитория записи ровно один метод `save`; ни одного унаследованного `delete`/`update`/`truncate` (расширение `JpaRepository` добавило бы их молча); у сущности `AuditLog` нет сеттеров — вернувшийся `@Setter` сделал бы журнал редактируемым через тот самый `save` |
| `AuditLogIntegrationTest` (`directory`) | 16 | Р-35, Р-36 (P2-5, P2-6): успешное создание → запись с `outcome = SUCCESS` и заполненным `clientIp`; P3-2: смена статуса компании — свои события `COMPANY`/`BLOCK` и `UNBLOCK` рядом с `UPDATE`, эхо того же статуса события не порождает; значение вне словаря (`SOMETHING_ELSE`/`FROB`) пишется **как есть**, без исключения, с WARN-маркером `AUDIT_OUTSIDE_DICTIONARY` по обоим полям; **откат операции → записи нет** — и через `TransactionTemplate.setRollbackOnly` после вызова сервиса, и через сбой на коммите (имя компании длиннее varchar(255) проходит `@NotBlank`, падает на flush — путь P2-8: аудит уже вызван, транзакция не состоялась); отказ (`COMPANY_MANAGER` правит чужую компанию) → 403, запись `DENIED`, имя в базе прежнее; запись отказа переживает откат объемлющей транзакции; **отказ в чтении самого журнала пишется** — `COMPANY_EMPLOYEE` и `COMPANY_HEAD` без компании, оба branch'а `listAuditLogs` (эти два краснеют, если вернуть `@Transactional(REQUIRES_NEW)` вместо шаблона: self-invocation, запись уходит с откатом `readOnly`-транзакции); переполняющий ввод (имя 5000 символов, id 400) → по-прежнему 403, а не 500, запись есть и обрезана по колонкам; запись об отказе подшита под компанию актора, а не под названную в запросе (иначе — текст в чужом обзоре аудита); падающий `save` журнала (`@SpyBean`) не ломает ни успех (201, ERROR-маркер от `AuditLogWriter`), ни отказ (403, маркер от `AuditLogService`) — оба ловятся `ListAppender`; `X-Real-IP` от доверенного пира (127.0.0.1) → он в записи, от недоверенного `remoteAddr` → адрес пира; прямой вызов сервиса вне запроса → запись есть, `clientIp` пуст, исключения нет |
| `AuditLogQueryTest` (`directory`) | 13 | P2-2: фильтр `entityType`/`entityId` для `COMPANY_HEAD` в базе даёт то же, что прежний `stream().filter` в памяти (включая нечувствительность к регистру — запрос `terminal` находит `TERMINAL`); чужие записи не видны даже при точном `entityId`; страницы 0 и 1 не пересекаются, `totalElements`/`totalPages` верные; порядок от новых к старым (и по `entityId` фикстур, и по убыванию `createdAt`); `page=-5&size=0` → 200 со срезанными значениями, а не 500, `size=2000000000` → 200 записей максимум; `AUDITOR` видит записи всех компаний и записи без компании. P3-1: `entityType` и `entityId` фильтруют **по отдельности** (регресс D.1); **пять записей на один `created_at` при `size=2` не дублируются и не теряются** (регресс D.3 — довесок `id DESC`, сеются через SQL: `@CreationTimestamp` перетирает время из билдера); `search` матчит `performedBy`/`action`/`entityId`/`details` без учёта регистра, пустая строка = нет фильтра; `%`/`_` буквально («haus» — ловушка для неэкранированного `h_u`); поиск не обходит скоуп компании; `outcome=denied` фильтрует, неизвестный `outcome` = нет фильтра; `from`/`to` — instant и дата целым днём по UTC, мусор = нет фильтра |
| `AuditLogSchemaTest` (`directory`) | 1 | P2-2: три индекса changeset'а 004 существуют после миграции — читаются `DatabaseMetaData.getIndexInfo`, а не `DATABASECHANGELOG` (прекондишен мог тихо пропустить changeset, бухгалтерия Liquibase этого не покажет); `created_at` в двух индексах — именно DESC |
| `PaymentLinkLastPaidAtTest` (`pbl`) | 10 | P2-15: один успешный платёж → `lastPaidAt` равен его времени; три платежа → время **последнего** (Р-46); `REFUNDED` и `PARTIALLY_REFUNDED` — дата оплаты **не пропадает** (возврат переписывает статус самой транзакции, отдельной строки нет); только `FAILED`/`PENDING`, только `AUTHORIZED` (холд — не платёж) и ссылка без транзакций → пусто; значение совпадает в одиночном ответе и в строке списка. **Главный тест — страница из 20 ссылок**: пакетный метод вызван ровно раз, поштучный не вызван ни разу, и число подготовленных запросов Hibernate у страницы из 20 строк равно числу у страницы из одной (при регрессии — 22 против 3). Проверено обратно: подмена реализации на поштучный поиск роняет тест обоими способами по отдельности. Пустая страница не выполняет запрос за датами вовсе — `IN ()` не SQL |
| `PaymentLinkRefundUsageTest` (`pbl`) | 10 | P2-16 (Р-49, Р-50): возврат не отменяет использование ссылки. **Главный тест** — лимит 3, два платежа, один возвращён: ссылка принимает ровно **один** платёж (не два), после него становится `COMPLETED`, четвёртое открытие отказано; открытие `ACTIVE`-ссылки с занятыми слотами, включая возвращённый платёж, отказано **до** похода к эквайеру (запись `COMPLETED` на пути открытия откатывается с отказом — это штатно). Частичный возврат через настоящий эндпоинт (вернули 1.00 из 100.00, эквайер-мок подтверждает по §5.7) — `currentPaymentsCount` не меняется; `refundedPaymentsCount` считает полные и частичные возвраты вместе (2 при `REFUNDED` + `PARTIALLY_REFUNDED`) и равен нулю без возвратов; понижение `maxPayments` ниже числа использований отвергается с учётом возвращённых (1 < 2 → 400 с обоими числами, равное — 200 с обоими счётчиками в ответе). Регрессии: оплаченная и возвращённая одноразовая остаётся `COMPLETED` и не открывается; холд `AUTHORIZED` занимает слот (P1-6 — сторожит `+ AUTHORIZED` в выводимом `SLOT_OCCUPYING_STATUSES`). Колонка `current_payments_count` совпадает с ответом API на всём пути «оплата → возврат → снова оплата» |
| `TransactionIndexSchemaTest` (`pbl`) | 1 | P2-3: три индекса changeset'а 007 существуют после миграции и состоят из ожидаемых колонок — читается `DatabaseMetaData.getIndexInfo`, не `DATABASECHANGELOG` (тот же довод, что у `AuditLogSchemaTest`); `created_at` — DESC в индексе по ссылке и **ASC** в индексе сверки: сверка разгребает очередь от старых к новым |
| `UserListPaginationTest` (`auth`) | 16 | P2-1: три страницы по 3 из 7 учётных записей не пересекаются и покрывают все семь, `totalElements`/`totalPages`/`number`/`size` верные; `size=5000` → 200, `page=-4&size=-1` → 200 со срезанными значениями, а не 500; soft-deleted записи отсутствуют и в `content`, и в `totalElements`; `COMPANY_HEAD` не достаёт чужую компанию **ни на одной** странице при `size=2`; `COMPANY_EMPLOYEE` по-прежнему 403. P3-1: поиск находит запись с третьей страницы (25 при `size=10`) на нулевой; регистр не важен; пустая/пробельная строка = нет фильтра; **`%` и `_` ищутся буквально** (`%` не возвращает таблицу); поиск не обходит скоуп `COMPANY_HEAD`; `totalElements` считает отфильтрованное (3 иглы из 23 строк → одна страница); находится по совпадению **только в названии компании** (join в таблицу `directory`); `role=auditor` фильтрует на сервере без учёта регистра, `role=SUPERHERO` = нет фильтра. P3-1a: **`COMPANY_HEAD` без компании — 200 с пустой страницей, а не все пользователи** (`:companyId IS NULL` в нативном запросе значит «без фильтра», ветку сторожит именно этот тест — без неё он красный, проверено); сеется и пользователь с `companyId = null`, `search`/`role` обходом не становятся |
| `DirectoryListPaginationTest` (`directory`) | 21 | P2-1 и Р-45: страницы компаний и терминалов не пересекаются, `totalElements` верный; **две компании с одинаковым именем при `size=1` возвращаются ровно по разу** — сторож уникального довеска в сортировке, то же для двух терминалов; потолок 200 и отрицательные `page`/`size` приводятся на обоих эндпоинтах; удалённая компания не видна ни в `content`, ни в счётчике; `COMPANY_HEAD` по-прежнему 403 на списке компаний, `AUDITOR` по-прежнему читает, `COMPANY_HEAD` видит только свои терминалы на всех страницах; `/terminals/options` отдаёт **заблокированный** терминал тоже, не содержит ни слов `login`/`password`, ни самих значений (проверяется по сырому телу — поймает и поле, добавленное позже), поля ровно три; `COMPANY_HEAD` видит в `options` только свою компанию, `COMPANY_EMPLOYEE` допущен. P3-1: поиск компаний и терминалов находит запись с третьей страницы (25 при `size=10`) на нулевой, терминал — и по id как тексту; регистр не важен, пустая строка = нет фильтра; **`%` и `_` буквально** на обоих списках («Parts»/«haus» — ловушки для неэкранированных `r_s`/`h_u`); терминал находится по совпадению **только в названии компании** (join внутри модуля); поиск терминалов не обходит скоуп `COMPANY_HEAD` |
| `PaymentLinkIntegrationTest` | 71 (80 запусков) | создание/чтение ссылок, RBAC (401/403), DMS complete + refund, изоляция листинга транзакций между компаниями, доступ к `/status`, серверный рендер страницы возврата, транзакции по ссылке (P0-4). P1-6: переоткрытие не трогает `AUTHORIZED`, холд занимает слот одноразовой и многоразовой ссылки, `PENDING` по-прежнему гасится. P1-7: многоразовая ссылка не закрывается после первой оплаты и закрывается после последней. P1-9: срок по умолчанию, явный срок, срок в прошлом и за потолком (400 на создании и на PATCH), потолок от `created_at` на состаренной ссылке, продление внутри потолка, `expiresAt` в ответах создания и `GET /{id}`, просроченная ссылка не открывается, планировщик помечает просроченную `EXPIRED`. P1-8a: `completeDms` для `PENDING` при незнакомом статусе от эквайера → 400, capture провайдеру не уходит. P1-8b: `Rejected` с `DeclineDescription` в `custAttrs` → `failureReason: "Invalid PAN"` в ответе `/status` и `mpDeclineReason` в базе, повторное чтение отдаёт сохранённое; `FullyPaid` с `PmoResultCode: Approved` → `failureReason` пуст. P0-9: после `/open` пароль в `provider_password`, в сырой колонке `provider_response` ни ключа, ни значения; redirect несёт пароль, а root-логгер за время открытия — нет; опрос с `password` в payload'е хранится без него при целых остальных полях и `mpStatusOutcome`; пустой ответ поверх «старой» записи с паролем не копирует его вперёд. P1-16: опрос с payload'ом §5.8.6 (`trans[]` + `srcToken` + `password`) → `/status` отдаёт `cardNumberMasked` `426863******3689`, `rrn` `629677123123123123`, `approvalCode` `629677`, повторное чтение — то же без второго вызова эквайера; `GET /transactions` показывает те же три поля, равные ответу `/status`. P2-9 (13 методов, 22 запуска): сумма меняется у ссылки без транзакций и у ссылки с одними `FAILED`; каждый из пяти запирающих статусов (`@EnumSource`) даёт 400 и прежнюю сумму в базе; та же сумма при `SUCCESS` — 200, а не отказ; `CANCELED → ACTIVE` со сроком в будущем — 200, с истёкшим — 400, с новым `expiresAt` в том же запросе — снова 200 (порядок применения полей); `EXPIRED`/`COMPLETED → ACTIVE` и `COMPLETED → CANCELED` — 400; `ACTIVE`/`EXPIRED → CANCELED` — 200; установка текущего статуса — 200 для всех четырёх статусов; `maxPayments` ниже числа успешных платежей — 400 с обоими числами, равное — 200 |
| `OpenLinkConcurrencyTest` (`pbl`) | 2 | P1-5: два одновременных открытия одной ссылки (`CountDownLatch` + задержка 200 мс в моке провайдера) дают ровно одну запись в `transactions` и один вызов `createEcomOrder` — для одноразовой ссылки и для многоразовой с `maxPayments = 1` |
| `TransactionReconciliationIntegrationTest` | 18 | фоновая сверка зависших `PENDING`: окна `min-age`/`max-age`, недоступность шлюза, `AUTHORIZED` и терминальные статусы, `batch-size`, устойчивость батча. P1-8a (**главный** — `providerAnswers("Paid")` старше `max-age` → остаётся `PENDING`, `mpProviderStatus: "Paid"`, без `reconciliationOutcome`): `Refused`, `PartPaid`, `Cancelled` → `PENDING`, не `FAILED`; `Expired` → `FAILED` сразу; ответ без `status` и `status` числом → `PENDING` без падения; запись старше `give-up-age` не выбирается и эквайер не опрашивается; старые записи не забивают батч; пустой ответ (`null`) от эквайера сохраняет прежний `providerResponse` и оставляет `PENDING` |
| `ProviderOrderStatusTest` (`pbl`, юнит) | 6 | P1-8a: вся таблица словаря (13 значений → свой outcome), `null`/пустая/пробелы → `UNKNOWN`, регистр и пробелы значимы, правдоподобные, но отсутствующие в контракте слова (`Paid`, `Settled`, `Completed`) → `UNKNOWN`, не-строки → `UNKNOWN` без исключения, `Cancelled` и `Canceled` дают один outcome |
| `ProviderOrderDetailsTest` (`pbl`, юнит) | 22 | P1-16: payload §5.8.6 → все три поля; §5.8.3 (`lastTran`, без `trans`) → `rrn`/`approvalCode` из `lastTran`; покупка + возврат → покупка; покупка + `Purchase - Void` → покупка; `isReversal: true` пропускается; две покупки → самая ранняя по `regTime`; записи без `description` (DMS) → самая ранняя не-реверсальная; только возвраты/реверсалы → пусто без исключения; ни `trans`, ни `lastTran` → пусто; `lastTran`-возврат → пусто; без `srcToken` — пуста только маска; `srcToken` без `displayName`; маска не переформатируется; `rrn` числом → текст, объектом → пусто, пробелы → пусто; `trans` строкой/картой, элементы не карты, `srcToken` строкой, `lastTran` списком, `null`-payload — пусто без падения; `isReversal` строкой `"true"`/мусором; `regTime` отсутствует/«yesterday» — без парсинга и без падения; «ничего не нашлось» → одна DEBUG-строка с `id` заказа и без пароля (в т.ч. вложенного), «нашлось» → тишина |
| `ProviderDeclineReasonTest` (`pbl`, юнит) | 15 | P1-8b: порядок §5.8.7 — `DeclineDescription` первым, иначе `PmoDeclineDescription`, иначе `PmoResultCode`; `PmoResultCode: Approved` → пусто (§5.8.3); `null`-заказ, отсутствующий/не-список `custAttrs`, элементы не карты, пустой список, `valAsStr` не строка/пустой/`null`, посторонние атрибуты — без падения |
| `MoneyOperationsIntegrationTest` | 23 | P0-7: запрет повторного capture, `PENDING` с опросом эквайера, 502 при неизвестном исходе, сохранность `refundedAmount`, отсутствие фабрикации `refundId` (теперь: подтверждение без `tranActionId` → `refundId` пуст, `acquirerReference` есть), отсутствие повторов. P0-8: частичная сумма уходит эквайеру и пишется в `capturedAmount`, сумма выше авторизованной и третий знак → 400, потолок возврата от захваченной суммы (главный тест), полный возврат захваченного → `REFUNDED`, SMS без capture считает от `amount`. P1-8b: подтверждённый возврат отдаёт три идентификатора и пишет одну запись `mpRefunds`; два частичных возврата → две записи с разными идентификаторами и суммами; неподтверждённый возврат / capture → 502, `refundedAmount`/статус/`capturedAmount` не тронуты и следа нет; подтверждённый capture пишет `mpCapture` с `ridByPmo` |
| `TxpgAcquiringClientTest` (`pbl`, юнит) | 35 | классификация сбоев `completeDms`/`refund` в живом клиенте через `MockRestServiceServer`: `errorCode`/4xx → отказ, 5xx/таймаут → неизвестный исход; P0-8: тело Clearing с суммой и формат суммы (`1E+3` → `"1000.00"`). P1-8b: ответ ровно из §5.7 → три идентификатора и `raw`; без `ridByPmo`, `{}`, `match` строкой/числом, `ridByPmo` из пробелов → `PaymentOutcomeUnknownException` (в сообщении назван `tran.match.ridByPmo`, тело в ERROR-логе), не `ClassCastException`; `ridByPmo` числом → строка, а объектом (`{}`, `{"id":"x"}`) или списком → `PaymentOutcomeUnknownException` (идентификатор — только скаляр, java-строка `{id=x}` в вердикт не утекает); без `approvalCode`/`tranActionId` или с объектом вместо `approvalCode` → успех + WARN (лог через `ListAppender`); `errorCode` по-прежнему `BusinessException` и решается раньше проверки подтверждения. P0-9 (логгер клиента на TRACE, `ListAppender`, проверяются сообщения и исключения всех уровней): `getOrderStatus` (успех с payload'ом §5.8.3, `errorCode`, таймаут), `completeDms`/`refund` (успех, 5xx, таймаут, неподтверждённый ответ с посторонним `password` → `NO CONFIRMATION` без него, но с остальным телом), `createEcomOrder` (DEBUG-лог тела) — ни одной строки с паролем, притом пароль **есть** в URL запроса (Р-25); `EcomCreateOrderResponse.Order.toString()` маскирует пароль, показывает `hppUrl`/`id`/`status`, `null` показывает как `null` |
| `ProviderPayloadsTest` (`pbl`, юнит) | 18 (36 запусков) | P0-9: `urlForLog` — URL опроса с `?password=…&orderDetailLevel=2…` → без query и без значения; адрес без query как есть и без выдуманного `password=***`; redirect плательщика → голый адрес; `null`/`""`/`не-url`/`http://`/`http://[::1`/пробелы/`?password=…` — без исключения; `null`/`""` как есть; неразбираемый адрес → заглушка, не вход; opaque-URI теряет «query». `withoutSecrets` — убирает `password`, сохраняет остальное (вложенные структуры целиком); не меняет вход (`Map.of`), копия изменяемая, `null` → `null`. P1-16: `scalarText` — строка как есть; `long`/`int`/`BigDecimal` → текст; `null`/пустая/пробелы/таб → `null`; `Map`/`List`/`Boolean` → `null` |
| `UrlConfigurationCheckTest` (`pbl`, юнит) | 14 | P1-10: три HTTPS-адреса → ни одного WARN; HTTP у `api-base-url` → ровно один WARN с адресом, `PBL_PROVIDER_API_BASE_URL` и словами про Basic-авторизацию; HTTP у `gateway-base-url` → WARN; `http://localhost:8080/`, `http://127.0.0.1:8080/` и `http://[::1]:8080/` в `base-url` → тишина, `http://pay.example.com/` → WARN; пустой, относительный (`/pay`), `ftp://`, непарсящийся, с пробелом/CRLF на конце (перевод строки виден в сообщении), незаменённый `https://ВАШ_ДОМЕН/` и пустой адрес шлюза → `IllegalStateException` с именем переменной. Лог перехватывается `ListAppender` logback |
| `ConfigurationExternalizationTest` (`pbl`, без Spring) | 3 | P1-10: боевой `application.yaml` читается с диска и не содержит `millikart.az` и `localhost:8080`; три адреса — ровно `${VAR}` без дефолта внутри плейсхолдера — сторож от повторного захардкоживания адресов |
| `DashboardSummaryTest` (`pbl`) | 17 | P3-7: доступ — руководитель компании видит только свои терминалы (чужая компания с деньгами засеяна рядом), `SYSTEM_ADMIN` видит всех, руководитель **без компании получает нули и 200**, а не 403, нераспознанная роль — 403. Деньги: частичное списание считается по `captured_amount`, а не по авторизованной сумме; `PARTIALLY_REFUNDED` остаётся оплаченным и уменьшает выручку на возврат, а не выпадает из неё; две валюты не сливаются ни в одной секции. Окно: `from > to` и окно больше 92 дней → 400; день без платежей присутствует с нулями; **два платежа по разные стороны полуночи пояса отчёта расходятся по своим суткам** — сторож группировки: одного платежа мало, сутки корзины берутся из её самого раннего момента и одиночная строка легла бы верно при любом дефекте. Отдельный вложенный контекст с `pbl.dashboard.zone=Asia/Tokyo` и **своим** `MockMvc` ловит подмену пояса отчёта системным — на бакинской машине она иначе неотличима. Все шесть статусов присутствуют, включая нулевые; терминал в топе назван именем из таблицы. Чтение по id: своя транзакция читается и **эквайер при этом не опрашивается** (`verifyNoInteractions`), чужая — 403. Порядок списка транзакций — новые первыми |
| `AuthApplicationTests`, `DirectoryApplicationTests`, `PblApplicationTests` | 3 | `contextLoads()` |

Итого `./gradlew test` гоняет 542 метода; с раскрытием `@ParameterizedTest` в `RoleTest`,
`JwtProviderTest`, `PublicEndpointsTest`, `ProviderOrderStatusTest`, `ProviderDeclineReasonTest`,
`ProviderPayloadsTest` и `PaymentLinkIntegrationTest` — 638 запусков (`:common` — 80, `:auth` — 96,
`:directory` — 97, `:pbl` — 365).

`common` до 16.08.2026 не имел каталога тестов вообще; `src/test/java` создан под `RoleTest`,
зависимость `spring-boot-starter-test` в `common/build.gradle` была уже объявлена.

Тесты гоняются на H2 (`MODE=PostgreSQL`) и с **выключенным** fallback-токеном (`api-token-enabled: false` во всех трёх тестовых профилях,
с 17.08.2026). С P1-1 тестовые профили повторяют боевую конфигурацию ещё в двух местах:
`management.server.port` задан (9081/9082/9080) и `springdoc.*.enabled: false`. Это важно
помнить: `src/test/resources/application.yaml` **полностью заменяет** боевой, а не дополняет
его, поэтому без этих строк тесты проверяли бы не ту конфигурацию, что работает в проде.
Под MockMvc management-контекст не поднимается вовсе — порт при сборке не занимается,
а `/actuator/**` на основном порту честно отдаёт 404. Раньше он был включён, но им не пользовался ни один тест: `AuthIntegrationTest`
объявлял поле `fallbackToken` и ни разу к нему не обращался — поле удалено вместе с токеном.
Ключ подписи в тестах — `test-only-jwt-secret-not-used-anywhere-else-0123456789`, один и тот же
в `auth`, `directory` и `pbl`.

`MigrationOrderTest` — единственный тест без Spring-контекста, который гоняет Liquibase вручную
(`liquibase.Liquibase` поверх `DriverManager`) на **своей** базе на каждый метод
(`jdbc:h2:mem:auth-migration-<метод>`). Иначе P1-2 не проверить: в тестах каждый модуль поднимает
собственную H2 (`mem:auth`, `mem:directory`, `mem:pbl`), сервисы там никогда не встречаются в одной
базе — **именно поэтому конфликт миграций никто и не заметил**. Стартовые состояния (уже созданная
`directory`-версия `companies`, позже возникший `terminals`, база без `DATABASECHANGELOG`)
задаются в тесте сырым SQL. Если правишь `002-user-directory-schema.xml` — смотри сюда.

`RefreshTokenConcurrencyTest` устроен как `OpenLinkConcurrencyTest`: два потока, `CountDownLatch`,
без MockMvc и без `@Transactional` на тесте (потоки должны видеть коммиты и блокировки друг друга).
Пауза внутри `AuthService.refresh` делается через `@SpyBean RefreshTokenService` + `doAnswer` на
`find` (после шага 1) или на `issue` (внутри шага 6, когда строка уже заблокирована условным UPDATE).
Второй тест держит блокировку 300 мс — меньше lock timeout H2 (1 с), иначе ждущий logout упадёт по
таймауту вместо того, чтобы дождаться.
`RefreshTokenIntegrationTest.WithZeroGrace` — `@Nested` с собственным `@SpringBootTest(properties =
"auth.refresh.rotation-grace=PT0S")`: это отдельный контекст, поэтому вложенный класс объявляет
**свой** `MockMvc` (`zeroGraceMvc`) и все refresh-вызовы делает через него; фикстура и репозитории
внешнего класса приходят из внешнего контекста, но база одна (`jdbc:h2:mem:auth`), так что это
работает. Не переноси refresh-вызовы на внешний `mockMvc` — там окно 10 с, и тест перестанет
проверять то, ради чего написан.

`AdminBootstrapIntegrationTest` поднимает **свою** базу (`jdbc:h2:mem:auth-bootstrap`): остальные
тесты `auth` делят `jdbc:h2:mem:auth` на весь JVM, а этому нужна пустая таблица `users` в момент
старта контекста — раньше, чем любой `@BeforeEach` успел бы её вычистить. Раннер сам разобран
юнит-тестом на моке репозитория, потому что «база пуста» на общей H2 не гарантируется порядком
тестов.

**Провайдер в тестах.** С 20.08.2026 стаба нет ни в боевой сборке, ни в тестовом профиле:
`TxpgAcquiringClient` — единственная реализация, и контекст, который её не подменил, получает
именно её (звонить ей при этом некому — тесты, которым провайдер не нужен, его не трогают).
Подмена делается явно, импортом:

- `@Import(StubAcquirerConfig.class)` — `PaymentLinkIntegrationTest` и
  `SecurityBoundaryIntegrationTest`. Конфигурация даёт `@Primary`-бин: мок Mockito, делегирующий
  тестовому двойнику `StubAcquiringClient` через `AdditionalAnswers.delegatesTo`. По умолчанию
  работает двойник, а тест, которому нужен конкретный ответ (например, нефинальный статус для
  страницы «в обработке»), переопределяет один метод через `doReturn(...)` и по-прежнему может
  `verify(...)`. **Сбрасывать мок между методами приходится самим** — `Mockito.reset` в
  `@BeforeEach` у `PaymentLinkIntegrationTest`: бин объявлен конфигурацией, а не `@MockBean`,
  поэтому слушатель Spring его не чистит, и записанные вызовы утекали бы в следующий тест
  (ловится тестами с `verify(..., never())`).
- `@MockBean AcquiringClient` — `MoneyOperationsIntegrationTest`,
  `TransactionReconciliationIntegrationTest`, `OpenLinkConcurrencyTest`: этим сценариям нужен
  отказ или задержка провайдера, то есть ровно то, чего двойник не делает.

И `StubAcquiringClient`, и `StubAcquirerConfig` лежат в `pbl/src/test/java/.../provider/`.
Возвращать их в `src/main` не надо ни под каким флагом: там это клиент, который отвечает
«оплачено», не спросив эквайера.

Изоляция `GET /transactions` покрыта семью тестами `listTransactions_*` (своя компания, чужая
компания, `SYSTEM_ADMIN`, глобальный `AUDITOR`, роль без `companyId`, неизвестная роль, без токена).
Фикстуры там создаются напрямую через `transactionRepository.save(...)`, минуя провайдера, —
делай так же в новых тестах на листинги.

Доступ к `/transactions/{id}/status` покрыт четырьмя тестами `checkStatus_*` (без токена,
чужая компания, своя компания, `SYSTEM_ADMIN`), страница возврата — пятью `redirectPage_*`
(чек, «в обработке», неизвестный `merchantRid`, кривой `tx`, игнорирование `?ID=`).

`TransactionReconciliationIntegrationTest` устроен иначе: двойник всегда отвечает `FullyPaid`,
поэтому провайдер там подменён через `@MockBean AcquiringClient` и каждый тест сам задаёт ответ.
Сверка вызывается напрямую через `TransactionReconciliationService`, крон не ждут
(`pbl.reconciliation.enabled: false` в тестовом профиле), окна задаются
через `@SpringBootTest(properties = …)`. Возраст фикстур выставляется нативным
`UPDATE transactions SET created_at = TIMESTAMPADD(...)` через `JdbcTemplate`:
поле `@CreationTimestamp` + `updatable = false`, обычным `save` его не сдвинуть.

Изоляция `GET /payment-links/{id}/transactions` покрыта четырьмя тестами `getLinkTransactions_*`
(своя компания, чужая компания, глобальный `AUDITOR`, неизвестная роль).

`DirectoryIntegrationTest` с 17.08.2026 держит семь токенов: `adminToken`, `headTokenCompany1`
(comp-01), `headTokenCompany2` (comp-02), `managerTokenCompany1`, `employeeTokenCompany1`,
`auditorToken` (`companyId = null`) и `unknownRoleToken` (роль `HACKER` → `getRole()` даёт `null`).
Фикстуры — через приватные `createCompany(...)` / `createTerminal(...)`, каждый тест берёт
собственный диапазон id терминалов (700301+), чтобы не пересекаться с соседями.
Два теста на P0-3 (`getTerminal_afterAnotherCompanyFetchedIt_stillReturns403`,
`getCompany_afterAdminFetchedIt_stillReturns403ForForeignCompany`) устроены так, что первый запрос
делает легитимный читатель, а второй — чужая компания: с возвращённым `@Cacheable` второй запрос
отдаёт 200 вместо 403, то есть тест действительно ловит регрессию, а не просто проверяет 403.

`MoneyOperationsIntegrationTest` намеренно живёт отдельно от `PaymentLinkIntegrationTest`:
провайдер там подменён через `@MockBean AcquiringClient` (как в `TransactionReconciliationIntegrationTest`),
потому что двойник всегда отвечает успехом и `FullyPaid` — ровно тем, чего эти сценарии не должны
получать. Остальные тесты `PaymentLinkIntegrationTest` работают на двойнике через
`@Import(StubAcquirerConfig.class)`.
`TxpgAcquiringClientTest` — юнит, без Spring-контекста: `MockRestServiceServer.bindTo(RestClient.Builder)`
даёт настоящий `TxpgAcquiringClient` поверх управляемого HTTP. Это единственное место, где
классификация проверяется на живом клиенте, — интеграционные тесты работают через мок и её
не задевают. Проверено обратно: если вернуть старое поведение (`SUCCESS` в допустимых статусах,
502 → 400, единый `BusinessException`), краснеют ровно те тесты, которые должны.

Тесты P0-8 в том же классе берут фикстуры через `dmsTransaction(key, status, amount, capturedAmount)`
и `smsTransaction(key, status, amount)` — прежний `transaction(key, status)` стал обёрткой над ними,
существующие тесты не правились. Сумма, ушедшая эквайеру, проверяется `ArgumentCaptor`, а не
`any()`. Проверено обратно: если вернуть потолок возврата на `transaction.getAmount()`, краснеют
`refund_afterPartialCapture_cannotExceedCapturedAmount` (возврат 501 при захваченных 500 отдаёт
200 вместо 400) и `refund_afterPartialCapture_fullCapturedAmount_marksRefunded`
(`PARTIALLY_REFUNDED` вместо `REFUNDED`) — и только они.

`OpenLinkConcurrencyTest` — единственный тест, который гоняет два потока против одного метода.
MockMvc там не участвует: оба потока вызывают `OpenLinkService.openAndBuildRedirect` напрямую,
старт синхронизирован `CountDownLatch`, а провайдер подменён `@MockBean` с задержкой 200 мс
в ответе и разными `providerOrderId` на каждый вызов — иначе окно гонки на H2 слишком узкое,
чтобы сломанная реализация проигрывала его стабильно. Сам тест не `@Transactional` (иначе потоки
не увидели бы блокировку), фикстуры создаются напрямую через репозитории.

Проверено обратно, по одному дефекту за раз: старый четырёхфазный `openAndBuildRedirect` даёт
две обслуженные попытки и две записи — краснеют оба `concurrentOpens_*`; `AUTHORIZED`,
возвращённый в список гашения вместе с подсчётом лимита только по `SUCCESS`, роняет
`reopen_withAuthorizedTransaction_doesNotMarkItFailed` (`expected: <AUTHORIZED> but was: <FAILED>`),
`reopen_singleUseLink_withAuthorizedTransaction_isRefused` и `multiUseLink_authorizedCountsTowardsLimit`;
возвращённый `+ 1` роняет `refreshStatus_onMultiUseLink_doesNotCompleteAfterFirstPayment`
(`expected: <ACTIVE> but was: <COMPLETED>`).

Тесты P1-9 живут в `PaymentLinkIntegrationTest` и не задают собственных значений TTL: в тестовом
профиле `pbl.link.*` совпадает с боевым (`PT24H` / `P90D`), иначе суд шёл бы над конфигурацией,
которой ни у кого нет. Срок по умолчанию сверяется **с допуском** (30 с от `now + 24h`), а не на
точное равенство. Возраст ссылки в
`updateLink_onAnOldLink_cannotExtendBeyondMaxTtlFromCreation` сдвигается нативным
`UPDATE payment_links SET created_at = TIMESTAMPADD(...)` через `JdbcTemplate` — как в
`TransactionReconciliationIntegrationTest` и по той же причине (`@CreationTimestamp` +
`updatable = false`). Массовый апдейт планировщика вызывается через `TransactionTemplate`:
`expireActiveLinksBefore` — `@Modifying`-запрос, ему нужна своя пишущая транзакция.
Проверено обратно: если снять проверку в `update`, краснеют `updateLink_withExpiresAtBeyondMaxTtl_returns400`
и `updateLink_onAnOldLink_cannotExtendBeyondMaxTtlFromCreation`; если оставить проверку,
но считать потолок от `Instant.now()` вместо `link.getCreatedAt()` — краснеет только второй,
ради чего он и написан.

**Не покрыто:** идемпотентность возвратов на уровне хранилища — таблицы `refunds` и ключей
идемпотентности нет (Р-12), 502 перекладывает сверку на человека. Фронтенд-тестов нет вообще.
Поведение кэша не покрыто и покрывать нечего — кэша в коде больше нет (§3).

Правило: любое изменение backend-кода сопровождается зелёным `./gradlew test`.

---

## 12. Правила работы для AI-агента

1. **Согласовывать изменения.** Перед правкой исходников или конфигов: описать, что и зачем
   меняется, какие файлы затрагиваются, — и дождаться явного подтверждения.
2. **Обновлять документацию в том же изменении.** Меняешь API, роли, схему БД, структуру —
   правь `AGENTS.md`, `.agents/workflows/*`, `<module>/*.md` и Postman-коллекции сразу.
3. **Проверять тестами.** `./gradlew test` после любой правки backend.
4. **Не расширять поверхность атаки.** Новый публичный путь — это правка
   `PublicEndpoints` и только её: список читают оба слоя, второй копии быть не должно.
   Не «чинить» `anyRequest().authenticated()` обратно в `permitAll()`,
   не вешать `@Cacheable` поверх проверок прав, не класть секреты в yaml,
   не логировать URL и тела с паролями — адрес к эквайеру и redirect-URL только через
   `ProviderPayloads.urlForLog`, чужой payload в лог и в `provider_response` только через
   `ProviderPayloads.withoutSecrets` (P0-9, §10). Секрет в конфиге — только `${ENV_VAR}` и **без дефолта**:
   ```bash
   grep -rn 'password:\|secret:\|api-token:' --include='*.yaml' auth common directory pbl \
     | grep -v /build/ | grep -v /test/ | grep -v '\${'
   ```
   должен не находить ничего.
5. **Фронтенд: перед правкой убедись, что файл достижим из `routes.tsx`** (§9), а после правки
   прогони `npm run typecheck` — `npm run build` без него не пройдёт. Удалённый мёртвый код
   и зависимости генератора (shadcn/Radix, второй axios-клиент) не возвращать.
   Access-токен — только в памяти (`auth/session.ts`), в `localStorage` не класть; роль —
   только через `parseRole`, без значения по умолчанию; новый закрытый маршрут — строка
   в `auth/routeAccess.ts`, а не литералы в `routes.tsx` и `Sidebar.tsx`. Проверка:
   ```bash
   grep -rn "|| 'SYSTEM_ADMIN'" frontend/src          # ничего
   grep -rn "localStorage" frontend/src               # только session.ts (refresh) и LanguageContext
   ```
6. **Не редактировать применённые Liquibase changeset'ы** — только новые файлы.
   Исключения делались дважды и оба раза согласовывались явно, на одном основании: боевых
   установок нет, БД пересоздаётся. P0-6 (17.08.2026) — удаление сида админа из
   `002-user-directory-schema.xml`; P1-2 (17.08.2026) — разбиение `2-auth-core` и добавление
   `<preConditions>` в `auth/002` и `pbl/002`–`004`. На существующей базе первая правка ломает
   контрольную сумму changeset'а; вторая безопаснее (новые id с условиями на базе, где старый
   уже применён, просто уходят в MARK_RAN), но правилом от этого не становится.
   `runOnChange` и `validCheckSum` не использовать: они маскируют расхождение, а не устраняют его.
7. **Не коммитить `build/`, `.gradle/`, `.idea/`.** Сейчас они, к сожалению, в индексе:
   111 файлов под `*/build/` (из них 58 `.class`), 56 под `*/.gradle/`, 13 под `.idea/`
   (включая `pbl/.idea/dataSources/` с описанием подключения к БД). Причина — `.gitignore`
   содержит только корневые `/build/` и `/.gradle/`, поэтому модульные не игнорируются.
   При удобном случае: `git rm -r --cached` + `.gitignore` → `**/build/`, `**/.gradle/`, `.idea/`.
8. **Роли — только через `enum Role`** (§6). Никаких строковых литералов ролей в main-коде:
   ```bash
   grep -rn '"SYSTEM_ADMIN"\|"COMPANY_HEAD"\|"COMPANY_MANAGER"\|"COMPANY_EMPLOYEE"\|"AUDITOR"' \
     --include='*.java' auth common directory pbl | grep -v /build/ | grep -v /test/
   ```
   должен не находить ничего. `Role.valueOf(` — тоже нигде, только `Role.fromValue(`.
   Наборы ролей — `EnumSet`, и в `pbl` они уже есть: `PaymentLinkService.READ_ROLES`,
   `LINK_WRITE_ROLES`, `REFUND_ROLES` и `isGlobalReader(role)` — переиспользуй, а не выписывай
   список заново. Роль может быть `null` (нераспознанное значение в токене) — проверка обязана
   приводить к отказу, а не к NPE.

---

*Последняя сверка с кодом: 25.08.2026, ревизия `3f890de` + правки P0-1, P0-2, P1-3, P0-4, P0-3, P1-15, P0-7, P0-8, P0-5, P0-6, P1-11, P1-1, P1-2, P1-5, P1-6, P1-7, P1-9, P1-12, P1-14, P1-13, P1-8a, P1-8b, P0-9, P1-16, P3-Auth (+P2-10), P2-9, P2-12, P2-13, P2-2+P2-5+P2-6 (аудит), P2-8 (блокировка терминалов), P2-14 (аудит во всех трёх сервисах), P2-3+P2-1 (индексы `transactions`, пагинация трёх списков), P2-15 (дата оплаты ссылки), P2-16,
P3-2 (единый словарь событий аудита + таблица в `technical_handover.md` §4.4)
P3-1 (серверный поиск по четырём спискам, пагинация и фильтры журнала аудита),
P3-1a (сторожевой тест ветки «COMPANY_HEAD без компании»)
P3-3 (гигиена репозитория: `.gitattributes`, `.gitignore`, раскоммит `build`/`.gradle`/`.idea`,
удаление модульных gradle-обёрток, README, `.env.example`)
P3-4 (комментарии в java сжаты вдвое: `main` 30% → 14,4%, тесты 17% → 10,1%; форма — §8)
P3-6 (`SettingsPage`: 1357 → 185 строк, удалены вкладки Security/Payment/API и врезки со списками)
P3-7 (сводка главной считается в базе: `GET /api/v1/dashboard/summary`, `GET /api/v1/transactions/{id}`,
порядок у списка транзакций, `HomePage` 818 → 435 строк)
P3-5a (25.08.2026 — отмена ссылки из списка через диалог, пять денежных диалогов переведены
на три языка, окно возврата больше не называется отменой транзакции)
и P3-5b (25.08.2026 — девять окон подтверждения сведены в `app/components/ConfirmDialog.tsx`,
`autoFocus` появился в четырёх окнах, где его не было)
(возврат не отменяет использование ссылки) в рабочем дереве.*
