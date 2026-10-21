# 🚀 ViShop Platform - Deployment Guide (Dev & Production)

Thư mục `deploy/` phân tách rõ ràng cấu hình container giữa môi trường **Development (Lập trình & Gỡ lỗi)** và **Production (Môi trường Vận hành Thực tế)**.

---

## ⚖️ Bảng so sánh Kiến trúc: Docker Dev vs Docker Production

| Tiêu chí | 🛠️ Môi trường DEV (`docker-compose.dev.yml`) | 🏭 Môi trường PROD (`docker-compose.prod.yml`) |
| :--- | :--- | :--- |
| **Mục đích** | Phát triển tính năng, gỡ lỗi nhanh, quan sát trực quan | Tối ưu hiệu năng, bảo mật cao, chịu tải, an toàn dữ liệu |
| **Dockerfile** | `Dockerfile.dev` (JDK runtime, hỗ trợ debug tool) | `Dockerfile.prod` (Alpine JRE siêu nhẹ, Non-root `appuser`) |
| **Spring Profile** | `SPRING_PROFILES_ACTIVE=dev` | `SPRING_PROFILES_ACTIVE=prod` |
| **Remote Debug (JDWP)** | ✅ Mở cổng debug **5000 - 5003** để gắn IDE gỡ lỗi | ❌ Tắt hoàn toàn cổng debug vì lý do an ninh |
| **Cổng mở ra ngoài (Host)** | Mở toàn bộ: Postgres (5432), Redis (6379), Kafka-UI (8088), Jaeger (16686)... | **Chỉ mở cổng Ingress**: API Gateway (8000) và Grafana (3000) |
| **Giới hạn tài nguyên (Limits)** | Không giới hạn để build và test nhanh | Giới hạn nghiêm ngặt CPU & RAM từng container (`limits` & `reservations`) |
| **Chính sách khởi động lại** | `restart: on-failure` (tránh loop khi đang debug) | `restart: always` (tự phục hồi khi có sự cố) |
| **Quản lý Log (Driver)** | Default console log (human-friendly) | `json-file` có xoay vòng (`max-size: 50m`, `max-file: 5`) tránh đầy đĩa |
| **Distributed Tracing** | Sampling **1.0 (100%)** phục vụ phân tích log | Sampling **0.1 (10%)** tiết kiệm dung lượng lưu trữ |
| **Biến môi trường** | `.env.dev` (giá trị mặc định tiện lợi) | `.env.prod` (mật khẩu mạnh, bảo mật) |

---

## 📁 Cấu trúc thư mục `deploy/`

```
deploy/
├── docker/
│   ├── Dockerfile.dev               # Dockerfile dành cho Dev (JDWP Remote Debug 5005)
│   ├── Dockerfile.prod              # Dockerfile dành cho Prod (Alpine JRE 21, Non-root)
│   ├── Dockerfile                   # Multi-stage Dockerfile tổng quát
│   ├── Dockerfile.api-gateway       # Dockerfile riêng cho API Gateway
│   ├── Dockerfile.identity-service  # Dockerfile riêng cho Identity Service
│   ├── Dockerfile.tenant-service    # Dockerfile riêng cho Tenant Service
│   └── Dockerfile.audit-service     # Dockerfile riêng cho Audit Service
├── scripts/
│   ├── up.ps1                       # Khởi động với tham số -Env dev hoặc -Env prod
│   ├── down.ps1                     # Dừng container theo môi trường
│   ├── build-images.ps1             # Build toàn bộ docker images
│   └── build-images.sh              # Build images trên Linux/CI
├── docker-compose.dev.yml           # Compose Dev: Full stack + Debug ports + Dev tools
├── docker-compose.prod.yml          # Compose Prod: Hardened, Resource limits, Private internal net
├── .env.dev                         # File cấu hình môi trường Dev
├── .env.prod.example                # File mẫu cấu hình môi trường Production
└── README.md
```

---

## 🛠️ Hướng dẫn vận hành

### 1. Môi trường Development (DEV)

Phù hợp cho lập trình viên cần chạy toàn bộ hệ thống trên máy cá nhân và debug:

```powershell
# Bằng script tự động (khuyên dùng):
.\deploy\scripts\up.ps1 -Env dev -Build

# Hoặc bằng lệnh Docker Compose:
docker compose --env-file deploy/.env.dev -f deploy/docker-compose.dev.yml up -d --build
```

#### Cổng Remote Debugging cho từng Service trong DEV:
Bạn có thể cấu hình **Remote JVM Debug** trong IntelliJ IDEA / VS Code:
* **API Gateway**: `localhost:5000`
* **Identity Service**: `localhost:5001`
* **Tenant Service**: `localhost:5002`
* **Audit Service**: `localhost:5003`

#### Giao diện quản trị & quan sát trong DEV:
* **API Gateway Entrypoint**: `http://localhost:8000`
* **Kafka UI**: `http://localhost:8088` (Xem Topic, Consumer groups, Messages)
* **Jaeger Tracing UI**: `http://localhost:16686` (Xem chi tiết TraceId/SpanId)
* **Grafana Dashboard**: `http://localhost:3000` (User: `admin` / Pass: `admin`)
* **Prometheus Metrics**: `http://localhost:9090`
* **PostgreSQL**: `localhost:5432` (kết nối trực tiếp bằng DBeaver/DataGrip)
* **Redis**: `localhost:6379` (kết nối bằng Redis Insight)

Để dừng môi trường Dev:
```powershell
.\deploy\scripts\down.ps1 -Env dev
```

---

### 2. Môi trường Production (PROD)

Phù hợp khi triển khai lên máy chủ staging hoặc production:

#### Bước 1: Tạo file cấu hình bảo mật `.env.prod`
```powershell
Copy-Item deploy\.env.prod.example deploy\.env.prod
```
Chỉnh sửa `deploy\.env.prod` với mật khẩu Database, Grafana và cấu hình thực tế.

#### Bước 2: Khởi động hệ thống Production
```powershell
# Bằng script tự động:
.\deploy\scripts\up.ps1 -Env prod -Build

# Hoặc bằng lệnh Docker Compose:
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --build
```

#### Điểm an toàn trong PROD:
- Chỉ cổng `8000` (API Gateway) và `3000` (Grafana) mở ra bên ngoài.
- Toàn bộ cơ sở dữ liệu PostgreSQL, Kafka Broker, Redis và các Microservices nội bộ chạy hoàn toàn trong mạng kín `vishop-prod-network`.
- Có cấu hình Resource Limits (CPU/RAM) và xoay vòng log (Log Rotation) tránh tràn ổ đĩa.

Để dừng môi trường Prod:
```powershell
.\deploy\scripts\down.ps1 -Env prod
```
