# Модуль Аутентификации и Авторизации (Auth Service)

> Контракты API сервиса `auth`. Сверено с кодом 29.09.2026. Схема таблиц —
> `../guides/application_description.md` §4, роли и матрица доступа — `../../AGENTS.md` §6, переменные
> окружения — `../guides/deployment_guide.md` §20.1.

## 1. Архитектура и Структура
-   **Стек**: Spring Boot, модуль `auth` Gradle-монорепозитория, порт `8081`.
-   **Связь с другими модулями**:
    -   `auth` отвечает за учётные записи, вход и выдачу JWT токенов пользователей.
    -   `directory` отвечает за компании и терминалы.
    -   `directory`, `pbl` и `ecom` проверяют подпись JWT тем же секретом и читают роль и компанию пользователя из claims.

---

## 2. База данных

Таблицы `auth` — `users`, `refresh_tokens`, `password_history`; схема — `../guides/application_description.md`
§4 (ER — §4.1, миграции — §4.3). Таблицу `companies` `auth` создаёт, если её ещё нет, и читает: существование
компании при создании и переводе пользователя, название — для поиска в списке пользователей. Журнал пишется
в общий `audit_logs`.

---

## 3. Спецификация JWT Токена

Модуль `auth` подписывает JWT-токен симметричным ключом (`HS256`). Ключ берётся из переменной
окружения `JWT_SECRET` — значения по умолчанию у него нет, и без неё сервис не стартует.
То же значение обязано стоять у `directory`, `pbl` и `ecom`: они проверяют подпись тем же ключом.
Срок — `JWT_EXPIRATION_MS`, по умолчанию 15 минут. Claims токена:

```json
{
  "sub": "user@company.com",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "COMPANY_HEAD",
  "companyId": "comp-01",
  "iat": 1787011200,
  "exp": 1787012100
}
```

`companyId` — только у пользователя с компанией; у `SYSTEM_ADMIN` и `AUDITOR` без компании этого claim'а нет.
Роль и компания берутся из строки пользователя при каждом выпуске токена — на входе и на `/refresh`.

---

## 3.1. Первый администратор

Миграции пользователей не заводят. Первый `SYSTEM_ADMIN` создаёт `AdminBootstrapRunner` на одном запуске
`auth` с `AUTH_BOOTSTRAP_ENABLED=true` из `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` и только
на пустой таблице `users`; процедура — `../guides/deployment_guide.md` §20.1, шаг 4.

-   Администратор создаётся с `passwordChangeRequired: true`: первый вход — через смену пароля (§4.1.3, Р-100).
-   В журнал — `USER` / `CREATE` от `system`: `Admin bootstrap created the first SYSTEM_ADMIN <логин>`.
-   Логин не email, пароль не по политике или нет переменных — сервис не стартует.

---

## 4. API Контракты (Эндпоинты `auth`)

Эндпоинты `/api/v1/auth/**` публичны, `/api/v1/users/**` — только с access-токеном (`Authorization: Bearer …`).
Ответ об ошибке — `ErrorResponse { timestamp, status, error, message, path }`. Общие отказы:

| Код | `message` | Когда |
|:---|:---|:---|
| `401` | `Missing or invalid Authorization header` / `Invalid or expired JWT token` | `/users` без токена или с негодным |
| `400` | `Invalid request payload format or parameter value` | битый JSON или значение не того типа в теле |
| `400` | `Parameter '<имя>' has an invalid value` | `id` в пути не UUID, нечисловой `page` или `size`; значение в ответ не попадает |
| `415` | `Content-Type … is not supported by this endpoint; send application/json` | тело не в JSON |

### 4.1. Вход в систему
-   **Метод**: `POST /api/v1/auth/login`
-   **Тело запроса**:
    ```json
    {
      "username": "user@company.com",
      "password": "secure_password"
    }
    ```
    Логин перед поиском обрезается и приводится к нижнему регистру.
-   **Ответ (200 OK)**:
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expiresIn": 900,
      "role": "COMPANY_HEAD",
      "refreshToken": "Q2FuJ3QgZ3Vlc3MgbWUsIEknbSByYW5kb20hISEh",
      "refreshExpiresIn": 1200,
      "passwordChangeRequired": false
    }
    ```
    -   `expiresIn` — срок access-токена в секундах, из `pbl.security.jwt.expiration-ms` (`JWT_EXPIRATION_MS`),
        по умолчанию 900 (15 минут).
    -   `refreshToken` — непрозрачная случайная строка (32 байта `SecureRandom`, base64url),
        не JWT; в базе хранится только её SHA-256. Одноразовая: каждый `refresh` выдаёт новую и гасит предъявленную.
    -   `refreshExpiresIn` — срок refresh-токена в секундах (`auth.refresh.ttl`, по умолчанию 20 минут, Р-99).
        Срок скользящий: каждый `refresh` выдаёт токен на новые 20 минут, так что это
        граница простоя на сервере (PCI DSS 8.2.8). Фронтенд выходит сам после 15 минут без действий и,
        пока пользователь работает, обновляет пару не реже раза в 5 минут.

    Каждый логин начинает новую **цепочку** (`family_id`) — на ноутбуке и на телефоне у одного
    пользователя две независимые сессии; выход из одной не трогает другую. Успешный вход обновляет
    `last_activity_at` (отсчёт до автоблокировки, §4.1.6).
-   **Пароль задал не владелец** (PCI DSS 8.3.5, Р-100) — при создании пользователя, при
    сбросе чужого пароля через `PATCH /users/{id}`, при bootstrap. Верный пароль тогда даёт `200` без
    сессии: `{"token": null, "expiresIn": 0, "role": "COMPANY_EMPLOYEE", "refreshToken": null,
    "refreshExpiresIn": 0, "passwordChangeRequired": true}`, в журнале — `AUTH` / `LOGIN` / `DENIED`
    `Login held: the password must be changed first`. Сессию даёт только смена пароля (§4.1.3).
-   **Отказы** — в порядке проверки:

    | Код | `message` | Когда | Журнал |
    |:---|:---|:---|:---|
    | `400` | `Username is required`, `Username must be a valid email address`, `Password is required` | не прошла валидация тела | — |
    | `429` | `Too many login attempts. Please try again later.` | с адреса клиента 10 неудачных попыток за 15 минут (`LOGIN_RATE_LIMIT_*`); заголовок `Retry-After` — секунды до конца окна | — ; `AUTH` / `RATE_LIMIT` пишет неудача, исчерпавшая лимит, — одна запись за окно |
    | `400` | `Invalid username or password` | нет такой учётки **или** неверный пароль — ответ одинаковый | `AUTH` / `LOGIN` / `DENIED`: `Login refused: no such account` или `… wrong password` |
    | `400` | `Account is locked due to multiple failed login attempts. Please try again in N minutes.` | пароль верен, но учётка в локауте | `AUTH` / `LOGIN` / `DENIED`: `Login refused: account locked until …` |
    | `400` | `Account is not active. Please contact your administrator.` | пароль верен, статус не `ACTIVE` | `AUTH` / `LOGIN` / `DENIED`: `Login refused: account status …` |

    Неизвестный логин и неверный пароль считаются в лимит адреса; успешный вход счётчик адреса обнуляет.
    Шесть неверных паролей подряд — локаут учётки на 30 минут (PCI DSS 8.3.4, Р-28) и запись `AUTH` /
    `LOCKOUT`; неверный пароль во время локаута его не продлевает. Лимит адреса и локаут действуют и на
    смену пароля (§4.1.3). Журнал успеха — `AUTH` / `LOGIN` / `SUCCESS` `Login successful, role …`.

### 4.1.1. Обновление пары токенов
-   **Метод**: `POST /api/v1/auth/refresh` — публичный (`/api/v1/auth/**`), заголовок
    `Authorization` не нужен: клиент зовёт его как раз тогда, когда access-токен истёк.
-   **Тело запроса**: `{ "refreshToken": "..." }`
-   **Ответ (200 OK)**: тот же `LoginResponse`, что у логина — новый access-токен и **новый**
    refresh-токен в той же цепочке. Предъявленный токен получает `rotated_at` и больше
    не должен использоваться. Роль и компания в новом access-токене — из текущей строки пользователя.
-   **Отказ — всегда `401 { "message": "Invalid refresh token" }`**, без уточнения причины
    (клиенту не сообщается, был ли токен неизвестным, просроченным, отозванным или повторно
    использованным). Причины, в порядке проверки:
    1. хеш токена не найден;
    2. `expires_at` в прошлом;
    3. цепочка отозвана (`revoked_at` заполнен — logout, блокировка, кража);
    4. токен уже заменён (`rotated_at` заполнен) **и** с момента замены прошло больше
       `auth.refresh.rotation-grace` (10 с). Это **повторное использование**: предыдущий
       владелец уже ушёл дальше, значит, копия есть у кого-то ещё. Гасится **вся цепочка**,
       в лог пишется `ERROR` с маркером **`REFRESH_TOKEN_REUSE`** — по нему настраивается
       мониторинг, в журнал — `AUTH` / `TOKEN_REUSE` / `DENIED` `Rotated refresh token reused … revoked N
       token(s) of family …`. Тот же повтор **внутри** окна — это гонка двух вкладок с одним токеном:
       запрос обслуживается, цепочка живёт, окно от повтора не сдвигается;
    5. пользователь не в статусе `ACTIVE` (или удалён физически) — цепочка гасится;
    6. пароль пользователя задан не им и ещё не сменён (Р-100) — цепочка гасится.
-   Отсутствующее поле `refreshToken` — `400 Refresh token is required`, не `401`.
-   Успешный `refresh` обновляет `last_activity_at` не чаще раза в сутки (Р-101).

### 4.1.2. Выход из системы
-   **Метод**: `POST /api/v1/auth/logout` — публичный, `Authorization` не нужен.
-   **Тело запроса**: `{ "refreshToken": "..." }` (можно и без тела).
-   **Ответ: `204 No Content`** — и для живого токена, и для неизвестного, и для пустого тела.
    Иначе эндпоинт стал бы оракулом «существует ли такой токен». Битый JSON — `400`, тело не в JSON — `415`
    (общие отказы, §4).
-   Гасится **вся цепочка** по `family_id`, а не один токен: выход закрывает сессию целиком,
    включая токен, только что выданный параллельной вкладкой. Другие сессии того же
    пользователя (другие логины) не затрагиваются.
-   Журнал: `AUTH` / `LOGOUT` / `SUCCESS` с логином в `entityId` — только когда токен нашёлся и что-то
    было отозвано; 204 на неизвестный или уже погашенный токен записи не оставляет.
-   **Access-токен при выходе не отзывается** и живёт до своего `exp` — до 15 минут: все сервисы проверяют
    его без базы, чёрного списка нет (`../../AGENTS.md` §5 п. 4). То же после блокировки и
    удаления пользователя: доступ пропадает на первом `/refresh`, который будет отклонён.

### 4.1.3. Смена пароля при входе (Р-100)
-   **Метод**: `POST /api/v1/auth/change-password` — публичный, как `/login`; `Authorization` не нужен.
-   **Тело запроса**: `{ "username": "…", "currentPassword": "…", "newPassword": "…" }`.
-   **Проверка текущего пароля — та же, что у входа** (§4.1): лимит адреса, локаут, статус, те же тексты
    отказов и записи в журнал; неверный текущий пароль — `400 Invalid username or password` и неудачная
    попытка в счётчиках.
-   **Новый пароль**: политика — не короче 12 символов, заглавная, строчная, цифра и спецсимвол (PCI DSS v4.0),
    иначе `400 Password must be at least 12 characters long and include uppercase, lowercase, digit, and special
    character (PCI-DSS v4.0 compliant)`; не повторяет ни один из четырёх последних — текущий и три из
    `password_history` (PCI DSS 8.3.7, Р-102), иначе `400 The new password must differ from the last 4 passwords`.
    Прежний пароль уходит в `password_history`.
-   Валидация тела: `Username is required`, `Username must be a valid email address`, `Current password is
    required`, `New password is required` — `400`.
-   **Ответ `200`** — как у входа (§4.1): пара токенов, `passwordChangeRequired: false`. Флаг обязательной
    смены снимается, прочие сессии пользователя гасятся. Журнал: `USER` / `PASSWORD_CHANGE`
    `Password changed by its owner at sign-in` и `AUTH` / `LOGIN` / `SUCCESS`.
-   Работает и без обязательной смены — как смена своего пароля по текущему.
-   `/refresh` пользователя с обязательной сменой отказывает (`401`) и гасит цепочку (§4.1.1).

### 4.1.4. Отзыв при блокировке, удалении и смене пароля
Все refresh-токены пользователя во всех его сессиях (`RefreshTokenService.revokeAllForUser`) гасят:
`PATCH /api/v1/users/{id}` со `status: BLOCKED`, сброс чужого пароля через `PATCH`, `DELETE /api/v1/users/{id}`,
смена пароля при входе (§4.1.3; кроме новой сессии) и автоблокировка (§4.1.6). С этого момента `/refresh`
для него отвечает 401; фактически доступ он теряет, когда истечёт его текущий access-токен (§4.1.2).

### 4.1.4a. Гонка «refresh против отзыва»
Refresh и отзыв одной цепочки могут идти одновременно (logout из другой вкладки, блокировка
администратором посреди refresh). Чтобы тот, кто коммитится вторым, видел работу первого:
ротация — условный `UPDATE … WHERE revoked_at IS NULL` (0 строк → 401, преемник не выпускается,
отзыв не затирается), а отзыв цепочки/пользователя сначала берёт `SELECT … FOR UPDATE` и лишь потом
делает bulk UPDATE — так он дожидается идущего refresh и гасит и вставленного им преемника.
Покрыто `RefreshTokenConcurrencyTest`.

### 4.1.5. Уборка
`RefreshTokenCleanupScheduler` (cron `auth.refresh.cleanup-cron`, по умолчанию `0 30 3 * * *`)
удаляет строки с `expires_at` в прошлом. Это только гигиена: просроченный токен и так отклоняется
на шаге 2. Выключается `auth.refresh.cleanup-enabled=false` (в тестовом профиле выключен).

### 4.1.6. Блокировка неактивных учёток (PCI DSS 8.2.6, Р-101)
`InactiveAccountScheduler` (cron `auth.inactivity.cron`, по умолчанию `0 45 3 * * *`) раз в сутки
блокирует учётки в статусе `ACTIVE`, у которых `last_activity_at` старше `auth.inactivity.max-idle`
(90 дней): статус `BLOCKED`, все refresh-токены гасятся, в журнале `USER` / `BLOCK` от `system`
(`Account … blocked: no activity for more than 90 days (PCI DSS 8.2.6), last activity …`).
Активность — вход, `refresh` (не чаще раза в сутки), создание учётки, разблокировка; это не «последний вход»,
и на экранах так не показывается. Администраторы не исключение: единственного заблокированного
администратора возвращают в базе (`../guides/deployment_guide.md` §20.3). Вернуть учётку — `PATCH /users/{id}`
со `status: ACTIVE`: разблокировка сбрасывает отсчёт. Выключается `auth.inactivity.enabled=false` (в тестовом
профиле выключен).

### 4.2. Управление Пользователями (CRUD)
Доступно ролям `SYSTEM_ADMIN` и `COMPANY_HEAD`. **Руководитель выдаёт и правит только роли ниже своей** —
`COMPANY_MANAGER`, `COMPANY_EMPLOYEE` — в своей компании, и себя (Р-85). Создание, правка и удаление сверяют
статус актора с базой: актор, чья учётка уже не `ACTIVE`, получает `403 Access denied` и запись `DENIED`
`Denied: actor account is <статус>` — заблокированный за время жизни токена не снимет блокировку сам.

1.  **Создать пользователя** (`POST /api/v1/users`)
    -   *Тело*:
        ```json
        { "username": "manager@comp01.com", "password": "<12+ символов: заглавная, строчная, цифра, спецсимвол>",
          "fullName": "…", "role": "COMPANY_MANAGER", "companyId": "comp-01" }
        ```
        Логин обрезается и приводится к нижнему регистру. `companyId` обязателен для ролей компании
        (`COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`); у `SYSTEM_ADMIN` и `AUDITOR` его можно не
        передавать, пустая строка — то же, что без компании. Роль по словарю не проверяется: учётке с неизвестной ролью везде отказывают (`../../AGENTS.md` §6).
    -   *Кто*: *админ* — любого пользователя в любую компанию; *руководитель* — только в свою компанию и только
        с ролями `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`.
    -   *Ответ `201`*: `UserResponse` (как в списке ниже), `passwordChangeRequired: true` — пароль задал не
        владелец, он сменит его при первом входе (Р-100).
    -   *Отказы* — в порядке проверки:

        | Код | `message` | Когда | Журнал |
        |:---|:---|:---|:---|
        | `400` | `Username is required`, `Username must be a valid email address`, `Username must be at most 255 characters`, `Password is required`, `Full name is required`, `Full name must be at most 255 characters`, `Role is required`, текст политики пароля (§4.1.3) | не прошла валидация тела | — |
        | `403` | `Access denied` | учётка актора не `ACTIVE` | `USER` / `CREATE` / `DENIED` |
        | `403` | `Cannot create user for another company` | руководитель не указал `companyId` или указал чужую | — |
        | `403` | `Cannot assign this role` | руководитель выдаёт роль не из `COMPANY_MANAGER`, `COMPANY_EMPLOYEE` | `USER` / `CREATE` / `DENIED` |
        | `403` | `Access denied` | прочие роли | — |
        | `400` | `Username already exists` | логин занят, в том числе удалённой учёткой | — |
        | `400` | `Role <ROLE> requires a company` | роль компании без `companyId` (Р-103) | — |
        | `400` | `Company not found` | компании нет или она удалена | — |
        | `409` | `The request conflicts with existing data` | одновременный запрос успел занять тот же ключ между проверкой и записью; повтор получит отказ выше | — |

    -   *Журнал*: `USER` / `CREATE` `Created user <логин> with role <роль> in company <id>`.
2.  **Список пользователей** (`GET /api/v1/users`) — постранично (P2-1).
    -   *Админ* видит всех; *руководитель* — только пользователей своей компании, руководитель без компании —
        пустую страницу. Остальные роли — `403 Access denied`, без записи в журнал.
    -   *Параметры*: `page` (по умолчанию `0`), `size` (по умолчанию `20`, потолок `200`). Значения **приводятся**,
        а не отклоняются: `?page=-1&size=0` отвечает `200`. Нечисловое значение — `400` (§4).
    -   *Поиск и фильтр* (P3-1): `search` — подстрока без учёта регистра по
        `username`, `fullName`, `companyId` и **названию компании**; `%` и `_` ищутся буквально
        (экранируются, ввод `%` не возвращает всю таблицу). Пустая или пробельная строка — то же,
        что отсутствие параметра; длиннее 100 символов — обрезается. `role` — точное значение
        роли (регистр не важен); неизвестная роль трактуется как отсутствие фильтра, не `400`.
        Оба фильтра работают **внутри** ролевого скоупа: `COMPANY_HEAD` поиском чужую компанию
        не достаёт. `totalElements` считает отфильтрованное.
    -   *Порядок*: по `username`, при совпадении — по `id`. Довесок обязателен: сортировка по
        неуникальному ключу позволяет базе расположить одинаковые записи по-разному в ответе
        на соседние запросы, и запись попадает на обе страницы сразу или ни на одну.
    -   *Ответ* — `PagedResponse`:
        ```json
        {
          "content": [
            {
              "id": "…", "username": "head@comp01.com", "fullName": "…",
              "role": "COMPANY_HEAD", "companyId": "comp-01",
              "status": "ACTIVE", "createdAt": "…", "passwordChangeRequired": false
            }
          ],
          "totalElements": 1, "totalPages": 1, "size": 20, "number": 0
        }
        ```
        Мягко удалённые учётные записи (`status = DELETED`) не входят ни в `content`, ни
        в `totalElements` — фильтр выполняется в запросе, а не после чтения страницы.
3.  **Получить пользователя** (`GET /api/v1/users/{id}`) — `UserResponse`.
    -   *Кто*: *админ* — любого; *руководитель* — пользователя своей компании.
    -   *Отказы*: `400 User not found` — нет такого или он удалён (`DELETED`); `403 Access denied` — чужая
        компания и прочие роли, без записи в журнал.
4.  **Редактировать пользователя** (`PATCH /api/v1/users/{id}`)
    -   *Тело* — все поля необязательны, `null` или отсутствие поля значит «не менять»:
        ```json
        { "fullName": "…", "role": "COMPANY_MANAGER", "password": "…", "status": "BLOCKED", "companyId": "comp-02" }
        ```
    -   *Кто кого*: *админ* — любого; *руководитель* — пользователей своей компании с ролями
        `COMPANY_MANAGER`, `COMPANY_EMPLOYEE` и себя; выдаёт только эти две роли (Р-85). Роль, совпадающая с
        текущей, — не выдача.
    -   *`status`* — только `ACTIVE` или `BLOCKED`, сравнение точное (Р-103). `DELETED` ставит только `DELETE`.
        `BLOCKED` гасит refresh-токены сразу (§4.1.4); `ACTIVE` у заблокированного сбрасывает отсчёт неактивности
        (§4.1.6).
    -   *`companyId`* (Р-90): перевести пользователя в другую компанию может только
        *админ*; компания должна существовать. Пустая строка снимает компанию. Совпадающее с текущим
        значение — не перевод и доступно всем, кому доступна правка.
    -   *Роль компании без компании запрещена*: если запрос меняет роль или компанию и в итоге у
        `COMPANY_HEAD`, `COMPANY_MANAGER` или `COMPANY_EMPLOYEE` нет `companyId` —
        `400 Role … requires a company`, и откатывается весь запрос. Запросы, которые роль и компанию
        не трогают (блокировка, переименование), старую запись без компании не отклоняют.
    -   *Пароль* (Р-100, Р-102): переданный `password` проходит политику паролей (§4.1.3). Чужой пароль,
        заданный через `PATCH`, пользователь сменит при следующем входе (`passwordChangeRequired: true` в
        ответе), и его сессии гасятся сразу; свой пароль владелец задаёт сам — смены он не требует. Свой пароль
        не повторяет ни один из четырёх последних; чужой с историей не сверяется — отказ сказал бы
        администратору, какие пароли пользователь недавно использовал. Прежний пароль в обоих случаях уходит
        в `password_history`.
    -   *Сессии*: новая роль и компания вступают в силу при следующем `/refresh` — он выпускает токен по текущей
        строке пользователя; до того живёт выданный access-токен (до 15 минут).
    -   *Ответ `200`*: `UserResponse`.
    -   *Отказы* — в порядке проверки:

        | Код | `message` | Когда | Журнал |
        |:---|:---|:---|:---|
        | `400` | текст политики пароля (§4.1.3), `Full name must be at most 255 characters` | не прошла валидация тела | — |
        | `400` | `User not found` | нет такого пользователя или он удалён | — |
        | `403` | `Access denied` | учётка актора не `ACTIVE` | `USER` / `UPDATE` / `DENIED` |
        | `403` | `Access denied` | чужая компания или роль без права правки | — |
        | `403` | `Access denied` | руководитель правит не себя и не `COMPANY_MANAGER`/`COMPANY_EMPLOYEE` | `USER` / `UPDATE` / `DENIED` |
        | `400` | `User status must be ACTIVE or BLOCKED` | иной `status` | — |
        | `400` | `The new password must differ from the last 4 passwords` | свой пароль повторяет один из четырёх последних | — |
        | `403` | `Cannot assign this role` | руководитель выдаёт роль не из `COMPANY_MANAGER`, `COMPANY_EMPLOYEE` | `USER` / `UPDATE` / `DENIED` |
        | `403` | `Cannot move a user to another company` | не админ меняет `companyId` | `USER` / `UPDATE` / `DENIED` |
        | `400` | `Company not found` | админ переводит в несуществующую или удалённую компанию | — |
        | `400` | `Role <ROLE> requires a company` | итог — роль компании без компании | — |

    -   *Журнал*: одна запись `UPDATE` с перечнем изменений (`role A -> B`, `companyId A -> B`, `status …`,
        `fullName`, `password` — фактом); смена пароля и статуса — ещё и свои `PASSWORD_CHANGE`,
        `BLOCK`/`UNBLOCK`.
5.  **Удалить пользователя** (`DELETE /api/v1/users/{id}`) — мягкое удаление: `status = DELETED`.
    -   *Кто*: как у правки — *админ* любого, *руководитель* себя и `COMPANY_MANAGER`/`COMPANY_EMPLOYEE` своей
        компании.
    -   *Ответ `204`*. Все refresh-токены пользователя гасятся (§4.1.4). Повторный `DELETE` удалённого проходит и
        пишет запись ещё раз.
    -   *Отказы*: `400 User not found` — нет такого пользователя; `403 Access denied` — на тех же основаниях, что
        у правки (неактивный актор и руководитель на чужой роли — с записью `USER` / `DELETE` / `DENIED`).
    -   *Журнал*: `USER` / `DELETE` `Soft deleted user <логин> (role <роль>)`.

---

## 5. Коллекция Postman
- **[Auth.postman_collection.json](../../auth/Auth.postman_collection.json)** — все эндпоинты сервиса.
  Запросы `Login`, `Change Password` и `Refresh` сохраняют access-токен в переменную коллекции `authToken`
  и refresh-токен в `refreshToken`, только если ответ их содержит; `Refresh` и `Logout` берут `{{refreshToken}}`
  оттуда. `Create User` сохраняет id созданного пользователя в `userId`. Токен из `Login` подставляют и в
  переменную `authToken` коллекции `directory`.

## 6. Конфигурация
Переменные `auth` (срок токенов, окно ротации, уборка, неактивность, лимит входа, bootstrap, доверенные прокси) —
таблица в `../guides/deployment_guide.md` §20.1.

> ⚠ `AUTH_REFRESH_TTL` (`auth.refresh.ttl`, по умолчанию `PT20M`) не ставить меньше 20 минут: фронтенд
> обновляет пару раз в 5 минут и выходит сам после 15 минут простоя, и более короткий срок обрывал бы живую сессию.
