# Модуль Управления Справочниками (Directory Service)

## 1. Архитектура и Структура
-   **Стек**: Java Spring Boot (независимый подпроект в папке `directory`, порт `8082`).
-   **База данных**: Общая PostgreSQL база данных `postgres` (общие таблицы `companies`, `terminals`, `users`).
-   **Связь с другими модулями**: 
    -   `directory` предоставляет эндпоинты для управления компаниями и терминалами.
    -   При создании компании или терминала данные записываются в общие таблицы `companies` и `terminals`.
    -   Модуль `auth` использует таблицу `companies` для валидации связи пользователей с компаниями.
    -   Модуль `pbl` считывает данные терминалов из таблицы `terminals` для проведения оплат через MilliKart.

---

## 2. База данных (Таблицы `companies` и `terminals` в общей БД)

### 2.1. Таблица `companies`
-   `id` (VARCHAR) — Primary Key (например, буквенно-цифровой код или UUID).
-   `name` (VARCHAR) — Название юридического лица / компании.
-   `status` (VARCHAR) — Статус (`ACTIVE`, `SUSPENDED`, `DELETED`).
-   `created_at` (TIMESTAMP).

### 2.2. Таблица `terminals`
-   `id` (INTEGER) — Primary Key (идентификатор терминала в MilliKart).
-   `name` (VARCHAR) — Пользовательское имя (например, "Касса 1").
-   `login` (VARCHAR) — Логин для API MilliKart.
-   `password` (VARCHAR) — Пароль для API MilliKart.
-   `company_id` (VARCHAR) — Foreign Key на `companies.id`.

---

## 3. API Контракты (Эндпоинты `directory`)

Все эндпоинты защищены проверкой JWT токена (сигнатура валидируется ключом `HS256`).

### 3.1. Управление Компаниями (Companies CRUD)
-   `POST /api/v1/companies` — Создать компанию.  
    *Доступ*: Только `SYSTEM_ADMIN`.  
    *Запрос*: `{"id": "comp-01", "name": "MilliKart LLC"}`
-   `GET /api/v1/companies` — Получить список компаний.  
    *Доступ*: Только `SYSTEM_ADMIN`.
-   `GET /api/v1/companies/{id}` — Детали компании.  
    *Доступ*: `SYSTEM_ADMIN`, а также `COMPANY_HEAD`/`COMPANY_MANAGER` (только своей компании).
-   `PATCH /api/v1/companies/{id}` — Редактировать компанию.  
    *Доступ*: Только `SYSTEM_ADMIN`.
-   `DELETE /api/v1/companies/{id}` — Удалить/деактивировать компанию.  
    *Доступ*: Только `SYSTEM_ADMIN`.

### 3.2. Управление Терминалами (Terminals CRUD)
-   `POST /api/v1/terminals` — Создать терминал.  
    *Доступ*: `SYSTEM_ADMIN` (для любой компании), `COMPANY_HEAD`/`COMPANY_MANAGER` (только для своей компании).  
    *Запрос*: `{"id": 998877, "name": "Main Terminal", "login": "term_login", "password": "term_password", "companyId": "comp-01"}`
-   `GET /api/v1/terminals` — Список терминалов.  
    *Доступ*: `SYSTEM_ADMIN` (все), остальные роли (только терминалы своей компании).
-   `GET /api/v1/terminals/{id}` — Детали терминала.
-   `PATCH /api/v1/terminals/{id}` — Редактировать учетные данные терминала.  
    *Доступ*: `SYSTEM_ADMIN`, `COMPANY_HEAD`/`COMPANY_MANAGER` (своей компании).
-   `DELETE /api/v1/terminals/{id}` — Удалить терминал.
