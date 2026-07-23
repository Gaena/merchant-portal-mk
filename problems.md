# Технический Долг и Архитектурные Планы на Будущее (Known Issues & Future Plans)

В данном документе зафиксированы архитектурные допущения и проблемы, отложенные для реализации на последующих этапах развития платформы **MP**.

---

## 1. Архитектура и Модульность (Shared Database Microservices)

### 📌 Проблема
В настоящее время микросервисы (`:auth`, `:directory`, `:pbl`) используют единую физическую базу данных PostgreSQL (`jdbc:postgresql://localhost:5432/postgres`) и общий набор таблиц (`companies`, `users`, `terminals`).
- Сущность `Company` задублирована в модулях `:auth` и `:directory`.
- Сущность `Terminal` задублирована в модулях `:directory` и `:pbl`.
- Сервис `pbl` напрямую читает из таблицы `terminals`, минуя REST API сервиса `directory`.

### 🎯 План Решения на Будущее
1. **Изоляция БД (Bounded Contexts)**: Выделить для каждого микросервиса отдельную базу данных или схему (`auth_db`, `directory_db`, `pbl_db`).
2. **Межсервисное взаимодействие**: Реализовать запросы между сервисами (например, получение данных терминала из `pbl` в `directory`) через REST (OpenFeign / RestClient) или gRPC с кэшированием результатов.
3. **Event-Driven Асинхронность**: При изменении статусов компаний или терминалов в `directory` публиковать события в Message Broker (RabbitMQ / Apache Kafka) для синхронизации в других сервисах.

---

## 2. Безопасность: Шифрование Секретов Терминалов (Secret Encryption)

### 📌 Проблема
Пароли эквайринговых терминалов MilliKart (поля `password` в таблице `terminals`) хранятся в базе данных в открытом виде (Plain Text).

### 🎯 План Решения на Будущее
1. **Application-Level Encryption (AES-256-GCM)**: Внедрить JPA `@Convert` / `AttributeConverter` для автоматического шифрования/расшифрования чувствительных полей терминалов при сохранении/чтении из БД.
2. **Vault / Secret Manager Integration**: Вынести мастер-ключ шифрования в HashiCorp Vault, AWS Secrets Manager или переменные окружения K8s Secrets.

---

## 3. Инфраструктура: Dockerification & Orchestration

### 📌 Проблема
В проекте отсутствуют стандартизированные контейнеры Docker (`Dockerfile`) для сборки образов каждого микросервиса, а также декларативные манифесты запуска окружения (`docker-compose.yml`, Helm-чарты/Kubernetes-манифесты).

### 🎯 План Решения на Будущее
1. **Multi-Stage Dockerfiles**: Создать оптимизированные `Dockerfile` на базе Eclipse Temurin 21 (Alpine / Distroless).
2. **Docker Compose**: Подготовить `docker-compose.yml` для поднятия всего стека локально (PostgreSQL + Auth (8081) + Directory (8082) + PBL (8080/8083)).
3. **CI/CD Pipeline**: Настроить автоматическую сборку образцов и прогонку интеграционных тестов в GitHub Actions / GitLab CI.
