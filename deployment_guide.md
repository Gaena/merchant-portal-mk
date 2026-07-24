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
15. [Обновление системы](#15-обновление-системы)
16. [Резервное копирование](#16-резервное-копирование)
17. [Мониторинг и логи](#17-мониторинг-и-логи)
18. [Устранение неполадок](#18-устранение-неполадок)
19. [Справочник: порты и сервисы](#19-справочник-порты-и-сервисы)

---

## 1. Обзор системы

Merchant Portal — это веб-приложение, состоящее из **4 компонентов**:

| Компонент | Описание | Порт |
|-----------|----------|------|
| **auth** | Авторизация пользователей, JWT-токены | `8081` |
| **directory** | Справочник компаний и терминалов | `8082` |
| **pbl** | Pay-By-Link — создание платёжных ссылок | `8080` |
| **frontend** | Веб-интерфейс (React + Vite) | `3000` (разработка) / через Nginx (продакшн) |

```
┌────────────────────────────────────────────────────────┐
│                     Интернет                           │
│                        │                               │
│                   ┌────▼────┐                           │
│                   │  Nginx  │  (порт 80/443)            │
│                   └────┬────┘                           │
│            ┌───────────┼───────────┐                    │
│            │           │           │                    │
│      Статические   /api/v1/auth  /api/v1/companies     │
│      файлы         /api/v1/users /api/v1/terminals     │
│      (frontend)        │        /api/v1/audit-logs     │
│            │           │           │                    │
│            │      ┌────▼────┐ ┌────▼─────┐              │
│            │      │  Auth   │ │Directory │              │
│            │      │ :8081   │ │  :8082   │              │
│            │      └─────────┘ └──────────┘              │
│            │                                            │
│            │      /api/v1/payment-links                  │
│            │      /api/v1/transactions                   │
│            │           │                                │
│            │      ┌────▼────┐                            │
│            │      │   PBL   │                            │
│            │      │ :8080   │                            │
│            │      └─────────┘                            │
│            │                                            │
│      ┌─────▼──────┐    ┌──────────────┐                 │
│      │  dist/      │    │  PostgreSQL  │                 │
│      │ (HTML/JS)   │    │   :5432      │                 │
│      └─────────────┘    └──────────────┘                 │
└────────────────────────────────────────────────────────┘
```

> [!IMPORTANT]
> Все три бэкенд-сервиса используют **одну и ту же базу данных PostgreSQL**, но разные таблицы. Миграции (создание таблиц) выполняются автоматически при первом запуске через Liquibase.

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

### 4.5. Установка PostgreSQL 15

```bash
# Добавляем официальный репозиторий PostgreSQL
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'
curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/postgresql.gpg
sudo apt update

# Устанавливаем PostgreSQL
sudo apt install -y postgresql-15

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

### 5.2. Разрешение подключения по паролю

Откройте конфигурационный файл PostgreSQL:

```bash
sudo nano /etc/postgresql/15/main/pg_hba.conf
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

### 7.3. Копирование JAR-файлов в рабочую директорию

```bash
# Создаём директорию для запускаемых файлов
sudo mkdir -p /opt/merchant-portal/deploy

# Копируем JAR-файлы
cp auth/build/libs/auth-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/auth.jar
cp directory/build/libs/directory-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/directory.jar
cp pbl/build/libs/pbl-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/pbl.jar
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
    url: jdbc:postgresql://localhost:5432/merchant_portal
    driver-class-name: org.postgresql.Driver
    username: postgres
    password: ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ
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
  security:
    jwt:
      secret: ЗАМЕНИТЕ_НА_СВОЙ_СЕКРЕТНЫЙ_КЛЮЧ_BASE64
      expiration-ms: 86400000

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
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
    url: jdbc:postgresql://localhost:5432/merchant_portal
    driver-class-name: org.postgresql.Driver
    username: postgres
    password: ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ
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
  security:
    jwt:
      secret: ЗАМЕНИТЕ_НА_СВОЙ_СЕКРЕТНЫЙ_КЛЮЧ_BASE64

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
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
    url: jdbc:postgresql://localhost:5432/merchant_portal
    driver-class-name: org.postgresql.Driver
    username: postgres
    password: ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ
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
  base-url: https://ВАШ_ДОМЕН/
  security:
    api-token: ЗАМЕНИТЕ_НА_СЛОЖНЫЙ_API_ТОКЕН
    api-token-enabled: true
    jwt:
      secret: ЗАМЕНИТЕ_НА_СВОЙ_СЕКРЕТНЫЙ_КЛЮЧ_BASE64
  provider:
    gateway-base-url: https://test.millikart.az:8083/
    api-base-url: http://test.millikart.az:8000/
    create-order-path: /order
    exec-tran-path: /order/{orderId}/exec-tran
    get-order-path: /order/{orderId}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
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

### 8.2. Что нужно заменить в конфигурации

> [!CAUTION]
> **Обязательно** замените следующие значения во ВСЕХ трёх файлах:

| Плейсхолдер | Где менять | На что заменить | Пример |
|---|---|---|---|
| `ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ` | Все 3 файла | Пароль из шага 5.1 | `Mp$ecure_2026!Prod` |
| `ЗАМЕНИТЕ_НА_СВОЙ_СЕКРЕТНЫЙ_КЛЮЧ_BASE64` | Все 3 файла | **Одинаковый** ключ во всех! | см. ниже |
| `ВАШ_ДОМЕН` | Только PBL | Ваш домен | `mp.millikart.az` |
| `ЗАМЕНИТЕ_НА_СЛОЖНЫЙ_API_ТОКЕН` | Только PBL | Любая длинная случайная строка | `pbl-prod-a7f3e9b2c4d1` |

### 8.3. Как сгенерировать JWT-секрет

Выполните команду:

```bash
openssl rand -base64 64 | tr -d '\n'
```

Скопируйте результат и вставьте вместо `ЗАМЕНИТЕ_НА_СВОЙ_СЕКРЕТНЫЙ_КЛЮЧ_BASE64` во **ВСЕХ ТРЁХ** конфигурационных файлах.

> [!WARNING]
> JWT-секрет должен быть **одинаковым** во всех трёх сервисах! Иначе авторизация не будет работать — сервисы не смогут проверять токены друг друга.

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

### 9.5. Запуск всех сервисов

```bash
# Перечитываем конфигурацию systemd (обязательно после создания новых юнитов)
sudo systemctl daemon-reload

# Включаем автозапуск при старте сервера
sudo systemctl enable mp-auth mp-directory mp-pbl

# Запускаем сервисы (порядок важен!)
sudo systemctl start mp-auth

# Ждём 15 секунд, пока auth инициализируется и создаст таблицы
sleep 15

sudo systemctl start mp-directory

sleep 10

sudo systemctl start mp-pbl
```

### 9.6. Проверка статуса

```bash
# Проверяем все три сервиса
sudo systemctl status mp-auth
sudo systemctl status mp-directory
sudo systemctl status mp-pbl
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

    # PBL-сервис (платёжные ссылки и транзакции)
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

    # PBL — страница оплаты (Thymeleaf-шаблоны)
    location /pay/ {
        proxy_pass http://pbl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # ───── Swagger UI (документация API) ─────
    # Доступна только с локального сервера. Уберите ограничение, если нужен внешний доступ.
    location /swagger-ui.html {
        proxy_pass http://pbl_backend;
        allow 127.0.0.1;
        deny all;
    }

    # ───── Actuator Health Checks ─────
    location /actuator/ {
        proxy_pass http://auth_backend;
        allow 127.0.0.1;
        deny all;
    }

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
curl -s http://localhost:8081/actuator/health | python3 -m json.tool

# Directory-сервис
curl -s http://localhost:8082/actuator/health | python3 -m json.tool

# PBL-сервис
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Для каждого сервиса вы должны увидеть:

```json
{
    "status": "UP"
}
```

### 14.2. Проверяем фронтенд

Откройте в браузере:

```
https://ВАШ_ДОМЕН
```

Вы должны увидеть страницу входа (логин) Merchant Portal.

### 14.3. Проверяем API через Nginx

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
sudo systemctl stop mp-pbl mp-directory mp-auth

# 5. Копируем новые JAR-файлы
cp auth/build/libs/auth-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/auth.jar
cp directory/build/libs/directory-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/directory.jar
cp pbl/build/libs/pbl-0.0.1-SNAPSHOT.jar /opt/merchant-portal/deploy/pbl.jar

# 6. Восстанавливаем права
sudo chown mpuser:mpuser /opt/merchant-portal/deploy/*.jar

# 7. Запускаем сервисы обратно
sudo systemctl start mp-auth
sleep 15
sudo systemctl start mp-directory
sleep 10
sudo systemctl start mp-pbl

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
sudo systemctl stop mp-pbl mp-directory mp-auth

# Восстанавливаем базу
gunzip -c /opt/merchant-portal/backups/merchant_portal_ДАТА.sql.gz | \
  PGPASSWORD="ВАШ_ПАРОЛЬ_БАЗЫ_ДАННЫХ" psql -U postgres -h localhost merchant_portal

# Запускаем обратно
sudo systemctl start mp-auth
sleep 15
sudo systemctl start mp-directory mp-pbl
```

---

## 17. Мониторинг и логи

### 17.1. Просмотр логов

```bash
# Логи конкретного сервиса (в реальном времени)
sudo journalctl -u mp-auth -f
sudo journalctl -u mp-directory -f
sudo journalctl -u mp-pbl -f

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
| Auth | `http://localhost:8081/actuator/health` | Статус сервиса + подключение к БД |
| Directory | `http://localhost:8082/actuator/health` | Статус сервиса + подключение к БД |
| PBL | `http://localhost:8080/actuator/health` | Статус сервиса + подключение к БД |

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

1. Проверьте, запущены ли сервисы: `sudo systemctl status mp-auth mp-directory mp-pbl`
2. Если `inactive (dead)` — запустите: `sudo systemctl start mp-auth`
3. Проверьте порты: `ss -tlnp | grep -E '8080|8081|8082'`

### ❌ Проблема: Страница логина не открывается

1. Проверьте Nginx: `sudo systemctl status nginx`
2. Проверьте файлы фронтенда: `ls /var/www/merchant-portal/index.html`
3. Проверьте файрвол: `sudo ufw status`
4. Проверьте DNS: `nslookup ВАШ_ДОМЕН`

### ❌ Проблема: «401 Unauthorized» после логина

1. Проверьте, что JWT-секрет **одинаковый** во всех трёх конфигурациях
2. Проверьте, что Auth-сервис работает: `curl http://localhost:8081/actuator/health`

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

### Внешние порты (доступны из интернета)

| Порт | Сервис | Назначение |
|------|--------|------------|
| `22` | SSH | Удалённый доступ |
| `80` | Nginx | HTTP (перенаправляет на 443) |
| `443` | Nginx | HTTPS (основной) |

### API-маршруты

| Маршрут | Бэкенд | Описание |
|---------|--------|----------|
| `/api/v1/auth/**` | Auth (:8081) | Логин, регистрация |
| `/api/v1/users/**` | Auth (:8081) | Управление пользователями |
| `/api/v1/companies/**` | Directory (:8082) | Управление компаниями |
| `/api/v1/terminals/**` | Directory (:8082) | Управление терминалами |
| `/api/v1/audit-logs/**` | Directory (:8082) | Журнал аудита |
| `/api/v1/payment-links/**` | PBL (:8080) | Платёжные ссылки |
| `/api/v1/transactions/**` | PBL (:8080) | Транзакции |

### Конфигурационные файлы

| Файл | Назначение |
|------|------------|
| `/opt/merchant-portal/config/auth-application.yaml` | Настройки Auth-сервиса |
| `/opt/merchant-portal/config/directory-application.yaml` | Настройки Directory-сервиса |
| `/opt/merchant-portal/config/pbl-application.yaml` | Настройки PBL-сервиса |
| `/etc/nginx/sites-available/merchant-portal` | Настройки веб-сервера |
| `/etc/systemd/system/mp-auth.service` | Systemd-юнит Auth |
| `/etc/systemd/system/mp-directory.service` | Systemd-юнит Directory |
| `/etc/systemd/system/mp-pbl.service` | Systemd-юнит PBL |

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

> [!TIP]
> **Быстрая шпаргалка** для повседневного использования:
> ```bash
> # Перезапустить всё
> sudo systemctl restart mp-auth mp-directory mp-pbl nginx
> 
> # Проверить всё
> sudo systemctl status mp-auth mp-directory mp-pbl nginx postgresql
> 
> # Посмотреть логи (последние 50 строк)
> sudo journalctl -u mp-auth -n 50
> sudo journalctl -u mp-directory -n 50
> sudo journalctl -u mp-pbl -n 50
> 
> # Бэкап прямо сейчас
> /opt/merchant-portal/backup.sh
> ```
