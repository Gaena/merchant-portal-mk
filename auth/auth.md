# Модуль Аутентификации и Авторизации (Auth Service)

## 1. Архитектура и Структура
-   **Стек**: Java Spring Boot (независимый подпроект в папке `auth`, порт `8081`).
-   **База данных**: Общая PostgreSQL база данных `postgres` (общие таблицы `users`, `companies`, `terminals`).
-   **Связь с другими модулями**: 
    -   `auth` отвечает за регистрацию, вход и выдачу JWT токенов пользователей.
    -   `directory` (соседний будущий модуль) отвечает за создание компаний и терминалов.
    -   `pbl` валидирует подпись JWT токена и проверяет роль/компанию пользователя, переданные в Claims.

---

## 2. База данных (Таблица `users` в общей БД)

Модуль `auth` монопольно управляет таблицей `users` и имеет доступ на чтение к таблице `companies` (для проверки существования компании при создании пользователя).

### Таблица `users`
-   `id` (UUID) — Primary Key.
-   `username` (VARCHAR) — Уникальный email пользователя (логин).
-   `password_hash` (VARCHAR) — Хэш пароля (BCrypt).
-   `full_name` (VARCHAR) — ФИО.
-   `role` (VARCHAR) — Роль (`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR`).
-   `company_id` (VARCHAR) — ID компании из таблицы `companies` (nullable).
-   `status` (VARCHAR) — Статус (`ACTIVE`, `BLOCKED`, `DELETED`).
-   `created_at` (TIMESTAMP).
-   `updated_at` (TIMESTAMP).

---

## 3. Спецификация JWT Токена

Модуль `auth` подписывает JWT-токен симметричным ключом (`HS256`). Claims токена:

```json
{
  "sub": "user@company.com",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "COMPANY_HEAD",
  "companyId": "comp-uuid-12345",
  "iat": 1787011200,
  "exp": 1787097600
}
```

---

## 4. API Контракты (Эндпоинты `auth`)

### 4.1. Вход в систему
-   **Метод**: `POST /api/v1/auth/login`
-   **Тело запроса**:
    ```json
    {
      "username": "user@company.com",
      "password": "secure_password"
    }
    ```
-   **Ответ (200 OK)**:
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "expiresIn": 86400,
      "role": "COMPANY_HEAD"
    }
    ```

### 4.2. Управление Пользователями (CRUD)
Доступно только авторизованным пользователям с ролями `SYSTEM_ADMIN` и `COMPANY_HEAD`.

1.  **Создать пользователя** (`POST /api/v1/users`)
    -   *Админ* может создавать любого пользователя.
    -   *Company Head* может создавать пользователей только со своей `companyId` и ролями ниже своей.
2.  **Список пользователей** (`GET /api/v1/users`)
    -   *Админ* видит всех.
    -   *Company Head* видит только пользователей своей компании.
3.  **Получить пользователя** (`GET /api/v1/users/{id}`)
4.  **Редактировать пользователя** (`PATCH /api/v1/users/{id}`)
5.  **Удалить пользователя** (`DELETE /api/v1/users/{id}`) — мягкое удаление.

---

## 5. Коллекция Postman
Для удобного ручного тестирования и интеграции добавлена Postman-коллекция:
- **[Auth.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/auth/Auth.postman_collection.json)**
  Включает скрипт автоматического сохранения JWT токена в переменную коллекции `authToken` после выполнения запроса `Login`.

