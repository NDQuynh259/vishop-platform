# 🚀 ViShop Platform - Infrastructure & Deployment Architecture

Thư mục `infra/` quản lý toàn diện hạ tầng (Infrastructure) và cấu hình container cho toàn bộ hệ sinh thái **ViShop Platform**.

---

## 📁 Cấu trúc thư mục `infra/`

```
infra/
├── compose/                         # Các profile Docker Compose theo môi trường
│   ├── docker-compose.infra.yml     # CHỈ chạy Middleware hạ tầng (Postgres, Kafka, Redis, Jaeger, Prometheus, Grafana)
│   ├── docker-compose.dev.yml       # Full-stack Dev: Infra + 4 Microservices (hỗ trợ JDWP remote debugging 5000-5003)
│   └── docker-compose.prod.yml      # Full-stack Prod: Hardened, giới hạn CPU/RAM, log rotation
│
├── docker/                          # Chứa Dockerfiles
│   ├── Dockerfile.dev               # Dev Dockerfile (JDK runtime, remote debug)
│   ├── Dockerfile.prod              # Prod Dockerfile (Minimal Alpine JRE 21, Non-root)
│   ├── Dockerfile.api-gateway       # Specialized Dockerfile cho Gateway
│   ├── Dockerfile.iam-service       # Identity, tenant access và CMS
│   └── Dockerfile.audit-service     # Specialized Dockerfile cho Audit Service
│
├── scripts/                         # Script tự động hoá
│   ├── up.ps1                       # Khởi động (-Env infra | -Env dev | -Env prod [-Build])
│   ├── down.ps1                     # Dừng container (-Env infra | -Env dev | -Env prod | -Env all [-Volumes])
│   ├── build-images.ps1             # Build tất cả Docker images (Windows)
│   └── build-images.sh              # Build tất cả Docker images (Linux/CI)
│
├── postgres/                        # Cấu hình & Script cơ sở dữ liệu PostgreSQL
│   └── init-db.sql                  # Tạo sẵn database & user cho các microservice
│
├── kafka/                           # Cấu hình & Script cho Message Broker
│   └── init-topics.sh               # Script tự động khởi tạo core topics
│
├── prometheus/                      # Cấu hình thu thập Metrics
│   └── prometheus.yml
│
├── grafana/                         # Dashboard & Datasources
│   └── provisioning/
│       └── datasources/
│           └── datasources.yml
│
├── loki/                            # Centralized Logging
│   └── loki-config.yml
│
├── .env.dev                         # Cấu hình local do người chạy tạo từ .env.example
├── .env.example                     # File mẫu biến môi trường
└── .env.prod.example                # File mẫu biến môi trường cho Production
```

---

## 🛠️ Hướng dẫn vận hành

### 1. Dành cho Developer chạy app trên IDE (Local Java Dev)
Tạo `infra/.env.dev` từ [mẫu dev](.env.example) và điền `POSTGRES_PASSWORD` cùng
`GRAFANA_ADMIN_PASSWORD`. Sau đó khởi động hạ tầng dùng bởi các service chạy từ IDE.

```powershell
# Dùng script:
.\infra\scripts\up.ps1 -Env infra

# Hoặc dùng docker compose trực tiếp:
docker compose --env-file infra/.env.dev -f infra/compose/docker-compose.infra.yml up -d
```

### 2. Dành cho Developer chạy Full-Stack bằng Docker (Dev Profile)
Điền khóa access dùng chung trong `infra/.env.dev` và khóa refresh trong
`services/iam-service/.env.dev`. Các service khác có thể dùng `.env.dev` riêng
từ mẫu trong thư mục service. Xem [bảng cấu hình theo service](../docs/environment.md).

```powershell
# Tạo file IAM từ mẫu nếu chưa có, sau đó điền JWT_REFRESH_SECRET.
Copy-Item services/iam-service/.env.example services/iam-service/.env.dev
.\infra\scripts\up.ps1 -Env dev -Build
```

**Cổng Remote Debugging (JDWP):**
- API Gateway: `localhost:5000`
- Identity Service: `localhost:5001`
- Tenant Service: `localhost:5002`
- Audit Service: `localhost:5003`

### 3. Môi trường Production
```powershell
# Tạo các file từ mẫu nếu chưa có.
Copy-Item infra\.env.prod.example infra\.env.prod
Copy-Item services/iam-service/.env.example services/iam-service/.env.prod

# Điền các biến bắt buộc vào hai file trước khi khởi động.
.\infra\scripts\up.ps1 -Env prod -Build
```

---

## 🛑 Dừng hệ thống

```powershell
# Dừng stack infra:
.\infra\scripts\down.ps1 -Env infra

# Dừng stack dev:
.\infra\scripts\down.ps1 -Env dev

# Dừng tất cả và xoá volumes dữ liệu:
.\infra\scripts\down.ps1 -Env all -Volumes
```

---

## 🌐 Danh sách Cổng Dịch vụ (Dev Mode)

| Dịch vụ | Cổng Host | Mục đích |
| :--- | :--- | :--- |
| **API Gateway** | `http://localhost:8000` | Cổng Ingress công khai duy nhất cho Client |
| **Kafka UI** | `http://localhost:8088` | Giao diện trực quan xem Topic, Message, Consumer Group |
| **Jaeger UI** | `http://localhost:16686` | Giao diện Distributed Tracing xem Span ID & Request latency |
| **Grafana** | `http://localhost:3000` | Dashboard Metrics & Logs. Mật khẩu quản trị lấy từ `GRAFANA_ADMIN_PASSWORD` |
| **Prometheus** | `http://localhost:9090` | Web UI Prometheus kiểm tra PromQL & Scrape targets |
| **PostgreSQL** | `localhost:5432` | Cổng DB mở để kết nối DBeaver / DataGrip |
| **Redis** | `localhost:6379` | Cache & Token storage |
| **Identity Service** | `http://localhost:8080` (JDWP: 5001) | Dịch vụ xác thực & người dùng |
| **Tenant Service** | `http://localhost:8081` (JDWP: 5002) | Quản lý tổ chức đa người thuê |
| **Audit Service** | `http://localhost:8082` (JDWP: 5003) | Ghi nhận nhật ký kiểm toán hệ thống |
