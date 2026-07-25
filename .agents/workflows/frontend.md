---
description: Руководство по фронтенд-модулю (React + TypeScript + Vite) и логике связи с бэкенд-микросервисами MP для AI-Агента.
---

# MP Merchant Portal — Руководство по Фронтенду и Спецификации Интеграции

Данный документ представляет собой единую инструкцию для AI-Агента по устройству фронтенд-модуля `frontend` и его сквозной интеграции с микросервисами бэкенда (`:auth`, `:directory`, `:pbl`).

---

## 1. Обзор Фронтенд-Модуля

- **Директория**: `/Users/salayevim/IdeaProjects/mp/frontend`
- **Стек**: React 18, TypeScript, Vite 5, Tailwind CSS, Material-UI (MUI), Radix UI, Lucide Icons, Axios, React Router v6.
- **Порт локального dev-сервера**: `http://localhost:3000`

---

## 2. Карта Портов и Маршрутизация Бэкенд-Сервисов

Фронтенд отправляет HTTP-запросы на единый относительный путь `/api/v1/*`. Прокси в `vite.config.ts` маршрутизирует их на соответствующий микросервис:

| Относительный путь | Целевой порт бэкенда | Название сервиса | Назначение |
|---|---|---|---|
| `/api/v1/auth/*`, `/api/v1/users/*` | `http://localhost:8081` | **`:auth`** | Аутентификация, генерация JWT, управление пользователями |
| `/api/v1/companies/*`, `/api/v1/terminals/*`, `/api/v1/audit-logs/*` | `http://localhost:8082` | **`:directory`** | Справочники компаний, эквайринговых терминалов, аудит |
| `/api/v1/payment-links/*`, `/api/v1/transactions/*` | `http://localhost:8080` | **`:pbl`** | Создание/листинг платежных ссылок, проведение транзакций, DMS complete, refund, status check |

---

## 3. Логика Связи с Бэкендом (API Binding Pipeline)

### 3.1. Перехватчик, Авторизация и Консольное Логирование (`src/api/client.ts`)
Все HTTP-запросы выполняются через единый экземпляр `apiClient`:
- При каждом запросе перехватчик автоматически считывает `token` из `localStorage` и подставляет заголовок `Authorization: Bearer <jwt_token>`.
- Включено детализированное консольное логирование вызовов API с цветовой разметкой:
  - `[HTTP REQ]` — логирование отправляемых HTTP-запросов (метод, URL, тело/параметры запроса).
  - `[HTTP RESP]` — логирование успешных ответов бэкенда (метод, URL, HTTP-статус и тело ответа).
  - `[HTTP ERR]` — логирование сетевых ошибок и статусов ответа 4xx/5xx.
- Если бэкенд возвращает статус `401 Unauthorized`, токен удаляется из `localStorage`, и пользователь автоматически перенаправляется на `/login`.

### 3.2. Аутентификация (`POST /api/v1/auth/login`)
- **Эндпоинт**: `POST /api/v1/auth/login` (Сервис `:auth`, порт 8081).
- **Тело запроса (JSON)**:
  ```json
  {
    "username": "admin@millikart.az",
    "password": "admin123"
  }
  ```
- **Обработка в UI**: [AuthContext.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/context/AuthContext.tsx) сохраняет токен и роль, выставляет `isAuthenticated = true` и перенаправляет в портал.

### 3.3. Управление Пользователями (`/api/v1/users`)
- **Сервис**: `:auth` (Порт 8081).
- **Сценарии во фронтенде**: Вынесено в отдельный самостоятельный модуль [UsersPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/UsersPage.tsx) (Маршрут `/users`, боковая панель).
  - `GET /api/v1/users`: Загрузка списка пользователей с подгрузкой компаний и гибким поиском по логину (`username`), полному имени (`fullName`), ID компании (`companyId`) и названию компании (`companyName`).
  - `POST /api/v1/users`: Создание пользователя (`username`, `password`, `fullName`, `role`, `companyId`).
  - `DELETE /api/v1/users/{id}`: Удаление пользователя.

### 3.4. Управление Компаниями (`/api/v1/companies`)
- **Сервис**: `:directory` (Порт 8082).
- **Сценарии во фронтенде**: Вынесено в отдельную страницу [CompaniesPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/CompaniesPage.tsx) (Маршрут `/companies`, доступна **только для АДМИНА**).
  - `GET /api/v1/companies`: Загрузка списка компаний с живым поиском по наименованию (`name`) и идентификатору (`id`).
  - `POST /api/v1/companies`: Регистрация новой компании (`id`, `name`).
  - `PATCH /api/v1/companies/{id}`: Переключение статуса компании (`ACTIVE` / `INACTIVE`) в реальном времени.
  - `DELETE /api/v1/companies/{id}`: Удаление компании.

### 3.5. Управление Терминалами (`/api/v1/terminals`)
- **Сервис**: `:directory` (Порт 8082).
- **Сценарии во фронтенде**: Вынесено в отдельную страницу [TerminalsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/TerminalsPage.tsx) (Маршрут `/terminals`).
  - `GET /api/v1/terminals`: Загрузка списка терминалов с мгновенным поиском по номеру терминала (`id`), имени (`name`), логину (`login`) и привязанной компании (`companyName`/`companyId`).
  - `POST /api/v1/terminals`: Регистрация нового эквайрингового терминала (`id`, `name`, `login`, `password`, `companyId`). При открытии модального окна подгружается выпадающий список доступных компаний (`GET /api/v1/companies`).
  - `PATCH /api/v1/terminals/{id}`: Модальное окно редактирования терминала (`name`, `login`, `password`, `companyId`).
  - `DELETE /api/v1/terminals/{id}`: Удаление терминала.

### 3.6. Журнал Аудита (`GET /api/v1/audit-logs`)
- **Сервис**: `:directory` (Порт 8082).
- **Сценарий во фронтенде**: Вынесено в отдельный самостоятельный модуль [AuditLogsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/AuditLogsPage.tsx) (Маршрут `/audit-logs`, боковая панель). Отображение действий пользователей и системных событий с фильтрацией по Entity Type и полем поиска.
- **Разграничение прав доступа (RBAC)**: На стороне бэкенда (`:directory`, класс `AuditLogService`) извлекаются роли и `companyId` из JWT-токена пользователя:
  - `SYSTEM_ADMIN` и `AUDITOR`: видят **все** аудит-логи всей системы.
  - `COMPANY_HEAD` и `COMPANY_MANAGER`: видят логи **исключительно своей компании** (`auditLogRepository.findAllByCompanyId(actorCompanyId)`).

### 3.7. Платежные Ссылки (`/api/v1/payment-links`)
- **Сервис**: `:pbl` (Порт 8080).
- **Сценарии во фронтенде**:
  - `POST /api/v1/payment-links`: Создание ссылки с валидацией наличия терминала.
  - `GET /api/v1/payment-links`: Листинг ссылок (содержит корректные поля `paymentType`, `usageType`, `description`, `customerName`, `maxPayments`, `expiresAt`).
  - `GET /api/v1/payment-links/{id}`: Просмотр деталей в [PayByLinkDetailPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/PayByLinkDetailPage.tsx).
  - `GET /api/v1/payment-links/{id}/open`: Переход покупателя к оплате. **Внимание**: Согласовано с Millikart — старый HPP URL не переиспользуется. При каждом открытии нескомплеченная сессия PENDING закрывается, генерируется новая сессия Millikart с уникальным `merchantRid` и фиксируется IP-адрес и User-Agent покупателя.
  - `PATCH /api/v1/payment-links/{id}`: Отмена активной ссылки (`status: "CANCELLED"`).

### 3.8. Проведение и Управление Транзакциями (`/api/v1/transactions`)
- **Сервис**: `:pbl` (Порт 8080).
- **Сценарии во фронтенде**:
  - `GET /api/v1/transactions?page=X&size=Y`: Загрузка списка транзакций с поддержкой серверной пагинации и извлечения обогащенного `TransactionResponse` DTO (`paymentLinkId`, `paymentType`, `terminalId`, `merchantRid`, `providerOrderId`, `cardNumberMasked`, `rrn`, `approvalCode`, `clientIp`, `userAgent`). Поле `providerOrderId` (с обработкой фолбэков `providerOrderId` / `provider_order_id`) передается на все страницы транзакций, отображается в карточке деталей, таблицах списка, передается во внутреннее состояние навигации `txObj`, учитывается при поиске и выгружается в экспорт Excel. При загрузке выполняются параллельные запросы к `/api/v1/terminals`, что позволяет сопоставлять `terminalId` с понятным человекочитаемым **названием терминала (`terminalName`)**.
  - `POST /api/v1/transactions/{transactionId}/complete`: Завершение (Capture) двухстадийного DMS-платежа в [PayByLinkDetailPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/PayByLinkDetailPage.tsx) и [TransactionDetailPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/TransactionDetailPage.tsx) (поддерживает транзакции со статусом `AUTHORIZED`, `PENDING` и `SUCCESS`). При получении `errorCode` от эквайринга MilliKart (например, `InvalidOrderState`, `Multiclearing is prohibited`) переключение статуса на SUCCESS блокируется и сообщение ответа передается клиенту.
  - `POST /api/v1/transactions/{transactionId}/refund`: Запрос полного/частичного возврата средств (`amount`, `reason`).
  - `GET /api/v1/transactions/{transactionId}/status`: Кнопка «Check Gateway Status» выполняет принудительный опрос эквайрингового шлюза по первичному ключу UUID транзакции (`transaction.id`).
  - **Статусы транзакций**: Статусы транзакций полностью синхронизированы во фронтенде и бэкенде в верхнем регистре (`APPROVED`, `PENDING`, `DECLINED`, `REFUNDED`, `PARTIALLY_REFUNDED`, `CANCELED`, `FAILED`).
  - **Роли пользователей (RBAC)**: Синхронизированы с ролевой моделью бэкенда (`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR`).

### 3.9. Аналитический Дашборд ([HomePage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/HomePage.tsx))
- **Сервисы**: `:pbl` (`/api/v1/transactions`, `/api/v1/payment-links`).
- **Сценарий**: Главная страница загружает реальные транзакции и платежные ссылки из базы данных и динамически вычисляет выручку (Total Revenue), количество операций, средний чек, процент успеха (Success Rate), расщепление статусов (включая `APPROVED`, `PENDING`, `REFUNDED`, `PARTIALLY_REFUNDED`, `CANCELED`, `FAILED`), 7-дневный тренд выручки, рейтинг терминалов, а также выводит интерактивную таблицу последних системных транзакций с кликабельным переходом к деталям.

---

### 3.10. Производительность, Разделение Бандла и Прямая Загрузка (Optimization Architecture)
- **Строгая Типизация DTO (`src/app/types/dto.ts`)**: Все страницы справочников ([CompaniesPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/CompaniesPage.tsx), [TerminalsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/TerminalsPage.tsx), [UsersPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/UsersPage.tsx), [AuditLogsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/AuditLogsPage.tsx)) используют строгие TypeScript интерфейсы `CompanyDto`, `TerminalDto`, `UserDto`, `AuditLogDto`.
- **Прямая Загрузка по URL ([TransactionDetailPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/TransactionDetailPage.tsx))**: При прямом открытии или перезагрузке страницы по URL `/transactions/:id` компонент автоматически выполняет fallback-запрос `GET /api/v1/transactions/:id` на бэкенд и плавно отображает индикатор загрузки `CircularProgress`.
- **Отключение Неиспользуемых Уведомлений**: Элементы UI уведомления (колокольчик и Popover в [Header.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/components/Header.tsx), а также вкладка Notifications в [SettingsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/SettingsPage.tsx)) закомментированы, так как сервисы уведомления временно не используются.

---

## 4. Структурная Карта Фронтенда

```text
frontend/
├── src/
│   ├── app/
│   │   ├── api/
│   │   │   └── client.ts         # Axios клиент с подстановкой JWT
│   │   ├── context/
│   │   │   └── AuthContext.tsx   # React Context управления авторизацией
│   │   ├── components/
│   │   │   ├── Sidebar.tsx       # Боковое меню (Home, PayByLink, Transactions, Terminals, Companies, Users, Audit Logs, Settings)
│   │   │   └── Header.tsx        # Шапка с профилем пользователя и кнопкой выхода
│   │   ├── pages/
│   │   │   ├── LoginPage.tsx     # Форма входа (`username` + `password`)
│   │   │   ├── PayByLinkPage.tsx # Таблица и модалка создания ссылок (интегрирована с API)
│   │   │   ├── PayByLinkDetailPage.tsx # Просмотр деталей ссылки по UUID + DMS Complete
│   │   │   ├── TransactionListPage.tsx # Листинг транзакций
│   │   │   ├── TransactionDetailPage.tsx # Детали транзакции + Direct URL Fetch + Refund + DMS Complete
│   │   │   ├── CompaniesPage.tsx # Управление компаниями (АДМИН), включает Toggle ACTIVE/INACTIVE
│   │   │   ├── TerminalsPage.tsx # Управление терминалами с выбором компании из выпадающего списка
│   │   │   ├── UsersPage.tsx     # Управление пользователями (/users, CRUD, RBAC)
│   │   │   ├── AuditLogsPage.tsx # Журнал аудита (/audit-logs, фильтрация по Entity/Actor)
│   │   │   ├── SettingsPage.tsx  # Настройки аккаунта, Безопасность, Уведомления, API Keys, Язык (Settings -> Display -> Language)
│   │   │   └── HomePage.tsx      # Главный дашборд
│   │   ├── types/
│   │   │   ├── dto.ts            # DTO интерфейсы (CompanyDto, TerminalDto, UserDto, AuditLogDto)
│   │   │   └── transaction.ts    # Типы транзакций и фильтров
│   │   ├── routes.tsx            # Маршрутизатор с React.lazy, Suspense и ProtectedRoute
│   │   └── App.tsx               # Корневой компонент (окантован в LanguageProvider для поддержки EN, AZ, RU)
│   └── index.css                 # Импорт Tailwind CSS
├── vite.config.ts                # Прокси для 8080/8081/8082 и алиас @
└── package.json
```

---

## 5. Инструкции по Сборке и Проверке для Агента

При любых изменениях во фронтенд-коде АГЕНТ ОБЯЗАН проводить проверку компиляции:

```bash
cd /Users/salayevim/IdeaProjects/mp/frontend
npm run build
```
*(Результат должен быть **SUCCESS** без ошибок типизации TypeScript)*.

---

## 6. Мультиязычность и Интернационализация (i18n)

Фронтенд полностью поддерживает 3 языка:
- **English (`en`)** — язык по умолчанию
- **Azərbaycan dili (`az`)**
- **Русский язык (`ru`)**

### Архитектура:
1. **`src/app/i18n/translations.ts`**: Содержит полные словари переводов для всех страниц (`common`, `nav`, `header`, `home`, `settings`, `payByLink`, `payByLinkDetail`, `transactions`, `terminals`, `companies`, `users`, `auditLogs`).
2. **`src/app/context/LanguageContext.tsx`**: Предоставляет хук `useLanguage()`, возвращающий `{ language, setLanguage, tObj }`. Состояние сохраняется в `localStorage` под ключом `mp_app_language`.
3. **Переключение языка**: Выполняется через **Settings → Display → Appearance → Language** или быструю кнопку с флагами в верхней панели `Header.tsx`.


