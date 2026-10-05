# Hướng dẫn triển khai (Deployment)

Tài liệu hướng dẫn đóng gói Container Docker và triển khai các Microservices của **ViShop Platform**.

---

## 1. Yêu cầu môi trường triển khai

* **Docker Engine 24+** và **Docker Compose v2** (hoặc Kubernetes 1.28+).
* **Container Registry** (GitHub Packages, AWS ECR, GitLab Container Registry).
* **Managed Services**:
  * PostgreSQL 16 (AWS RDS, Cloud SQL hoặc tự host).
  * Apache Kafka (AWS MSK, Confluent Cloud, Strimzi Kafka Operator hoặc Kafka KRaft cluster).
  * Redis 7 (AWS ElastiCache hoặc Redis Cluster).

---

## 2. Dockerfile chuẩn cho Spring Boot Microservice

Mỗi microservice sử dụng Docker Multi-stage build với Gradle cache và Eclipse Temurin JRE 21 siêu nhẹ:

```dockerfile
# Stage 1: Build Jar
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY platform-common ./platform-common
COPY services/tenant-service ./services/tenant-service

RUN ./gradlew :services:tenant-service:bootJar --no-daemon

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Tạo non-root user vì lý do bảo mật
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/services/tenant-service/build/libs/tenant-service-*.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE="prod"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 3. Các biến môi trường Production quan trọng

| Biến môi trường | Ý nghĩa | Ví dụ |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | JDBC URL kết nối tới Database của Service | `jdbc:postgresql://db-host:5432/vishop_tenant` |
| `SPRING_DATASOURCE_USERNAME` | Username DB | `vishop_prod` |
| `SPRING_DATASOURCE_PASSWORD` | Password DB | `[SECURE_PASSWORD]` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS`| Danh sách Kafka Brokers | `kafka-1:9092,kafka-2:9092` |
| `SERVER_PORT` | Cổng HTTP của service | `8081` |
| `JAVA_OPTS` | Tinh chỉnh JVM memory và GC | `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0` |

---

## 4. Quản lý Database Migration (Flyway)

Trong kiến trúc Spring Boot của ViShop, **Flyway** được tích hợp sẵn trong runtime (`spring.flyway.enabled: true`). Khi container khởi động:
1. Service tự động kết nối database.
2. Kiểm tra bảng `flyway_schema_history`.
3. Tự động áp dụng tuần tự các script `V{version}__{description}.sql` chưa chạy trước khi mở cổng HTTP đón traffic.

---

## 5. Health Check & Observability trong Kubernetes

Mỗi container cung cấp các endpoint Actuator phục vụ Kubernetes Probes:
* **Liveness Probe**:
  `GET /actuator/health/liveness` (Kiểm tra container còn sống)
* **Readiness Probe**:
  `GET /actuator/health/readiness` (Kiểm tra DB & Kafka đã kết nối sẵn sàng nhận request)
* **Metrics**:
  `GET /actuator/prometheus` (Tích hợp Prometheus scraping)
