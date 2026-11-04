# Thiết kế Subscription

Hai module `subscription` và `payment` dùng cùng DB/transaction manager. Composition
nối contract `application/api`, không truy cập trực tiếp repository/entity của module kia.

```mermaid
flowchart LR
    Gateway --> Checkout[Checkout composition]
    Checkout --> Subscription[Subscription API]
    Checkout --> Payment[Payment API]
    SePay --> Inbox[Durable webhook inbox]
    Inbox --> Worker[Payment worker]
    Worker --> Payment
    Payment --> Apply[Payment composition]
    Apply --> Subscription
    Subscription --> DB[(Subscription DB)]
    Payment --> DB
    DB --> Outbox[Outbox relay]
    Outbox --> Kafka[vishop.billing.events]
```

`SubscriptionApi.applyPayment` tham gia transaction của payment handler để thay đổi tiền,
cấp kỳ và outbox rollback cùng nhau. QR đi qua port/DTO độc lập.
Service nhận context Gateway ký riêng, không truy vấn DB của IAM.
Contract `/api/v1/billing/**`, `billing.*` và topic billing tiếp tục được giữ.

Chi tiết hiện hành: [README service](../README.md);
thiết kế quan hệ/khóa: [Database](database-design.md).
