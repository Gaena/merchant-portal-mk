---
description: Руководство по фронтенд-модулю (React + TypeScript + Vite) и логике связи с бэкенд-микросервисами MP для AI-Агента.
---

# MP Merchant Portal — Руководство по Фронтенду и Спецификации Интеграции

> ⚠ **Сверено с кодом 14.08.2026 (ревизия `3f890de`) плюс P1-14 от 18.08.2026 (TypeScript, гейт
> `tsc -b`, `VITE_API_BASE_URL`, удаление мёртвого кода), P2-12 от 20.08.2026 (словарь статусов
> транзакции сведён с бэкендом) и P2-13 от 21.08.2026 (то же для статусов ссылки; отмена ссылки
> больше не «удаётся» при отказе бэкенда). Источник правды — корневой [`AGENTS.md`](../../AGENTS.md)**
> (там же: список маршрутов, что осталось спорного после уборки и раздел «грабли»). Известные баги: [`code_review.md`](../../code_review.md).

Данный документ представляет собой единую инструкцию для AI-Агента по устройству фронтенд-модуля `frontend` и его сквозной интеграции с микросервисами бэкенда (`:auth`, `:directory`, `:pbl`).

---

## 1. Обзор Фронтенд-Модуля

- **Директория**: `/Users/salayevim/IdeaProjects/mp/frontend`
- **Стек**: React 18.3.1, **Vite 6.3.5**, Material-UI 7.3.5 (+ `@mui/lab` 7.0.1-beta.19), Axios `^1.7.9` (в lock 1.18.1), **`react-router` 7.13.0** (пакет `react-router`, не `react-router-dom`), recharts 2.15.2, xlsx 0.18.5.
- **TypeScript 7.0.2** (с 18.08.2026, P1-14): `npm run typecheck` = `tsc -b`, и он же стоит первым шагом в `npm run build`. `strict` выключен **явно** в `tsconfig.app.json` (TS ≥ 7 включает его по умолчанию) — включение отдельной задачей.
- **oxlint 1.16.0**: `npm run lint`, конфиг `.oxlintrc.json`.
- **Tailwind CSS 4.1.12** подключён (`vite.config.ts`, `src/styles/tailwind.css`), но живых классов два (`StatsOverview.tsx`, `figma/ImageWithFallback.tsx`). shadcn/Radix и `src/app/components/ui/**` **удалены** 18.08.2026 вместе с 48 зависимостями генератора. Новый код писать на MUI.
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

### 3.1. Перехватчик и Авторизация (`src/app/api/client.ts`)

> Единственный клиент — `src/app/api/client.ts`. Второй, старый `src/api/client.ts`, который логировал
> тела всех запросов и ответов в консоль (включая пароль при логине), **удалён 18.08.2026 (P1-14)** —
> не заводить заново и не добавлять консольное логирование запросов в живой клиент.
Все HTTP-запросы выполняются через единый экземпляр `apiClient`:
- При каждом запросе перехватчик автоматически считывает `token` из `localStorage` и подставляет заголовок `Authorization: Bearer <jwt_token>`.
- Консольного логирования запросов **нет**.
- `baseURL` = `import.meta.env.VITE_API_BASE_URL ?? ''`. Пусто по умолчанию → запросы идут относительно текущего origin: в dev их разводит прокси Vite по трём портам, в проде — nginx на том же домене с той же разводкой. Если фронтенд обслуживается отдельно от API, задайте `VITE_API_BASE_URL` (шаблон — `frontend/.env.example`; реальный `.env` игнорируется git'ом). Значение зашивается в бандл на этапе `vite build`, в рантайме не читается.
- ⚠ Любой ответ 401 из любого эндпоинта разлогинивает пользователя целиком.
- Если бэкенд возвращает статус `401 Unauthorized`, токен удаляется из `localStorage`, и пользователь автоматически перенаправляется на `/login`.

### 3.2. Аутентификация (`POST /api/v1/auth/login`)
- **Эндпоинт**: `POST /api/v1/auth/login` (Сервис `:auth`, порт 8081).
- **Тело запроса (JSON)**:
  ```json
  {
    "username": "admin@millikart.az",
    "password": "<пароль администратора>"
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
  - `PATCH /api/v1/terminals/{id}` с `{"status": "ACTIVE" | "BLOCKED"}`: блокировка и разблокировка
    терминала. Удаления терминалов нет (P2-8) — `DELETE` отвечает 405.

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
  - `PATCH /api/v1/payment-links/{id}`: Отмена ссылки — фронтенд шлёт `{ "status": "CANCELED" }` (одна `l`, как в `PaymentLinkStatus`). **С 21.08.2026 (P2-13, Р-34) состояние после отмены берётся с сервера, а не дорисовывается на клиенте:** при успехе показывается сообщение об отмене, при отказе — текст `ErrorResponse.message` от бэкенда, и в обоих случаях список/карточка перечитываются (`GET`). До этого ветка `catch` повторяла ветку успеха, поэтому отказ бэкенда (например, 400 «`payment link status cannot be changed from COMPLETED to CANCELED`» после P2-9) рисовал ссылку отменённой, пока по ней продолжали платить.
  - **Статусы и типы ссылки** (сведены с бэкендом 21.08.2026, P2-13, Р-33): `LINK_STATUSES` в `utils/payByLinkData.ts` — ровно четыре значения `pbl/.../domain/PaymentLinkStatus.java` (`ACTIVE`, `EXPIRED`, `COMPLETED`, `CANCELED`); `LINK_USAGE_TYPES` — `SINGLE`/`MULTIPLE` (`UsageType.java`), `PAYMENT_TYPES` — `SMS`/`DMS` (`PaymentType.java`). Ответ разбирается `parseLinkStatus`/`parseLinkUsageType`/`parsePaymentType` на границе: незнакомое значение → `null` плюс предупреждение в консоль, ссылка показывается серым и как есть (`PaymentLink.statusRaw`), **без подстановки по умолчанию** и **без предложения действий** (отмены, шаринга, копирования). Статуса `paid` у бэкенда нет — оплаченная одноразовая ссылка приходит как `COMPLETED`; написание одно — `CANCELED`. Ручные `toLowerCase()`/`toUpperCase()` при чтении и отправке убраны. Подписи — `payByLink.statuses` в `i18n/translations.ts` (`Record<LinkStatus, string>`, три языка).
  - **Мок-генератор `generateLinks` удалён** (P2-13) вместе с `merchantTerminals`: обе страницы работают только на API. Поля `PaymentLink`, которые заполнял генератор, а API не отдаёт (`paidAt`, `redirectUrl`, `note`, `dmsStatus`, `finalizedAt`, `cardNetwork`, `cardLast4`, `transactionId`, `payerIp`, `sentVia`), оставлены с пометкой «всегда `undefined` до появления поля в API» — разметка их читает, но на экран они не попадают.

### 3.8. Проведение и Управление Транзакциями (`/api/v1/transactions`)
- **Сервис**: `:pbl` (Порт 8080).
- **Сценарии во фронтенде**:
  - `GET /api/v1/transactions?page=X&size=Y`: ⚠ фактически вызывается с жёстко зашитыми `page=0, size=100` (`src/app/App.tsx:116`), а фильтрация, поиск, пагинация и экспорт в Excel выполняются на клиенте поверх этих 100 записей. Серверная пагинация поддержана бэкендом, но фронтендом не используется. Загрузка списка транзакций с извлечением и извлечения обогащенного `TransactionResponse` DTO (`paymentLinkId`, `paymentType`, `terminalId`, `merchantRid`, `providerOrderId`, `cardNumberMasked`, `rrn`, `approvalCode`, `clientIp`, `userAgent`). Поле `providerOrderId` (с обработкой фолбэков `providerOrderId` / `provider_order_id`) передается на все страницы транзакций, отображается в карточке деталей, таблицах списка, передается во внутреннее состояние навигации `txObj`, учитывается при поиске и выгружается в экспорт Excel. При загрузке выполняются параллельные запросы к `/api/v1/terminals`, что позволяет сопоставлять `terminalId` с понятным человекочитаемым **названием терминала (`terminalName`)**.
    **С 15.08.2026 ответ ограничен компанией пользователя** (`SYSTEM_ADMIN` и `AUDITOR` по-прежнему видят все компании) — до этого эндпоинт отдавал транзакции всех компаний без проверки прав. Для фронтенда это значит: у компанейских ролей список стал короче, а роль вне пяти известных получает `403`, а не данные. Клиентская фильтрация поверх первых 100 записей от этого не изменилась, но и «100 записей на всю систему» больше не бывает.
  - `POST /api/v1/transactions/{transactionId}/complete`: Завершение (Capture) двухстадийного DMS-платежа в [PayByLinkDetailPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/PayByLinkDetailPage.tsx) и [TransactionDetailPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/TransactionDetailPage.tsx) (поддерживает транзакции со статусом `AUTHORIZED`, `PENDING` и `SUCCESS`). При получении `errorCode` от эквайринга MilliKart (например, `InvalidOrderState`, `Multiclearing is prohibited`) переключение статуса на SUCCESS блокируется и сообщение ответа передается клиенту.
  - `POST /api/v1/transactions/{transactionId}/refund`: Запрос полного/частичного возврата средств (`amount`, `reason`).
  - `GET /api/v1/transactions/{transactionId}/status`: Кнопка «Check Gateway Status» выполняет принудительный опрос эквайрингового шлюза по первичному ключу UUID транзакции (`transaction.id`). **С 15.08.2026 (P0-2) эндпоинт требует JWT** — токен подставляет интерсептор `apiClient`, правок на фронтенде не потребовалось. Чужая компания получит `403`, роль вне пяти известных — тоже `403`.
  - **Статусы транзакций** (сведены с бэкендом 20.08.2026, P2-12): `TransactionStatus` в `types/transaction.ts` — ровно шесть значений `pbl/.../domain/TransactionStatus.java` (`PENDING`, `AUTHORIZED`, `SUCCESS`, `FAILED`, `PARTIALLY_REFUNDED`, `REFUNDED`); `PaymentMethod` — `SMS`/`DMS`. Ответ разбирается `parseTransactionStatus`/`parsePaymentMethod` на границе (`App.tsx`, `TransactionDetailPage`): незнакомое значение → `null` плюс предупреждение в консоль, показывается серым как есть (`Transaction.statusRaw`), **без подстановки по умолчанию**. Статусов `APPROVED`, `DECLINED`, `CANCELED`, `3d-failed` в бэкенде нет и в типе быть не должно — сравнение с ними теперь не проходит `tsc -b`. Подписи — `transactions.statuses` в `i18n/translations.ts` (`Record<TransactionStatus, string>`, три языка).
  - **Роли пользователей (RBAC)**: имена ролей совпадают с бэкендом (`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR`), но ⚠ `AuthContext.tsx:46` подставляет `role || 'SYSTEM_ADMIN'` (fail-open), а `routes.tsx:26-32` проверяет только факт логина — ролевых guard'ов на маршрутах нет. Фактическая матрица прав — в корневом `AGENTS.md`, §6.

### 3.9. Аналитический Дашборд ([HomePage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/HomePage.tsx))
- **Сервисы**: `:pbl` (`/api/v1/transactions`, `/api/v1/payment-links`).
- **Сценарий**: Главная страница загружает реальные транзакции и платежные ссылки из базы данных и динамически вычисляет выручку (Total Revenue), количество операций, средний чек, процент успеха (Success Rate), расщепление статусов (`SUCCESS`, `PENDING` + `AUTHORIZED`, `REFUNDED` + `PARTIALLY_REFUNDED`, `FAILED` — доли «Canceled» нет, такого статуса у транзакции не существует), 7-дневный тренд выручки, рейтинг терминалов, а также выводит интерактивную таблицу последних системных транзакций с кликабельным переходом к деталям.

---

### 3.10. Производительность, Разделение Бандла и Прямая Загрузка (Optimization Architecture)
- **Строгая Типизация DTO (`src/app/types/dto.ts`)**: Все страницы справочников ([CompaniesPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/CompaniesPage.tsx), [TerminalsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/TerminalsPage.tsx), [UsersPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/UsersPage.tsx), [AuditLogsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/AuditLogsPage.tsx)) используют строгие TypeScript интерфейсы `CompanyDto`, `TerminalDto`, `UserDto`, `AuditLogDto`.
- **Прямая Загрузка по URL ([TransactionDetailPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/TransactionDetailPage.tsx))**: При прямом открытии или перезагрузке страницы по URL `/transactions/:id` компонент выполняет fallback-запрос `GET /api/v1/transactions/:id` — ⚠ **такого маппинга в `TransactionController` нет** (есть только `GET /`, `GET /{identifier}/status`, `POST /{id}/complete`, `POST /{id}/refund`), запрос вернёт 404 и плавно отображает индикатор загрузки `CircularProgress`.
- **Отключение Неиспользуемых Уведомлений**: Элементы UI уведомления (колокольчик и Popover в [Header.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/components/Header.tsx), а также вкладка Notifications в [SettingsPage.tsx](file:///Users/salayevim/IdeaProjects/mp/frontend/src/app/pages/SettingsPage.tsx)) закомментированы, так как сервисы уведомления временно не используются.

---

## 4. Структурная Карта Фронтенда

```text
frontend/
├── src/
│   ├── app/
│   │   ├── api/
│   │   │   └── client.ts         # единственный Axios-клиент: baseURL из VITE_API_BASE_URL, JWT из localStorage, 401 → /login
│   │   ├── context/
│   │   │   ├── AuthContext.tsx   # авторизация (token/user в localStorage)
│   │   │   └── LanguageContext.tsx # useLanguage(): { language, setLanguage, t, tObj }
│   │   ├── i18n/
│   │   │   └── translations.ts   # словари en / az / ru
│   │   ├── layouts/
│   │   │   └── MainLayout.tsx    # каркас: Header + Sidebar + <Outlet/>
│   │   ├── components/
│   │   │   ├── Header.tsx        # шапка: профиль, язык, выход
│   │   │   ├── Sidebar.tsx       # боковое меню
│   │   │   ├── FilterPanel.tsx   # фильтры списка транзакций (варианты статуса — из TRANSACTION_STATUSES)
│   │   │   ├── StatsOverview.tsx # карточки сумм над списком транзакций
│   │   │   ├── TransactionTable.tsx # таблица транзакций (используется двумя списками)
│   │   │   └── figma/ImageWithFallback.tsx # ниоткуда не импортируется, оставлен (см. AGENTS.md §9)
│   │   ├── pages/
│   │   │   ├── LoginPage.tsx     # /login
│   │   │   ├── HomePage.tsx      # / — дашборд
│   │   │   ├── TransactionListPage.tsx          # /transactions
│   │   │   ├── EcommerceTransactionListPage.tsx # /transactions/ecommerce
│   │   │   ├── TransactionDetailPage.tsx        # /transactions/:id (+ refund, DMS complete, status check)
│   │   │   ├── PayByLinkPage.tsx       # /pay-by-link — список и модалка создания (реальный API)
│   │   │   ├── PayByLinkDetailPage.tsx # /pay-by-link/:id (+ cancel, DMS complete)
│   │   │   ├── CompaniesPage.tsx # /companies (только SYSTEM_ADMIN)
│   │   │   ├── TerminalsPage.tsx # /terminals
│   │   │   ├── UsersPage.tsx     # /users
│   │   │   ├── AuditLogsPage.tsx # /audit-logs
│   │   │   └── SettingsPage.tsx  # /settings: Account, Security (реальный API); Payment / API & Webhooks / Display — моки; вкладка Notifications закомментирована
│   │   ├── types/
│   │   │   ├── dto.ts            # CompanyDto, TerminalDto, UserDto, AuditLogDto
│   │   │   └── transaction.ts    # Transaction, TransactionFilters, TRANSACTION_STATUSES / PAYMENT_METHODS + parse* (зеркало enum'ов pbl)
│   │   ├── utils/
│   │   │   ├── exportExcel.ts    # выгрузка в xlsx
│   │   │   ├── mockData.ts       # вопреки имени — живые форматтеры (+ мок terminalRids для фильтра)
│   │   │   ├── payByLinkData.ts  # типы PaymentLink/LinkStatus (⚠ LinkStatus с бэкендом не сведён — problems.md §10), форматтеры; мок-генератор generateLinks импортируется, но не вызывается
│   │   │   └── statusColors.ts   # цвета статусов
│   │   ├── routes.tsx            # createRouter(layoutProps: any) — React.lazy, Suspense, ProtectedRoute
│   │   └── App.tsx               # корневой компонент, загрузка транзакций, провайдеры (Language, Auth, Theme)
│   ├── main.tsx                  # точка входа: createRoot → app/App.tsx, импортирует styles/index.css
│   ├── styles/                   # index.css → fonts.css + tailwind.css + theme.css
│   ├── assets/                   # hero.png, react.svg, vite.svg — использовались только удалённым src/App.tsx
│   └── index.css                 # ⚠ никем не импортируется (дубль @import "tailwindcss") — кандидат на удаление
├── .env.example                  # VITE_API_BASE_URL (шаблон; реальный .env в .gitignore)
├── .oxlintrc.json                # конфиг oxlint (npm run lint)
├── default_shadcn_theme.css      # тема генератора, никем не читается — кандидат на удаление
├── tsconfig.json                 # references → tsconfig.app.json (src) + tsconfig.node.json (vite.config.ts)
├── vite.config.ts                # dev-прокси для 8080/8081/8082 и алиас @
└── package.json                  # merchant-portal-frontend
```

Мёртвые `src/App.tsx`, `src/App.css`, `src/api/client.ts`, `src/types/index.ts`, `src/app/components/ui/**`,
`ReportsPage`, `NotificationsPage`, `POSTransactionListPage`, `POSFilterPanel`, `POSTransactionTable` удалены 18.08.2026.

---

## 5. Инструкции по Сборке и Проверке для Агента

При любых изменениях во фронтенд-коде АГЕНТ ОБЯЗАН прогнать проверку типов и сборку:

```bash
cd frontend
npm ci                 # или npm install
npm run typecheck      # tsc -b — src/ (tsconfig.app.json) и vite.config.ts (tsconfig.node.json)
npm run lint           # oxlint; сейчас 0 ошибок и ~78 предупреждений (no-unused-vars, exhaustive-deps)
npm run build          # tsc -b && vite build → dist/
```

`npm run build` **падает на ошибках типов** — типы проверяются до `vite build`, «зелёная»
сборка теперь означает «tsc прошёл». Правила:

- чинить ошибки по существу; `as any`, `@ts-ignore`, `!` для подавления — только с комментарием, почему иначе никак;
- `strict` выключен явно в `tsconfig.app.json` — под TS 7 он включён по умолчанию, и на 18.08.2026
  его включение даёт всего 8 ошибок (`TS18048` в компараторах сортировки `TransactionListPage.tsx:99-100`
  и `EcommerceTransactionListPage.tsx:103-104`). Это отдельная задача; новый код писать так, чтобы он проходил и под `strict`;
- `routes.tsx:42` — `createRouter(layoutProps: any)`: пропсы страниц из `App.tsx` уходят нетипизированными.
  Не закрыто в P1-14 (тянет каскад правок), кандидат на отдельную задачу.

Тестов на фронтенде нет ни одного — после `typecheck` обязательна ручная проверка в браузере (`npm run dev`).

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


