# 📦 Руководство по развёртыванию Merchant Portal (MP)

> **Для кого эта документация:** для тех, кто будет устанавливать систему на сервер. Знания программирования НЕ требуются — достаточно уметь подключаться к серверу и вводить команды.

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

---

## 1. Обзор системы

Merchant Portal — это веб-приложение, состоящее из **5 компонентов**:

| Компонент | Описание | Порт |
|-----------|----------|------|
| **auth** | Авторизация пользователей, JWT-токены | `8081` |
| **directory** | Справочник компаний и терминалов | `8082` |
| **pbl** | Pay-By-Link — платёжные ссылки, операции, сводка главной, проверка терминала у провайдера | `8080` |
| **ecom** | Выписка E-commerce и справочник терминалов провайдера — читает базу платёжного шлюза; ставится только там, где к ней есть доступ | `8083` |
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
> `ecom` вдобавок читает базу платёжного шлюза MilliKart (Oracle) — только на чтение. Без доступа к ней его не устанавливают: остальные сервисы работают и без него, просто вкладка E-commerce остаётся пустой.

---

## 2. Что нужно подготовить заранее

### Минимальные требования к серверу

| Параметр | Минимум | Рекомендуется |
|----------|---------|---------------|
| ОС | Ubuntu 22.04 LTS / CentOS 8+ | Ubuntu 24.04 LTS |
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
- **Только для `ecom`:** адрес базы платёжного шлюза, схема и учётная запись с правом только на чтение, а также сетевой доступ к этой базе с сервера — выдаёт MilliKart

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
> Все команды ниже предназначены для **Ubuntu/Debian**. Если у вас CentOS/RHEL — замените `apt` на `yum` или `dnf`.

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
# Добавляем репозиторий с Java
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

### 4.4. Установка Node.js 20

Node.js нужен для сборки фронтенда (веб-интерфейса).

```bash
# Устанавливаем Node.js 20 через NodeSource
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# Проверяем версии
node -v    # Должно быть v20.x.x
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
> **Обязательно** замените `ВАШ_НАДЁЖНЫЙ_ПАРОЛЬ` на настоящий сложный пароль! Запишите его — он понадобится дальше.
> 
> Пример хорошего пароля: `Mp$ecure_2026!Prod`
> 
> **НЕ ИСПОЛЬЗУЙТЕ** пароль `password` на продакшн-серверах!

### 5.1a. Роль приложения и права на журнал аудита (Р-42)

Журнал аудита (`audit_logs`) — доказательство того, что происходило в системе. Значит, само
приложение не должно уметь его править и удалять: строки только добавляются. В коде это уже так
(репозиторий журнала умеет единственное — `save`, у записи нет сеттеров), а в базе это делается
правами.

Заведите **обычную роль для приложения** — не суперпользователя — и выдайте ей права:

```sql
-- Роль, под которой работают сервисы портала. NOSUPERUSER и NOCREATEROLE — обязательно.
CREATE ROLE mp_app WITH LOGIN PASSWORD 'ПАРОЛЬ_ПРИЛОЖЕНИЯ' NOSUPERUSER NOCREATEDB NOCREATEROLE;

-- Работа со схемой и с обычными таблицами
GRANT CONNECT ON DATABASE merchant_portal TO mp_app;
GRANT USAGE ON SCHEMA public TO mp_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO mp_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO mp_app;

-- А журнал аудита — только добавление и чтение. Ни UPDATE, ни DELETE.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM mp_app;
GRANT SELECT, INSERT ON audit_logs TO mp_app;

-- То же самое для таблиц, которые появятся при следующих миграциях
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mp_app;
```

> [!WARNING]
> **Сегодня эти права ни на что не влияют.** Все сервисы подключаются к базе как `postgres`,
> то есть суперпользователем, а на суперпользователя `REVOKE` не действует — он обходит проверку
> прав целиком. Пока `DB_USERNAME` в `/opt/merchant-portal/config/mp.env` (раздел 8.3) не сменят с `postgres` на
> `mp_app`, защита журнала существует только в коде приложения.
>
> Смена пользователя базы затрагивает все сервисы и миграции сразу, поэтому она вынесена в
> отдельную работу и записана в известных ограничениях (`AGENTS.md`, раздел 10). Команды выше приведены здесь,
> чтобы их можно было выполнить сразу, как только до этого дойдут руки.

> [!NOTE]
> **Liquibase и права.** Миграции создают и меняют таблицы, а роль приложения такого права не
> имеет. Два рабочих варианта:
> 1. выдать `mp_app` право на схему (`GRANT CREATE ON SCHEMA public TO mp_app;`) — проще, но роль
>    приложения снова может создать таблицу и, например, подменить журнал;
> 2. прогонять миграции отдельным пользователем (тем же `postgres`) до старта сервисов, а сервисы
>    запускать под `mp_app` с выключенным Liquibase (`spring.liquibase.enabled=false`) — строже,
>    но требует отдельного шага при каждом обновлении.
>
> Пока сервисы ходят под `postgres`, выбирать не приходится; решать это нужно вместе со сменой
> `DB_USERNAME`.

### 5.2. Разрешение подключения по паролю

Откройте конфигурационный файл PostgreSQL:

```bash
sudo nano /etc/postgresql/16/main/pg_hba.conf
```

Найдите строку (ближе к концу файла):

```
local   all   all   peer
```

И замените `peer` на `md5`:

```
local   all   all   md5
```

Также найдите строку:

```
host    all   all   127.0.0.1/32   scram-sha-256
```

Убедитесь, что она выглядит так (или замените метод на `md5`):

```
host    all   all   127.0.0.1/32   md5
```

> **Как сохранить файл в nano:** нажмите `Ctrl+O`, затем `Enter`, затем `Ctrl+X` для выхода.

Перезапустите PostgreSQL:

```bash
sudo systemctl restart postgresql
```

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

# Копируем JAR-файлы
cp auth/build/libs/auth-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/auth.jar
cp directory/build/libs/directory-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/directory.jar
cp pbl/build/libs/pbl-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/pbl.jar
# Только если на этом сервере ставится ecom:
cp ecom/build/libs/ecom-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/ecom.jar
```

---

## 8. Шаг 6 — Настройка конфигурации

Каждый микросервис нужно настроить для работы с вашей базой данных и окружением.

### 8.1. Создание конфигурационных файлов

Создадим отдельные конфигурационные файлы для каждого сервиса:

#### Конфигурация Auth-сервиса

```bash
sudo mkdir -p /opt/merchant-portal/config

cat > /opt/merchant-portal/config/auth-application.yaml << 'EOF'
server:
  port: 8081

spring:
  application:
    name: auth
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/merchant_portal}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

auth:
  bootstrap:
    # Разовое создание первого администратора. Подробности — раздел 20.
    enabled: ${AUTH_BOOTSTRAP_ENABLED:false}
  login:
    rate-limit:
      # Лимит неудачных попыток входа с одного адреса (P3-Auth). Строки обязательны: у этих
      # настроек нет значений по умолчанию в коде, и без них сервис не стартует.
      enabled: ${LOGIN_RATE_LIMIT_ENABLED:true}
      max-failures: ${LOGIN_RATE_LIMIT_MAX_FAILURES:10}
      window: ${LOGIN_RATE_LIMIT_WINDOW:PT15M}
  refresh:
    # Refresh-токены (P1-12): срок, окно снисхождения ротации, уборка просроченных.
    ttl: ${AUTH_REFRESH_TTL:P30D}
    rotation-grace: ${AUTH_REFRESH_ROTATION_GRACE:PT10S}
    cleanup-enabled: ${AUTH_REFRESH_CLEANUP_ENABLED:true}
    cleanup-cron: "${AUTH_REFRESH_CLEANUP_CRON:0 30 3 * * *}"

mp:
  # Прокси, чьим заголовкам X-Real-IP / X-Forwarded-For сервис верит, — этот nginx (раздел 11).
  trusted-proxies: ${TRUSTED_PROXIES:127.0.0.1,::1}

pbl:
  security:
    jwt:
      secret: ${JWT_SECRET}
      # Срок access-токена: 15 минут (P1-13). Фронтенд обновляет его сам через /refresh;
      # это же — верхняя граница, сколько после выхода/блокировки пользователь ещё имеет доступ.
      expiration-ms: ${JWT_EXPIRATION_MS:900000}

management:
  server:
    # Actuator на отдельном порту, привязанном к 127.0.0.1: снаружи сервера он недоступен
    # физически. Порты: auth 9081, directory 9082, pbl 9080, ecom 9083. Здесь — auth.
    port: ${MANAGEMENT_PORT:9081}
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      # Можно оставить always: детали (состояние БД, свободное место на дисках) видны
      # только с самой машины, на публичном порту эндпоинта нет вовсе.
      show-details: always

springdoc:
  # Swagger выключен в постоянной эксплуатации: /v3/api-docs — это полная карта API.
  # На время приёмо-сдаточных испытаний включается SWAGGER_ENABLED=true, см. раздел 14.3.
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
    path: /v3/api-docs
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
    path: /swagger-ui.html

logging:
  level:
    root: INFO
    az.millikart: INFO
  file:
    name: /var/log/merchant-portal/auth.log
EOF
```

#### Конфигурация Directory-сервиса

```bash
cat > /opt/merchant-portal/config/directory-application.yaml << 'EOF'
server:
  port: 8082

spring:
  application:
    name: directory
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/merchant_portal}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

directory:
  terminal-reconciliation:
    # Сверка статусов терминалов со справочником провайдера, который обновляет ecom. Где ecom не
    # установлен, справочник пуст, и сверка ничего не меняет.
    enabled: ${DIRECTORY_TERMINAL_RECONCILIATION_ENABLED:true}
    cron: "${DIRECTORY_TERMINAL_RECONCILIATION_CRON:0 */15 * * * *}"

mp:
  trusted-proxies: ${TRUSTED_PROXIES:127.0.0.1,::1}

pbl:
  security:
    jwt:
      secret: ${JWT_SECRET}
      # Одинаково с auth (P1-13). directory токены только проверяет — значение здесь для
      # единообразия конфигураций.
      expiration-ms: ${JWT_EXPIRATION_MS:900000}

management:
  server:
    # Actuator на отдельном порту, привязанном к 127.0.0.1: снаружи сервера он недоступен
    # физически. Порты: auth 9081, directory 9082, pbl 9080, ecom 9083. Здесь — directory.
    port: ${MANAGEMENT_PORT:9082}
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      # Можно оставить always: детали (состояние БД, свободное место на дисках) видны
      # только с самой машины, на публичном порту эндпоинта нет вовсе.
      show-details: always

springdoc:
  # Swagger выключен в постоянной эксплуатации: /v3/api-docs — это полная карта API.
  # На время приёмо-сдаточных испытаний включается SWAGGER_ENABLED=true, см. раздел 14.3.
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
    path: /v3/api-docs
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
    path: /swagger-ui.html

logging:
  level:
    root: INFO
    az.millikart: INFO
  file:
    name: /var/log/merchant-portal/directory.log
EOF
```

#### Конфигурация PBL-сервиса

```bash
cat > /opt/merchant-portal/config/pbl-application.yaml << 'EOF'
server:
  port: 8080

spring:
  application:
    name: pbl
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/merchant_portal}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

pbl:
  dashboard:
    # Часовой пояс сводки на главной странице (P3-7). Строка обязательна: без неё сервис не стартует.
    zone: ${PBL_DASHBOARD_ZONE:Asia/Baku}
  # Публичный адрес сервиса — задаётся переменной окружения PBL_BASE_URL (mp.env, п. 8.3),
  # а не правкой этого файла. Дефолта нет намеренно: значение уходит эквайеру как адрес
  # возврата плательщика (hppRedirectUrl).
  base-url: ${PBL_BASE_URL}
  security:
    api-token: ${PBL_API_TOKEN:}
    api-token-enabled: ${PBL_API_TOKEN_ENABLED:false}
    jwt:
      secret: ${JWT_SECRET}
      # Одинаково с auth (P1-13). pbl токены только проверяет — значение здесь для
      # единообразия конфигураций.
      expiration-ms: ${JWT_EXPIRATION_MS:900000}
  link:
    # Срок жизни ссылки, если мерчант не передал expiresAt при создании.
    default-ttl: ${PBL_LINK_DEFAULT_TTL:PT24H}
    # Потолок: дальше этого от момента СОЗДАНИЯ ссылки срок выставить нельзя —
    # ни при создании, ни последующими PATCH'ами.
    max-ttl: ${PBL_LINK_MAX_TTL:P90D}
  reconciliation:
    # Фоновая сверка зависших PENDING-транзакций с эквайером (callback'а у эквайера нет —
    # опрос единственный способ дожать статус). Значения по умолчанию рабочие.
    enabled: ${PBL_RECONCILIATION_ENABLED:true}
    cron: "${PBL_RECONCILIATION_CRON:0 */2 * * * *}"
    min-age: ${PBL_RECONCILIATION_MIN_AGE:PT2M}
    max-age: ${PBL_RECONCILIATION_MAX_AGE:PT24H}
    # Старше этого возраста PENDING сверкой не опрашивается — ручной случай (P1-8a).
    give-up-age: ${PBL_RECONCILIATION_GIVE_UP_AGE:P7D}
    batch-size: ${PBL_RECONCILIATION_BATCH_SIZE:50}
  provider:
    # Адреса эквайера — из окружения, без дефолтов: дефолт на тестовый стенд в проде
    # отправил бы платежи тестовому эквайеру (ничего не списывается, портал показывает
    # «оплачено»). Значения выдаёт MilliKart, задаются в mp.env (п. 8.3).
    gateway-base-url: ${PBL_PROVIDER_GATEWAY_BASE_URL}
    api-base-url: ${PBL_PROVIDER_API_BASE_URL}
    # Пути — константы протокола, дефолты у них есть; в кавычках из-за фигурных скобок.
    create-order-path: "${PBL_PROVIDER_CREATE_ORDER_PATH:/order}"
    exec-tran-path: "${PBL_PROVIDER_EXEC_TRAN_PATH:/order/{orderId}/exec-tran}"
    get-order-path: "${PBL_PROVIDER_GET_ORDER_PATH:/order/{orderId}}"

mp:
  trusted-proxies: ${TRUSTED_PROXIES:127.0.0.1,::1}

management:
  server:
    # Actuator на отдельном порту, привязанном к 127.0.0.1: снаружи сервера он недоступен
    # физически. Порты: auth 9081, directory 9082, pbl 9080, ecom 9083. Здесь — pbl.
    port: ${MANAGEMENT_PORT:9080}
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      # Можно оставить always: детали (состояние БД, свободное место на дисках) видны
      # только с самой машины, на публичном порту эндпоинта нет вовсе.
      show-details: always

springdoc:
  # Swagger выключен в постоянной эксплуатации: /v3/api-docs — это полная карта API.
  # На время приёмо-сдаточных испытаний включается SWAGGER_ENABLED=true, см. раздел 14.3.
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
    path: /v3/api-docs
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
    path: /swagger-ui.html

resilience4j:
  circuitbreaker:
    instances:
      acquiring:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10000ms
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      acquiring:
        maxAttempts: 3
        waitDuration: 500ms

logging:
  level:
    root: INFO
    az.millikart: INFO
  file:
    name: /var/log/merchant-portal/pbl.log
EOF
```

#### Конфигурация ecom-сервиса (только где есть доступ к базе шлюза)

```bash
cat > /opt/merchant-portal/config/ecom-application.yaml << 'EOF'
server:
  port: 8083

spring:
  application:
    name: ecom
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/merchant_portal}
    driver-class-name: org.postgresql.Driver
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml

ecom:
  txpg:
    # Схема базы шлюза, из которой читаем; у тестового стенда и прода она может отличаться.
    schema: ${ECOM_TXPG_SCHEMA:TXPG}
    # Предохранители отчётных запросов: они идут по боевой базе платежей.
    max-window: ${ECOM_TXPG_MAX_WINDOW:P92D}
    max-page-size: ${ECOM_TXPG_MAX_PAGE_SIZE:200}
    query-timeout: ${ECOM_TXPG_QUERY_TIMEOUT:PT30S}
    fetch-size: ${ECOM_TXPG_FETCH_SIZE:200}
    # Сколько обновлений подряд терминал должен отсутствовать у провайдера, чтобы считаться выключенным.
    missing-runs-before-disable: ${ECOM_TXPG_MISSING_RUNS:3}
    datasource:
      # Без значений по умолчанию: адрес и учётную запись базы шлюза выдаёт MilliKart (mp.env, п. 8.3).
      url: ${ECOM_TXPG_URL}
      driver-class-name: oracle.jdbc.OracleDriver
      username: ${ECOM_TXPG_USERNAME}
      password: ${ECOM_TXPG_PASSWORD}
      hikari:
        # Маленький пул намеренно: каждое соединение отнимает ресурс у боевых авторизаций.
        maximum-pool-size: ${ECOM_TXPG_POOL_SIZE:4}
        connection-timeout: 10000
  terminal-sync:
    # Обновление справочника терминалов провайдера.
    enabled: ${ECOM_TERMINAL_SYNC_ENABLED:true}
    cron: "${ECOM_TERMINAL_SYNC_CRON:0 */15 * * * *}"

mp:
  trusted-proxies: ${TRUSTED_PROXIES:127.0.0.1,::1}

pbl:
  security:
    jwt:
      # Тот же JWT_SECRET, что у остальных сервисов.
      secret: ${JWT_SECRET}
      expiration-ms: ${JWT_EXPIRATION_MS:900000}

management:
  server:
    # Actuator на отдельном порту, привязанном к 127.0.0.1: снаружи сервера он недоступен
    # физически. Порты: auth 9081, directory 9082, pbl 9080, ecom 9083. Здесь — ecom.
    port: ${MANAGEMENT_PORT:9083}
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

springdoc:
  api-docs:
    enabled: ${SWAGGER_ENABLED:false}
    path: /v3/api-docs
  swagger-ui:
    enabled: ${SWAGGER_ENABLED:false}
    path: /swagger-ui.html

logging:
  level:
    root: INFO
    az.millikart: INFO
  file:
    name: /var/log/merchant-portal/ecom.log
EOF
```

### 8.2. Что нужно заменить в конфигурации

В самих yaml-файлах менять **ничего не нужно**. Раньше здесь был плейсхолдер `ВАШ_ДОМЕН`
в `pbl.base-url`; с P1-10 домен, как и адреса эквайера, задаётся переменной окружения
(`PBL_BASE_URL`, `PBL_PROVIDER_GATEWAY_BASE_URL`, `PBL_PROVIDER_API_BASE_URL` — следующий
пункт), а не правкой yaml. Все три без значения по умолчанию: сервис без них не стартует.

Все настройки окружения — записи вида `${ПЕРЕМЕННАЯ}`. Ни секретов, ни адресов в yaml
больше нет и быть не должно: их подставляет окружение процесса. Файл конфигурации после
этого можно показывать кому угодно, и один и тот же файл годится для тестового стенда
и для прода.

### 8.3. Файл с переменными окружения

Секреты и адреса живут в одном файле, который читает systemd. Шаблон со всеми переменными и
пояснениями — `.env.example` в корне репозитория.

```bash
sudo touch /opt/merchant-portal/config/mp.env
sudo chown mpuser:mpuser /opt/merchant-portal/config/mp.env
# Файл содержит пароль БД и ключ подписи — читать его должен только сервис:
sudo chmod 600 /opt/merchant-portal/config/mp.env

sudo tee /opt/merchant-portal/config/mp.env > /dev/null << EOF
DB_URL=jdbc:postgresql://localhost:5432/merchant_portal
DB_USERNAME=postgres
DB_PASSWORD=ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ
JWT_SECRET=$(openssl rand -base64 48)
PBL_BASE_URL=https://ВАШ_ДОМЕН/
PBL_PROVIDER_GATEWAY_BASE_URL=АДРЕС_ШЛЮЗА_ОТ_MILLIKART
PBL_PROVIDER_API_BASE_URL=АДРЕС_API_ОТ_MILLIKART
AUTH_BOOTSTRAP_ENABLED=false
PBL_API_TOKEN_ENABLED=false
SWAGGER_ENABLED=false
EOF
```

Если на этом сервере ставится `ecom`, допишите в тот же файл доступ к базе шлюза — значения выдаёт MilliKart:

```bash
sudo tee -a /opt/merchant-portal/config/mp.env > /dev/null << 'EOF'
ECOM_TXPG_URL=jdbc:oracle:thin:@//АДРЕС_БАЗЫ_ШЛЮЗА:ПОРТ/СЕРВИС
ECOM_TXPG_USERNAME=ПОЛЬЗОВАТЕЛЬ_ТОЛЬКО_НА_ЧТЕНИЕ
ECOM_TXPG_PASSWORD=ПАРОЛЬ
ECOM_TXPG_SCHEMA=TXPG
EOF
```

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
> плейсхолдер), относительный путь или схема кроме `http`/`https` — отказ стартовать
> с инструкцией (в том числе значение с пробелом или переводом строки на конце — оно
> используется как есть и сломало бы каждый URL); не-HTTPS адрес на любом хосте, кроме
> `localhost`/`127.0.0.1`/`[::1]`, — WARN в рамке.
>
> `PBL_PROVIDER_GATEWAY_BASE_URL` и `PBL_PROVIDER_API_BASE_URL` — адреса шлюза и API эквайера,
> их выдаёт MilliKart, и у тестового стенда и прода они разные. Дефолтов у них нет по той же
> причине: дефолт на тестовый стенд в проде — это платежи, ушедшие тестовому эквайеру, при
> «оплачено» в портале. Если адрес API у эквайера пока только `http://`, сервис стартует,
> но пишет WARN: по этому каналу уходит Basic-авторизация с логином и паролем терминала.
> Это разговор с MilliKart о HTTPS, а не правка конфигурации.

> [!NOTE]
> `SWAGGER_ENABLED=false` — это рабочее состояние. Как временно включить документацию
> на время приёмки, описано в разделе [14.3](#143-swagger-на-время-приёмки).
> `MANAGEMENT_PORT` в файл не добавляем: у каждого сервиса свой порт actuator задан
> в его yaml (auth 9081, directory 9082, pbl 9080, ecom 9083), и одна общая переменная сломала бы
> это разделение. Переопределять его нужно только при конфликте портов — и тогда
> персонально, в юните конкретного сервиса.

> [!NOTE]
> Здесь `<< EOF` **без кавычек** — это намеренно: так `$(openssl rand -base64 48)` выполнится
> и в файл попадёт готовый ключ. В блоках выше кавычки (`<< 'EOF'`) есть, потому что там
> `${...}` должны остаться текстом.

Проверьте, что ключ действительно записался (а не строка `$(openssl…)`):

```bash
sudo grep JWT_SECRET /opt/merchant-portal/config/mp.env
```

> [!WARNING]
> `JWT_SECRET` должен быть **одинаковым** во всех сервисах — поэтому файл один на всех.
> Разные значения означают, что токен, выданный `auth`, не пройдёт проверку в `directory`, `pbl` и `ecom`,
> и любой запрос к ним вернёт 401.

> [!CAUTION]
> `DB_PASSWORD` и `JWT_SECRET` (а для `pbl` — и три адреса из блока выше, для `ecom` — доступ к базе шлюза) **не имеют значений
> по умолчанию**. Сервис, запущенный без них, не стартует и печатает, что именно задать. Это не
> помеха, а защита: значение по умолчанию — ровно то, из-за чего прежний ключ подписи попал
> в репозиторий и стал публичным, а у адресов дефолт — это прод, молча отправляющий
> плательщиков на `localhost`.

Полная процедура первого запуска, создания администратора и смены ключа —
раздел [20](#20-первый-запуск-и-ротация-ключа).

### 8.4. Создание директории для логов

```bash
sudo mkdir -p /var/log/merchant-portal
sudo chown $USER:$USER /var/log/merchant-portal
```

---

## 9. Шаг 7 — Запуск бэкенд-сервисов через systemd

systemd — это менеджер служб в Linux. Он будет **автоматически запускать** наши сервисы при старте сервера и перезапускать их при сбоях.

### 9.1. Создание системного пользователя

Для безопасности сервисы будут запускаться от отдельного пользователя:

```bash
sudo useradd -r -s /bin/false mpuser
sudo chown -R mpuser:mpuser /opt/merchant-portal/deploy
sudo chown -R mpuser:mpuser /opt/merchant-portal/config
sudo chown -R mpuser:mpuser /var/log/merchant-portal
```

### 9.2. Создание systemd-юнита для Auth

```bash
sudo cat > /etc/systemd/system/mp-auth.service << 'EOF'
[Unit]
Description=Merchant Portal - Auth Service
Documentation=https://gitlab.millikart.az/mp
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Секреты (DB_PASSWORD, JWT_SECRET и остальные) — только отсюда, не из yaml.
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/auth.jar \
    --spring.config.location=file:/opt/merchant-portal/config/auth-application.yaml

Restart=on-failure
RestartSec=10
StartLimitIntervalSec=60
StartLimitBurst=3

StandardOutput=journal
StandardError=journal

Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

[Install]
WantedBy=multi-user.target
EOF
```

### 9.3. Создание systemd-юнита для Directory

```bash
sudo cat > /etc/systemd/system/mp-directory.service << 'EOF'
[Unit]
Description=Merchant Portal - Directory Service
Documentation=https://gitlab.millikart.az/mp
After=network.target postgresql.service mp-auth.service
Requires=postgresql.service

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Секреты (DB_PASSWORD, JWT_SECRET и остальные) — только отсюда, не из yaml.
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/directory.jar \
    --spring.config.location=file:/opt/merchant-portal/config/directory-application.yaml

Restart=on-failure
RestartSec=10
StartLimitIntervalSec=60
StartLimitBurst=3

StandardOutput=journal
StandardError=journal

Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

[Install]
WantedBy=multi-user.target
EOF
```

### 9.4. Создание systemd-юнита для PBL

```bash
sudo cat > /etc/systemd/system/mp-pbl.service << 'EOF'
[Unit]
Description=Merchant Portal - PBL Service (Pay-By-Link)
Documentation=https://gitlab.millikart.az/mp
After=network.target postgresql.service mp-auth.service
Requires=postgresql.service

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Секреты (DB_PASSWORD, JWT_SECRET и остальные) — только отсюда, не из yaml.
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/pbl.jar \
    --spring.config.location=file:/opt/merchant-portal/config/pbl-application.yaml

Restart=on-failure
RestartSec=10
StartLimitIntervalSec=60
StartLimitBurst=3

StandardOutput=journal
StandardError=journal

Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

[Install]
WantedBy=multi-user.target
EOF
```

### 9.4a. Создание systemd-юнита для ecom (только где есть доступ к базе шлюза)

```bash
sudo cat > /etc/systemd/system/mp-ecom.service << 'EOF'
[Unit]
Description=Merchant Portal - ecom Service (E-commerce statement)
Documentation=https://gitlab.millikart.az/mp
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
User=mpuser
Group=mpuser

WorkingDirectory=/opt/merchant-portal/deploy

# Секреты (DB_PASSWORD, JWT_SECRET, ECOM_TXPG_*) — только отсюда, не из yaml.
EnvironmentFile=/opt/merchant-portal/config/mp.env

ExecStart=/usr/bin/java \
    -Xms256m -Xmx512m \
    -jar /opt/merchant-portal/deploy/ecom.jar \
    --spring.config.location=file:/opt/merchant-portal/config/ecom-application.yaml

Restart=on-failure
RestartSec=10
StartLimitIntervalSec=60
StartLimitBurst=3

StandardOutput=journal
StandardError=journal

Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

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
# Перечитываем конфигурацию systemd (обязательно после создания новых юнитов)
sudo systemctl daemon-reload

# Включаем автозапуск при старте сервера
sudo systemctl enable mp-auth mp-directory mp-pbl
sudo systemctl enable mp-ecom          # только если установлен ecom

# Запускаем сервисы. Порядок значения не имеет (с 17.08.2026, P1-2): миграции каждого
# сервиса обложены преконтролями, любой из сервисов может создать общие таблицы первым.
sudo systemctl start mp-auth mp-directory mp-pbl
sudo systemctl start mp-ecom           # только если установлен ecom
```

> [!NOTE]
> На **первой** установке единственный внешний ключ, который зависит от порядка, —
> `fk_terminals_company`: его создаёт `auth`, а таблицу `terminals` — `directory`/`pbl`.
> Если `auth` успел стартовать раньше них, ключ появится при следующем запуске `auth`
> (`sudo systemctl restart mp-auth`). Подробности — раздел
> [20.1](#201-первый-запуск-новой-установки), шаг 3.

### 9.6. Проверка статуса

```bash
# Проверяем сервисы
sudo systemctl status mp-auth
sudo systemctl status mp-directory
sudo systemctl status mp-pbl
sudo systemctl status mp-ecom          # только если установлен ecom
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

# Устанавливаем все зависимости
npm install
```

> Это займёт 1–3 минуты. Вы увидите много текста — это нормально.

### 10.2. Сборка для продакшна

```bash
npm run build
```

> С 18.08.2026 `npm run build` = `tsc -b && vite build`: сначала проверка типов, потом сборка.
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
> **`X-Real-IP` в блоках ниже — не украшение, а источник адреса клиента.** С P3-Auth
> (19.08.2026) `auth` считает по нему неудачные попытки входа, а `pbl` пишет его
> в `transactions.client_ip`. Сервисы верят этому заголовку только от адресов из
> `TRUSTED_PROXIES` (по умолчанию `127.0.0.1,::1` — этот самый nginx), и берут
> **последний** элемент `X-Forwarded-For`, потому что `$proxy_add_x_forwarded_for`
> дописывает настоящий адрес в конец, а всё, что прислал клиент, оставляет впереди.
>
> Поэтому:
> * **строку `proxy_set_header X-Real-IP $remote_addr;` убирать нельзя** ни из одного
>   `location` — без неё все запросы через этот маршрут считаются под адресом самого
>   nginx, то есть лимит входа становится общим на весь мир;
> * `$remote_addr` не заменять на `$http_x_real_ip` или `$http_x_forwarded_for` —
>   это ровно то, что прислал клиент;
> * если однажды перед nginx встанет ещё один прокси (балансировщик, CDN), адрес
>   этого прокси нужно добавить в `TRUSTED_PROXIES`, иначе адреса клиентов схлопнутся
>   в один.


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

# Сервис выписки (ecom). Поднимается только там, где есть доступ к схеме шлюза провайдера.
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

    # PBL-сервис (платёжные ссылки, операции, сводка главной)
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

    # Сводка главной страницы. Без этого блока запрос уходит в location / и вместо данных
    # возвращается index.html фронтенда.
    location /api/v1/dashboard {
        proxy_pass http://pbl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
    }

    # Сервис выписки: транзакции мерчанта из схемы шлюза провайдера и слепок его терминалов.
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

    # Проверка учётных данных терминала пробным заказом у провайдера (кнопка «Тест»).
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

    # PBL — страница оплаты (Thymeleaf-шаблоны)
    location /pay/ {
        proxy_pass http://pbl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # ───── Swagger UI (документация API) ─────
    # Работает, только когда сервис запущен с SWAGGER_ENABLED=true (раздел 14.3).
    # При выключенном флаге springdoc не регистрирует эндпоинты и оба пути отдают 404.
    # Ограничение allow/deny оставлено: даже включённую документацию не стоит показывать наружу.
    location /swagger-ui.html {
        proxy_pass http://pbl_backend;
        allow 127.0.0.1;
        deny all;
    }

    location /v3/api-docs {
        proxy_pass http://pbl_backend;
        allow 127.0.0.1;
        deny all;
    }

    # ───── Actuator ─────
    # Здесь его НЕТ намеренно. Actuator живёт на отдельных портах (auth 9081, directory 9082,
    # pbl 9080, ecom 9083), привязанных к 127.0.0.1: с самого сервера он доступен по curl, снаружи —
    # никак. Проксировать его через nginx означало бы вернуть наружу ровно то, что мы убрали:
    # состояние подключения к БД, свободное место на дисках и версии компонентов.
    # Проверка здоровья — раздел 14.1.

    # ───── Статические файлы фронтенда ─────
    
    # Кэширование статических ресурсов (JS, CSS, изображения)
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
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

# ecom (только если установлен)
curl -s http://127.0.0.1:9083/actuator/health | python3 -m json.tool
```

> [!IMPORTANT]
> Порты **не те же**, что у самого сервиса. Actuator вынесен на отдельный порт
> (auth 9081, directory 9082, pbl 9080, ecom 9083), привязанный к `127.0.0.1`: эти команды работают
> только с самого сервера, снаружи порт закрыт на уровне сети, а не пароля.
> На рабочих портах 8080–8083 путь `/actuator/health` теперь отдаёт **404** — это
> нормально и означает, что настройка применилась.

Токен для health-check не нужен и не будет нужен: пробы мониторинга его не имеют.
Для каждого сервиса вы должны увидеть примерно это (детали видны, потому что порт локальный):

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

Если ответ пустой или «connection refused» — сервис не поднялся; смотрите логи
(`sudo journalctl -u mp-auth -n 50`).

### 14.2. Проверяем фронтенд

Откройте в браузере:

```
https://ВАШ_ДОМЕН
```

Вы должны увидеть страницу входа (логин) Merchant Portal.

### 14.3. Swagger на время приёмки

Интерактивная документация API (Swagger UI) заявлена в `technical_handover.md` как
передаваемый артефакт, но **в постоянной эксплуатации она выключена**: `/v3/api-docs` —
это полная карта API, включая пути, которые снаружи знать незачем.

При выключенном флаге springdoc вообще не регистрирует эти эндпоинты: `/swagger-ui.html`
и `/v3/api-docs` отдают 404 (без токена — 401, потому что модель доступа теперь
«по умолчанию запрещено»).

**Включить на время приёмо-сдаточных испытаний:**

```bash
# 1. Добавляем переменную в общий файл окружения
sudo sed -i 's/^SWAGGER_ENABLED=.*/SWAGGER_ENABLED=true/' /opt/merchant-portal/config/mp.env
sudo grep SWAGGER_ENABLED /opt/merchant-portal/config/mp.env

# 2. Перезапускаем сервисы
sudo systemctl restart mp-auth mp-directory mp-pbl
sudo systemctl restart mp-ecom         # только если установлен ecom

# 3. Проверяем с самого сервера
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/v3/api-docs   # ожидается 200
```

Открыть UI: `http://localhost:8081/swagger-ui.html` (auth), `:8082` (directory),
`:8080` (pbl), `:8083` (ecom). Через интернет он не откроется — в конфигурации nginx на эти пути стоит
`allow 127.0.0.1; deny all;`. С рабочего места пользуйтесь SSH-туннелем:

```bash
ssh -L 8081:localhost:8081 ПОЛЬЗОВАТЕЛЬ@ВАШ_СЕРВЕР
# и затем в браузере: http://localhost:8081/swagger-ui.html
```

**Выключить обратно после приёмки** (обязательный шаг, не забыть):

```bash
sudo sed -i 's/^SWAGGER_ENABLED=.*/SWAGGER_ENABLED=false/' /opt/merchant-portal/config/mp.env
sudo systemctl restart mp-auth mp-directory mp-pbl
sudo systemctl restart mp-ecom         # только если установлен ecom

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
# 1. Переходим в директорию проекта
cd /opt/merchant-portal

# 2. Получаем обновления из Git
git pull

# 3. Собираем новую версию
./gradlew clean build -x test

# 4. Останавливаем сервисы
sudo systemctl stop mp-ecom            # только если установлен ecom
sudo systemctl stop mp-pbl mp-directory mp-auth

# 5. Копируем новые JAR-файлы
cp auth/build/libs/auth-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/auth.jar
cp directory/build/libs/directory-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/directory.jar
cp pbl/build/libs/pbl-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/pbl.jar
cp ecom/build/libs/ecom-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/ecom.jar   # только если установлен ecom

# 6. Восстанавливаем права
sudo chown mpuser:mpuser /opt/merchant-portal/deploy/*.jar

# 7. Запускаем сервисы обратно (порядок не важен — миграции обложены преконтролями)
sudo systemctl start mp-auth mp-directory mp-pbl
sudo systemctl start mp-ecom           # только если установлен ecom

# 8. Проверяем
sudo systemctl status mp-auth mp-directory mp-pbl
```

### 15.2. Обновление фронтенда

```bash
cd /opt/merchant-portal/frontend

npm install
npm run build

sudo cp -r dist/* /var/www/merchant-portal/
sudo chown -R www-data:www-data /var/www/merchant-portal

# Nginx НЕ нужно перезапускать — он автоматически раздаёт новые файлы
```

---

## 16. Резервное копирование

### 16.1. Бэкап базы данных

Создайте скрипт для автоматического бэкапа:

```bash
sudo nano /opt/merchant-portal/backup.sh
```

Вставьте:

```bash
#!/bin/bash
# ═══════════════════════════════════════
# Бэкап базы данных Merchant Portal
# ═══════════════════════════════════════

BACKUP_DIR="/opt/merchant-portal/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/merchant_portal_${DATE}.sql.gz"

# Создаём директорию, если не существует
mkdir -p "$BACKUP_DIR"

# Создаём бэкап и сжимаем
PGPASSWORD="ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ" pg_dump -U postgres -h localhost merchant_portal | gzip > "$BACKUP_FILE"

# Удаляем бэкапы старше 30 дней
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +30 -delete

echo "[$(date)] Бэкап создан: $BACKUP_FILE ($(du -sh $BACKUP_FILE | cut -f1))"
```

Сделайте скрипт исполняемым и настройте автозапуск:

```bash
chmod +x /opt/merchant-portal/backup.sh

# Добавляем в cron (автоматический запуск каждый день в 3:00 ночи)
(crontab -l 2>/dev/null; echo "0 3 * * * /opt/merchant-portal/backup.sh >> /var/log/merchant-portal/backup.log 2>&1") | crontab -
```

### 16.2. Восстановление из бэкапа

```bash
# Останавливаем сервисы
sudo systemctl stop mp-ecom            # только если установлен ecom
sudo systemctl stop mp-pbl mp-directory mp-auth

# Восстанавливаем базу
gunzip -c /opt/merchant-portal/backups/merchant_portal_ДАТА.sql.gz | \
  PGPASSWORD="ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ" psql -U postgres -h localhost merchant_portal

# Запускаем обратно (порядок не важен)
sudo systemctl start mp-auth mp-directory mp-pbl
sudo systemctl start mp-ecom           # только если установлен ecom
```

---

## 17. Мониторинг и логи

### 17.1. Просмотр логов

```bash
# Логи конкретного сервиса (в реальном времени)
sudo journalctl -u mp-auth -f
sudo journalctl -u mp-directory -f
sudo journalctl -u mp-pbl -f
sudo journalctl -u mp-ecom -f          # если установлен ecom

# Логи за последний час
sudo journalctl -u mp-auth --since "1 hour ago"

# Все логи за сегодня
sudo journalctl -u mp-auth --since today

# Логи Nginx (ошибки)
sudo tail -f /var/log/nginx/error.log

# Логи Nginx (все запросы)
sudo tail -f /var/log/nginx/access.log
```

### 17.2. Health-check эндпоинты

Каждый сервис имеет встроенный эндпоинт для проверки здоровья:

| Сервис | URL | Что показывает |
|--------|-----|----------------|
| Auth | `http://127.0.0.1:9081/actuator/health` | Статус сервиса + подключение к БД |
| Directory | `http://127.0.0.1:9082/actuator/health` | Статус сервиса + подключение к БД |
| PBL | `http://127.0.0.1:9080/actuator/health` | Статус сервиса + подключение к БД |
| ecom | `http://127.0.0.1:9083/actuator/health` | Статус сервиса + подключение к базам |

Это **отдельные порты**, не рабочие 8080–8083, и они привязаны к `127.0.0.1`:
запрос проходит только с самого сервера. Токен не нужен. Кроме `health` открыты
`info` и `metrics` — например, `curl -s http://127.0.0.1:9081/actuator/metrics`.

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

**Частые причины:**
- `Connection refused` → PostgreSQL не запущен: `sudo systemctl start postgresql`
- `Password authentication failed` → Неверный пароль в конфигурации
- `Port already in use` → Порт занят другим процессом: `sudo lsof -i :8081`
- `java: command not found` → Java не установлена или неверный путь

### ❌ Проблема: «502 Bad Gateway» в браузере

Это означает, что Nginx не может связаться с бэкенд-сервисом.

1. Проверьте, запущены ли сервисы: `sudo systemctl status mp-auth mp-directory mp-pbl` (и `mp-ecom`, если установлен)
2. Если `inactive (dead)` — запустите: `sudo systemctl start mp-auth`
3. Проверьте порты: `ss -tlnp | grep -E '8080|8081|8082|8083'`

### ❌ Проблема: Страница логина не открывается

1. Проверьте Nginx: `sudo systemctl status nginx`
2. Проверьте файлы фронтенда: `ls /var/www/merchant-portal/index.html`
3. Проверьте файрвол: `sudo ufw status`
4. Проверьте DNS: `nslookup ВАШ_ДОМЕН`

### ❌ Проблема: «401 Unauthorized» после логина

1. Проверьте, что JWT-секрет **одинаковый** во всех сервисах
2. Проверьте, что Auth-сервис работает: `curl http://127.0.0.1:9081/actuator/health`
   (порт actuator — 9081, а не рабочий 8081; на 8081 этот путь отдаёт 404)

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
| `8083` | ecom (выписка E-commerce), если установлен | HTTP |

### Порты actuator (только с самой машины, `127.0.0.1`)

| Порт | Сервис | Переменная | Что отдаёт |
|------|--------|------------|------------|
| `9081` | Auth | `MANAGEMENT_PORT` | `/actuator/health`, `/info`, `/metrics` |
| `9082` | Directory | `MANAGEMENT_PORT` | то же |
| `9080` | PBL | `MANAGEMENT_PORT` | то же |
| `9083` | ecom | `MANAGEMENT_PORT` | то же |

Эти порты не слушают внешний интерфейс: их защищает привязка адреса, а не токен, —
именно поэтому пробы мониторинга работают без авторизации. На рабочих портах
(8080–8083) путь `/actuator/**` не обслуживается и отдаёт 404.

### Внешние порты (доступны из интернета)

| Порт | Сервис | Назначение |
|------|--------|------------|
| `22` | SSH | Удалённый доступ |
| `80` | Nginx | HTTP (перенаправляет на 443) |
| `443` | Nginx | HTTPS (основной) |

### API-маршруты

| Маршрут | Бэкенд | Описание |
|---------|--------|----------|
| `/api/v1/auth/**` | Auth (:8081) | Вход, обновление токена, выход |
| `/api/v1/users/**` | Auth (:8081) | Управление пользователями |
| `/api/v1/companies/**` | Directory (:8082) | Управление компаниями |
| `/api/v1/terminals/**` | Directory (:8082) | Управление терминалами |
| `/api/v1/audit-logs/**` | Directory (:8082) | Журнал аудита |
| `/api/v1/payment-links/**` | PBL (:8080) | Платёжные ссылки |
| `/api/v1/transactions/**` | PBL (:8080) | Транзакции |
| `/api/v1/dashboard/**` | PBL (:8080) | Сводка главной страницы |
| `/api/v1/acquiring/**` | PBL (:8080) | Проверка учётных данных терминала у провайдера |
| `/api/v1/ecom/**` | ecom (:8083) | Выписка E-commerce и справочник терминалов провайдера |

### Конфигурационные файлы

| Файл | Назначение |
|------|------------|
| `/opt/merchant-portal/config/auth-application.yaml` | Настройки Auth-сервиса |
| `/opt/merchant-portal/config/directory-application.yaml` | Настройки Directory-сервиса |
| `/opt/merchant-portal/config/pbl-application.yaml` | Настройки PBL-сервиса |
| `/opt/merchant-portal/config/ecom-application.yaml` | Настройки ecom-сервиса (если установлен) |
| `/etc/nginx/sites-available/merchant-portal` | Настройки веб-сервера |
| `/etc/systemd/system/mp-auth.service` | Systemd-юнит Auth |
| `/etc/systemd/system/mp-directory.service` | Systemd-юнит Directory |
| `/etc/systemd/system/mp-pbl.service` | Systemd-юнит PBL |
| `/etc/systemd/system/mp-ecom.service` | Systemd-юнит ecom (если установлен) |

### Директории

| Путь | Содержимое |
|------|------------|
| `/opt/merchant-portal/` | Исходный код и конфигурация |
| `/opt/merchant-portal/deploy/` | Запускаемые JAR-файлы |
| `/opt/merchant-portal/config/` | Конфигурации для продакшна |
| `/opt/merchant-portal/backups/` | Бэкапы базы данных |
| `/var/www/merchant-portal/` | Собранный фронтенд (HTML/CSS/JS) |
| `/var/log/merchant-portal/` | Логи приложения |
| `/var/log/nginx/` | Логи Nginx |

---

## 20. Первый запуск и ротация ключа

Раздел про две вещи, которые делаются руками и в определённом порядке: как поднять
**новую** установку и как **сменить** ключ подписи JWT на уже работающей.

Оркестратора и хранилища секретов в проекте нет — сервисы запускаются вручную. Поэтому
защита встроена в сами приложения: `DB_PASSWORD` и `JWT_SECRET` (а у `pbl` — ещё три адреса,
`PBL_BASE_URL`, `PBL_PROVIDER_GATEWAY_BASE_URL`, `PBL_PROVIDER_API_BASE_URL`, у `ecom` — `ECOM_TXPG_URL`,
`ECOM_TXPG_USERNAME`, `ECOM_TXPG_PASSWORD`, п. 8.3) не имеют
значений по умолчанию, и сервис без них **не стартует**, печатая, что именно задать.

### 20.1. Первый запуск новой установки

#### Шаг 1. Сгенерировать ключ подписи

```bash
openssl rand -base64 48
```

Требования проверяются на старте: непустой, не короче 32 байт (HS256 подписывает
256-битным хешем). Ключ, лежавший в этом репозитории до 17.08.2026, отвергается отдельно
по SHA-256 — он публичный, и вернуть его «чтобы заработало» не получится.

#### Шаг 2. Задать переменные окружения для всех сервисов

Как это оформляется для systemd — в разделе [8.3](#83-файл-с-переменными-окружения):
один файл `/opt/merchant-portal/config/mp.env` с правами `600`, подключённый в юниты
через `EnvironmentFile=`. Для ручного запуска из консоли:

```bash
export DB_PASSWORD='пароль пользователя PostgreSQL'
export JWT_SECRET='значение из шага 1'
# Только для pbl (P1-10): публичный адрес и адреса эквайера, без дефолтов
export PBL_BASE_URL='https://ВАШ_ДОМЕН/'
export PBL_PROVIDER_GATEWAY_BASE_URL='адрес шлюза от MilliKart'
export PBL_PROVIDER_API_BASE_URL='адрес API от MilliKart'
# Только для ecom: база платёжного шлюза, учётная запись только на чтение
export ECOM_TXPG_URL='jdbc:oracle:thin:@//адрес:порт/сервис от MilliKart'
export ECOM_TXPG_USERNAME='пользователь от MilliKart'
export ECOM_TXPG_PASSWORD='пароль от MilliKart'
```

| Переменная | auth | directory | pbl | ecom | Значение по умолчанию |
|---|:---:|:---:|:---:|:---:|---|
| `DB_PASSWORD` | **обязательна** | **обязательна** | **обязательна** | **обязательна** | нет |
| `JWT_SECRET` | **обязательна** | **обязательна** | **обязательна** | **обязательна** | нет |
| `DB_URL` | необязательна | необязательна | необязательна | необязательна | `jdbc:postgresql://localhost:5432/postgres` |
| `DB_USERNAME` | необязательна | необязательна | необязательна | необязательна | `postgres` |
| `JWT_EXPIRATION_MS` | необязательна | необязательна | необязательна | необязательна | `900000` (15 минут, с P1-13). Токен выдаёт `auth`; `directory` и `pbl` только проверяют его, переменная объявлена у всех сервисов для единообразия. Фронтенд обновляет токен сам через `/refresh`; это же — верхняя граница, сколько после выхода, блокировки или удаления пользователь ещё имеет доступ |
| `AUTH_REFRESH_TTL` | необязательна | не читается | не читается | не читается | `P30D` (срок refresh-токена) |
| `AUTH_REFRESH_ROTATION_GRACE` | необязательна | не читается | не читается | не читается | `PT10S` (окно, в котором повтор заменённого refresh-токена — гонка вкладок, а не кража) |
| `AUTH_REFRESH_CLEANUP_ENABLED` | необязательна | не читается | не читается | не читается | `true` |
| `AUTH_REFRESH_CLEANUP_CRON` | необязательна | не читается | не читается | не читается | `0 30 3 * * *` |
| `AUTH_BOOTSTRAP_ENABLED` | необязательна | не читается | не читается | не читается | `false` |
| `BOOTSTRAP_ADMIN_USERNAME` | обязательна при включённом bootstrap | не читается | не читается | не читается | нет |
| `BOOTSTRAP_ADMIN_PASSWORD` | обязательна при включённом bootstrap | не читается | не читается | не читается | нет |
| `PBL_API_TOKEN_ENABLED` | не читается | не читается | необязательна | не читается | `false` |
| `PBL_API_TOKEN` | не читается | не читается | обязательна при включённом флаге | не читается | пусто |
| `PBL_BASE_URL` | не читается | не читается | **обязательна** | не читается | нет — публичный адрес сервиса, уходит эквайеру как адрес возврата плательщика (P1-10) |
| `PBL_PROVIDER_GATEWAY_BASE_URL` | не читается | не читается | **обязательна** | не читается | нет — адрес шлюза эквайера, выдаёт MilliKart; не-HTTPS даёт WARN |
| `PBL_PROVIDER_API_BASE_URL` | не читается | не читается | **обязательна** | не читается | нет — адрес e-commerce API эквайера, выдаёт MilliKart; не-HTTPS даёт WARN |
| `PBL_PROVIDER_CREATE_ORDER_PATH` | не читается | не читается | необязательна | не читается | `/order` |
| `PBL_PROVIDER_EXEC_TRAN_PATH` | не читается | не читается | необязательна | не читается | `/order/{orderId}/exec-tran` |
| `PBL_PROVIDER_GET_ORDER_PATH` | не читается | не читается | необязательна | не читается | `/order/{orderId}` |
| `LOGIN_RATE_LIMIT_ENABLED`, `LOGIN_RATE_LIMIT_MAX_FAILURES`, `LOGIN_RATE_LIMIT_WINDOW` | необязательны | не читаются | не читаются | не читаются | `true`, `10`, `PT15M` — лимит неудачных попыток входа с одного адреса |
| `TRUSTED_PROXIES` | необязательна | необязательна | необязательна | необязательна | `127.0.0.1,::1` — прокси, чьим заголовкам с адресом клиента верим |
| `DIRECTORY_TERMINAL_RECONCILIATION_ENABLED`, `DIRECTORY_TERMINAL_RECONCILIATION_CRON` | не читаются | необязательны | не читаются | не читаются | `true`, `0 */15 * * * *` — сверка статусов терминалов со справочником провайдера |
| `PBL_DASHBOARD_ZONE` | не читается | не читается | необязательна | не читается | `Asia/Baku` — часовой пояс сводки на главной |
| `ECOM_TXPG_URL`, `ECOM_TXPG_USERNAME`, `ECOM_TXPG_PASSWORD` | не читаются | не читаются | не читаются | **обязательны** | нет — база платёжного шлюза и учётная запись только на чтение, выдаёт MilliKart |
| `ECOM_TXPG_SCHEMA` | не читается | не читается | не читается | необязательна | `TXPG` |
| `ECOM_TERMINAL_SYNC_ENABLED`, `ECOM_TERMINAL_SYNC_CRON` | не читаются | не читаются | не читаются | необязательны | `true`, `0 */15 * * * *` — обновление справочника терминалов провайдера |

«Не читается» означает, что сервис эту переменную игнорирует; лишняя переменная
в общем файле окружения ничему не мешает.

> [!CAUTION]
> `JWT_SECRET` обязан **совпадать во всех сервисах**, символ в символ. Токен выдаёт
> `auth`, а проверяют его `directory`, `pbl` и `ecom` тем же самым ключом (HS256 симметричный).
> Разные значения — и любой запрос к ним вернёт 401, хотя логин при этом
> будет проходить успешно. Это самая частая ошибка при развёртывании.

Полный список переменных с пояснениями — `.env.example` в корне репозитория.

#### Шаг 3. Запустить сервисы

```bash
sudo systemctl start mp-auth mp-directory mp-pbl
sudo systemctl start mp-ecom           # только если установлен ecom
```

> [!NOTE]
> **Порядок больше не важен** (с 17.08.2026, P1-2). Раньше требовалось поднимать `auth` первым:
> его changeset создавал таблицу `companies` без `<preConditions>`, и стартовавший раньше
> `directory` ронял `auth` на «table companies already exists». Теперь каждый changeset,
> создающий общую таблицу, обложен собственным условием — по одному объекту на changeset, —
> поэтому первым может подняться любой из сервисов, в том числе все одновременно.
>
> Внешний ключ `terminals.company_id → companies.id` создаёт `auth`, а саму таблицу `terminals` —
> `directory` или `pbl`. Если `auth` стартовал раньше них, ключ на этом запуске не создаётся:
> changeset стоит под `onFail="CONTINUE"`, не записывается в `DATABASECHANGELOG` и повторяет
> попытку при следующем запуске `auth`. Поэтому **на свежей установке, где `auth` поднялся
> раньше остальных, перезапустите его один раз после того, как поднялись `directory` и `pbl`** —
> либо просто дождитесь ближайшего планового рестарта. Проверить:
>
> ```bash
> psql -U postgres -d postgres -c "\d terminals" | grep fk_terminals_company
> ```

#### Шаг 4. Один раз создать администратора

Миграция администратора **не заводит**: пароль, лежащий в changeset, одинаков на всех
установках и виден каждому, у кого есть репозиторий. Вместо этого первый `SYSTEM_ADMIN`
создаётся разовым запуском `auth`.

```bash
sudo systemctl stop mp-auth

sudo tee -a /opt/merchant-portal/config/mp.env > /dev/null << 'EOF'
BOOTSTRAP_ADMIN_USERNAME=admin@ваша-компания.az
BOOTSTRAP_ADMIN_PASSWORD=ПридуманныйПароль123!
EOF
sudo sed -i 's/^AUTH_BOOTSTRAP_ENABLED=false/AUTH_BOOTSTRAP_ENABLED=true/' /opt/merchant-portal/config/mp.env

sudo systemctl daemon-reload
sudo systemctl start mp-auth
```

В журнале должна появиться строка уровня WARN — пароль в неё не попадает:

```bash
sudo journalctl -u mp-auth -n 100 | grep "Admin bootstrap"
# Admin bootstrap created SYSTEM_ADMIN 'admin@ваша-компания.az' (id=…). Set AUTH_BOOTSTRAP_ENABLED=false …
```

Требования к паролю — те же, что и у любого пользователя портала (PCI-DSS v4.0):
минимум 12 символов, заглавная и строчная буквы, цифра и спецсимвол. Слабый пароль —
сервис не стартует. Логин обязан быть адресом электронной почты: форма входа проверяет
формат, и учётная запись с другим логином никогда не смогла бы войти.

Проверьте, что вход работает, и **сразу выключите флаг**:

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

### 20.2. Ротация ключа подписи

Ключ меняют планово или после любого подозрения, что он утёк: попал в лог, в переписку,
в скриншот, в чужие руки вместе с бэкапом конфигурации.

```bash
# 1. Новый ключ
NEW_SECRET=$(openssl rand -base64 48)

# 2. Остановить ВСЕ сервисы
sudo systemctl stop mp-ecom            # только если установлен ecom
sudo systemctl stop mp-pbl mp-directory mp-auth

# 3. Заменить значение в общем файле окружения
sudo sed -i "s|^JWT_SECRET=.*|JWT_SECRET=${NEW_SECRET}|" /opt/merchant-portal/config/mp.env
sudo grep JWT_SECRET /opt/merchant-portal/config/mp.env   # убедиться, что значение одно и новое

# 4. Поднять обратно (порядок не важен — миграции уже применены)
sudo systemctl start mp-auth mp-directory mp-pbl
sudo systemctl start mp-ecom           # только если установлен ecom
```

> [!CAUTION]
> **Все выданные access-токены станут недействительными.** Чёрного списка и версионирования
> ключей в системе нет, а при ручном запуске нет и промежутка, когда старый и новый ключ
> приняты одновременно. С P1-13 это уже не выбрасывает пользователей на страницу входа:
> refresh-токен не подписан ключом (в базе лежит его SHA-256), поэтому первый же запрос
> с 401 заставит фронтенд вызвать `/refresh`, `auth` выпустит access-токен уже новым ключом,
> и запрос повторится сам. Пользователь заметит только паузу на время перезапуска сервисов;
> заново войти придётся лишь тому, у кого истёк или отозван refresh-токен.
> Планируйте ротацию на время наименьшей нагрузки — сами перезапуски дают простой.

> [!WARNING]
> Останавливать нужно **все** сервисы, а не перезапускать по одному. Сервис со старым
> ключом и сервис с новым не понимают токены друг друга: пока идёт «плавный» перезапуск,
> часть запросов будет получать 401 без всякой закономерности.

Пароль базы данных (`DB_PASSWORD`) меняется так же — правкой одного файла и перезапуском
всех сервисов, — но выданных токенов он не затрагивает и пользователей из портала
не выбрасывает.

### 20.3. Если сервис не стартует

| Сообщение при старте | Что произошло | Что делать |
|---|---|---|
| `The environment variable JWT_SECRET is not set` | Переменной нет в окружении процесса | Задать её; для systemd — проверить `EnvironmentFile=` в юните |
| `The environment variable DB_PASSWORD is not set` | То же для пароля БД | Задать её |
| `JWT signing secret is too short: N bytes` | Ключ короче 32 байт | Сгенерировать заново: `openssl rand -base64 48` |
| `JWT signing secret is the key that leaked into this repository's git history` | Подставлен старый публичный ключ | Сгенерировать новый; старый использовать нельзя |
| `BOOTSTRAP_ADMIN_PASSWORD does not satisfy the password policy` | Пароль администратора слабее политики | 12+ символов, заглавная, строчная, цифра, спецсимвол |
| `auth.bootstrap.enabled is true but BOOTSTRAP_ADMIN_USERNAME and/or BOOTSTRAP_ADMIN_PASSWORD is not set` | Флаг включён, а данных администратора нет | Задать обе переменные либо выключить флаг |
| `pbl.security.api-token-enabled is true but pbl.security.api-token is empty` | Включён статический токен без значения | Выключить `PBL_API_TOKEN_ENABLED` (обычно это и нужно) |
| `The environment variable PBL_BASE_URL is not set` (то же для `PBL_PROVIDER_GATEWAY_BASE_URL`, `PBL_PROVIDER_API_BASE_URL`) | Адреса нет в окружении `pbl` | Задать её в `mp.env` (п. 8.3); дефолта нет намеренно |
| `The environment variable PBL_BASE_URL (property pbl.base-url) is empty` / `has leading or trailing whitespace` / `is not a valid URL` / `must be an absolute URL` / `has a host part that is not a valid host name` / `must use http or https` | Переменная есть, но значение не годится (пусто, пробел или CRLF на конце, незаменённый `ВАШ_ДОМЕН`, относительный путь, `ftp://`) | Задать абсолютный `https://` адрес без лишних пробелов, например `https://ВАШ_ДОМЕН/` с реальным доменом |
| `WARNING: the acquirer address is not HTTPS` (в рамке, сервис стартует) | Адрес шлюза или API эквайера задан по `http://` | Это не ошибка конфигурации: HTTPS даёт MilliKart. Запросить у них `https://` адрес и заменить переменную |
| `WARNING: the public address of this service is not HTTPS` (в рамке, сервис стартует) | `PBL_BASE_URL` по `http://` на не-локальном хосте | В проде — `https://ВАШ_ДОМЕН/` (раздел 12); для `localhost`/`127.0.0.1`/`[::1]` предупреждения нет |
| Логин проходит, но `directory`, `pbl` или `ecom` отвечают 401 | `JWT_SECRET` различается между сервисами | Привести к одному значению и перезапустить все сервисы |
| `ecom` не стартует с ошибкой подключения к базе шлюза | Не заданы или неверны `ECOM_TXPG_URL`, `ECOM_TXPG_USERNAME`, `ECOM_TXPG_PASSWORD`, либо с сервера нет сетевого доступа к базе шлюза | Задать переменные в `mp.env` (п. 8.3) и проверить доступ; остальные сервисы работают без `ecom` |
| Платёж проходит у эквайера, но плательщик возвращается не туда / транзакция висит в PENDING | `PBL_BASE_URL` указывает не на этот сервис (чужой домен, опечатка) | Исправить `PBL_BASE_URL` в `mp.env` и перезапустить `mp-pbl`; сервис такое не роняет, см. п. 8.3 |

---

> [!TIP]
> **Быстрая шпаргалка** для повседневного использования:
> ```bash
> # Перезапустить всё
> sudo systemctl restart mp-auth mp-directory mp-pbl nginx
> sudo systemctl restart mp-ecom   # если установлен ecom
> 
> # Проверить всё
> sudo systemctl status mp-auth mp-directory mp-pbl nginx postgresql
> 
> # Посмотреть логи (последние 50 строк)
> sudo journalctl -u mp-auth -n 50
> sudo journalctl -u mp-directory -n 50
> sudo journalctl -u mp-pbl -n 50
> sudo journalctl -u mp-ecom -n 50   # если установлен ecom
> 
> # Бэкап прямо сейчас
> /opt/merchant-portal/backup.sh
> ```
