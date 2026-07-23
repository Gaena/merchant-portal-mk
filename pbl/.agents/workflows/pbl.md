---
description: Инструкции и логическая структура для реализации и исправления сервиса Pay-By-Link.
---

# PayByLink Service Implementation Workflow

Этот рабочий процесс (workflow) описывает согласованную логику работы платежных ссылок, транзакций, динамических терминалов и разграничения доступа для последующей генерации кода.

---

## 1. Логика аутентификации и разграничения доступа

### 1.1. Декодирование Bearer токена (JWT)
- Каждый запрос к мерчант-эндпоинтам (`/api/v1/payment-links/**`, `/api/v1/transactions/**`) должен содержать заголовок `Authorization: Bearer <token>`.
- Валидация подписи токена **не требуется**.
- Токен представляет собой стандартный JWT. Сервис должен декодировать его полезную нагрузку (middle payload block) из Base64 и прочитать параметры:
  - `userId` (или `sub`) — уникальный идентификатор пользователя.
  - `role` — роль пользователя (`SYSTEM_ADMIN`, `COMPANY_HEAD`, `COMPANY_MANAGER`, `COMPANY_EMPLOYEE`, `AUDITOR`).
  - `companyId` — идентификатор компании, к которой привязан пользователь (для `SYSTEM_ADMIN` может отсутствовать).
- Извлеченные параметры пробрасываются в контекст запроса в виде атрибутов (`userId`, `userRole`, `companyId`).

### 1.2. Публичные эндпоинты (Bypass Authentication)
Фильтр безопасности полностью пропускает запросы от покупателей без проверки Bearer токена:
- `GET /api/v1/payment-links/{id}/open` (Переход по ссылке).
- `/api/v1/payment-links/redirect` (Redirect Page статуса оплаты на фронтенде).
- `GET /api/v1/transactions/{providerOrderId}/status` (Опрос статуса транзакции фронтендом).
- Любые статические ресурсы.

### 1.3. Контроль доступа по компаниям и ролям (RBAC)
- Таблица `user_terminals` **удаляется**. Связи «пользователь — терминал» больше не существует.
- Каждый терминал в таблице `terminals` теперь имеет поле `company_id` (привязка терминала к компании).
- Учетные данные провайдера MilliKart - TXPG извлекаются динамически по `terminal_id` из таблицы `terminals` (поля `id` (Integer), `name`, `login`, `password`, `company_id`).

#### Матрица прав доступа:
| Роль | Просмотр (GET/LIST) | Создание/Изменение ссылок (POST/PATCH) | Списание DMS (`/complete`) | Возврат (`/refund`) |
| :--- | :---: | :---: | :---: | :---: |
| **SYSTEM_ADMIN** | ✅ Разрешено | ✅ Разрешено | ✅ Разрешено | ✅ Разрешено |
| **COMPANY_HEAD** | ✅ Разрешено | ✅ Разрешено | ✅ Разрешено | ✅ Разрешено |
| **COMPANY_MANAGER**| ✅ Разрешено | ✅ Разрешено | ✅ Разрешено | ✅ Разрешено |
| **COMPANY_EMPLOYEE**|✅ Разрешено | ✅ Разрешено | ✅ Разрешено | ❌ Запрещено |
| **AUDITOR** | ✅ Разрешено | ❌ Запрещено | ❌ Запрещено | ❌ Запрещено |

#### Правила валидации:
1. **Проверка принадлежности к компании (Company Match)**:
   - Если роль пользователя **НЕ** `SYSTEM_ADMIN`, то для любой операции с терминалом проверяется совпадение компании пользователя и компании терминала: `user.companyId == terminal.companyId`. Если они не совпадают — возвращается `403 Forbidden`.
   - Для `SYSTEM_ADMIN` проверка Company Match отключается. Он имеет доступ ко всем терминалам.
2. **Создание ссылки (`POST /api/v1/payment-links`)**:
   - Роли: разрешено всем, кроме `AUDITOR`.
   - Валидация: проверяется существование терминала в таблице `terminals` (иначе `400 Bad Request`). Выполняется Company Match.
3. **Получение ссылки по ID (`GET /api/v1/payment-links/{id}`)**:
   - Роли: разрешено всем.
   - Валидация: выполняется Company Match.
4. **Обновление ссылки (`PATCH /api/v1/payment-links/{id}`)**:
   - Роли: разрешено всем, кроме `AUDITOR`.
   - Валидация: выполняется Company Match.
5. **Получение списка ссылок (`GET /api/v1/payment-links`)**:
   - Роли: разрешено всем.
   - Если передан фильтр по терминалу (`?terminal=XXX`), проверяется доступность этого терминала (через Company Match).
   - Если фильтр не передан, то для `SYSTEM_ADMIN` возвращаются все ссылки. Для остальных ролей выборка автоматически ограничивается терминалами компании пользователя:
     `SELECT pl FROM PaymentLink pl WHERE pl.terminalId IN (SELECT t.id FROM Terminal t WHERE t.companyId = :companyId)`
6. **DMS Clearing (`/complete`)**:
   - Роли: разрешено всем, кроме `AUDITOR`.
   - Валидация: выполняется Company Match.
7. **Возвраты (`/refund`)**:
   - Роли: разрешено только `SYSTEM_ADMIN`, `COMPANY_HEAD` и `COMPANY_MANAGER`. Для `COMPANY_EMPLOYEE` и `AUDITOR` возвращается `403 Forbidden`.
   - Валидация: выполняется Company Match.

---

## 2. Логика работы оплаты по ссылке

### 2.1. Жизненный цикл платежной ссылки (PaymentLink)
- **Инициализация**: Создается со статусом `ACTIVE`.
- **Счетчик оплат (`currentPaymentsCount`)**: Вычисляется динамически на основе таблицы транзакций для исключения блокировок при конкурентных запросах:
  `SELECT COUNT(t) FROM Transaction t WHERE t.link.id = :linkId AND t.status = 'SUCCESS'`
- **Срок действия (`expiresAt`)**: Проверяется в момент перехода `/open`. Если транзакция переведена в `PENDING` до `expiresAt`, оплата принимается, даже если редирект от эквайера произошел после наступления `expiresAt`.
- **Реактивация ссылок**: 
  Мерчант может перевести ссылку из статусов `COMPLETED`/`EXPIRED` обратно в `ACTIVE` через `PATCH`, изменив срок действия или увеличив лимит оплат.

### 2.2. Одноразовая ссылка (`UsageType.SINGLE`)
- **Переход по ссылке (`/open`)**:
  - Валидация: статус `ACTIVE`, время жизни не истекло, оплат еще нет.
  - Контроль повторных оплат:
    - Система проверяет наличие активной транзакции в статусе `PENDING` или `AUTHORIZED` для этой ссылки.
    - **Таймаут сессии оплаты**: Если активная транзакция находится в статусе `PENDING` более 10 минут (время жизни заказа на стороне провайдера), система автоматически переводит статус этой транзакции в `FAILED`. Это позволяет клиенту начать новую попытку оплаты.
    - Если активная транзакция существует и она свежая (создана менее 10 минут назад), создание новой транзакции запрещено. Клиент перенаправляется на ранее сгенерированный HPP URL.
  - Если активных попыток оплаты нет, генерируется UUID, запрашиваются учетные данные терминала, регистрируется заказ у эквайера, создается транзакция `PENDING`, и клиент направляется на HPP.
- **Завершение оплаты (Redirect Page)**:
  - Клиент возвращается на Redirect Page фронтенда.
  - Фронтенд асинхронно опрашивает статус транзакции через бэкенд.
  - При успехе: транзакция -> `SUCCESS`, ссылка -> `COMPLETED`, редирект на `successUrl`. При ошибке: транзакция -> `FAILED`, ссылка остается `ACTIVE`.

### 2.3. Многоразовая ссылка (`UsageType.MULTIPLE`)
- **Переход по ссылке (`/open`)**:
  - Валидация: статус `ACTIVE`, лимит оплат не исчерпан.
  - Контроль повторных оплат: для одного плательщика в один момент времени разрешена только одна активная транзакция (`PENDING` / `AUTHORIZED`). Применяется аналогичное правило 10-минутного таймаута для `PENDING`-транзакций.
  - При создании новой попытки регистрируется заказ и создается транзакция `PENDING`.
- **Завершение оплаты**:
  - При успехе: транзакция -> `SUCCESS`. Если лимит достигнут, ссылка -> `COMPLETED`.

---

## 3. Логика работы с таблицей `transactions`

### 3.1. Поля таблицы транзакций
- `id` (UUID) — Внутренний уникальный идентификатор.
- `link_id` (UUID) — Внешний ключ на платещую ссылку.
- `merchant_rid` (UUID) — UUID заказа, переданный провайдеру в `ridByMerchant`.
- `provider_order_id` (String) — ID заказа провайдера.
- `provider_password` (String) — Пароль заказа провайдера (для последующих операций).
- `amount` (Decimal) — Сумма транзакции.
- `refunded_amount` (Decimal) — Сумма выполненных возвратов.
- `status` (Enum) — `PENDING`, `AUTHORIZED`, `SUCCESS`, `FAILED`, `PARTIALLY_REFUNDED`, `REFUNDED`.
- `provider_response` (JSONB) — Лог ответа провайдера.
- `created_at` / `updated_at` (Timestamp) — Метки времени.

### 3.2. DMS Clearing (Списание)
- Вызывается через: `POST /api/v1/transactions/{transactionId}/complete`.
- Транзакция должна быть в статусе `AUTHORIZED`.
- Сервис загружает учетные данные терминала (`login`/`password`) по `terminal_id` платежной ссылки и отправляет запрос списания провайдеру.
- При успехе статус транзакции переводится in `SUCCESS`.

### 3.3. Возвраты (Refunds)
- Вызывается через: `POST /api/v1/transactions/{transactionId}/refund`.
- Валидация: статус `SUCCESS` или `PARTIALLY_REFUNDED`, сумма возвратов не превышает сумму транзакции.
- Сервис загружает учетные данные терминала и отправляет запрос возврата провайдеру.
- Обновление статусов: поле `refunded_amount` увеличивается, при полном возврате статус -> `REFUNDED`, иначе -> `PARTIALLY_REFUNDED`.

---

## 4. Интеграция с эквайером (MilliKart - TXPG)

Каждый запрос к API провайдера требует передачи заголовка `Authorization: Basic <credentials>` на основе логина и пароля терминала, полученных из БД.

### 4.1. Регистрация заказа (SMS и DMS)
- **URL**: `POST https://test.millikart.az:8083/order`
- **Request Payload**:
  ```json
  {
      "order": {
          "typeRid": "Order_SMS",
          "ridByMerchant": "{{random_uuid}}",
          "amount": "{{amount}}",
          "currency": "{{currency}}",
          "description": "{{description}}",
          "language": "az",
          "hppRedirectUrl": "{{CustomerRedirectUrl}}",
          "subMerchant": {
              "url": "https://millikart.az/"
          }
      }
  }
  ```
- **Response Payload**:
  ```json
  {
      "order": {
          "hppUrl": "https://test.millikart.az:8083/pay",
          "id": 173870,
          "status": "Preparing",
          "password": "1li8ba7qw4zge"
      }
  }
  ```
- **Формирование ссылки на HPP**: `{hppUrl}?id={id}&password={password}`

### 4.2. DMS Clearing (Списание)
- **URL**: `POST https://test.millikart.az:8000/api/order/{{orderId}}/exec-tran?password={{password}}`
- **Request Payload**:
  ```json
  {  
      "tran": {
          "phase": "Clearing"
      }
  }
  ```

### 4.3. Возвраты (Refunds)
- **URL**: `POST https://test.millikart.az:8000/api/order/{{orderId}}/exec-tran?password={{password}}`
- **Request Payload**:
  ```json
  {
      "tran": {
          "phase": "Single",
          "amount": "{{amount}}",
          "type": "Refund"
      }
  }
  ```

### 4.4. Проверка статусов оплаты (Status Check)
- **URL**: `GET https://test.millikart.az:8000/api/order/{{orderId}}?password={{password}}&orderDetailLevel=2&tokenDetailLevel=2&tranDetailLevel=2`
- **Логика**: Запрос статуса для верификации оплаты на Redirect Page.
