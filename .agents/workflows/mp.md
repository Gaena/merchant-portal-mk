---
description: Рабочий процесс (Workflow) и контекст проекта MP (Multi-Project: auth, directory, pbl) для AI-агента. Покрывает архитектуру, текущий статус, структуру БД, RBAC и инструкции по сборке/тестированию.
---

# MP (Multi-Project) — Руководство и Статус Проекта для AI-Агента

Данный документ представляет собой единый источник правды (Single Source of Truth) по текущему состоянию, архитектуре и правилам разработки проекта **MP**.

---

## 1. Обзор Проекта и Архитектурный Контекст

Проект **MP** — это микросервисная платформа для электронной коммерции и платежных сервисов (Pay-By-Link, справочники компаний/терминалов, аутентификация/авторизация).

- **Технологический стек**:
  - **Сборщик**: Gradle Multi-Module (Root `mp`, подпроекты `:common`, `:auth`, `:directory`, `:pbl`)
  - **Язык / Фреймворк**: Java 21, Spring Boot 3.1.0
  - **База данных**: PostgreSQL (общие таблицы в рамках одной схемы/БД), H2 (для интеграционных тестов), Liquibase
  - **Безопасность**: Единый модуль `:common` для JWT (`HS256`) проверки подписи, фильтрации и Stateless RBAC

---

## 2. Текущий Статус Модулей Проекта

Все 4 модуля (`:common`, `auth`, `directory`, `pbl`) полностью реализованы, оптимизированы и покрыты интеграционными тестами (`./gradlew test` -> **BUILD SUCCESSFUL**).

### 2.0. `common` — Общий Модуль Безопасности и Исключений
- **Функционал**:
  - Единая реализация генерации и валидации HMAC-SHA256 подписи JWT (`JwtProvider`).
  - Централизованный фильтр аутентификации `JwtAuthFilter` с поддержкой fallback static tokens.
  - Централизованная иерархия ошибок (`BusinessException`, `ResourceNotFoundException`, `InvalidStateException`, `ConflictException`, `UnauthorizedException`) и `GlobalExceptionHandler`.

### 2.1. `auth` — Сервис Аутентификации и Пользователей (Порт 8081)

- **Файл документации**: [auth.md](file:///Users/salayevim/IdeaProjects/mp/auth/auth.md)
- **Postman Коллекция**: [Auth.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/auth/Auth.postman_collection.json)
- **Функционал**:
  - Вход пользователей (`POST /api/v1/auth/login`) и генерация JWT с claims: `userId`, `role`, `companyId`.
  - Управление пользователями (CRUD `POST/GET/PATCH/DELETE /api/v1/users`).
  - Ограничение видимости и действий в зависимости от ролей (`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR`).
- **Статус**: Готов. Интеграционные тесты пройдены (`AuthIntegrationTest`).

### 2.2. `directory` — Сервис Справочников (Порт 8082)
- **Файл документации**: [directory.md](file:///Users/salayevim/IdeaProjects/mp/directory/directory.md)
- **Postman Коллекция**: [Directory.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/directory/Directory.postman_collection.json)
- **Функционал**:
  - Управление компаниями (`/api/v1/companies`).
  - Управление терминалами MilliKart (`/api/v1/terminals`).
  - Система логирования аудита (`/api/v1/audit-logs`, `AuditLog`).
- **Статус**: Готов. Интеграционные тесты пройдены (`DirectoryIntegrationTest`).

### 2.3. `pbl` — Сервис Платежных Ссылок Pay-By-Link (Порт 8080/8083)
- **Файл документации**: [pay-by-link.md](file:///Users/salayevim/IdeaProjects/mp/pbl/pay-by-link.md)
- **Postman Коллекция**: [Pay-By-Link.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/pbl/Pay-By-Link.postman_collection.json), [NON-PSP Ecom.postman_collection.json](file:///Users/salayevim/IdeaProjects/mp/pbl/NON-PSP%20Ecom.postman_collection.json)
- **Функционал**:
  - Создание, редактирование, просмотр, листинг ссылок на оплату (`payment_links`).
  - Открытие платежной страницы (`GET /api/v1/payment-links/{id}/open`), генерация сессии в эквайринге MilliKart (TXPG).
  - Обработка редиректов и генерация онлайн-чека на Thymeleaf (`redirect.html`).
  - Отслеживание транзакций (`transactions`), завершение двухстадийных платежей DMS (`/complete`), проведение возвратов (`/refund`).
  - Интеграционные клиенты: `TxpgAcquiringClient` (боевой MilliKart) и `StubAcquiringClient` (заглушка для локальной отладки/тестирования).
- **Статус**: Готов. Интеграционные тесты пройдены (`PaymentLinkIntegrationTest`).

---

## 3. Общая Схема Базы Данных и Взаимосвязи

Все микросервисы разделяют общую предметную область PostgreSQL:

| Таблица | Управляющий модуль | Читающие/использующие модули | Назначение |
|---|---|---|---|
| `users` | `auth` | `directory`, `pbl` | Пользователи системы, хэши паролей, роли, связь с компанией |
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
| `COMPANY_EMPLOYEE` | Создание и просмотр платежных ссылок, проведение DMS. **Возвраты запрещены (403 Forbidden)**. |
| `AUDITOR` | Просмотр (Read-Only) данных своей компании. **Любые записи/модификации запрещены (403 Forbidden)**. |

---

## 5. Команды Сборки и Тестирования для Агента

При совершении любых изменений АГЕНТ ОБЯЗАН запускать проверку.

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
