# История работ MP

Журнал устранения находок ревью от 14.08.2026 и всех работ после него: очередь спринтов,
журнал по датам, описание сентябрьских работ и полные описания закрытых задач. Файл только
дописывается: старые записи не правятся, даже если описанное в них с тех пор изменилось.

Где что теперь:
- решения Р-NN — [`decisions.md`](decisions.md);
- правила и известные ограничения, действующие сейчас, — корневой `AGENTS.md`;
- находки ревью — [`code_review.md`](code_review.md), заморожен: все пункты закрыты.

Упоминания файлов в записях — на момент записи. 13.09.2026 документы переехали в `project_docs/`,
а `problems.md`, `.agents/workflows/*` и `pbl/.agents/workflows/pbl.md` удалены — их тексты
остались в истории git.

**Легенда статусов:** `—` не начато · `ТЗ` задание составлено · `WIP` в работе · `REVIEW` ждёт проверки · `✅` принято

---

## Принятые решения

Таблица перенесена в [`decisions.md`](decisions.md) 13.09.2026 — там все решения Р-1…Р-72.

---

## Очередь

### Спринт 0 — блокеры

| ID | Проблема | Модуль | Статус | Зависит от | Примечание |
|:---|:---|:---|:---|:---|:---|
| **P0-1** | `GET /api/v1/transactions` не проверяет права — видны транзакции всех компаний | `pbl` | **✅** | — | Сделано 15.08.2026. Заодно закрыт P2-4 (N+1) и решение Р-1 (`AUDITOR`) |
| **P0-2** | `GET /api/v1/transactions/{providerOrderId}/status` публичный, отдаёт PII | `pbl`, `common` | **✅** | — | Сделано 15.08.2026. Заодно закрыт P1-4 (подпись провайдера на redirect) |
| **P1-3** | Нет реконсиляции зависших `PENDING` | `pbl` | **✅** | — | Сделано 16.08.2026. Callback отменён (Р-7), заготовки удалены; фоновая сверка опросом |
| **R-Role** | Ввести `enum Role`, убрать строковые литералы (P2-7) | `common` + все | **✅** | — | Сделано 16.08.2026. Заодно закрыт P0-4 и P2-7 |
| **P0-4** | `getTransactionsByLinkId` проверяет несуществующие роли | `pbl` | **✅** | ~~Р-1~~ | Сделано 16.08.2026 вместе с R-Role: `READ_ROLES` вместо `MERCHANT_*`, `principal` вместо `principal.getRole()` |
| **P0-3** | `@Cacheable` кэширует вместе с проверкой прав | `directory` | **✅** | ~~R-Role~~ | Сделано 17.08.2026 вместе с P1-15. Кэш убран целиком (Р-9), `CacheConfig` оставлен под `pbl` |
| **P0-5** | JWT-секрет в репозитории и в истории git | все | **✅** | — | Сделано 17.08.2026 вместе с P0-6 и P1-11. Секрет из `JWT_SECRET` без дефолта, старый ключ отвергается по SHA-256 |
| **P0-6** | Дефолтный `admin@millikart.az` в прод-миграции | `auth` | **✅** | — | Сделано 17.08.2026. Сид удалён из changeset'а, вместо него `AdminBootstrapRunner` из переменных окружения |
| **P0-7** | `@Retry` на неидемпотентных `refund` / `completeDms` | `pbl`, `common` | **✅** | — | Сделано 17.08.2026. Ретрай снят, введено разделение «отказ» / «исход неизвестен» (502) |
| **P0-8** | `completeDms` не передаёт `amount` провайдеру | `pbl` | **✅** | Р-11 | Закрыт полностью 17.08.2026. Запрет повторного capture — вместе с P0-7; сумма уходит в Clearing, захваченная сумма в `captured_amount`, потолок возврата считается от неё |
| **P0-9** | Пароли в query-параметрах URL и в логах | `pbl` | **✅** | — | Сделано 19.08.2026 (логи и хранение, Р-25: отправка не тронута). Блокер, ошибочно считался закрытым; проверено 18.08.2026: все три места на месте, плюс четвёртое — после P1-8a опрос заказа клал в `provider_response` payload эквайера целиком (§5.8.3: с `password`), и пятое — `getOrderStatus` логировал тело ответа целиком. Пароль в адресе `exec-tran` — `problems.md` §5 |

### Спринт 1 — работоспособность

| ID | Проблема | Модуль | Статус | Зависит от |
|:---|:---|:---|:---|:---|
| ~~P1-1~~ | Spring Security `permitAll()`, actuator и swagger открыты | `common` | **✅** (сделано 17.08.2026) | — |
| ~~P1-2~~ | Порядок старта: `auth` падает, если `directory` стартовал первым | `auth` | **✅** | — | Плюс починен потерянный FK (`MARK_RAN` → `CONTINUE`) |
| ~~P1-3~~ | Нет callback от провайдера и реконсиляции зависших `PENDING` | `pbl` | **✅** (сделано 16.08.2026) | — |
| ~~P1-4~~ | Redirect-эндпоинт не проверяет подпись провайдера | `pbl` | **✅** (закрыт в P0-2) | — |
| ~~**P1-5**~~ | Гонка при открытии ссылки — двойная оплата single-use | `pbl` | **✅** | — | Сделано 17.08.2026 связкой с P1-6 и P1-7: один `@Transactional` поверх `findWithLockById` |
| ~~**P1-6**~~ | Переоткрытие ссылки гасит `AUTHORIZED` с живым холдом | `pbl` | **✅** | — | Сделано 17.08.2026. Гасим только `PENDING`, `AUTHORIZED` занимает слот |
| ~~**P1-7**~~ | Ошибка +1 при подсчёте успешных платежей (auto-flush) | `pbl` | **✅** | — | Сделано 17.08.2026. `+ 1` убран, подтверждено регрессионным тестом |
| ~~**P1-8a**~~ | Словарь статусов заказа выдуман, незнакомый статус проваливается молча | `pbl` | **✅** | — | Сделано 18.08.2026. Словарь из §5.8.8, `UNKNOWN`/`SETTLED_OTHER` не дают `FAILED`, у сверки появилась верхняя граница выборки. Замечание про пустой ответ закрыто |
| ~~**P1-8b**~~ | Успех денежной операции определяется по отсутствию `errorCode` | `pbl` | **✅** | ~~P1-8a~~ | Сделано 19.08.2026. Успех capture/возврата — только по `tran.match.ridByPmo` (иначе 502, Р-23), след `mpCapture`/`mpRefunds`, `refundId` из контракта, причина отказа `failureReason` (Р-24) |
| ~~**P1-16**~~ | `cardNumberMasked` / `rrn` / `approvalCode` в карточке транзакции всегда пустые | `pbl` | **✅** | — | Сделано 19.08.2026. `ProviderOrderDetails.read` (без Spring): маска — `order.srcToken.displayName`, `rrn`/`approvalCode` — с записи покупки в `order.trans[]` / `order.lastTran`; правило скаляра вынесено в `ProviderPayloads.scalarText`. Только бэкенд (Р-26), без колонок и миграции |
| ~~**P1-9**~~ | Ссылки создаются без TTL | `pbl` | **✅** | — | Сделано 18.08.2026. Срок выставляется всегда (24 ч по умолчанию), потолок 90 дней считается от `created_at`, `expiresAt` появился в запросе создания и в полном ответе |
| ~~**P1-10**~~ | `base-url` и адреса провайдера захардкожены, `api-base-url` на HTTP | `pbl` | **✅** | — | Сделано 18.08.2026. Три адреса — из окружения без дефолтов, HTTP у эквайера даёт WARN и не блокирует старт |
| ~~**P1-11**~~ | Пароль БД в открытом виде | все | **✅** | — | Сделано 17.08.2026 в составе P0-5: `DB_PASSWORD` без дефолта |
| ~~P1-12~~ | Нет refresh-токена, logout и отзыва | `auth` | **✅** | — | Бэкенд готов; TTL access-токена сократится с фронтендом |
| ~~P1-13~~ | Фронт: роль по умолчанию `SYSTEM_ADMIN`, нет ролевых guard'ов | `frontend` | **✅** | — | Сделано 18.08.2026. Access-токен только в памяти, refresh в `localStorage`, single-flight обновление по 401, восстановление сессии при загрузке, `parseRole` fail-closed, `RoleRoute` + раскладка в `auth/routeAccess.ts`, 404/`errorElement`; TTL access-токена — 15 минут |
| ~~P1-14~~ | Фронт не собирается для прода, TypeScript не установлен | `frontend` | **✅** | — | Сделано 18.08.2026. TypeScript 7 + `tsc -b` перед `vite build`, `VITE_API_BASE_URL`, ≈7 600 строк мёртвого кода и 48 зависимостей удалены |
| **P1-15** | `COMPANY_EMPLOYEE` может удалять терминалы своей компании | `directory` | **✅** | — | Сделано 17.08.2026 вместе с P0-3. `TERMINAL_WRITE_ROLES` = `SYSTEM_ADMIN`/`COMPANY_HEAD`/`COMPANY_MANAGER` |

### Спринт 2 — техдолг

P2-1 … P2-12 и P3 — см. `code_review.md`, §4 и §5. Разбираем после спринтов 0-1.

**Кросс-задача:** ввести `enum Role` в `common` и убрать строковые литералы (P2-7).
Разблокирует P0-4 и P1-15, делает невозможным класс ошибок «опечатка в роли».
Решено делать не первым шагом, а когда дойдём до P0-4.

---

## Журнал

### 15.08.2026 — P0-1 ✅ принято

Решения: Р-1 (аудитор — глобальный читатель) и Р-2 (в задачу входит фикс N+1).

Сделано: `TransactionRepository` +2 метода с `@EntityGraph(attributePaths = "link")`;
`PaymentLinkService.listTransactions` фильтрует по терминалам компании; введены
`READ_ROLES` и `isGlobalReader`; `validateAccess` и `list` переведены на них.
Тесты: 7 новых в `PaymentLinkIntegrationTest`, прогон всех модулей 30/30 зелёный.
Права на запись не расширились — `AUDITOR` не входит в `allowedRoles` у `create`,
`update`, `completeDms`, `refund`, до нового `return` не доходит (проверено по коду).

Сверх ТЗ исполнитель обновил `application_description.md`, `pbl/pay-by-link.md`,
`pbl/.agents/workflows/pbl.md` — это требование `.agents/AGENTS.md`, содержание корректное.
Правки приняты; на будущее в ТЗ не писать «диффом затронуты только эти файлы».

**Остаточные хвосты (не блокеры, вынести в отдельные задачи):**
- фикс N+1 не покрыт тестом на количество запросов — только структурно (`@EntityGraph`)
- новая семантика `AUDITOR` для `GET /payment-links` и `/payment-links/{id}` тестом не закреплена
- `size` в `PageRequest.of(page, size)` по-прежнему не ограничен сверху

**Сделано** (тот же день, ожидает проверки):

- `TransactionRepository`: `findAllBy(Pageable)` и `findByLink_TerminalIdIn(Collection<Integer>, Pageable)`,
  оба с `@EntityGraph(attributePaths = "link")` — попутно закрывает P2-4.
- `PaymentLinkService`: константа `READ_ROLES` и хелпер `isGlobalReader(role)`;
  `listTransactions` переписан по образцу `list()` (403 для неизвестной роли, пустая страница
  при отсутствии `companyId`/терминалов, фильтр по терминалам компании);
  `validateAccess` и `list()` переведены на `isGlobalReader` — это и есть реализация Р-1.
- `PaymentLinkIntegrationTest`: 7 тестов `listTransactions_*`, второй терминал для `other-company`,
  токены `globalAuditorToken` (AUDITOR без компании), COMPANY_EMPLOYEE без компании и роль `HACKER`.
  Фикстуры создаются напрямую через репозитории, без стаб-провайдера.
- `./gradlew test` — 30 методов, 0 падений (было 23).
- Проверено, что права на запись не расширились: `AUDITOR` не входит в `allowedRoles`
  у `create`, `update`, `completeDms`, `refund` и отсекается до нового `return` в `validateAccess`;
  `createPaymentLink_asAuditor_returns403` проходит.
- Документация обновлена: `AGENTS.md` (§2, §6, §10, §11, §12), `.agents/workflows/mp.md`,
  `.agents/workflows/frontend.md`, `pbl/.agents/workflows/pbl.md`, `pbl/pay-by-link.md`,
  `application_description.md`, `code_review.md` (пометки «исправлено» у P0-1 и P2-4).

Не входило в задачу и осталось: серверные фильтры листинга (терминал, статус, даты),
`GET /api/v1/transactions/{id}`, запрос листинга в Postman-коллекции `Pay-By-Link`
(его там нет и не было).

### 15.08.2026 — P0-2 ✅ принято

Разобрались, почему эндпоинт публичный: `redirect.html:380` опрашивает его из браузера
до 15 раз, поэтому авторизацию и сняли. Решения Р-3…Р-6.

Побочная находка: `OpenLinkController.redirectPage` берёт идентификатор из query-параметра
`ID`, который контролирует кто угодно, хотя в пути уже лежит неугадываемый `merchantRid`.
Это закрывает P1-4 в рамках той же задачи.

Точки касания: `JwtAuthFilter:131`, `TransactionController:44`, `PaymentLinkService:362`,
`OpenLinkController:58-80`, `redirect.html:372-472`, `TransactionRepository`,
падающий тест `PaymentLinkIntegrationTest:323`, `Pay-By-Link.postman_collection.json:266`.
Фронтенд правки не требует — интерсептор уже шлёт токен.

**Сделано** (тот же день, ожидает проверки):

- `JwtAuthFilter`: убран whitelist-блок для `*/status`. Публичных групп осталось ровно три:
  `/api/v1/auth/`, `*/open`, `/api/v1/payment-links/redirect`.
- `PaymentLinkService`: публичный `checkAndStatusUpdate(String)` удалён. Вместо него
  `checkAndStatusUpdate(String, UserPrincipal)` (мерчант, `validateAccess` по `READ_ROLES`)
  и `refreshByMerchantRid(UUID)` (страница плательщика). Общая логика опроса провайдера —
  в приватном `refreshStatus(Transaction)`, поведение не менялось, включая ранний выход
  на терминальном статусе. Поиск по UUID/`providerOrderId` вынесен в `resolveTransaction`.
- Новый DTO `PaymentReceiptView` (9 полей) — только то, что плательщик и так знает.
  `clientIp`, `userAgent`, `providerOrderId`, маскированная карта, RRN, approval code
  на публичную страницу не уходят.
- `TransactionRepository.findByMerchantRid(UUID)` с `@EntityGraph(attributePaths = "link")`.
- `OpenLinkController.redirectPage(String tx, Model)`: параметры `ID`/`PASSWORD`/`STATUS`
  не объявлены вообще, строка лога с `PASSWORD` удалена. Кривой `tx` и неизвестный
  `merchantRid` дают 200 и нейтральную страницу, а не 404/500.
- `redirect.html`: блок `<script>` (101 строка) удалён целиком, разметка на `th:if`/`th:text`,
  четыре состояния (`PAID`/`AUTHORIZED` → чек, `FAILED`, `PENDING`, `receipt == null`).
  Все значения через `th:text` (экранирование проверено на `<script>` в описании).
  `alert()` из «Close Page» убран — кнопка вызывает `window.close()` инлайном.
- Postman: у запроса «Check Transaction Status» снят override `auth: noauth`
  и добавлен явный заголовок `Authorization: Bearer {{apiToken}}` (в коллекции нет
  переменной `token`, только `apiToken`).
- Тесты: существующий `smsPayment_statusCheckMoves…` теперь ходит с `headToken`;
  добавлено 9 новых (`checkStatus_*` ×4, `redirectPage_*` ×5).
  `./gradlew test` — 39 методов, 0 падений (было 30).
- `AcquiringClient` в тесте обёрнут в `@SpyBean`: настоящий стаб по умолчанию,
  а тесту про «в обработке» нужен нефинальный ответ провайдера (`Preparing`).
- Документация обновлена: `AGENTS.md` (шапка, §2, §6, §7, §10, §11), `.agents/workflows/mp.md`,
  `.agents/workflows/frontend.md`, `pbl/.agents/workflows/pbl.md`, `pbl/pay-by-link.md`,
  `application_description.md`.

**Отступление от ТЗ, требует решения:** `refreshByMerchantRid` глушит ошибку провайдера
(`try/catch` вокруг `refreshStatus`) и рендерит последнее известное состояние. В ТЗ этого нет,
но старый контроллер так и делал — иначе недоступный TXPG даёт плательщику 500.

**Хвосты:** страница возврата не подписана и не защищена от повторного открытия чужим лицом,
знающим `merchantRid` (ссылка одноразовая по смыслу, но не по коду); зависшие `PENDING`
по-прежнему никто не дожимает — P1-3.

**Результат P0-2.** Whitelist в `JwtAuthFilter` сокращён до трёх групп путей;
`checkAndStatusUpdate` разделён на мерчантский (с `principal` и проверкой компании)
и публичный `refreshByMerchantRid`, общее ядро — приватный `refreshStatus`;
добавлен `PaymentReceiptView`; `redirect.html` рендерится Thymeleaf'ом без JS.
Тесты 39/39 зелёные, 9 новых. P1-4 (доверие query-параметру `ID`) закрыт заодно.

Сверх ТЗ: `refreshByMerchantRid` ловит `RuntimeException` от эквайера и рендерит
последнее известное состояние — страница плательщика не падает, если TXPG недоступен.
Хорошее дополнение, принято.

**Хвост:** `resolveTransaction` различает 404 (нет такой) и 403 (есть, но чужая) —
теоретическая возможность перебором узнать существование `providerOrderId`.
Риск низкий (нужен валидный JWT), но при чистке RBAC стоит унифицировать на 404.

### 16.08.2026 — P1-3

Проверил Postman-коллекцию TXPG: задокументированы только `POST /order`,
`GET /order/{id}` и `POST /order/{id}/exec-tran`. Асинхронных уведомлений от MilliKart нет,
единственный `callback` в коллекции — это чужой `hppRedirectUrl` в примере ответа.
Значит `PaymentCallbackRequest` и `callback-secret` — заготовки под несуществующий контракт
(Р-7): делаем только сверку опросом, заготовки удаляем.

Р-8: зависшие помечаем `FAILED`, но с защитой от ложных срабатываний при недоступности шлюза.

**Результат P1-3.** Заготовки удалены (`PaymentCallbackRequest`, `pbl.provider.callback-secret`).
Добавлены `TransactionReconciliationService` (логика, тестируется без крона) и
`TransactionReconciliationScheduler` (только вызов, `@ConditionalOnProperty` на
`pbl.reconciliation.enabled`, cron `0 */2 * * * *`). Точка входа на одну запись —
`PaymentLinkService.reconcileOne(id, maxAge)` с `REQUIRES_NEW`, переиспользует приватный
`refreshStatus` без изменений. Из `PaymentLinkScheduler` убрана мёртвая инжекция
`TransactionRepository`.

Разведение случаев — три раздельных выхода из `reconcileOne`, а не один `catch`:
опрос бросил → WARN и выход (статус не трогаем); опрос дал финальный статус → его и применили;
опрос дал нефинальный и возраст > `max-age` → `FAILED` + `reconciliationOutcome` /
`reconciledAt` в `providerResponse` (мержим, не затираем). Ссылка не трогается.

Тесты 47/47 зелёные, 8 новых (`TransactionReconciliationIntegrationTest`, `@MockBean`
на `AcquiringClient`). Мутационная проверка главного теста: убрал `return` из `catch` —
`reconcile_pendingOlderThanMaxAge_providerUnreachable_staysPending` падает
(`expected: <PENDING> but was: <FAILED>`), остальные семь остаются зелёными.

**Хвосты:** `min-age` не защищает от гонки со страницей возврата, если плательщик открыл её
позже 2 минут — два параллельных `refreshStatus` по одной транзакции возможны (последствия
безобидные: одинаковый маппинг, оптимистичной блокировки на `Transaction` нет).
Прогон не координируется между инстансами `pbl` — при горизонтальном масштабировании
сверка пойдёт с каждого узла; нужен shedlock или подобное.
Ветка «шлюз лежит дольше `max-age`» оставляет записи `PENDING` навсегда — это осознанно,
но метрики/алерта на размер очереди нет.

**Результат P1-3.** `TransactionReconciliationService` + `TransactionReconciliationScheduler`
(cron каждые 2 мин, `min-age` 2 мин, `max-age` 24 ч, батч 50, всё через env).
`PaymentLinkService.reconcileOne` в `REQUIRES_NEW`, переиспользует `refreshStatus` из P0-2.
Заготовки `PaymentCallbackRequest` и `callback-secret` удалены.
Тесты 47/47 зелёные, 8 новых в отдельном классе с `@MockBean AcquiringClient`.

Защита из Р-8 реализована корректно: `refreshStatus` обёрнут в try/catch, при исключении
метод выходит **до** любых изменений, проверка возраста стоит после успешного опроса.
Тест `reconcile_pendingOlderThanMaxAge_providerUnreachable_staysPending` проверяет и статус,
и отсутствие маркера в `providerResponse`.

**Хвосты:**
- `AUTHORIZED` сознательно вне сверки (протухание холдов — P1-6)
- в тесте `MIN_AGE`/`MAX_AGE`/`BATCH_SIZE` продублированы константами рядом с `@SpringBootTest(properties=…)`;
  при правке одного места легко забыть второе

### 16.08.2026 — enum Role (перед P0-3)

Р-9: кэш в `directory` убираем целиком, а не чиним. Р-10: `enum Role` выносим отдельной
задачей, чтобы рефакторинг не смешивался с правками безопасности в одном диффе.

Enum вскрывает P0-4 автоматически: список `List.of("SYSTEM_ADMIN", "MERCHANT_ADMIN",
"MERCHANT_USER")` на типизированном `EnumSet` просто не пишется. Поэтому P0-4 закрывается
здесь же — это единственное разрешённое изменение поведения в задаче.

**Ключевой риск задачи:** роль в БД и в JWT — свободная строка (`varchar(50)`).
`Role.valueOf()` на неизвестном значении бросит исключение и превратит нынешний 403 в 500.
Парсинг обязан быть нейтрализующим: неизвестное значение → нет роли → отказ.
И только точное совпадение регистра — иначе строка `system_admin` в БД внезапно станет админом.

**Отложено на потом:** валидация роли в `CreateUserRequest`/`UpdateUserRequest`
(сейчас `@NotBlank String`, можно завести пользователя с любой строкой). После enum такой
пользователь безопасен — ему везде отказывают, — но мусор в БД продолжит копиться.

**Результат R-Role + P0-4.** `common/.../security/Role.java`: пять значений и
`fromValue(String) → Optional<Role>` через неизменяемую `Map<String, Role>` — не бросает никогда,
сравнение точное (ни `equalsIgnoreCase`, ни `trim`). `UserPrincipal` хранит оба представления:
`getRawRole()` (сырая строка, для логов и текстов ошибок) и `getRole()` → `Role`, nullable.
`getAuthorities()` не тронут — по-прежнему `ROLE_` + сырая строка.

Переведены все шесть файлов: `UserService`, `JwtAuthFilter`, `CompanyService`, `TerminalService`,
`AuditLogService`, `PaymentLinkService`. Наборы — `EnumSet`: `READ_ROLES` (`allOf`),
`LINK_WRITE_ROLES`, `REFUND_ROLES`.

`PaymentLinkService.validateAccess` теперь принимает `UserPrincipal`, а не пару
role/companyId: это единственная точка, где роль сравнивается с набором, и через статические
хелперы она null-безопасна для всех восьми вызовов сразу. Заодно ушёл прямой
`principal.getRole()` из `getTransactionsByLinkId` (NPE при `principal == null`).
Тексты ошибок и логов не изменились — в них подставляется `rawRole`, поэтому в логе
по-прежнему видно, что реально пришло в токене (`role HACKER`, а не `role null`).

**P0-4** закрыт здесь же: `READ_ROLES` вместо `List.of("SYSTEM_ADMIN", "MERCHANT_ADMIN",
"MERCHANT_USER")`. Права совпали с `get(UUID, principal)` для самой ссылки.

Тесты: 47/47 существующих прошли **без единой правки** (единственное изменение в
`PaymentLinkIntegrationTest` — четыре дописанных теста `getLinkTransactions_*`).
Новое: `RoleTest` в `common` (каталога тестов там не было, создан `src/test/java`) — 7 методов,
25 запусков с раскрытием `@ParameterizedTest`. Итого 58 методов / 76 запусков.

Проверки приёмки: grep по строковым литералам ролей в main-коде backend не находит ничего;
`Role.valueOf(` нет нигде.

**Хвосты:**
- поведение сохранено буквально, включая P1-15: в `TerminalService.validateWriteAccessToCompany`
  пользователь с нераспознанной ролью и совпавшим `companyId` по-прежнему проходит. То же в
  `CompanyService.validateAccess` и `TerminalService.validateReadAccessToCompany` на чтение.
  Enum это делает *видимым*, но не чинит — по условию задачи
- фронтенд (8 файлов) и миграция `002-user-directory-schema.xml` живут на своих строках
- `READ_ROLES` = `EnumSet.allOf(Role.class)`: если появится шестая роль без права чтения,
  её надо будет вычесть явно, иначе она молча получит доступ
- **P0-3 разблокирован**

**Результат enum Role + P0-4.** `Role` в `common.security` с `fromValue`, который не бросает
и сравнивает строго по регистру (через неизменяемую `Map`, без `valueOf`). Наборы ролей —
`EnumSet`. Строковых литералов ролей в прод-коде не осталось. `validateAccess` в `pbl`
упрощён: принимает `UserPrincipal` целиком, поэтому NPE при `principal == null` больше
невозможен. `UserPrincipal` хранит и сырую строку (для логов), и разобранную роль.

**Рефакторинг доказуемо нейтрален:** `AuthIntegrationTest` и `DirectoryIntegrationTest`
имеют нулевой diff, в `PaymentLinkIntegrationTest` нет ни одного удаления из этой задачи.
Тесты 76/76 зелёные (`RoleTest` — 25 параметризованных кейсов).

P0-4 закрыт: `getTransactionsByLinkId` теперь на `READ_ROLES`, добавлены 4 интеграционных теста.

**Хвост:** `JwtAuthFilter:98` — токен без claim'а `role` по-прежнему получает
`COMPANY_EMPLOYEE` по умолчанию. Поведение сохранено намеренно (рефакторинг), но теперь,
когда роль типизирована, логичнее «нет claim'а → нет роли → отказ». Не эскалация
(подписать токен без ключа нельзя), но стоит поправить при чистке `auth`.

### 16.08.2026 — P0-3 + P1-15 (одной задачей)

Обе правки в `directory`, обе стали однострочными после enum.

Решено попутно: `CacheConfig` в `common` не удаляем — после снятия аннотаций он остаётся
неиспользуемым, но инфраструктура пригодится в `pbl`, где `validateAccess` дёргает
`terminalRepository.findById` в каждой операции. Добавляем комментарий, чтобы никто
не принял кэширование за работающее.

Роли на запись терминалов: `SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER` —
по `.agents/workflows/mp.md` («COMPANY_MANAGER: управление терминалами своей компании»).
`COMPANY_EMPLOYEE` теряет доступ на запись, что и требовалось.

Регрессионный тест на кэш строится так: запросить терминал под comp-01 (в старом коде
это клало ответ в кэш), затем тот же id под comp-02 — раньше приходило 200 с чужими
данными, теперь обязано быть 403. Такой тест краснеет на старом коде.

**Результат P0-3 + P1-15 (17.08.2026).**

Кэш снят целиком: `@Cacheable` с `TerminalService.getTerminal` и `CompanyService.getCompany`,
`@CacheEvict` с `updateTerminal`/`deleteTerminal`/`updateCompany`/`deleteCompany`, импорты удалены.
`grep -rn "@Cacheable\|@CacheEvict" --include=*.java directory` не находит ничего.
`CacheConfig` в `common` на месте; в его javadoc записано, что потребителей у кэшей сейчас нет,
инфраструктура ждёт `pbl` (`PaymentLinkService.validateAccess` → `terminalRepository.findById`
на каждой операции), и что вешать `@Cacheable` на метод с проверкой доступа внутри нельзя —
именно на этом стояла дыра.

`validateWriteAccessToCompany`: роль проверяется до `companyId` по
`TERMINAL_WRITE_ROLES = EnumSet.of(SYSTEM_ADMIN, COMPANY_HEAD, COMPANY_MANAGER)`.
Отдельная ветка для `AUDITOR` сохранена ради текста ошибки («AUDITOR is read-only») —
она информативнее общего «Access denied». `COMPANY_EMPLOYEE` и нераспознанная роль (`getRole()` → `null`)
получают отказ; совпадение `companyId` само по себе прав на запись больше не даёт.
`validateReadAccessToCompany` не тронут: сотрудник свои терминалы видит.

Тесты: `DirectoryIntegrationTest` вырос с 2 методов до 12, оба существующих —
`testCompanyLifecycleAndAudit` и `testTerminalLifecycleAndRBAC` — не правились
(в них пишет `COMPANY_HEAD`, а он остался в разрешённых). Добавлены токены
`managerTokenCompany1`, `employeeTokenCompany1`, `auditorToken` (`companyId = null`)
и `unknownRoleToken` (роль `HACKER`).

**Регрессия подтверждена:** с временно возвращённым `@Cacheable` на `getTerminal`/`getCompany`
оба теста краснеют с `Status expected:<403> but was:<200>` — первый (легитимный) запрос кладёт
ответ в кэш, второй запрос чужой компании получает его мимо проверки. Остальные 10 тестов при
этом зелёные, то есть на регрессию ловят ровно эти два.

`./gradlew test` зелёный целиком: 68 методов / 86 запусков (auth 6, common 25, directory 13, pbl 42).

Документация: `AGENTS.md` (§3 — кэш неактивен, §6 — матрица прав и абзац про `TERMINAL_WRITE_ROLES`,
§10 — оба пункта переехали в «закрытые блокеры», §11 — счётчики и устройство тестов),
`.agents/workflows/mp.md` (§2.2, §4), `directory/directory.md` (§3.2), `application_description.md`
(§2 стек, §4.1, §6.1-6.3).

**Хвосты:**
- `CompanyService.validateAccess` и `TerminalService.validateReadAccessToCompany` на чтение
  по-прежнему пропускают нераспознанную роль при совпавшем `companyId`. Это не эскалация
  (токен без ключа не подписать, а `companyId` свой), но асимметрия с записью налицо
- ~~`DELETE /terminals/{id}` при живом FK `fk_payment_links_terminal` даёт 500~~ — закрыто 21.08.2026 (P2-8): удаления терминалов нет, есть блокировка
- ~~листинги без пагинации~~ — закрыто 22.08.2026 (P2-1): `listUsers`, `listCompanies`
  и `listTerminals` принимают `Pageable` и отдают `PagedResponse`; непагинированных листингов
  в проекте не осталось

**Результат P0-3 + P1-15.** Аннотаций кэша в `directory` не осталось; `CacheConfig` сохранён
с развёрнутым предупреждением, почему нельзя вешать `@Cacheable` поверх проверок прав.
`validateWriteAccessToCompany` получил `TERMINAL_WRITE_ROLES` (`SYSTEM_ADMIN`, `COMPANY_HEAD`,
`COMPANY_MANAGER`), роль проверяется до `companyId`. Тесты 86/86 зелёные, в `directory`
было 2 теста — стало 12, старые не правились. Документация обновлена в 7 файлах.

Регрессионный тест `getTerminal_afterAnotherCompanyFetchedIt_stillReturns403` устроен верно:
сначала легитимное чтение (в старом коде оно грело кэш), затем тот же id чужой компанией.
С возвращённым `@Cacheable` шаг 3 даёт 200 вместо 403.

**Осталось блокеров: четыре** — P0-5, P0-6 (секреты), P0-7, P0-8 (деньги). *(P0-7 и запрет
повторного capture из P0-8 закрыты ниже, в записи от 17.08.2026.)*

### 17.08.2026 — P0-7 + часть P0-8

В коллекции TXPG есть только `phase: "Single"` (reversal, refund), примера `Clearing` нет
вовсе. Значит неизвестно, принимает ли клиринг `amount`: добавить поле вслепую в платёжный
путь нельзя — при неудаче ломается весь capture. Отсюда Р-11.

Ключевая мысль задачи: снять `@Retry` недостаточно. Одиночный запрос тоже может уйти
в таймаут уже после исполнения на стороне эквайера. Сейчас любая ошибка = откат транзакции,
и мерчант повторяет возврат руками — получается двойной возврат без всякого retry.
Поэтому вводим разделение «шлюз отказал» (определённый ответ) и «исход неизвестен»
(таймаут, обрыв, 5xx) с отдельным исключением и внятным сообщением.

**Результат P0-7 + часть P0-8.** Сделано:

- `common/.../exception/PaymentOutcomeUnknownException.java` — новое исключение, **не** наследник
  `BusinessException`: 400 читается мерчантом как «повторяй», а это ровно то, чего нельзя.
  `GlobalExceptionHandler` отдаёт по нему **502** с текстом «проверьте статус транзакции перед
  повторной попыткой» и пишет ERROR с маркером `PAYMENT_OUTCOME_UNKNOWN` для мониторинга.
- `TxpgAcquiringClient`: `@Retry` снят с `completeDms` и `refund`, оставлен на `createEcomOrder`
  и `getOrderStatus`; `@CircuitBreaker` не тронут — он не переотправляет запрос. Классификация
  вынесена в `classifyMoneyOperationFailure`: `errorCode` в 200 и 4xx → `BusinessException`;
  5xx, `ResourceAccessException` и всё остальное → `PaymentOutcomeUnknownException`.
  Дефолт — «неизвестно»: принять отказ за неизвестность стоит одной ручной проверки статуса,
  наоборот — денег держателя карты. `getOrderStatus` и `createEcomOrder` не менялись.
- `PaymentLinkService.completeDms`: `SUCCESS` → отказ `Transaction has already been captured`
  (это и был прямой путь к двойному списанию); `AUTHORIZED` → как раньше; `PENDING` → сначала
  `refreshStatus`, затем решение по свежему ответу. `PENDING` намеренно оставлен в допустимых:
  после P0-2 страница плательщика опрашивает эквайер один раз, и холд, поставленный после этого,
  у нас ещё числится `PENDING`. `PaymentOutcomeUnknownException` из `refreshStatus` не ловится.
- Сумма Clearing: тело запроса не тронуто, валидации нет (Р-11), но расхождение
  `request.amount()` и `transaction.getAmount()` пишется WARN со ссылкой на открытый вопрос.
- `refund`: фабрикация `refundId` убрана — нет поля в ответе провайдера, значит `null` и WARN.

Тесты: 86 методов / 104 запуска, зелёные; существующие не правились. Два новых класса —
`MoneyOperationsIntegrationTest` (10, `@MockBean AcquiringClient`, отдельно от
`PaymentLinkIntegrationTest`, чтобы не ломать его `@SpyBean`-стаб) и `TxpgAcquiringClientTest`
(8, юнит на `MockRestServiceServer` — единственное место, где классификация проверяется
на живом клиенте, а не через мок).

Регрессия проверена откатом, по одному изменению за раз:

| Что временно вернули | Что покраснело |
|:---|:---|
| `SUCCESS` в списке допустимых статусов capture | только `completeDms_alreadyCaptured_returns400` |
| 502 → 400 для `PaymentOutcomeUnknownException` | 4 теста, включая `refund_providerTimeout_returns502AndKeepsRefundedAmount` |
| единый `BusinessException` в клиенте | 4 теста `*_leavesTheOutcomeUnknown`, при этом `*_isAPlainRefusal` остались зелёными |

Что осталось: идемпотентных ключей и таблицы `refunds` нет (Р-12) — 502 перекладывает сверку
на человека, а не решает её; сумма Clearing не передаётся (Р-11, ждём MilliKart).

**Осталось блокеров: три** — P0-5, P0-6 (секреты), P0-9 (пароли в логах); P0-8 открыт частично.

**Результат P0-7 + части P0-8.** `@Retry` снят с `completeDms` и `refund`, оставлен на
`createEcomOrder` и `getOrderStatus` (с обоснованием в комментариях). Введён
`PaymentOutcomeUnknownException` → HTTP 502 с маркером для мониторинга. Классификация
ошибок вынесена в `classifyMoneyOperationFailure`: 4xx и `errorCode` в 200 — отказ (400),
5xx / таймаут / прочее — неизвестный исход (502). Повторный capture транзакции в `SUCCESS`
запрещён; `PENDING` перед отказом опрашивает эквайер. Выдуманный `refundId` убран.

Тесты 104/104 зелёные: новые классы `MoneyOperationsIntegrationTest` (10) и
`TxpgAcquiringClientTest` (8). Существующие не правились.

Сверх ТЗ исполнитель закрыл случай, который я не описал: если после опроса эквайера
`PENDING`-транзакция оказалась уже `SUCCESS`, capture отклоняется, а не выполняется поверх.

**Открытый вопрос к MilliKart (блокирует остаток P0-8):** принимает ли `phase: "Clearing"`
поле `amount`. До ответа частичный capture тихо клирит полную сумму, в лог пишется WARN.

**Осталось блокеров: два** — P0-5 и P0-6, оба про секреты.

### 17.08.2026 — P0-5 + P0-6 + P1-11 (одной задачей)

Ручной старт (Р-13) означает, что защита строится не на оркестраторе, а на самом
приложении: секреты приходят из окружения **без дефолтов**, и сервис обязан падать
на старте, если их нет. Дефолт — ровно то, из-за чего ключ и оказался в репозитории.

Отдельно: `JwtProvider` должен отказываться стартовать на скомпрометированном ключе.
Его SHA-256: `d29195138e093fd14e29672b4e8dce022359291148322fa3aa93c48a08ed5409`
(длина 68 байт). Сравнение по хешу, чтобы литерал не вернулся в исходники.

Важная деталь: тестовые `application.yaml` сейчас используют тот же самый ключ —
их надо перевести на отдельное тестовое значение, иначе проверка уронит все тесты.

Найдено попутно: `pbl.security.api-token` имеет дефолт `pbl-secret-token` — это
статический бэкдор с ролью `SYSTEM_ADMIN`. В проде выключен флагом, но в тестовых
конфигах `auth` и `directory` включён, при этом не используется ни одним тестом
(`AuthIntegrationTest:49` объявляет `fallbackToken` и не обращается к нему).

**Результат P0-5 + P0-6 + P1-11.**

*Секреты.* `JwtProvider` больше не имеет дефолта и отказывается стартовать на пустом ключе,
на ключе короче 32 байт и на скомпрометированном (сверка по SHA-256, литерала в исходниках нет).
`DB_PASSWORD`, `JWT_SECRET` и `PBL_API_TOKEN` — без значений по умолчанию; `DB_URL` и
`DB_USERNAME` дефолты сохранили, они не секреты. Историю git не трогали.

*Админ.* `<insert tableName="users">` удалён из `002-user-directory-schema.xml` (правка
применённого changeset'а — согласованное исключение, боевых установок нет). Вместо него
`auth/.../bootstrap/AdminBootstrapRunner.java`: только при `AUTH_BOOTSTRAP_ENABLED=true`,
только на пустой таблице `users`, пароль через тот же `PasswordConstraintValidator`,
факт создания — в WARN без пароля.

*Тесты* 120/120 зелёные (было 104). Новые: `JwtProviderTest` (7 методов / 9 запусков),
`AdminBootstrapRunnerTest` (5), `AdminBootstrapIntegrationTest` (2). Тестовые конфиги
переведены на `test-only-jwt-secret-not-used-anywhere-else-0123456789`, fallback-токен
выключен во всех трёх, неиспользуемое поле `fallbackToken` из `AuthIntegrationTest` удалено.
`AuthIntegrationTest` пришлось тронуть ещё в одном месте: его фикстура заводила админа
с паролем `admin123`, а критерий приёмки требует, чтобы этой строки не осталось в исходниках;
теперь фикстура хеширует пароль через `PasswordEncoder`, а не носит захардкоженный BCrypt.

**Найдено при проверке и закрыто сверх ТЗ.** `@ConfigurationProperties` **не считает ошибкой**
нерезолвнутый `${...}`: `spring.datasource.password: ${DB_PASSWORD}` без переменной уезжал
в драйвер как строка `${DB_PASSWORD}`, и сервис падал много позже — на «password authentication
failed», где о причине не сказано ничего. `@Value` (`JWT_SECRET`, `PBL_API_TOKEN`) на том же
месте бросает сразу, но его сообщение — диагноз, а не инструкция. Оба случая переводит
в инструкцию `common/.../security/MissingSecretFailureAnalyzer.java` (стандартный
`FailureAnalyzer` Spring Boot, зарегистрирован через `META-INF/spring.factories` в `common`).
Проверено живым запуском `:auth:bootRun` для обоих отсутствующих переменных и для
скомпрометированного ключа.

**Осталось блокеров: один** — P0-9 (пароли в логах); P0-8 открыт частично.

**Результат P0-5 + P0-6 + P1-11.** `JwtProvider` без дефолта, отказывается стартовать на
пустом, коротком (<32 байт) и скомпрометированном ключе (сравнение по SHA-256, литерал
в исходники не вернулся). `DB_PASSWORD` и `JWT_SECRET` без значений по умолчанию во всех
трёх сервисах; `DB_URL`/`DB_USERNAME` дефолты сохранены для локального запуска.
Сид админа удалён из миграции, заведён `AdminBootstrapRunner` — одноразовый, только на
пустой базе, пароль проверяется тем же `PasswordConstraintValidator`.

Бэкдор `pbl-secret-token` обезврежен: дефолта нет, а включённый флаг с пустым токеном
роняет старт с инструкцией. Тестовые конфиги переведены на отдельный ключ и `api-token-enabled: false`.

Тесты 120/120 зелёные: `JwtProviderTest` (9), `AdminBootstrapRunnerTest` (5),
`AdminBootstrapIntegrationTest` (2). Добавлены `.env.example` и раздел 20
«Первый запуск и ротация ключа» в `deployment_guide.md`.

**Все девять блокеров P0 закрыты.** Остаток P0-8 (передача `amount` в Clearing)
ждёт ответа MilliKart — см. Р-11.

**Хвост:** литерал старого ключа остался в `code_review.md` как цитата находки.
После ротации он бесполезен, но при передаче репозитория заказчику отчёт стоит
либо вычистить, либо приложить отдельно.

### 17.08.2026 — ответ MilliKart по Clearing

Подтвердили: `phase: "Clearing"` **принимает** `amount`. Остаток P0-8 разблокирован —
можно передавать сумму в теле и валидировать `amount <= transaction.amount`.
Задача небольшая (тело запроса + валидация + WARN убрать), делаем сразу после P1-1.

### 17.08.2026 — P1-1 (разбор)

Текущее состояние: `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())`.
Вся авторизация держится на `JwtAuthFilter.requiresAuthentication`, то есть на разборе
префикса пути. Всё, что не начинается с `/api/v1/`, открыто: `/actuator/health`
с `show-details: always`, `/actuator/metrics`, `/actuator/info`, `/swagger-ui.html`,
`/v3/api-docs` — во всех трёх сервисах.

**Главный риск задачи:** после правки список публичных путей окажется в двух местах —
в `JwtAuthFilter` и в `SecurityConfig`. Разъезд между ними и есть будущая дыра.
Список обязан жить в одном месте и использоваться обоими.


### 17.08.2026 — P1-1 (сделано)

Модель перевёрнута: `SecurityConfig` заканчивается `anyRequest().authenticated()`.

**Список публичных путей — в одном месте**, `common/.../security/PublicEndpoints.java`,
три группы: `PUBLIC_API` (публичны по бизнес-смыслу), `INFRASTRUCTURE` (`/actuator/**`,
защищён привязкой порта, не токеном) и `SWAGGER` (разрешается только при включённом флаге).
`JwtAuthFilter.requiresAuthentication` стал обёрткой над `PublicEndpoints.isPublic`;
разбор префикса `/api/v1/` — то, из-за чего всё остальное было публичным, — убран.

**Actuator** — на отдельном порту, привязанном к `127.0.0.1`: auth 9081, directory 9082,
pbl 9080. `show-details: always` оставлен: детали видны только с машины.
**Swagger** — за `SWAGGER_ENABLED`, по умолчанию выключен; матчеры в `SecurityConfig`
добавляются под тем же флагом.

**Отказы одного формата.** `SecurityErrorResponder` — сразу `AuthenticationEntryPoint`,
`AccessDeniedHandler` и писатель 401 для фильтра. Отказ аутентифицированному пользователю —
403, а не 401: 401 сказал бы «войди заново» там, где повторный вход не поможет, и разошёлся
бы с `InvalidStateException` → 403 во всём остальном проекте.

**Побочно закрыто:** `NoResourceFoundException` падал в `@ExceptionHandler(Exception.class)`,
поэтому любой несуществующий путь отвечал 500 «Unexpected server error» и писал ERROR в лог.
Без этой правки требуемые 404 (actuator и swagger на основном порту) были бы недостижимы.

**Про критерий «верни `permitAll()` и убедись, что тест краснеет».** В лоб он не выполняется,
и это следствие самого задания: раз оба слоя читают один список, каждый из них по отдельности
отклоняет неизвестный путь. Проверено тремя экспериментами:

| Что откатывали | Результат |
|:---|:---|
| только `SecurityConfig` → `anyRequest().permitAll()` | краснеет `SpringSecurityLayerIntegrationTest` (3 теста) |
| только `JwtAuthFilter` → прежний разбор префикса | краснеют `unknownPath_withToken_returns404`, `swaggerUi_withFlagOff_returns404`, `apiDocs_withFlagOff_returns404` |
| оба слоя (состояние до P1-1) | краснеет `unknownPath_withoutToken_returns401` во всех трёх модулях + `openWithExtraPathSegment_withoutToken_returns401` + `errorResponseShape_isConsistent` |

Чтобы регрессия именно в `SecurityConfig` ловилась отдельно, добавлен
`SpringSecurityLayerIntegrationTest`: он подменяет бин `jwtAuthFilter` сквозным (тем же именем,
`spring.main.allow-bean-definition-overriding=true`), и тогда отвечает только Spring Security.

**Ужесточение:** `/api/v1/payment-links/*/open` вместо «любой путь под `/api/v1/payment-links/`,
оканчивающийся на `/open`» — ровно один сегмент, как в маппинге. `/api/v1/payment-links/a/b/open`
без токена теперь 401.

Тесты 207/207 зелёные (было 120). Существующие тесты не правились ни одного.
Обновлены `.env.example`, `deployment_guide.md` (разделы 8.1, 8.3, 11, 14.1, новый 14.3, 17.2,
18, 19), `AGENTS.md` (§6, §10, §11, §12.4), `application_description.md`, `technical_handover.md`.

**Хвост:** `PaymentLinkMapper` и `OpenLinkService` строят публичные URL из своих литералов
(`/api/v1/payment-links/{id}/open`, `/api/v1/payment-links/redirect/{tx}`). Это не вторая копия
списка доступа, но при переименовании этих путей менять придётся оба места.

**Результат P1-1.** Модель по умолчанию перевёрнута: `anyRequest().authenticated()`.
Список публичных путей вынесен в `PublicEndpoints` и используется и `SecurityConfig`,
и `JwtAuthFilter` — разъезда двух списков больше не может быть по построению.
Actuator во всех трёх сервисах переехал на отдельный порт (9081/9082/9080) с привязкой
к `127.0.0.1`. Swagger под флагом `SWAGGER_ENABLED`, по умолчанию выключен.
Заведён `SecurityErrorResponder` — единый `ErrorResponse` и от фильтра, и от Spring Security.

Тесты 207/207 зелёные (было 120). Новые классы: `PublicEndpointsTest` (31),
`SecurityBoundaryIntegrationTest` в трёх модулях (10+10+14), `SwaggerEnabledIntegrationTest`
(4×3), `SpringSecurityLayerIntegrationTest` (4), `ManagementPortIntegrationTest` (6).

Сверх ТЗ: `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` — без этого
Spring Security блокирует отрисовку страницы ошибки. И `ManagementPortIntegrationTest`
поднимает реальный management-порт и проверяет, что health отвечает без токена там
и отсутствует на основном порту — это сильнее, чем ручная проверка из ТЗ.

### 17.08.2026 — остаток P0-8 (объём больше, чем казалось)

Оценка «полчаса» была неверной. Потолок возврата считается от `transaction.getAmount()`
(`PaymentLinkService:379` и `:405`) — от **авторизованной** суммы. Пока capture всегда полный,
авторизованная и захваченная суммы совпадают. Включение частичного capture их разводит:
авторизовали 1500, захватили 500 → мерчант сможет вернуть до 1500, из которых 1000
никогда не списывалась.

Поэтому передать `amount` эквайеру без учёта захваченной суммы нельзя — это открывает
дыру в возвратах одновременно с закрытием дыры в capture. Нужна колонка `captured_amount`
и миграция, потолок возврата считается от неё с откатом на `amount` (у SMS-платежей
capture не бывает, колонка остаётся пустой).

Попутно: `BigDecimal.toString()` в `refund` может дать научную нотацию (`1E+3`).
Формат суммы для провайдера — строка с двумя знаками через `toPlainString()`,
суммы с более чем двумя знаками после запятой отклоняем.

### 17.08.2026 — остаток P0-8 (сделано)

Тело Clearing теперь несёт сумму:

```
POST https://api.txpg.example.com/order/1234567/exec-tran?password=…
{"tran":{"phase":"Clearing","amount":"500.00"}}
```

**Что сделано.**

1. Миграция `pbl/.../004-add-captured-amount.xml` — `transactions.captured_amount numeric(19,2)`,
   nullable, подключена в `db.changelog-master.xml` после `003-…`.
   `Transaction.capturedAmount` — без `@Builder.Default`: `null` = «capture не выполнялся».
2. `TxpgAcquiringClient.completeDms` кладёт `amount`; `formatAmount` =
   `setScale(2, RoundingMode.UNNECESSARY).toPlainString()` используется и в `completeDms`,
   и в `refund` (там было `amount.toString()`).
3. `PaymentLinkService.completeDms`: WARN-заглушка про открытый вопрос убрана; вместо неё
   отказ на сумме выше авторизованной и на третьем знаке после запятой; после успешного
   ответа провайдера пишется `capturedAmount`, статус остаётся `SUCCESS`.
4. `PaymentLinkService.refund`: потолок и переход в `REFUNDED` считаются от
   `refundableBase(tx)` = `capturedAmount` ?: `amount`. Текст ошибки теперь про захваченную сумму.
5. `TransactionResponse.capturedAmount` — мерчанту видно, сколько реально списано.

**Сверх ТЗ, осознанно.** Проверка «не больше двух знаков» поставлена **и в `refund`**, не только
в capture. В ТЗ сказано, что до клиента исключение из `RoundingMode.UNNECESSARY` не дойдёт,
потому что сумму отсекает валидация в сервисе, — но валидация была описана только для `completeDms`,
а `refund` шлёт сумму через тот же `formatAmount`. Без этой проверки возврат на `500.005` дал бы
`ArithmeticException` **до** `try` в клиенте, то есть 500 вместо 400. Одна строка, поведение
остальных сумм не меняется.

Ещё два теста сверх восьми — в `TxpgAcquiringClientTest`: тело Clearing целиком
(`content().json(…, strict)`) и `1E+3` → `"1000.00"` в refund. Это единственное место, где
проверяется, что уходит на провод; сама задача просила показать тело запроса.

**Проверка обратной регрессии** (критерий приёмки). Потолок временно возвращён на
`transaction.getAmount()` — покраснели ровно два теста:
`refund_afterPartialCapture_cannotExceedCapturedAmount` (возврат 501 при захваченных 500:
`Status expected:<400> but was:<200>` — деньги, которых не списывали, уходят обратно) и
`refund_afterPartialCapture_fullCapturedAmount_marksRefunded` (`PARTIALLY_REFUNDED` вместо
`REFUNDED`). После восстановления — зелёные.

**Результат.** Тесты 217/217 зелёные (было 207): +8 в `MoneyOperationsIntegrationTest`
(10 → 18), +2 в `TxpgAcquiringClientTest` (8 → 10). Существующие тесты не правились;
`completeDmsAndRefundFlow` в `PaymentLinkIntegrationTest` (capture 1500.50, потом возврат 500)
зелёный без изменений. `grep -n "amount.toString()"` в `TxpgAcquiringClient` не находит ничего,
WARN-заглушки в `completeDms` нет.

Обновлены `AGENTS.md` (шапка, §7, §10, §11), `application_description.md`, `pbl/pay-by-link.md`,
`pbl/.agents/workflows/pbl.md`, `.agents/workflows/mp.md`, `pbl/Pay-By-Link.postman_collection.json`.

**Осталось блокеров: один** — P0-9 (пароли в логах). P0-8 закрыт полностью.

**Хвост:** таблицы `refunds` и идемпотентных ключей по-прежнему нет (Р-12), поэтому 502 на
возврате всё так же перекладывает сверку на человека. Неиспользованный остаток холда после
частичного capture мы провайдеру не отменяем: Void в системе не реализован вообще
(заявлен в `technical_handover.md`, кода нет), протухание холдов — отдельная задача P1-6.

**Результат остатка P0-8.** Миграция `004-add-captured-amount.xml`, поле `capturedAmount`
в `Transaction` и `TransactionResponse`. `formatAmount` с `toPlainString()` и
`RoundingMode.UNNECESSARY` применён и в `completeDms`, и в `refund` — научная нотация
из `BigDecimal.toString()` больше невозможна. Валидации: сумма capture не выше
авторизованной, не более двух знаков после запятой. Потолок возврата считается через
`refundableBase` — от захваченной суммы для DMS, от авторизованной для SMS.

Тесты 217/217 зелёные, в `MoneyOperationsIntegrationTest` стало 18. Существующий
`completeDmsAndRefundFlow` не правился. Тест
`refund_afterPartialCapture_cannotExceedCapturedAmount` проверяет и отказ, и что
провайдер не вызван, и что `refundedAmount` не изменился.

**✅ Все девять блокеров P0 закрыты полностью.**

Открытых вопросов к MilliKart не осталось.

### 17.08.2026 — P1-2 (разбор, найдена вторая проблема)

Известное: `2-auth-core` создаёт `companies`, `users` и FK **без preConditions**,
поэтому падает, если `directory` уже создал `companies`.

Найдено при разборе: `2-auth-terminal-fk` (строки 58-71) добавляет FK
`terminals.company_id → companies.id` с условием «обе таблицы существуют» и
`onFail="MARK_RAN"`. По рекомендованному порядку `auth` стартует первым, когда
`terminals` ещё не создан pbl'ем → условие не выполнено → changeset помечен выполненным →
**ключ не создаётся никогда**. Следование инструкции молча теряет внешний ключ.

Суть: `MARK_RAN` означает «считай сделанным», а для межсервисной зависимости нужно
«попробуй на следующем старте» — это `onFail="CONTINUE"`, он не пишет запись
в DATABASECHANGELOG.

Проверить заодно: `pbl/002-add-indexes.xml`, `003-...`, `004-...` тоже без preConditions.
Межсервисного конфликта там нет (таблицами владеет только pbl), но идемпотентности тоже нет.

**Результат P1-2 (17.08.2026).** Обе проблемы закрыты, порядок запуска сервисов больше
не имеет значения.

`002-user-directory-schema.xml`: `2-auth-core` разбит на три changeset'а по одному объекту —
`2-auth-companies`, `2-auth-users`, `2-auth-users-company-fk`, у каждого своё условие.
Условие на весь блок здесь ставить нельзя: с уже существующей `companies` и отсутствующей
`users` весь changeset ушёл бы в MARK_RAN, `users` не создалась бы, и `ddl-auto: validate`
уронил бы сервис. Комментарий про отсутствие сида админа (P0-6) сохранён.

`2-auth-terminal-fk`: `MARK_RAN` → `CONTINUE` плюс `<not><foreignKeyConstraintExists/></not>`.
В changeset'е стоит комментарий, объясняющий, почему именно CONTINUE — иначе следующий
«унифицирует» его обратно на MARK_RAN, и ключ снова потеряется. То же условие и та же
причина у `2-auth-users-company-fk`.

`pbl`: `002-add-indexes.xml` разбит на три changeset'а с `<not><indexExists/></not>`,
`003-add-client-ip-and-user-agent.xml` — на два с `<not><columnExists/></not>`,
у `004-add-captured-amount.xml` добавлено такое же условие. Межсервисного конфликта там нет,
это защита от базы, восстановленной без `DATABASECHANGELOG`.
`directory/003-directory-schema.xml`, `pbl/001-initial-schema.xml` и `2-auth-lockout-fields`
не трогали — условия там уже стояли.

`runOnChange` и `validCheckSum` не добавлялись сознательно: они маскируют расхождение.

Проверено на H2 (`MODE=PostgreSQL`), что `foreignKeyConstraintExists` и `indexExists`
отрабатывают в **обеих** ветках (объект есть / объекта нет) и не бросают
`PreconditionErrorException`; у `foreignKeyConstraintExists` указан `foreignKeyTableName` —
без него снапшот-генератору не по чему искать ключ. Оба предиката снапшотные, то есть
работают через JDBC-метаданные и не зависят от диалекта; на живом PostgreSQL не проверялось,
базы под рукой нет.

Новый `auth/src/test/java/az/millikart/auth/MigrationOrderTest.java` — 5 методов, без
Spring-контекста, Liquibase гоняется вручную на своей H2 на каждый метод. Именно этого
теста не хватало: в обычных тестах каждый модуль поднимает собственную базу
(`mem:auth`, `mem:directory`, `mem:pbl`), сервисы там никогда не встречаются, и конфликт
миграций не воспроизводится.

Проверено обратно: со старым changelog краснеют
`authMigrations_whenCompaniesAlreadyExists_succeed` («table COMPANIES already exists»),
`terminalFk_isCreatedOnALaterRun_whenTerminalsAppears` (ключ не появляется — MARK_RAN
записал changeset как выполненный) и `authMigrations_onDatabaseWithoutChangelogTable_skipEverything`;
`authMigrations_onEmptyDatabase_createUsersAndCompanies` и `authMigrations_runTwice_areIdempotent`
остаются зелёными — это базовые сценарии, они и должны работать на обоих вариантах.

Тесты 222/222 зелёные (было 217), существующие не правились.

Документация: `AGENTS.md` §4, §10 (новая запись в закрытых блокерах), §11 и §12.6;
`deployment_guide.md` §9.5 и §20.1 шаг 3; `application_description.md` §9.2 и §9.3;
`.env.example`; `.agents/workflows/mp.md` §5.

**Результат P1-2.** `2-auth-core` разбит на `2-auth-companies`, `2-auth-users` и
`2-auth-users-company-fk`, у каждого своё условие. `2-auth-terminal-fk` переведён
на `onFail="CONTINUE"` с комментарием, прямо запрещающим «унифицировать» его к `MARK_RAN`
и объясняющим, что MARK_RAN означал бы бесследную потерю внешнего ключа.
В `pbl` условия добавлены в 002, 003 и 004 — миграции стали идемпотентными.

Тесты 222/222 зелёные. `MigrationOrderTest` (5) прогоняет Liquibase вручную на отдельной
H2-базе: сценарий «companies уже создана directory», пустая база, появление `terminals`
на втором прогоне, двойной прогон, и база без `DATABASECHANGELOG`.

Порядок запуска сервисов больше не важен — требование убрано из `AGENTS.md`
и `deployment_guide.md`.

### 17.08.2026 — P1-5 + P1-6 + P1-7 связкой

Все три в `OpenLinkService.openAndBuildRedirect` и вокруг. Живых холдов нет
(система не в проде), поэтому разбираться с уже захолдированными деньгами не нужно —
меняем поведение только вперёд.

Косвенное подтверждение P1-7: в `completeDms` (`PaymentLinkService:346`) стоит такой же
подсчёт `countByLinkIdAndStatus(..., SUCCESS)` **без** `+ 1`, а в `refreshStatus` (`:627`)
с `+ 1`. Два соседних пути считают одно и то же по-разному — это опечатка, не замысел.

Решение по P1-6: `AUTHORIZED` не гасим никогда, и считаем его занимающим слот наравне
с `SUCCESS` при проверке лимита использований. Это закрывает и потерю холда,
и повторное открытие single-use ссылки с висящим холдом.

Известное следствие, выносим отдельной задачей: у `AcquiringClient` нет операции Void
(в коллекции TXPG она есть — `phase: "Single", voidKind: "Full"`), поэтому брошенный
холд заблокирует single-use ссылку до истечения срока на стороне банка.

**Результат P1-5.** `openAndBuildRedirect` — один `@Transactional`-метод, `TransactionTemplate`
из `OpenLinkService` убран вместе с четырьмя фазами. Порядок: `findWithLockById` (`SELECT … FOR
UPDATE`, hint `jakarta.persistence.lock.timeout = 10000`) → проверки → гашение прошлых `PENDING`
→ вызов провайдера → запись `Transaction`. Оба компромисса записаны javadoc'ом на методе:
блокировка строки держится на время HTTP-вызова (в худшем случае 13 с: 3 с соединение + 10 с
чтение), и брошенный у эквайера заказ при падении коммита.

Одной блокировки для теста «ровно одна запись» не хватило, и это оказалось важным: сериализация
сама по себе не запрещает второму вызову погасить только что созданный `PENDING` и завести
**второй живой заказ** у эквайера — а отменить первый мы не умеем. Поэтому у гашения появилось
условие: `PENDING`, созданный **после** начала текущего запроса (то есть пока он стоял в очереди
за блокировкой), — это второй одновременный клик, а не брошенная сессия; такой запрос получает
`ConflictException` (409, «A payment session for this link is already being opened»). Для
последовательного переоткрытия (`createdAt` раньше) поведение прежнее — гасим и выдаём новую
сессию, ровно как раньше.

**Результат P1-6.** В списке гашения остался только `PENDING`. Проверка лимита считает
`countByLinkIdAndStatusIn(id, EnumSet.of(SUCCESS, AUTHORIZED))` — холд занимает слот. Отказы
различимы: «Single-use payment link has already been used» против «Payment link has an authorized
payment awaiting capture». Известное ограничение (нет Void) записано в `AGENTS.md` §10.

**Результат P1-7.** `+ 1` убран, комментарий на его месте объясняет auto-flush и ссылается на
`completeDms:346` как на образец того же подсчёта.

Тесты 230/230 зелёные (было 222; +2 в новом `OpenLinkConcurrencyTest`, +6 в
`PaymentLinkIntegrationTest`), существующие тесты не правились. Проверено обратно, возвратом
старого поведения по одному дефекту за раз:

- старый четырёхфазный `openAndBuildRedirect` → краснеют оба теста `concurrentOpens_*`
  (оба потока обслужены, два разных `providerOrderId`, две записи);
- `AUTHORIZED` обратно в список гашения и подсчёт только по `SUCCESS` → краснеют
  `reopen_withAuthorizedTransaction_doesNotMarkItFailed` (`expected: <AUTHORIZED> but was: <FAILED>`),
  `reopen_singleUseLink_withAuthorizedTransaction_isRefused` и `multiUseLink_authorizedCountsTowardsLimit`;
- `+ 1` обратно → краснеет `refreshStatus_onMultiUseLink_doesNotCompleteAfterFirstPayment`
  (`expected: <ACTIVE> but was: <COMPLETED>`).

Документация: `AGENTS.md` §7, §10 (новая запись в закрытых блокерах + известное ограничение
по Void), §11; `application_description.md` §14.5, §15 и §16.3; `pbl/pay-by-link.md` §5.5;
`code_review.md` P1-5, P1-6, P1-7.

**Результат P1-5 + P1-6 + P1-7.** `openAndBuildRedirect` стал одним `@Transactional`-методом
с `findWithLockById` (`PESSIMISTIC_WRITE`, таймаут блокировки 10 с — под стать таймауту
чтения у эквайера). `TransactionTemplate` из сервиса убран. Гасится только `PENDING`;
`AUTHORIZED` входит в `EnumSet.of(SUCCESS, AUTHORIZED)` при проверке лимита, то есть
занимает слот и не теряется. `+ 1` убран с комментарием и ссылкой на `completeDms:346`
как на образец.

Тесты 230/230 зелёные. `OpenLinkConcurrencyTest` (2) синхронизирует потоки через
`CountDownLatch` и задерживает мок провайдера, чтобы окно гонки реально открылось;
проверяет и число успешных ответов, и число записей, и что `createEcomOrder` вызван один раз.
В `PaymentLinkIntegrationTest` стало 39 тестов (+6 на холд и подсчёт).

**Известное ограничение, вынести отдельной задачей:** операции Void у `AcquiringClient` нет
(в коллекции TXPG она есть — `voidKind: "Full"`), поэтому брошенный авторизованный холд
блокирует одноразовую ссылку, пока банк не снимет его сам.

---

### 18.08.2026 — P1-9 (срок жизни платёжной ссылки)

Задача была не про одно поле, а про расхождение в трёх местах сразу: `create` не выставлял
`expires_at` (все ссылки бессрочны), `PaymentLinkResponse` не отдавал `expiresAt` (фронтенд
подставлял «сейчас + 24 часа» и рисовал по нему обратный отсчёт — `PayByLinkPage.tsx:296`),
а `technical_handover.md` числил TTL реализованным. Планировщик при этом был исправен —
`expireActiveLinksBefore` искал просроченные и не находил ничего.

**Решения (заданы в ТЗ):** срок по умолчанию 24 часа (совпадает с тем, что уже показывал
интерфейс), мерчант может задать свой при создании и через PATCH, потолок — 90 дней
от момента создания ссылки.

**Сделано:**

- `pbl/application.yaml`: `pbl.link.default-ttl` (`PBL_LINK_DEFAULT_TTL`, `PT24H`) и
  `pbl.link.max-ttl` (`PBL_LINK_MAX_TTL`, `P90D`), оба `Duration` ISO-8601. Те же значения —
  в тестовом профиле: он **заменяет** боевой целиком, а тестировать конфигурацию, которой
  ни у кого нет, бессмысленно.
- `CreatePaymentLinkRequest.expiresAt` — необязательный, без `@Future`: вторая граница
  (потолок) всё равно проверяется в сервисе, а сообщение должно называть предельную дату.
- `PaymentLinkService.create`: не передан → `now + default-ttl`; передан → `validateExpiresAt`.
- `PaymentLinkService.update`: тот же `validateExpiresAt`, но точка отсчёта потолка —
  `link.getCreatedAt()`. Это и есть смысл потолка: от «сейчас» его можно было бы сдвигать
  каждым PATCH'ем ещё на 90 дней. Раньше `expiresAt` в PATCH принимался вообще без проверок.
- `PaymentLinkResponse.expiresAt` рядом с `createdAt` + маппер.
- `validateExpiresAt` — приватный, один на оба пути; `formatTtl` печатает «90 days» вместо
  `PT2160H`, иначе сообщение об ошибке ничего не объясняет.

**Чего не делали (по ТЗ):** миграции для бэкфилла (боевых данных нет, `NULL` в `expires_at`
корректно означает «без срока» и для планировщика, и для проверки при открытии),
`PaymentLinkScheduler` (уже умел всё, что нужно), `status` в `update` (воскрешение `EXPIRED` —
это P2-9), фронтенд.

**Хвост во фронтенде (P1-13/P1-14):** после появления `expiresAt` в ответе запасной вариант
`new Date(Date.now() + 86400000)` в `PayByLinkPage.tsx:213,296` стал мёртвым кодом. Он
безвреден — реальное значение теперь всегда приходит с сервера, — но снять его нужно
отдельной задачей.

**Тесты.** `PaymentLinkIntegrationTest`: 39 → 49 методов; `./gradlew test` 240/240 зелёный
(было 230). Срок по умолчанию сверяется с допуском 30 с, а не на точное равенство. Возраст
ссылки в `updateLink_onAnOldLink_cannotExtendBeyondMaxTtlFromCreation` сдвигается нативным
`UPDATE payment_links SET created_at = TIMESTAMPADD(...)` через `JdbcTemplate` (как в
`TransactionReconciliationIntegrationTest`: `@CreationTimestamp` + `updatable = false` иначе
не сдвинуть), массовый апдейт планировщика вызывается через `TransactionTemplate` —
`expireActiveLinksBefore` это `@Modifying`-запрос и ему нужна своя пишущая транзакция.
Существующие тесты не правились, кроме `createPaymentLink_returns201WithBody`, куда добавлена
проверка нового поля.

**Проверено обратно, по одному изменению за раз:**

- снять проверку в `update` (`link.setExpiresAt(request.expiresAt())`) → краснеют
  `updateLink_withExpiresAtBeyondMaxTtl_returns400` и
  `updateLink_onAnOldLink_cannotExtendBeyondMaxTtlFromCreation` (2 из 49);
- оставить проверку, но считать потолок от `Instant.now()` вместо `link.getCreatedAt()` →
  краснеет только `updateLink_onAnOldLink_cannotExtendBeyondMaxTtlFromCreation` — то есть
  «потолок от создания» закреплён отдельно от «потолок вообще есть».

**Документация:** `AGENTS.md` (шапка, §7, §10 — пункт про «нельзя задать при создании» убран,
добавлена запись в закрытых блокерах, TTL убран из списка нереализованного в
`technical_handover.md`, §11), `application_description.md` (§8 DTO, §14, §15, §16.2),
`pbl/pay-by-link.md` (§3.1, §5.1, §5.2), `.env.example`, `.agents/workflows/mp.md`,
`code_review.md` (пометка «исправлено» у P1-9).

### 17.08.2026 — P1-9 ✅

Решения: срок по умолчанию 24 часа (совпал с тем, что уже рисовал фронтенд),
потолок 90 дней **от момента создания** ссылки.

Найдено при разборе: `PaymentLinkResponse` не содержал `expiresAt` (в
`PaymentLinkSummaryResponse` оно было), из-за чего фронтенд на `PayByLinkPage.tsx:296`
подставлял «сейчас + 24 часа» и рисовал обратный отсчёт для бессрочной ссылки.
Поле добавлено — таймер стал правдой.

Результат: `validateExpiresAt(expiresAt, createdAt)` общий для create и update,
потолок считается от `createdAt` — цепочкой PATCH'ей срок не продлить.
Сообщение об ошибке называет и конкретную границу, и срок словами (`formatTtl`,
иначе в тексте был бы `PT2160H`). Тесты 240/240 зелёные, в `PaymentLinkIntegrationTest`
стало 49. Фронтенд не тронут.

**Хвост:** запасной вариант `new Date(Date.now() + 86400000)` в `PayByLinkPage.tsx:213,296`
стал мёртвым — убрать при работе над фронтендом (P1-13/P1-14).

### 18.08.2026 — P1-12 ✅ (бэкенд)

Решения: только бэкенд, access-токен пока 24 ч (фронтенд не умеет обновлять — иначе
выкидывало бы каждые 15 минут); ротация с окном снисхождения.

Сделано: таблица `refresh_tokens` (хранится SHA-256, не сам токен), непрозрачный токен
на 32 байта из `SecureRandom`, эндпоинты `/refresh` и `/logout`, отзыв цепочки при
блокировке и удалении пользователя, плановая уборка просроченных. `expiresIn` считается
из настройки, литерал `86400` убран.

Тесты 226/226 зелёные: `RefreshTokenIntegrationTest` (14 + вложенный класс с нулевым
окном), `RefreshTokenConcurrencyTest` (3).

**Три вещи сверх ТЗ, все существенные:**
1. Проверка `revoked` стоит **до** проверки окна снисхождения — иначе предшественник,
   воспроизведённый внутри окна после logout, воскрешал бы сессию. При этом маркер кражи
   всё равно пишется, чтобы мониторинг не ослеп из-за того, что пользователь успел выйти.
2. `markRotatedIfLive` — условный UPDATE вместо сохранения прочитанной сущности.
   Закрывает гонку «logout во время refresh»: обычный save записал бы устаревший
   `revoked_at = NULL` поверх отзыва и выпустил преемника мёртвой цепочки.
3. `rotated_at` фиксирует время **первой** ротации (`COALESCE`), поэтому повторами
   внутри окна украденный токен нельзя держать живым бесконечно.

**Ограничение, описано честно:** access-токен после logout остаётся валидным до истечения.
Полный отзыв наступит, когда TTL сократится вместе с доработкой фронтенда.

### 18.08.2026 — P1-14 (фронтенд: типы и прод-сборка)

Решения: мёртвый код удалять, а не исключать из проверки; `strict` не включать (отдельная задача).

Сделано: удалены 57 файлов / ≈7 600 строк (стартовый шаблон Vite, второй axios-клиент
с логированием паролей, дубль типов, `components/ui/**` shadcn, пять страниц/компонентов
вне роутера) — каждый проверен grep'ом на импорты. Из `package.json` ушли 48 зависимостей
генератора (Radix ×26, cmdk, vaul, lucide, date-fns, sonner, react-hook-form, motion …),
имя пакета — `merchant-portal-frontend`, `peerDependencies`/`pnpm.overrides` убраны.
Поставлены TypeScript 7.0.2, `@types/react|react-dom|node`, oxlint 1.16.0; скрипты
`typecheck` (`tsc -b`), `lint`, `build` = `tsc -b && vite build`, `preview`.
`apiClient` получил `baseURL: import.meta.env.VITE_API_BASE_URL ?? ''`, добавлен
`frontend/.env.example`; `frontend/.env` уже игнорируется корневым `.gitignore`.

Найдено при разборе:
- **TS 7 включает `strict` по умолчанию** и удалил `baseUrl` из tsconfig — оба момента
  закрыты явно (`"strict": false` с комментарием; `paths` без `baseUrl`). Под `strict`
  остаётся всего 8 ошибок (`TS18048` в двух одинаковых компараторах сортировки).
- `@mui/lab@7.0.1-beta.23` требовал `@mui/material ^7.3.9` при зафиксированной 7.3.5 —
  `npm ls` показывал `invalid`, `npm i` без `--legacy-peer-deps` падал. Пин на
  `7.0.1-beta.19` (peer ровно `^7.3.5`; из lab используется только `Timeline*`).
- Первый прогон `tsc -b`: 19 ошибок (без strict). Среди них настоящий runtime-баг:
  `TransactionTable.tsx` использовал `tObj` без вызова `useLanguage()` → `ReferenceError`
  при непустом списке на `/transactions` и `/transactions/ecommerce`. Остальное — типы
  (`channel` расширялся до `string`, `redirectUrl`/`note` в `PaymentLink` объявлены
  обязательными при том, что маппинг из API их не задаёт, `form.terminalId` отсутствовал
  в начальном состоянии, `Record<TransactionStatus,…>` для карт нижнего регистра — след P2-12,
  мёртвый операнд `transaction.payerIp`). Плюс `cardNumberMasked?: String` → `string`.

Не сделано намеренно: `routes.tsx:42` `createRouter(layoutProps: any)` (каскад правок),
запасной `Date.now() + 86400000` (P1-13), регистр статусов (P2-12), `strict`.
Проверка: `npm ci`, `typecheck`, `lint` (0 ошибок / 78 предупреждений), `build` зелёные;
ручной прогон в браузере — см. отчёт по задаче.

### 18.08.2026 — P1-14 ✅

Решения: мёртвый код удаляем, `strict` пока не включаем.

Удалено 7612 строк: `src/App.tsx`, `src/api/client.ts` (**мёртвый клиент, логировавший
пароль при логине**), `src/types/index.ts`, `src/App.css`, все 48 файлов `components/ui`,
страницы Reports/Notifications/POS и два POS-компонента. Все зависимости `@radix-ui/*`
и спутники убраны. `ImageWithFallback` и Tailwind оставлены — они живые.

Поставлены TypeScript 7 и oxlint, `build` теперь `tsc -b && vite build` — гейт настоящий.
`baseURL: import.meta.env.VITE_API_BASE_URL ?? ''` плюс `frontend/.env.example`;
пустая строка = нынешнее поведение, dev с прокси не меняется.

Важная деталь: в TS 7 `strict` включён по умолчанию, поэтому его пришлось выключить
**явно** — иначе первый прогон дал бы ту самую лавину, которой мы решили избежать.

Компилятор поймал немного, но по делу: `cardNumberMasked?: String` → `string`
(boxed-тип), и несколько полей DTO (`redirectUrl`, `note`, `merchantRid`),
которые читались как обязательные, хотя бэкенд их не отдаёт и в живых данных
они всегда `undefined`.

Бэкенд не тронут, 258 тестов зелёные (полный прогон 08:47; прежние числа 240 и 226
читались из частично обновлённых отчётов).

**Осталось непротипизированным:** `routes.tsx:42` — `createRouter(layoutProps: any)`,
пропсы всех страниц ходят через нетипизированный объект. Разбирать вместе с P1-13.

### 18.08.2026 — P1-13 (фронтенд: авторизация — роли, маршруты, токены)

Решения: access-токен **только в памяти**, refresh — в `localStorage` (`mp_refresh_token`);
httpOnly-cookie не делаем (правки бэкенда — отдельная задача); `/me` не заводим — `fullName`
из email больше не выдумывается, показывается сам email; TTL access-токена сокращён до 15 минут
одной переменной `JWT_EXPIRATION_MS` во всех трёх сервисах.

Сделано:
- `types/role.ts` — `ROLES`/`Role`/`parseRole` (строгое сравнение, зеркало `Role.fromValue`).
- `auth/session.ts` — хранилище токенов и профиля; `applyLoginResponse` fail-closed
  (нераспознанная роль или ответ без токенов → `AuthError`, сессия не создаётся);
  все обращения к `localStorage` под `try/catch`; событие `storage` гасит сессию в других
  вкладках при выходе.
- `api/client.ts` — request-интерсептор берёт токен из памяти; response-интерсептор на 401:
  `/login`/`/refresh`/`/logout` не трогать → уже повторяли → `clearSession` → иначе
  single-flight `refreshSession()` и повтор. Сетевая ошибка `/refresh` сессию не сбрасывает.
- `AuthContext` на `useSyncExternalStore`: восстановление при загрузке через `/refresh`
  с экраном загрузки (форма логина не мигает); `logout` — сброс сразу, `POST /logout` вдогонку.
- `auth/routeAccess.ts` (единственная раскладка «маршрут → роли»), `auth/guards.tsx`
  (`ProtectedRoute`, `PublicOnlyRoute` → `/`, `RoleRoute` → `ForbiddenPage`), `Sidebar`
  фильтрует пункты по той же раскладке; `NotFoundPage` (`*`), `RouteErrorPage` (`errorElement`);
  `LoginPage` → `navigate('/', { replace: true })`; `createRouter(props: AppRouterProps)`.
- `App.tsx`: транзакции запрашиваются после появления сессии (`AppShell` под `AuthProvider`),
  а не при первом рендере до логина (раньше запрос уходил без токена и список пустовал).
- `CompaniesPage`: внутренний «Access Denied» снят (за него отвечает `RoleRoute`, и по матрице
  список компаний читает и `AUDITOR`); `isAdmin` прячет только кнопки записи.
- Бэкенд: `expiration-ms: ${JWT_EXPIRATION_MS:900000}` в `auth`, `directory`, `pbl`;
  `.env.example`, `deployment_guide.md`, `auth/auth.md`, `application_description.md`, `AGENTS.md`.

Ревью (adversarial, 5 линз — 4 упали по лимиту сессии, security-линза дошла): подтверждена гонка
«`/refresh` в полёте vs выход» — ответ применялся безусловно и воскрешал сессию; закрыто счётчиком
поколения сессии (`getSessionGeneration`) + гашением свежевыпущенного refresh-токена. Заодно:
отклонённый вход/refresh гасит выпущенный сервером refresh-токен; при повторном входе поверх
оставшегося (после сетевого сбоя восстановления) токена старый гасится; логи через `describeError`
(объект `AxiosError` содержит тело запроса с refresh-токеном); `localStorage.clear()` в другой
вкладке тоже гасит сессию; легаси-ключи `token`/`user` прежней версии сносятся при загрузке.

Найдено при разборе: `useAuth().user.id` нигде не используется, `fullName` — только в `Header`
и моковой вкладке Account в `SettingsPage` (заменено на email); `user?.role === 'ADMIN'` — сравнение
с несуществующей ролью (`Sidebar`, `SettingsPage`, `CompaniesPage`), под типом `Role` не компилируется —
убрано. Ротация `JWT_SECRET` теперь не выкидывает пользователей: refresh-токен не подписан ключом,
первый 401 приводит к `/refresh` и повтору (исправлен CAUTION в `deployment_guide.md`).

Не сделано намеренно: httpOnly-cookie, `/me`, упреждающее обновление по `expiresIn`
(обновление реактивное, по 401), регистр статусов (P2-12), `strict`, мёртвый запасной
`Date.now() + 86400000` в `PayByLinkPage`/`PayByLinkDetailPage`.

### 18.08.2026 — P1-13 ✅

Решения: refresh и сокращение TTL — вместе с ролями; access-токен в памяти,
refresh в `localStorage`.

Fail-open роль убрана (`|| 'SYSTEM_ADMIN'` не находится нигде), заведён union `Role`
с `parseRole` без приведения регистра — зеркало `common.security.Role.fromValue`.
Access-токен ушёл из `localStorage` полностью: там остались только `mp_refresh_token`
и язык. Обновление токена — single-flight, с защитой от цикла на `/login` и `/refresh`
и флагом `_retried`. Ролевые guard'ы читают раскладку из `auth/routeAccess.ts`,
её же использует сайдбар — разъехаться нельзя. В обоих местах написано, что это UX,
а права проверяет бэкенд.

Починено попутно: `navigate('/home')` → `navigate('/', { replace: true })`,
добавлены `errorElement` и `path: '*'`, `createRouter(layoutProps: AppRouterProps)` —
хвост из P1-14 закрыт.

TTL access-токена сокращён до 15 минут во всех трёх сервисах. Тестовый профиль задаёт
свой срок (1 час), поэтому на тесты не влияет — 258 зелёные.

**Сверх ТЗ:** `LEGACY_KEYS` — чистка ключей старой версии, чтобы у уже работающих
пользователей access-токен не остался лежать в хранилище; обработчик события `storage`
с разбором `key === null` (это `localStorage.clear()`) — синхронизация выхода между
вкладками; `try/catch` на доступ к `localStorage` для приватного режима.

**Измеримый результат связки P1-12 + P1-13:** заблокированный администратором
пользователь терял доступ через 24 часа, теперь — через 15 минут.

### 18.08.2026 — P1-10 (адреса в конфигурации)

Разбор. Решения Р-16, Р-17, Р-18.

`pbl.base-url: http://localhost:8080/` подставляется в трёх местах, и одно из них —
`OpenLinkService:195`, где из него строится `hppRedirectUrl`, **уходящий эквайеру**
в теле `createEcomOrder`. В проде TXPG вернул бы плательщика на `localhost`, и не завершился
бы ни один платёж. Это не «некрасивый дефолт», а неработающая оплата.

Найдено попутно: у `TxpgAcquiringClient` в `@Value` свои встроенные дефолты, и они
**противоречат** yaml — `api-base-url` там `https://test.millikart.az:8083` против
`http://test.millikart.az:8000/` в конфиге, пути `/api/order/{orderId}/…` против
`/order/{orderId}/…`. Пока ключи в yaml есть, дефолты мертвы; в день, когда ключ уедет
или в нём будет опечатка, сервис молча пойдёт на другой хост и другие пути. Убираем все
шесть: значение по умолчанию живёт в одном месте — в yaml.

`MissingSecretFailureAnalyzer` расширяем на адреса — иначе оператор получит
«Could not resolve placeholder 'PBL_BASE_URL'» без единого слова о том, что делать.

ТЗ передано в отдельную сессию.

### 18.08.2026 — P1-10 ✅

Три адреса ушли в окружение без дефолтов (`PBL_BASE_URL`, `PBL_PROVIDER_GATEWAY_BASE_URL`,
`PBL_PROVIDER_API_BASE_URL`), пути остались с дефолтами. У `TxpgAcquiringClient` убраны все
шесть встроенных `@Value`-дефолтов, а вместе с ними — расхождение с yaml (`:8083` против
`:8000`, `/api/order/…` против `/order/…`).

`UrlConfigurationCheck` роняет старт на пустом, относительном и не-http значении и пишет WARN
в рамке на HTTP, старт не блокируя (Р-17). Флага-исключения нет.
`MissingSecretFailureAnalyzer` расширен на адреса, с отдельным текстом причины: у секрета
дефолт — это утёкший ключ, у адреса — продакшен, который шлёт плательщиков на localhost.

275 тестов зелёные (+17 к 258), прогон в 18:01 — после всех правок кода в 17:58.

**Сверх ТЗ:** отказ на значении с пробелом или CRLF на конце (значение используется как есть,
хвост из env-файла с Windows-переводом строки сломал бы каждый URL; в сообщении `\r\n` виден
явно); отказ при `URI.getHost() == null` — это ловит незаменённый `https://ВАШ_ДОМЕН/` прямо
из deployment_guide; `[::1]` в списке локальных хостов; третий сторож в
`ConfigurationExternalizationTest` — регулярка требует ровно `${VAR}` и ловит дефолт,
пронесённый обратно внутрь плейсхолдера (`${PBL_BASE_URL:http://127.0.0.1:8080/}` обошёл бы
обе подстрочные проверки).

**Мелочи на потом:** в `pbl/src/test/resources/application.yaml` пути провайдера остались
старой формы (`/api/order/{orderId}/…`) — они инертны (`stub: true`, реальный клиент в тестах
Spring не поднимается), но документируют форму URL, которой нет; `@Value("${pbl.base-url}")`
теперь в четырёх местах вместо трёх; со `stub: true` адреса эквайера всё равно обязательны —
задокументировано в `.env.example`, но при локальной отладке это лишний шаг.

### 18.08.2026 — P1-8 (разбор: контракта нет, но нашлась вторая проблема)

Решения Р-19, Р-20.

Исходная формулировка подтверждается: `checkAndThrowIfErrorCode` смотрит ровно на наличие
ключа `errorCode`, всё остальное считается успехом. Но чинить это нечем: **в
`NON-PSP Ecom.postman_collection.json` у запросов `Refund` и `Reversal` нет ни одного
сохранённого ответа** — ни успешного, ни отказного. То есть «нет `errorCode` — значит успех»
это не небрежность, а единственное, что можно было написать вслепую. Ждём контракт (Р-19),
письмо составлено.

**Вторая проблема, в ревью её нет, и она опаснее.** `PaymentLinkService.refreshStatus`
(:689-712) — цепочка `if / else if` по строкам `FullyPaid`/`Cleared`, `Authorized`,
`Failed`/`Canceled`/`Declined`/`Rejected`. Незнакомый статус **проваливается молча**: нет
`else`, нет WARN, транзакция остаётся `PENDING`, в лог идёт «updated status to: PENDING».

Дальше это попадает в `reconcileOne`, который помечает `FAILED`, если после опроса
транзакция всё ещё `PENDING`, а опрос удался. Незнакомый статус от нефинального он не
отличает. Если TXPG ответит `Paid`, `PartiallyPaid`, `Settled` или `Completed` —
**оплаченный платёж будет закрыт как FAILED** через сутки, молча. Исходный P1-8 записывает
отказ как успех, этот — успех как отказ.

Попутно: `refund` читает `refundId` из ответа эквайера, а строки `refundId` нет ни в одном
месте коллекции TXPG. Похоже, поле выдумано, и после P0-7 (фальшивый `REF-XXXX` убран)
`refundId` в ответе всегда `null`. Включено в вопросы к MilliKart.

Регистр заказа (`authorizedChargeAmount`, `clearedChargeAmount`, `clearedRefundAmount`,
`trans[]` с `approvalCode` и `rrn`) не используется вообще — это кандидат на подтверждение
денежных операций, когда придёт ответ по контракту.

### 18.08.2026 — P1-8: нашёлся контракт TXPG

Документ «Client side integration» v0.1.3 (Vladislav Popov, 28.12.2023) — то, чего не хватало.
Положен в репозиторий: `pbl/TXPG-client-side-integration.md`.

**§5.8.8 — словарь статусов заказа. Наш список почти весь выдуман.**

| Документ | Значение | Есть у нас |
|:---|:---|:---|
| `FullyPaid` | успешная оплата | да → SUCCESS |
| `Rejected` | ошибочная транзакция | да → FAILED |
| `Expired` | истекла по таймауту | **нет** |
| `Closed` | закрыта, API-запросы больше не принимаются | **нет** |
| `PartPaid` | частично отменена или частично возвращена | **нет** |
| `Cancelled` | полностью отменена (reversal) | **нет** — у нас `Canceled`, одна `l` |
| `Refused` | полностью возвращена | **нет** |

В коде при этом есть `Cleared`, `Authorized`, `Failed`, `Declined`, `Canceled` — ни одного
из них в документе нет. `Canceled` против `Cancelled` — промах в одну букву: полностью
отменённый заказ не попадает в ветку FAILED и проваливается в тишину.

Пять задокументированных статусов не обрабатываются нигде. `Refused` (полный возврат) и
`PartPaid` (частичный) сегодня остаются `PENDING` и через сутки помечаются `FAILED`
как брошенные плательщиком.

**§5.5-5.7 — ответ `exec-tran` (одинаковый для Single, Reversal и Refund):**

```json
{"tran": {"approvalCode": "340775",
          "match": {"tranActionId": "220613-09172925-000hbr=", "ridByPmo": "220613334596244733"}}}
```

Поля `refundId` **не существует**. `PaymentLinkService:473` читает именно его, то есть
`refundId` в ответе на возврат всегда `null`. Правильные идентификаторы —
`tran.match.tranActionId` (модуль e-comm) и `tran.match.ridByPmo` (ядро ПЦ).

**§5.8.8, примечание — эквайер сам описывает, как проверять успех операции:**
`order.trans` не пуст, `trans.billingStatus == "Normal"`, `trans.ridByPmo != null`.
По каждой операции — `trans.description`: `Purchase`, `Purchase - Void`, `Refund`.
Последняя операция — по `trans.regTime`; знак `trans.clearAmount` отличает покупку от
возврата. Вопрос про синхронность `clearedRefundAmount` отпадает: проверять нужно `trans[]`.

**§5.8.7 — причина отказа доступна и выбрасывается.** `custAttrs` с `rid` =
`DeclineDescription`, `PmoDeclineDescription`, `PmoResultCode`. Мерчант сейчас видит
`FAILED` без причины.

**Новая находка: три поля в карточке транзакции всегда пустые.**
`mapToTransactionResponse` (:804-806) читает `cardNumberMasked`, `rrn` и `approvalCode`
из корня `providerResponse`, то есть из объекта `order`. По документу `rrn` и `approvalCode`
лежат в `order.trans[]` / `order.lastTran`, а маскированный номер карты — это
`order.srcToken.displayName`; ключа `cardNumberMasked` в контракте нет вообще. То есть в
проде мерчант видит пустыми ровно те три поля, по которым сверяют платёж с выпиской банка.
Заведено как отдельная задача.

**Подтвердилось задним числом:** §4.1 — «STATUS parameter value can be temporary, so you have
to verify transaction status using a Transaction details request». Это ровно решение Р-6
(параметры `ID`/`PASSWORD`/`STATUS` из редиректа игнорировать, статус перепроверять запросом).

**Чего документ не покрывает:** DMS (`Order_DMS`) в нём нет совсем — статус авторизованного
холда и ответ на `phase: "Clearing"` не описаны, так что `Authorized` остаётся
неподтверждённым. Список в §5.8.8 назван «MAJOR status field values», то есть может быть
неполным. Ответ `exec-tran` показан только успешный.

### 18.08.2026 — P1-8a (ТЗ) и побочный эффект Р-20

Решения Р-21, Р-22.

При подготовке ТЗ вскрылось следствие Р-20, которого не было видно раньше.
`TransactionReconciliationService` (:59-61) выбирает **все** `PENDING` старше `min-age`,
без верхней границы, батчами по `batch-size` и с `ORDER BY created_at ASC`. Сегодня это
безопасно: любая зависшая строка через `max-age` уходит в `FAILED` и выпадает из выборки.
Как только незнакомый статус перестаёт давать `FAILED` (Р-20), такие строки остаются
`PENDING` навсегда — и, будучи самыми старыми, начинают занимать весь батч. Сверка
перестанет доходить до свежих платежей: классическое вытеснение головой очереди.

Поэтому в P1-8a добавлена верхняя граница выборки (`pbl.reconciliation.give-up-age`,
по умолчанию `P7D`) и счётчик строк за этой границей в логе цикла — чтобы куча зависших
была видна, а не росла молча.

### 18.08.2026 — P1-8a (проверка)

315 тестов зелёные (+40 к 275), прогон в 20:30 — после последней правки кода в 20:29.
`TxpgAcquiringClient` не тронут (mtime 17:18), то есть граница Р-21 соблюдена.

Словарь `ProviderOrderStatus` собран из §5.8.8, каждая группа снабжена ссылкой на источник,
`Authorized`/`Cleared` честно помечены как не подтверждённые контрактом. `Canceled` и
`Cancelled` лежат рядом. `reconcileOne` пропускает к таймауту только `NON_FINAL`, причём
поставлена ещё и защита на будущее: любой новый outcome не получит таймаут молча.
Верхняя граница выборки (`give-up-age`, `P7D`) закрыта тестами
`reconcile_olderThanGiveUpAge_isNotSelectedAndAcquirerIsNotPolled` и
`reconcile_givenUpRowDoesNotStarveTheBatch` — второй проверяет ровно то вытеснение
головой очереди, ради которого граница вводилась.

**Замечание (одно).** `refreshStatus` при `orderDetails == null` теперь затирает
`providerResponse`. Раньше вся запись payload'а стояла внутри `if (orderDetails != null)`,
и при пустом ответе прошлый payload сохранялся; теперь `stored` собирается с нуля, получает
только `mpStatusOutcome: "UNKNOWN"` и заменяет собой всё, что было известно о заказе —
включая ответ на создание заказа. Путь достижим: `getOrderStatus` возвращает `null`, когда
эквайер отдаёт 200 с пустым телом. Пострадает ровно та строка, которая после этой задачи
уходит в ручной разбор, и разбирать её будет нечем.

Исправление: при `orderDetails == null` не трогать `providerResponse` (или засеивать
`stored` прежним значением) — как это уже делают `completeDms` и `reconcileOne`, которые
сливают, а не заменяют. Плюс тест: `getOrderStatus` возвращает `null` → прежний payload на
месте, статус `PENDING`, WARN есть.

### 18.08.2026 — P1-8a ✅

Замечание закрыто ровно так, как предлагалось: при `orderDetails == null` `stored`
засеивается прежним `providerResponse`, при непустом ответе замена остаётся полной —
чтобы устаревший `mpProviderStatus` не пережил переход в понятный статус. Появился
`reconcile_providerAnswersWithNullPayload_keepsPreviousPayloadAndStaysPending`: до правки
из шестнадцати сценариев сверки ни один не подставлял `null`.

316 тестов зелёные, прогон в 21:01 — после правок в 21:00.

Мелочь на будущее, не дефект: при пустом ответе в `providerResponse` теперь соседствуют
старый `status` (например `Preparing`) и свежий `mpStatusOutcome: UNKNOWN`. Читается как
противоречие, хотя означает «последний опрос не ответил ничего». В логе это объяснено
отдельным WARN; если когда-нибудь станет мешать при разборе споров — добавить пометку,
что payload от предыдущего опроса.

### 18.08.2026 — P1-8b (ТЗ)

Решения Р-23, Р-24.

Контракт (§5.5-5.7) даёт форму успешного ответа `exec-tran` — одинаковую для оплаты,
reversal и возврата: `tran.approvalCode` плюс `tran.match.tranActionId` и
`tran.match.ridByPmo`. Проверка успеха, которую сам эквайер описывает в §5.8.8, опирается
на `ridByPmo` — идентификатор операции в ядре процессингового центра. Его наличие и есть
доказательство, что операция дошла.

Нашлось попутно: `refund` **вообще не сохраняет** ответ эквайера. `providerResponse` в нём
не трогается, идентификаторы уходят один раз в HTTP-ответ и теряются навсегда. При разборе
спора по возврату не остаётся ничего. `completeDms` в этом смысле аккуратнее — он сливает
ответ в `providerResponse`, но и там идентификаторы не выделены.

Про `custAttrs`: в §5.8.3 видно, что `PmoResultCode` присутствует и у **успешного** заказа
со значением `Approved`. Значит извлекать причину отказа можно только для отказавших
заказов и с явным пропуском `Approved` — иначе «Approved» попадёт в поле «причина отказа».

### 19.08.2026 — P1-8b (сделано)

Сделано по ТЗ, границы Р-21/Р-23/Р-24 соблюдены; `checkAndThrowIfErrorCode`, словарь статусов,
сверка и P1-16 (`order.trans[]`, `rrn`, маска карты) не тронуты.

- `AcquiringClient.completeDms/refund` → `MoneyOperationResult(approvalCode, tranActionId,
  ridByPmo, raw)`. `TxpgAcquiringClient.requireConfirmation` идёт сразу за
  `checkAndThrowIfErrorCode`: нет `tran.match.ridByPmo` — ERROR `NO CONFIRMATION` с телом целиком
  и `PaymentOutcomeUnknownException` (502); нет `approvalCode`/`tranActionId` — WARN. Разбор через
  `asMap`/`asText`, поэтому `tran`/`match` любого типа и `ridByPmo` числом не роняют поток.
  `classifyMoneyOperationFailure` пропускает `PaymentOutcomeUnknownException` без обёртки.
- Стаб отвечает формой §5.5-5.7 с разными идентификаторами на каждый вызов; `REF-XXXXXXXX` убран.
- `PaymentLinkService`: `mpCapture` после clearing, список `mpRefunds` на возвратах (запись
  `{tranActionId, ridByPmo, approvalCode, amount, at}`), в той же транзакции, что и смена статуса;
  чтение `refundId` из ответа удалено. `RefundResponse` = `refundId`(`tranActionId`),
  `acquirerReference`(`ridByPmo`), `approvalCode`. `ProviderDeclineReason.extract` — только при
  `FAILED_FINAL`, `mpDeclineReason` → `TransactionResponse.failureReason`.
- Тесты: `TxpgAcquiringClientTest` +10, `MoneyOperationsIntegrationTest` +5,
  `ProviderDeclineReasonTest` 15 (без Spring), `PaymentLinkIntegrationTest` +2.
  `./gradlew test` — 348 запусков (279 методов), 0 падений; `:pbl:test` — 206. Приёмочные grep'ы: `"refundId"` в
  `pbl/src/main` нет; `ridByPmo` и `PaymentOutcomeUnknownException` в клиенте есть;
  `mpCapture|mpRefunds|mpDeclineReason` в сервисе есть. Пункт 5 приёмки (`git diff | grep
  cardNumberMasked`) показывает строку `cardNumberMasked` как **контекст** (без `+`/`-`) — она
  попадает в hunk от более ранней незакоммиченной правки `receiptState`, а не от P1-8b.
- Документация: `pay-by-link.md` §5.8 (`failureReason`), §5.10 (новая форма ответа возврата,
  `mpRefunds`/`mpCapture`), §6 (502 при 200 без `ridByPmo`); Postman-описание возврата;
  `AGENTS.md` §7, §10, §11.

Два места, где решение принято мной, а не ТЗ, — на проверку:
1. В `refund` сырой ответ эквайера по-прежнему сливается в `providerResponse` поверх старого
   (как делает `completeDms`), а не только пишется в `mpRefunds`. Второй возврат перетирает
   `tran` первого на верхнем уровне — но след каждого целиком лежит в `mpRefunds`.
2. `mpDeclineReason` пишется только при `FAILED_FINAL`; `FAILED` терминален, `refreshStatus`
   на нём выходит рано, так что причина не затирается последующими опросами. При переоткрытии
   ссылки (`PENDING` → `FAILED` руками) причины нет — и не должно быть.

Самопроверка (пять независимых ревьюеров-агентов с разными линзами, каждая находка — через
двух скептиков): критичного и среднего по коду — ничего; по итогам поправлено следующее.
Тесты `*WithoutClassCast` проверяли только тип исключения, а catch-all в
`classifyMoneyOperationFailure` завернул бы и `ClassCastException` в тот же тип — теперь
проверяются текст про `tran.match.ridByPmo` и отсутствие `cause`. Сумма в `mpCapture`/`mpRefunds`
нормализуется к двум знакам (как на проводе; `assertCapturableScale` гарантирует безопасность
`UNNECESSARY`), закреплено `completeDms_confirmed_recordsCaptureEvidence` со `scale 0` на входе.
Javadoc `ProviderDeclineReason` больше не приписывает порядок поиска контракту: §5.8.7 читает
`DeclineDescription` → `PmoResultCode`, а `PmoDeclineDescription` упоминает «также»; порядок
`DeclineDescription` → `PmoDeclineDescription` → `PmoResultCode` — решение Р-24. Две устаревшие
ссылки «`order.trans[]` — P1-8b» (комментарий в `SETTLED_OTHER` и текст WARN в `reconcileOne`,
оба из P1-8a) заменены на P1-16 — только текст, логика сверки не тронута.
`completeDmsAndRefundFlow` теперь проверяет `acquirerReference`/`refundId` и след
`mpCapture`/`mpRefunds` от реального стаба.

### 18.08.2026 — P1-8b (проверка)

348 тестов зелёные (+32 к 316), прогон в 22:45 — после последней правки кода в 22:44.

Подтверждение операции требуется (`tran.match.ridByPmo`), при его отсутствии — 502 и полное
тело ответа в лог на ERROR, как договаривались по Р-23. `classifyMoneyOperationFailure`
получил ранний возврат для `PaymentOutcomeUnknownException`, так что исключение не заворачивается
само в себя — на это есть отдельный хелпер в тесте (`assertUnconfirmedNotWrapped`).
`refund_errorCodeStillWinsOverMissingConfirmation` фиксирует приоритет: явный отказ остаётся
400 и не превращается в 502.

`ProviderDeclineReason` пропускает `Approved`, разбирается защитно и покрыт 14 тестами.
В `PaymentLinkIntegrationTest` есть и отрицательный случай: у оплаченной транзакции
`failureReason` пуст.

Следы операций (`mpCapture`, `mpRefunds`) не могут быть затёрты последующим опросом:
`refreshStatus` выходит сразу на терминальных статусах, включая `PARTIALLY_REFUNDED`.

**Замечание (одно).** `asText` в `TxpgAcquiringClient` (:332-338) принимает **любое**
не-пустое значение после `String.valueOf`. Для `ridByPmo` это значит, что ответ
`{"tran":{"match":{"ridByPmo":{}}}}` даст текст `"{}"` — непустой, значит «операция
подтверждена». Тем же путём пройдут `[]`, `false` и вложенный объект, который превратится
в java-строку вида `{a=1}` и в таком виде ляжет в свидетельство для разбора спора.

Замысел в javadoc сформулирован верно («число тоже подтверждает»), но код не ограничивает
значение скаляром. Структура под `ridByPmo` означает ровно то, ради чего вводилось Р-23:
форма ответа не та, что в контракте, то есть исход неизвестен.

Исправление: в `asText` принимать только `String` и `Number`, остальное → `null`.
Плюс тест: `ridByPmo` объектом и списком → `PaymentOutcomeUnknownException`. Сейчас тесты
покрывают число, пустую строку и не-объектный `match`, но не структуру на месте самого
идентификатора.

### 19.08.2026 — P1-8b, правка `asText`

`TxpgAcquiringClient.asText` принимал что угодно через `String.valueOf`: `"ridByPmo": {}`
давал непустое `"{}"` и операция считалась подтверждённой, вложенный объект уезжал в
`mpRefunds`/`mpCapture` java-строкой `{a=1}`. Теперь идентификатор — только `String` или
`Number`; структура на месте `ridByPmo` → `PaymentOutcomeUnknownException` (форма не §5.5-5.7,
Р-23), на месте `approvalCode`/`tranActionId` → «отсутствует», WARN. `asMap`, разбор
`tran`/`match`, текст исключения не тронуты. Четыре новых теста
(`refund_ridByPmoAsObject/AsList/AsNestedObject_leavesTheOutcomeUnknown`,
`refund_approvalCodeAsObject_isTreatedAsAbsent`); `refund_ridByPmoAsNumber_isAcceptedAsText`
зелёный. `:pbl:test` — 210 запусков, 0 падений.

### 18.08.2026 — P1-8b ✅

Замечание закрыто: `asText` принимает только `String` и `Number`, всё остальное — отсутствие
идентификатора. Четыре теста: `ridByPmo` объектом, списком и вложенным объектом дают 502,
причём отдельно проверено, что java-рендер структуры (`{id=x}`) не протекает в текст вердикта;
структура в необязательном `approvalCode` не роняет подтверждённую операцию, а лишь даёт WARN.
`refund_ridByPmoAsNumber_isAcceptedAsText` остался зелёным — число по-прежнему принимается.

352 теста зелёные, прогон в 23:02 — после правок в 23:01.

Спринт 1 закрыт, кроме P1-16.

### 18.08.2026 — P0-9 не закрыта (найдено при сверке плана)

Я ранее считал спринт 0 закрытым целиком. Это неверно: **P0-9 никогда не делалась**,
статус в таблице так и стоял `—`. Проверил код — все три места из ревью на месте:

1. `TxpgAcquiringClient` — пароль заказа уходит в query-параметр, и URL целиком пишется
   в лог на INFO: `completeDms` (:134, :147), `refund` (:180, :191),
   `getOrderStatus` (:220, :227). Query-строка оседает ещё и в логах любого прокси
   между сервисом и эквайером.
2. `OpenLinkController:40` — `log.info("Redirecting customer to HPP URL: {}", redirectUrl)`,
   а redirect-URL собирается в `OpenLinkService:230` вместе с `&password=`.
3. `OpenLinkService:221-224` — пароль дублируется в JSON-колонку `provider_response`,
   хотя для него есть отдельная колонка `provider_password`.

**Четвёртое место, которого в ревью не было и которое появилось по ходу наших же задач.**
`refreshStatus` (P1-8a) кладёт в `provider_response` payload заказа целиком, а он по §5.8.3
контракта содержит поле `password`. То есть пароль заказа теперь попадает в колонку не только
при создании транзакции, но и после каждого опроса статуса — из ответа самого эквайера.
Значит чистка нужна не только в месте записи (пункт 3), но и при сохранении payload'а опроса.

Плюс `log.debug("PROVIDER RESP BODY [createEcomOrder]: {}", response)` — ответ на создание
заказа тоже содержит `password`; на DEBUG, но при включённом DEBUG это тот же самый пароль
в логе.

### 19.08.2026 — P0-9 (сделано)

Сделано по ТЗ (Р-25): меняются только логи и хранение, отправка не тронута —
`queryParam("password", …)` по-прежнему в трёх местах `TxpgAcquiringClient`, пароль по-прежнему
в redirect-URL плательщика. Миграции для уже записанных `provider_response` нет (Р-14). P1-16
не тронут.

- Новый `az.millikart.pbl.provider.ProviderPayloads` (без Spring): `urlForLog(url)` —
  `UriComponentsBuilder.fromUriString(url).replaceQuery(null).toUriString()`, не
  `replaceQueryParam` (добавил бы `password=***` там, где пароля не было); `null`/`""` как есть,
  неразбираемый адрес → `<unparseable url>`, не сам адрес; страховка от opaque-URI
  (`mailto:a?b` — `replaceQuery` не трогает scheme-specific part) — всё после `?` отрезается.
  `withoutSecrets(payload)` — копия без `password` по верхнему уровню, вход не меняется,
  `null` → `null` (в логе «нет тела» остаётся видимым).
- `TxpgAcquiringClient`: четыре лога запроса через `urlForLog`; ERROR `NO CONFIRMATION … Full
  body` через `withoutSecrets`. **Сверх ТЗ, найдено при написании теста 7:** `getOrderStatus`
  писал в INFO тело ответа целиком (`Response: {}`), а это конверт `{"order": {…, "password":
  …}}` — без этого «ни одна строка лога после `getOrderStatus` не содержит пароль» не
  выполнялось. Теперь логируется сам объект `order` через `withoutSecrets`, и после проверки
  `errorCode` (отказ больше не объявляется `SUCCESS` первой строкой); возвращаемое значение не
  изменилось. Логи ответов `completeDms`/`refund` тоже через `withoutSecrets` — по той же логике,
  что и `Full body`: чужой сырой payload.
- `EcomCreateOrderResponse.Order.toString()` → `password=***` (`null` — как `null`, отсутствие
  пароля стоит видеть).
- `OpenLinkService`: из `providerResponse` убран ключ `password` (`hppUrl`, `id`, `status`
  остались), redirect-URL не изменён. `OpenLinkController:40` — `urlForLog(redirectUrl)`.
- `PaymentLinkService.refreshStatus`: `stored.putAll(withoutSecrets(orderDetails))`; ветка
  пустого ответа не тронута, но её результат тоже через `withoutSecrets`. **Сверх ТЗ:** сырой
  ответ `exec-tran` (`capture.raw()`, `result.raw()`) в `completeDms`/`refund` тоже кладётся через
  `withoutSecrets` — иначе правило из `AGENTS.md` («чужой payload в базу — только через
  `withoutSecrets`») нарушалось бы в двух местах сразу после его записи; поведение сегодня
  идентично (пароля в этом ответе нет), `MoneyOperationResult.raw` остаётся сырым.
- Тесты: `ProviderPayloadsTest` 14 методов / 21 запуск; `TxpgAcquiringClientTest` +11 (логгер
  клиента на TRACE, проверяются сообщения и исключения всех уровней; пароль при этом **есть** в
  URL запроса — иначе тест был бы пустым); `PaymentLinkIntegrationTest` +4 (сырая колонка
  `provider_response` через `JdbcTemplate`, root-логгер на время `/open`, опрос с `password`,
  пустой ответ поверх «старой» записи с паролем). Проверено обратно: с `urlForLog` и
  `withoutSecrets`, возвращающими вход, краснеют ровно восемь тестов клиента.
  `./gradlew test` — 388 запусков (312 методов), 0 падений; `:pbl:test` — 246.
  Приёмочные grep'ы из ТЗ: 1 и 2 молчат, 3 находит `withoutSecrets` в `PaymentLinkService`,
  4 даёт 3.
- Документация: `problems.md` §5 (пароль в адресе `exec-tran` не требуется по §5.5-5.7 и по
  Postman — проверить на стенде и убрать; в `GET /order/{id}` требуется по §5.8), `AGENTS.md`
  (§7, §10 — P0-9 в закрытых, «грабли», §11, §12), `code_review.md` (P0-9 ✅),
  `pbl/pay-by-link.md` §3.2, таблица спринта 0.

Вне нашего контроля остаётся: пока `?password=` уходит в адресе, он оседает в логах прокси и
эквайера. Spring в сообщениях своих исключений query-строку срезает (проверено тестами на
таймаут и 5xx); что пишут на DEBUG/FINE сам HTTP-стек (`HttpURLConnection`) и прокси — за
пределами этой правки, и это ещё один довод убрать параметр после сверки на стенде.

### 19.08.2026 — P0-9 ✅

388 тестов зелёные (+36 к 352), прогон в 08:31 — после последней правки кода в 08:31:03.

Все пять мест закрыты: адреса в логах без query-строки, `provider_response` без `password`
и при создании транзакции, и после каждого опроса, тела ответов эквайера — через
`withoutSecrets`, `toString()` рекорда `EcomCreateOrderResponse.Order` маскирует пароль на
уровне типа, а не в месте вызова. Отправка не тронута (Р-25), `queryParam("password")`
по-прежнему три штуки, и это записано в `problems.md` §5 с проверкой на стенде.

`urlForLog` не маскирует параметр, а отбрасывает query целиком — с явным объяснением, почему
`replaceQueryParam` не годится (он бы добавил `password=***` туда, где пароля не было).
Отдельно обработан opaque-URI, у которого query живёт внутри scheme-specific части.

**Проверял отдельно и не нашёл дефекта.** Подозревал утечку на таймауте: сообщение
`ResourceAccessException` попадает и в лог, и в текст `PaymentOutcomeUnknownException`, а
Spring строит его из адреса запроса. Посмотрел исходник `DefaultRestClient` (ветка 6.1.x) —
`createResourceAccessException` обрезает строку по `indexOf('?')`, то есть query туда не
попадает. Плюс тело ответа 502 мерчанту — фиксированная строка, `ex.getMessage()` наружу не
уходит (`GlobalExceptionHandler:52-54`). Реализация это всё равно закрепила тестами
`refund_readTimeout_neverLogsTheOrderPassword` и `completeDms_serverError_neverLogsTheOrderPassword`,
так что регрессия в Spring или своя правка сообщения будут пойманы.

Спринт 0 закрыт полностью. Из спринта 1 осталась только P1-16.

### 19.08.2026 — P1-16 (ТЗ)

Решение Р-26.

`mapToTransactionResponse` (:1017-1021) читает три поля из корня `providerResponse`, то есть
из объекта `order`. По контракту там их нет:

| Поле | Где лежит на самом деле |
|:---|:---|
| маска карты | `order.srcToken.displayName` (§5.8.4), например `426863******3689`. Ключа `cardNumberMasked` в контракте нет вовсе |
| `rrn` | `order.trans[].rrn` или `order.lastTran.rrn` (§5.8.3, §5.8.5) |
| `approvalCode` | там же |

Фронт при этом полностью готов: `types/transaction.ts` объявляет все три, переводы для RRN
и кода одобрения есть на трёх языках, `cardLast4` вычисляется из маски
(`App.tsx:151`, `TransactionDetailPage.tsx:129`, `PayByLinkDetailPage.tsx:316`). То есть
правка чисто бэкендовая, интерфейс заполнится сам.

Выбор записи из `trans[]` придётся описать эвристикой: контракт различает операции по
`description` (`Purchase`, `Purchase - Void`, `Refund`), но **DMS в документе не описан**,
и какой `description` у авторизации и клиринга — неизвестно. Поэтому берём первую
не-реверсальную и не-возвратную запись, предпочитая `Purchase`, и самую раннюю по `regTime`.

Хранить отдельными колонками не нужно: `refreshStatus` выходит сразу на терминальных
статусах, значит payload успешной транзакции больше не перезаписывается, и чтение на лету
безопасно. Миграции нет.

`ProviderPayloads.withoutSecrets` вычищает только `password` верхнего уровня, так что
вложенный `srcToken` в базе сохраняется. Полного номера карты и CVV в нём нет — эквайер
отдаёт уже маскированный `displayName`.

### 19.08.2026 — P1-16 (сделано)

Сделано по ТЗ (Р-26): только три поля, только бэкенд, без колонок и миграции, фронт не тронут
(снимок `git status --porcelain frontend/` и SHA-256 от `git diff -- frontend/` до и после
правки совпадают).

- Новый `az.millikart.pbl.provider.ProviderOrderDetails` (без Spring, рядом с
  `ProviderOrderStatus` и `ProviderDeclineReason`): `read(order)` →
  `TransactionFacts(maskedCard, rrn, approvalCode)`. Маска — `order.srcToken.displayName`
  (§5.8.4), как есть. Запись для `rrn`/`approvalCode`: кандидаты из `order.trans[]` — карты,
  `isReversal` не `true` (boolean или строка `"true"`), `description` не `Refund` и без `Void`;
  если есть `description == "Purchase"` — только они; самая ранняя по `regTime`
  (строковое сравнение, в javadoc объяснено, почему не `LocalDateTime.parse`; запись с
  `regTime` бьёт запись без него, среди записей без — первая по порядку списка); ничего в
  `trans[]` — `order.lastTran` (§5.8.3) с теми же фильтрами; ничего — `null`/`null` и одна
  DEBUG-строка (`id` заказа, `trans`, `lastTran` — через `withoutSecrets`, под
  `isDebugEnabled()`, потому что это путь листинга). Защитный разбор на каждом шаге, ничего не
  бросает.
- `TxpgAcquiringClient.asText` → `ProviderPayloads.scalarText` (String/Number → текст,
  пустое/структура/boolean → `null`); `requireConfirmation` читает `ridByPmo`, `approvalCode`,
  `tranActionId` через него, приватного `asText` больше нет. Поведение клиента не изменилось —
  35 тестов `TxpgAcquiringClientTest` зелёные без правок.
- `PaymentLinkService.mapToTransactionResponse`: три чтения из корня заменены на
  `ProviderOrderDetails.read(resp)`; javadoc объясняет, почему без колонок (terminal-статусы не
  переопрашиваются, capture/возвраты кладут своё поверх) и почему `srcToken` в колонке допустим
  (уже маска, PAN/CVV эквайер не отдаёт).
- **Сверх ТЗ, только текст.** Комментарии `refreshStatus` (`SETTLED_OTHER`) и WARN в
  `reconcileOne`, а также javadoc теста `reconcile_olderThanMaxAge_providerSaysRefused_staysPending`
  обещали, что суммы внешних возвратов из `order.trans[]` прочитает P1-8b, потом P1-16. Ни та, ни
  другая этого не делают (Р-21 и Р-26 об этом не говорят). Ссылки заменены на новый
  `problems.md` §6 — там и сформулирована отдельная задача. Логика сверки, словарь статусов и
  денежные пути не тронуты; `TransactionReconciliationIntegrationTest` (18) зелёный без правок.
  Стаб (`StubAcquiringClient.getOrderStatus`) не менялся — он по-прежнему отвечает
  `{status, id}`, так что при локальной отладке со стабом три поля остаются пустыми; если
  нужно видеть их в UI без боевого шлюза — отдельная мелкая правка стаба (форма §5.8.6).
- Тесты: `ProviderOrderDetailsTest` — 22 (14 из ТЗ + `lastTran`-возврат, маска не
  переформатируется, пустой `approvalCode`, не-карты среди карт, `isReversal` строкой/мусором,
  `regTime` отсутствует/«yesterday», DEBUG-строка без пароля, тишина при успехе);
  `ProviderPayloadsTest` +4 метода / +15 запусков на `scalarText`; `PaymentLinkIntegrationTest`
  +2 (`checkStatus_fullPayload_returnsMaskedCardRrnAndApprovalCode`,
  `listTransactions_showsTheSameCardFactsAsTheStatusCard`). Payload'ы — из §5.8.3/§5.8.6
  дословно. Проверено обратно: со старым чтением из корня краснеют ровно два интеграционных.
  `./gradlew test` — 427 запусков (340 методов), 0 падений; `:pbl:test` — 285.
  Приёмочные grep'ы из ТЗ: 1 молчит, 2 находит `ProviderOrderDetails.read`, 3 — `scalarText` в
  `ProviderPayloads`, `ProviderOrderDetails` и `TxpgAcquiringClient`, приватного `asText` нет;
  4 — см. выше (в рабочем дереве лежат незакоммиченные правки фронта от P1-13/P1-14, поэтому
  сама команда из ТЗ их покажет — сравнение со снимком до правки подтверждает, что P1-16 фронт не
  трогал).
- Документация: `AGENTS.md` (§7 — таблица «где лежит», §10 — P1-16 в закрытых, «грабли», §11,
  подвал), `problems.md` §4 (DMS: `Purchase` — предпочтение, не фильтр; уточнить `description`
  у MilliKart) и новый §6, `pbl/pay-by-link.md` §5.8 (источник трёх полей), таблица спринта 1.

Спринт 1 закрыт полностью.

### 19.08.2026 — P1-16 ✅ (спринт 1 закрыт)

427 тестов зелёные (+39 к 388), прогон в 18:24 — после последней правки кода в 18:19.
Фронт не тронут: самый свежий файл во `frontend/` от 18.08 12:56, то есть со времён P1-13.

Замечаний нет. Разбор `trans[]` сделан ровно по описанной эвристике, включая тонкость,
которую я в ТЗ задал словами, а не кодом: запись с `regTime` выигрывает у записи без него,
а среди записей без `regTime` сохраняется порядок списка. `lastTran` проходит те же фильтры,
что и элементы `trans[]` — у возвращённого заказа последняя операция это возврат, и брать
из неё `rrn` покупки было бы ошибкой; на это есть отдельный тест
(`lastTranThatIsARefund_isNotTheSource`). Отладочный лог «ничего не нашлось» закрыт
проверкой `log.isDebugEnabled()` — метод вызывается на каждую строку списка транзакций.

**Мой промах в ТЗ.** Критерий приёмки №4 (`git status --porcelain frontend/`) в этом проекте
не работает: в сессии ничего не коммитится, поэтому он всегда показывает весь фронтенд из
P1-13/P1-14 и «чистым» быть не может. Проверять «не трогали ли фронт» надо по времени
изменения файлов, а не по git.

**Остаточный риск, честно записанный в `problems.md` §5** (не дефект этой задачи): если заказ
вернули на стороне эквайера уже после того, как транзакция дошла до `SUCCESS`, сервис этого
не заметит — терминальные статусы повторно не опрашиваются, и в портале платёж останется
оплаченным. Контракт даёт всё для обнаружения (`order.trans[]` с `Refund` и отрицательным
`clearAmount`), но это отдельная задача.

---

## Итог спринтов 0 и 1

Все девять блокеров и шестнадцать задач спринта 1 закрыты. Тестов: 20 → **427**.
Открытым остаётся хвост спринта 2 — P3 (гигиена репозитория); P2-1, P2-2, P2-3, P2-5, P2-6,
P2-8, P2-9, P2-10, P2-12, P2-13, P2-14 и P2-15 закрыты. Плюс список хвостов ниже.

---

## Спринт 2 — сверка с кодом 19.08.2026

Прошёлся по всем пунктам P2/P3 из ревью и проверил, что из них ещё живо.

**Закрылось попутно:** P2-4 (N+1 — в P0-1), P2-7 (строковые роли — в R-Role),
P2-11 (мёртвый код фронта — в P1-14).

**Открыто:**

| ID | Что | Проверено |
|:---|:---|:---|
| ~~P2-1~~ | `findAll()` без пагинации | **✅** — сделано 22.08.2026 (Р-44, Р-45): `listUsers`, `listCompanies`, `listTerminals` принимают `Pageable` и отдают `PagedResponse` из `common`; `page`/`size` = `0`/`20`, потолок 200, значения приводятся; сортировка с уникальным довеском (`username, id`; `name, id`); фильтр soft-deleted — в запросе; ролевые правила не тронуты. Вместе с API — экраны (`TablePagination` на трёх страницах) и лёгкий `GET /api/v1/terminals/options`. Непагинированных листингов в проекте не осталось |
| ~~P2-2~~ | Аудит: ни одного индекса в схеме `directory`, фильтрация в памяти | **Сделано 21.08.2026** (с P2-5, P2-6): changeset 004 — три индекса (`(company_id, created_at desc)`, `(entity_type, entity_id)`, `(created_at desc)`); фильтрация, пагинация и порядок `createdAt DESC` — в базе, `stream().filter` убран; `PagedResponse` переехал в `common` |
| ~~P2-3~~ | Нет индекса на `transactions.link_id` | **✅** — сделано 22.08.2026: changeset `pbl/007-transaction-indexes.xml`, три индекса — `(link_id, status)`, `(link_id, created_at desc)`, `(status, created_at)`. Оказалось шире исходной формулировки: не покрыта была и выборка фоновой сверки, которая идёт каждые две минуты круглосуточно |
| ~~P2-5~~ | Аудит откатывается вместе с бизнес-операцией | **Сделано 21.08.2026** (Р-35): успех — `AuditEvent` + `AuditLogWriter` (`AFTER_COMMIT`, `fallbackExecution`) → `recordSuccess` (`REQUIRES_NEW`); отказ — `logDenied` (`REQUIRES_NEW`) во всех точках отказа `directory`, `outcome = DENIED`; сбой записи журнала не роняет операцию (маркер `AUDIT_WRITE_FAILED`) |
| ~~P2-6~~ | Нет IP в аудите | **Сделано 21.08.2026** (Р-36): `client_ip varchar(45)` + `ClientIpFilter`/`ClientIpHolder` в `common` поверх `ClientIp.resolve`; вне запроса — `null`, это штатно |
| ~~P2-8~~ | Удаление терминала с FK → 500 | **Сделано 21.08.2026** (Р-37…Р-40): удаления терминалов больше нет — `status` `ACTIVE`/`BLOCKED` через тот же `PATCH`, `DELETE` отвечает 405; блокировка переводит `ACTIVE`-ссылки терминала в `SUSPENDED` одним `UPDATE` в той же транзакции, разблокировка возвращает их в `ACTIVE` (просроченные — в `EXPIRED`); возвраты, capture DMS и сверка не тронуты (Р-38) |
| ~~P2-9~~ | `update()` ссылки меняет сумму при идущих платежах и воскрешает просроченную | **Сделано 20.08.2026** (Р-31, Р-32): `AMOUNT_LOCKING_STATUSES` + `ALLOWED_STATUS_TRANSITIONS` в `PaymentLinkService`, `maxPayments` не ниже прошедших платежей, одна строка аудита на правку |
| ~~P2-10~~ | `X-Forwarded-For` без доверенного прокси | **Сделано 19.08.2026** вместе с P3-Auth (Р-29): `ClientIp.resolve` в `common`, локальный `extractClientIp` удалён |
| ~~P2-12~~ | Статистика транзакций всегда нули | **Сделано 20.08.2026** (Р-30): во фронтовом `TransactionStatus` остались шесть значений бэкенда, разбор на границе (`parseTransactionStatus`) вместо `as any` и `\|\| 'APPROVED'`; `tsc -b` теперь ловит сравнение с несуществующим статусом |
| ~~P2-13~~ | Отмена ссылки «удаётся» всегда; словарь статусов ссылки не сведён с бэкендом | **Сделано 21.08.2026** (Р-33, Р-34): `LINK_STATUSES`/`LINK_USAGE_TYPES`/`PAYMENT_TYPES` — значения бэкенда, разбор на границе (`parseLinkStatus` и соседи) вместо `(l.status \|\| 'active').toLowerCase()`; `catch`, повторявший ветку успеха, убран — при отказе показывается текст бэкенда, состояние в обоих случаях перечитывается с сервера; мок-генератор `generateLinks` удалён |
| ~~P2-15~~ | Дата оплаты ссылки не отдаётся; на карточке — генератор выдуманных транзакций | **✅** — сделано 22.08.2026 (Р-46…Р-48): `lastPaidAt` в обоих ответах (`PAID_STATUSES` = `SUCCESS` + `REFUNDED` + `PARTIALLY_REFUNDED`), список — один пакетный запрос на страницу; в списке «Оплачена» только у `COMPLETED`; построитель выдуманных транзакций удалён **до** включения поля. Пункта в `code_review.md` у задачи нет — она выросла из решений, а не из ревью |
| ~~P2-16~~ | Возврат отменяет факт использования ссылки: счётчик падает, слот освобождается, частичный возврат стирает платёж целиком (`problems.md` §16) | **✅** — сделано 24.08.2026 (Р-49, Р-50): `PAID_STATUSES` переехал в `TransactionStatus` и считает `currentPaymentsCount` (и колонку), слоты (`+ AUTHORIZED`, P1-6), проверки открытия и запрет понижать `maxPayments`; одиночный `countByLinkIdAndStatus` удалён из репозитория; новое поле `refundedPaymentsCount` только в одиночном ответе; на карточке «из них возвращено: K» при K > 0, три языка. Пункта в `code_review.md` у задачи нет (закрыт лишь пункт §5 про непоследовательную колонку) — задача выросла из `problems.md` §16 и решений |
| ~~P3-1~~ | Поиск на трёх справочных экранах фильтрует одну загруженную страницу (`problems.md` §14); у журнала аудита нет ни пагинации (обрезка на 200), ни поиска; фильтр журнала по одному `entityType` молча игнорируется; сортировка журнала без уникального довеска | **✅** — сделано 24.08.2026: `search` у всех четырёх списков (users — включая название компании нативным join'ом в таблицу `directory`, `problems.md` §18; terminals — включая название компании обычным JPQL), `role` у users, `outcome`/`from`/`to` и независимые `entityType`/`entityId` у журнала (D.1), сортировка журнала `createdAt DESC, id DESC` (D.3), индекс не расширялся — решение комментарием (D.4); экранирование `%`/`_` через `SearchTerms` (`common`), без `pg_trgm` намеренно; фронт — debounce 300 мс, сброс страницы, `AbortController`, `TablePagination` на `AuditLogsPage`, колонки «Результат»/«IP», `AUTH`/`AUDIT_LOG` в фильтре, `searchOnPage` и все `filtered*` удалены. Пункта в `code_review.md` у задачи нет — задача из ТЗ |
| ~~P3-1a~~ | Ветка «`COMPANY_HEAD` без компании» в `UserService.listUsers` не покрыта тестом: P3-1 перевернул смысл `null` (`company_id = NULL` не матчил никого → `:companyId IS NULL` значит «все»), при следующей правке запроса защита отвалилась бы молча | **✅** — сделано 24.08.2026: `companyHeadWithoutCompany_seesNobody_notEveryone` в `UserListPaginationTest` — 200 с пустой страницей (не 403), сеется и пользователь без компании, `search`/`role` обходом не становятся; доказательность проверена руками (без ветки тест красный). Пункта в `code_review.md` нет — задача из ТЗ |
| ~~P3-2~~ | Словарь аудита разъехался: `entityId` у `AUTH` — три разных смысла, `ACCESS` против `READ` на один отказ, приватные константы и литералы, logout/блокировка компании не журналируются, неизвестный исход у эквайера записан как `SUCCESS` | **✅** — сделано 24.08.2026: `AuditEntity`/`AuditAction` в `common/audit`, все ~40 вызовов на константах, литералов и приватных `*_ENTITY` нет; `ACCESS` → `READ`; `entityId` у `AUTH` — всегда логин (вход, `RATE_LIMIT`, `TOKEN_REUSE`); logout пишет `AUTH/LOGOUT` только при реальном отзыве; `COMPANY/BLOCK`\|`UNBLOCK` отдельными событиями; `AuditOutcome.UNRESOLVED` у `logUnresolved`; значение вне словаря — WARN `AUDIT_OUTSIDE_DICTIONARY`, не исключение; таблица 30 сочетаний в `technical_handover.md` §4.4. Пункта в `code_review.md` у задачи нет — задача из ТЗ, не из ревью |
| ~~P3-4~~ | Комментарии в java разрослись: 30% в `main` (2723 на 6433 строки кода) и 17% в тестах, 86% из них — блочные `/** */`, 279 строк только HTML-разметки, в 20 файлах из 121 комментариев больше, чем кода | **✅** — сделано 24.08.2026: форма `//`, по-русски, ≤4 строк на блок (правило в `AGENTS.md` §8); `main` 2723 → 1082 (14,4%), тесты 1932 → 1085 (10,1%). Ни одной строки кода не изменено — доказано построчным сличением с индексом git; тесты 621, счётчик тот же. Тринадцать фактов «нельзя потерять» проверены поимённо. Пункта в `code_review.md` у задачи нет — задача из ТЗ |
| ~~P3-3~~ | Гигиена репозитория: 178 файлов мусора в индексе (`build/` со 126 МБ jar'ов, `.gradle/`, `.idea/`, дублирующие gradle-обёртки), `.gitignore` из двух заякоренных к корню строк, нет `.gitattributes` (фантомный diff `gradlew.bat`), README из двух одинаковых строк | **✅** — сделано 24.08.2026: `.gitattributes` + renormalize, `.gitignore` переписан (`build/`/`.gradle/` без слэша, `.idea/*` + `!checkstyle-idea.xml`), мусор раскоммичен, обёртки удалены с диска, README и `.env.example` написаны. Историю не переписывали, секреты не трогали (их в раскоммиченном и не было), коммитов нет — порядок за заказчиком. `./gradlew cleanTest test` — 621 зелёный, `npm run build` — чисто. Отмечены ✅ три пункта `code_review.md` §5 (+ README частично: имя `frontend/package.json` не менялось — в ТЗ не было) и п. 16 в §8 |
| ~~P3-6~~ | `SettingsPage` (1357 строк) показывает как рабочие настройки, которых нет: «2FA включена», «тайм-аут сессии 30 минут», «3-D Secure обязателен» — три ложных утверждения о безопасности; вебхуки на `api.acmecorp.com` из шаблона; `handleSave` только зажигает зелёную полосу; две недостижимые панели с кнопкой удаления терминала (запрещено с P2-8) | **✅** — сделано 24.08.2026 (Р-55): удалены вкладки Security/Payment/API, выдуманные поля Account и Display, недостижимые панели `terminals`/`companies` и их диалоги, 113 строк закомментированного `notifications`, `handleSave` и три запроса в никуда; вкладок больше нет — страница плоская, 1357 → **185** строк. Живых контрола два: название компании (`GET`/`PATCH /api/v1/companies/{id}` по claim'у `companyId` вместо страницы списка — тот отвечал 403 и писал отказ в журнал всем, кроме `SYSTEM_ADMIN`/`AUDITOR`) и язык. Сверх ТЗ: «Сохранить» больше не врёт — зелёная полоса только на 2xx, и `list[0]` (переименование чужой компании) убран. Блок `settings` в `translations.ts` переписан на три языка — девять живых ключей. Закрыт хвост `problems.md` §14. Пункта в `code_review.md` у задачи нет — задача из ТЗ |
| ~~P3-7~~ | Главная страница считает аналитику в браузере по **двадцати** строкам (обе выборки без `page`/`size`, у контроллеров `defaultValue = "20"`), а подписывает результат как «All system transactions» и «Real…»; поверх — константа «Avg Processing Speed 1.2s», доли 60/40 из воздуха, подстановки адреса и браузера, сочинённая история статусов при клике, тренд «за 7 дней» по названию дня недели, сложение разных валют и выручка как `SUM(amount)` по `SUCCESS` (мимо `captured_amount` и `PAID_STATUSES`) | **✅** — сделано 24.08.2026 (Р-56, ТЗ в `P3-7.md`): `GET /api/v1/dashboard/summary` в `pbl` — окно `from`/`to` (умолчание 7 суток, потолок 92 дня, превышение → 400, не зажим), пояс `pbl.dashboard.zone` = `Asia/Baku` в ответе, деньги по валютам, три группировки, доступ из наборов `PaymentLinkService` (`READ_ROLES`, `isGlobalReader` стали `public`), нет компании → нули и 200. Плюс недостающий `GET /api/v1/transactions/{id}` (без него не убрать сочинённый `statusHistory`), **порядок у списка транзакций** (`createdAt DESC, id DESC` — его не было вовсе) и changeset `008` с `idx_transactions_created`. `HomePage` 818 → 435 строк, блок `home` в `translations.ts` переписан на три языка. `DashboardSummaryTest` (17), тесты 542 метода / 638 запусков. Пункта в `code_review.md` у задачи нет — она выросла из разбора главной страницы |
| ~~P3-5a~~ | Отмена ссылки из списка (`PayByLinkPage.tsx:645`) уходила на сервер по клику по иконке — пробел, найденный при проверке P3-5 (Р-55); пять денежных диалогов написаны по-английски мимо `translations.ts`; окно возврата средств озаглавлено `Cancel Transaction` и про возврат не говорит, хотя зовётся `POST /transactions/{id}/refund` и в журнал идёт `REFUND` | **✅** — сделано 25.08.2026: отмена из списка идёт через диалог (`cancelTarget: PaymentLink`, не id — окну нужны `shortCode` и сумма), слово в слово тот же, что на карточке; задействованы семь простаивавших переведённых ключей (`cancelLinkAction`, `cancelConfirmTitle`/`Text`, `cancelLink`, `finalizeDMS`, `refundAction`/`refundTitle`/`confirmRefund`), заведены восемь новых на три языка; окно возврата переименовано в возврат; сумма вынесена из фразы в рамку (подстановок словарь не умеет); `*Busy` гасит обе кнопки на время запроса, фокус — на безопасной. Бэкенд не тронут — 638 тестов зелёные. Пункта в `code_review.md` у задачи нет — она выросла из проверки Р-55 |
| ~~P3-5b~~ | Девять окон подтверждения в шести файлах (`UsersPage`, `CompaniesPage`, `TerminalsPage`×2, `PayByLinkPage`, `PayByLinkDetailPage`×2, `TransactionDetailPage`×2) — каждое работает правильно, но три правила P3-5 записаны в девяти копиях: десятый диалог напишут, забыв одно из трёх, и никто не заметит. Видно уже сейчас: `autoFocus` стоит в пяти окнах из девяти, в остальных четырёх Enter подтверждает опасное действие | **✅** — сделано 25.08.2026: `app/components/ConfirmDialog.tsx` (96 строк) — четыре правила записаны один раз (`onClose` глух при `busy`, кнопка отказа первая и с `autoFocus`, обе `disabled={busy}`, подтверждение `contained` и цветное); всё, что различается по содержимому, ушло в `children`, поэтому ни один из девяти не потребовал особого случая; `CompaniesPage` остался одним вызовом на два действия. `<DialogActions>` в `pages` 15 → 6 (остались шесть форм), `autoFocus` в `pages` — 0. Единственное намеренное изменение поведения — `autoFocus` в четырёх окнах, где его не было. Плюс два английских ключа на три языка (`payByLink.shareDialogTitle`, `terminals.registerAction`). Бэкенд не тронут — 638 тестов зелёные. Пункта в `code_review.md` у задачи нет — она выросла из отклонения №1 Р-55, отложенного в P3-5a |
| P3 | Гигиена репозитория, мелочи (остаток) | см. `code_review.md` §5. Безопасность входа выделена в P3-Auth и **сделана 19.08.2026**; чистка индекса, обёртки, README — **P3-3, сделано 24.08.2026**. Остаются: `directory/settings.gradle`, неиспользуемый `springBootVersion` в `directory/build.gradle`, `RestTemplate` bean, `ConflictException`, маппинг `InvalidStateException → 403`, имя `@figma/my-make-file` во `frontend/package.json` |

**Переоценка приоритета.** Два пункта из P3 — перечисление аккаунтов и отсутствие лимита
попыток входа по IP — на деле не мелочи. Блокировка аккаунта после 6 неудач при отсутствии
лимита по IP означает, что любой, кто знает email пользователя, выключает его за минуту.
Для портала мерчантов это способ отключить конкретного клиента. Взяты в работу первыми.

### 19.08.2026 — Безопасность входа (ТЗ)

Решения Р-27, Р-28, Р-29.

`AuthService.login` (:58-112) проверяет статус аккаунта (:68) и блокировку (:74) **до**
проверки пароля, и оба ответа называют причину: «User account is blocked», «Account is
locked…». То есть существование пользователя и его состояние узнаются без единого верного
пароля. Плюс несуществующий пользователь отваливается на :62 вообще без вызова bcrypt —
это разница в ~100 мс, по которой существование определяется даже при одинаковых текстах.

Лимита попыток по IP нет нигде. И пока `extractClientIp` берёт первый элемент
`X-Forwarded-For`, любой лимит по адресу обходится одним заголовком — поэтому Р-29.

### 19.08.2026 — P3-Auth (сделано)

Решения Р-27, Р-28, Р-29. Заодно закрыт P2-10. Фронтенд не тронут (ни одного файла под
`frontend/` с изменённым временем правки; лежащие там незакоммиченные изменения — от P1-13/P1-14).

- **`az.millikart.common.web.ClientIp`** (без Spring) — единственный источник адреса клиента.
  Правило ровно под нашу схему: пир не в доверенных → заголовки игнорируются целиком, возвращается
  `getRemoteAddr()`; доверенный пир → `X-Real-IP`, иначе **последний** элемент `X-Forwarded-For`,
  иначе адрес пира. Значение обязано разбираться как IP-литерал не длиннее 45 символов, иначе
  берётся адрес пира: оно становится ключом кэша в лимитере, и 4 КБ заголовка не должны его
  раздувать. Сверх ТЗ (мелочь, но существенная): `InetAddress.getByName` вызывается только на
  строке, которая физически не может оказаться именем хоста (цифры и точки — или шестнадцатеричные
  цифры, двоеточия и точки), иначе подделанный заголовок превращал бы каждую попытку входа в
  DNS-запрос. Сравнение с доверенным списком — по канонической форме, поэтому `::1` в настройке
  совпадает с пиром `0:0:0:0:0:0:0:1`.
- **`TrustedProxies`** (`common`, `@Component`) — `mp.trusted-proxies` из
  `${TRUSTED_PROXIES:127.0.0.1,::1}`, объявлена в `application.yaml` `auth` и `pbl` (и в обоих
  тестовых профилях). Пустой список = заголовкам не верим никогда; в javadoc написано, что список
  расширяют только при появлении второго прокси.
- **`TooManyRequestsException`** (`common`) + `@ExceptionHandler` → **429** с `Retry-After`
  в секундах (не меньше 1). Тело — обычный `ErrorResponse`, без «сколько попыток осталось».
  `GlobalExceptionHandler.build` разделён на `build` и `body`, чтобы ответ с заголовком собирался
  из того же тела.
- **`az.millikart.auth.security.LoginRateLimiter`** — Caffeine `адрес → неудачи`,
  `expireAfterWrite(window)`, потолок 100 000 адресов (переполнение вытесняет по LRU, то есть
  fail-open — в javadoc сказано прямо). Настройки `auth.login.rate-limit.{enabled,max-failures,window}`
  = `${LOGIN_RATE_LIMIT_*}`, по умолчанию 10 / PT15M; без дефолтов в `@Value` (как
  `RefreshTokenService`) — конфигурация обязана быть в yaml. Считаются только неудачи, успешный
  вход обнуляет счётчик, выключенный флаг отключает механизм целиком. Пакетный конструктор
  принимает `Ticker` — так тест проходит окно без `sleep`.
- **`AuthService.login(request, clientIp)`** — новый порядок: лимит → поиск пользователя (нет —
  сравнение с фиксированным bcrypt-хэшем-заглушкой и общая ошибка) → пароль → неверен: счётчик
  аккаунта + счётчик адреса + общая ошибка → верен и заблокирован: про блокировку со временем →
  верен и статус не `ACTIVE`: «Account is not active. Please contact your administrator.» → успех:
  оба счётчика сброшены. Порог 6/30 минут не тронут (Р-28), литералы `attempts >= 6` и
  `30, ChronoUnit.MINUTES` оставлены на месте — приёмочный grep из ТЗ их находит.
  **Решение сверх ТЗ:** попытки, сделанные во время действующей блокировки, счётчик аккаунта не
  наращивают и блокировку не продлевают. Раньше до этого места вообще не доходило (блокировка
  проверялась до пароля), а с новым порядком «продлевать на каждой попытке» означало бы, что
  аккаунт держат закрытым бесконечно — то самое, от чего задача и защищает.
- **Логи входа** несут разрешённый адрес и нормализованный `cleanEmail` (раньше в разных строках
  было по-разному: то `cleanEmail`, то `request.username()`).
- **`pbl`**: `OpenLinkController.extractClientIp` удалён, зовётся `ClientIp.resolve` — P2-10.
  В `transactions.client_ip` пишется то же поле, но уже не выбранное плательщиком.
- **Тесты.** `ClientIpTest` (`common`, без Spring) — 9 (7 из ТЗ + одноэлементный
  `X-Forwarded-For` + «мусор в `X-Real-IP` не отменяет чтение `X-Forwarded-For`»);
  `LoginRateLimiterTest` (`auth`, без Spring) — 6 (5 из ТЗ + отказ на бессмысленной конфигурации);
  `AuthIntegrationTest` — +9 (13–20 из ТЗ и «заблокированный с верным паролем узнаёт о блокировке»,
  парный к 16). Каждый новый интеграционный тест ходит со своего `remoteAddr`: это и изоляция от
  общего контекста (лимитер — синглтон на все классы), и причина, по которой заголовки в тесте 19
  не считаются доверенными — `127.0.0.1` как раз доверенный.
  **Пункт 21 ТЗ («существующие проверки блокировки остаются зелёными без правок») выполнен не
  буквально, и не мог быть:** он противоречит пункту 16. Существующий
  `testAccountLockout_After6FailedAttempts` ожидал, что 7-я попытка с **неверным** паролем ответит
  «Account is locked» — это и есть устраняемая утечка. Изменено одно ожидание (теперь общий отказ),
  а сам механизм проверяется строже прежнего: `failed_login_attempts = 6` и `lockout_until` ≈ +30
  минут читаются из базы, 7-я попытка блокировку не продлевает, и отдельный тест показывает
  «Account is locked… Please try again in N minutes» на **верном** пароле.
  `./gradlew test` — 451 запуск (364 метода), 0 падений; `:common` 74, `:auth` 65, `:directory` 27,
  `:pbl` 285. Приёмочные grep'ы 1–5 из ТЗ — как в ТЗ.
- **Документация**: `.env.example` (`TRUSTED_PROXIES`, три `LOGIN_RATE_LIMIT_*`, отдельный абзац
  про общий внешний IP офиса — поднимать порог, а не выключать механизм), `deployment_guide.md`
  §11.1 (врезка: `X-Real-IP` теперь источник адреса, строку из `location` убирать нельзя, второй
  прокси — в `TRUSTED_PROXIES`), `AGENTS.md` (§10 закрытые + «грабли» про `ClientIp.resolve`, §11
  таблица и итоги, подвал), `problems.md` §7 (остаточная утечка) и §8 (`/auth/refresh` не
  лимитируется — и почему это опасно не сегодня, а при следующем публичном эндпоинте), таблица
  спринта 2 (P2-10 и P3).

### 19.08.2026 — P3-Auth ✅

Перечисление аккаунтов и отсутствие лимита попыток входа закрыты, P2-10 закрыт вместе с ними.
Из спринта 2 остаются P2-1, P2-3 и хвост P3 (гигиена
репозитория). *(P2-12 закрыт 20.08.2026, P2-13 — 21.08.2026, P2-1 и P2-3 — 22.08.2026;
на момент этой записи все были открыты. Из спринта 2 остаётся только хвост P3.)*

### 19.08.2026 — Безопасность входа ✅ (P2-10 закрыт заодно)

451 тест зелёный (+24 к 427), прогон в 19:14 — после последней правки кода в 19:08.

Порядок проверок в `login` переставлен ровно как в ТЗ: лимит по IP → поиск → bcrypt (для
несуществующего пользователя — против константного хэша, чтобы время совпадало) → и только
после верного пароля разговор про блокировку и статус. Несуществующий пользователь и неверный
пароль дают одинаковый текст и одинаковый 400. Лимит — 429 с `Retry-After`.

**Две вещи сверх ТЗ, обе существенные.**

`registerFailedAttempt` **не увеличивает счётчик аккаунта, пока блокировка уже активна**.
Я этого не написал, а без этого дыра осталась бы в другом виде: атакующий, продолжая стучать,
переставлял бы `lockout_until` вперёд и держал бы чужой аккаунт закрытым бесконечно. Теперь
тридцать минут отсчитываются от шестой неудачи, а не от последнего стука постороннего.

В `ClientIp` — защита от DNS. `InetAddress.getByName` на строке вроде `a.b` уходит к
резолверу, то есть подделанный заголовок превращал бы каждую попытку входа в DNS-запрос.
Перед разбором стоит проверка, что строка состоит только из символов IP-литерала. Тем же
методом решена канонизация: `::1` в настройках совпадает с `0:0:0:0:0:0:0:1` от контейнера.

Отдельно проверил транзакционность: `login` помечен
`@Transactional(noRollbackFor = BusinessException.class)`, поэтому увеличенный счётчик
неудач переживает выброшенное исключение, а сброс просроченной блокировки долетает до базы
через dirty checking, без явного `save`. Замечаний нет.

**P2-10 закрыт вместе с этим:** `OpenLinkController` больше не разбирает `X-Forwarded-For`
сам, адрес берётся через `ClientIp.resolve` — из `X-Real-IP`, а при его отсутствии из
**последнего** элемента `X-Forwarded-For`, и только если запрос пришёл от доверенного прокси.

### 19.08.2026 — P2-12 и P2-9 (ТЗ)

Решения Р-30, Р-31, Р-32.

**P2-12 оказался шире, чем «нули в статистике».** `App.tsx:147` кладёт статус как
`String(t.status || 'APPROVED').toUpperCase() as any`. До интерфейса доходят настоящие
`PENDING` / `AUTHORIZED` / `SUCCESS` / `FAILED` / `PARTIALLY_REFUNDED` / `REFUNDED`, а фронт
написан под чужой словарь: `APPROVED`, `DECLINED`, `3d-failed`, `success`, `canceled`.
Тип `TransactionStatus` перечисляет одиннадцать значений из трёх словарей, и **`SUCCESS`
с `AUTHORIZED` в нём нет вовсе** — `as any` это скрывает.

Ломается не только статистика: цвета и подписи в `TransactionTable` (`case 'APPROVED'`,
`case 'DECLINED'`), фильтр по статусу (`TransactionListPage:58` — точное сравнение),
разметка в `TransactionDetailPage:62`. `PayByLinkDetailPage:303` переводит настоящий
`FAILED` обратно в выдуманный `3d-failed`. Дефолт `|| 'APPROVED'` подставляет значение,
которого бэкенд не шлёт никогда.

**P2-9.** `update()` меняет `amount` без единой проверки (:159-161) и разрешает
`ACTIVE` из любого статуса (:200-206) — просрочка и исчерпанный лимит воскресают.
Заодно `maxPayments` можно опустить ниже числа уже прошедших платежей.


### 20.08.2026 — P2-9 ✅

473 теста зелёные (+22 к 451): `:common` 74, `:auth` 65, `:directory` 27, `:pbl` 307.
Приёмочные grep'ы из ТЗ — оба находят своё (`AMOUNT_LOCKING_STATUSES` в `PaymentLinkService:156`,
`existsByLinkIdAndStatusIn` в `TransactionRepository:42`).

Сделано ровно по ТЗ, решения Р-31 и Р-32 не пересматривались:

- **Сумма.** `AMOUNT_LOCKING_STATUSES` = `PENDING`, `AUTHORIZED`, `SUCCESS`, `PARTIALLY_REFUNDED`,
  `REFUNDED` — перечислением, с комментарием, почему не `!= FAILED`. Проверка — новым
  `TransactionRepository.existsByLinkIdAndStatusIn`, то есть запросом, а не выборкой в память.
- **Переходы.** `ALLOWED_STATUS_TRANSITIONS` — `Map<PaymentLinkStatus, Set<PaymentLinkStatus>>`:
  `ACTIVE → CANCELED`, `EXPIRED → CANCELED`, `CANCELED → ACTIVE`, у `COMPLETED` пустой набор.
  Отдельным методом `applyStatusChange`: сначала «тот же статус — не изменение», потом таблица,
  потом срок для реактивации.
- **`maxPayments`** сверяется с `countByLinkIdAndStatus(id, SUCCESS)`.
- **Аудит.** Список `changes` собирается по ходу метода и уходит одной строкой `log.info`
  со старыми и новыми значениями; прежние `log.debug` по полям убраны.

**Три решения сверх буквы ТЗ, все три мелкие и все три осознанные.**

`countByLinkIdAndStatus(id, SUCCESS)` считается **один раз** в начале метода и используется
и проверкой `maxPayments`, и маппером ответа. Раньше он стоял в конце; запрос как был один,
так и остался.

Сравнение сумм — `compareTo`, не `equals`: `BigDecimal("100.0").equals(new BigDecimal("100.00"))`
даёт `false` из-за масштаба, и PATCH с «той же» суммой из портала (`100.0` против `100.00`
в базе) получал бы 400 на ровном месте. ТЗ говорит «равна текущей» — равенство здесь численное.

Все поля, а не только сумма и статус, теперь применяются лишь при реальном изменении
(`description`, `metadata`, `expiresAt`, `maxPayments`, три поля плательщика). Иначе строка
аудита перечисляла бы всё, что было в теле запроса, а не то, что правка изменила, — то есть
ровно ту задачу, ради которой она заводилась, и не решала бы. Персональные данные попадают
в неё только именами полей (`customerEmail`), без значений.

**Что специально не делали:** отдельной таблицы истории изменений ссылки (это своя задача —
`problems.md` §9); валидацию `expiresAt` (P1-9) не трогали; фронтенд не трогали — форма
редактирования получает 400 и показывает текст ошибки.

**Документация:** `AGENTS.md` (§10 закрытые блокеры + «грабли» про заморозку суммы и порядок
полей в `update`, §11 таблица и итоги, подвал), `fix_plan.md` (строка спринта 2 + эта запись),
`code_review.md` (§4 и §8), `problems.md` §9, `pbl/pay-by-link.md` §5.2 (три новых отказа PATCH:
контракт эндпоинта там уже описан, и без них он врал бы интегратору).


### 20.08.2026 — P2-12 ✅

Решение Р-30. Правка целиком во фронтенде, бэкенд не тронут — словарь статусов там правильный.

**Чинили причину, а не сравнения.** Симптом в ревью был описан как «регистр статусов»: фильтр
и `StatsOverview` сравнивают `'success'` с приходящим `'SUCCESS'`. На деле дело не в регистре.
`types/transaction.ts` перечислял **одиннадцать** значений из трёх разных словарей — `APPROVED`,
`DECLINED`, `CANCELED`, `success`, `pending`, `canceled`, `3d-failed` — и при этом `SUCCESS`
и `AUTHORIZED` в типе не было вовсе. `App.tsx:147` клал статус как
`String(t.status || 'APPROVED').toUpperCase() as any`, и `as any` не давал компилятору увидеть
ни одного расхождения.

Поэтому сначала убрали заглушку, а список правок дал сам `tsc -b`:

1. `TRANSACTION_STATUSES` — шесть значений, зеркало `pbl/.../domain/TransactionStatus.java`.
   `PaymentMethod` сведён к `SMS`/`DMS` (`PaymentType.java`).
2. `parseTransactionStatus` / `parsePaymentMethod` — зеркало `parseRole` из P1-13: строгое
   сравнение, никогда не бросает, незнакомое значение → `null` плюс предупреждение в консоль
   с исходной строкой. Никакой подстановки: `|| 'APPROVED'` был не опечаткой, а способом
   назвать «успешной» транзакцию с любым нераспознанным статусом.
3. `as any` снят в обоих мапперах ответа (`App.tsx`, `TransactionDetailPage` — второй такой же
   маппер, в ТЗ он не значился) и во всех местах, до которых дошли.
4. Неизвестный статус не прячется: `Transaction.statusRaw` хранит исходное значение, строка
   в таблице показывается серым (`colorSchemes.neutral`) с ним же.
5. `StatsOverview`: выручка по `SUCCESS`; успешные — `SUCCESS`, ожидающие — `PENDING` +
   `AUTHORIZED`, неуспешные — `FAILED`, возвращённые — `REFUNDED` + `PARTIALLY_REFUNDED`.
6. `i18n/translations.ts`: `transactions.statuses` объявлен как `Record<TransactionStatus, string>`
   — подписи ко всем шести на en/az/ru, и новый статус потребует их во всех трёх языках.

**Четыре решения сверх буквы ТЗ, все осознанные.**

`Transaction.statusRaw` — отдельное поле. ТЗ говорит «в таблице такая строка показывается
как есть»; без исходного значения показать было бы нечего, а класть непрошедшую разбор строку
обратно в `status` — вернуть ровно ту дыру, которую закрываем.

`AUTHORIZED` покрашен в `info`, а не в тот же `warning`, что `PENDING`. В счётчике они вместе
(«в процессе»), но в таблице это разные вещи: по одной деньги захолдированы, по другой — нет.

`transaction.status !== 'canceled'` в `TransactionDetailPage` (гейт блока действий) переведён
в `!== 'FAILED'` по таблице соответствий из ТЗ. Раньше условие было истинным **всегда** —
статуса `'canceled'` не существовало, — так что «Cancel & Refund» предлагался и по неуспешному
платежу. Теперь не предлагается.

`PayByLinkDetailPage` и `HomePage`: из их локальных карт статусов убраны `COMPLETED`,
`CANCELED`, `CANCELLED` — тот же выдуманный словарь, только не типизированный, поэтому
компилятор их не показывал. `HomePage` заодно перестал перебирать `PROCESSING`, `INIT`,
`DECLINED`, `ERROR`, `3D-FAILED`, `EXPIRED` и разбирает статус один раз на транзакцию,
а не по четыре раза (иначе предупреждение о неизвестном статусе печаталось бы четырежды).

**Что специально не делали:** словарь статусов **ссылки** (`LinkStatus` в `payByLinkData.ts`:
`'active' | 'paid' | 'completed' | 'expired' | 'cancelled' | 'canceled'` против
`PaymentLinkStatus` на бэкенде) — та же болезнь, но другой словарь и другая задача,
записана в `problems.md` §10. Заголовки карточек `StatsOverview` оставлены английскими,
как и весь остальной текст этого компонента, — переводы заведены для подписей статусов.

**Проверка:** `npm run typecheck`, `npm run lint` (0 ошибок, 71 предупреждение — все
пред­существующие, ни одного в правленых файлах), `npm run build` — зелёные. Гейт проверен
отдельно: временный файл со сравнением `t.status === 'APPROVED'` даёт
`TS2367: … '"APPROVED"' have no overlap`. Тестов на фронтенде нет — остальное ручным прогоном
в браузере.

**Документация:** `AGENTS.md` (§10 закрытые блокеры + «грабли» про словари бэкенда во фронтенде,
снята устаревшая строка про мёртвые `getStatusColor`/`getStatusIcon` и переуказан хвост P1-13,
подвал), `fix_plan.md` (строка спринта 2 + эта запись), `code_review.md` (§4 и §8),
`problems.md` §10, `.agents/workflows/frontend.md` (§4.6 и карта файлов). §11 `AGENTS.md`
не тронут: тестов задача не добавляет, их на фронтенде нет.


### 20.08.2026 — переключатель провайдера ✅ (не нумерованная задача, всплыло при запуске)

`pbl` не стартовал: «Parameter 0 of constructor in `OpenLinkService` required a bean of type
`AcquiringClient` that could not be found».

Причина не в `OpenLinkService` — он просто первый, кому бин понадобился. Выбор реализации делали
два `@ConditionalOnProperty` на самих клиентах: `havingValue = "true"` на `StubAcquiringClient`,
`havingValue = "false", matchIfMissing = true` на `TxpgAcquiringClient`. Дополняют друг друга они
только для строк `true` и `false`: свойство, заданное чем-то ещё, не подходит ни одному условию
(`matchIfMissing` не при чём — свойство присутствует), и `AcquiringClient` не создаётся вовсе.
Промахнуться просто — `--pbl.provider.stub` без `=true` доезжает до Spring как свойство с пустым
значением, пустая переменная окружения выглядит так же.

Сделано: выбор вынесен в `AcquiringClientConfig` — один `@Bean`, обычный `if`, обе реализации
перестали быть `@Component`. Непонятное значение флага роняет старт с объяснением, в стиле
`UrlConfigurationCheck` и `JwtProvider`: назван флаг, названо значение, сказано, что выбирает
каждое из двух допустимых.

**Отступление от того, что я предложил в переписке.** Я обещал `@ConditionalOnMissingBean`
на `TxpgAcquiringClient` — так короче, но на сканируемых `@Component` результат зависит от порядка
сканирования классов, и Spring прямо рекомендует эту аннотацию только для автоконфигураций.
Цена ошибки здесь несимметрична: промах в одну сторону — «бина нет» (шумно), в другую — локальный
отладочный запуск молча уходит в боевой эквайринг. Поэтому явная фабрика, а не порядок сканирования.

`Boolean.parseBoolean` тоже не подошёл: он отвечает `false` на всё, чего не узнал, то есть опечатка
в флаге увела бы отладку в боевой эквайринг — ровно та тишина, ради устранения которой всё и
затевалось.

Тесты: `AcquiringClientConfigTest` (`pbl`, 7 методов / 26 запусков) — разбор значений плюс два
теста на `ApplicationContextRunner`: пустой флаг роняет контекст с именем флага в сообщении,
`true`/`false` дают ровно один бин нужного класса. `./gradlew test` — 499 запусков (384 метода),
0 падений; `:common` 74, `:auth` 65, `:directory` 27, `:pbl` 333.

Документация: `AGENTS.md` §7 (как устроен выбор и почему), «Тонкости» (пара
`@ConditionalOnProperty` на двух реализациях одного интерфейса — не переключатель), §11.
`.env.example` не трогал: решение P1-10 «не выносить `pbl.provider.stub` в окружение» в силе,
флаг остаётся аргументом запуска.


### 20.08.2026 — стаб провайдера убран из приложения ✅

Продолжение предыдущей записи. Раз доступ к тестовому стенду MilliKart есть всегда, вторая
реализация `AcquiringClient` не нужна: локальный запуск ходит на стенд через `PBL_PROVIDER_*`,
как и прод, просто по другим адресам.

Убрано: `StubAcquiringClient` из `src/main`, `AcquiringClientConfig` вместе с его тестом, флаг
`pbl.provider.stub` (в том числе из тестового профиля). `TxpgAcquiringClient` снова обычный
`@Component` и единственная реализация. Переключателя нет — значит, нет и способа выбрать им
не то или не выбрать ничего.

Главное здесь не удобство, а то, что боевая сборка больше не содержит клиент, который отвечает
«оплачено», не спросив эквайера.

**Двойник для тестов остался, но переехал в `src/test`** — иначе 333 теста стали бы сетевыми,
медленными и зависимыми от состояния стенда, а `MoneyOperationsIntegrationTest` и сверка вообще
не смогли бы получить нужные им отказы. Подмена теперь явная, импортом:
`@Import(StubAcquirerConfig.class)` даёт `@Primary`-мок, делегирующий `StubAcquiringClient` через
`AdditionalAnswers.delegatesTo`. Так подключены `PaymentLinkIntegrationTest` и
`SecurityBoundaryIntegrationTest` (второй реально открывает ссылку и без двойника получал 400
вместо 302). Остальные три класса как были на `@MockBean`, так и остались.

**Одна неочевидность, стоившая пяти красных тестов.** Бин объявлен конфигурацией, а не
`@MockBean`, поэтому слушатель Spring не сбрасывает Mockito между методами и записанные вызовы
утекают в следующий тест — ловится теми, где стоит `verify(..., never())`. Лечится
`Mockito.reset(acquiringClient)` в `@BeforeEach`; делегат, заданный при создании мока, `reset`
переживает. Это записано в `AGENTS.md` §11 — грабли ровно для того, кто добавит следующий такой
тест.

`./gradlew test` — 473 запуска (377 методов), 0 падений; `:common` 74, `:auth` 65, `:directory` 27,
`:pbl` 307. Минус 26 запусков — это удалённый `AcquiringClientConfigTest`, проверявший разбор
исчезнувшего флага.

Документация: `AGENTS.md` (§4 команды запуска, §7 провайдер, «Тонкости», §11 — как провайдер
подменяется в тестах, таблица и итоги; в закрытой записи P1-10 помечено, что режим со стабом
больше не существует), `test.env`, `application_description.md` (дерево файлов и таблица
реализаций).


### 20.08.2026 — 405 и 415 вместо 500 ✅ (не нумерованная задача, всплыло в логе)

В логе `auth`: `Unhandled exception processing GET /api/v1/auth/login`,
`HttpRequestMethodNotSupportedException`, ERROR со стектрейсом. Само обращение законное —
`/login` только `@PostMapping`, а GET по этому адресу шлёт браузер, в который вставили ссылку,
и любой сканер. Неверна была реакция: `HttpRequestMethodNotSupportedException` не значился
в `GlobalExceptionHandler`, проваливался в `handleUnexpected` и возвращался как 500
«Unexpected server error».

Это ровно тот дефект, который в том же файле уже чинили для «обработчика нет» (`handleNoHandler`,
404, с комментарием про опечатку в URL) — просто про метод тогда забыли.

Добавлено два обработчика рядом с ним:

- `HttpRequestMethodNotSupportedException` → **405** с заголовком `Allow` (RFC 9110 требует его
  на 405, и именно он делает отказ действенным для клиента) и текстом, называющим допустимые
  методы. Лог — `debug`.
- `HttpMediaTypeNotSupportedException` → **415** (форма вместо JSON, запрос вообще без
  `Content-Type`). Тот же класс ошибки, тоже давал 500.

Тесты — `SecurityBoundaryIntegrationTest` (`auth`), 10 → 13 методов: 405 с `Allow: POST`
на `GET /login`, 415 на форму, и третий про порядок — на защищённом пути неверный метод без
токена по-прежнему 401, потому что 405 не должен опережать аутентификацию и сообщать анониму,
какие глаголы у эндпоинта есть.

`./gradlew test` — 476 запусков (380 методов), 0 падений; `:common` 74, `:auth` 68,
`:directory` 27, `:pbl` 307.

Документация: `AGENTS.md` §11 (строка теста и итоги) и «Тонкости» — общее правило: всё, чего нет
в `GlobalExceptionHandler`, становится 500 с ERROR, поэтому ERROR в логе обязан означать, что
сломались мы, а не что кто-то ошибся глаголом.

### 21.08.2026 — проверка P2-9, P2-12 и трёх внеплановых задач

476 тестов зелёные (+25 к 451), прогон в 07:41 — после последней правки кода в 07:40.
Фронтенд: исходники правились 19.08 в 20:38-20:43, `frontend/dist` собран в 20:52,
то есть `tsc -b` перед `vite build` прошёл уже на новом типе.

**P2-9 — принято.** Сумма закрыта явным набором `AMOUNT_LOCKING_STATUSES` (перечисление,
не отрицание `FAILED`), `PATCH` с неизменившейся суммой до проверки не доходит, переходы
статуса заданы таблицей: `ACTIVE → CANCELED`, `EXPIRED → CANCELED`, `CANCELED → ACTIVE`
с условием, что срок в будущем. `COMPLETED` не покидает своё состояние.

**P2-12 — принято.** `TRANSACTION_STATUSES` — ровно шесть значений бэкенда, тип выводится
из массива, `as any` в `App.tsx` снят, `parseTransactionStatus` на незнакомом значении
возвращает `null` вместо подстановки. Оставшиеся в поиске `'success'` — это названия цветов
MUI, а `as any` в `TransactionListPage:95-96` — приведение `Date.getTime()` при сортировке,
к статусам отношения не имеет.

**Три задачи сделаны в других окнах без моего ТЗ** (записаны в журнале 20.08): переключатель
провайдера, вынос стаба из приложения, 405/415 вместо 500. Проверил вторую, как самую
рискованную: `StubAcquiringClient` больше нет в `src/main` — он переехал в тестовые исходники
вместе с `StubAcquirerConfig`, `@ConditionalOnProperty("pbl.provider.stub")` из боевого кода
исчез, ключа `stub` в боевом yaml нет. Это лучше, чем было: раньше `pbl.provider.stub=true`,
случайно попавший в прод, заставил бы портал показывать «оплачено» по платежам, которых
не было. Теперь это физически невозможно — класса нет в поставке.

**Наблюдение, не дефект этих задач.** У статусов платёжной **ссылки** на фронте та же
болезнь, которую мы только что вылечили у транзакций: `payByLinkData.ts:3` объявляет
`LinkStatus = 'active' | 'paid' | 'completed' | 'expired' | 'cancelled' | 'canceled'` —
значения `paid` бэкенд не присылает никогда (`PaymentLinkStatus` = `ACTIVE`, `EXPIRED`,
`COMPLETED`, `CANCELED`), а `cancelled` и `canceled` дублируют друг друга. Сейчас спасает
`toLowerCase()` в `getStatusConfig` (`PayByLinkPage:88`), поэтому визуально всё работает,
но тип не описывает реальность и компилятор снова ничего не поймает. Кандидат в следующую
задачу — та же правка, что и P2-12, но для ссылок.

### 21.08.2026 — статусы ссылок на фронте (ТЗ)

Решения Р-33, Р-34.

Начиналось как гигиена словаря, оказалось с настоящей ошибкой.

**`PayByLinkPage.handleCancel` (:175-184) в `catch` делает ровно то же, что в `try`:**
ставит ссылке `cancelled` и показывает сообщение об отмене, только с припиской `(local)`.
Если запрос не прошёл, мерчант видит ссылку отменённой, а она живая и по ней платят.
После P2-9 это стало срабатывать чаще: завершённую ссылку бэкенд теперь отменить не даёт.

**Мёртвые ветки на детальной странице.** Маппинг из API (`PayByLinkDetailPage:411`) приводит
статус к нижнему регистру, то есть `CANCELED` → `canceled` с одной `l`, а проверка на :156
написана как `=== 'cancelled'` с двумя. Поэтому пометка об отмене на детальной странице
не показывается никогда. Ветки `status === 'paid'` (:127, :437) мертвы по другой причине:
такого статуса у бэкенда нет вовсе (`PaymentLinkStatus` = `ACTIVE`, `EXPIRED`, `COMPLETED`,
`CANCELED`).

**Молчаливый дефолт.** `(l.status || 'active').toLowerCase()` в трёх местах
(`PayByLinkPage:203`, `:286`, `PayByLinkDetailPage:411`) — нераспознанный статус превращается
в «активна», то есть в «по ссылке можно платить». Ровно то, что убрали у транзакций
(`|| 'APPROVED'`).

**Мёртвый груз.** `generateLinks` импортируется на обеих живых страницах и не вызывается
ни разу — мок-генератор едет в сборку впустую.

Тип `payByLink.statuses` в переводах стоит объявить как `Record<PaymentLinkStatus, string>` —
зеркало того, что P2-12 сделал для транзакций (`translations.ts:243`), чтобы компилятор
требовал все четыре статуса на всех трёх языках.


### 21.08.2026 — P2-13 ✅

Решения Р-33 и Р-34. Правка только во фронтенде, бэкенд не тронут.

`npm run typecheck`, `npm run lint`, `npm run build` — зелёные. Предупреждений линтера
стало 60 против 71 до правки (0 ошибок); в правленых файлах остались только два
пре-существующих — `txnStatusConfig` и `card` внутри `buildLinkedTxns`, которые ТЗ просило
не трогать.

**Главное — не словарь, а отмена.** `PayByLinkPage.handleCancel` в `catch` делал ровно то же,
что в `try`: ставил строке `cancelled` и показывал сообщение об отмене с припиской `(local)`.
Любой отказ — 400 от бэкенда, 403, оборванная сеть — рисовал мерчанту закрытую ссылку, по
которой продолжали платить. После P2-9 самый частый путь сюда открылся: `COMPLETED` не покидает
своё состояние, попытка отмены завершённой ссылки — это 400. Теперь локальной правки состояния
нет вовсе: при успехе — `payByLink.linkCancelledSuccess`, при отказе — `ErrorResponse.message`
от бэкенда, и в `finally` перечитывание с сервера в обоих случаях. То же на детальной странице.

**Словарь.** `LINK_STATUSES` (`ACTIVE`, `EXPIRED`, `COMPLETED`, `CANCELED`), `LINK_USAGE_TYPES`
(`SINGLE`/`MULTIPLE`), `PAYMENT_TYPES` (`SMS`/`DMS`) — типы выводятся из массивов, источники
указаны в комментарии. `parseLinkStatus`/`parseLinkUsageType`/`parsePaymentType` — общий
внутренний `parseEnumValue`: строгое сравнение, незнакомое → `null` плюс предупреждение
в консоль с исходным значением. `PaymentLink.status` стал `LinkStatus | null`, рядом
`statusRaw` — зеркало того, что P2-12 сделал для транзакции. Нераспознанный статус
показывается серым (`getLinkStatusColors` → нейтральная схема) с исходным значением,
иконка — вопросительный знак, и все действия по такой строке заблокированы:
`disabled={link.status !== 'ACTIVE'}` закрывает и копирование, и шаринг, и отмену.

**Что показал компилятор** после смены словаря — три десятка `TS2367: no overlap` на двух
страницах. Из них по существу:
- `link.status === 'cancelled'` (две `l`) против маппинга, клавшего `canceled` (одна) —
  пометка об отменённой ссылке на детальной странице не показывалась **никогда**. Это была
  вторая настоящая ошибка задачи;
- шесть блоков под `status === 'paid'` — статуса нет у бэкенда вовсе. Блоки, завязанные на
  факт оплаты (дата оплаты в списке, «Payment Details», событие «Payment Received», алерт
  «Payment received on …»), переведены на условие по наличию самой даты (`link.paidAt`);
  цвет hero-карточки и подпись «Amount Received» — на `COMPLETED`;
- `isDmsAuthorized` требовал `status === 'paid'` — условие снято, стадия DMS живёт
  в своём поле;
- фильтр и статистика на списке: `paid` → `COMPLETED`, добавлен фильтр `CANCELED`,
  так что все четыре значения словаря на экране есть.

**Мёртвый груз.** `generateLinks`, `merchantTerminals` и служебные массивы генератора удалены
вместе с висячими импортами (`PayByLinkPage:64`, `PayByLinkDetailPage:57`). Из `PaymentLink`
удалены `terminalRid` и `paymentMethod` — их не читает никто. Поля, которые разметка читает,
оставлены с явной пометкой «всегда `undefined` до появления поля в API». Заодно ушли четыре
неиспользуемых импорта иконок на списке.

**Переводы.** `payByLink.statuses` объявлен как `Record<LinkStatus, string>` — ключ `paid`
убран, четыре статуса есть на всех трёх языках, лишний ключ теперь не проходит `tsc`.
Добавлен `linkCancelFailed` (запасной текст, когда в теле ответа нет `message`).
Рядом со `statusLabel` появился `linkStatusLabel` с тем же правилом: `null` → показать `raw`.

**Три решения сверх ТЗ** (каждое — следствие Р-34, но в тексте задачи их не было):
1. `fetchPaymentLinks` перестал очищать список в `catch`. Перечитывание теперь вызывается
   и после отмены; при выключенном бэкенде прежний `setLinks([])` стирал бы с экрана строки,
   о судьбе которых мы ничего не узнали, — а проверка №3 из ТЗ требует ровно обратного:
   «состояние строки не меняется».
2. Диалог отмены на детальной странице закрывается в `finally`, а не только при успехе: после
   отказа он спрашивал бы про отмену ссылки, которую бэкенд отменять отказался. Текст отказа
   виден в snackbar (он поверх диалога, но так понятнее).
3. Snackbar с ошибкой живёт 10 секунд вместо 3 — сообщение бэкенда длиннее «Link copied».
   Заодно у него теперь `severity="error"`, а не «успех» зелёным.

**Ещё две правки по правилу «убрать приведение регистра при чтении»:** в `HomePage` доли
SMS/DMS и SINGLE/MULTIPLE считаются через `parsePaymentType`/`parseLinkUsageType` вместо
`String(l.paymentType || '').toUpperCase()`; на детальной странице `parsePaymentMethod(link.paymentType)`
заменён на само значение — после разбора типы совпадают.

**Не тронуто по ТЗ:** локальный словарь связанных транзакций (`LinkedTxn.status`,
`txnStatusConfig`, `buildLinkedTxns`). Он мёртв — `txnStatusConfig` не читается ниоткуда,
`buildLinkedTxns` возвращает пустой список. Условие `link.status !== 'paid'` в нём пришлось
перевести (иначе не собирается) — стало `!link.paidAt`, поведение то же: пустой список.
Отмечено в `problems.md` §10 как кандидат на уборку: оживлять его нельзя, он собирает строки
из `cardNetwork ?? 'Visa'` и `cardLast4 ?? '4242'`.

**Проверка в браузере не проводилась** — по решению заказчика в этом окне. Автоматические
гейты зелёные; шесть ручных проверок из ТЗ (включая главную — отмену завершённой ссылки
и скриншот отказа) остаются за заказчиком. Для них в локальную БД добавлены две строки-фикстуры,
чтобы на экране были все четыре статуса: `11111111-…-111111111111` — `COMPLETED`
(MULTIPLE, 3 из 3 платежей) и `22222222-…-222222222222` — `EXPIRED`. Удалить:
`delete from payment_links where id in ('11111111-1111-4111-8111-111111111111',
'22222222-2222-4222-8222-222222222222');`

Приёмочный grep (должен молчать):
```bash
grep -rnE "===? *'(APPROVED|DECLINED|3d-failed|success|pending|canceled|cancelled|paid|active|expired|completed|sms|dms|single|multiple)'" \
  frontend/src --include='*.ts' --include='*.tsx'
```

Документация: `AGENTS.md` §9 (ловушка `payByLinkData.ts` снята), §10 — правило «Словари бэкенда
во фронтенде» распространено на ссылки и запись P2-13 в «Закрытых блокерах», футер;
`problems.md` §10 закрыт (там же — что осталось и что проверено по `DmsStatus`);
`.agents/workflows/frontend.md` — §3.7 (`PATCH` шлёт `CANCELED`, а не `CANCELLED`, поведение
при отказе, словари ссылки, удаление мок-генератора) и шапка. §11 `AGENTS.md` не менялся:
тестов правка не добавляет, на фронтенде их нет. `code_review.md` не менялся: P2-13 вырос
из наблюдения в журнале 21.08 и решений Р-33/Р-34, отдельного пункта ревью у него нет.

### 21.08.2026 — P2-13 ✅ (статусы ссылок на фронте)

Исходники правились 14:12-14:23, `frontend/dist` собран в 14:37 — значит `tsc -b` перед
`vite build` прошёл уже на новом словаре.

Замечаний нет. Словари подняты до значений бэкенда (`ACTIVE`/`EXPIRED`/`COMPLETED`/`CANCELED`,
`SMS`/`DMS`, `SINGLE`/`MULTIPLE`), выведены из `as const`-массивов, разбор — через общий
`parseEnumValue` с `null` на незнакомом значении и без подстановок. `handleCancel` больше
не рисует отмену при отказе: успех и ошибка различаются, текст берётся из тела ответа
(`response.data.message`), список перечитывается в `finally` — состояние на экране приходит
с сервера в обоих случаях. `generateLinks` и `merchantTerminals` удалены полностью.
`payByLink.statuses` в переводах объявлен как `Record<LinkStatus, string>` — зеркало того,
что P2-12 сделал для транзакций.

**Сверх ТЗ:** кнопка отмены теперь `disabled={link.status !== 'ACTIVE'}`. Самый частый путь
к исходной ошибке (отмена завершённой ссылки) стал недостижим из интерфейса, а не только
корректно обработан.

**Найдено при проверке, отдельная задача.** Ветку `status === 'paid'` заменили на условие
по `link.paidAt` — как и просило ТЗ, — но блок всё равно не рисуется никогда:
**`PaymentLinkResponse` не отдаёт времени оплаты вообще.** В нём есть `expiresAt` и
`createdAt`, поля вроде `paidAt` нет, и маппинг из API его не выставляет. Значит на обоих
экранах — в списке (`PayByLinkPage:584`) и на детальной (`PayByLinkDetailPage:127`, `:200`,
`:620`) — разметка под дату оплаты есть, а данных под неё нет. Реализация это честно
задокументировала комментарием, а не спрятала.

Данные на сервере есть: время успешной транзакции по этой ссылке. Правка небольшая —
добавить в `PaymentLinkResponse` время первой (или последней) транзакции в статусе `SUCCESS`.
Кандидат в следующую задачу.

### 21.08.2026 — Аудит (ТЗ)

Решения Р-35, Р-36. Закрывает P2-2, P2-5, P2-6.

`AuditLogService.logAction` помечен `@Transactional` без `REQUIRES_NEW` (:26), то есть
присоединяется к транзакции вызывающего и исчезает вместе с ней при откате.

Но простое `REQUIRES_NEW` дало бы обратную ошибку, и на живом примере: `logAction`
вызывается **после** `repository.delete/save`, но до конца транзакции, а удаление терминала
с привязанными платёжными ссылками падает на внешнем ключе уже при сбросе изменений
(наша же незакрытая P2-8). Журнал записал бы «удалил терминал 5» при неудавшемся удалении.
Отсюда Р-35: успех пишем после коммита, отказы — сразу.

`audit_logs` не имеет **ни одного индекса** (`003-directory-schema.xml:98-117`), при этом
`listAuditLogs` для `COMPANY_HEAD`/`COMPANY_MANAGER` грузит все логи компании и фильтрует
их в памяти Java (`AuditLogService:56-59`), а для `SYSTEM_ADMIN`/`AUDITOR` без фильтров
делает `findAll()` без пагинации. Поля с IP в сущности нет вовсе, хотя
`technical_handover.md` §4.4 фиксацию IP заявляет.

Покрытие журнала узкое: шесть точек в `CompanyService` и `TerminalService`. Заведение,
блокировка и удаление пользователей не пишутся — `UserService` живёт в `auth`, отдельном
сервисе, до журнала не дотягивается. Это отдельная задача, в эту не входит.

### 21.08.2026 — P2-2 / P2-5 / P2-6 (сделано)

Журнал аудита `directory`: запись успеха — только после коммита, отказы фиксируются, IP
пишется, фильтрация и пагинация — в базе. Решения Р-35, Р-36 реализованы как приняты.

**`common`.** `ClientIpHolder` (`ThreadLocal`) + `ClientIpFilter`
(`OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE)` — раньше security-цепочки): адрес
разрешается через `ClientIp.resolve` от доверенных прокси, кладётся в держатель и в MDC под
ключом `clientIp`, оба чистятся в `finally` — пул потоков переиспользует потоки.
`PagedResponse` перенесён из `pbl/.../dto` в `az.millikart.common.dto` (импорты `pbl`
поправлены; второго в проекте нет — приёмочный `find` даёт один файл).

**Схема.** `004-audit-log-ip-and-indexes.xml` (ids `4-directory-audit-*` — уникальны в
пределах проекта, `DATABASECHANGELOG` общий): `client_ip varchar(45)`, `outcome varchar(16)
not null default 'SUCCESS'` (бэкфилл честен — отказы раньше не писались), три индекса:
`(company_id, created_at desc)`, `(entity_type, entity_id)`, `(created_at desc)`.
Прекондишены в стиле P1-2. `createIndex`-элементы записаны в одну строку — приёмочный
`grep -c "createIndex"` считает строки и должен дать ровно 3.

**Запись.** Успех: сервисы публикуют `AuditEvent` (адрес забирается из `ClientIpHolder`
в момент публикации — `AuditEvent.of`); `AuditLogWriter` —
`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)` — вызывает
`AuditLogService.recordSuccess`. Отказы: `logDenied` прямо в точке отказа — ролевые
проверки `CompanyService` (включая `validateAccess` на чтении и `listCompanies`),
`validateWriteAccessToCompany`/`validateReadAccessToCompany` и ролевые ветки `TerminalService`
(проверки внутри валидаторов, сигнатуры расширены на принципала/действие), отказ в самом
`listAuditLogs`. В `details` — роль, компания актора и что именно пытались сделать.

**Два отступления от буквы ТЗ, оба вынужденные.**
1. Своя транзакция открывается `TransactionTemplate`'ом (`PROPAGATION_REQUIRES_NEW`), а не
   аннотацией `@Transactional(REQUIRES_NEW)`. Аннотацию применяет прокси, а `listAuditLogs`
   вызывает `logDenied` внутри своего же класса — прокси не участвует, запись присоединялась
   к `readOnly`-транзакции листинга и уходила с её откатом. Шаблон делает границу свойством
   метода, а не способа вызова.
2. Катч вокруг записи, а не внутри неё: `persist` не сбрасывается до коммита, поэтому сбой
   всплывает после тела метода. У успеха катч держит `AuditLogWriter` (иначе исключение из
   `AFTER_COMMIT`-слушателя уронило бы ответ уже закоммиченной операции), у отказа — сам
   `logDenied` (там запись идёт до `throw`, и исключение подменило бы 403 на 500). Оба пишут
   ERROR с маркером `AUDIT_WRITE_FAILED`.

Плюс две защиты, которых в ТЗ не было, но без которых механизм отключается снаружи: строки
режутся по ширине колонок (`details` — 4000, id — 255; имя компании в теле запроса ничем не
ограничено, и переполнение роняло запись об отказе вместе с ответом 403), а `companyId`
записи об отказе берётся от актора, а не из запроса (журнал режется по этой колонке — иначе
любой аутентифицированный пользователь пишет произвольный текст в обзор аудита чужой
компании).

**Чтение.** `listAuditLogs`: `Pageable`, `PagedResponse<AuditLogResponse>` (+`clientIp`,
+`outcome`), сортировка всегда `createdAt DESC`, фильтр в запросе. `entityType` сравнивается
точно (значение приводится к верхнему регистру в сервисе), `entityId` — `IgnoreCase`: полный
`IgnoreCase` по обеим колонкам компилируется в `upper(entity_type) = upper(?)`, и заведённый
этой же задачей индекс `(entity_type, entity_id)` такой запрос обслужить не может. Результат
тот же, что давал прежний `equalsIgnoreCase` в памяти (для admin-ветки заодно убрана старая
несогласованность — она фильтровала с учётом регистра). Контроллер: `page`/`size`
с умолчаниями `0`/`20`, как у транзакций `pbl`, но со срезкой (`size` в `[1, 200]`,
`page` ≥ 0): `PageRequest.of(0, 0)` бросает `IllegalArgumentException`, обработчика на него
нет, и опечатка в параметре возвращала бы 500 со стектрейсом. Ролевые правила не менялись.

**Фронтенд (минимально, чтобы страница не опустела).** `AuditLogsPage` разбирает `content`
пагинированного ответа (было `Array.isArray(res.data)` — с новым контрактом список был бы
всегда пуст); до собственной пагинации страницы запрашивается `size=200`. В `AuditLogDto`
добавлены `clientIp`/`outcome`. `tsc -b` зелёный.

**Тесты.** `./gradlew test` — 500 запусков (было 476), все зелёные: `AuditLogIntegrationTest`
(14), `AuditLogQueryTest` (6), `AuditLogSchemaTest` (1, индексы читаются из
`DatabaseMetaData`, не из `DATABASECHANGELOG`), `ClientIpFilterTest` (3, `common`);
`DirectoryIntegrationTest` переведён на `$.content` и порядок «новые первыми». Приёмочные
греп-проверки из ТЗ — все выполняются.

**Шесть из этих тестов появились после ревью, а не до него.** Первый прогон был зелёным на
494 запусках, и ревью (6 направлений, каждая находка проверялась отдельным скептиком) нашло
в этом зелёном коде потерю записи об отказе на self-invocation и переполнение `details`;
оба воспроизведены тестами до правки — `deniedListByEmployee_isRecorded`,
`deniedListByHeadWithoutCompany_isRecorded`, `deniedCreate_withOversizedInput_*`,
`deniedCreate_isFiledUnderActorsCompany_*`, `auditWriteFailure_onDeniedPath_stillReturns403`,
`pagingParameters_areClamped_*`. Отдельно отмечено ревью и исправлено: `%X{clientIp:--}`
в `logback-spring.xml` — MDC-ключ заполнялся, но не выводился ни в одной строке лога.

**Документация.** `AGENTS.md` §10 (запись + две «грабли»: журнал и `ClientIpHolder`), §11,
подвал; `technical_handover.md` §4.4 сверен с кодом (IP теперь правда, покрытие — только
`directory`); `problems.md` §9 перенацелен с «смотреть на P2-5» на готовый образец, новый §11 —
действия над пользователями (`auth`) не фиксируются нигде, включая заведение и блокировку
администратора; `code_review.md` — пометки в таблице §4 и в плане §8;
`directory/directory.md` — контракт `GET /api/v1/audit-logs` (раньше не был описан вовсе).

Не тронуто по ТЗ: аудит в `auth` (теперь `problems.md` §11), P2-8, пагинация остальных
списков (P2-1), ролевые правила журнала, существующие записи журнала. Замечено попутно и
**не** правилось: `TransactionController.list` в `pbl` строит `PageRequest.of(page, size)`
из сырых параметров — те же 500 на `size=0` и неограниченный размер страницы; правка туда
относится к P2-1 (заведена отдельной задачей). *(P2-1 закрыт 22.08.2026 **без** этой правки —
его ТЗ прямо запрещало трогать пагинацию транзакций и ссылок. Ссылка перенацелена:
`problems.md` §15, где записаны оба эндпоинта — `TransactionController.list` и
`PaymentLinkController.list`.)*

### 21.08.2026 — Аудит ✅ (P2-2, P2-5, P2-6)

500 тестов зелёные (+24 к 476), прогон в 18:39 — после последней правки кода в 18:38.
В `directory` было 27 тестов, стало 48.

Замечаний нет. Успех пишется после коммита через событие, отказ — синхронно в своей
транзакции, три индекса заведены и проверяются тестом по метаданным схемы (включая
направление сортировки, а не только факт существования), фильтрация ушла в запрос,
пагинация появилась, `PagedResponse` переехал в `common` и существует в одном экземпляре.

**Главная находка реализации, которой не было в ТЗ.** `logDenied` вызывается в том числе
из `listAuditLogs` — то есть из того же класса. `@Transactional(REQUIRES_NEW)` там **не
сработал бы**: аннотацию применяет прокси, а при вызове изнутри класса прокси в цепочке нет,
и запись молча присоединилась бы к read-only транзакции листинга, которую отказ и откатывает.
Потерялась бы ровно та запись, которую больше некому написать. Решено `TransactionTemplate`
с `PROPAGATION_REQUIRES_NEW` — граница транзакции стала свойством метода, а не способа вызова.

**Ещё три вещи сверх ТЗ, все по делу:**

Значения обрезаются до ширины колонок перед вставкой. Строки приходят из тел запросов
и path-переменных, длину никто выше не ограничивает, и на пути отказа падение вставки
превратило бы 403 в 500. Есть тест (`deniedCreate_withOversizedInput_stillReturns403AndIsRecorded`).

Отказ пишется в компанию **исполнителя**, а не в ту, которую он пытался тронуть. Иначе
менеджер мог бы писать записи в чужой журнал, просто пытаясь делать запрещённое, а глава
той компании видел бы у себя чужие следы.

У размера страницы появился потолок (200) и мягкое приведение параметров вместо ошибки.
Таблица только растёт, и неограниченный `size` — приглашение вытащить её в память одним
запросом.

Оба ключевых сценария закрыты тестами: `rolledBackOperation_leavesNoAuditRecord` и
`commitTimeFailure_afterAuditEventPublished_leavesNoAuditRecord` — второй воспроизводит
ровно тот случай с удалением терминала, из-за которого мы отказались от простого
`REQUIRES_NEW`.

Дыра с аудитом `auth` записана в `problems.md` §11, `technical_handover.md` §4.4 приведён
в соответствие с реальностью.

### 21.08.2026 — P2-8 → блокировка терминалов (ТЗ)

Решения Р-37 … Р-40. Разбор с заказчиком превратил «вернуть 409 вместо 500» в смену модели:
терминалы не удаляются, а блокируются.

Что всплыло при разборе и попало в ТЗ:

**Блокировка не должна ломать обслуживание уже начатых платежей** (Р-38) — это главное.
Логин и пароль терминала используются не только для приёма денег: через них идут опрос
статуса (сверка P1-3), списание холда DMS и возврат. Выключение терминала целиком повесило бы
незавершённый платёж, оставило бы холд на карте до отпускания банком и лишило бы клиента
возврата.

**`terminals` создают оба сервиса.** `directory/003-directory-schema.xml:54` и
`pbl/001-initial-schema.xml:15` — обе с преконтролями, потому что база общая и стартовать
сервисы могут в любом порядке (P1-2). Значит колонку `status` надо добавлять **в обоих**
changelog'ах, каждый раз под `not columnExists`: иначе сервис, стартовавший первым, создаст
таблицу без колонки, а `ddl-auto: validate` на его же сущности со `status` не пройдёт,
и он не поднимется.

**Ту же таблицу читает `pbl`** — своя сущность `Terminal` на `terminals`, три места
(`OpenLinkService:186`, `PaymentLinkService:471`, `:564`). Проверка статуса нужна и там.

**`payment_links.status` — `varchar(255)` без CHECK**, так что новое значение `SUSPENDED`
миграции не требует. Массовое обновление ссылок терминала попадает в существующий индекс
`idx_payment_links_term_status` (`terminal_id`, `status`).

**Гонка:** плательщик открывает ссылку в момент блокировки. Проверять статус терминала надо
после захвата блокировки строки в `openAndBuildRedirect`, а не до.

### 21.08.2026 — P2-8 (сделано)

Решения Р-37 … Р-40 реализованы как приняты. Терминалы больше не удаляются: `deleteTerminal`
и `@DeleteMapping` удалены целиком, путь остался — `DELETE /api/v1/terminals/{id}` отвечает 405
(обработчик от 20.08.2026).

**Схема.** `terminals.status varchar(16) not null default 'ACTIVE'` добавлен **двумя**
changeset'ами — `directory/005-terminal-status.xml` (`5-directory-terminal-status`) и
`pbl/005-terminal-status.xml` (`5-pbl-terminal-status`), каждый под `not columnExists`. Оба
порядка старта проверены `TerminalStatusMigrationTest` (5 методов, без Spring, по образцу
`MigrationOrderTest`): пустая база + pbl первым, пустая база + directory первым, повторные
прогоны, база без `DATABASECHANGELOG`, живая база с `terminals` без колонки (существующие
терминалы получают `ACTIVE`, а не пустое значение).

**`directory`.** `TerminalStatus { ACTIVE, BLOCKED }`, поле в сущности, `status` в
`UpdateTerminalRequest` и `TerminalResponse`. Смена статуса — в существующем `PATCH`, отдельных
`/block` и `/unblock` нет. Установка того же статуса — не изменение и ссылок не трогает.
Массовое обновление — `PaymentLinkStatusRepository`: три нативных `UPDATE` без отображения
сущности `PaymentLink`, в той же транзакции, что и смена статуса терминала (тест на откат:
падение обновления ссылок откатывает и блокировку). В журнал пишется отдельное событие
`BLOCK`/`UNBLOCK` с числом затронутых ссылок в `details` — единственный след массовой операции.

**`pbl`.** `PaymentLinkStatus.SUSPENDED`; в таблице переходов P2-9 он недостижим и непокидаем,
причём оба отказа получили собственный текст — «cannot be changed from SUSPENDED to ACTIVE»
отправило бы мерчанта искать неисправность в ссылке, а она в терминале. Сущность `Terminal`
получила `status`; проверки — ровно в двух местах, где платёж **начинается**: создание ссылки
(400 с именем терминала) и открытие ссылки плательщиком. В `openAndBuildRedirect` терминал
читается **после** `findWithLockById` и до любой записи; отказ плательщику — тот же текст, что
для недоступной ссылки, без упоминания терминала.

**Проверено обратно.** Проверка терминала, перенесённая выше захвата блокировки, роняет
`terminalBlockedWhileTheOpenWaitsForTheLock_stopsThePayment` — тест держит блокировку строки,
блокирует терминал, пока открытие стоит в очереди за ней, и требует отказа. Это единственный
тест, который отличает правильный порядок от неправильного, поэтому он проверен обоими
способами, а не только зелёным прогоном.

**Найдено по ходу и исправлено:** блокировка терминала падала на базе, где `pbl` никогда не
мигрировал (нет таблицы `payment_links`) — админ получал 500 при первом развёртывании, если
`directory` поднялся первым. Это тот же P1-2 в новом месте. Отсутствие таблицы теперь штатный
случай: ссылок нет, двигать нечего, WARN в лог, блокировка проходит
(`PaymentLinkStatusRepositoryTest`).

**Отступление от ТЗ, осознанное.** Пункт 6 ТЗ требовал ограничить `ACTIVE`-терминалами «список
терминалов, доступных мерчанту (`PaymentLinkService:386`)». По этой ссылке — фильтр видимости
**платёжных ссылок**, и ограничение там спрятало бы от мерчанта ровно те `SUSPENDED` ссылки,
которые пункты 5 и 8 того же ТЗ требуют показывать (со статусом, цветом и переводами). Сделано
по смыслу: заблокированный терминал не предлагается там, где начинается новый платёж — в
выпадающем списке формы создания ссылки на фронте (плюс отказ бэкенда при создании), — а
видимость ссылок не тронута. Покрыто `suspendedLinksRemainVisibleToTheMerchant`.

**Фронтенд.** Кнопка удаления заменена на блокировку/разблокировку, добавлен столбец статуса,
заблокированные строки приглушены. Перед блокировкой — подтверждение с числом активных ссылок,
которые будут приостановлены (`GET /api/v1/payment-links?terminal=X&status=ACTIVE&size=1`,
`totalElements`; не посчиталось — так и написано в диалоге, блокировка всё равно доступна).
В форме создания ссылки — только активные терминалы, при их отсутствии — текст про блокировку
вместо пустого списка. `LINK_STATUSES` пополнен `SUSPENDED` (янтарный, отличим и от активной,
и от отменённой), переводы на трёх языках — `Record<LinkStatus, string>` этого и потребовал.
Словарь статусов терминала — тоже `Record<TerminalStatus, string>` на трёх языках.

**Тесты.** `./gradlew test` — 529 запусков (было 500), зелёные:
`TerminalBlockingIntegrationTest` (9, `directory`: приостановка только `ACTIVE`, неприкосновенность
`EXPIRED`/`COMPLETED`/`CANCELED`, чужой терминал не тронут, возврат в `ACTIVE`, просроченные в
`EXPIRED`, повторная установка того же статуса, откат одной транзакцией, аудит с числами, отказ
`COMPANY_EMPLOYEE`), `TerminalStatusMigrationTest` (5), `PaymentLinkStatusRepositoryTest` (1),
`TerminalBlockedIntegrationTest` (13, `pbl`: отказ на `SUSPENDED` и на заблокированном терминале
без обращения к эквайеру, гонка с блокировкой строки, 400 при создании, `SUSPENDED` не
выставляется и не снимается мерчантом, видимость приостановленных ссылок, **и три теста Р-38 —
возврат, capture и опрос статуса на заблокированном терминале проходят**). Плюс
`deleteTerminal_isNotSupported_returns405` в `DirectoryIntegrationTest`; прежний тест удаления
терминала переписан на блокировку. Фронтенд: `npm run typecheck`, `npm run lint`
(60 предупреждений, 0 ошибок), `npm run build` — зелёные; в браузере не проверялось (тестов
фронта в проекте нет, для сценария нужны поднятые auth + directory + pbl и база).

**Документация.** `AGENTS.md` §10 (запись + грабли про Р-38 и порядок проверки под блокировкой),
§11, подвал; `fix_plan.md` (таблица спринта, эта запись); `code_review.md` (P2-8 в §4, план §8);
`problems.md` §12 — блокировка локальна, в MilliKart терминал продолжает работать;
`technical_handover.md` §4.2; `directory/directory.md` (контракт `PATCH`, `DELETE` → 405, колонка
`status`); `pbl/pay-by-link.md` (`SUSPENDED`, отказ создания на заблокированном терминале);
`application_description.md` (эндпоинты, ER-диаграмма, таблица миграций);
`.agents/workflows/frontend.md`.

Приёмочные проверки ТЗ проходят с одной оговоркой: греп «удаления нет» ищет `@DeleteMapping`
по всему модулю `directory` и находит его у **компаний** (`CompanyController`, мягкое удаление) —
их эта задача не касается. У терминалов ни `deleteTerminal`, ни `@DeleteMapping` не осталось.

### 21.08.2026 — P2-8 → блокировка терминалов ✅

529 тестов зелёные (+29 к 500), прогон в 19:30, после него правились только документы.
`directory` 48 → 64, `pbl` 307 → 320.

Замечаний нет. Проверил по пунктам, включая те, где ошибка стоила бы денег:

**Р-38 соблюдено буквально.** В трёх местах — возврат, списание холда, опрос статуса —
стоит комментарий «не проверяет статус терминала и не должен (Р-38)», и на каждое есть тест:
`refundOnBlockedTerminal_goesThrough`, `captureOnBlockedTerminal_goesThrough`,
`statusPollOnBlockedTerminal_reachesAFinalStatus`. Без этих трёх тестов правка рано или
поздно «навела бы порядок» и добавила проверку туда, где её быть не должно.

**Гонка закрыта:** `terminalBlockedWhileTheOpenWaitsForTheLock_stopsThePayment` — блокировка
происходит, пока открытие ссылки ждёт блокировку строки; платёж не стартует.

**Миграции в двух модулях** (`directory/005-terminal-status.xml` и `pbl/005-terminal-status.xml`)
с разными идентификаторами changeset'ов и одинаковым преконтролем `not columnExists`. Второй
по порядку помечает себя выполненным. В обоих файлах написано, почему дублирование
обязательно, а не небрежность.

**Массовое обновление** — три `UPDATE` без цикла по сущностям, в одной транзакции со сменой
статуса терминала; `failureToSuspendLinks_rollsBackTheBlockItself` доказывает атомарность.

**Мелочи сверх ТЗ:** в запросе разблокировки учтён `expires_at IS NULL` (ссылки, созданные
до P1-9); отдельно запрещена отмена приостановленной ссылки
(`merchantCannotCancelASuspendedLinkEither`); проверено, что приостановленные ссылки
не исчезают из списка мерчанта (`suspendedLinksRemainVisibleToTheMerchant`).

Заодно закрыт исходный P2-8: `DELETE /api/v1/terminals/{id}` отвечает 405, а не 500.

### 21.08.2026 — Аудит во всех сервисах (ТЗ)

Решения Р-41 … Р-43.

Заказчик предложил вынести аудит в отдельный сервис и раздать модулям контракт. Разобрали:
цель верная, механизм в этом проекте не работает. Сервисы стартуют вручную, и на вопрос
«что делать, когда аудит недоступен» все три ответа плохи — потеря записей, остановка
возвратов и входа, либо очередь исходящих в каждом сервисе, которая сложнее самого журнала.
Плюс изоляция мнимая: база одна на всех.

**Найдено при разборе, отдельная проблема.** Все три сервиса подключаются к базе под
пользователем `postgres` — суперпользователем. В `deployment_guide.md` нет ни `CREATE ROLE`,
ни единого `GRANT`. То есть привилегий не разделено вообще, и любой скомпрометированный
сервис может не только переписать журнал, но и сделать с базой что угодно. Записано
в `problems.md` отдельным пунктом; в этой задаче закрывается частично — ролью с правом
только на вставку в `audit_logs`.

**Второе, что всплыло.** Если писать в журнал каждую попытку входа, отбитую лимитом по IP,
получится усилитель: атакующий, долбящийся с заблокированного адреса, вызывает неограниченный
поток вставок в журнал — при том что сам лимитер до базы не доходит. Поэтому в журнал идёт
**момент превышения порога** (один раз за окно), а не каждая последующая попытка. В
`LoginRateLimiter` для этого уже есть ровно одна точка — `if (count == maxFailures)`.

**Третье.** `PaymentOutcomeUnknownException` (исход операции неизвестен) — единственный
случай, который обязан попасть в журнал, хотя локально ничего не коммитится. Значит писать
его надо не событием после коммита, а синхронно, как отказ.

### 22.08.2026 — P2-14 (сделано)

Журнал аудита теперь ведут все три сервиса. Решения Р-41 … Р-43 реализованы как приняты.

**Перенос (Р-41).** `AuditLog`, `AuditOutcome`, `AuditEvent`, `AuditLogService`, `AuditLogWriter`
и репозиторий записи переехали в `az.millikart.common.audit`; в `common/build.gradle` добавлен
`spring-boot-starter-data-jpa`. В `directory` осталась читающая сторона — `AuditLogQueryService`,
`AuditLogQueryRepository`, `AuditLogResponse`, контроллер: журнал показывает только он. Два
репозитория Spring Data на одну сущность, у каждого методы своей стороны.

**Ловушка со сканированием, ровно та, что описана в ТЗ.** `scanBasePackages = "az.millikart"`
находит компоненты `common`, но базовый пакет для `@Entity` и репозиториев берётся от класса
приложения. Во всех трёх сервисах прописаны явные `@EntityScan` и `@EnableJpaRepositories`, и в
каждом перечислены свой пакет **и** `az.millikart.common.audit` — без своего пакета сервис
перестаёт видеть собственные сущности.

**Схема.** `auth/004-audit-logs.xml` и `pbl/006-audit-logs.xml` создают `audit_logs` сразу в
финальном виде (все колонки + три индекса) под `not tableExists`, с уникальными id changeset'ов.
Пять changeset'ов `directory/004` после этого находят каждый свой объект и помечаются
выполненными — то, ради чего они в своё время были разбиты по одному на колонку и индекс.
Проверено во всех порядках: `auth` первым, `pbl` первым, `directory` первым.

**`auth`.** Заведение пользователя (включая первого администратора из `AdminBootstrapRunner`,
`performedBy = system`), изменение — с перечислением изменившихся полей, где смена роли выглядит
как `role COMPANY_EMPLOYEE -> SYSTEM_ADMIN`, — блокировка и разблокировка отдельными действиями,
смена пароля, удаление, успешный и неудачный вход, срабатывание блокировки после шестой неудачи,
повторное использование refresh-токена. Отказ в повышении привилегий (`Cannot assign
administrative role`) тоже пишется.

**Лимит по IP.** `LoginRateLimiter.recordFailure` теперь возвращает `true` ровно в точке
`count == maxFailures`, а запись делает вызывающий (`AuthService`). Так лимитер остался без
зависимости от аудита и, главное, без похода в базу на каждую отбитую попытку: 20 попыток при
пороге 10 оставляют одну запись, а не двадцать (тест на это есть).

**`pbl`.** Создание, изменение и отмена ссылки; списание холда DMS и возврат — с суммой, валютой
и идентификаторами эквайера; отказы в доступе к чужим терминалам. Отдельно —
`PaymentOutcomeUnknownException`: обе денежные операции ловят его, пишут запись **синхронно**
(`logUnresolved`) и пробрасывают дальше. Локально в этом случае не коммитится ничего, событие
после коммита не сработало бы никогда, а запись — единственное свидетельство, что операция была.

**Только на дозапись (Р-42).** У репозитория записи ровно один метод `save` (расширяет пустой
`Repository`, а не `JpaRepository`), у сущности сняты сеттеры, читающий репозиторий тоже
read-only. Тестам полный доступ нужен, поэтому в тестовых исходниках каждого сервиса лежит
`AuditLogTestRepository` — прикладной код до него не дотянется. В `deployment_guide.md` §5.1a
добавлены роль `mp_app` и права (`INSERT`/`SELECT` на `audit_logs`, без `UPDATE`/`DELETE`), с
предупреждением, что **сегодня они ни на что не влияют**: сервисы ходят под `postgres`.
Полностью проблема записана в `problems.md` §13 как открытая.

**Тесты.** `./gradlew test` — 552 запуска (было 529), зелёные. Новые: `AuthAuditIntegrationTest`
(10), `PblAuditIntegrationTest` (7), `AuditLogAppendOnlyTest` (3, `common`), плюс три метода в
`SharedSchemaMigrationTest` (переименован из `TerminalStatusMigrationTest`: он теперь про обе
общие таблицы). Перенесённые тесты `directory` продолжают проходить оттуда же.

**Документация.** `AGENTS.md` (запись в закрытых блокерах, четыре новых «грабли» — контракт
публикации, ловушка со сканированием, запрет секретов в `details`, одна запись за окно у
лимитера, — §11 и подвал); `problems.md` §13; `deployment_guide.md` §5.1a;
`technical_handover.md` §4.4; `code_review.md` (план §8).

Приёмочные проверки ТЗ проходят. Одна оговорка: грep «журнал только на дозапись» ищет слова
`delete|deleteAll|update` в файле репозитория и находит их **в javadoc**, где объясняется, что
таких методов нет. В коде их нет — это доказывает `AuditLogAppendOnlyTest`, который читает
список методов интерфейса рефлексией.

### 21.08.2026 — Аудит во всех сервисах ✅ (P2-14)

552 теста зелёные (+23 к 529), прогон в 20:22, кода после него не трогали.

Замечаний нет. Машинерия одна на всех — `az.millikart.common.audit`, читающая сторона
осталась в `directory` (`AuditLogQueryService` + `AuditLogQueryRepository`). Ловушка с
`@EntityScan` обойдена: во всех трёх сервисах перечислены и собственный пакет, и
`az.millikart.common.audit` — объявив аннотацию явно, легко потерять умолчание и остаться
без своих же сущностей.

Оба теста, которые я называл ключевыми, на месте:
`addressRateLimit_writesExactlyOneRecordPerWindow` — превышение лимита пишет одну запись
за окно, а не по записи на каждую отбитую попытку, иначе журнал стал бы усилителем атаки;
`refundWithUnknownOutcome_isRecordedEvenThoughTheTransactionRolledBack` — запись о возврате
с неизвестным исходом сохраняется, хотя локально ничего не закоммитилось. Второй сделан
и для списания холда, чего я не просил.

**Дозапись обеспечена типом, а не соглашением.** `AuditLogRepository` намеренно **не**
наследует `JpaRepository`: в интерфейсе есть только `save`, поэтому `delete`, `deleteAll`
и массовых обновлений просто не существует. В javadoc — «не расширять этот интерфейс».

**В развёртывании роль `mp_app`** (не суперпользователь) с `REVOKE UPDATE, DELETE, TRUNCATE
ON audit_logs` — `TRUNCATE` я не упоминал, а им журнал стирается одной командой. Добавлены
`ALTER DEFAULT PRIVILEGES`, чтобы права распространялись на будущие таблицы, и явное
предупреждение, что до смены `DB_USERNAME` защита не действует: на суперпользователя
`REVOKE` не влияет.

Проблема с привилегиями записана целиком — `problems.md` §13, с планом перехода на `mp_app`.
Порядок старта закрыт тестами в `auth` (`MigrationOrderTest`) и `directory`
(`SharedSchemaMigrationTest`).

### 21.08.2026 — P2-1 + P2-3 (ТЗ)

Решения Р-44, Р-45.

Уточнил приоритет с заказчиком: три списка (пользователи, компании, терминалы) ограничены
бизнесом и сами по себе не бомба — по-настоящему растущие таблицы, транзакции и журнал
аудита, уже разбиты на страницы. А вот индексы — да.

**На `transactions` ровно один индекс** — `idx_transactions_provider_order`. Не покрыто:

- `link_id`, по которому фильтруют пять методов репозитория. `OpenLinkService:149, 152, 160,
  182` — это **три-четыре таких запроса на каждое открытие ссылки плательщиком**, плюс по
  одному при списании холда (`:598`), обновлении (`:231`), просмотре карточки (`:415`)
  и каждом опросе статуса (`:1061`). Каждый — полный проход по таблице транзакций, самой
  быстрорастущей в системе;
- `status` + `created_at`, по которым выбирает фоновая сверка
  (`findByStatusAndCreatedAtBetweenOrderByCreatedAtAsc`). Она работает **каждые две минуты,
  круглосуточно**, и тоже читает таблицу целиком — независимо от того, есть ли платежи.

Проверил заодно, нет ли N+1 при листинге ссылок — нет, все вызовы `countByLinkIdAndStatus`
одиночные.

### 22.08.2026 — P2-3 + P2-1 (сделано)

Индексы на `transactions` и пагинация трёх последних листингов. Решения Р-44 и Р-45
реализованы как приняты.

**573 теста зелёные** (+21 к 552), прогон в 02:16 — после последней правки бэкенда.
`:common` — 80, `:auth` — 84 (было 78), `:directory` — 81 (было 67), `:pbl` — 328 (было 327).
Методов 477 (было 456). `cd frontend && npm run build` — чисто.

**Индексы (P2-3).** Новый `pbl/007-transaction-indexes.xml`, три changeset'а, по одному на
индекс, каждый под своим `<not><indexExists/></not>`:

- `idx_transactions_link_status` `(link_id, status)` — `countByLinkIdAndStatus`,
  `countByLinkIdAndStatusIn`, `existsByLinkIdAndStatusIn`, а по префиксу и любой запрос
  только по `link_id`, включая проверку внешнего ключа при удалении родительской строки;
- `idx_transactions_link_created` `(link_id, created_at DESC)` —
  `findByLinkIdOrderByCreatedAtDesc` и `findFirstByLinkIdAndStatusInOrderByCreatedAtDesc`;
- `idx_transactions_status_created` `(status, created_at)` — выборка фоновой сверки, плюс
  `countByStatusAndCreatedAtBefore`, который считает залежавшиеся `PENDING`. Порядок
  **возрастающий**: сверка разгребает очередь от старых к новым.

Больше ни одного — каждый индекс замедляет вставку, а строка в `transactions` пишется на
каждом платеже. В комментарии к changeset'у записано, какой запрос закрывает каждый индекс и
почему `link_id` без индекса — это отдельная беда: PostgreSQL внешние ключи автоматически
не индексирует.

**Пагинация (P2-1).** Три листинга приняли `Pageable` и отдают `PagedResponse` из `common`;
контроллеры принимают `page`/`size` с умолчаниями `0`/`20` и потолком 200, значения
приводятся, а не отвергаются — ровно как `AuditLogController`. Ролевые правила не тронуты
ни в одном из трёх.

**Две вещи сверх буквы ТЗ, обе вынужденные.**

1. **Фильтр soft-deleted пришлось перенести в запрос.** `listUsers` и `listCompanies`
   отсеивали `DELETED` через `stream().filter` **после** чтения. Оставь это как есть — и
   страницы выходили бы короче `size`, а `totalElements` считал бы записи, которых вызывающий
   не увидит. Заведены `findAllByStatusNot` и `findAllByCompanyIdAndStatusNot`; в
   `CompanyService` литерал `"DELETED"` из четырёх мест собран в константу.
2. **`TerminalService` пришлось слегка переставить.** `listTerminals` и новый
   `listTerminalOptions` делят одни и те же ролевые проверки, поэтому они вынесены в
   `isGlobalReader` и `requireOwnCompany`. Правила и тексты отказов те же, включая запись
   отказа в журнал; проверено `DirectoryIntegrationTest` и новыми тестами.

**Лёгкий список терминалов (Р-45).** `GET /api/v1/terminals/options` — `id`, `name`, `status`,
без пагинации. Учётных данных в `TerminalOptionResponse` **нет вовсе** — не замаскированы, их
там просто нет, и тест проверяет это по сырому телу ответа, а не по разобранным полям: поле,
добавленное сюда позже по невнимательности, тоже покраснеет.

Заблокированные терминалы эндпоинт отдаёт. Это единственное место задачи, где легко «навести
порядок» и всё сломать: форме создания ссылки нужны только `ACTIVE`, экрану транзакций — все,
потому что он подставляет имена терминалов в старые платежи. Отфильтруй на сервере — и на
экране транзакций у платежей через снятый с обслуживания терминал вместо имени появится
`Terminal #N`. Написано в javadoc сервиса, контроллера и DTO, в `directory.md` и в «граблях»
`AGENTS.md`.

**Фронтенд (Р-44).** `UsersPage`, `CompaniesPage`, `TerminalsPage` получили `TablePagination`
с перезапросом при смене страницы и размера и с настоящим `totalElements` в счётчике. Создание
и удаление строки больше не правят массив локально, а перечитывают страницу: сортирует и режет
на страницы сервер, и дописанная в конец строка оказалась бы не на своём месте, а страница —
длиннее `size`.

Клиентский поиск на трёх страницах **оставлен и подписан**: он фильтрует загруженный массив, а
массив теперь — одна страница. Под полем поиска на всех трёх экранах стоит
`common.searchOnPage` («ищет по текущей странице», три языка). Серверный поиск — отдельная
работа, и не маленькая: `ILIKE '%…%'` обычным btree не обслуживается, нужен `pg_trgm` или
ограничение префиксом. Записано в `problems.md` §14.

**Два потребителя переведены на `options` сверх названных в ТЗ.** ТЗ называло `PayByLinkPage`
и `App.tsx`; `TransactionDetailPage:79` тянула тот же `/api/v1/terminals` ради имени терминала
и после пагинации молча перестала бы его находить (`Array.isArray(res.data)` на `PagedResponse`
— `false`, карта имён пустая). Врезки со списками в `SettingsPage` пагинации не получили —
они берут одну страницу по потолку (`size: 200`), то есть до двухсот записей ведут себя как
раньше; записано в `problems.md` §14.

**Повисшая ссылка перенацелена.** Запись от 21.08.2026 обещала, что `TransactionController.list`
в `pbl` (сырые `page`/`size`, 500 на `size=0`, размер страницы без потолка) починит P2-1. P2-1
закрыт без этого: ТЗ прямо запрещало трогать пагинацию транзакций и ссылок. Проблема записана
в `problems.md` §15 — и не одним эндпоинтом, а двумя: `PaymentLinkController.list` устроен так
же. Формулировка «умолчания одни на всех» в «граблях» `AGENTS.md` названа с этими двумя
исключениями, чтобы документ не утверждал того, чего в коде нет.

**Приёмка ТЗ:** `grep -c "createIndex" …/00*-transaction-indexes.xml` → 3; `findAll()` нет ни в
одном из трёх сервисов; в `TerminalOptionResponse.java` нет ни `login`, ни `password`;
`./gradlew test` и `npm run build` зелёные.

**Не проверено:** ручные пункты 10-12 (переключение страниц на трёх экранах, отсутствие
заблокированного терминала в форме создания ссылки, имя терминала у старого платежа) — это
требует поднятой базы и трёх сервисов, чего в этой сессии не было. Поведение закрыто
автотестами со стороны API (`options` отдаёт `BLOCKED`, страницы не пересекаются), но клик
руками за них не сделан.

**Документация:** `AGENTS.md` (§9 — постраничные экраны и два запроса за терминалами; §10 —
запись P2-3+P2-1 в закрытых, четыре «грабли» вместо старой про непагинированные листинги; §11 —
три новых класса тестов, итоги 477/573, пометки к `AuthIntegrationTest` и
`DirectoryIntegrationTest`; подвал); `fix_plan.md` (таблица спринта 2, эта запись, перенацеленная
ссылка в записи от 21.08.2026); `code_review.md` (P2-1 и P2-3 в §4, пункт 13 плана §8);
`problems.md` §14 (поиск по текущей странице, плюс перенацеленное обещание пагинации
`AuditLogsPage`) и §15 (непроверенные `page`/`size` в `pbl`);
`auth/auth.md` (контракт `GET /api/v1/users`); `directory/directory.md` (`GET /api/v1/companies`,
`GET /api/v1/terminals`, новый `GET /api/v1/terminals/options`); `application_description.md`
(три таблицы эндпоинтов, §9.3 — новый changeset `pbl/007` и заодно два пропущенных с P2-14);
Postman-коллекции `auth` и `directory` — `page`/`size` у трёх листингов и новый запрос
`Terminal Options (lightweight)`.

Замечено попутно и **не** правилось: в коллекции `directory` остался запрос
`DELETE Delete Terminal`, который с P2-8 отвечает 405, — хвост той задачи, не этой.

### 21.08.2026 — P2-3 + P2-1 ✅

573 теста зелёные (+21 к 552), прогон в 22:27, кода после него не трогали.
Фронтенд: исходники правились до 22:18, `dist` собран в 22:29 — проверка типов прошла.

**Индексы.** На `transactions` теперь четыре вместо одного: добавлены
`idx_transactions_link_status`, `idx_transactions_link_created` и
`idx_transactions_status_created`. `TransactionIndexSchemaTest` проверяет не только наличие,
но и **направление сортировки** колонок — то есть что `created_at DESC` действительно `DESC`,
а не просто присутствует.

**Пагинация.** `findAll()` в трёх сервисах не осталось. Сортировка везде с уникальным
довеском (`Sort.by(asc("username"), asc("id"))` и аналогично), и это закрыто именно теми
тестами, которые я просил:
`companiesWithTheSameName_appearExactlyOnce_acrossPages` и такой же для терминалов. Без
довеска пагинация выглядит рабочей ровно до первой пары одинаковых имён.

**Лёгкий эндпоинт.** `TerminalOptionResponse` — только `id`, `name`, `status`; учётных данных
терминала нет, и на это есть отдельный тест `options_carryNoTerminalCredentials`.
Заблокированные терминалы отдаются (`options_includeBlockedTerminals`) — иначе на экране
транзакций у старых платежей пропало бы имя терминала.

**Честность вместо тихой поломки.** Клиентский поиск на страницах теперь видит только
текущую страницу — серверного поиска по пользователям нет, и выдумывать его в рамках этой
задачи не стали. Вместо того чтобы оставить ловушку, под полем поиска добавили подпись
(`helperText={tObj.common.searchOnPage}`), а в коде — комментарий, почему так. Серверный
поиск записан как отдельная будущая работа.

Дополнительно: `/options` объявлен выше `/{id}` с комментарием, почему порядок важен.

### 21.08.2026 — дата оплаты ссылки (ТЗ)

Решения Р-46, Р-47, Р-48.

`PaymentLinkResponse` отдаёт `createdAt` и `expiresAt` и ничего про платёж. На фронте поле
`paidAt` в типе есть, маппинг его не заполняет. Из-за этого:

- в списке (`PayByLinkPage:586`) у **оплаченной** ссылки в колонке показывается её срок
  действия, потому что ветка «Paid + дата» недостижима;
- на карточке не рисуется поле «когда оплачено» (`:620`) и не появляется строка в ленте
  событий (`:127`);
- `buildLinkedTxns` (`:200`) начинается с `if (!link.paidAt) return []` — то есть таблица
  связанных операций на карточке **пуста всегда**.

**Главная находка, из-за которой задача не сводится к добавлению поля.** Тот же
`buildLinkedTxns` — генератор выдуманных данных: `link.cardNetwork ?? 'Visa'`,
`link.cardLast4 ?? '4242'`, `id: link.transactionId ?? \`TXN-${link.shortCode}-001\``,
и синтетические строки SMS/DMS, собранные из полей ссылки, а не из реальных транзакций.
В ленте событий рядом — `new Date(link.paidAt.getTime() + 3 * 1000)`, придуманная отметка
времени «через три секунды».

Сегодня всё это мертво ровно потому, что `paidAt` не приходит. **Стоит начать его отдавать —
и на карточке платёжной ссылки появится несуществующая транзакция с картой «Visa ···· 4242»
и выдуманным номером.** В платёжном портале, рядом с настоящими операциями. Поэтому Р-48:
генератор удаляется в этой же задаче, до включения поля.

**Вторая тонкость.** `refund()` меняет статус самой транзакции на `REFUNDED` /
`PARTIALLY_REFUNDED`, отдельной строки не создаёт. Значит «когда оплатили» нельзя искать
только по `SUCCESS`: у возвращённого платежа дата оплаты пропала бы. Нужен набор
`SUCCESS` + `REFUNDED` + `PARTIALLY_REFUNDED`.

**Третья.** Список ссылок собирается без единого запроса на строку (`toSummary` — проекция
самой ссылки). Дата оплаты «в лоб» превратила бы это в запрос на каждую строку. Нужен один
пакетный запрос на страницу.

Побочное наблюдение, не в этой задаче: `currentPaymentsCount` считается только по `SUCCESS`,
поэтому после возврата счётчик платежей по ссылке уменьшается, а статус `COMPLETED`
не пересчитывается. Живой ошибки нет, но цифра на карточке ведёт себя неожиданно.

### 22.08.2026 — P2-15 (сделано)

Дата оплаты платёжной ссылки. Решения Р-46, Р-47 и Р-48 реализованы как приняты.

**583 теста зелёные** (+10 к 573), прогон в 03:12. `:common` — 80, `:auth` — 84,
`:directory` — 81, `:pbl` — 338 (было 328). Методов 487 (было 477).
`cd frontend && npm run build` — чисто.

**Порядок работы был именно тот, что в ТЗ: сначала снос (Р-48), потом поле.** Это не
формальность. Построитель запасных строк на карточке ссылки — `if (!link.paidAt) return []`
и дальше сочинённая транзакция: карта по умолчанию, идентификатор из короткого кода ссылки,
пары SMS / DMS-Auth / DMS-Capture, выведенные из полей ссылки. Включи `lastPaidAt` первым — и
на карточке платёжной ссылки, рядом с настоящими операциями, появилась бы несуществующая
транзакция с выдуманным номером карты. Сначала удалено, потом включено.

**Сверх названного в ТЗ удалено ещё четыре подстановки — все на пути того же поля.**
ТЗ просило «пройтись по остальным умолчаниям на этих двух страницах»; вот что нашлось, и
каждое оживало ровно вместе с датой оплаты:

1. `txn.clientIp || '127.0.0.1'` и `txn.userAgent || 'Macintosh; Intel Mac OS X'` в таблице
   связанных операций — приписывали настоящему платежу обстоятельства, которых никто не
   наблюдал. Теперь прочерк.
2. Строки блока Payment Details: «Transaction ID» (`?? '—'`), «Payment Method»
   (`{cardNetwork} ···· {cardLast4}` → нарисовало бы `undefined ···· undefined`) и «Payer IP»
   (пустая строка). Ни одного из трёх полей API по ссылке не отдаёт — строки убраны целиком.
3. **Самое неприятное:** стадия DMS была тернарником, у которого ветка «иначе» означала
   «Finalized — Captured». `dmsStatus` маппинг не заполняет, поэтому с приходом даты оплаты
   **каждая** DMS-ссылка объявлялась бы captured — про холд, который на самом деле может
   висеть неснятым. Теперь строка показывается, только когда стадия известна.
4. Событие «Customer Redirected» с отметкой «оплата + 3 секунды» — удалено вместе с
   `redirectUrl`, которого в API нет.

Флаг `isReal` в таблице (`!!txn.createdAt`) отличал сочинённые строки от настоящих — вместе с
сочинёнными он потерял смысл и убран. Мёртвые импорты иконок (`CardIcon`, `SecurityIcon`)
сняты.

**`PAID_STATUSES`.** `SUCCESS` + `REFUNDED` + `PARTIALLY_REFUNDED`, рядом с
`AMOUNT_LOCKING_STATUSES`. `AUTHORIZED` в набор **не** входит, и это отдельное решение,
записанное в javadoc: холд DMS — зарезервированные деньги, а не взятые; дата оплаты появляется
при списании. Тесты закрывают обе стороны — и что возврат дату не теряет, и что холд её не
создаёт.

**Пакетный запрос.** `findLastPaidAtByLinkIds` — JPQL с `GROUP BY`, один запрос на страницу,
склейка в память. Написан на JPQL, а не нативным SQL: так enum-статусы и `UUID` связываются
типобезопасно и одинаково работают на H2 и PostgreSQL, и так же написан единственный другой
`@Query` в `pbl` (`PaymentLinkRepository.search`). SQL, в который он разворачивается, приведён
в javadoc дословно — по нему и проходит приёмочный grep из ТЗ (`GROUP BY link_id`); в самом
JPQL стоит `GROUP BY t.link.id`. Пустая страница запрос не выполняет вовсе.

**Главный тест проверен обратным ходом.** `listOfTwentyLinks_resolvesDatesWithASingleQuery`
утверждает три вещи: пакетный метод вызван ровно раз, поштучный — ни разу, и число
подготовленных запросов Hibernate у страницы из 20 строк равно числу у страницы из одной.
Реализация была временно подменена на поштучный поиск: тест краснеет **обоими** способами по
отдельности — мок сообщает «findLastPaidAtByLinkIds wanted but not invoked», а счётчик
statements даёт «one row: 3, twenty rows: 22». Ровно те 19 лишних запросов, ради которых
тест и написан. После проверки реализация возвращена.

**Фронтенд.** `paidAt` заполняется из `lastPaidAt` на обеих страницах. В списке «Оплачена» с
датой — только у `COMPLETED` (Р-47): у активной многоразовой ссылки с тремя платежами из пяти
на экране должен остаться срок, потому что он ещё может измениться, а дата платежа видна на
карточке. Подпись — `payByLink.paid`, три языка. Поле «Paid on» в шапке карточки, строка
«Payment Received» в ленте событий и алерт по SMS-платежу заработали сами.

**Записано как отдельные будущие работы:** `problems.md` §16 — `currentPaymentsCount` считается
только по `SUCCESS`, поэтому после возврата счётчик уменьшается, а `COMPLETED` не
пересчитывается (ТЗ прямо запрещало это трогать; расхождение с `lastPaidAt`, который возврат
учитывает, теперь заметно); `problems.md` §17 — чего карточка ссылки после сноса не показывает
(карта, номер транзакции, адрес плательщика, стадия DMS) и что кнопка списания холда DMS
недостижима, а на её недостижимой ветке обработчик шлёт идентификатор ссылки вместо
идентификатора транзакции.

**Приёмка ТЗ:** генератор удалён (оба grep'а пусты); `PAID_STATUSES` на месте; `GROUP BY link_id`
находится; `./gradlew :pbl:test` и `npm run build` зелёные.

**Не проверено:** ручные пункты 10-12 (в списке «Оплачена» у завершённой и срок у активной;
дата оплаты и строка ленты на карточке; отсутствие выдуманных строк в таблице связанных
операций) — нужна поднятая база и запущенные сервисы, чего в этой сессии не было. Пункт 12
косвенно закрыт статически: в файле не осталось ни `'4242'`, ни `?? 'Visa'`, ни шаблона
`TXN-…`, а таблица получает строки только из ответа API.

**Документация:** `AGENTS.md` (§9 — карточка без выдуманных данных, `paidAt` вычеркнут из
списка «всегда undefined»; §10 — запись P2-15 в закрытых, три новые «грабли», перенацеленная
пометка в записи P2-13 про мёртвый словарь связанных транзакций; §11 — `PaymentLinkLastPaidAtTest`
и итоги 487/583; подвал); `fix_plan.md` (таблица спринта 2, эта запись); `problems.md` §16 и §17;
`pbl/pay-by-link.md` (`lastPaidAt` в §5.1 и в списочном ответе — что значит, когда отсутствует
и почему возврат считается); `application_description.md` (перечень DTO и новый раздел про
`lastPaidAt` рядом с разделом про `expiresAt`); `frontend/.../payByLinkData.ts` (комментарий у
`paidAt` — поле больше не из числа «всегда undefined»). `code_review.md` не трогался: задача
выросла из решений Р-46…Р-48, пункта в ревью у неё нет.

### 22.08.2026 — дата оплаты ссылки ✅ (P2-15)

583 теста зелёные (+10 к 573), кода после прогона не трогали. Фронт собран после правок.

Замечаний нет. Порядок соблюдён: генератор выдуманных данных удалён **до** включения поля.
`buildLinkedTxns`, `LinkedTxn`, `?? 'Visa'`, `?? '4242'` и синтетический
`TXN-…-001` в `PayByLinkDetailPage` не находятся вовсе. Таблица связанных операций теперь
запрашивает `GET /api/v1/payment-links/{id}/transactions` и при пустом ответе честно пишет,
что операций пока нет.

Три теста, которые я называл ключевыми, на месте:
`listOfTwentyLinks_resolvesDatesWithASingleQuery` — страница из двадцати ссылок разрешает
даты **одним** запросом, а не двадцатью; `emptyPage_doesNotQueryForDatesAtAll`;
`refundedPayment_keepsItsPaymentDate` вместе с вариантом для частичного возврата — дата
оплаты не исчезает после возврата.

**Сверх ТЗ:** `authorizedHold_isNotAPayment` — авторизованный холд DMS не считается оплатой.
Я этого не оговаривал, и это верно: холд — не списанные деньги, и подписывать такую ссылку
«оплачена» было бы неправдой.

В списке «Оплачена» показывается только при `status === 'COMPLETED' && link.paidAt` (Р-47).

### 24.08.2026 — P2-16 (сделано)

Возврат больше не отменяет факт использования ссылки (Р-49, Р-50). Проблема была в том, что
«сколько раз ссылкой воспользовались» считалось по одному `SUCCESS`, а `refund()` переписывает
статус самой транзакции — после возврата платёж исчезал из всей арифметики: цифра на карточке
падала, слот освобождался (ссылка «разрешено 3» собирала 4), частичный возврат 1 из 100 стирал
платёж целиком.

Что сделано:

1. **`PAID_STATUSES` переехал из `PaymentLinkService` в `TransactionStatus`** — виден обоим
   сервисам и стал единственным ответом на «платёж состоялся?»: `currentPaymentsCount` и колонка
   `current_payments_count`, `lastPaidAt` (P2-15), слоты и проверки открытия (`OpenLinkService`),
   запрет понижать `maxPayments` (P2-9). В javadoc набора — прямым текстом: возвращённый платёж —
   это состоявшийся платёж; не сужать набор обратно до `SUCCESS`.
2. **`SLOT_OCCUPYING_STATUSES` выводится** из `PAID_STATUSES` + `AUTHORIZED` (static-блок,
   `EnumSet.copyOf`) — наборы не разъедутся, отдельное основание холда (P1-6) не потеряно.
3. **Смысл `currentPaymentsCount` записан** в javadoc `PaymentLinkResponse` (имя прежнее — поле
   уже в API). Колонка получает то же число при каждом расчёте (`completeDms`, `refreshStatus`);
   `refund()` ссылку не трогает — и не должен: возврат не выводит статусы из набора, база и ответ
   сходятся сами. Заодно выяснилось, что колонка и раньше фактически хранила «использования»
   (возврат её никогда не уменьшал) — миграции не нужно, расходился только ответ API.
4. **`refundedPaymentsCount`** (`REFUNDED` + `PARTIALLY_REFUNDED`) — только в
   `PaymentLinkResponse`; в списочный DTO счётчики не добавлены (N+1). Оба счётчика — по
   `idx_transactions_link_status`.
5. **Фронтенд:** `refundedCount` в типе и маппингах; на карточке под «N / M» строка
   «из них возвращено: K» только при K > 0 (`payByLinkDetail.summary.refundedOfUsed`,
   en/az/ru). Список не тронут.

**Сверх ТЗ:** одиночный `countByLinkIdAndStatus` удалён из `TransactionRepository` совсем —
после перевода всех мест на набор у него не осталось вызовов, а оставить его — значит оставить
дорожку обратно к счёту по голому `SUCCESS`. Приёмочный `grep` из ТЗ это и требовал по духу.

Тесты: `PaymentLinkRefundUsageTest`, 10 методов — все девять сценариев ТЗ. Главный:
лимит 3, два платежа, один возвращён → ссылка принимает ровно один платёж и закрывается;
четвёртое открытие отбито. Открытие `ACTIVE`-ссылки со слотами, занятыми в т.ч. возвращённым
платежом, отказано до похода к эквайеру — при этом выяснилось и зафиксировано в тесте, что
запись `COMPLETED` на пути открытия штатно откатывается вместе с отказом (долговечные переходы
делает платёжный путь; это поведение существовало и до P2-16). Частичный возврат и возврат
одноразовой — через настоящий эндпоинт `/refund` с моком эквайера по §5.7.

`./gradlew test` — 593 запуска зелёные (+10 к 583: `:pbl` 338 → 348), 497 методов.
`cd frontend && npm run build` и `tsc -b` — чисто. Приёмочные greps из ТЗ проходят:
`countByLinkIdAndStatus(...SUCCESS)` не находится ни в одном из двух сервисов,
`PAID_STATUSES`/`AUTHORIZED` в `OpenLinkService` на месте, счётчиков в
`PaymentLinkSummaryResponse` нет.

Документы: `AGENTS.md` (§10 закрытый блокер, две «тонкости», §11 таблица + итоги, футер),
`pay-by-link.md` (смысл `currentPaymentsCount`, новое поле, отказ `maxPayments`, «возврат не
открывает ссылку», отсутствие счётчиков в списке), `problems.md` §16 закрыт, `code_review.md`
§5 — пометка у пункта про непоследовательную колонку, `application_description.md` — упоминания
счётчика. Ручная проверка карточки в браузере не делалась: нужен живой эквайер с проведённым
возвратом; фронт проверен типами и сборкой, логика — интеграционными тестами (как в P2-15).

### 24.08.2026 — возврат и счётчик использований ✅ (P2-16)

593 теста зелёные (+10 к 583). Фронтенд правился уже после прогона бэкенда (тестов там нет),
но собран следом: последний исходник 09:33:07, последний файл сборки 09:33:21 — то есть
`tsc -b` прошёл на новом коде.

Замечаний нет. Более того, реализация решила задачу аккуратнее, чем ТЗ.

**`PAID_STATUSES` переехал на сам enum `TransactionStatus`,** а не остался в сервисе. Это
правильнее: набором пользуются оба сервиса, и на перечислении он находится сам собой,
без импорта из чужого класса. В javadoc — прямая фраза «не оптимизируйте этот набор
до `SUCCESS`» и объяснение, почему `AUTHORIZED` в него намеренно не входит: холд — это
зарезервированные деньги, а не взятые.

**`SLOT_OCCUPYING_STATUSES` не перечислен заново, а выведен** из `PAID_STATUSES` плюс
`AUTHORIZED`, в статическом блоке, с комментарием «чтобы эти два набора никогда не разъехались».
Я в ТЗ просил именно такой состав, но списком; вывод из общего набора лучше — при следующей
правке одного второй поедет за ним автоматически.

Оба сценария, которые я называл опасными, закрыты тестами:
`refundDoesNotFreeASlot_linkAcceptsOneMorePayment_notTwo` — многоразовая ссылка после
возврата принимает **ещё один** платёж, а не два; `partialRefund_leavesTheUseCounted` —
возврат одного маната из ста не отменяет использования.

И страховка, ради которой писался пункт 7 ТЗ: `authorizedHold_stillOccupiesASlot`. При таком
рефакторинге легче всего заменить весь набор на `PAID_STATUSES` и потерять холд — тогда
ссылка с живой авторизацией на карте стала бы открываемой, и это было бы второе списание.

`refundedPaymentsCount` есть только в карточке; в списочном ответе счётчиков нет — N+1
не вернулся.

### 24.08.2026 — P3-2 (сделано)

Единый словарь событий аудита и таблица для приёмки — по ТЗ, все пять шагов.

**Шаг 1, словарь.** `common/src/main/java/az/millikart/common/audit/AuditEntity.java` и
`AuditAction.java` — финальные классы строковых констант (не enum — колонки строковые, enum
требовал бы конвертер и падал бы на чтении значения вне списка; это записано в javadoc класса).
Все вызовы в трёх сервисах переведены; приватные `USER_ENTITY`, `AUTH_ENTITY`, `LINK_ENTITY`,
`TRANSACTION_ENTITY` и литералы в `directory` и `AdminBootstrapRunner` удалены.

**Шаг 2, расхождения.** `ACCESS` → `AuditAction.READ` в обоих местах `pbl`
(`PaymentLinkService.validateAccess`); действия `ACCESS` в словаре нет, и javadoc
`AuditAction` прямо запрещает его возвращать. `entityId` у `AUTH` — всегда логин: успешный
вход пишет `user.getUsername()` вместо UUID; `RATE_LIMIT` — логин вместо IP (адрес остаётся в
`client_ip`); `TOKEN_REUSE` — тоже логин (правило ТЗ «всегда логин» применено и к нему, хотя в
списке расхождений он не значился: достаётся по `stored.getUserId()` одним `findById`, ради
единственной записи о краже — оправдано; фолбэк — UUID строкой, если пользователя вдруг нет).
`"ALL"` оставлен и зафиксирован соглашением в таблице; асимметрия `companyId` успех/отказ
оставлена и вынесена в таблицу отдельным пояснением.

**Шаг 3, дыры.** Logout: `AuthService.logout` публикует `AuditEvent` `AUTH`/`LOGOUT` **только
при `revoked > 0`** — повторный logout того же токена и незнакомый токен не пишут ничего
(«нашёлся и был отозван» из ТЗ прочитано буквально: у мёртвой семьи отзывать нечего). Запись
через событие, а не синхронно: она должна появиться только если отзыв закоммитился. Смена
статуса компании — `COMPANY`/`BLOCK`|`UNBLOCK` по образцу `UserService` (любой уход с `ACTIVE`
— BLOCK, возврат — UNBLOCK, эхо того же статуса события не порождает); общий `UPDATE` остался.
`AuditOutcome.UNRESOLVED` добавлен (в `varchar(16)` влезает, CHECK-ограничения на колонке нет),
`logUnresolved` пишет его вместо `SUCCESS`; юнион на фронте
(`frontend/src/app/types/dto.ts`) дополнен `'UNRESOLVED'` — единственная правка фронтенда,
поведение не меняется (аудит на фронте не рендерится).

**Шаг 4, таблица.** `technical_handover.md` §4.4: 30 строк — по одной на каждое реальное
сочетание `entityType`/`action` (AUTH×5, USER×6, COMPANY×7, TERMINAL×6, AUDIT_LOG×1,
PAYMENT_LINK×3, TRANSACTION×2), колонки по ТЗ. Под таблицей пять пояснений: `"ALL"`,
асимметрия `companyId`, «успешные LIST/READ не журналируются намеренно», append-only
(репозиторий с одним `save` + REVOKE на уровне БД), переживание отката + маркер
`AUDIT_WRITE_FAILED`. Устранено и внутреннее противоречие §4.4: висел абзац «действия над
пользователями и ссылками в журнал не попадают» времён до P2-14.

**Шаг 5, сторож.** `AuditLogService.warnIfOutsideDictionary` на всех трёх путях записи
(`recordSuccess`, `logDenied`, `logUnresolved`): значение вне словаря пишется как есть, в лог
приложения уходит WARN с маркером `AUDIT_OUTSIDE_DICTIONARY` (константа там же, рядом с
`AUDIT_WRITE_FAILED`). Исключений валидация не бросает.

**Тесты** — `./gradlew test`: 501 метод / 597 запусков, зелёные
(`:common` 80, `:auth` 86 (+2), `:directory` 83 (+2), `:pbl` 348).
Новые: `AuthAuditIntegrationTest.logoutWithLiveToken_isRecordedAgainstTheLogin`,
`logoutWithUnknownOrSpentToken_writesNothing`;
`AuditLogIntegrationTest.companyStatusChange_isItsOwnBlockAndUnblockEvent`,
`valueOutsideDictionary_isWrittenAsIs_andWarnedWithMarker`.
Обновлённые проверки: успешный вход — `entityId` = логин; `RATE_LIMIT` — логин в `entityId`,
адрес в `client_ip`; `pbl` — `single("READ")` вместо `single("ACCESS")` + `entityType TERMINAL`;
оба теста неизвестного исхода — `outcome = UNRESOLVED`. Их падение при переименовании было
ожидаемым и подтвердило, что тесты проверяют значения, а не факт записи.

**Приёмка.**
`grep -rn '"USER"\|"AUTH"\|"COMPANY"\|"TERMINAL"\|"PAYMENT_LINK"\|"TRANSACTION"\|"AUDIT_LOG"'
*/src/main/java --include=*.java | grep -v AuditEntity.java` — пусто;
`grep -rn '"ACCESS"' */src/main/java --include=*.java` — пусто;
строк в таблице §4.4 — 30, ровно по числу сочетаний в коде.

**Документы.** `technical_handover.md` §4.4 (таблица); `directory/directory.md` §3.3 (три
значения `outcome`, ссылка на словарь и таблицу); `auth/auth.md` §4.1.2 (logout журналируется,
контракт эндпоинта не изменился); `AGENTS.md` §10 (закрытый блокер + тонкость про словарь),
§11 (счётчики), сноска про §4.4; `frontend/src/app/types/dto.ts` (юнион `outcome`).
`problems.md` не тронут — отложенного нет; в `code_review.md` пункта у задачи нет (ТЗ, не ревью).
Postman-коллекции не тронуты — контракты эндпоинтов не менялись (у logout поведение по HTTP
прежнее). БД пересоздаётся, миграция значений не нужна (по ТЗ).


### 24.08.2026 — P3-2 (проверено, Р-51)

Проверено статически: тесты 597, все зелёные (auth 86, common 80, directory 83, pbl 348;
было 593). Прогон в 10:55–10:56, исходники правились в 10:47–10:52 — тесты гоняли после правок.

Все критерии приёмки ТЗ выполнены буквально: литералов сущностей в проде нет,
`"ACCESS"` нет, приватных `*_ENTITY` нет; в таблице `technical_handover.md` §4.4
ровно 30 строк-сочетаний, как и заявлено под ней.

Проверка словаря вызывается из **всех трёх** путей записи (`recordSuccess`, `logDenied`,
`logUnresolved` — строки 88, 119, 149), а не только из одного. Это то место, где такая
проверка обычно и остаётся мёртвой; здесь она живая. И она действительно только `log.warn`,
исключение не бросается — тест `valueOutsideDictionary_isWrittenAsIs_andWarnedWithMarker`
проверяет и отсутствие исключения, и запись значения как есть, и оба warning'а.

Утверждения таблицы выборочно сверены с кодом — имена методов (`CompanyService.updateCompany`,
`PaymentLinkService.completeDms`/`refund`, `TerminalService.validateWriteAccessToCompany`),
тексты `details` (`Login updated` / `Password updated` без значений), `performedBy = system`
у bootstrap. Расхождений не нашёл.

**Что исполнитель сделал лучше ТЗ:**

1. **Logout пишется не «если токен нашёлся», а если `revoked > 0`.** Я написал первое,
   правильное — второе: повторный выход с уже отозванным токеном тоже не должен оставлять
   записи. Формулировка в javadoc точная: 204 на незнакомый токен — это маскировка,
   а маскировка не имеет права оставлять следы.
2. **Запись о выходе публикуется событием, а не пишется синхронно** — то есть появится,
   только если отзыв токенов закоммитился. В ТЗ этого не было.
3. **`TOKEN_REUSE` тоже переведён на логин.** Я задал правило «у `AUTH` всегда логин», но
   перечислил только вход, lockout и лимит; исполнитель применил правило и к четвёртому
   месту, где раньше был UUID. Ради этого добавлен `loginOf()` — один лишний запрос на
   событие, которое читает человек, ищущий всё по аккаунту. Размен верный.
4. **Тест на «повтор того же статуса не должен фабриковать BLOCK»**
   (`companyStatusChange_isItsOwnBlockAndUnblockEvent`, последняя треть). PATCH со
   `status = ACTIVE` у уже активной компании пишет только `UPDATE`. Я этого не просил,
   а это ровно тот случай, на котором такие события обычно и начинают шуметь.

**Мелочь, менять не нужно, но пусть будет записано:** в `logout`, если пользователя уже нет,
в `entityId` уходит `userId.toString()` — то есть UUID, вопреки правилу «у `AUTH` всегда
логин», записанному в javadoc `AuditEntity` и в §4.4. Практически недостижимо:
`UserService.deleteUser` отзывает все refresh-токены удаляемого, так что живого токена
без пользователя быть не должно. Оставляю как есть — альтернатива (не писать запись вовсе)
хуже.

### 24.08.2026 — P3-1 (сделано)

Серверный поиск по четырём спискам и пагинация журнала аудита — по ТЗ, части A–E.

**Общее.** `common/src/main/java/az/millikart/common/search/SearchTerms.java` — единственное
место нормализации (`normalize`: trim, пустое/пробельное → null, обрезка до 100) и построения
LIKE-паттерна (`toLikePattern`: нижний регистр, `%`/`_`/`!` экранируются, обёртка `%…%`).
Escape-символ — `!`, а не `\` из примера ТЗ: паттерн скармливается JPQL, Criteria и нативному
SQL на H2 и PostgreSQL, и бэкслеш требовал бы собственного экранирования на нескольких слоях;
семантика ровно та, которую требует ТЗ (`%` и `_` ищутся буквально). Требование «никакого
полнотекстового поиска, `pg_trgm`, GIN» записано в javadoc класса — отсутствие индекса
читается как решение, не забывчивость.

**Часть A, users.** `UserController.list`: `search` (норма — в контроллере) и `role`
(trim + upper-case, значение вне `Role.fromValue` → отсутствие фильтра, не 400).
`UserRepository.search` — `@Query(nativeQuery = true)` с отдельным `countQuery`:
`LEFT JOIN companies` (таблица `directory`) ради поиска по **названию компании**;
кросс-модульный долг записан новым пунктом `problems.md` §18 (по образцу
`PaymentLinkStatusRepository`). Поиск: `username`, `full_name`, `company_id`, `c.name`;
`ORDER BY username, id` уехал внутрь нативного запроса. Скоупы не менялись; тонкость:
`COMPANY_HEAD` без компании теперь получает пустую страницу **явно** (раньше её давал
`company_id = NULL`, который не матчит ничего; в нативном запросе `null`-скоуп значил бы
«все» — комментарий на месте).

**Части B–C, directory.** `CompanyRepository.search` (JPQL: `name`, `id`) и
`TerminalRepository.search` (JPQL: `name`, `login`, `cast(id as string)`, `companyId` и
название компании через `left join Company c on c.id = t.companyId` — внутри одного модуля).
Скоуп компании — условие запроса рядом с поиском; отказы в аудит не менялись.
`/api/v1/terminals/options` не тронут.

**Часть D, журнал.** D.1: `entityType` и `entityId` независимы (баг «фильтр только парой»
закрыт). D.2: `search` (performed_by/action/entity_id/details), `outcome`
(SUCCESS/DENIED/UNRESOLVED, неизвестное → нет фильтра), `from`/`to` (ISO-instant или дата —
целый день по UTC; мусор → нет фильтра — та же коэрция, что у `page`/`size`). D.3: сортировка
`createdAt DESC, id DESC`. D.4: `idx_audit_logs_created` оставлен по одному `created_at`,
решение зафиксировано комментарием у сортировки в `AuditLogQueryService`. Реализация — 
`Specification` (репозиторий расширяет `JpaSpecificationExecutor` — методы только читающие,
Р-42 не задет; derived-методы удалены): отсутствующий фильтр не попадает в SQL вовсе, что
обходит хрупкую типизацию `:param IS NULL` на PostgreSQL.

**Часть E, фронт.** Новый `frontend/src/app/hooks/useDebounced.ts` (300 мс). На всех четырёх
страницах: параметры запроса вместо клиентского фильтра, `setPage(0)` при любой смене
поиска/фильтра, `AbortController` в эффекте загрузки + `axios.isCancel` (гонка «ив»/«ива»),
кнопка Refresh переведена на `() => fetch()` (иначе event попадал бы в параметр `signal`).
`filteredUsers`/`filteredCompanies`/`filteredTerminals`/`filteredLogs` удалены целиком.
`AuditLogsPage`: `TablePagination` (10/20/50/100, по умолчанию 20) вместо `size: 200`;
`entityTypeFilter` серверный, в списке добавлены `AUTH` и `AUDIT_LOG`; новые колонки
«Результат» (DENIED — `error`, UNRESOLVED — `warning`, как требовал P3-2 «не выглядеть
успехом») и «IP»; фильтры по результату и датам (границы локального дня → instant).
i18n: `common.searchOnPage` удалён из интерфейса и трёх языков; девять новых ключей
`auditLogs.*` добавлены на en/az/ru. `tsc -b` и `npm run build` — чисто.

**Тесты** — `./gradlew test`: 524 метода / 620 запусков, зелёные
(`:common` 80, `:auth` 95 (+9), `:directory` 97 (+14), `:pbl` 348). По списку ТЗ на каждый
список: находит за пределами первой страницы (25 записей, size=10), регистр, пустая строка =
нет фильтра, `%`/`_` буквально (с ловушками «Parts»/«haus» на неэкранированные `r_s`/`h_u`),
скоуп не обходится, `totalElements` считает отфильтрованное, users по названию компании,
журнал: регресс D.1 и регресс D.3 (пять записей с одним `created_at` сеются через SQL —
`@CreationTimestamp` перетирает время из билдера — и при `size=2` не дублируются и не
теряются), `outcome`/`from`/`to`/мусор в датах. Фронтовых тестов нет: тестовой инфраструктуры
во `frontend/` нет, ради debounce-теста не заводилась (по ТЗ).

**Приёмка.** `grep -rn "searchOnPage" frontend/src` — пусто;
`grep -rn "filteredUsers\|filteredCompanies\|filteredTerminals\|filteredLogs" frontend/src` —
пусто; счётчик тестов 620 > 593. Ручная проверка «в журнале больше 200 записей, последняя
достижима» — на стенде при приёмке; машинно то же самое покрыто тестами пагинации и D.3.

**Документы.** `problems.md`: §14 закрыт (остался хвост — врезки `SettingsPage` без пагинации,
отмечен в закрывающей плашке), новый §18 про кросс-модульный join; `auth/auth.md` §users-list,
`directory/directory.md` (companies, terminals, §3.3 журнала — параметры, независимость
фильтров, довесок сортировки), `application_description.md` §6.4 (параметры журнала + снят
устаревший абзац «пользователи и ссылки не журналируются»), `AGENTS.md` §10 (закрытый блокер +
тонкость про `SearchTerms`), §11 (счётчики трёх тестовых классов, итоги), сноска ревизии.
В `code_review.md` пункта нет — задача из ТЗ. Postman-коллекции: `List Users` в `Auth`
получил выключенные параметры `search`/`role`, `List Companies`/`List Terminals`/`List Audit
Logs` в `Directory` — `search` (журнал — ещё `outcome`/`from`/`to`, а у `entityType`/`entityId`
в описании отмечена независимость); параметры добавлены `disabled`, чтобы запросы по умолчанию
вели себя как раньше; сериализация — табами, как в экспорте.


### 24.08.2026 — P3-1 (проверено, Р-52)

Тесты 620, все зелёные (auth 95, common 80, directory 97, pbl 348; было 597, +23).
Ни один java-исходник не новее своего прогона тестов. Фронт: `dist` от 11:54:41 новее
последнего файла в `src` (11:54:10), а `build` = `tsc -b && vite build` — значит типы
сошлись, а не только собрался бандл.

Критерии приёмки выполнены: `searchOnPage` нет ни в коде, ни в трёх языках интерфейса;
`filteredUsers`/`filteredCompanies`/`filteredTerminals`/`filteredLogs` — ноль вхождений;
`search` есть на всех четырёх эндпоинтах; у журнала появились `TablePagination`, `AUTH`
и `AUDIT_LOG` в выпадающем списке, колонки «результат» и IP, фильтры по исходу и датам
(и `colSpan` поднят до 8 — мелочь, которую при добавлении колонок забывают чаще всего).
`problems.md` §14 закрыт, §18 заведён под новый долг. Ключей i18n добавлено 10 × 3 языка.

**Экранирование.** Вынесено в `common/search/SearchTerms` — одно место на все четыре списка.
Символ экранирования `!`, а не `\`, и причина в javadoc верная: один и тот же шаблон уходит
в JPQL, в Criteria и в нативный запрос, на H2 и на PostgreSQL, а обратный слэш на части этих
слоёв требует собственного экранирования. Порядок замен правильный — сначала сам `!`,
потом `%` и `_`; обратный порядок дал бы `!!%` вместо `!%`. `ESCAPE '!'` объявлен у каждого
LIKE, проверил все восемь в JPQL/нативных запросах и четыре вызова Criteria.

**Нативный запрос в `auth`.** `countQuery` повторяет условие `where` дословно — то самое
место, где обычно и расходится. `PageRequest.of(page, size)` без `Sort`: у нативного запроса
динамическая сортировка невозможна, `ORDER BY` внутри запроса. Сделано осознанно и написано
в javadoc.

**Главная находка исполнителя — предотвращённое расширение прав.** Раньше
`findAllByCompanyIdAndStatusNot(null, …)` для `COMPANY_HEAD` без компании давал пустой список:
в SQL `company_id = NULL` не истинно ни для одной строки. В новом запросе `:companyId IS NULL`
означает «без фильтра по компании», то есть **всех**. Смысл `null` перевернулся с «не найти
ничего» на «найти всё», и такой руководитель начал бы видеть всех пользователей системы.
Исполнитель это заметил и возвращает пустую страницу явно, с комментарием. У терминалов
та же ловушка закрыта структурно — `requireOwnCompany` бросает исключение, а не отдаёт `null`.

**Что стоит дописать (одна строка кода, один тест):** у этой защиты нет теста.
`search_doesNotBypassTheCompanyScope` и `companyHead_seesOnlyOwnCompany_acrossPages` есть,
а «руководитель без компании не видит никого» — нет. Это ровно та ветка, которая молча
отвалится при следующей правке запроса.

**Мелочь про тест экранирования в `auth`.** В `percentAndUnderscore_areSearchedLiterally`
половина про `%` доказательная: без экранирования вернулись бы все три строки вместо одной.
Половина про `_` — нет: ни одна из посеянных строк не совпала бы с `r_s` и как с шаблоном,
так что этот assert проходит при любом поведении. В directory-версии сделано правильно —
там посеяна `Parts LLC`, которую незаэкранированный `r_s` находит («Pa**rts**»), а
заэкранированный нет. Поведение доказано общим `SearchTerms`, слабее читается только
auth-тест.

**Не относится к задаче, но пусть будет записано:** `UsersPage` и `TerminalsPage`
по-прежнему тянут 200 компаний ради подстановки названия в колонке. Больше 200 компаний —
и в части строк вместо названия будет id. На поиск это больше не влияет (он серверный),
только на подпись.

### 24.08.2026 — P3-1a (сделано)

Один сторожевой тест: `UserListPaginationTest.companyHeadWithoutCompany_seesNobody_notEveryone`
(`auth`). Ветка в `UserService.listUsers`, закрывающая перевёрнутый смысл `null` после P3-1
(`:companyId IS NULL` в нативном запросе — «без фильтра», то есть все; до P3-1 `company_id =
NULL` не матчил никого), не была покрыта: существующие тесты гоняют руководителя с компанией
и прошли бы и без неё.

Сценарий по ТЗ: токен `COMPANY_HEAD` с `companyId = null` строится локально в тесте (не в
`setup()` — он нужен одному тесту); сеются пользователи `comp-01`, `comp-02` и один **без
компании вовсе** (третий доказывает, что пустота — «запрос не выполнялся», а не «NULL = NULL
совпал»); проверяются 200 + пустой `content` + `totalElements = 0` без параметров, с `search`
по заведомо существующему логину и с `role` — оба новых пути P3-1 не становятся обходом.
Javadoc теста объясняет, почему это не дубль `search_doesNotBypassTheCompanyScope`; у самой
ветки в `UserService` появилась ссылка на сторожащий тест.

**Доказательный критерий проверен руками**: ветка временно убрана → тест красный
(`FAILED`, руководитель без компании увидел всех), ветка возвращена → зелёный.

**Тесты**: `./gradlew test` — 525 методов / 621 запуск, зелёные
(`:common` 80, `:auth` 96 (+1), `:directory` 97, `:pbl` 348). Счётчик вырос с 620.

**Документы**: `AGENTS.md` §10 (закрытая запись) и §11 (строка `UserListPaginationTest`
15 → 16, итоги, сноска ревизии). `problems.md` и модульные API-доки не тронуты — поведение
эндпоинта не менялось, отложенного нет; в `code_review.md` пункта нет — задача из ТЗ.

### 24.08.2026 — P3-3 (сделано)

Гигиена репозитория — по ТЗ, шаги 1–5 в заданном порядке. Ничего не закоммичено — порядок
коммитов за заказчиком; история не переписывалась (оба 63-МБ jar'а остаются в `.git`, то же
решение, что по ключу JWT); секреты этой задачей не чистились — проверено, что в
раскоммиченных `.idea/` и `build/resources/` реальных значений и не было.

**Шаг 1.** `.gitattributes` — первым: `* text=auto eol=lf`, `*.bat`/`*.cmd` — `eol=crlf`
(`.bat` с LF на части Windows выполняется неверно), `*.jar`/`*.png`/`*.jpg`/`*.woff2` —
`binary`. `git add --renormalize .` упал на отслеживаемых, но удалённых с диска файлах
прошлых задач (`auth-…-plain.jar`, переехавший в `common` `AuditLog.java`) — удаления
зафиксированы `git add -A`, после чего renormalize прошёл. Побочный эффект `git add -A`:
в индекс попали и 59 файлов прошлых спринтов, прежде не добавлявшихся (все проверены —
мусора среди них нет, это исходники/доки, которым и так предстоял коммит).

**Шаг 2.** `.gitignore` переписан. Обе тонкости из ТЗ соблюдены и записаны комментариями
прямо в файле: `build/`/`.gradle/` **без** ведущего слэша (шаблон действует на любой
глубине — ведущий слэш и был причиной 166 отслеживаемых файлов сборки), `.idea/*` вместо
`.idea/` (иначе `!.idea/checkstyle-idea.xml` молча не работает — исключение внутри
исключённого каталога не действует). Плюс `node_modules/`, `frontend/dist/`, `*.iml` и
прежний блок `.env`/`!.env.example`.

**Шаг 3.** Раскоммит: `build` (корень, common, auth, directory), `.gradle` (корень, auth,
directory), `.idea` — файлы на диске остались; `checkstyle-idea.xml` возвращён `git add -f`
(без `-f` git молчит — путь уже под `.gitignore`).

**Шаг 4.** Дублирующие обёртки `auth|directory|pbl / gradlew, gradlew.bat, gradle/`
(12 файлов) удалены с диска и из git. Перед удалением проверено grep'ом по скриптам,
`.agents/` и всем `*.md`: модульные обёртки не звал никто — везде корневой
`./gradlew :модуль:задача` (единственное упоминание — сама находка в `code_review.md` §5).

**Шаг 5.** README написан: два предложения о системе, таблица модулей с портами
(common/—, auth/8081, directory/8082, pbl/8080, frontend/3000), запуск через переменные
окружения без значений со ссылкой на новый `.env.example` (создан: DB_*, JWT_SECRET,
bootstrap-админ, PBL_PROVIDER_* — все пустые) и `deployment_guide.md`, команды тестов,
карта шести документов по строке на каждый. Секретов и внутренних адресов нет.

**Приёмка** (всё сошлось):
`git ls-files | grep -E "(^|/)(build|\.gradle)/" | wc -l` → 0;
`git ls-files ".idea/*"` → только `checkstyle-idea.xml`;
`git ls-files | grep -cE "^(auth|directory|pbl)/gradlew"` → 0;
`git ls-files | wc -l` → **309** — это оценочные 248 + 2 новых файла задачи
(`.gitattributes`, `.env.example`) + 59 файлов спринтов, впервые попавших в индекс (см. шаг 1);
`git check-ignore -v` подтверждает `build/` для jar'а и `.gradle/` для кэша, `.idea/misc.xml`
игнорируется, `checkstyle-idea.xml` — нет (код 1); `git diff --stat gradlew.bat` — пусто.
Главное — прогоном, не глазами: `./gradlew cleanTest test` (форсированный перезапуск —
обычный `test` был up-to-date и ничего бы не доказал) — **525 методов / 621 запуск, зелёные**;
`npm run build` во `frontend/` — `✓ built`. Попутная мелочь: снесён пустой `.git/index.lock`
от 14.08 (стейл упавшей операции, живых git-процессов не было).

**Документы.** `code_review.md`: ✅ у трёх пунктов §5 (build/.gradle, `.idea`, обёртки),
«частично ✅» у README (имя `@figma/my-make-file` не менялось — не входило в ТЗ), ✅ у п. 16
плана §8 (вторая половина — мёртвый фронт — была закрыта P1-14). `AGENTS.md` §10 + сноска
ревизии. `problems.md` не тронут — отложенного нет. §11 не менялся — тестов задача не
добавляла (это единственная P3-задача без роста счётчика: её приёмка — сборка и grep'ы).


### 24.08.2026 — P3-1a и P3-3 (проверено, Р-53)

Тесты 621, все зелёные (auth 96, common 80, directory 97, pbl 348; было 620 — ровно +1,
новый тест). Ни один исходник не новее своего прогона. Фронт пересобран в 12:50:51,
`build` = `tsc -b && vite build` — типы сошлись.

**P3-1a.** `companyHeadWithoutCompany_seesNobody_notEveryone` — все три пути закрыты
(без параметров, с `search`, с `role`), посеян и пользователь с `companyId = null`,
чтобы пустой ответ нельзя было объяснить совпадением `NULL = NULL`. Тест кусается:
без ветки-защиты `companyScope` остаётся `null`, `:companyId IS NULL` снимает фильтр,
и все три посеянные записи вернулись бы — `totalElements` стал бы 3 вместо 0.
В javadoc написано, почему это не дубликат `search_doesNotBypassTheCompanyScope`,
и что 200 с пустой страницей — сохранённое поведение до P3-1, а не новое.

**P3-3.** Критерии выполнены: `build/` и `.gradle/` в отслеживаемом — ноль;
в `.idea/` остался ровно `checkstyle-idea.xml`; модульных обёрток нет, корневая цела.
`git check-ignore` проверен на шести путях, включая два, которые обязаны **не**
игнорироваться: `gradle/wrapper/gradle-wrapper.jar` (отрицание работает) и
`.idea/checkstyle-idea.xml` (исключение внутри `.idea/*` работает — то самое место,
где ошибка была бы тихой). Обе тонкости не просто реализованы, а объяснены
комментариями прямо в `.gitignore`, где их прочтёт следующий.

`.gitattributes` создан, и `git diff gradlew.bat` пуст и в рабочей копии, и в индексе —
пляска CRLF/LF прекратилась. README — 59 строк по делу, все ссылки резолвятся.
`.env.example` заведён в корне и во фронтенде, оба — пустые шаблоны без значений.
Прогнал поиск секретов по всем файлам, впервые входящим в индекс: чисто.

Сборка после удаления модульных обёрток жива — тесты и сборка фронта отработали
в 12:49–12:50, то есть уже без них.

**Ошибка в моём критерии приёмки.** Я написал «отслеживаемых файлов станет примерно 248
вместо 438». Стало 309, и это правильно: я считал только вычитание мусора и не учёл, что
одновременно в индекс попадут 122 новых файла спринта (438 − 252 + 122 + 2 ≈ 309). Критерий
был привязан к состоянию рабочей копии, которое я не мог предсказать, — та же ошибка,
что с `git status --porcelain frontend/` в P1-16. Работа тут ни при чём.

**Что осталось на заказчике (коммиты вне ТЗ, по решению):** индекс сейчас смешанный —
262 файла `src/`, 167 удалений мусора, 12 удалений обёрток, 12 файлов `.idea/`, 18
документов. Отдельного «гигиенического» коммита из такого индекса не собрать без
предварительного `git restore --staged` по путям `src/`.

**Мелочи, не влияющие на приёмку:** в индекс впервые входят `.claude/launch.json` и
`.postman/resources.yaml` — оба проверены, ничего чувствительного, но в ТЗ их не было,
это решение заказчика. В `code_review.md` §515 дублирующие обёртки всё ещё описаны как
открытая проблема — стоит пометить закрытыми. `.git` по-прежнему 122 МБ: оба
63-мегабайтных jar остаются в истории, как и предупреждалось.

### 24.08.2026 — P3-4 (сделано)

Сжатие комментариев в java. Правились только комментарии; фронтенд (там 5%), доки,
Liquibase-changelog'и, `.gitignore` и `.gitattributes` не тронуты — проверено `git diff`.

**Итог.** `main` 2723 → **1082** строки комментариев на неизменных 6433 строках кода
(30% → 14,4%, потолок ТЗ 15%); тесты 1932 → **1085** на 9655 (17% → 10,1%, потолок 12%).
По модулям (main): `common` 639 → 248, `auth` 454 → 202, `directory` 306 → 123,
`pbl` 1324 → 509. Форма — только `//`, по-русски, максимум 4 строки на блок; правило
записано в `AGENTS.md` §8, чтобы следующая правка не начала откатывать его обратно.

**Как это доказано, а не заявлено.** Из каждого файла вырезаются комментарии — с учётом
строковых литералов, чтобы `"http://…"` осталось кодом, — и результат сличается построчно
с эталоном. Эталоном служит **индекс git**: после P3-3 весь код был застейджен `git add -A`,
поэтому индекс и есть состояние до P3-4. `HEAD` для этого не годится, и об этом независимо
предупредили три исполнителя: в рабочем дереве лежит несколько спринтов незакоммиченного,
часть файлов в `HEAD` вообще отсутствует. Результат: 177 файлов, расхождений нет; строк
с `@Test` — 502, как было; `./gradlew cleanTest test` — 621 тест, ноль падений, счётчик
не сдвинулся (что и требовалось: правка комментариев не меняет поведение).

**Тринадцать пунктов таблицы «нельзя потерять» проверены поимённо** — 17 grep-проверок,
все ✓: `PAID_STATUSES` не сводить к `SUCCESS` и почему `AUTHORIZED` вне набора;
`SLOT_OCCUPYING_STATUSES` выведен из него, а не переписан; в `SearchTerms` — почему `!`,
а не бэкслеш, почему такой порядок замен, почему индекса нет намеренно; запрет `ACCESS`;
«не enum»; «не `JpaRepository`»; warn вместо исключения; синхронная запись при откате;
ветка руководителя без компании; `revoked > 0` в logout; `looksLikeLiteral` против
DNS-запроса по подделанному заголовку; непустой `ridByPmo`; «деньги могли уйти» в обоих
денежных путях `pbl`.

**Расхождение с буквой критерия приёмки.** `grep -rc "/\*\*" */src/main/java` даёт **один**
файл, а не ноль: в `PublicEndpoints` совпадают Ant-пути в **коде** (`"/api/v1/auth/**"`,
`"/actuator/**"` и ещё три), трогать которые запрещено железным правилом. Настоящих блочных
комментариев не осталось нигде: строгий grep по началу строки (`^\s*/\*`, `^\s*\*/`,
`^\s*\* `) даёт 0 и в `main`, и в тестах. Побочно всплыло, что комментарий с текстом
`/api/v1/auth/**` ложно краснил тот же критерий — переписан как «весь `/api/v1/auth`».

**Как делалось.** 16 файлов, включая все критичные и самый крупный `PaymentLinkService`
(393 строки комментариев, 90 блоков заменены скриптом, каждая замена проверялась на
единственность), сделаны мной вручную. Остальные 161 файл — девятью параллельными
исполнителями по непересекающимся наборам, с явным списком фактов, которые обязаны выжить
в их файлах. Каждый отчитался цифрами, обоими grep'ами и собственной проверкой кода;
централизованно всё пересчитано заново, на слово не принималось ничего.

**Дважды по дороге исполнители перезаписали мои скрипты проверки в общем скрадпаде** (файлы
с общими именами `codehash.py`, `count.py`), вместе с эталонным снимком. Проверки
восстановлены под уникальными именами и переведены на индекс git, который затереть нельзя.
Вывод на будущее: инструменты контроля не класть в каталог, куда пишут исполнители.

**Документы.** `AGENTS.md` §8 (конвенция комментариев — форма, четыре категории, что режется,
правило «не влезает в 4 строки — факт в §10, в коде строка со ссылкой») и §10 (закрытая
запись). `problems.md` не тронут — отложенного нет; в `code_review.md` пункта у задачи нет.
§11 не менялся: тестов задача не добавляла.


### 24.08.2026 — P3-4 (проверено, Р-54)

Комментарии в java сжаты. Основной код: 2723 строки комментариев → **1082**, доля 30% → **14%**
(потолок 15%). Тесты: 1932 → **1085**, 17% → **10%** (потолок 12%). Разметки (`<p>`, `{@code}`,
`{@link}`, `@param`) — ноль. Блоков `/** */` — ноль. Фронтенд не тронут: 601/11232, как было.

**Строк кода ровно столько же — 6433 в main и 9655 в тестах.** Совпадение до единицы —
самый убедительный признак, что уехали только комментарии. Тесты 621, все зелёные, счётчик
не изменился; прогон в 14:35–14:36, ни один исходник не новее своего прогона.

**Все тринадцать пунктов списка «нельзя потерять» на месте**, и почти каждый стал точнее
прежнего. Выборочно:

- `PAID_STATUSES`: вместо абзаца прозы — «сузишь набор до SUCCESS: возврат освободит слот,
  и ссылка с лимитом 3 соберёт 4». Раньше следствие приходилось выводить самому.
- `AuditLogRepository`: «журнал, который правит аудируемое им приложение, ничего не доказывает».
- `AuditEntity`: «журнал, который не может показать собственную историю, хуже строки».
- `logout`: «204 на что угодно — маскировка, а маскировка не имеет права оставлять следы».
- `ClientIp.looksLikeLiteral`: «пропустишь что-то ещё, хоть "a.b", — подделанный заголовок
  превратит каждую попытку входа в DNS-запрос».
- `MoneyOperationResult`: 26 строк javadoc → 4 строки; факт «пустым ridByPmo не бывает,
  клиент не соберёт объект без него» сохранён.

Сверх ТЗ исполнитель сделал две вещи, обе к лучшему:

1. **Комментарии переведены на русский.** В ТЗ этого не было. Читать их заказчику, и
   формулировки на русском вышли жёстче английских — сравнение выше это показывает.
2. **Ссылки на сторожевые тесты.** В `UserService.listUsers` к объяснению добавлено
   «Сторож — тест `companyHeadWithoutCompany_seesNobody_notEveryone` (P3-1a)». Комментарий
   теперь не только предупреждает, но и говорит, что именно поймает нарушителя. Этого
   я не просил, и это лучше, чем то, что я просил.

**Третья ошибка в моём критерии приёмки за сессию.** Я написал проверку
`grep -rl "/\*\*"` — она «нашла» оставшийся javadoc в `PublicEndpoints.java`, а там
ant-паттерны в строковых литералах: `"/api/v1/auth/**"`, `"/actuator/**"`. Мой grep не
отличает открывающий javadoc от звёздочки в пути; с якорем `^\s*/\*\*` — ноль.
Закономерность за сессию: критерии, привязанные к тексту и к состоянию рабочей копии,
у меня ошибаются, а привязанные к числам (счётчик тестов, число строк кода, `check-ignore`) —
нет. На будущее выбирать вторые.

**Мелочь, решение за заказчиком:** `.idea/checkstyle-idea.xml` мы в P3-3 оставили в git ради
«общей настройки команды», а внутри `activeLocationIds` пуст — активной конфигурации нет,
только два дефолтных конфига плагина. Checkstyle к тому же не подключён ни к одному
`build.gradle`. Если пустой файл не нужен, строку-исключение в `.gitignore` можно убрать,
и `.idea/` уйдёт целиком.


### 24.08.2026 — P3-6 (сделано)

Удаление нерабочих настроек — по ТЗ. Бэкенд не тронут: `git status --porcelain` по `auth`,
`common`, `directory`, `pbl` — пусто, тесты не запускались намеренно (нечего проверять).

**Что удалено.** Вкладки `Security` (2FA, «пароль изменён 15.03.2026», тайм-аут сессии, список
из трёх выдуманных «активных сессий»), `Payment` (девять полей: суммы, SMS/DMS/MIT/CIT,
3-D Secure, авто-капчуринг, задержка) и `API & Webhooks` (ключ, секрет, три события и зашитый
в код `https://api.acmecorp.com/webhooks/payments` — домен из шаблона генератора). Из `Account`
ушли телефон, адрес, ИНН и «контактное лицо»: в `CompanyResponse` только `id`, `name`, `status`
и четыре поля аудита, поэтому строки `businessPhone: comp.phone || …` и `taxId: comp.taxId || …`
читали то, чего сервер не отдаёт, и всегда побеждала константа. Из `Display` ушло всё, кроме
языка. Мёртвый код: панели `terminals` и `companies` (вкладок к ним не было — `setCurrentTab`
звался только из списка пяти вкладок; внутри жила кнопка удаления терминала, а `DELETE
/api/v1/terminals/{id}` отвечает 405 с P2-8), диалоги терминала, компании и пользователя,
113 строк закомментированного `notifications`, `handleSave`, `activeSessions`,
`handleGenerateNewApiKey` и три запроса, чей результат никуда не выводился (`/terminals`,
`/users`, `/audit-logs`). **1357 → 185 строк.**

**Вкладок не осталось.** Живых контрола два, поэтому язык переехал в одну плоскую страницу
вместе с названием компании и email — это был вариант, оставленный ТЗ на усмотрение исполнителя.

**Р-55: «Сохранить» не должно врать — три правки сверх ТЗ.** ТЗ исходит из того, что название
компании «грузится и сохраняется по-настоящему». По коду это было верно ровно для одной роли и
с побочным эффектом:

1. Страница читала **список** `GET /api/v1/companies?size=200`. Список разрешён только
   `SYSTEM_ADMIN` и `AUDITOR` (`CompanyService.listCompanies`), остальным он отвечает 403
   **и пишет `COMPANY/LIST` с `outcome = DENIED` в журнал аудита** — то есть каждое открытие
   настроек руководителем компании оставляло в журнале отказ, а в поле оставалась константа
   `'MilliKart Merchant'`. Теперь читается одна компания: `GET /api/v1/companies/{id}` по claim'у
   `companyId` из токена — он разрешён владельцу компании. Заодно исчез последний `size: 200`
   на этой странице (хвост `problems.md` §14).
2. У `SYSTEM_ADMIN` claim'а `companyId` нет (`AdminBootstrapRunner` заводит его с `companyId(null)`),
   и прежний код в этом случае брал `list[0]` — первую компанию по алфавиту, — а «Сохранить»
   переименовывал её. Переименование чужой компании из чужого экрана. Теперь: нет `companyId` —
   нет карточки, вместо неё строка «учётная запись не привязана к компании, компаниями управляют
   на странице «Компании»».
3. `updateCompany` пускает только `SYSTEM_ADMIN`, а `catch {}` глотал 403 и всё равно показывал
   зелёное «Settings saved successfully!». Теперь поле редактируемо только у `SYSTEM_ADMIN`,
   зелёная полоса — только на 2xx, отказ — текстом бэкенда. Пустое имя не отправляется:
   `UpdateCompanyRequest` без валидации, `updateCompany` пропускает `isBlank` и отвечает 200 —
   «сохранено» было бы неправдой и здесь.

**i18n.** Блок `settings` в `translations.ts` переписан во всех трёх языках: удалены
`settings.tabs.*`, `settings.security.*`, `settings.notifications.*`, `settings.payment.*`,
`settings.api.*` и ключи удалённых полей `account`/`display`. Остались девять ключей, и все девять
страница читает — до задачи из всего блока читался **один** (`settings.display.language`),
остальное было английскими литералами прямо в разметке; теперь литералов на странице нет.
`subtitle` переписан: он обещал «безопасность, уведомления и интерфейс». Интерфейс `Translations`
приведён к тому же составу — `tsc -b` не даст сослаться на удалённый ключ.

**Приёмка.** Четыре grep'а ТЗ — пусто (`acmecorp|webhook|twoFactor|require3DSecure|autoCapture|captureDelay`
по `frontend/src`; `handleSave\b`, `currentTab === 'terminals'|'companies'|notifications` по странице;
`settings.security|settings.payment|settings.api` по `frontend/src`). Первый и второй потребовали
правки **своего** текста, а не чужого: в комментарии к файлу я сначала перечислил удалённое как
«API & Webhooks», а обработчик назвал `handleSave` — grep'ы приёмки поймали обе, названия
изменены на «API» и `handleSaveCompany`. `npm run build` — `✓ built`, `oxlint` — 0 ошибок,
0 предупреждений.

**Документы.** `AGENTS.md` §9 (раздел ровно про это: «что на моках» переписан, добавлен абзац
про плоскую страницу и список «не возвращать»; поправлены три места, где `SettingsPage` числился
владельцем врезок и select'ов ролей), §10 — запись P3-6, сноска ревизии. `problems.md` §14 —
хвост закрыт вторым способом из плана («убрать»), открытых пунктов в §14 не осталось.
`technical_handover.md` §4.2 — строка «Управление лимитами и статусной моделью (Active / Blocked /
Pending)» была ложной дважды: лимитов нет ни в API, ни в базе, статуса `Pending` нет ни у
компании, ни у терминала; заменена на честную статусную модель и явное «настраиваемых лимитов,
правил обработки платежей, 2FA, тайм-аута сессии и вебхуков в системе нет». `code_review.md` не
тронут — пункта у задачи там нет, она из ТЗ. §11 не менялся: тестов задача не добавляла,
фронтенд их не имеет.


### 24.08.2026 — P3-7 (сделано)

Настоящий дашборд — по ТЗ `P3-7.md`, все четыре решения из раздела «Решения» заказчик подтвердил
до начала работы.

**Бэкенд.** `GET /api/v1/dashboard/summary` в `pbl`: `DashboardController`, `DashboardService`,
`DashboardRepository` (только чтение, без `JpaRepository` — дашборд ничего не пишет).
**Три** группировки на всю страницу: часовая `(сутки, час, валюта, статус)`, по терминалам
`(terminalId, валюта, статус)` и по ссылкам `(статус, тип платежа, тип использования)`. Итоги,
разбивка по статусам, посуточная выручка и распределение по часам **не запрашиваются отдельно**,
а сворачиваются из первой: независимые запросы могли бы разойтись между собой, и человек увидел
бы, что сумма по дням не сходится с итогом.

**Денежных `CASE` в запросах нет намеренно.** Первая версия считала `sum(case when status in
:paid then coalesce(captured, amount) else 0 end)` — смешение `BigDecimal` с целым литералом
в ветках `CASE` молча роняет копейки. Вместо этого база складывает суммы по всем статусам,
а по `PAID_STATUSES` они сворачиваются уже в сервисе: лишние суммы отброшены, ни одна не округлена.

**Три вещи всплыли по ходу и вошли в задачу:**

1. **`GET /api/v1/transactions/{id}` не существовал.** У `TransactionController` были список,
   `/{identifier}/status`, `/complete` и `/refund` — чтения по id не было, при том что
   `TransactionDetailPage.tsx:81` его уже звал и молча получал отказ. Карточка открывалась только
   тем, что список успел положить в состояние роутера. Без этого эндпоинта нельзя убрать
   сочинённый `statusHistory` — карточке нечем открыться.
2. **У списка транзакций не было порядка.** `PageRequest.of(page, size)` шёл без `Sort`, база
   отдавала строки как ей удобно. «Последние операции» на главной были просто какими-то
   операциями. Теперь `createdAt DESC, id DESC`; довесок по id — по той же причине, что в P2-1.
3. **Время в SQL пришлось убрать совсем — и это стоило отдельного круга.** Первая версия резала
   сутки в запросе: `CAST(TIMESTAMPADD(SECOND, :offsetSeconds, t.createdAt) AS LocalDate)`
   в списке выборки и то же выражение в `GROUP BY`. Все тесты зелёные — и падение на первом же
   открытии главной: PostgreSQL связывает один и тот же именованный параметр в двух местах
   **разными** placeholder'ами, не признаёт выражения одинаковыми и отвечает
   `column "created_at" must appear in the GROUP BY clause`. H2 такой запрос выполняет.

   Дальше выяснилось, что дело глубже параметра. Liquibase `type="timestamp"` разворачивается
   на PostgreSQL в `timestamp **with** time zone`, а на H2 — без пояса, и Hibernate кладёт туда
   стенные часы JVM (проверено пробой: `18:57Z` лежит как `22:57`). То есть **сдвиг, верный
   в тестах, неверен в проде, и наоборот**: моя первая формула (разница смещений пояса отчёта
   и пояса JVM) была верна ровно для H2.

   Итог: база не трогает время вовсе. Группировка идёт по `CAST(created_at AS date)` и
   `EXTRACT(HOUR ...)` — без единого параметра, — а сутки и час в поясе отчёта сервис вычисляет
   из `MIN(created_at)`: момент читается одинаково на обоих движках. Условие корректности одно —
   смещение пояса кратно часу, записано комментарием у `foldBuckets`. Расхождение H2 и
   PostgreSQL записано в `problems.md` §19 вместе с планом (Testcontainers).

   **Урок процедурный.** Зелёные тесты на H2 не означают работающий SQL. Исправленные запросы
   я прогнал в `psql` на рабочей базе через сгенерированный Hibernate текст (`show-sql`), а не
   на глаз — все три отработали на настоящих данных. Так надо было с самого начала.

**Р-56: пояс отчёта — настройка `pbl.dashboard.zone` со значением `Asia/Baku`.** Не UTC: портал
азербайджанский, и в UTC каждый платёж после 20:00 по местному уезжает в следующие сутки —
«сегодня» мерчанта перестаёт быть его днём. Пояс возвращается в ответе, чтобы подпись на экране
бралась оттуда, а не выдумывалась фронтендом. У пояса с переходом на летнее время окно,
перешагивающее перевод часов, съедет на час — цена одного смещения на всё окно, записана
комментарием.

**Индекс.** Changeset `008-dashboard-indexes.xml`, `idx_transactions_created` на `(created_at)`.
Ни один из трёх индексов P2-3 не обслуживает отбор по диапазону `created_at` без фильтра по
статусу: `idx_transactions_status_created` ведёт со `status`, которого в предикате сводки нет.
Решено рассуждением, а не `EXPLAIN`: установки с боевыми объёмами нет, а на девелоперской базе
любой план — seq scan и любой план быстрый. Это записано в самом changeset'е, чтобы **наличие**
индекса читалось как решение, — так же, как `SearchTerms` записал отсутствие `pg_trgm`.

**Фронтенд.** `HomePage` 818 → **435** строк, `useMemo` с аналитикой (~120 строк) удалён целиком.
Ушли: «Avg Processing Speed 1.2s», «Total Links Active» (дубль карточки выше под иконкой
«отменено»), бейджи `+100% Live`/`Active`/`Live Avg`, подстановки адреса и браузера вместо
незаписанных `clientIp`/`userAgent`, сочинение `statusHistory`/комиссии/описания при переходе
на карточку (теперь переход **без** router state), блок «Key Performance Indicators» целиком —
после чистки в нём оставались два дубля. График типов платежей разведён на два независимых
разбиения: в общих осях тип платежа и тип использования складывались в удвоенное число ссылок.
Каждая валюта — свой блок карточек и свой график: общая ось Y для манатов и евро это то же
сложение разных денег, только нарисованное. Последние операции берутся с **явным** `size: 10`.
Префикс `/api/v1/dashboard` заведён в `vite.config.ts` — без этого дев-сборка получила бы 404
от Vite, и это выглядело бы как ошибка бэкенда.

**i18n.** Блок `home` переписан во всех трёх языках. До задачи из четырнадцати ключей страница
читала **два** (`title`, `subtitle`); осиротели `home.metrics.*` (включая `vsLastMonth` —
сравнения «с прошлым месяцем» нет нигде), `home.charts.*`, `home.recentTransactions` и весь
`home.quickActions.*` — блока «Quick Actions» на странице не было вовсе. Теперь ключей 25 и все
читаются; английских литералов в разметке не осталось. Заодно удалён
`payByLink.noTerminalsWarning` — ключ ни на что не ссылался и указывал за терминалами
в «Настройки», где их с P3-6 нет.

**Приёмка.** Семь grep'ов ТЗ — пусто. Два из них снова поймали **мой** текст, а не чужой:
в комментариях я назвал удалённое его прежними словами. Это второй раз за два дня, и вывод
тот же: критерий, привязанный к тексту, ловит автора критерия. `./gradlew cleanTest test` —
**542 метода / 638 запусков**, зелёные (было 525/621). `npm run build` — `✓ built`, `oxlint` —
0 ошибок, 0 предупреждений.

**Документы.** `AGENTS.md` §9 (абзац про главную и список «не возвращать»), §10 (запись P3-7),
§11 (строка `DashboardSummaryTest` и счётчик), сноска ревизии. `application_description.md` §7.5,
§8.5, §9.3. `pbl/pay-by-link.md` — контракт сводки, чтение по id и оба новых 400.
`technical_handover.md` §4. `problems.md` §19 (пояс хранения) и §20 (комиссия и история статусов
на карточке операции — отложено сознательно, в ТЗ так и записано). `code_review.md` не тронут —
пункта у задачи там нет. Коллекции Postman у `pbl` нет, обновлять нечего.

### 24.08.2026 — подтверждение опасных действий на экране компаний (по просьбе заказчика)

Не нумерованная задача — правка из разговора, поэтому строки в таблице спринта нет.

Было: на `CompaniesPage` корзина вызывала `DELETE /api/v1/companies/{id}` прямо из `onClick`,
а переключатель статуса — `PATCH` из `onChange`. Ни одного вопроса, ни одного шанса передумать.
Подтверждений на фронтенде не было нигде: ни здесь, ни у пользователей, ни у терминалов.

Сделано: одно окно на оба действия, состояние `PendingAction` — пока оно не подтверждено, на
сервер не уходит ничего. В окне названы имя и id **этой** компании: подтверждать «компанию»
вслепую значит подтверждать не глядя. Кнопка подтверждения заблокирована на время запроса.

**Три текста, а не один общий** — и это главное в правке. Последствия действий разные настолько,
что общий текст был бы вреднее отсутствия окна:

- **удаление** — мягкое, строка остаётся со статусом `DELETED`, но становится невидимой на всех
  путях чтения, и правка такой компании отвечает «Company not found». То есть **из портала это
  необратимо**; в окне стоит отдельное предупреждение;
- **снятие пометки «активна»** — по коду **не останавливает ничего**. Проверено grep'ом по трём
  модулям: единственное чтение статуса компании — фильтр `DELETED` в `CompanyService`; `auth`
  читает `companies` только ради поиска по названию, `pbl` не читает вовсе. Вход, создание ссылок
  и приём платежей у «неактивной» компании работают как у активной. В окне так и написано,
  и сказано, что для остановки платежей блокируют терминалы (там блокировка настоящая, P2-8);
- **возврат пометки «активна»** — ничего, кроме самой пометки.

**Найдено попутно и записано:** событие аудита `COMPANY/BLOCK` (P3-2) читается ревизором как
«компания была заблокирована», а блокировки нет — `problems.md` §21 с двумя вариантами решения
(сделать проверку настоящей или убрать `BLOCK`/`UNBLOCK` из словаря; промежуточного нет).
`technical_handover.md` §4.2 — две строки: статус компании ничего не останавливает, удаление
необратимо из портала.

**i18n.** В блоке `companies` было 13 ключей, читались 8. Осиротевшие удалены: `editCompany`
и `editDialogTitle` (диалога правки компании на экране нет), `email`, `phone`, `taxId` — полей,
которых нет в `CompanyResponse`, тот же мусор генератора, что вычищен из настроек в P3-6.
Добавлены семь ключей под три текста подтверждения, во всех трёх языках; читаются все 15.

Приёмка: `npm run build` — `✓ built`, `oxlint` — 0/0, осиротевших ключей `companies.*` не
осталось (проверено перебором ключей блока против исходника страницы). Бэкенд не тронут.

### 25.08.2026 — подтверждения на терминалах и пользователях (по просьбе заказчика)

Продолжение записи от 24.08.2026: тот же приём распространён на два оставшихся справочных экрана.
Не нумерованная задача, строки в таблице спринта нет.

**Поправка к вчерашней записи.** Я написал, что у `TerminalsPage` блокировка уходит по клику.
Это неверно: диалог подтверждения там был с P2-8, и хороший — он ещё и считает, сколько активных
ссылок будет приостановлено. Ошибся, потому что искал подтверждения grep'ом по именам
`window.confirm|confirmOpen|ConfirmDialog`, а состояние там называется `blocking`. Вывод тот же,
что уже был про grep'ы по тексту: искать надо по смыслу (вызовы `apiClient.*` прямо из `onClick`),
а не по угаданным именам.

**Терминалы.** Состояние `blocking` обобщено до `statusChange` с полем `nextStatus`:
- **разблокировка** теперь спрашивает наравне с блокировкой. Она тоже трогает чужие ссылки,
  просто в другую сторону, и «случайно нажал» стоит столько же. Счёт затронутого симметричен:
  перед блокировкой считаются `ACTIVE`-ссылки терминала (их приостановят), перед разблокировкой —
  `SUSPENDED` (их вернут в работу). Оба — тем же `GET /payment-links?terminal=&status=&size=1`
  по `totalElements`, без новой ручки в бэкенде; `SUSPENDED` — валидное значение
  `PaymentLinkStatus`, проверено по контроллеру;
- **сохранение правки** уходит на сервер только после отдельного окна со **списком изменений**:
  в одной форме лежат название, логин, пароль и компания-владелец, и цена промаха у них разная.
  Список строится сличением формы с исходной строкой; если изменений нет, запрос не отправляется
  вовсе — PATCH, который ничего не меняет, всё равно оставил бы запись в журнале аудита.

**Пользователи.** Удаление — окно с логином, именем и ролью удаляемого плюс предупреждение:
учётная запись перестаёт работать сразу (`deleteUser` гасит все refresh-токены), запись остаётся
в базе скрытой, а восстановить или отредактировать её из портала нельзя (`updateUser` на
удалённом отвечает «User not found»). Заодно ошибка удаления перестала показываться системным
`alert()` — теперь полосой на странице, как на остальных экранах.

**Изменения пользователя на экране нет вовсе** — и это, возможно, важнее самой правки.
`PATCH /api/v1/users/{id}` в API есть: смена роли, компании, блокировка, пароль, всё
журналируется поимённо (P2-14). На `UsersPage` из мутаций только создание и удаление.
Подтверждать нечего, потому что менять нечем. Записано в `AGENTS.md` §9 — если экран правки
появится, окно нужно и ему.

**i18n.** Осиротевшие ключи вычищены в обоих блоках, как накануне в `companies`: удалены
`terminals.location` (такого поля нет), `users.editUser` (экрана правки нет), `users.email`
(страница показывает `username`) и `users.filterRole` (фильтр подписан `users.role`). Вместо
хардкода по-английски заведены в дело уже существовавшие `terminals.createDialogTitle`,
`editDialogTitle`, `editTerminal` и `password`. Добавлены шесть ключей у терминалов и три
у пользователей, во всех трёх языках. Осиротевших не осталось ни в одном из трёх блоков
(проверено перебором ключей блока против исходника страницы): companies 15, terminals 25,
users 15 — все читаются.

Приёмка: `tsc -b` (он и поймал все девять недостающих ключей до сборки), `npm run build` —
`✓ built`, `oxlint` — тех же 38 предупреждений, что и до правки, ноль новых. Бэкенд не тронут.


### 24.08.2026 — P3-5 и P3-6 (проверено, Р-55)

Фронт собран: `dist` 20:12:32 новее последнего файла в `src` (20:09:46), сборка идёт через
`tsc -b && vite build` — типы сошлись. Тестов 638, все зелёные.

**P3-6 — выполнена полностью.** `SettingsPage.tsx` 1357 → **185 строк**. Все девять
контрольных маркеров дают ноль: `acmecorp`, `webhook`, `twoFactor`, `require3DSecure`,
`autoCapture`, `captureDelay`, `settings.security|payment|api`. Мёртвые панели `terminals`
и `companies`, закомментированный `notifications` и сам `handleSave` удалены. Осиротевших
ключей i18n не осталось.

**P3-5 — шесть действий из семи.** Закрыты: удаление пользователя (`pendingDelete`),
удаление компании и блокировка/разблокировка компании (`pending: PendingAction`),
блокировка **и разблокировка** терминала (`statusChange` вместо прежнего `blocking` —
асимметрия, о которой я спрашивал в ТЗ, устранена). Диалог удаления пользователя называет
объект по правилу №1: имя, логин и роль в рамке внутри окна. Жёстко зашитых строк в новых
диалогах нет, ключи заведены на все три языка.

**Пробел: `PayByLinkPage.tsx:645`.** Иконка «Cancel link» в строке таблицы вызывает
`handleCancel(link.id)` напрямую, без диалога. На карточке ссылки
(`PayByLinkDetailPage`) подтверждение есть и было раньше — а в списке нет, хотя
промахнуться строкой в таблице из двадцати ссылок куда проще, чем на карточке.

**Отклонения от ТЗ, оба обсуждаемые:**

1. Общего компонента `ConfirmDialog` нет — у каждой страницы свой диалог. В ТЗ просил один
   на всех. Сейчас реализаций пять: три новых (Users, Companies, Terminals) и две прежних
   (PayByLinkDetail, TransactionDetail). Не ошибка, но правка формулировки или поведения
   теперь стоит пяти заходов вместо одного.
2. `editConfirm` в `TerminalsPage` — подтверждение на сохранение правки терминала со списком
   меняемых полей. Это вне согласованного объёма («только опасные действия»). Но правка
   терминала меняет логин и пароль эквайринга, так что защита осмысленная. Решение за
   заказчиком.

**Две собственные ошибки при этой проверке, обе поймал до отчёта:**

- Сначала решил, что отмена ссылки и списание холда вообще без подтверждений — файлы
  `PayByLinkDetailPage` и `TransactionDetailPage` сегодня не менялись, а кнопки вызывают
  обработчики напрямую. Проверил контекст: обе кнопки находятся **внутри** диалогов,
  которые там были и до P3-5, и оба диалога показывают сумму. Файлы не тронули правильно.
- В одной из проверок подписал вывод «пусто = ни один диалог не открывается», хотя в той же
  выдаче строками ниже было видно `setFinalizeDialogOpen(true)` и `setCancelDialogOpen(true)`
  на строках 520 и 530. Подпись врала, данные — нет.

**Вне обеих задач:** в дереве появился Dashboard — `DashboardController`, `DashboardService`,
`DashboardRepository`, `DashboardSummaryResponse` и тест `DashboardSummaryTest`; менялись
`TransactionController` и `PaymentLinkService`, на фронте — `routes.tsx` и `HomePage.tsx`.
Тестов стало 638 против 621 (+17), все зелёные. Это работа из другого окна, в объём P3-5/P3-6
не входит и мной не проверялась.

---

### 25.08.2026 — P3-5a (сделано)

Хвост P3-5, найденный при её же проверке (Р-55): подтверждение отмены ссылки в списке плюс
переводы денежных диалогов. Бэкенд не тронут ни строкой.

**1. Отмена ссылки из списка.** `PayByLinkPage.tsx:645` звал `handleCancel(link.id)` прямо из
`onClick` иконки в строке таблицы. Теперь клик ставит `cancelTarget: PaymentLink | null`, а
`handleCancel` читает цель оттуда. Держим **ссылку, а не id** намеренно: окну нужны `shortCode`
и сумма, а по id их пришлось бы искать в `links` заново. `shortCode` в списке и на карточке
считается одинаково (`l.id.slice(0, 8).toUpperCase()`), и это ровно то значение, что стоит
в колонке «Link ID / Ref» — окно называет ту строку, по которой кликнули, её же подписью.

**2. Список и карточка — одно окно.** Заголовок `payByLink.cancelConfirmTitle`, вопрос
`cancelConfirmText`, рамка с коротким кодом и суммой, кнопки `payByLink.keepLink` /
`payByLinkDetail.cancelLink`. Разметка рамки взята у `CompaniesPage` (`action.hover`, рамка
`divider`) — идиома P3-5, а не новая.

**3. Пять денежных диалогов переведены.** Пропущенное правило «ни одной строки в коде» до
`PayByLinkDetailPage` и `TransactionDetailPage` не дошло, потому что P3-5 их справедливо не
трогала — подтверждения там уже были. Английский текст в них дублировал ключи, **которые уже
лежали в словаре на трёх языках и не использовались нигде**: `payByLink.cancelLinkAction`,
`cancelConfirmTitle`, `cancelConfirmText`, `payByLinkDetail.cancelLink`, `finalizeDMS`,
`transactions.detail.refundAction`, `refundTitle`, `confirmRefund`. Все семь задействованы.
Заведены восемь новых на en/az/ru: `payByLink.keepLink`, `transactions.detail.completeAction`,
`completeTitle`, `captureExplains`, `captureAmount`, `confirmCapture`, `refundQuestion`,
`keepTransaction`.

**4. Окно возврата говорит о возврате.** Кнопка звалась `Cancel & Refund`, окно —
`Cancel Transaction` с текстом «cancel this transaction», где возврат не упоминался ни разу,
а бэкенд зовёт `POST /transactions/{id}/refund` и пишет в журнал `REFUND`. Теперь заголовок —
`refundTitle` («Возврат средств по транзакции»), текст — новый `refundQuestion`, кнопка
подтверждения — `confirmRefund`, а кнопка на самой странице — `refundAction` («Оформить
возврат (Refund)»). Экран, кнопка и журнал аудита говорят одно слово.

**5. Сумма ушла из фразы в рамку.** Окно списания холда на карточке ссылки гласило
«This will capture **₼X** that is currently authorized…» — подстановка внутрь предложения,
которую словарь не умеет и под которую не хочется заводить механику. Сумма и короткий код
вынесены в рамку под текстом; ровно так уже было устроено окно списания на карточке
транзакции, так что два окна одного действия сошлись и внешне.

**6. Двойное подтверждение закрыто.** У всех четырёх окон появился `*Busy`: обе кнопки гаснут
на время запроса, `onClose` игнорируется, пока запрос идёт. Фокус при открытии — на безопасной
кнопке (`autoFocus`): Enter по инерции ничего не списывает и не гасит. В трёх окнах P3-5
(Users, Companies, Terminals) `autoFocus` не ставили — там на это работает умолчание MUI
(фокус получает контейнер диалога, не кнопка); здесь он проставлен явно, потому что требование
сформулировано в ТЗ и его хочется читать в коде, а не выводить из поведения библиотеки.

**Решения, принятые сверх буквы ТЗ (все мелкие, все называю):**

- **`refundAmount` оставлен простаивать.** Ключ переведён на три языка, но это подпись поля
  формы — в него зашито «(AZN)». Рядом с `formatCurrency`, которая сама подставляет символ
  валюты транзакции, получалось «Сумма возврата (AZN): ₼100.00» — валюта дважды и, при
  не-AZN транзакции, ещё и противоречиво. В рамке стоит `transactions.columns.amount`
  («Сумма»), а `refundAmount` дождётся формы частичного возврата, для которой и заведён.
- **Переведены и две кнопки, открывающие денежные окна** — `Finalize Payment` и `Complete`
  (в таблице ТЗ была только `Cancel Link` на строке 532). Кнопка-инициатор — часть того же
  диалога, и оставлять её английской, переведя окно, значило бы получить экран на двух языках.
- **Подсказка в жёлтой врезке `PayByLinkDetailPage:968` теперь называет кнопку её настоящей
  подписью** (`{tObj.payByLinkDetail.finalizeDMS}` вместо зашитого «Finalize Payment»). Это
  не вычистка врезки — фраза вокруг осталась английской, как и её соседи («Payment received
  on…», «Expired without payment on…»). Но без этой правки я бы сам сломал подсказку: она
  посылала бы к кнопке, которой на азербайджанском и русском экране больше нет.

**Не сделано намеренно:** общего `ConfirmDialog` по-прежнему нет — это отклонение №1 из Р-55,
и ТЗ прямо просило его здесь не заводить. Реализаций пять, сведены тексты и поведение, не код.
Алерты `PayByLinkDetailPage` («Payment received on…», «Funds Authorized», «Payment Finalized»,
«Expired without payment on…») и `TransactionDetailPage` («Transaction refund initiated
successfully…», «Completed — funds captured») остались английскими: это копия страницы, а не
диалоги, и вычищать её надо всю разом, отдельной задачей. Записано в `problems.md` §22.

**Приёмка.**

```
grep -rn "status: 'CANCELED'\|/refund\|/complete" app/pages/*.tsx   → 5 строк,
    у каждой кнопка-инициатор внутри <Dialog> (проверено разбором вложенности тегов,
    не глазами: <Dialog\b против </Dialog>, <DialogTitle/Content/Actions не считаются)
grep -rnE "Cancel Payment Link|Keep Link|Keep Transaction|Finalize DMS|This will capture|
    Amount to capture|Confirm & Capture|Are you sure" app/pages/                    → 0
grep -rc "tObj.payByLink.cancelLinkAction" app/pages/   → PayByLinkPage.tsx:1  (≥ 1)
```

`npm run build` (`tsc -b && vite build`) — чисто; пропуск ключа в любом из трёх языков
`tsc` поймал бы, интерфейс `TranslationDictionary` расширен всеми восемью. `oxlint` — 38
предупреждений, в тронутых файлах только прежние неиспользуемые импорты (`Card`,
`CardContent`, `Timeline*`), ни одного нового. `./gradlew test` — `UP-TO-DATE`, 638 методов,
`failures="0" errors="0"`: бэкенд не менялся.

**Ошибка при проверке, поймана до отчёта.** Первый скрипт, проверявший «каждая кнопка внутри
`<Dialog>`», напечатал `*** OUTSIDE ***` по всем пяти вызовам. Дефект был в скрипте:
`<Dialog[\s>]` не совпадает с `<Dialog`, стоящим последним на строке (у всех окон открывающий
тег многострочный). С `<Dialog\b` все пять — внутри. Вывод врал, код был прав.

**Документы.** `AGENTS.md` §8 (правило про отсутствие подстановок в словаре и про проверку
готовых ключей), §9 (врезка «Деньги — то же правило, четырьмя окнами дальше» с grep'ом
приёмки; отдельно повторено, что общего `ConfirmDialog` нет), §10 — запись P3-5a, сноска
ревизии и дата сверки. §11 не трогали: тестов по-прежнему 542 метода / 638 запусков.
`fix_plan.md` — строка в таблице и эта запись. `problems.md` — §22 про английские алерты.
`code_review.md` не трогали: пункта у задачи там нет, она выросла из проверки Р-55.


### 24.08.2026 — P3-5a (проверено, Р-56)

Пробел закрыт. Иконка отмены в списке ссылок (`PayByLinkPage`, прежняя строка 645) больше не
зовёт `handleCancel(link.id)` — она открывает диалог через `setCancelTarget(link)`. В окне
`shortCode` и сумма, обе кнопки `disabled` на время запроса, `onClose` заблокирован, пока идёт
запрос.

Сверх ТЗ: `autoFocus` поставлен на **безопасную** кнопку с комментарием «Enter по инерции
оставляет ссылку живой». Я это правило писал в P3-5 («опасная кнопка не в фокусе»), но
здесь оно не только выполнено, а объяснено там, где его иначе снимут при следующей правке.

Английский из диалогов убран: в `app/pages/` ноль совпадений по восьми контрольным строкам,
все они теперь только в `translations.ts`, где им и место. Простаивавшие ключи
`cancelLinkAction` и `cancelLink` (переведены на три языка, не использовались нигде) введены
в дело. Новые ключи `cancelConfirmTitle`, `cancelConfirmText`, `keepLink`, `keepTransaction`
заведены во **всех трёх** языковых секциях — проверял разбором файла по секциям `en/az/ru`,
а не подсчётом совпадений.

Формулировка возврата приведена в порядок: диалог теперь `refundTitle` = «Issue Transaction
Refund» и `refundAction` = «Process Refund». Экран, кнопка и запись в журнале аудита
(`REFUND`) говорят одно слово. Раньше заголовок был «Cancel Transaction», а бэкенд звал
`/refund`.

Финальная проверка по всем страницам: единственные оставшиеся `onClick={handleCancel|
handleDelete|handleRefund|handleComplete|handleFinalize}` — это кнопки подтверждения
**внутри** диалогов (шесть штук). Ни одна строка таблицы и ни одна панель инструментов
больше не зовёт разрушающий обработчик напрямую.

Сборка прошла (`dist` 22:13:01 новее `src` 22:09:04, сборка через `tsc -b && vite build`).
Тестов 638, все зелёные — бэкенд не трогали.

**Ещё одна моя оплошность при проверке.** Первый прогон контрольного grep дал 5 совпадений
английского, и я чуть не записал это в недоделку. Совпадения были в `translations.ts` —
я ударил по `app/`, тогда как в самом ТЗ критерий написан по `app/pages/`. Ошибка в
исполнении проверки, не в критерии и не в работе.

**Не сделано намеренно:** общего `ConfirmDialog` по-прежнему нет, реализаций шесть.
Сведение их в одну — отдельная задача со своим риском задеть работающие диалоги;
в P3-5a она сознательно не входила.

---

### 25.08.2026 — P3-5b (сделано)

Хвост, отложенный в P3-5a: отклонение №1 из Р-55 («общего `ConfirmDialog` нет»). Девять окон
подтверждения в шести файлах сведены в один компонент. Бэкенд не тронут ни строкой.

**Зачем.** Все девять работали правильно — проблема была не в них. Три правила P3-5 (не закрывать
окно во время запроса, гасить обе кнопки, держать фокус на безопасной) были записаны в девяти
местах; десятый диалог написали бы, забыв одно из трёх, и никто бы не заметил — окно ведь
«работает». Это уже было видно в коде: `autoFocus` стоял в пяти окнах из девяти.

**Поправка к ТЗ.** ТЗ говорит, что `autoFocus` есть «только в `PayByLinkPage`, в остальных
восьми Enter подтверждает опасное действие». По коду он был в **пяти** из девяти — `PayByLinkPage`
(отмена), обоих окнах `PayByLinkDetailPage` и обоих окнах `TransactionDetailPage`; все пять
расставил P3-5a, и это записано в его же логе («Фокус при открытии — на безопасной кнопке»).
Без `autoFocus` были четыре: `UsersPage`, `CompaniesPage` и оба окна `TerminalsPage`. Итог тот
же, что просило ТЗ — фокус теперь во всех девяти, — но намеренное изменение поведения касается
четырёх экранов, а не восьми.

**1. Компонент.** `frontend/src/app/components/ConfirmDialog.tsx`, 96 строк. API — ровно тот,
что в ТЗ. Четыре правила записаны один раз:

- `onClose={() => { if (!busy) onCancel(); }}` — окно не закрыть кликом мимо во время запроса;
- кнопка отказа **первая** и с `autoFocus` — Enter не подтверждает опасное действие;
- обе кнопки `disabled={busy}` — двойной клик не даёт двух запросов;
- подтверждение `variant="contained"` и цветное, `DialogTitle` с `fontWeight: 700`.

Умолчания: `confirmColor='error'`, `cancelLabel` — `tObj.common.cancel`, `maxWidth='sm'`,
`busy=false`. Всё, чем окна различаются по содержимому, приходит в `children` — поэтому **ни
один из девяти не потребовал особого случая внутри компонента**, и он остался маленьким.

**2. Переезд — по одному, поведение сохранено.** Что пережило переезд поимённо:

- `TerminalsPage`, смена статуса — `Alert` с вычисляемым severity (`affectedLinks ? 'warning' : 'info'`),
  число затронутых ссылок и ветка «не посчиталось», заголовок, различающий блокировку
  и разблокировку, цвет кнопки `warning`/`success`, `maxWidth="xs"`.
- `TerminalsPage`, правка — построчный список меняемых полей; логин и пароль эквайринга
  показываются как «Login updated» / «Password updated», **без значений**.
- `CompaniesPage` — **один** вызов `ConfirmDialog` на два действия, как и просило ТЗ: заголовок,
  вопрос, цвет и надпись кнопки считаются из `pending.kind`, `Alert` про необратимость —
  только у удаления.
- `PayByLinkPage` / `PayByLinkDetailPage` — `shortCode` и сумма в рамке, `keepLink` на безопасной
  кнопке, `CancelIcon`/`FinalizeIcon` на опасной.
- `TransactionDetailPage` — суммы на `success.light` и `error.light`; заголовки, приведённые
  в P3-5a («возврат», а не «отмена»), не откатились.
- `UsersPage` — имя, логин и роль в рамке, `Alert` про необратимость.

У правки терминала `busy` как не было, так и нет: `handleUpdate` его не заводит, окно получает
`busy` пропсом, и без него `disabled={false}` — ровно прежнее поведение. Заводить там состояние
я не стал: это было бы изменение поведения сверх единственного разрешённого.

**3. Два английских ключа.** `payByLink.shareDialogTitle` («Share Payment Link» /
«Ödəniş Linkini Paylaş» / «Поделиться ссылкой») и `terminals.registerAction`
(«Register Terminal» / «Terminalı Qeydiyyatdan Keçir» / «Зарегистрировать терминал»).
Оба в формах, не в подтверждениях, как и сказано в ТЗ.

**Решения, принятые сверх буквы ТЗ (все мелкие, все называю):**

- **Соседний `Cancel` переведён тоже.** В `DialogActions` окна создания терминала рядом
  с `Register Terminal` стоял зашитый `Cancel`. Ключ `common.cancel` есть с самого начала;
  оставить одну кнопку английской, переведя вторую в той же строке, значило бы получить
  форму на двух языках. ТЗ его не называло — в его grep'ах он и не ищется.
- **Выровнены отступы и вид кнопки отказа.** У `DialogActions` было два варианта (`p: 2.5`
  у Users/Companies/Terminals, `px: 3, pb: 2.5, gap: 1` у денежных окон P3-5a), у кнопки отказа —
  два вида (текстовая и `outlined`). В одном компоненте двух вариантов быть не может; взят
  вариант P3-5a как более поздний. Это косметика, но она видна на трёх экранах, и я её называю,
  а не прячу под «ничего не поменялось».
- **Вопрос везде стал `DialogContentText`.** У окна смены статуса терминала пояснение было
  `Typography variant="body2"`, у остальных восьми — `DialogContentText` (body1, `text.secondary`).
  Компонент рендерит `question` одним способом; окно терминала подтянулось к остальным.
  Отступ до `Alert` сохранён — `sx={{ mt: 2 }}` на самом `Alert`, идиома Users/Companies.

**Приёмка** (`cd frontend/src`):

| Критерий | Ожидалось | Получено |
|:---|:---|:---|
| `grep -rc "<DialogActions" app/pages/*.tsx \| awk …` | 6 | 6 |
| `grep -rn "ConfirmDialog" app/components/ \| wc -l` | >0 | 2 |
| `grep -rln "ConfirmDialog" app/pages/*.tsx \| wc -l` | 6 | 6 |
| `grep -rnE "Share Payment Link\|Register Terminal" app/pages/` | 0 | 0 |
| `grep -rn "autoFocus" app/pages/*.tsx` | 0 | 0 |
| `disabled={*Busy}` в `pages` | «заметно упасть» | было 13 строк, стало 1 — и та не диалоговая (`IconButton` в строке таблицы, `TerminalsPage:386`). Сам grep занижает: его четыре имени не ловят `finalizeBusy` и `completeBusy`, так что кнопок с `disabled` уехало со страниц шестнадцать, а осталось две — обе внутри `ConfirmDialog` |

`npm run build` (`tsc -b && vite build`) — чисто. `npx oxlint` — 0 ошибок, 38 предупреждений,
все до единого пре-существующие: новых неиспользуемых импортов ни в шести страницах, ни
в компоненте нет (проверено пофайлово по json-выводу oxlint). Тестов 638, все зелёные
(`./gradlew test` — `UP-TO-DATE`, бэкенд не тронут: изменения только в `frontend/src`).

**Проверка руками — выполнена заказчиком 25.08.2026, диалоги в порядке.** Прогон делал не я:
приложение было поднято (`localhost:3000`, бэкенд на 8080), но экран входа требует пароля,
а вводить пароли мне нельзя. Заказчик прошёл окна сам и подтвердил, что они работают.

**Документы.** `AGENTS.md` §9 (абзац «Реализаций диалога по-прежнему пять» переписан на
«Окно подтверждения одно», абзац «Три текста, а не один общий» уточнён), §10 — запись P3-5b
и новый пункт в «Тонкости, на которых легко ошибиться» («новое окно подтверждения — только
`ConfirmDialog`»), сноска ревизии. §11 не тронут — тестов не прибавилось, бэкенд не менялся.
`fix_plan.md` — строка спринта и эта запись. `code_review.md` не тронут: пункта у задачи там
нет, она выросла из отклонения №1 Р-55. `problems.md` — §22 остаётся открытым, эта задача
английские врезки карточек не трогала.

**Номер задачи.** ТЗ пришло под номером P3-7, но он занят: `P3-7.md` — это дашборд, закрытый
24.08.2026, со строкой в `fix_plan.md` и записью в `AGENTS.md` §10/§11. По согласованию
с заказчиком задача проведена как **P3-5b** — она и по существу хвост P3-5/P3-5a, как P3-1a
у P3-1.


### 25.08.2026 — P3-7 / ConfirmDialog (проверено, Р-57)

В коде задача помечена `P3-5b` — расхождение только в имени, оставляю как есть, чтобы
не гонять правку ради ярлыка.

Числа сошлись ровно: `<DialogActions>` в страницах 15 → **6**, и по файлам ровно там, где
предсказывал (Companies 1, PayByLink 2, Terminals 2, Users 1, обе Detail-страницы по нулю).
Вызовов `<ConfirmDialog>` — **девять**, в шести файлах. `autoFocus` в страницах — ноль,
он теперь только внутри компонента. Английский `Share Payment Link` и `Register Terminal`
убран.

Компонент делает все четыре правила сам: `onClose` игнорируется, пока `busy`; кнопка отказа
первая и в фокусе; обе кнопки гаснут; подтверждение `contained` и цветное. Правило про фокус
до этого было выполнено в одном месте из девяти — теперь во всех, и это единственное
намеренное изменение поведения.

Пережило переезд всё, что должно: вычисляемый severity у `Alert` с числом затронутых ссылок
(`affectedLinks ? 'warning' : 'info'`), список меняемых полей терминала, один диалог на два
действия у компаний с вычисляемыми пропсами, `shortCode` и сумма у отмены ссылки, `error.light`
и заголовки про возврат у транзакции, карточка с логином и ролью плюс `Alert` про необратимость
у пользователя.

**Требование ТЗ, которое исполнитель правильно НЕ выполнил буквально.** Я написал, что логин
и пароль эквайринга должны показываться как «Login updated» / «Password updated», без значений.
Пароль так и сделан — `editPasswordReplaced`, без значения. Логин показывается целиком,
старое → новое. И это верно: правило «без значений» я списал из соглашения журнала аудита
(P3-2), а там оно потому, что журнал читают люди, которые на этот экран не смотрели. Диалог
же читает тот, кто минуту назад сам вписал логин в форму под этим окном. Разные читатели —
разный ответ. Ошибка в моей формулировке, не в реализации.

**Единственный незакрытый пункт: фронт не пересобран.** Хронология: шесть страниц
переведены в 09:06–09:08, сборка прошла в 09:08:31, после чего в 09:57:59 правились
`ConfirmDialog.tsx` и `translations.ts` — и сборки с тех пор не было. Проверка руками шла,
судя по всему, в dev-режиме, а он собирает esbuild'ом и типы не проверяет. То есть `tsc -b`
не прогонялся ровно по тем двум файлам, где живут типовые ошибки: сигнатура пропсов
компонента и полнота ключей перевода. Лечится одной командой `npm run build` во `frontend`.

Тестов 638, все зелёные — бэкенд не трогали, как и требовалось.

---

## Сентябрь 2026: встреча с МК, `ecom`, тесты на PostgreSQL, кнопка «Тест»

Задачи этого месяца шли без номеров P-x; решения — Р-58…Р-72 в [`decisions.md`](decisions.md).
Коммиты на ветке `feature/ecom-provider-integration`: `24e9fdd` (тесты на PostgreSQL), `0b4e786`
(логин и пароль терминала), `c133b77` (история операции), `2e2c122` (сервис `ecom`, синхронизация
терминалов), `91fc9f9` (фронтенд), `007675e` (документация), `62d8a3d` (`merchantRid` и
`ridByMerchant`). Кнопка «Тест» и ревизия документации на 13.09.2026 не закоммичены.
Тестов на 13.09.2026 — 691 запуск, все зелёные: auth 96, common 80, directory 109, pbl 379, ecom 27.

Описания ниже перенесены из «Тонкостей» и §11 `AGENTS.md` без правок текста; в `AGENTS.md` от них
остались короткие правила.

### 11.09.2026 — по встрече с МК: идентификаторы, терминал, деньги, история

- **Номер заказа провайдера и `ridByMerchant` — первыми везде** (Р-58). Мерчант узнаёт свой платёж
  по этим двум номерам, внутренний UUID операции ему не говорит ничего. Порядок колонок и подписей
  изменён в списке операций, в связанных операциях на карточке ссылки, в поиске, в карточке
  операции и на главной; UUID остался последней колонкой и отдельным техническим блоком карточки.
  Разбор ответа сведён в один `frontend/src/app/utils/mapTransaction.ts`, подпись терминала —
  в `utils/terminals.ts`.
- **Основной параметр терминала — его логин, и `options` его отдаёт** (11.09.2026, по встрече с
  МК). Имя терминала мерчант придумывает сам, числовой `id` внутренний, а узнаёт он терминал по
  логину эквайринга — поэтому логин подписывает терминал везде, где тот показан, а имя идёт
  пояснением (`frontend/src/app/utils/terminals.ts`, единственное место с этим порядком). Ворота
  у `options` ровно те же, что у постраничного `GET /api/v1/terminals`, который логин отдаёт и так
  (`isGlobalReader` + `requireOwnCompany` на обоих), — новой видимости это не даёт. **Пароль
  терминала в `TerminalOptionResponse` не входит вовсе** (не замаскирован — его там нет), сторож —
  `options_carryTheTerminalLoginButNeverItsPassword`.
- **Терминал ссылки есть в обоих ответах** (11.09.2026): `terminal` был только в
  `PaymentLinkResponse`, и карточка ссылки терминал не показывала вовсе, а маппинг из API поле
  не переносил. Теперь он есть и в `PaymentLinkSummaryResponse` — берётся из самой строки ссылки,
  никакого похода в справочник, — и карточка, открытая из списка, подписывает терминал сразу,
  а не после собственного запроса. Подпись — логин, разрешается лёгким фидом терминалов.
- **`merchantReference` с экранов убран, из API — нет** (11.09.2026). Это `merchantOrderId`:
  собственный номер заказа мерчанта, необязательный, эквайеру не уходит, служит только для сверки
  на его стороне. Форма создания ссылки в портале его не отправляет, поэтому у всех созданных
  через портал ссылок он пуст — строка на карточке не рисовалась никогда, в выгрузке стояло «N/A»,
  а ветка поиска не находила ничего. В `LinkedTransactions` было хуже: при пустом номере туда
  подставлялся сперва номер заказа провайдера, потом внутренний id операции, и мерчанту их
  показывали как его собственную ссылку. Вернётся на экран вместе с полем в форме создания.
- **Пароль терминала: один путь наружу, одна роль, след у каждого чтения** (11.09.2026).
  `TerminalResponse.password` как был `"********"`, так и остаётся — в списках, карточках и
  лёгком фиде ключа эквайринга нет, сколько бы экранов их ни читало. Настоящий пароль отдаёт
  только `GET /api/v1/terminals/{id}/password`, только `SYSTEM_ADMIN`, и каждое чтение ложится
  в журнал как `READ` по терминалу (сам ключ в `details` не пишется — журнал читают чужие).
  Смена пароля через `PATCH` сужена до той же роли: `COMPANY_HEAD` и `COMPANY_MANAGER` правят
  имя, логин, компанию и статус, а на поле `password` получают `403`. Отказ, а не тихое
  игнорирование: глава компании иначе решил бы, что ключ сменён, и остался бы со старым.
  Создание терминала не тронуто — пароль там задаёт тот, кто заводит терминал, и он его знает.
  Сторожа: `password_*`, `passwordChange_*`, `terminalResponse_keepsThePasswordMasked`
  и `nameChange_byACompanyHead_stillGoesThrough` в `TerminalBlockingIntegrationTest` (+5).
- **История операции собирается из записанного и ничего не дорисовывает** (11.09.2026).
  Блок «Transaction Status History» на карточке был пуст всегда: `statusHistory` во фронтовом типе
  заполнялся пустым массивом, а ответ по операции истории не нёс вовсе. Единственным местом, где
  она «была», оставалась таблица связанных операций на карточке ссылки — там router state
  собирал две записи с одним и тем же временем и подписями «Payment attempt initiated by
  customer» / «Payment successfully completed», которых никто не писал (ровно то, что запрещает
  Р-48). Теперь историю отдаёт `TransactionResponse.statusHistory`, а собирает
  `PaymentLinkService.statusHistoryOf` из трёх источников, у каждого из которых есть **своё**
  записанное время: `createdAt`, метка списания `mpCapture.at` и каждая запись `mpRefunds[i].at`.
  Статус после возврата считается нарастающим итогом от `refundableBase` — сравнялись `REFUNDED`,
  нет `PARTIALLY_REFUNDED`. Последним, и только если текущее состояние ничем выше не объяснено,
  идёт событие `STATUS` со временем `updatedAt`: так на экран попадает SMS-платёж, ставший
  `SUCCESS`, и холд, ставший `AUTHORIZED`, — отдельной записи об этих переходах не существует.
  Событие без времени отбрасывается на обеих сторонах. Переход из связанных операций больше
  не кладёт router state вовсе — карточка грузит себя сама, как давно делает главная.
  Сторожа: `statusHistory_*` в `MoneyOperationsIntegrationTest` (+3).
- **Кнопка действия видна ровно тогда, когда бэкенд его примет** (11.09.2026). Условием показа
  блока действий на карточке операции стояло «любой статус, кроме `FAILED`», а возврат уходил на
  полную сумму платежа. У возвращённой операции кнопка возврата предлагала то, на что
  `PaymentLinkService.refund` отвечает «вернуть можно только успешные»; у частично возвращённой —
  «сумма превышает склиренную». Правила бэкенда теперь повторены на экране (`isRefundable` и
  `refundableLeftOf` в `TransactionDetailPage.tsx`): возврат только у `SUCCESS` и
  `PARTIALLY_REFUNDED`, потолок — `capturedAmount ?? amount`, из него вычитается уже
  возвращённое, и уходит остаток, а не полная сумма. `capturedAmount` ради этого появился
  в `Transaction` и в общем разборе — без него потолок у частично склиренного DMS считался
  по `amount` и всегда превышал настоящий. Меняешь правило на бэкенде — меняй обе эти функции.
- **Отказ денежной операции показывается там, куда мерчант смотрит, и отличает «отказано» от
  «неизвестно»** (11.09.2026). Возврат и списание DMS-холда имеют два разных исхода, и бэкенд их
  уже разводит (Р-23): отказ эквайера — 400, неподтверждённый исход — 502 плюс незакрытая запись
  в журнале аудита. Фронтенд обе ветки валил в один красный алерт, и тот рисовался **первым
  элементом страницы**, тогда как кнопка возврата стоит под всей карточкой операции: окно
  подтверждения закрывалось, алерт оставался за пределами экрана, и отказ выглядел как «ничего
  не произошло». Теперь отказ остаётся в самом окне подтверждения, у неподтверждённого исхода
  свой тон и свой текст, а повтор до выяснения закрыт — повторить возврат поверх возможно уже
  ушедших денег хуже всего, что тут можно предложить. Разбор исхода — один на проект,
  `frontend/src/app/utils/moneyOperationError.ts`; всё, что 5xx или вовсе без ответа, считается
  неизвестностью намеренно.
- **Кнопка проверки статуса обязана показывать ответ.** Она звала `/transactions/{id}/status` и
  выбрасывала результат (`await` без присваивания), то есть единственный предписанный после
  неизвестного исхода шаг на экране ничего не менял. Ответ теперь разбирается тем же
  `utils/mapTransaction.ts`, что и список, и кладётся поверх всех прочих источников карточки.
  Этот разбор был двумя копиями — в `App.tsx` и на карточке, — и они успели разойтись: карточка
  знала о `merchantRid`, список нет. Копий больше нет, новую заводить не надо.

### 12.09.2026 — сервис `ecom` и синхронизация терминалов с провайдером

- **`ecom` читает чужую базу и только читает** (12.09.2026). Сервис заведён под платежи, которые
  мы не порождаем: у мерчанта свой онлайн-эквайринг, источник истины — операционная схема шлюза
  (TXPG), и ходим мы в неё синхронно на каждый запрос UI. Что здесь важно не сломать:
  - **Два источника данных в одном процессе.** Наша PostgreSQL помечена `@Primary` — ей достаются
    JPA, Liquibase и всё, что просит `DataSource` без уточнения. Снимешь `@Primary` — миграции
    однажды уедут в чужую базу. Пул к шлюзу маленький и `read-only`, транзакционного менеджера
    у него нет вовсе: писать туда нечем.
  - **Одна строка на заказ, а не на операцию.** `tran` даёт строку на операцию, у DMS-платежа их
    минимум две. Без группировки по `orderid` мерчант видит каждый платёж дважды, любая сумма
    задваивается, а пагинация едет. Токен свёрнут подзапросом по той же причине: он привязан
    к заказу, и две попытки оплаты размножили бы каждую операцию вдвое.
  - **Скоуп собирается в `EcomScopeService` и нигде больше.** У провайдера один терминал — это
    один мерчант, поэтому цепочка простая: компания → её терминалы → `terminals.provider_rid`
    у каждого. Отдельного справочника связей нет намеренно: он дублировал бы таблицу терминалов
    и однажды с ней разъехался (такая таблица была заведена 11.09.2026 и удалена на следующий
    день, когда выяснилась топология). Пустой список означает пустую выписку; ветки «терминалов
    нет, значит показать всё» не существует, и заводить её нельзя — так выглядит показ
    мерчанту А оборотов мерчанта Б.
  - **Статус выводится из денег, а не из кодов.** Полного словаря `order_.status`,
    `tran.trantype` и `pmoresultcode` провайдер не утверждал, поэтому `EcomStatusResolver`
    смотрит на суммы одобренных операций: они однозначны в любой версии их словаря. Сырые коды
    заказа уезжают на экран рядом, чтобы расхождение было видно. Придёт словарь — это первое
    место на уточнение; `void` против `refund` и chargeback здесь сейчас не различаются ничем.
  - **`o.password` не попадает никуда.** Пара «номер заказа плюс пароль» даёт доступ к операциям
    по чужому заказу. Ни в выписку, ни в экспорт, ни в логи.
  - **Период обязателен и ограничен, страница — курсорная.** Смещение по операционной базе
    дорожает с каждой страницей и пропускает строки между запросами. Курсор — пара «время
    последней операции, номер заказа».
  - **Чего пока нет:** комиссии, даты расчёта, имени терминала, approval code, 3DS — их в шлюзе
    нет, и подставлять вместо них своё нельзя. Заказ без операций в периоде в выписку не попадает
    (окно строится по `tr.id`) — это осознанно, см. `TxpgTransactionRepository`.
- **Статусы терминалов синхронизируются с провайдером, и у каждого правила есть цена**
  (12.09.2026). Слепок терминалов провайдера держит `ecom` в таблице `provider_terminals`;
  сверяет его с нашими `terminals` — `directory`, потому что смена статуса там уже умеет
  приостанавливать и восстанавливать платёжные ссылки и писать в журнал. Никакого HTTP между
  сервисами: база у всех одна, и `directory` читает слепок нативным запросом, как давно читает
  `payment_links`.
  - **Выключение терминала останавливает приём платежей**, поэтому чужой сбой не должен до него
    доходить. Неудачный опрос и пустой ответ не применяются вовсе: у работающего эквайринга
    не бывает нуля терминалов, это признак оборванной выборки. Терминал гасится не первым
    пропаданием, а после трёх подряд — сбой должен продержаться три четверти часа.
  - **`status_source` решает, кого можно трогать.** Выключенный человеком не включается никем
    и никогда: это решение клиента, и то, что у провайдера всё в порядке, его не отменяет.
    Выключенный синхронизацией включается ею же, когда провайдер вернёт терминал, — без этой
    ветки человек тоже не смог бы (ему запрещено), и терминал остался бы мёртвым навсегда,
    а его ссылки приостановленными.
  - **Отсутствие строки в слепке — это «не знаем», а не «выключен».** Выключение фиксируется
    явным флагом; терминал без `provider_rid` сверка не касается вовсе.
  - **Синхронизация идёт расписанием, а не входом администратора.** Вход — событие случайное,
    а платой за него была бы зависимость аутентификации от чужой базы: недоступный шлюз означал
    бы, что в портал никто не войдёт. Обновить слепок вручную можно кнопкой.
  - Название и логин терминала принадлежат провайдеру: при заведении админ выбирает строку
    слепка и вводит только пароль, а синхронизация обновляет оба поля, если провайдер их сменил.
    Один терминал провайдера — одна наша компания, это ограничение в базе.

### 12.09.2026 — тесты с поведением СУБД переехали на PostgreSQL (Testcontainers)

**Что вскрыл перевод тестов блокировки.** `TerminalBlockingIntegrationTest` ставил срок ссылки
через `java.sql.Timestamp.from(instant)`, а колонка `expires_at` объявлена как `timestamp` без
зоны, и Hibernate кладёт в неё `Instant` в UTC. Драйвер же переводит `Timestamp` в **локальную
зону JVM**: на машине в Баку срок «час назад» ложился в базу как «через три часа»,
разблокировка считала ссылку живой и возвращала её в `ACTIVE` вместо `EXPIRED`. Само приложение
согласовано само с собой, ошибка была в тесте — но увидеть её на H2 было нельзя. Пишешь время
в тестах напрямую, минуя Hibernate, — пиши его в UTC (`LocalDateTime.ofInstant(..., UTC)`).

**Вторая находка: `TIMESTAMPADD` в тестах.** Три теста состаривали строку запросом
`created_at = TIMESTAMPADD(SECOND, ?, created_at)`. Это функция H2, у PostgreSQL её нет вовсе, и
такой запрос там не разбирается — семнадцать тестов свёртки упали сразу. Переписано на
`created_at ± CAST(? AS double precision) * INTERVAL '1 second'`. Сам приём — сдвигать
сохранённое значение, а не писать абсолютную метку — остаётся правильным и по той же причине,
что в находке выше: так результат не зависит от зоны, в которой драйвер закодирует время.
**`PaymentLinkIntegrationTest` этот запрос ещё содержит** и потому остаётся на H2: переведёшь
его — начни с этой строки.

Остальные тесты с базой остаются на H2 намеренно: там база просто хранилище, и настоящая СУБД
добавила бы секунды, не добавив проверок.

### 12.09.2026 — `merchantRid` и `ridByMerchant`

- **`merchantRid` и `ridByMerchant` — разные идентификаторы, и путать их нельзя** (12.09.2026,
  словарь провайдера).

      merchantRid    — reference id **мерчанта**, задаёт **провайдер**;
      ridByMerchant  — reference id **платежа**, задаёт **мерчант**.

  У нас первое живёт в `terminals.merchant_rid` (у провайдера один терминал — это один мерчант),
  второе — в `transactions.rid_by_merchant`. Колонка транзакции называлась `merchant_rid` и
  обещала совсем не то, что в ней лежит: случайный UUID, который мы порождаем на попытку оплаты
  и отправляем провайдеру именно полем `ridByMerchant` (`EcomCreateOrderRequest.Order`).
  Переименовано в 009; поле терминала звалось `provider_rid` и переименовано туда же по словарю.
  **`ridByMerchant` бывает пустым.** По ссылкам, заведённым порталом, его генерирует
  `OpenLinkService`, поэтому в `pbl` колонка not null. Из базы провайдера, где заказ заводил сам
  мерчант, он может не прийти вовсе — в `EcomTransactionResponse` поле nullable, и подставлять
  вместо него номер заказа провайдера или внутренний id нельзя: такая подмена уже была на
  карточке операции и выдавала мерчанту чужие номера за его собственные.

### 13.09.2026 — кнопка «Тест» у терминала

- **Кнопка «Тест» у терминала заводит у провайдера настоящий заказ, и это согласовано**
  (13.09.2026). Другого запроса, который проверял бы разом и логин с паролем терминала, и то, что
  ему разрешены оплаты, у провайдера нет: запрос статуса проверяет только первое. Пробный заказ
  остаётся неоплаченным, через десять минут уходит в Expired, а выписка берёт только завершённые
  заказы — провайдер подтвердил, что на такую нагрузку согласен.
  - **Выписка обязана отсекать незавершённые заказы.** Это теперь не пожелание, а условие: иначе
    каждое нажатие «Тест» появится у мерчанта строкой в выписке. SQL выписки пока предварительный
    — замени его, не потеряв этого фильтра.
  - Проверка живёт в `pbl` (`TerminalCheckService`, `/api/v1/acquiring/terminal-checks`), а не
    рядом с остальными эндпоинтами терминалов: к провайдеру умеет ходить только `pbl`, а весь
    `/api/v1/terminals` маршрутизируется в `directory`.
  - **Без `@Retry` и без `@CircuitBreaker`**, в отличие от боевого заведения заказа. Повтор только
    множит пробные заказы, а общий с платёжным путём breaker означал бы, что админ, десять раз
    проверивший неверный пароль, закрывает приём платежей всем мерчантам.
  - Четыре исхода, и различать нужно все: `OK`, `INVALID_CREDENTIALS` (провайдер отвечает
    `InvalidLogin`), `REJECTED` (ключ подошёл, заказ не дали — его слова наружу), `UNREACHABLE`
    (5xx или нет ответа — о терминале это не говорит ничего). Классификация по коду в теле, а не
    по HTTP-статусу: тот же `InvalidLogin` может прийти и в 200, и в 4xx.
  - Только `SYSTEM_ADMIN`, каждая проверка в журнале аудита с исходом, пароль в запись не идёт.
    Для заведённого терминала ключ берётся из базы и наружу не уходит вовсе.

### 13.09.2026 — ревизия документации

- Документы собраны в `project_docs/`; в корне остались `README.md` и `AGENTS.md` (агенты ищут его
  именно там). Удалены `problems.md`, `implementation_plan.md` (план слияния веток другого проекта),
  `.agents/AGENTS.md`, `.agents/workflows/{mp,frontend}.md` и `pbl/.agents/workflows/pbl.md` — последний
  к тому же утверждал, что подпись JWT проверять не нужно.
- Решения вынесены в `decisions.md` (Р-72): Р-49 и Р-50 стояли в таблице дважды, Р-25 и Р-51…Р-57 в неё
  не попадали, сентябрь (Р-58…Р-71) не был записан вовсе.
- `AGENTS.md` сжат до правил: 2 887 → 826 строк. История закрытых задач (часть её лежала внутри
  «Тонкостей») и таблица тестов перенесены сюда, открытые пункты `problems.md` — одной строкой каждый
  в §10 «Известные ограничения». Номера разделов §1–§12 сохранены: на §6 и §10 ссылается код.
- `code_review.md` заморожен: отметки о закрытии у 14 пунктов P0/P1 и трёх P2, где их не было, статусы
  мелочей P3 (три пункта гигиены открыты), закрыты §6 и план §8.
- `technical_handover.md` переписан по коду: роли, стек, Void, `ecom`, синхронизация терминалов,
  кнопка «Тест», словарь аудита (показ пароля и проверка терминала пишутся и при успехе).
- `deployment_guide.md`: четвёртый сервис (`ecom`) во всех шагах — сборка, конфигурация, переменные,
  systemd, проверки, обновление, справочник. Найдены три пробела, из-за которых установка по
  руководству не поднялась бы: в конфигурации `auth` не было `auth.login.rate-limit.*`, в
  конфигурации `pbl` — `pbl.dashboard.zone` (обе без значений по умолчанию в коде, а
  `--spring.config.location` заменяет встроенный `application.yaml`), в nginx не было маршрута
  `/api/v1/dashboard`. Дублирующийся номер раздела 14.3 исправлен.
- `application_description.md` переписан обзором архитектуры: 2 574 → 583 строки. Контракты, матрица
  ролей, стек и карта API из него убраны — у них свои места; таблицы, рассыпанные по ячейке на строку,
  собраны заново.
- Модульные документы: `directory.md` — логин в `options`, пароль терминала, `merchantRid` при заведении,
  синхронизация статусов; `pay-by-link.md` — `rid_by_merchant`, полный `TransactionResponse` со
  `statusHistory`, `terminalLogin` в сводке, проверка терминала (§5.14); `auth.md` — шапка. Новый
  `ecom.md` — контракты выписки и справочника терминалов провайдера.
- В коде и конфигурации 40 ссылок на документы в 28 файлах переведены на новые места
  (`problems.md` → `AGENTS.md` §10, пути → `project_docs/`), в том числе в текстах логов и сообщений
  при старте и в XML-комментариях применённых changeset'ов — вне `<changeSet>`, в контрольную сумму
  Liquibase они не входят. После правок `./gradlew test` — 691 запуск, все зелёные.

### 13.09.2026 — версия PostgreSQL для прода (Р-73)

- Выбрана PostgreSQL 16 — та же версия, что у тестового контейнера `postgres:16-alpine`. Руководство
  ставило 15 (разделы 4.5 и 5.2) — переведено на 16; из известных ограничений `AGENTS.md` снят пункт
  о расхождении версий; версия указана в `AGENTS.md` §3 и §11, `technical_handover.md` §3 и `README.md`.

### 13.09.2026 — карточка записи журнала аудита

- Строки `AuditLogsPage` кликабельны (и открываются с клавиатуры — Enter или пробел): окно поверх
  списка показывает запись целиком — время локальное и в ISO, исполнителя, IP, компанию, ресурс,
  ID объекта, ID записи и полный текст деталей. Отдельного запроса нет: всё приходит строкой списка,
  фильтры и страница при закрытии не сбрасываются. Выбран вариант «окно», а не отдельная страница
  `/audit-logs/{id}` — последней понадобился бы новый эндпоинт в `directory`.
- Для `TRANSACTION` и `PAYMENT_LINK` (их `entityId` — UUID) в окне есть переход в карточку операции
  или ссылки; у терминалов, компаний и пользователей своих карточек нет.
- Переводы на трёх языках; заголовок колонки «Entity ID» перестал быть зашитым английским.
  Проверено: `npm run typecheck`, `npm run build`, `npm run lint` (0 ошибок); в браузере не
  проверялось — нужен вход.

### 14.09.2026 — выписка `ecom` по SQL провайдера (Р-74, Р-75)

- **Провайдер прислал SQL выписки и выгрузку со стенда** от 11.09.2026: 16 заказов, 25 операций.
  Предварительный запрос `TxpgTransactionRepository` разошёлся с данными в главном — деньги он
  считал суммой `tranamt` всех операций `Purchase`, а у провайдера `trantype` у всех покупок
  `Purchase`, и авторизацию от списания отличает только `phase`. На выгрузке это 770 AZN списанных
  вместо 357, DMS-заказ на 22 AZN — 44, четыре холда без списания (175604, 175605, 175670, 175675)
  — «успешно» на 61 AZN, а `AUTHORIZED` не выходил никогда. Ещё он читал колонки, которых в SQL
  провайдера нет (`tr.terminalid`, `tr.ridbypmo`, `o.srcemail`, `o.srcmobile`), и функцию
  `getHighIdForTime`: одной отсутствующей хватило бы, чтобы выписка падала целиком.
- **В SQL провайдера две ловушки размножения строк, в код они не перенесены.** `join login l on
  l.merchantid = o.merchantid` ничего не выбирает, но умножает каждую операцию на число логинов
  мерчанта (в выгрузке дублей нет только потому, что логин у мерчанта один); `join txpg.token`
  обычным join задваивает операции покупателя, пробовавшего две карты. Фильтра Р-71 в нём нет —
  заказы `Authorized` попадали в выборку. TRANID в выгрузке испорчен Excel: 18 цифр не помещаются
  в double.
- **Как устроено теперь** (Р-74). Страница номеров заказов — по окну `tran.id` (приём провайдера) и
  дате создания заказа, курсор — номер заказа; затем все операции этих заказов. Склейка и деньги —
  `EcomOrderAssembler`, итоги периода — `EcomStatsAccumulator` по потоку строк теми же правилами.
  Верхняя граница окна ставится только у периодов, закончившихся больше суток назад: поведение
  `getLowIdForTime` на будущем времени провайдер не подтвердил. Даты шлюза читаются местным
  временем новой настройки `ecom.txpg.zone` (`Asia/Baku`).
- **Деньги и статус** (Р-75): вид операции — по `phase`, `voidkind` и `trantype = Refund`
  (`EcomOperationKind`), возвраты — по модулю `clearamt`. `Closed`, по словам провайдера, ставит он
  сам: заказ дольше 10 минут в `Preparing` или автоотмена DMS-холда без списания через N дней. Такой
  холд — новый статус `CANCELED`; название предложено и ждёт подтверждения. Незавершённые для
  Р-71 — `Preparing`, `Authorized`, `Expired`.
- **Контракт** (`ecom.md` §2): операции вложены в строку заказа, `GET …/{orderId}/operations`
  заменён карточкой `GET …/{orderId}`; фильтр `terminalIds` стал `merchantRids` и только сужает
  скоуп; `/terminals` отдаёт терминалы скоупа из нашей базы, без запроса к шлюзу; `/stats` — счётчики
  всех статусов и суммы по валютам. Убраны `terminalId`, `customerEmail`, `customerPhone`,
  `firstOperationAt`, `operationCount` и поиск по почте — колонок для них в SQL провайдера нет.
- **Открыто у провайдера** (`AGENTS.md` §10): как в базе выглядят возвраты и реверсалы, второй
  клиринг заказа 175533, пояс дат, индекс `tran.orderid`. Справочник терминалов — следующей задачей,
  SQL по нему у пользователя есть.
- Тесты `ecom`: 27 → 64. Новые — `EcomOrderAssemblerTest` (на выгрузке стенда),
  `EcomStatsAccumulatorTest`, `TxpgTransactionRepositoryTest` (скоуп и Р-71 в каждом запросе, пояс
  дат, только колонки провайдера); переписаны `EcomTransactionScopeTest` и `EcomStatusResolverTest`.
  `./gradlew test` — 728 запусков, все зелёные.
- Документы: `ecom.md` §1–§2, `decisions.md` (Р-74, Р-75), `AGENTS.md` §10,
  `application_description.md` §3.5 и §8, `technical_handover.md` §4.6, `deployment_guide.md`
  (`ECOM_TXPG_ZONE`), `.env.example`.

### 14.09.2026 — ответы провайдера по выписке: возвраты, мультиклиринг (Р-76)

- **Возвраты подтверждены на данных стенда** (скриншот выборки по заказу 175195): `trantype = Refund`,
  `phase = Single`, `clearamt` с минусом (-5, -20, -5), `tranamt` — положительная сумма, у каждого
  возврата свой RRN. Разбор совпал с кодом: списано 30, возвращено 30 — `REFUNDED`, у провайдера
  `Refused` («полностью возвращён»). Реверсалов в данных по-прежнему нет.
- **Второй клиринг заказа 175533 — мультиклиринг**: по одной авторизации несколько списаний, они
  складываются. **Индекс на `tran.orderid` есть** — чтение операций по номерам заказов опирается на него
  законно. Из открытых вопросов `AGENTS.md` §10 оба пункта сняты.
- **Нашлась дыра в Р-71, закрыта Р-76.** У 175195 до финального `Refused` стоял `Authorized`, хотя 30 из
  50 AZN уже были списаны: при мультиклиринге заказ после списания остаётся `Authorized`. Фильтр
  прятал такие заказы вместе со взятыми деньгами до финального статуса — до N дней. Теперь `Authorized`
  с одобренным списанием (`Purchase` / `Clearing` без `voidkind`) в выписке виден — во всех трёх
  запросах, чтобы итоги не разошлись со страницей; авторизация без списания по-прежнему скрыта.
  Признак списания в SQL и в `EcomOperationKind.CAPTURE` один, это отмечено в обоих местах.
- Тесты `ecom`: 64 → 67 — возвраты 175195, частичное списание у `Authorized`, точный вид исключения в
  SQL. `./gradlew test` — 731 запуск, все зелёные.
- Документы: `decisions.md` (Р-76), `ecom.md` §1, §2.2, §2.3, `AGENTS.md` §10, `technical_handover.md` §4.6.

### 14.09.2026 — `getLowIdForTime` на будущем времени

- По словам провайдера, функция на будущем времени не работает. Верхнюю границу окна код и так ставил
  только у прошедших периодов, но по нашим часам и по неподтверждённому поясу, а нижняя граница
  получала `dateFrom` как есть — период в будущем или расхождение поясов отдавали функции будущее
  время. Теперь все четыре вызова идут через `lowIdForTime`: аргумент — `least(cast(:t as date),
  sysdate - interval '5' minute)`, то есть не позже часов самой базы с запасом. Окно снизу от этого
  только шире; границы периода по `order_.createtime` не меняются.
- Тесты `ecom`: 67 → 68 (`theIdWindowFunctionNeverGetsTimeBeyondTheDatabaseClock` — во всех запросах, в
  том числе для периода в будущем и с верхней границей). `./gradlew test` — 732 запуска, все зелёные.
- Документы: `AGENTS.md` §10 (пункт снят из неподтверждённого, добавлено правило вызова).

### 14.09.2026 — выгрузка стенда по BazarStore: реверсалы (Р-77)

- Пришла полная выгрузка по двум терминалам стенда: 114 заказов, 188 операций, все статусы провайдера
  (`FullyPaid`, `PartPaid`, `Closed`, `Refused`, `Cancelled`, `Rejected`, `Expired`), впервые — реверсалы
  (`voidkind` `Full` и `Partial`) и отказы (`InvalidRequest`, `DestNotAvail`, `SystemError`,
  `FormatError`, пустой код). Все 114 заказов прогнаны через настоящий `EcomOrderAssembler` отдельной
  программой вне сборки: неразобранных операций нет.
- Реверсал бывает двух видов. У холда (`Purchase` / `Auth`, `clearamt` 0, `authkind` `Undefined`) он
  снимает холд целиком или частично. У покупки (`Purchase` / `Single`, `clearamt` с минусом) — отменяет
  её. Второй вид код считал возвратом: 175162 и 175164, у провайдера `Cancelled`, выходили «Возвращён».
  По Р-77 реверсал покупки уменьшает `capturedAmount`, в `refundedAmount` — только возвраты, полностью
  отменённая покупка — `CANCELED`. После правки все четыре `Cancelled` совпали, остальные 112 заказов
  не изменились.
- Намеренно расходятся с кодом провайдера (статус по деньгам): `Rejected` после одобренного холда без
  списания (175378) — `CANCELED`, `Rejected` после полного возврата (175246) — `REFUNDED`. `PartPaid`
  бывает не только после частичного возврата, но и при оплате меньше суммы заказа (`Single` на 10 при
  заказе на 16) — это `SUCCESS` с `capturedAmount` меньше `amount`. Таблица соответствий — `ecom.md` §2.3.
- Итог по выборке выписки (без двух `Expired`): 112 заказов, списано 2 199 AZN, возвращено 258 AZN;
  `SUCCESS` 59, `FAILED` 23, `CANCELED` 16, `PARTIALLY_REFUNDED` 7, `REFUNDED` 7.
- Пояс дат: последняя операция в выгрузке — 17:24:53, файл создан в 18:27 по Баку, значит смещение
  базы не меньше +3 часов и это не UTC; `Asia/Baku` с этим согласуется, подтверждения провайдера нет.
- Тесты `ecom`: 68 → 72 — все сочетания статусов стенда (`TxpgRows.providerStatusesSeenOnTheStand`, по
  заказу на сочетание), реверсал покупки полный и частичный, частичный реверсал холда; фикстура 175195
  дополнена строками холда и реверсала. `./gradlew test` — 736 запусков, все зелёные.
- Документы: `decisions.md` (Р-77), `ecom.md` §1 и §2.3, `AGENTS.md` §10, `technical_handover.md` §4.6.

### 14.09.2026 — справочник терминалов по SQL провайдера, частичная оплата, пояс (Р-78, Р-79)

- **Пояс дат шлюза — Asia/Baku**, провайдер подтвердил; из неподтверждённого в `AGENTS.md` §10 снято.
- **Справочник терминалов провайдера** (`TxpgProviderTerminalSource`) переписан по SQL провайдера:
  `login` → `terminal` → `terminalpmo` → `merchant`, процессинг `70`, TID на `PBY`. Строку со статусом
  провайдер прислал закомментированной; по решению пользователя берутся терминалы, у которых и логин,
  и терминал `Active` (Р-79), — выключенный у провайдера пропадает и через три опроса гасит наш терминал.
  Ключ остался `merchant.rid` (по нему идёт выписка), название — мерчанта, логин — `login.login`.
  Прежний черновой запрос читал `merchant.login`, которого в схеме нет.
- **Одна строка на мерчанта** (пользователь подтвердил). Одинаковые строки схлопываются; разные логины у
  одного мерчанта в опросе не применяются: мерчант не обновляется и не гаснет, новый не заводится, в
  лог — предупреждение, в ответе ручной синхронизации — новое поле `ambiguous`.
- **Частичная оплата — отдельный статус `PARTIALLY_PAID`** (Р-78): списано больше нуля, но меньше суммы
  заказа, возвратов нет. Прогон 114 заказов выгрузки через код: 12 заказов, которые провайдер ведёт как
  `PartPaid` без возвратов, из «успешно» стали частично оплаченными (оплата 10 при заказе на 16 и
  списание после снятия части холда); `SUCCESS` остался только у полных оплат, остальные не изменились.
  Возврат важнее недоплаты, мультиклиринг сверх суммы заказа — `SUCCESS`.
- **Название «Отменён» (`CANCELED`) подтверждено**; в Р-75 снята пометка об ожидании.
- Справочник на данных ещё не проверялся: выгрузки по нему не было, тесты проверяют форму запроса.
- Тесты `ecom`: 72 → 81 — `TxpgProviderTerminalSourceTest` (фильтры, ключ по мерчанту), три теста
  синхронизации на дубли, четыре на частичную оплату в `EcomStatusResolverTest`; ожидания по выгрузке
  стенда обновлены. `./gradlew test` — 745 запусков, все зелёные.
- Документы: `decisions.md` (Р-78, Р-79, правка Р-75), `ecom.md` §1, §2.2, §2.3, §2.5, §3, `AGENTS.md` §10,
  `technical_handover.md` §4.6.

### 14.09.2026 — вкладка E-commerce на API `ecom`, форма терминала из справочника (Р-80)

- **Вкладка `/transactions/ecommerce` переписана.** Раньше она брала общий список операций портала из
  `App` и отсеивала на экране строки с `channel === 'ecommerce'` — к выписке провайдера отношения не
  имела. Теперь это отдельная страница на API `ecom`: период по дате создания (по умолчанию последние
  семь суток, проверка «не длиннее 92 дней» ещё до запроса), терминалы из `/transactions/terminals`,
  сумма и поиск по номеру заказа, `ridByMerchant` или RRN уходят на сервер; итоги — `/stats` по всему
  периоду (заказы, списано и возвращено по валютам, счётчики статусов); страница курсорная с «показать
  ещё»; выгрузка в Excel — только загруженных строк. `merchantRids` уходит повторяющимся параметром:
  axios по умолчанию шлёт `merchantRids[]`, и контроллер молча не узнал бы фильтр.
- **Карточка заказа** `/transactions/ecommerce/:orderId`: идентификаторы и статус провайдера, деньги,
  карта и терминал (логином, как везде — Р-59), история операций с нашим видом операции и сырыми
  кодами провайдера рядом. 404 — одно сообщение на «нет, чужой или не завершён».
- **Два новых статуса на экране**: `PARTIALLY_PAID` («Частично оплачен», бирюзовый) и `CANCELED`
  («Отменён», серо-синий — не путать с серым «неизвестным» статусом); словарь вкладки свой —
  `tObj.ecommerce`, на трёх языках. Статусы и разбор ответа — `types/ecom.ts` и `utils/ecom.ts`.
- **Форма заведения терминала**: у `SYSTEM_ADMIN` вместо полей названия и логина — выбор из справочника
  провайдера (`GET /api/v1/ecom/provider-terminals`) с кнопкой «Обновить справочник»; в запрос уходит
  `merchantRid`, «Тест» проверяет логин выбранной строки. У руководителя и менеджера справочника нет
  (403), у них ручной ввод остался. Подписи полей формы переведены — там были зашиты английский и
  русский текст.
- **Комментарии `ecom`**: последние 13 блоков `/** */` в 8 файлах переписаны в `//` по правилу §9
  (заодно ушло устаревшее упоминание таблицы привязок мерчантов в `EcomApplication`). В основном коде
  `ecom` javadoc-блоков больше нет.
- Проверено: `npm run typecheck`, `npm run build`, `npm run lint` (0 ошибок, 33 предупреждения — новых
  в изменённых файлах нет); `./gradlew :ecom:test` — 81, все зелёные. В запущенном dev-сервере новые
  модули загружаются без ошибок, разбор ответа, проверка периода, цвета и подписи отработали на
  примерах; сами страницы за входом в браузере не открывались — нужен пароль.
- Документы: `decisions.md` (Р-80), `AGENTS.md` §9, `ecom.md` §1, `application_description.md` §8 и §10,
  `technical_handover.md` §4.6.

### 14.09.2026 — номер терминала выдаёт база (Р-81)

- «Numeric Terminal ID» больше не нужен: терминал опознаётся логином и терминалом провайдера. Поле убрано
  из формы заведения и колонка — из списка терминалов; в заголовках окон правки, подтверждения правки и
  блокировки терминал подписан логином вместо `#номер`. Подписи `terminals.terminalId` и
  `terminalIdHint` удалены из словаря.
- Номер остаётся внутренним ключом — на него ссылаются платёжные ссылки, адреса `/api/v1/terminals/{id}`
  и журнал аудита. Его выдаёт последовательность `terminals_id_seq` (`directory/007`): на живой базе она
  продолжает после наибольшего номера (`setval` в той же миграции), у колонки есть значение по умолчанию
  для записи мимо сервиса. `CreateTerminalRequest` без `id`; присланный клиентом `id` игнорируется.
  Номер берёт `TerminalRepository.nextId` после всех проверок, а не `@GeneratedValue`: тесты и сверка
  сохраняют терминалы с заданным номером, и `save` сущности с генерируемым ключом молча заменил бы его.
- Отказ в заведении пишется в журнал с `entityId` `NEW` — номера у ещё не сохранённого терминала нет.
- Postman (`directory`): из «Create Terminal» убран `id`, ответ записывает номер в `{{terminalId}}`.
- Тесты `directory`: 109 → 111 — номер выдаёт база и присланный `id` игнорируется
  (`DirectoryIntegrationTest`), нумерация на живой базе продолжается после наибольшего номера
  (`SharedSchemaMigrationTest`, PostgreSQL); фикстуры заведения берут номер из ответа. `./gradlew test` —
  747 запусков, все зелёные. Фронтенд: `typecheck`, `build`, `lint` (0 ошибок).
- Документы: `decisions.md` (Р-81), `directory.md` (`POST /terminals`), `AGENTS.md` §10,
  `application_description.md` §4.3, `technical_handover.md` §4.4.

---

### 14.09.2026 — ревью фронтенда: деньги, роли, форма ссылки, мёртвый код (Р-82)

- **Деньги.** Остаток к возврату считается в копейках (`refundableLeftOf`): `10.10 − 9.80` в double
  давало `0.29999999999999893`, бэкенд отвечал 400 «more than two decimal places», а окно показывало
  `0.30`. Запрет на повтор после неподтверждённого исхода (502) снимался любым ответом 200 на
  `/status`, хотя по операции в терминальном статусе бэкенд эквайера не спрашивает; теперь его снимает
  только перечитанная операция с движением денег (`moneyMoved`), а сбой самой проверки — отдельная
  ошибка (`checkError`), не исход. Новые ключи `statusStillUnresolved`, `checkStatusFailed`; подпись
  «Refund Amount (AZN)» без валюты.
- **Роли.** `COMPANY_HEAD` не мог создать пользователя, `COMPANY_HEAD`/`COMPANY_MANAGER` — терминал:
  формы брали компанию из `GET /companies` (403 для них). Компанию выбирает только `SYSTEM_ADMIN`,
  остальным подставляется своя из токена, список компаний им не запрашивается. Заведён
  `auth/actionAccess.ts` (`TERMINAL_WRITE_ROLES`, `LINK_WRITE_ROLES`, `REFUND_ROLES`): кнопки
  заведения/правки/блокировки терминалов, возврата и списания видны только ролям, которым бэкенд их
  примет; `COMPANY_HEAD` не предлагается назначать `SYSTEM_ADMIN`.
- **Терминалы.** Подтверждение правки без `busy` давало два PATCH по двойному клику; PATCH шлёт только
  изменившиеся поля (журнал писал «Name changed from X to X»); `fetchCompanies` подменял компанию в
  форме правки из устаревшего замыкания; окно блокировки открывалось заново после «Отмена», когда
  приходил счёт ссылок; ошибки «Тест» и показа пароля не показывались (писались в `error` диалога).
  Русские и английские строки страницы — в словарь; «Тест» проверяет тот же обрезанный пароль, что
  сохраняется.
- **Ссылки.** Двойная пагинация (сервер + `slice`) оставляла страницы со второй пустыми; пустые поля
  клиента заменялись на «N/A», `customer@example.com`, `+994500000000` и сохранялись в базу; выбор
  срока, redirect URL, заметка и «отправить письмо» собирались и выбрасывались — срок теперь уходит как
  `expiresAt`, остальные контролы убраны; `maxUses || 5` заменён валидацией; статистика по одной
  серверной странице и клиентский поиск сняты, фильтр по статусу серверный; кнопки Email/WhatsApp — настоящие
  `mailto:`/`wa.me` с кодированием (раньше копировали строку в буфер), QR-«скоро» убран; окно создания не
  закрывается во время запроса; `₼` заменён на `formatCurrency` с валютой ссылки; карточка различает
  загрузку, отказ и «не найдена», связанные операции разбирают статус через `parseTransactionStatus`.
- **Выдуманные поля.** `mapTransaction` больше не ставит `fee: 0`, `timestamp = now`, «Customer», «N/A»,
  «Transaction», `AZN`; строки «комиссия» и «к получению» сняты с карточки операции, вместо них —
  склиренная сумма, когда она отличается; `failureReason` показывается. Выписка `ecom` не подставляет
  AZN пустой валюте; `formatCurrency` без валюты печатает число. Разбор `Approved` — в `utils/ecom.ts`,
  пустой код результата — «неизвестно», не отказ.
- **Структура.** Страница `/transactions` с `FilterPanel`, `StatsOverview`, `TransactionTable` и
  состояние операций в `App.tsx` удалены (Р-65); роутер создаётся один раз на уровне модуля (раньше —
  на каждое изменение фильтра, с утечкой `popstate`-listener'ов); карточка операции грузит себя сама,
  после возврата/списания перечитывает, а не уводит на список; `Header`/`MainLayout` без
  `newTransactionCount`; выход — через `ConfirmDialog`. Удалены Tailwind, `tw-animate-css`, shadcn-тема,
  `figma/ImageWithFallback`, `src/assets/*`, пустые CSS, хак `propTypes` и `figmaAssetResolver`;
  `utils/mockData.ts` → `utils/format.ts`.
- **Вход.** Сетевая ошибка больше не показывается как «проверьте пароль»; `MALFORMED_RESPONSE` — свой
  текст, а не отладочная строка; ложная надпись о 2FA и недостижимый OTP-диалог удалены; поля с
  `name`/`autoComplete`, форма `noValidate`; после входа — на адрес, с которого увели на `/login`
  (`state.from`, только свой относительный путь).
- **Мелочи.** `LanguageContext` читает `localStorage` под `try/catch`; группы сайдбара раскрываются по
  ключу, а не по локализованной подписи, версия — из `package.json` (`__APP_VERSION__`); «Обновить» в
  журнале аудита идёт через отменяемый эффект; спиннер выписки гаснет при невалидном периоде; `HomePage`
  подписывает статусы и типы словарём, границы периода — в зоне сервера, «платежей нет» не показывается
  поверх ошибки загрузки; `RouteErrorPage` перезагружает страницу при упавшем чанке.
- Проверка: `npm run typecheck`, `npm run lint` (0 предупреждений, было 33), `npm run build` — зелёные.
  Бэкенд не трогался; в браузере с живым бэкендом не проверялось — сервисы на стенде не были запущены.
- Документы: `decisions.md` (Р-82), `AGENTS.md` §3, §8, §9, §10, `application_description.md` §10.

---

### 15.09.2026 — скоуп выписки `ecom` по логинам терминалов (Р-83)

- **Пришёл запрос выписки** со скоупом по логинам: `tr.merchantid in (select l.merchantid from login l
  where l.ownerkind = 'TerminalSys' and l.login in (...))` и `m.id = tr.merchantid` в join. От портала он
  отличался этими двумя местами; окно `sysdate - 100`, `join login`, `join token` без подзапроса, отсутствие
  фильтра Р-71 и страниц портал по-прежнему не переносит (Р-71, Р-74). По выбору пользователя взяты
  только скоуп и `m.id = tr.merchantid`.
- **Как устроено.** `EcomScopeService.scopeFor` отдаёт `EcomScope`: логины терминалов скоупа (префикс
  `TerminalSys/` Basic-логина снимается, пустые и повторы отбрасываются) и их `merchant_rid`. Все три
  запроса `TxpgTransactionRepository` несут `loginScope()` и `m.id = tr.merchantid`; `merchantRids`
  фильтра — `m.rid in (:merchant_rids)` только в выборе заказов периода и только когда фильтр задан.
  `EcomTransactionFilter` — `logins` плюс необязательные `merchantRids`. Сущность `Terminal` в `ecom`
  читает `login`, `TerminalRepository` — `findByCompanyId` вместо двух запросов по `merchant_rid`.
- **Что меняется для пользователя.** Терминал, заведённый вручную без `merchant_rid`, теперь даёт
  выписку по логину, но в фильтре `/terminals` его нет. API и фронтенд не менялись. Выписка доверяет
  логину терминала — ограничение записано в `AGENTS.md` §10.
- **Проверено на локальном `oracle-free`** (схема `TXPG` по SQL провайдера, выгрузка BazarStore) временным
  тестом, после проверки удалённым: новый репозиторий под `MP_ECOM` и старый из `HEAD` дали одинаковые
  строки страницы и итогов (372), собранные заказы совпали, с фильтром по терминалу — тоже (128). Неизвестный
  логин и `merchantRid` мерчанта вне скоупа логинов дают пусто, карточка заказа чужого мерчанта — пусто. Сравнение шло на
  задвоенных данных (в `TRAN` были копии операций BazarStore с `id` 900001–900188); после их удаления
  выписка по двум логинам — 112 заказов, 186 операций, списано 2 199 AZN, возвращено 258 AZN, `SUCCESS`
  47, `PARTIALLY_PAID` 12, `FAILED` 23, `CANCELED` 16, `PARTIALLY_REFUNDED` 7, `REFUNDED` 7 — ровно
  итоги выгрузки из записей Р-77 и Р-78.
- Тесты `ecom`: 81 → 87 — новый `EcomScopeServiceTest` (компания, администратор и аудитор, префикс
  `TerminalSys/`, роль без компании), скоуп по логинам и сужение фильтром в
  `TxpgTransactionRepositoryTest`, терминал без `merchant_rid` в `EcomTransactionScopeTest`.
  `./gradlew test` — 753 запуска, все зелёные.
- Документы: `decisions.md` (Р-83), `ecom.md` §2.1, §2.4, §2.6, `AGENTS.md` §10,
  `application_description.md` §6 и §8.

---

## Описания закрытых задач

> Перенесено из `AGENTS.md` §10 («Закрытые блокеры» и записи, попавшие в «Тонкости») 13.09.2026
> без правок текста, по порядку дат. Ссылки «§7», «§10», «см. ниже» внутри записей относятся к
> `AGENTS.md` на момент записи, `problems.md §N` — к удалённому файлу (он есть в истории git).

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
  3. **`GET /api/v1/terminals/options`** (Р-45) — `id`, `name`, `login`, `status`, без пагинации
     и без пароля терминала. Отдаёт **и заблокированные**: фильтрует потребитель, а не сервер
     (см. «грабли»). На него переведены форма создания ссылки, экран транзакций, карточка
     транзакции, главная и фильтр по терминалу; врезки `SettingsPage` брали одну страницу по
     потолку — сами врезки удалены 24.08.2026 (P3-6). `login` добавлен 11.09.2026 — см. «грабли».
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

### `technical_handover.md` расходился с кодом (запись на 25.08.2026)

> Устарело 13.09.2026: техпаспорт переписан по коду.


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

---

## Тесты: что покрывает каждый класс (снимок на 12.09.2026)

> Перенесено из `AGENTS.md` §11 13.09.2026. Числа методов — на дату последней правки строк и с тех
> пор не поддерживаются: актуальное число даёт `./gradlew test`. Тестов `ecom` и кнопки «Тест» в
> таблице нет.

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
