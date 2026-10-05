# Hướng dẫn tạo mới một Microservice

Tài liệu này hướng dẫn các bước để tạo một Microservice mới trong `services/` tuân thủ kiến trúc chuẩn **DDD + Hexagonal + CQRS + Kafka** của dự án ViShop Platform.

---

## 1. Khởi tạo Service trong Gradle

Giả sử bạn cần tạo service mới là `catalog-service`:

### Bước 1: Đăng ký trong `settings.gradle.kts`
Mở [settings.gradle.kts](../settings.gradle.kts) và thêm service vào danh sách:
```kotlin
include(
    // Platform common modules
    "platform-common:common-domain",
    ...
    // Services
    "services:tenant-service",
    "services:audit-service",
    "services:identity-service",
    "services:catalog-service" // <-- Thêm service mới
)
```

### Bước 2: Tạo `build.gradle.kts` cho service mới
Tạo file `services/catalog-service/build.gradle.kts`:
```kotlin
plugins {
    id("org.springframework.boot")
}

dependencies {
    // Kế thừa toàn bộ nền tảng platform-common
    implementation(project(":platform-common:common-domain"))
    implementation(project(":platform-common:common-web"))
    implementation(project(":platform-common:common-security"))
    implementation(project(":platform-common:common-events"))
    implementation(project(":platform-common:common-persistence"))
    implementation(project(":platform-common:common-observability"))

    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")

    // Database & Migration
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
}
```

---

## 2. Tạo cấu trúc thư mục 4 tầng

Tạo các package tương ứng bên dưới thư mục `services/catalog-service/src/main/java/com/vishop/catalog/`:

```text
com.vishop.catalog/
├── CatalogApplication.java               # Main class (@SpringBootApplication)
├── domain/                               # 1. Domain Layer
│   ├── Product.java                      # Aggregate Root (extends AggregateRoot<UUID>)
│   ├── Sku.java                          # Value Object (implements ValueObject)
│   ├── ProductRepositoryPort.java        # Port interface
│   └── event/
│       └── ProductCreatedEvent.java      # Domain Event (implements DomainEvent)
├── application/                          # 2. Application Layer
│   ├── command/
│   │   ├── CreateProductCommand.java     # implements Command<ProductResponseDto>
│   │   └── CreateProductCommandHandler.java # implements CommandHandler<...>
│   ├── query/
│   │   ├── GetProductByIdQuery.java      # implements Query<ProductResponseDto>
│   │   └── GetProductByIdQueryHandler.java # implements QueryHandler<...>
│   └── dto/
│       ├── CreateProductRequest.java
│       └── ProductResponseDto.java
├── infrastructure/                       # 3. Infrastructure Layer
│   └── persistence/
│       ├── ProductJpaEntity.java         # JPA entity
│       ├── SpringDataProductRepository.java # JpaRepository
│       └── ProductRepositoryAdapter.java # implements ProductRepositoryPort
└── presentation/                         # 4. Presentation Layer
    └── rest/
        └── ProductController.java        # REST Controller dùng CommandBus & QueryBus
```

---

## 3. Tạo cấu hình và DB Migration

### File `src/main/resources/application.yml`
```yaml
server:
  port: 8083 # Cổng riêng cho service mới

spring:
  application:
    name: catalog-service

  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/vishop_catalog}
    username: ${SPRING_DATASOURCE_USERNAME:vishop}
    password: ${SPRING_DATASOURCE_PASSWORD:vishop_secret}
    driver-class-name: org.postgresql.Driver

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate

  flyway:
    enabled: true
    baseline-on-migrate: true

  kafka:
    bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      properties:
        enable.idempotence: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### File `src/main/resources/db/migration/V1__init_catalog_schema.sql`
Mỗi service cần khởi tạo bảng nghiệp vụ và luôn đi kèm 2 bảng nền tảng của microservice:
1. `outbox_events`: Để lưu trữ sự kiện cần gửi sang Kafka.
2. `consumed_messages`: Để bảo đảm tính idempotent khi consume sự kiện từ Kafka.

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Bảng nghiệp vụ
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- 2. Transactional Outbox
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    event_type VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_events_pending ON outbox_events (occurred_at ASC) WHERE status = 'PENDING';

-- 3. Consumed Messages (Idempotency)
CREATE TABLE IF NOT EXISTS consumed_messages (
    message_id VARCHAR(100) NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (message_id, consumer_group)
);
```

---

## 4. Biên dịch và kiểm tra

Chạy lệnh kiểm tra biên dịch service mới:
```powershell
.\gradlew.bat :services:catalog-service:compileJava
```
Khởi chạy service ở môi trường dev:
```powershell
.\gradlew.bat :services:catalog-service:bootRun
```
