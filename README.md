# ViShop Platform

Nền tảng thương mại điện tử đa kênh và quản lý bán hàng đa tổ chức (Multi-Tenant E-Commerce Platform) được phát triển theo kiến trúc **Microservices, Domain-Driven Design (DDD), CQRS và Apache Kafka** trên nền **Java 21 LTS** và **Spring Boot 3.3.x**.

---

## 1. Công nghệ nền tảng (Tech Stack)

* **Ngôn ngữ**: Java 21 LTS
* **Framework**: Spring Boot 3.3.5
* **Build Tool**: Gradle Multi-Module (Kotlin DSL)
* **Kiến trúc**: Domain-Driven Design (DDD), Hexagonal Architecture (Ports & Adapters), CQRS
* **Event Broker**: Apache Kafka (KRaft mode — không cần Zookeeper)
* **Reliability Pattern**: Transactional Outbox Pattern + Idempotent Consumer
* **Cơ sở dữ liệu**: PostgreSQL 16 (Database-per-service), Flyway Migrations, Spring Data JPA
* **Cache & Lock**: Redis 7
* **API Standard**: RESTful, RFC 9457 Problem Details, Cursor Pagination
* **Quan sát & Giám sát**: Spring Boot Actuator, Micrometer Prometheus, OpenTelemetry, SLF4J MDC
* **Kiểm thử**: JUnit 5, Testcontainers

---

## 2. Cấu trúc Monorepo

```text
vishop-platform/
├── platform-common/                     # Thư viện nền tảng dùng chung
│   ├── common-domain/                   # DDD Core: AggregateRoot, Entity, VO, Event, CQRS interfaces, DomainException
│   ├── common-web/                      # Web & CQRS Bus: SpringCommandBus, SpringQueryBus, Problem Details (RFC 9457)
│   ├── common-security/                 # Multi-Tenancy: TenantContext, TenantContextFilter
│   ├── common-events/                   # Kafka Backbone: EventEnvelope, KafkaTopicNaming, KafkaEventPublisher, DLQ
│   ├── common-persistence/              # Transactional Outbox, OutboxPublisher, ConsumedMessage (Idempotency)
│   ├── common-observability/            # Spring Actuator & Prometheus
│   └── common-test/                     # Testcontainers helpers
│
├── services/                            # Các Microservices độc lập (Bounded Contexts)
│   ├── tenant-service/                  # Quản lý Tenant, tổ chức, gian hàng (DDD + CQRS + Outbox)
│   ├── identity-service/                # Quản lý người dùng, tài khoản & xác thực
│   └── audit-service/                   # Kafka Event Consumer ghi nhận lịch sử audit log
│
├── infra/
│   └── docker-compose/                  # Hạ tầng chạy local: Kafka KRaft, Kafka UI, Postgres 16, Redis 7
│
├── build.gradle.kts                      # Cấu hình Gradle chung
├── settings.gradle.kts                   # Khai báo subprojects
└── docs/                                # Toàn bộ tài liệu kiến trúc & hướng dẫn
```

---

## 3. Khởi động nhanh (Quick Start)

### 3.1. Khởi động hạ tầng cục bộ (Docker Compose)
```powershell
docker compose -f infra/docker-compose/docker-compose.yml up -d
```
* **Kafka Broker**: `localhost:29092`
* **Kafka UI**: [http://localhost:8088](http://localhost:8088)
* **PostgreSQL 16**: `localhost:5432` (user/pass: `vishop`/`vishop_secret`)
* **Redis 7**: `localhost:6379`

### 3.2. Biên dịch & Kiểm thử
```powershell
# Biên dịch
.\gradlew.bat compileJava

# Chạy toàn bộ Unit & Integration tests
.\gradlew.bat test
```

### 3.3. Khởi chạy Microservice
```powershell
# Chạy tenant-service (Port 8081)
.\gradlew.bat :services:tenant-service:bootRun

# Chạy audit-service (Port 8082)
.\gradlew.bat :services:audit-service:bootRun
```

---

## 4. Tài liệu chi tiết trong `docs/`

* [Kiến trúc hệ thống (Architecture)](./docs/architecture.md)
* [Cấu trúc giải phẫu Microservice (Domain Module Anatomy)](./docs/domain-module-anatomy.md)
* [Tạo mới Microservice](./docs/creating-a-module.md)
* [Hướng dẫn cài đặt & khởi chạy (Getting Started)](./docs/getting-started.md)
* [Chuẩn mực lập trình (Code Standards)](./docs/code-standards.md)
* [Xử lý ngoại lệ chuẩn RFC 9457 (Error Handling)](./docs/error-handling.md)
* [Kiến trúc Đa người thuê (Multi-Tenancy)](./docs/tenancy.md)
* [Chiến lược Microservices & Bounded Contexts](./docs/scaling-to-services.md)
* [Giám sát hệ thống (Observability)](./docs/observability.md)
* [Khắc phục sự cố (Troubleshooting)](./docs/troubleshooting.md)
* [Hướng dẫn triển khai (Deployment)](./docs/deployment.md)
