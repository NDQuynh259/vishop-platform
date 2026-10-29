CREATE TABLE billing_prices (
id uuid PRIMARY KEY,
plan_code varchar(30) NOT NULL CHECK(plan_code IN ('BASIC',
'PRO',
'BUSINESS')),
name varchar(200) NOT NULL,
cycle varchar(20) NOT NULL CHECK(cycle IN ('MONTHLY',
'YEARLY')),
amount_vnd bigint NOT NULL CHECK(amount_vnd>0),
shop_limit integer NOT NULL CHECK(shop_limit>0),
monthly_orders bigint NOT NULL CHECK(monthly_orders>0),
additional_members integer NOT NULL CHECK(additional_members>=0),
active boolean NOT NULL
);

CREATE TABLE billing_accounts (
tenant_id uuid PRIMARY KEY,
plan_code varchar(30) NOT NULL DEFAULT 'FREE',
cycle varchar(20),
anchor_at timestamptz,
purchased_cycles integer NOT NULL DEFAULT 0,
ends_at timestamptz,
revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0)
);

CREATE TABLE billing_orders (
id uuid PRIMARY KEY,
tenant_id uuid NOT NULL,
actor_id uuid NOT NULL,
price_id uuid NOT NULL REFERENCES billing_prices(id),
plan_code varchar(30) NOT NULL,
plan_name varchar(200) NOT NULL,
cycle varchar(20) NOT NULL,
amount_vnd bigint NOT NULL CHECK(amount_vnd>0),
shop_limit integer NOT NULL,
monthly_orders bigint NOT NULL,
additional_members integer NOT NULL,
payment_code varchar(50) NOT NULL UNIQUE,
idempotency_key varchar(100) NOT NULL,
expected_revision bigint NOT NULL,
received_vnd bigint NOT NULL DEFAULT 0 CHECK(received_vnd>=0),
state varchar(30) NOT NULL CHECK(state IN ('AWAITING_PAYMENT',
'PARTIALLY_PAID',
'FULFILLED',
'EXPIRED',
'CANCELLED',
'REQUIRES_REVIEW')),
created_at timestamptz NOT NULL,
expires_at timestamptz NOT NULL,
term_starts_at timestamptz,
term_ends_at timestamptz,
review_reason varchar(100),
UNIQUE(tenant_id,
actor_id,
idempotency_key)
);

CREATE TABLE subscription_terms (
order_id uuid PRIMARY KEY REFERENCES billing_orders(id),
tenant_id uuid NOT NULL,
starts_at timestamptz NOT NULL,
ends_at timestamptz NOT NULL CHECK(ends_at>starts_at)
);

CREATE TABLE sepay_inbox (
id bigint PRIMARY KEY,
raw_payload text NOT NULL,
review_reason varchar(100),
payload_hash varchar(64) NOT NULL,
status varchar(30) NOT NULL CHECK(status IN ('PENDING',
'DONE',
'REVIEW')),
received_at timestamptz NOT NULL,
processed_at timestamptz
);

CREATE TABLE bank_payments (
id bigint PRIMARY KEY REFERENCES sepay_inbox(id),
order_id uuid REFERENCES billing_orders(id),
account_number varchar(100) NOT NULL,
bank varchar(100) NOT NULL,
reference_code varchar(200),
amount_vnd bigint NOT NULL CHECK(amount_vnd>0),
transfer_type varchar(10) NOT NULL,
status varchar(30) NOT NULL,
review_reason varchar(100),
occurred_at timestamptz NOT NULL,
created_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX uq_billing_open_order ON billing_orders(tenant_id) WHERE state IN ('AWAITING_PAYMENT','PARTIALLY_PAID');
CREATE INDEX idx_billing_tenant_orders ON billing_orders(tenant_id,created_at DESC,id DESC);
CREATE INDEX idx_sepay_inbox_pending ON sepay_inbox(received_at,id) WHERE status='PENDING';
CREATE INDEX idx_bank_payments_review ON bank_payments(status,created_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    event_type VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count INT NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    error_message VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_billing_outbox_pending ON outbox_events(occurred_at,id) WHERE status='PENDING';

CREATE UNIQUE INDEX uq_bank_reference ON bank_payments(lower(bank),account_number,reference_code) WHERE reference_code IS NOT NULL AND reference_code<>'';
