# MP Merchant Portal — Frontend Module

Реализация личного кабинета мерчанта на базе React 18, TypeScript 7, Vite 6 и Material UI 7.

Правила фронтенда — маршруты, авторизация, подтверждения, прокси на сервисы — в корневом
[`AGENTS.md`](../AGENTS.md), §9; устройство приложения — [`project_docs/application_description.md`](../project_docs/application_description.md), §10.

## Команды:

```bash
# Установка зависимостей
npm ci

# Запуск локального dev-сервера (порт 3000, /api/v1/* проксируется на бэкенд)
npm run dev

# Проверка типов (tsc -b) и линтер (oxlint)
npm run typecheck
npm run lint

# Продакшн сборка: tsc -b && vite build → dist/
npm run build

# Локально отдать собранный dist/
npm run preview
```

## Адрес API

`VITE_API_BASE_URL` (см. `.env.example`). Пусто по умолчанию — запросы идут относительно origin
(dev-прокси Vite / nginx на том же домене в проде). Значение зашивается в бандл при сборке.
