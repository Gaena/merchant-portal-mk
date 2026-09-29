# 📦 Руководство по развёртыванию Merchant Portal (MP)

> **Для кого эта документация:** для тех, кто будет устанавливать систему на сервер. Знания программирования НЕ требуются — достаточно уметь подключаться к серверу и вводить команды.
>
> Сверено с кодом 29.09.2026.

---

## 📋 Содержание

1. [Обзор системы](#1-обзор-системы)
2. [Что нужно подготовить заранее](#2-что-нужно-подготовить-заранее)
3. [Шаг 1 — Подключение к серверу](#3-шаг-1--подключение-к-серверу)
4. [Шаг 2 — Установка системных программ](#4-шаг-2--установка-системных-программ)
5. [Шаг 3 — Настройка базы данных PostgreSQL](#5-шаг-3--настройка-базы-данных-postgresql)
6. [Шаг 4 — Получение исходного кода проекта](#6-шаг-4--получение-исходного-кода-проекта)
7. [Шаг 5 — Сборка бэкенда (Java-сервисы)](#7-шаг-5--сборка-бэкенда-java-сервисы)
8. [Шаг 6 — Настройка конфигурации](#8-шаг-6--настройка-конфигурации)
9. [Шаг 7 — Запуск бэкенд-сервисов через systemd](#9-шаг-7--запуск-бэкенд-сервисов-через-systemd)
10. [Шаг 8 — Сборка фронтенда](#10-шаг-8--сборка-фронтенда)
11. [Шаг 9 — Настройка Nginx](#11-шаг-9--настройка-nginx)
12. [Шаг 10 — Настройка HTTPS (SSL-сертификат)](#12-шаг-10--настройка-https-ssl-сертификат)
13. [Шаг 11 — Настройка файрвола](#13-шаг-11--настройка-файрвола)
14. [Шаг 12 — Проверка работоспособности](#14-шаг-12--проверка-работоспособности)
    - [14.3 Swagger на время приёмки](#143-swagger-на-время-приёмки)
15. [Обновление системы](#15-обновление-системы)
16. [Резервное копирование](#16-резервное-копирование)
17. [Мониторинг и логи](#17-мониторинг-и-логи)
18. [Устранение неполадок](#18-устранение-неполадок)
19. [Справочник: порты и сервисы](#19-справочник-порты-и-сервисы)
20. [Первый запуск и ротация ключа](#20-первый-запуск-и-ротация-ключа)
    - [20.1 Первый запуск и полный перечень переменных](#201-первый-запуск-новой-установки)

---

## 1. Обзор системы

Merchant Portal — это веб-приложение, состоящее из **5 компонентов**:

| Компонент | Описание | Порт |
|-----------|----------|------|
| **auth** | Авторизация пользователей, JWT-токены | `8081` |
| **directory** | Справочник компаний и терминалов | `8082` |
| **pbl** | Pay-By-Link — платёжные ссылки, операции, статистика оплат по ссылкам, проверка терминала у провайдера (кнопка «Тест») | `8080` |
| **ecom** | Выписка E-commerce, сводка главной страницы, справочники терминалов и логинов провайдера — читает базу платёжного шлюза | `8083` |
| **frontend** | Веб-интерфейс (React + Vite) | `3000` (разработка) / через Nginx (продакшн) |

```
Интернет → Nginx (порты 80/443)
  ├── статические файлы фронтенда (dist/)
  ├── /api/v1/auth, /api/v1/users                                → auth       :8081
  ├── /api/v1/companies, /api/v1/terminals, /api/v1/audit-logs   → directory  :8082
  ├── /api/v1/payment-links, /api/v1/transactions,
  │   /api/v1/dashboard, /api/v1/acquiring                       → pbl        :8080  → API шлюза MilliKart
  └── /api/v1/ecom                                               → ecom       :8083  → база шлюза (только чтение)

auth, directory, pbl, ecom → PostgreSQL :5432 (одна база на все сервисы)
```

> [!IMPORTANT]
> Все бэкенд-сервисы используют **одну и ту же базу данных PostgreSQL**. Миграции (создание таблиц) выполняются автоматически при первом запуске через Liquibase.
>
> Все четыре сервиса **обязательны**. `ecom` вдобавок читает базу платёжного шлюза MilliKart (Oracle) — только на чтение. Без него не заводятся компании и терминалы: логин компании и терминал выбираются из справочников провайдера, которые снимает только `ecom` (Р-93, Р-94), — а главная страница и вкладка E-commerce пусты.

---

## 2. Что нужно подготовить заранее

### Минимальные требования к серверу

| Параметр | Минимум | Рекомендуется |
|----------|---------|---------------|
| ОС | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS |
| CPU | 2 ядра | 4 ядра |
| RAM | 4 ГБ | 8 ГБ |
| Диск | 20 ГБ | 50 ГБ SSD |
| Сеть | Публичный IP-адрес | + доменное имя |

### Что нужно знать/иметь

- **IP-адрес сервера** — выдаётся хостинг-провайдером
- **Логин и пароль** для подключения к серверу (обычно `root` + пароль)
- **Доменное имя** (если планируется HTTPS), например `mp.millikart.az`
- **Доступ к Git-репозиторию** проекта (логин + пароль или SSH-ключ)
- **Адреса шлюза и API эквайера** для `pbl` — выдаёт MilliKart
- **Для `ecom`:** адрес базы платёжного шлюза, схема и учётная запись с правом только на чтение, а также сетевой доступ к этой базе с сервера — выдаёт MilliKart

---

## 3. Шаг 1 — Подключение к серверу

### На Windows

1. Скачайте программу **PuTTY** с [putty.org](https://www.putty.org/)
2. Установите и запустите её
3. В поле **Host Name** введите IP-адрес сервера
4. Нажмите **Open**
5. Введите логин (обычно `root`) и пароль

### На macOS / Linux

Откройте **Терминал** и введите:

```bash
ssh root@ВАШ_IP_АДРЕС
```

Введите пароль когда попросят (символы не отображаются — это нормально).

> [!TIP]
> Если вы подключились успешно, вы увидите строку вроде `root@server:~#` — это означает, что вы «внутри» сервера и можете вводить команды.

---

## 4. Шаг 2 — Установка системных программ

> [!IMPORTANT]
> Все команды в этом руководстве — для **Ubuntu** (`apt`, пути `/etc/postgresql/16/…`, пользователь `www-data`). На других дистрибутивах пакеты и пути другие.

### 4.1. Обновление системы

Сначала обновим список доступных программ:

```bash
sudo apt update && sudo apt upgrade -y
```

> **Что это делает:** скачивает информацию о новых версиях программ и обновляет уже установленные. Флаг `-y` означает «отвечать на все вопросы „Да"».

### 4.2. Установка базовых утилит

```bash
sudo apt install -y curl wget git unzip software-properties-common
```

### 4.3. Установка Java 21

Проект написан на Java 21 — это **обязательная** версия.

```bash
# Устанавливаем JDK 21
sudo apt install -y openjdk-21-jdk

# Проверяем, что Java установилась
java -version
```

Вы должны увидеть что-то вроде:

```
openjdk version "21.0.x" ...
```

> [!CAUTION]
> Если команда `java -version` показывает другую версию (например, 17 или 11), нужно переключиться:
> ```bash
> sudo update-alternatives --config java
> ```
> Выберите номер, соответствующий Java 21, и нажмите Enter.

### 4.4. Установка Node.js 22 LTS

Node.js нужен для сборки фронтенда (веб-интерфейса).

```bash
# Устанавливаем Node.js 22 через NodeSource
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs

# Проверяем версии
node -v    # Должно быть v22.x.x
npm -v     # Должно быть 10.x.x
```

### 4.5. Установка PostgreSQL 16

> Версия 16 выбрана для прода (решение Р-73) и совпадает с той, на которой идут тесты
> (`postgres:16-alpine` в `PostgresTestContainer`). Меняется одна — меняется и другая.

```bash
# Добавляем официальный репозиторий PostgreSQL
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/postgresql.gpg
sudo apt update

# Устанавливаем PostgreSQL
sudo apt install -y postgresql-16

# Проверяем, что PostgreSQL запущен
sudo systemctl status postgresql
```

Вы должны увидеть строку `Active: active (running)` — значит база данных работает.

### 4.6. Установка Nginx

Nginx — это веб-сервер, который будет раздавать фронтенд и перенаправлять запросы на бэкенд.

```bash
sudo apt install -y nginx

# Проверяем
sudo systemctl status nginx
```

---

## 5. Шаг 3 — Настройка базы данных PostgreSQL

### 5.1. Создание пользователя и базы данных

Сначала придумайте пароль базы данных. Проще всего сгенерировать его:

```bash
openssl rand -base64 24
```

Запишите значение — оно понадобится в §8.3 и §16. В таком пароле нет кавычек, `$` и обратной косой
черты, которые пришлось бы экранировать в SQL, в файле окружения и в `~/.pgpass`.

```bash
# Переключаемся на пользователя postgres (системный пользователь БД)
sudo -u postgres psql
```

Вы попадёте в консоль PostgreSQL (строка будет начинаться с `postgres=#`). Введите следующие команды **по одной, нажимая Enter после каждой**:

```sql
-- Устанавливаем пароль для пользователя postgres
ALTER USER postgres WITH PASSWORD 'ВАШ_НАДЁЖНЫЙ_ПАРОЛЬ';

-- Создаём базу данных (если её ещё нет)
CREATE DATABASE merchant_portal;

-- Выходим из консоли PostgreSQL
\q
```

> [!WARNING]
> **Обязательно** замените `ВАШ_НАДЁЖНЫЙ_ПАРОЛЬ` на пароль, сгенерированный выше.
>
> **НЕ ИСПОЛЬЗУЙТЕ** пароль `password` на продакшн-серверах!

### 5.1a. Роль приложения и права на журнал аудита (Р-42)

**При установке этот шаг не выполняется.** Сервисы подключаются к базе как `postgres`, то есть
суперпользователем, а на него `REVOKE` не действует. Перевод сервисов на отдельную роль — известное
ограничение ([`AGENTS.md`](../../AGENTS.md) §10); зачем журналу аудита права только на добавление —
[`technical_handover.md`](technical_handover.md) §4.4. Команды ниже — для этого перевода.

Выполнять **после первого запуска сервисов** (§9.5): таблицу `audit_logs` создают миграции.

```bash
sudo -u postgres psql -d merchant_portal
```

```sql
-- Роль, под которой будут работать сервисы. NOSUPERUSER и NOCREATEROLE — обязательно.
CREATE ROLE mp_app WITH LOGIN PASSWORD 'ПАРОЛЬ_ПРИЛОЖЕНИЯ' NOSUPERUSER NOCREATEDB NOCREATEROLE;

-- Работа со схемой и с обычными таблицами
GRANT CONNECT ON DATABASE merchant_portal TO mp_app;
GRANT USAGE ON SCHEMA public TO mp_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO mp_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO mp_app;

-- А журнал аудита — только добавление и чтение. Ни UPDATE, ни DELETE.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM mp_app;
GRANT SELECT, INSERT ON audit_logs TO mp_app;

-- То же самое для таблиц и последовательностей, которые появятся при следующих миграциях
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mp_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO mp_app;
```

Защита заработает, когда `DB_USERNAME` в `/opt/merchant-portal/config/mp.env` (§8.3) сменят с
`postgres` на `mp_app`. Вместе с этим решается, кто применяет миграции — у `mp_app` нет права менять схему:

1. выдать `mp_app` право на схему (`GRANT CREATE ON SCHEMA public TO mp_app;`) — проще, но роль
   приложения снова может создать таблицу и, например, подменить журнал;
2. прогонять миграции отдельным пользователем (тем же `postgres`) до старта сервисов, а сервисы
   запускать под `mp_app` с выключенным Liquibase (`spring.liquibase.enabled=false`) — строже,
   но требует отдельного шага при каждом обновлении.

### 5.2. Разрешение подключения по паролю

Сервисы ходят в базу по TCP на `localhost` с паролем. В PostgreSQL 16 на Ubuntu это разрешено из коробки
методом `scram-sha-256`, который драйвер JDBC поддерживает, — менять ничего не нужно. Проверьте:

```bash
sudo grep -E '^(local|host)' /etc/postgresql/16/main/pg_hba.conf
```

Должна быть строка:

```
host    all   all   127.0.0.1/32   scram-sha-256
```

Строку `local all all peer` не трогайте: на ней держатся команды `sudo -u postgres psql` из этого руководства.

### 5.3. Проверка подключения

```bash
psql -U postgres -h localhost -d merchant_portal
```

Введите пароль, который вы установили. Если вы увидели `merchant_portal=#` — база данных настроена правильно! Введите `\q` и нажмите Enter для выхода.

---

## 6. Шаг 4 — Получение исходного кода проекта

### 6.1. Создание рабочей директории

```bash
# Создаём директорию для приложения
sudo mkdir -p /opt/merchant-portal
sudo chown $USER:$USER /opt/merchant-portal
cd /opt/merchant-portal
```

### 6.2. Клонирование репозитория

```bash
git clone ВАШ_URL_РЕПОЗИТОРИЯ .
```

> [!NOTE]
> Замените `ВАШ_URL_РЕПОЗИТОРИЯ` на реальный URL. Точка (`.`) в конце означает «клонировать в текущую директорию».
>
> Пример: `git clone https://gitlab.millikart.az/mp/merchant-portal.git .`

Если Git спросит логин и пароль — введите их.

---

## 7. Шаг 5 — Сборка бэкенда (Java-сервисы)

### 7.1. Сборка всех модулей одной командой

```bash
cd /opt/merchant-portal

# Делаем gradlew исполняемым
chmod +x gradlew

# Запускаем сборку (займёт 2-5 минут)
./gradlew clean build -x test
```

> **Что происходит:** Gradle скачивает все зависимости (библиотеки) и компилирует Java-код в исполняемые JAR-файлы. Флаг `-x test` пропускает тесты для ускорения.

Вы должны увидеть в конце:

```
BUILD SUCCESSFUL in Xm Xs
```

> [!CAUTION]
> Если вы видите `BUILD FAILED` — проверьте:
> 1. Установлена ли Java 21: `java -version`
> 2. Есть ли доступ в интернет (Gradle скачивает библиотеки)
> 3. Достаточно ли памяти: `free -h` (нужно минимум 2 ГБ свободной RAM)

### 7.2. Где находятся собранные файлы

После успешной сборки, JAR-файлы появятся здесь:

| Сервис | Путь к JAR-файлу |
|--------|------------------|
| auth | `auth/build/libs/auth-0.0.1-SNAPSHOT.jar` |
| directory | `directory/build/libs/directory-0.0.1-SNAPSHOT.jar` |
| pbl | `pbl/build/libs/pbl-0.0.1-SNAPSHOT.jar` |
| ecom | `ecom/build/libs/ecom-0.0.1-SNAPSHOT.jar` |

### 7.3. Копирование JAR-файлов в рабочую директорию

```bash
# Создаём директорию для запускаемых файлов
sudo mkdir -p /opt/merchant-portal/deploy

# Копируем JAR-файлы (владелец — root, сервис их только читает)
sudo cp auth/build/libs/auth-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/auth.jar
sudo cp directory/build/libs/directory-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/directory.jar
sudo cp pbl/build/libs/pbl-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/pbl.jar
sudo cp ecom/build/libs/ecom-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/ecom.jar
```

---

## 8. Шаг 6 — Настройка конфигурации

Вся настройка сервисов — переменные окружения в одном файле `/opt/merchant-portal/config/mp.env` (§8.3).

### 8.1. Создание конфигурационных файлов

Отдельных файлов конфигурации не создаётся. `application.yaml` каждого сервиса встроен в его JAR-файл,
а всё, что отличается от установки к установке, — адреса, секреты, расписания — сервис читает из
переменных окружения. Их полный перечень — таблица [§20.1](#201-первый-запуск-новой-установки).

> [!WARNING]
> Не заводите внешних копий `application.yaml` и не добавляйте в юниты `--spring.config.location`.
> Внешний файл целиком заменяет встроенный и устаревает с первым же обновлением: свойства, появившегося
> в новой версии без значения по умолчанию, в копии нет, и сервис не стартует с
> `Could not resolve placeholder '…'`. Копии, оставшиеся от прежней установки, удалите вместе с этим
> параметром в юнитах (§9).

### 8.2. Что нужно заменить в конфигурации

В yaml — ничего: секретов и адресов в нём нет, только ссылки вида `${ПЕРЕМЕННАЯ}`. Значения задаются
в `mp.env` (§8.3), какие из них обязательны и что значат — таблица [§20.1](#201-первый-запуск-новой-установки).

### 8.3. Файл с переменными окружения

Файл один на все четыре сервиса, его читает systemd. Владелец — `root`, группа — `mpuser`, системный
пользователь, от имени которого работают сервисы (§9.1): сервис файл читает, но изменить не может.
Сначала создайте пользователя, каталог и пустой файл с нужными правами:

```bash
# Системный пользователь сервисов (без входа в систему)
sudo useradd -r -s /bin/false mpuser

sudo mkdir -p /opt/merchant-portal/config
sudo chown root:mpuser /opt/merchant-portal/config
sudo chmod 750 /opt/merchant-portal/config

# Файл содержит пароль БД и ключи — читать его должен только сервис
sudo touch /opt/merchant-portal/config/mp.env
sudo chown root:mpuser /opt/merchant-portal/config/mp.env
sudo chmod 640 /opt/merchant-portal/config/mp.env
```

Затем запишите значения — адреса и учётную запись базы шлюза выдаёт MilliKart:

```bash
# 1. Значения, которые вводите вы. 'EOF' в кавычках: знак $ в пароле останется как есть
sudo tee /opt/merchant-portal/config/mp.env > /dev/null << 'EOF'
DB_URL=jdbc:postgresql://localhost:5432/merchant_portal
DB_USERNAME=postgres
DB_PASSWORD=ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ
PBL_BASE_URL=https://ВАШ_ДОМЕН/
PBL_PROVIDER_GATEWAY_BASE_URL=АДРЕС_ШЛЮЗА_ОТ_MILLIKART
PBL_PROVIDER_API_BASE_URL=АДРЕС_API_ОТ_MILLIKART
ECOM_TXPG_URL=jdbc:oracle:thin:@//АДРЕС_БАЗЫ_ШЛЮЗА:ПОРТ/СЕРВИС
ECOM_TXPG_USERNAME=ПОЛЬЗОВАТЕЛЬ_ТОЛЬКО_НА_ЧТЕНИЕ
ECOM_TXPG_PASSWORD=ПАРОЛЬ
ECOM_TXPG_SCHEMA=TXPG
AUTH_BOOTSTRAP_ENABLED=false
PBL_API_TOKEN_ENABLED=false
SWAGGER_ENABLED=false
EOF

# 2. Ключи генерируются на месте. Здесь EOF без кавычек — намеренно: так $(openssl …) выполнится
#    и в файл попадёт готовый ключ
sudo tee -a /opt/merchant-portal/config/mp.env > /dev/null << EOF
JWT_SECRET=$(openssl rand -base64 48)
CREDENTIALS_ENCRYPTION_KEY=$(openssl rand -base64 32)
EOF
```

Проверьте, что ключ действительно записался (а не строка `$(openssl…)`):

```bash
sudo grep JWT_SECRET /opt/merchant-portal/config/mp.env
```

> [!NOTE]
> В значениях `mp.env` не используйте кавычки и обратную косую черту и не оставляйте пробелов по краям:
> systemd разбирает их по-своему. Пароли удобнее всего генерировать `openssl rand -base64 24`.

> [!CAUTION]
> Учётная запись базы шлюза должна иметь право **только на чтение**: `ecom` в эту базу не пишет,
> а его отчётные запросы идут по боевой базе платежей. Лучший вариант — отдельная читающая реплика.

> [!CAUTION]
> **`PBL_BASE_URL` — это адрес, на который эквайер вернёт плательщика после оплаты.**
> Сервис отдаёт его MilliKart как `hppRedirectUrl` при создании каждого заказа. Неверное,
> но формально корректное значение (чужой домен, опечатка в домене, `http://` вместо
> `https://`) сервис **не роняет**:
> он стартует, ссылки создаются, страница оплаты открывается — а после оплаты эквайер
> отправляет плательщика не на тот хост, и ни один платёж не завершается. Ошибки в логе
> при этом не будет. Проверьте значение дважды: схема `https://`, ваш домен, завершающий `/`.
> На старте проверяется только форма: пустое значение, не-URL (в том числе незаменённый
> плейсхолдер), относительный путь, схема кроме `http`/`https`, пробел или перевод строки на конце —
> отказ стартовать с инструкцией (§20.4); не-HTTPS адрес на любом хосте, кроме
> `localhost`/`127.0.0.1`/`[::1]`, — WARN в рамке.
>
> `PBL_PROVIDER_GATEWAY_BASE_URL` и `PBL_PROVIDER_API_BASE_URL` — адреса шлюза и API эквайера,
> их выдаёт MilliKart, и у тестового стенда и прода они разные. Если адрес API у эквайера пока только
> `http://`, сервис стартует, но пишет WARN: по этому каналу уходит Basic-авторизация с логином и паролем
> компании. Это разговор с MilliKart о HTTPS, а не правка конфигурации.

> [!WARNING]
> `JWT_SECRET` должен быть **одинаковым** во всех четырёх сервисах — поэтому файл один на всех.
> Токен выдаёт `auth`, а `directory`, `pbl` и `ecom` проверяют его тем же ключом (HS256 симметричный).
> Разные значения — и вход проходит, а любой запрос к остальным сервисам возвращает 401.

> [!WARNING]
> `CREDENTIALS_ENCRYPTION_KEY` — ключ, которым зашифрованы пароли компаний к провайдеру (Р-93):
> `directory` шифрует, `pbl` расшифровывает, значение одно. Его **нельзя терять и менять**: с другим
> ключом сохранённые пароли не расшифровываются, и каждой компании пароль придётся ввести заново.
> Храните копию ключа отдельно от бэкапов базы (§16).

> [!CAUTION]
> Обязательные переменные (таблица [§20.1](#201-первый-запуск-новой-установки)) **не имеют значений
> по умолчанию**: сервис без них не стартует и называет переменную (§20.4). Дефолт у секрета — это ключ,
> опубликованный вместе с кодом, а у адреса — прод, молча отправляющий плательщиков на `localhost`
> или платежи — тестовому эквайеру.

> [!NOTE]
> `SWAGGER_ENABLED=false` — это рабочее состояние; как временно включить документацию на время
> приёмки — [§14.3](#143-swagger-на-время-приёмки). `MANAGEMENT_PORT` и `SERVER_PORT` в этот файл
> не добавляйте: их читают все четыре сервиса, и одно значение на всех — конфликт портов (§20.1).

Полная процедура первого запуска, создания администратора и смены ключа —
раздел [20](#20-первый-запуск-и-ротация-ключа).

---

## 9. Шаг 7 — Запуск бэкенд-сервисов через systemd

systemd — это менеджер служб в Linux. Он будет **автоматически запускать** наши сервисы при старте сервера и перезапускать их при сбоях.

### 9.1. Создание системного пользователя

Сервисы работают не от `root`, а от системного пользователя `mpuser` — он создан в §8.3 вместе с файлом
окружения. Проверить:

```bash
id mpuser
```

Права на запись `mpuser` не нужны нигде: JAR-файлы в `deploy/` принадлежат `root` и открыты на чтение
(§7.3), `mp.env` читается через группу (§8.3), а логи сервисы отдают в journald (§17.1).

### 9.2. Создание systemd-юнита для Auth

```bash
sudo tee /etc/systemd/system/mp-auth.service > /dev/null << 'EOF'
[Unit]
Description=Merchant Portal - Auth Service
After=network.target postgresql.service
Requires=postgresql.service
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Вся настройка — переменные из этого файла (§8.3, перечень — §20.1)
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/auth.jar

Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
```

### 9.3. Создание systemd-юнита для Directory

```bash
sudo tee /etc/systemd/system/mp-directory.service > /dev/null << 'EOF'
[Unit]
Description=Merchant Portal - Directory Service
After=network.target postgresql.service
Requires=postgresql.service
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Вся настройка — переменные из этого файла (§8.3, перечень — §20.1)
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/directory.jar

Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
```

### 9.4. Создание systemd-юнита для PBL

```bash
sudo tee /etc/systemd/system/mp-pbl.service > /dev/null << 'EOF'
[Unit]
Description=Merchant Portal - PBL Service (Pay-By-Link)
After=network.target postgresql.service
Requires=postgresql.service
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Вся настройка — переменные из этого файла (§8.3, перечень — §20.1)
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/pbl.jar

Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
```

### 9.4a. Создание systemd-юнита для ecom

```bash
sudo tee /etc/systemd/system/mp-ecom.service > /dev/null << 'EOF'
[Unit]
Description=Merchant Portal - ecom Service (E-commerce statement)
After=network.target postgresql.service
Requires=postgresql.service
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Вся настройка — переменные из этого файла (§8.3, перечень — §20.1)
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/ecom.jar

Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
```

### 9.5. Запуск всех сервисов

> [!IMPORTANT]
> Если это **первый** запуск установки — сначала прочитайте раздел
> [20](#20-первый-запуск-и-ротация-ключа): пустая база не содержит ни одного пользователя,
> и администратора нужно завести отдельным шагом.

```bash
# Перечитываем конфигурацию systemd (обязательно после создания или правки юнитов)
sudo systemctl daemon-reload

# Включаем автозапуск при старте сервера
sudo systemctl enable mp-auth mp-directory mp-pbl mp-ecom

# 1. auth, directory и pbl — в любом порядке (P1-2): их миграции обложены преконтролями
sudo systemctl start mp-auth mp-directory mp-pbl

# 2. Ждём в журнале строку «Started DirectoryApplication»
#    (нет строки — подождите несколько секунд и повторите команду)
sudo journalctl -u mp-directory -n 30 --no-pager | grep 'Started DirectoryApplication'

# 3. ecom — только после этого
sudo systemctl start mp-ecom
```

> [!IMPORTANT]
> На **пустой** базе `ecom` стартует только после `directory` или `pbl`: его миграция дополняет таблицу
> `terminals`, которую создают они, а таблицу `audit_logs` `ecom` не создаёт вовсе. Запущенный первым,
> он падает на миграции; после старта `directory` его достаточно запустить ещё раз
> (`sudo systemctl reset-failed mp-ecom && sudo systemctl start mp-ecom`). Схема и порядок
> миграций — [`application_description.md`](application_description.md) §4.2. При обновлении — наоборот:
> `ecom` запускают первым, не позже `directory` (§15.1).

> [!NOTE]
> Внешний ключ `fk_terminals_company` (`terminals.company_id → companies.id`) создаёт `auth`, и только
> когда таблица `terminals` уже есть; иначе он повторяет попытку при следующем своём старте
> ([`application_description.md`](application_description.md) §4.2). Поэтому на свежей установке
> перезапустите `auth` один раз после того, как поднялись остальные (`sudo systemctl restart mp-auth`), —
> или дождитесь ближайшего планового рестарта. Проверить:
>
> ```bash
> sudo -u postgres psql -d merchant_portal -c "\d terminals" | grep fk_terminals_company
> ```

### 9.6. Проверка статуса

```bash
# Проверяем сервисы
sudo systemctl status mp-auth
sudo systemctl status mp-directory
sudo systemctl status mp-pbl
sudo systemctl status mp-ecom
```

Для каждого сервиса вы должны увидеть `Active: active (running)`.

> [!TIP]
> **Полезные команды для управления сервисами:**
> ```bash
> sudo systemctl stop mp-auth        # Остановить
> sudo systemctl restart mp-auth     # Перезапустить
> sudo systemctl status mp-auth      # Проверить статус
> sudo journalctl -u mp-auth -f      # Смотреть логи в реальном времени
> sudo journalctl -u mp-auth --since "1 hour ago"  # Логи за последний час
> ```

---

## 10. Шаг 8 — Сборка фронтенда

### 10.1. Установка зависимостей

```bash
cd /opt/merchant-portal/frontend

# Устанавливаем зависимости ровно тех версий, что в package-lock.json
npm ci
```

> Это займёт 1–3 минуты. Вы увидите много текста — это нормально.

### 10.2. Сборка для продакшна

```bash
npm run build
```

> `npm run build` — это `tsc -b && vite build`: сначала проверка типов, потом сборка.
> Если сборка упала с `error TS…` — это ошибка в коде фронтенда, а не окружения.
>
> Адрес API задаётся переменной `VITE_API_BASE_URL` (шаблон `frontend/.env.example`) **на этапе
> сборки**. Для схемы из этого руководства (Nginx раздаёт фронтенд и проксирует `/api/v1/*` с того же
> домена) её задавать **не нужно** — пустое значение означает «относительно текущего домена».
> Указывать её нужно только если фронтенд обслуживается с другого домена, чем API.

После успешной сборки появится папка `dist/` с готовыми файлами:

```bash
ls -la dist/
```

Вы должны увидеть `index.html` и папку `assets/`.

### 10.3. Копирование файлов для Nginx

```bash
sudo mkdir -p /var/www/merchant-portal
sudo cp -r dist/* /var/www/merchant-portal/
sudo chown -R www-data:www-data /var/www/merchant-portal
```

---

## 11. Шаг 9 — Настройка Nginx

### 11.1. Создание конфигурации сайта

> [!IMPORTANT]
> **`X-Real-IP` и `X-Forwarded-For` в блоках ниже — источник адреса клиента.** По нему `auth` считает
> неудачные попытки входа, `pbl` пишет адрес плательщика в `transactions.client_ip`, все сервисы —
> в журнал аудита и в логи. Сервисы верят этим заголовкам только от адресов из `TRUSTED_PROXIES`
> (по умолчанию `127.0.0.1,::1` — этот самый nginx): берут `X-Real-IP`, а без него — **последний**
> элемент `X-Forwarded-For`, потому что `$proxy_add_x_forwarded_for` дописывает настоящий адрес в конец,
> а всё, что прислал клиент, оставляет впереди.
>
> Поэтому:
> * **строки `X-Real-IP` и `X-Forwarded-For` не убирать** ни из одного `location` — без них адресом
>   клиента становится адрес самого nginx, и лимит входа становится общим на весь мир;
> * `$remote_addr` не заменять на `$http_x_real_ip` или `$http_x_forwarded_for` —
>   это ровно то, что прислал клиент;
> * если перед nginx встанет ещё один прокси (балансировщик, CDN) — настройте nginx по §11.4;
>   `TRUSTED_PROXIES` при этом не трогать.

```bash
sudo nano /etc/nginx/sites-available/merchant-portal
```

Вставьте следующий текст (используйте **Ctrl+Shift+V** для вставки в терминале):

```nginx
# ═══════════════════════════════════════════════════════════
# Merchant Portal — Nginx Configuration
# ═══════════════════════════════════════════════════════════

# Апстримы бэкенд-сервисов
upstream auth_backend {
    server 127.0.0.1:8081;
    keepalive 32;
}

upstream directory_backend {
    server 127.0.0.1:8082;
    keepalive 32;
}

upstream pbl_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

# Сервис выписки и сводки главной (ecom)
upstream ecom_backend {
    server 127.0.0.1:8083;
    keepalive 16;
}

server {
    listen 80;
    server_name ВАШ_ДОМЕН;    # Замените на ваш домен, например: mp.millikart.az
    
    # Корневая директория с фронтендом
    root /var/www/merchant-portal;
    index index.html;

    # Ограничение размера загружаемых файлов
    client_max_body_size 10M;

    # Включаем сжатие для ускорения загрузки
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript image/svg+xml;
    gzip_min_length 1000;

    # ───── API-маршруты ─────

    # Auth-сервис (авторизация и пользователи)
    location /api/v1/auth {
        proxy_pass http://auth_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    location /api/v1/users {
        proxy_pass http://auth_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    # Directory-сервис (компании и терминалы)
    location /api/v1/companies {
        proxy_pass http://directory_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    location /api/v1/terminals {
        proxy_pass http://directory_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    location /api/v1/audit-logs {
        proxy_pass http://directory_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    # PBL-сервис (платёжные ссылки, операции, страницы плательщика /open и /redirect)
    location /api/v1/payment-links {
        proxy_pass http://pbl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    location /api/v1/transactions {
        proxy_pass http://pbl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    # Статистика оплат по ссылкам (вкладка «Статистика» страницы Pay by Link). Без этого блока
    # запрос уходит в location / и вместо данных возвращается index.html фронтенда.
    location /api/v1/dashboard {
        proxy_pass http://pbl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    # Сервис выписки: заказы из схемы шлюза провайдера, сводка главной, справочники провайдера.
    # read_timeout длиннее обычного: отчётный запрос по чужой базе бывает небыстрым, а сервис
    # сам ограничивает его своим statement timeout (ECOM_TXPG_QUERY_TIMEOUT).
    location /api/v1/ecom {
        proxy_pass http://ecom_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 90s;
    }

    # Проверка терминала пробным заказом у провайдера с логином и паролем компании (кнопка «Тест»).
    # Отдельный префикс в PBL: /api/v1/terminals целиком уходит в directory, а к провайдеру
    # умеет ходить только PBL.
    location /api/v1/acquiring {
        proxy_pass http://pbl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    # Actuator и Swagger сюда не проксируются: actuator слушает только 127.0.0.1 (§19),
    # Swagger открывают через SSH-туннель (§14.3).

    # ───── Статические файлы фронтенда ─────
    
    # Кэширование статических ресурсов (JS, CSS, изображения). Своего add_header здесь нет
    # намеренно: add_header внутри location отменяет заголовки безопасности уровня server (ниже).
    location /assets/ {
        expires 1y;
        try_files $uri =404;
    }

    # Все остальные маршруты → index.html (React Router / SPA)
    location / {
        try_files $uri $uri/ /index.html;
    }

    # ───── Безопасность ─────
    
    # Запрещаем доступ к скрытым файлам
    location ~ /\. {
        deny all;
        return 404;
    }

    # Заголовки безопасности
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
}
```

### 11.2. Активация конфигурации

```bash
# Удаляем конфигурацию по умолчанию
sudo rm -f /etc/nginx/sites-enabled/default

# Создаём символическую ссылку (активирует наш сайт)
sudo ln -s /etc/nginx/sites-available/merchant-portal /etc/nginx/sites-enabled/

# Проверяем, что конфигурация Nginx корректна
sudo nginx -t
```

Вы должны увидеть:

```
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

> [!CAUTION]
> Если вы видите ошибку — **не перезапускайте** Nginx! Проверьте конфигурацию на опечатки. Частая ошибка — забыли заменить `ВАШ_ДОМЕН`.

### 11.3. Перезапуск Nginx

```bash
sudo systemctl restart nginx
```

### 11.4. Балансировщик или CDN перед nginx

Сервисы видят только nginx и сверяют с `TRUSTED_PROXIES` только его адрес. Если перед nginx стоит ещё
один прокси, `$remote_addr` в nginx — адрес этого прокси, и адреса всех клиентов схлопываются в один.
Настоящий адрес восстанавливает модуль realip самого nginx (в пакете Ubuntu он есть). Добавьте в блок
`server` из §11.1, до первого `location`:

```nginx
    # Адрес балансировщика или его подсеть; для нескольких — несколько строк
    set_real_ip_from 10.0.0.10;
    real_ip_header X-Forwarded-For;
    real_ip_recursive on;
```

После этого `$remote_addr` — адрес клиента, и строки `X-Real-IP $remote_addr` в блоках `location`
передают сервисам уже его. `TRUSTED_PROXIES` не меняйте: сервисам по-прежнему шлёт запросы nginx
с `127.0.0.1`. Доверять в `set_real_ip_from` можно только своим прокси: адрес из этого списка может
подставить любой `X-Forwarded-For`.

---

## 12. Шаг 10 — Настройка HTTPS (SSL-сертификат)

> [!IMPORTANT]
> Этот шаг обязателен для продакшн-серверов! HTTPS защищает данные пользователей (пароли, токены) от перехвата.

### 12.1. Установка Certbot

```bash
sudo apt install -y certbot python3-certbot-nginx
```

### 12.2. Получение SSL-сертификата

```bash
sudo certbot --nginx -d ВАШ_ДОМЕН
```

Certbot попросит:
1. **Email** — укажите рабочий email (для уведомлений об истечении сертификата)
2. **Согласие с условиями** — нажмите `A` (Agree)
3. **Рассылка** — нажмите `N` (No)
4. **Перенаправление** — выберите `2` (Redirect HTTP → HTTPS)

> [!NOTE]
> Для получения сертификата ваш домен должен быть настроен (DNS A-запись указывает на IP сервера), и порт 80 должен быть открыт.

### 12.3. Автоматическое продление

Certbot автоматически настраивает продление. Проверим:

```bash
sudo certbot renew --dry-run
```

Если видите `Congratulations` — всё настроено.

---

## 13. Шаг 11 — Настройка файрвола

```bash
# Разрешаем SSH (чтобы не потерять доступ!)
sudo ufw allow OpenSSH

# Разрешаем HTTP и HTTPS
sudo ufw allow 'Nginx Full'

# Включаем файрвол
sudo ufw enable

# Проверяем правила
sudo ufw status
```

> [!CAUTION]
> **ВНИМАНИЕ!** Всегда разрешайте SSH **ДО** включения файрвола, иначе вы потеряете доступ к серверу!

---

## 14. Шаг 12 — Проверка работоспособности

### 14.1. Проверяем бэкенд-сервисы

Выполните на сервере:

```bash
# Auth-сервис
curl -s http://127.0.0.1:9081/actuator/health | python3 -m json.tool

# Directory-сервис
curl -s http://127.0.0.1:9082/actuator/health | python3 -m json.tool

# PBL-сервис
curl -s http://127.0.0.1:9080/actuator/health | python3 -m json.tool

# ecom
curl -s http://127.0.0.1:9083/actuator/health | python3 -m json.tool
```

Порты `90xx` — порты actuator, а не рабочие порты сервисов; они доступны только с самого сервера, токен
не нужен (§19). Для каждого сервиса вы должны увидеть примерно это:

```json
{
    "status": "UP",
    "components": {
        "db": { "status": "UP" },
        "diskSpace": { "status": "UP" },
        "ping": { "status": "UP" }
    }
}
```

У `ecom` в `db` два источника: наша база (`dataSource`) и база шлюза (`txpgDataSource`). DOWN у второго —
неверные `ECOM_TXPG_*` или нет сетевого доступа к базе шлюза (§20.4).

Если ответ пустой или «connection refused» — сервис не поднялся; смотрите логи
(`sudo journalctl -u mp-auth -n 50`).

### 14.2. Проверяем фронтенд

Откройте в браузере:

```
https://ВАШ_ДОМЕН
```

Вы должны увидеть страницу входа (логин) Merchant Portal.

### 14.3. Swagger на время приёмки

Интерактивная документация API (Swagger UI) заявлена в [`technical_handover.md`](technical_handover.md) как
передаваемый артефакт, но **в постоянной эксплуатации она выключена**: `/v3/api-docs` —
это полная карта API, включая пути, которые снаружи знать незачем.

При выключенном флаге springdoc вообще не регистрирует эти эндпоинты: `/swagger-ui.html`
и `/v3/api-docs` отдают 404, а без токена — 401 (модель доступа «по умолчанию запрещено»).

**Включить на время приёмо-сдаточных испытаний:**

```bash
# 1. Включаем флаг в общем файле окружения
sudo sed -i 's/^SWAGGER_ENABLED=.*/SWAGGER_ENABLED=true/' /opt/merchant-portal/config/mp.env
sudo grep SWAGGER_ENABLED /opt/merchant-portal/config/mp.env

# 2. Перезапускаем сервисы
sudo systemctl restart mp-auth mp-directory mp-pbl mp-ecom

# 3. Проверяем с самого сервера
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/v3/api-docs   # ожидается 200
```

Открыть UI: `http://localhost:8081/swagger-ui.html` (auth), `:8082` (directory),
`:8080` (pbl), `:8083` (ecom). Через интернет он не откроется: nginx эти пути на сервисы не проксирует,
а рабочие порты закрыты файрволом (§13). С рабочего места пользуйтесь SSH-туннелем:

```bash
ssh -L 8081:localhost:8081 ПОЛЬЗОВАТЕЛЬ@ВАШ_СЕРВЕР
# и затем в браузере: http://localhost:8081/swagger-ui.html
```

**Выключить обратно после приёмки** (обязательный шаг, не забыть):

```bash
sudo sed -i 's/^SWAGGER_ENABLED=.*/SWAGGER_ENABLED=false/' /opt/merchant-portal/config/mp.env
sudo systemctl restart mp-auth mp-directory mp-pbl mp-ecom

# Проверяем, что документация закрылась
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/v3/api-docs   # ожидается 401
```

> [!NOTE]
> Флаг один на все сервисы и включает обе части сразу — и `/v3/api-docs`, и
> `/swagger-ui.html`. Разрешение этих путей в Spring Security привязано к тому же флагу:
> при `SWAGGER_ENABLED=false` для них не создаётся ни одного разрешающего правила.

### 14.4. Проверяем API через Nginx

```bash
# Проверка авторизации (должен вернуть ошибку 401 — это нормально, значит API работает)
curl -s -o /dev/null -w "%{http_code}" https://ВАШ_ДОМЕН/api/v1/users
# Ожидаемый ответ: 401 или 403
```

---

## 15. Обновление системы

Когда разработчики выпускают новую версию, выполните следующие шаги:

### 15.1. Обновление бэкенда

```bash
# 1. Переходим в директорию проекта и получаем обновления из Git
cd /opt/merchant-portal
git pull

# 2. Собираем новую версию
./gradlew clean build -x test

# 3. Смотрим, не появились ли новые переменные окружения с прошлого обновления
git diff 'HEAD@{1}' -- .env.example '*/src/main/resources/application.yaml'

# 4. Останавливаем сервисы
sudo systemctl stop mp-auth mp-directory mp-pbl mp-ecom

# 5. Копируем новые JAR-файлы
sudo cp auth/build/libs/auth-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/auth.jar
sudo cp directory/build/libs/directory-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/directory.jar
sudo cp pbl/build/libs/pbl-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/pbl.jar
sudo cp ecom/build/libs/ecom-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/ecom.jar

# 6. Запускаем сначала ecom и ждём в журнале строку «Started EcomApplication»
#    (нет строки — подождите несколько секунд и повторите вторую команду)
sudo systemctl start mp-ecom
sudo journalctl -u mp-ecom -n 30 --no-pager | grep 'Started EcomApplication'

# 7. Запускаем остальные — между собой порядок не важен, миграции обложены преконтролями
sudo systemctl start mp-auth mp-directory mp-pbl

# 8. Проверяем
sudo systemctl status mp-ecom mp-auth mp-directory mp-pbl
```

- **Шаг 3 — сверка `mp.env` с таблицей [§20.1](#201-первый-запуск-новой-установки).** Новая строка вида
  `${ПЕРЕМЕННАЯ}` без значения после двоеточия — новая обязательная переменная: допишите её в `mp.env`
  до запуска, иначе сервис не стартует (§20.4). Смысл и значения по умолчанию — таблица §20.1.
- **Шаги 6–7 — `ecom` не позже `directory`:** `directory` читает колонку
  `provider_terminals.terminal_rid`, которую добавляет миграция `ecom` (Р-96); на старой схеме заведение
  терминала и сверка статусов падают на запросе.

### 15.2. Обновление фронтенда

```bash
cd /opt/merchant-portal/frontend

npm ci
npm run build

sudo cp -r dist/* /var/www/merchant-portal/
sudo chown -R www-data:www-data /var/www/merchant-portal

# Nginx НЕ нужно перезапускать — он автоматически раздаёт новые файлы
```

---

## 16. Резервное копирование

> [!WARNING]
> Бэкап базы без ключа `CREDENTIALS_ENCRYPTION_KEY` восстанавливается не целиком: пароли компаний к
> провайдеру лежат в базе зашифрованными этим ключом, и с другим ключом их не расшифровать — каждой
> компании пароль придётся ввести заново. Храните копию `mp.env` (или хотя бы этот ключ) отдельно от
> бэкапов базы, в защищённом месте. `JWT_SECRET` восстанавливать не нужно: новый ключ лишь заставит
> пользователей войти заново.

### 16.1. Бэкап базы данных

Бэкап делает скрипт, который cron запускает от `root`. Пароль базы он берёт из `~/.pgpass` пользователя
`root`, а не из самого скрипта:

```bash
# 1. Пароль базы для pg_dump (формат: хост:порт:база:пользователь:пароль)
sudo touch /root/.pgpass
sudo chmod 600 /root/.pgpass
sudo tee /root/.pgpass > /dev/null << 'EOF'
localhost:5432:merchant_portal:postgres:ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ
EOF

# 2. Каталог для журнала бэкапа
sudo mkdir -p /var/log/merchant-portal

# 3. Скрипт бэкапа
sudo tee /opt/merchant-portal/backup.sh > /dev/null << 'EOF'
#!/bin/bash
# ═══════════════════════════════════════
# Бэкап базы данных Merchant Portal
# ═══════════════════════════════════════
set -o pipefail
# Бэкап читает только root: в нём хэши паролей пользователей и шифротексты паролей компаний
umask 077

BACKUP_DIR="/opt/merchant-portal/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/merchant_portal_${DATE}.sql.gz"

# Создаём директорию, если не существует
mkdir -p "$BACKUP_DIR"

# Создаём бэкап и сжимаем (пароль — из ~/.pgpass)
if ! pg_dump -U postgres -h localhost -d merchant_portal | gzip > "$BACKUP_FILE"; then
    echo "[$(date)] ОШИБКА: бэкап не создан"
    rm -f "$BACKUP_FILE"
    exit 1
fi

# Удаляем бэкапы старше 30 дней
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +30 -delete

echo "[$(date)] Бэкап создан: $BACKUP_FILE ($(du -sh "$BACKUP_FILE" | cut -f1))"
EOF
sudo chmod 700 /opt/merchant-portal/backup.sh

# 4. Автозапуск каждый день в 3:00 ночи — в cron пользователя root
(sudo crontab -l 2>/dev/null; echo "0 3 * * * /opt/merchant-portal/backup.sh >> /var/log/merchant-portal/backup.log 2>&1") | sudo crontab -
```

Проверить сразу: `sudo /opt/merchant-portal/backup.sh`.

### 16.2. Восстановление из бэкапа

Дамп восстанавливается в **пустую** базу: залитый поверх существующих таблиц, он даст ошибки
`already exists` и дубликаты. Поэтому базу сначала пересоздают:

```bash
# Останавливаем сервисы
sudo systemctl stop mp-auth mp-directory mp-pbl mp-ecom

# Пересоздаём базу — все текущие данные в ней будут удалены
sudo -u postgres dropdb merchant_portal
sudo -u postgres createdb merchant_portal

# Восстанавливаем базу; при первой ошибке psql остановится
sudo gunzip -c /opt/merchant-portal/backups/merchant_portal_ДАТА.sql.gz | \
  sudo -u postgres psql -d merchant_portal -v ON_ERROR_STOP=1

# Запускаем обратно (порядок не важен)
sudo systemctl start mp-auth mp-directory mp-pbl mp-ecom
```

`mp.env` при этом должен содержать тот же `CREDENTIALS_ENCRYPTION_KEY`, что был при создании бэкапа (см. выше).

---

## 17. Мониторинг и логи

### 17.1. Просмотр логов

Логи сервисов пишутся только в journald (systemd): в файлы сервисы не пишут. Сколько журнала хранится,
задают настройки journald (`SystemMaxUse=` в `/etc/systemd/journald.conf`). В `/var/log/merchant-portal/`
лежит только журнал бэкапа (§16.1).

```bash
# Логи конкретного сервиса (в реальном времени)
sudo journalctl -u mp-auth -f
sudo journalctl -u mp-directory -f
sudo journalctl -u mp-pbl -f
sudo journalctl -u mp-ecom -f

# Логи за последний час
sudo journalctl -u mp-auth --since "1 hour ago"

# Все логи за сегодня
sudo journalctl -u mp-auth --since today

# Логи Nginx (ошибки)
sudo tail -f /var/log/nginx/error.log

# Логи Nginx (все запросы)
sudo tail -f /var/log/nginx/access.log

# Всё по одному запросу или одному прогону планировщика — по traceId (он же в заголовке ответа X-Trace-Id)
sudo journalctl -u 'mp-*' --since today | grep '\[3f9c2a1b\]'

# Сигналы для мониторинга: неизвестный исход денежной операции (pbl), потерянная запись журнала (все сервисы)
sudo journalctl -u 'mp-*' --since today | grep 'PAYMENT_OUTCOME_UNKNOWN\|AUDIT_WRITE_FAILED'
```

Формат строки: `время [поток] [traceId] [адрес клиента] [пользователь] УРОВЕНЬ логгер - сообщение`. У
запроса `traceId` — 8 символов (или значение заголовка `X-Trace-Id` / `X-Correlation-Id`, если его прислал
клиент), у прогона планировщика — имя и 8 символов (`tx-reconcile-3f9c2a1b`), на старте — `system`; вне
запроса адрес и пользователь — `-`. ERROR означает, что нужен человек: неизвестный исход денежной
операции, сбой, потерянная запись журнала. Запросы к провайдеру и его ответы целиком пишутся на DEBUG;
включить на время разбора — переменной `LOGGING_LEVEL_AZ_MILLIKART_PBL_PROVIDER=DEBUG` в
`/opt/merchant-portal/config/mp.env` и перезапуском `mp-pbl` (пароль заказа маскируется и там).

### 17.2. Health-check эндпоинты

Каждый сервис имеет встроенный эндпоинт для проверки здоровья:

| Сервис | URL | Что показывает |
|--------|-----|----------------|
| Auth | `http://127.0.0.1:9081/actuator/health` | Статус сервиса + подключение к БД |
| Directory | `http://127.0.0.1:9082/actuator/health` | Статус сервиса + подключение к БД |
| PBL | `http://127.0.0.1:9080/actuator/health` | Статус сервиса + подключение к БД |
| ecom | `http://127.0.0.1:9083/actuator/health` | Статус сервиса + подключение к нашей базе и к базе шлюза |

Порты actuator и почему они без токена — §19. Кроме `health` открыты `info` и `metrics` — например,
`curl -s http://127.0.0.1:9081/actuator/metrics`.

### 17.3. Мониторинг ресурсов сервера

```bash
# Использование памяти
free -h

# Использование диска
df -h

# Использование CPU и памяти процессами
htop    # (если установлен) или top

# Размер базы данных
sudo -u postgres psql -c "SELECT pg_size_pretty(pg_database_size('merchant_portal'));"
```

---

## 18. Устранение неполадок

### ❌ Проблема: Сервис не запускается

```bash
# Смотрим подробные логи
sudo journalctl -u mp-auth -n 100 --no-pager
```

Сообщения, которыми сервис объясняет отказ стартовать, и что с ними делать, — таблица §20.4.

**Частые причины:**
- `Connection refused` → PostgreSQL не запущен: `sudo systemctl start postgresql`
- `Password authentication failed` → Неверный `DB_PASSWORD` в `mp.env`
- `Port already in use` → Порт занят другим процессом: `sudo lsof -i :8081`
- `java: command not found` → Java не установлена или неверный путь

### ❌ Проблема: «502 Bad Gateway» в браузере

Это означает, что Nginx не может связаться с бэкенд-сервисом.

1. Проверьте, запущены ли сервисы: `sudo systemctl status mp-auth mp-directory mp-pbl mp-ecom`
2. Если `inactive (dead)` — запустите: `sudo systemctl start mp-auth`
3. Проверьте порты: `ss -tlnp | grep -E '8080|8081|8082|8083'`

### ❌ Проблема: Страница логина не открывается

1. Проверьте Nginx: `sudo systemctl status nginx`
2. Проверьте файлы фронтенда: `ls /var/www/merchant-portal/index.html`
3. Проверьте файрвол: `sudo ufw status`
4. Проверьте DNS: `nslookup ВАШ_ДОМЕН`

### ❌ Проблема: «401 Unauthorized» после логина

1. Проверьте, что `JWT_SECRET` один на все сервисы (§8.3) и что после его смены перезапущены все четыре
2. Проверьте, что Auth-сервис работает: `curl http://127.0.0.1:9081/actuator/health`

### ❌ Проблема: Ошибка Liquibase при запуске

```
Liquibase: Validation Failed
```

Это значит, что структура базы данных не совпадает с ожидаемой. Варианты решения:
1. **Для первого запуска** — база данных должна быть пустой
2. **Для обновления** — не трогайте базу руками, Liquibase сам применит миграции

### ❌ Проблема: Нехватка памяти

Если сервер «зависает» или процессы убиваются:

```bash
# Проверяем память
free -h

# Уменьшаем потребление памяти Java (в systemd-юнитах)
# Измените -Xmx512m на -Xmx256m
sudo nano /etc/systemd/system/mp-auth.service

# После изменений:
sudo systemctl daemon-reload
sudo systemctl restart mp-auth
```

---

## 19. Справочник: порты и сервисы

### Внутренние порты (только внутри сервера)

| Порт | Сервис | Протокол |
|------|--------|----------|
| `5432` | PostgreSQL | TCP |
| `8080` | PBL (Pay-By-Link) | HTTP |
| `8081` | Auth (Авторизация) | HTTP |
| `8082` | Directory (Справочник) | HTTP |
| `8083` | ecom (выписка E-commerce, сводка главной) | HTTP |

Сервисы слушают эти порты на всех интерфейсах; снаружи их закрывает файрвол (§13) — не открывайте их.
Весь внешний трафик идёт через nginx.

### Порты actuator (только с самой машины, `127.0.0.1`)

| Порт | Сервис | Переменная | Что отдаёт |
|------|--------|------------|------------|
| `9081` | Auth | `MANAGEMENT_PORT` | `/actuator/health`, `/info`, `/metrics` |
| `9082` | Directory | `MANAGEMENT_PORT` | то же |
| `9080` | PBL | `MANAGEMENT_PORT` | то же |
| `9083` | ecom | `MANAGEMENT_PORT` | то же |

Actuator вынесен на отдельный порт, привязанный к `127.0.0.1`: снаружи сервера он недоступен физически.
Эти порты защищает привязка адреса, а не токен, — поэтому пробы мониторинга работают без авторизации,
а детали (состояние баз, свободное место на дисках) видны только с самой машины. На рабочих портах
(8080–8083) путь `/actuator/**` не обслуживается и отдаёт 404 — это нормально. Через nginx actuator
не проксируется.

### Внешние порты (доступны из интернета)

| Порт | Сервис | Назначение |
|------|--------|------------|
| `22` | SSH | Удалённый доступ |
| `80` | Nginx | HTTP (перенаправляет на 443) |
| `443` | Nginx | HTTPS (основной) |

### API-маршруты

| Маршрут | Бэкенд | Описание |
|---------|--------|----------|
| `/api/v1/auth/**` | Auth (:8081) | Вход, смена пароля, обновление токена, выход |
| `/api/v1/users/**` | Auth (:8081) | Управление пользователями |
| `/api/v1/companies/**` | Directory (:8082) | Управление компаниями |
| `/api/v1/terminals/**` | Directory (:8082) | Управление терминалами |
| `/api/v1/audit-logs/**` | Directory (:8082) | Журнал аудита |
| `/api/v1/payment-links/**` | PBL (:8080) | Платёжные ссылки, страницы плательщика (`/open`, `/redirect`) |
| `/api/v1/transactions/**` | PBL (:8080) | Транзакции |
| `/api/v1/dashboard/**` | PBL (:8080) | Статистика оплат по ссылкам (вкладка «Статистика» страницы Pay by Link) |
| `/api/v1/acquiring/**` | PBL (:8080) | Проверка терминала у провайдера (кнопка «Тест») |
| `/api/v1/ecom/**` | ecom (:8083) | Выписка E-commerce, сводка главной, справочник терминалов провайдера |

### Конфигурационные файлы

| Файл | Назначение |
|------|------------|
| `/opt/merchant-portal/config/mp.env` | Переменные окружения всех сервисов (§8.3) |
| `/etc/nginx/sites-available/merchant-portal` | Настройки веб-сервера |
| `/etc/systemd/system/mp-auth.service` | Systemd-юнит Auth |
| `/etc/systemd/system/mp-directory.service` | Systemd-юнит Directory |
| `/etc/systemd/system/mp-pbl.service` | Systemd-юнит PBL |
| `/etc/systemd/system/mp-ecom.service` | Systemd-юнит ecom |
| `/opt/merchant-portal/backup.sh` | Скрипт бэкапа (§16.1) |
| `/root/.pgpass` | Пароль базы для бэкапа (§16.1) |

### Директории

| Путь | Содержимое |
|------|------------|
| `/opt/merchant-portal/` | Исходный код |
| `/opt/merchant-portal/deploy/` | Запускаемые JAR-файлы |
| `/opt/merchant-portal/config/` | Файл окружения `mp.env` |
| `/opt/merchant-portal/backups/` | Бэкапы базы данных |
| `/var/www/merchant-portal/` | Собранный фронтенд (HTML/CSS/JS) |
| `/var/log/merchant-portal/` | Журнал бэкапа `backup.log`; логи сервисов — в journald (§17.1) |
| `/var/log/nginx/` | Логи Nginx |

---

## 20. Первый запуск и ротация ключа

Раздел про вещи, которые делаются руками и в определённом порядке: как поднять **новую** установку,
как **сменить** ключ подписи JWT на работающей и как вернуть заблокированного администратора.
Оркестратора и хранилища секретов в проекте нет (Р-13): сервисы запускает systemd с переменными из
`mp.env`, а защита от забытой переменной встроена в сами сервисы — без обязательной переменной сервис
не стартует и называет её (§20.4).

### 20.1. Первый запуск новой установки

1. **Переменные и ключи** — `mp.env` по §8.3; `JWT_SECRET` и `CREDENTIALS_ENCRYPTION_KEY` генерируются
   там же. Требования к ключу подписи проверяются на старте: непустой, не короче 32 байт (HS256
   подписывает 256-битным хешем), не известный скомпрометированный ключ из истории репозитория — он
   отвергается по SHA-256.
2. **Юниты systemd** — §9.2–9.4a.
3. **Запуск** — §9.5: `auth`, `directory` и `pbl` в любом порядке, `ecom` — после `directory`; на свежей
   установке `auth` перезапускают один раз, чтобы появился внешний ключ `fk_terminals_company` (там же).
4. **Первый администратор** — ниже.
5. **Справочники провайдера.** Логины мультимерчантов и терминалы провайдера `ecom` снимает раз в
   15 минут (`ECOM_TERMINAL_SYNC_CRON`); до первого прохода компанию и терминал не завести. Не ждать —
   кнопка «Обновить справочник» в форме компании или терминала ([`admin_guide.md`](admin_guide.md) §4.1).

#### Один раз создать администратора

Миграция администратора **не заводит**: пароль, лежащий в changeset, одинаков на всех установках и виден
каждому, у кого есть репозиторий. Первый `SYSTEM_ADMIN` создаётся разовым запуском `auth`:

```bash
sudo systemctl stop mp-auth

sudo tee -a /opt/merchant-portal/config/mp.env > /dev/null << 'EOF'
BOOTSTRAP_ADMIN_USERNAME=admin@your-company.az
BOOTSTRAP_ADMIN_PASSWORD=Portal-Start-2026!
EOF
sudo sed -i 's/^AUTH_BOOTSTRAP_ENABLED=false/AUTH_BOOTSTRAP_ENABLED=true/' /opt/merchant-portal/config/mp.env

sudo systemctl start mp-auth
```

Логин и пароль в примере замените своими. `daemon-reload` не нужен: `mp.env` перечитывается при каждом
старте сервиса.

В журнале должна появиться строка уровня WARN — пароль в неё не попадает:

```bash
sudo journalctl -u mp-auth -n 100 | grep "Admin bootstrap"
# Admin bootstrap created SYSTEM_ADMIN 'admin@your-company.az' (id=…). Set AUTH_BOOTSTRAP_ENABLED=false …
```

Требования к паролю — те же, что и у любого пользователя портала (PCI-DSS v4.0): минимум 12 символов,
**латинские** заглавная и строчная буквы, цифра и спецсимвол (например `!`, `-`, `_`). Кириллица
за букву не считается. Слабый пароль — сервис не стартует. Логин обязан быть адресом электронной почты:
сервер при входе проверяет формат, и учётная запись с другим логином никогда не смогла бы войти.

При первом входе портал попросит сменить пароль из `BOOTSTRAP_ADMIN_PASSWORD` на свой (PCI DSS 8.3.5,
Р-100): тот знает каждый, кто видел файл окружения. Проверьте, что вход работает, и **сразу выключите флаг**:

```bash
sudo sed -i 's/^AUTH_BOOTSTRAP_ENABLED=true/AUTH_BOOTSTRAP_ENABLED=false/' /opt/merchant-portal/config/mp.env
sudo sed -i '/^BOOTSTRAP_ADMIN_PASSWORD=/d' /opt/merchant-portal/config/mp.env
sudo systemctl restart mp-auth
```

> [!NOTE]
> Забыть выключить флаг не опасно: раннер работает только на **пустой** таблице `users`.
> При следующем старте он увидит существующих пользователей, напишет в лог, что пропускает
> себя, и ничего не тронет. Но пароль администратора останется лежать в файле окружения —
> ради этого строку и удаляют.

#### Переменные окружения — полный перечень

Все переменные, которые читают сервисы. Значения задаются в `mp.env` (§8.3).
**обяз.** — обязательна, сервис без неё не стартует; **+** — читается, есть значение по умолчанию;
**—** — сервис её не читает. Лишняя переменная в общем файле ничему не мешает — кроме `MANAGEMENT_PORT`
и `SERVER_PORT`, см. их строки.

**Общие**

| Переменная | auth | directory | pbl | ecom | По умолчанию и смысл |
|---|:---:|:---:|:---:|:---:|---|
| `DB_PASSWORD` | **обяз.** | **обяз.** | **обяз.** | **обяз.** | нет — пароль пользователя PostgreSQL (§5.1) |
| `JWT_SECRET` | **обяз.** | **обяз.** | **обяз.** | **обяз.** | нет — ключ подписи токенов, не короче 32 байт; одно значение во всех четырёх сервисах (§8.3) |
| `DB_URL` | + | + | + | + | `jdbc:postgresql://localhost:5432/postgres`; в этой установке — `…/merchant_portal` (§8.3) |
| `DB_USERNAME` | + | + | + | + | `postgres` (отдельная роль приложения — §5.1a) |
| `JWT_EXPIRATION_MS` | + | + | + | + | `900000` (15 минут, P1-13) — срок access-токена. Его вписывает в токен `auth`; `directory`, `pbl` и `ecom` токен только проверяют. Это же — верхняя граница, сколько после выхода, блокировки или удаления пользователь ещё имеет доступ |
| `TRUSTED_PROXIES` | + | + | + | + | `127.0.0.1,::1` — прокси, чьим заголовкам с адресом клиента сервис верит (этот nginx, §11.1). Балансировщик перед nginx сюда не добавляют — §11.4 |
| `SWAGGER_ENABLED` | + | + | + | + | `false`; `true` — только на время приёмки (§14.3) |
| `MANAGEMENT_PORT` | + | + | + | + | порт actuator: auth `9081`, directory `9082`, pbl `9080`, ecom `9083` (§19). **В `mp.env` не добавлять**: его читают все четыре, одно значение на всех — конфликт портов. Менять — только в юните одного сервиса строкой `Environment=MANAGEMENT_PORT=…` |
| `SERVER_PORT` | + | + | + | + | рабочий порт: auth `8081`, directory `8082`, pbl `8080`, ecom `8083`. **В `mp.env` не добавлять**: объявлен только у `ecom`, но Spring применяет его к порту любого сервиса, и все четыре попытаются занять один. Менять — в юните одного сервиса и в `upstream` nginx (§11.1) |
| `LOGGING_LEVEL_AZ_MILLIKART_PBL_PROVIDER` | — | — | + | — | не задана (`INFO`); `DEBUG` — на время разбора обмена с провайдером (§17.1) |

**auth**

| Переменная | auth | directory | pbl | ecom | По умолчанию и смысл |
|---|:---:|:---:|:---:|:---:|---|
| `AUTH_BOOTSTRAP_ENABLED` | + | — | — | — | `false`; `true` — на один запуск, чтобы создать первого администратора (выше) |
| `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD` | **обяз.** при включённом bootstrap | — | — | — | нет — логин (адрес почты) и пароль первого администратора |
| `LOGIN_RATE_LIMIT_ENABLED`, `LOGIN_RATE_LIMIT_MAX_FAILURES`, `LOGIN_RATE_LIMIT_WINDOW` | + | — | — | — | `true`, `10`, `PT15M` — лимит неудачных попыток входа с одного адреса |
| `AUTH_REFRESH_TTL` | + | — | — | — | `PT20M` — срок refresh-токена, скользящий: граница простоя на сервере (PCI DSS 8.2.8, Р-99). **Не меньше 20 минут:** фронтенд выходит после 15 минут простоя и, пока пользователь работает, обновляет токены не чаще раза в 5 минут (`frontend/src/app/auth/idle.ts`), — меньший срок обрывает живую сессию |
| `AUTH_REFRESH_ROTATION_GRACE` | + | — | — | — | `PT10S` — окно, в котором повтор заменённого refresh-токена — гонка вкладок, а не кража |
| `AUTH_REFRESH_CLEANUP_ENABLED`, `AUTH_REFRESH_CLEANUP_CRON` | + | — | — | — | `true`, `0 30 3 * * *` — уборка просроченных refresh-токенов |
| `AUTH_INACTIVITY_ENABLED`, `AUTH_INACTIVITY_MAX_IDLE`, `AUTH_INACTIVITY_CRON` | + | — | — | — | `true`, `P90D`, `0 45 3 * * *` — блокировка учёток без активности (PCI DSS 8.2.6, Р-101); больше 90 дней стандарт не разрешает |

**directory и pbl**

| Переменная | auth | directory | pbl | ecom | По умолчанию и смысл |
|---|:---:|:---:|:---:|:---:|---|
| `CREDENTIALS_ENCRYPTION_KEY` | — | **обяз.** | **обяз.** | — | нет — ключ AES-256 паролей компаний к провайдеру, 32 байта в base64, одно значение в `directory` и `pbl` (Р-93). Не терять и не менять (§8.3, §16) |
| `DIRECTORY_TERMINAL_RECONCILIATION_ENABLED`, `DIRECTORY_TERMINAL_RECONCILIATION_CRON` | — | + | — | — | `true`, `0 */15 * * * *` — сверка статусов терминалов со справочником провайдера |
| `PBL_BASE_URL` | — | — | **обяз.** | — | нет — публичный адрес портала, уходит эквайеру как адрес возврата плательщика (P1-10, §8.3) |
| `PBL_PROVIDER_GATEWAY_BASE_URL` | — | — | **обяз.** | — | нет — адрес шлюза эквайера (страница оплаты), выдаёт MilliKart; не-HTTPS даёт WARN |
| `PBL_PROVIDER_API_BASE_URL` | — | — | **обяз.** | — | нет — адрес e-commerce API эквайера, выдаёт MilliKart; не-HTTPS даёт WARN |
| `PBL_PROVIDER_CREATE_ORDER_PATH`, `PBL_PROVIDER_EXEC_TRAN_PATH`, `PBL_PROVIDER_GET_ORDER_PATH` | — | — | + | — | `/order`, `/order/{orderId}/exec-tran`, `/order/{orderId}` — пути протокола эквайера, менять не нужно |
| `PBL_API_TOKEN_ENABLED` | — | — | + | — | `false` — статический токен с ролью `SYSTEM_ADMIN` без пароля; не включать |
| `PBL_API_TOKEN` | — | — | **обяз.** при включённом флаге | — | пусто |
| `PBL_DASHBOARD_ZONE` | — | — | + | — | `Asia/Baku` — часовой пояс статистики оплат по ссылкам (вкладка «Статистика» страницы Pay by Link). Пояс главной и выписки — `ECOM_TXPG_ZONE` |
| `PBL_LINK_DEFAULT_TTL` | — | — | + | — | `PT24H` — срок ссылки, если при создании он не задан |
| `PBL_LINK_MAX_TTL` | — | — | + | — | `P90D` — потолок срока ссылки, считается от её создания |
| `PBL_RECONCILIATION_ENABLED`, `PBL_RECONCILIATION_CRON` | — | — | + | — | `true`, `0 */2 * * * *` — фоновая сверка зависших `PENDING` с эквайером |
| `PBL_RECONCILIATION_MIN_AGE`, `PBL_RECONCILIATION_MAX_AGE`, `PBL_RECONCILIATION_GIVE_UP_AGE`, `PBL_RECONCILIATION_BATCH_SIZE` | — | — | + | — | `PT2M`, `PT24H`, `P7D`, `50`: моложе `MIN_AGE` операция не опрашивается; неоплаченная старше `MAX_AGE` становится `FAILED` (только при статусе эквайера `Preparing`); старше `GIVE_UP_AGE` не опрашивается — ручной разбор; `BATCH_SIZE` — сколько операций за проход |

**ecom**

| Переменная | auth | directory | pbl | ecom | По умолчанию и смысл |
|---|:---:|:---:|:---:|:---:|---|
| `ECOM_TXPG_URL`, `ECOM_TXPG_USERNAME`, `ECOM_TXPG_PASSWORD` | — | — | — | **обяз.** | нет — база платёжного шлюза и учётная запись только на чтение, выдаёт MilliKart |
| `ECOM_TXPG_SCHEMA` | — | — | — | + | `TXPG` — схема шлюза, из которой читаем |
| `ECOM_TXPG_ZONE` | — | — | — | + | `Asia/Baku` — пояс, в котором база шлюза хранит даты; в нём же режутся периоды выписки и главной. Ошибка сдвигает всю выписку на разницу поясов |
| `ECOM_TXPG_MAX_WINDOW` | — | — | — | + | `P92D` — самый длинный период одного запроса выписки и главной |
| `ECOM_TXPG_MAX_PAGE_SIZE` | — | — | — | + | `200` — потолок размера страницы выписки |
| `ECOM_TXPG_STATUS_SCAN_LIMIT` | — | — | — | + | `1000` — сколько заказов страница с фильтром по статусу просматривает за один запрос (Р-87) |
| `ECOM_TXPG_QUERY_TIMEOUT` | — | — | — | + | `PT30S` — потолок времени одного запроса к базе шлюза (nginx ждёт `ecom` 90 секунд, §11.1) |
| `ECOM_TXPG_FETCH_SIZE` | — | — | — | + | `200` — строк за одно обращение к базе шлюза |
| `ECOM_TXPG_POOL_SIZE` | — | — | — | + | `4` — соединений к базе шлюза; мало намеренно: каждое отнимает ресурс у боевых авторизаций |
| `ECOM_TXPG_MISSING_RUNS` | — | — | — | + | `3` — сколько обновлений подряд терминал должен отсутствовать у провайдера, чтобы у нас выключиться (Р-66) |
| `ECOM_TERMINAL_SYNC_ENABLED`, `ECOM_TERMINAL_SYNC_CRON` | — | — | — | + | `true`, `0 */15 * * * *` — обновление справочников терминалов и логинов провайдера |

Расписания всех фоновых процессов — [`application_description.md`](application_description.md) §9.

### 20.2. Ротация ключа подписи

Ключ меняют планово или после любого подозрения, что он утёк: попал в лог, в переписку,
в скриншот, в чужие руки вместе с бэкапом конфигурации.

```bash
# 1. Новый ключ
NEW_SECRET=$(openssl rand -base64 48)

# 2. Остановить ВСЕ сервисы
sudo systemctl stop mp-auth mp-directory mp-pbl mp-ecom

# 3. Заменить значение в общем файле окружения
sudo sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${NEW_SECRET}|" /opt/merchant-portal/config/mp.env
sudo grep JWT_SECRET /opt/merchant-portal/config/mp.env   # убедиться, что значение одно и новое

# 4. Поднять обратно (порядок не важен — миграции уже применены)
sudo systemctl start mp-auth mp-directory mp-pbl mp-ecom
```

> [!CAUTION]
> **Все выданные access-токены станут недействительными.** Чёрного списка и версионирования
> ключей в системе нет, а при ручном запуске нет и промежутка, когда старый и новый ключ
> приняты одновременно. На страницу входа пользователей это не выбрасывает (P1-13):
> refresh-токен не подписан ключом (в базе лежит его SHA-256), поэтому первый же запрос
> с 401 заставит фронтенд вызвать `/refresh`, `auth` выпустит access-токен уже новым ключом,
> и запрос повторится сам. Пользователь заметит только паузу на время перезапуска сервисов;
> заново войти придётся лишь тому, у кого истёк или отозван refresh-токен.
> Планируйте ротацию на время наименьшей нагрузки — сами перезапуски дают простой.

> [!WARNING]
> Останавливать нужно **все** сервисы, а не перезапускать по одному. Сервис со старым
> ключом и сервис с новым не понимают токены друг друга: пока идёт «плавный» перезапуск,
> часть запросов будет получать 401 без всякой закономерности.

Пароль базы данных (`DB_PASSWORD`) меняется в трёх местах — в PostgreSQL, в `mp.env` и в `/root/.pgpass`
(§16.1). Выданных токенов это не затрагивает и пользователей из портала не выбрасывает:

```bash
# 1. Новый пароль — запишите значение
openssl rand -base64 24

# 2. Остановить все сервисы
sudo systemctl stop mp-auth mp-directory mp-pbl mp-ecom

# 3. Сменить пароль в PostgreSQL (psql спросит его дважды)
sudo -u postgres psql -c "\password postgres"

# 4. Заменить значение DB_PASSWORD в mp.env и пароль в конце строки /root/.pgpass
sudo nano /opt/merchant-portal/config/mp.env
sudo nano /root/.pgpass

# 5. Поднять обратно
sudo systemctl start mp-auth mp-directory mp-pbl mp-ecom
```

### 20.3. Заблокирован единственный администратор

Учётку без активности 90 дней — в неё не входили и её сессию не обновляли — `auth` блокирует сам
(PCI DSS 8.2.6, Р-101), и администраторы не исключение. Если заблокирован единственный `SYSTEM_ADMIN`,
разблокировать его через портал некому — вернуть в базе, указав его логин:

```bash
sudo -u postgres psql -d merchant_portal -c "UPDATE users SET status = 'ACTIVE', last_activity_at = now() \
  WHERE username = 'admin@your-company.az' AND status = 'BLOCKED';"
```

Запись в журнал аудита такая правка не оставит — отметьте её во внутреннем журнале работ. Чтобы этого не
случалось, держите двух администраторов и входите хотя бы раз в квартал.

### 20.4. Если сервис не стартует

| Сообщение при старте | Что произошло | Что делать |
|---|---|---|
| `The environment variable JWT_SECRET is not set` | Переменной нет в окружении процесса | Задать её; для systemd — проверить `EnvironmentFile=` в юните |
| `JWT signing secret is not set` | Переменная есть, но пустая (`JWT_SECRET=`) | Записать ключ: `openssl rand -base64 48` (§8.3) |
| `The environment variable DB_PASSWORD is not set` | То же для пароля БД | Задать её |
| `The environment variable CREDENTIALS_ENCRYPTION_KEY is not set` | Нет ключа паролей компаний (`directory`, `pbl`) | Задать: `openssl rand -base64 32`, одно значение на оба сервиса. Если пароли компаний уже сохранены — вернуть прежний ключ (§16) |
| `Credentials encryption key is not set` | Переменная есть, но пустая | То же |
| `Credentials encryption key is not valid base64` | Значение испорчено: обрезано, лишние символы | Вернуть исходное значение; новый ключ — только на установке без сохранённых паролей компаний |
| `Credentials encryption key is N bytes, AES-256 requires exactly 32` | Ключ не той длины | Сгенерировать заново: `openssl rand -base64 32` |
| `JWT signing secret is too short: N bytes` | Ключ короче 32 байт | Сгенерировать заново: `openssl rand -base64 48` |
| `JWT signing secret is the key that leaked into this repository's git history` | Подставлен известный публичный ключ | Сгенерировать новый; этот использовать нельзя |
| `BOOTSTRAP_ADMIN_PASSWORD does not satisfy the password policy` | Пароль администратора слабее политики | 12+ символов, латинские заглавная и строчная, цифра, спецсимвол |
| `BOOTSTRAP_ADMIN_USERNAME must be an email address` | Логин администратора — не адрес почты | Задать адрес почты: с другим логином войти нельзя |
| `auth.bootstrap.enabled is true but BOOTSTRAP_ADMIN_USERNAME and/or BOOTSTRAP_ADMIN_PASSWORD is not set` | Флаг включён, а данных администратора нет | Задать обе переменные либо выключить флаг |
| `pbl.security.api-token-enabled is true but pbl.security.api-token is empty` | Включён статический токен без значения | Выключить `PBL_API_TOKEN_ENABLED` (обычно это и нужно) |
| `The environment variable PBL_BASE_URL is not set` (то же для `PBL_PROVIDER_GATEWAY_BASE_URL`, `PBL_PROVIDER_API_BASE_URL`) | Адреса нет в окружении `pbl` | Задать её в `mp.env` (§8.3); дефолта нет намеренно |
| `The environment variable PBL_BASE_URL (property pbl.base-url) is empty` / `has leading or trailing whitespace` / `is not a valid URL` / `must be an absolute URL` / `has a host part that is not a valid host name` / `must use http or https` | Переменная есть, но значение не годится (пусто, пробел или CRLF на конце, незаменённый `ВАШ_ДОМЕН`, относительный путь, `ftp://`) | Задать абсолютный `https://` адрес без лишних пробелов, например `https://ВАШ_ДОМЕН/` с реальным доменом |
| `The environment variable ECOM_TXPG_URL is not set: ecom has no address for the provider gateway database` (то же для `ECOM_TXPG_USERNAME` — `user name`, `ECOM_TXPG_PASSWORD` — `password`) | Нет доступа к базе шлюза в окружении `ecom` | Задать переменные в `mp.env` (§8.3); значения выдаёт MilliKart |
| `Migration failed for changeset …002-ecom-terminal-status-source…` с причиной `relation "terminals" does not exist` | `ecom` запущен на пустой базе раньше `directory` и `pbl` | Запустить `mp-directory`, затем снова `mp-ecom`: `sudo systemctl reset-failed mp-ecom && sudo systemctl start mp-ecom` (§9.5) |
| `Could not resolve placeholder '…'` (например `'auth.inactivity.max-idle'`) | Сервис запущен с внешней копией `application.yaml` (`--spring.config.location`), в которой нет свойства из новой версии | Удалить копию и параметр из юнита (§8.1), `daemon-reload`, запустить |
| `WARNING: the acquirer address is not HTTPS` (в рамке, сервис стартует) | Адрес шлюза или API эквайера задан по `http://` | Это не ошибка конфигурации: HTTPS даёт MilliKart. Запросить у них `https://` адрес и заменить переменную |
| `WARNING: the public address of this service is not HTTPS` (в рамке, сервис стартует) | `PBL_BASE_URL` по `http://` на не-локальном хосте | В проде — `https://ВАШ_ДОМЕН/` (раздел 12); для `localhost`/`127.0.0.1`/`[::1]` предупреждения нет |
| Логин проходит, но `directory`, `pbl` или `ecom` отвечают 401 | `JWT_SECRET` различается между сервисами | Одно значение на все (§8.3), перезапустить все сервисы |
| `ecom` работает, но health DOWN у `txpgDataSource` (§14.1), в журнале ERROR `Provider terminal sync skipped: gateway unavailable: …` или `gateway query failed: …` (то же для `Provider login sync`) | Неверные `ECOM_TXPG_URL`, `ECOM_TXPG_USERNAME`, `ECOM_TXPG_PASSWORD` или `ECOM_TXPG_SCHEMA`, нет прав на чтение либо сетевого доступа к базе шлюза. Сервис стартует — соединение с базой шлюза открывается при первом запросе | Исправить переменные в `mp.env` или доступ и перезапустить `mp-ecom`. Пока справочники не обновляются, новые компании и терминалы не заводятся, а выписка и главная не открываются |
| Платёж проходит у эквайера, но плательщик возвращается не туда / транзакция висит в PENDING | `PBL_BASE_URL` указывает не на этот сервис (чужой домен, опечатка) | Исправить `PBL_BASE_URL` в `mp.env` и перезапустить `mp-pbl`; сервис такое не роняет, см. §8.3 |

---

> [!TIP]
> **Быстрая шпаргалка** для повседневного использования:
> ```bash
> # Перезапустить всё
> sudo systemctl restart mp-auth mp-directory mp-pbl mp-ecom nginx
> 
> # Проверить всё
> sudo systemctl status mp-auth mp-directory mp-pbl mp-ecom nginx postgresql
> 
> # Посмотреть логи (последние 50 строк)
> sudo journalctl -u mp-auth -n 50
> sudo journalctl -u mp-directory -n 50
> sudo journalctl -u mp-pbl -n 50
> sudo journalctl -u mp-ecom -n 50
> 
> # Бэкап прямо сейчас
> sudo /opt/merchant-portal/backup.sh
> ```
