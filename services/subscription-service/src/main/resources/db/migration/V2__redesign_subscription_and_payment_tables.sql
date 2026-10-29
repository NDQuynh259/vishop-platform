-- Preserve V1 and upgrade existing data in one Flyway/PostgreSQL transaction.
ALTER TABLE billing_prices RENAME TO subscription_prices;
ALTER TABLE billing_accounts RENAME TO subscriptions;
ALTER TABLE billing_orders RENAME TO subscription_orders;
ALTER TABLE sepay_inbox RENAME TO sepay_webhook_inbox;
ALTER TABLE bank_payments RENAME TO payment_transactions;

CREATE TABLE subscription_plans (
    code VARCHAR(30) PRIMARY KEY CHECK (code IN ('FREE', 'BASIC', 'PRO', 'BUSINESS')),
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO subscription_plans(code, name) VALUES
    ('FREE', 'Free'), ('BASIC', 'Basic'), ('PRO', 'Pro'), ('BUSINESS', 'Business');

ALTER TABLE subscription_prices
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT fk_subscription_price_plan FOREIGN KEY (plan_code) REFERENCES subscription_plans(code),
    ADD CONSTRAINT uq_subscription_price_plan UNIQUE (id, plan_code),
    ADD CONSTRAINT ck_subscription_price_name CHECK (btrim(name) <> '');
CREATE INDEX idx_subscription_prices_available ON subscription_prices(plan_code, cycle, amount_vnd) WHERE active;

ALTER TABLE subscriptions
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT fk_subscription_plan FOREIGN KEY (plan_code) REFERENCES subscription_plans(code),
    ADD CONSTRAINT ck_subscription_period CHECK (
        (plan_code = 'FREE' AND cycle IS NULL AND anchor_at IS NULL AND purchased_cycles = 0 AND ends_at IS NULL)
        OR (plan_code <> 'FREE' AND cycle IS NOT NULL AND cycle IN ('MONTHLY', 'YEARLY') AND anchor_at IS NOT NULL
            AND purchased_cycles > 0 AND ends_at IS NOT NULL AND ends_at > anchor_at)
    );

DROP INDEX uq_billing_open_order;
ALTER TABLE subscription_orders
    ADD COLUMN order_status VARCHAR(20),
    ADD COLUMN payment_status VARCHAR(20),
    ADD COLUMN fulfillment_status VARCHAR(20),
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Terms are the evidence of fulfillment, even if V1's combined state later became REVIEW.
UPDATE subscription_orders o SET term_starts_at = t.starts_at, term_ends_at = t.ends_at
FROM subscription_terms t WHERE t.order_id = o.id;
UPDATE subscription_orders o SET
    order_status = CASE
        WHEN EXISTS (SELECT 1 FROM subscription_terms t WHERE t.order_id = o.id) THEN 'COMPLETED'
        WHEN state = 'EXPIRED' THEN 'EXPIRED'
        WHEN state = 'CANCELLED' THEN 'CANCELLED'
        ELSE 'OPEN' END,
    payment_status = CASE WHEN received_vnd = 0 THEN 'UNPAID'
        WHEN received_vnd < amount_vnd THEN 'PARTIAL'
        WHEN received_vnd = amount_vnd THEN 'PAID' ELSE 'OVERPAID' END,
    fulfillment_status = CASE
        WHEN EXISTS (SELECT 1 FROM subscription_terms t WHERE t.order_id = o.id) THEN 'APPLIED'
        WHEN state = 'REQUIRES_REVIEW' THEN 'REVIEW'
        ELSE 'PENDING' END;
ALTER TABLE subscription_orders
    DROP COLUMN state,
    ALTER COLUMN order_status SET NOT NULL,
    ALTER COLUMN payment_status SET NOT NULL,
    ALTER COLUMN fulfillment_status SET NOT NULL,
    ADD CONSTRAINT ck_subscription_order_status CHECK (order_status IN ('OPEN', 'COMPLETED', 'EXPIRED', 'CANCELLED')),
    ADD CONSTRAINT ck_subscription_payment_status CHECK (payment_status IN ('UNPAID', 'PARTIAL', 'PAID', 'OVERPAID')),
    ADD CONSTRAINT ck_subscription_fulfillment_status CHECK (fulfillment_status IN ('PENDING', 'APPLIED', 'REVIEW')),
    ADD CONSTRAINT ck_subscription_payment_total CHECK (
        (payment_status = 'UNPAID' AND received_vnd = 0)
        OR (payment_status = 'PARTIAL' AND received_vnd > 0 AND received_vnd < amount_vnd)
        OR (payment_status = 'PAID' AND received_vnd = amount_vnd)
        OR (payment_status = 'OVERPAID' AND received_vnd > amount_vnd)
    ),
    ADD CONSTRAINT ck_subscription_order_dates CHECK (expires_at > created_at),
    ADD CONSTRAINT ck_subscription_order_revision CHECK (expected_revision >= 0),
    ADD CONSTRAINT ck_subscription_order_limits CHECK (shop_limit > 0 AND monthly_orders > 0 AND additional_members >= 0),
    ADD CONSTRAINT ck_subscription_order_name CHECK (btrim(plan_name) <> ''),
    ADD CONSTRAINT ck_subscription_order_cycle CHECK (cycle IN ('MONTHLY', 'YEARLY')),
    ADD CONSTRAINT ck_subscription_order_fulfilled CHECK (
        (order_status = 'COMPLETED') = (fulfillment_status = 'APPLIED')
        AND ((fulfillment_status = 'APPLIED' AND term_starts_at IS NOT NULL AND term_ends_at IS NOT NULL AND term_ends_at > term_starts_at)
            OR (fulfillment_status <> 'APPLIED' AND term_starts_at IS NULL AND term_ends_at IS NULL))
    ),
    ADD CONSTRAINT uq_subscription_order_tenant UNIQUE (tenant_id, id),
    ADD CONSTRAINT fk_subscription_order_account FOREIGN KEY (tenant_id) REFERENCES subscriptions(tenant_id),
    ADD CONSTRAINT fk_subscription_order_price_plan FOREIGN KEY (price_id, plan_code) REFERENCES subscription_prices(id, plan_code);
CREATE UNIQUE INDEX uq_subscription_open_order ON subscription_orders(tenant_id)
    WHERE order_status = 'OPEN' AND fulfillment_status = 'PENDING';
ALTER INDEX idx_billing_tenant_orders RENAME TO idx_subscription_orders_tenant_created;
CREATE INDEX idx_subscription_orders_expiry ON subscription_orders(expires_at, id)
    WHERE order_status = 'OPEN' AND fulfillment_status = 'PENDING';

ALTER TABLE subscription_terms
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT fk_subscription_term_account FOREIGN KEY (tenant_id) REFERENCES subscriptions(tenant_id),
    ADD CONSTRAINT fk_subscription_term_tenant_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES subscription_orders(tenant_id, id);
CREATE INDEX idx_subscription_terms_tenant_period ON subscription_terms(tenant_id, starts_at DESC, ends_at);

-- A canonical bank transaction has its own UUID; receiving a webhook is a separate concern.
ALTER TABLE payment_transactions DROP CONSTRAINT bank_payments_id_fkey;
ALTER TABLE payment_transactions DROP CONSTRAINT bank_payments_pkey;
ALTER TABLE payment_transactions RENAME COLUMN id TO provider_transaction_id;
ALTER TABLE payment_transactions RENAME COLUMN order_id TO matched_order_id;
ALTER TABLE payment_transactions
    ADD COLUMN id UUID,
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'SEPAY' CHECK (provider = 'SEPAY'),
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    ADD COLUMN allocated_vnd BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN sub_account VARCHAR(100),
    ADD COLUMN payment_code VARCHAR(100),
    ADD COLUMN matched_tenant_id UUID;
UPDATE payment_transactions SET id = md5('SEPAY:' || provider_transaction_id::text)::uuid;
UPDATE payment_transactions p SET sub_account = i.raw_payload::jsonb ->> 'subAccount',
    payment_code = i.raw_payload::jsonb ->> 'code'
FROM sepay_webhook_inbox i WHERE i.id = p.provider_transaction_id;
UPDATE payment_transactions p SET matched_tenant_id = o.tenant_id
FROM subscription_orders o WHERE o.id = p.matched_order_id;
ALTER TABLE payment_transactions
    ALTER COLUMN id SET NOT NULL,
    ADD CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    ADD CONSTRAINT uq_payment_provider_transaction UNIQUE (provider, provider_transaction_id),
    ADD CONSTRAINT ck_payment_provider_id CHECK (provider_transaction_id > 0),
    ADD CONSTRAINT ck_payment_direction CHECK (transfer_type IN ('in', 'out')),
    ADD CONSTRAINT ck_payment_status CHECK (status IN ('ALLOCATED', 'REVIEW')),
    ADD CONSTRAINT ck_payment_allocation_balance CHECK (allocated_vnd >= 0 AND allocated_vnd <= amount_vnd),
    ADD CONSTRAINT ck_payment_incoming_allocation CHECK (allocated_vnd = 0 OR transfer_type = 'in'),
    ADD CONSTRAINT ck_payment_matched_order CHECK (status <> 'ALLOCATED' OR matched_order_id IS NOT NULL),
    ADD CONSTRAINT fk_payment_matched_tenant_order FOREIGN KEY (matched_tenant_id, matched_order_id)
        REFERENCES subscription_orders(tenant_id, id) MATCH FULL;
ALTER INDEX uq_bank_reference RENAME TO uq_payment_bank_reference;
ALTER INDEX idx_bank_payments_review RENAME TO idx_payment_transactions_review;
CREATE INDEX idx_payment_matched_order ON payment_transactions(matched_tenant_id, matched_order_id, occurred_at);

ALTER TABLE sepay_webhook_inbox ADD COLUMN payment_transaction_id UUID REFERENCES payment_transactions(id);
UPDATE sepay_webhook_inbox i SET payment_transaction_id = p.id
FROM payment_transactions p WHERE p.provider_transaction_id = i.id;
ALTER INDEX idx_sepay_inbox_pending RENAME TO idx_sepay_webhook_pending;
CREATE INDEX idx_sepay_webhook_review ON sepay_webhook_inbox(received_at, id) WHERE status = 'REVIEW';
CREATE INDEX idx_sepay_webhook_transaction ON sepay_webhook_inbox(payment_transaction_id);

CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment_transactions(id),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_allocation_tenant_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES subscription_orders(tenant_id, id),
    CONSTRAINT uq_payment_order_allocation UNIQUE (payment_id, order_id)
);
CREATE INDEX idx_payment_allocations_order ON payment_allocations(tenant_id, order_id, created_at);

-- The database maintains allocation totals atomically on the payment row. Concurrent allocations
-- cannot exceed the actual amount received; the CHECK aborts the entire transaction on overflow.
CREATE FUNCTION maintain_payment_allocation_total() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (NEW.id <> OLD.id OR NEW.payment_id <> OLD.payment_id OR NEW.tenant_id <> OLD.tenant_id OR NEW.order_id <> OLD.order_id) THEN
        RAISE EXCEPTION 'Allocation identity is immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        UPDATE payment_transactions SET allocated_vnd = allocated_vnd - OLD.amount_vnd WHERE id = OLD.payment_id;
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        UPDATE payment_transactions SET allocated_vnd = allocated_vnd + NEW.amount_vnd WHERE id = NEW.payment_id;
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER trg_payment_allocation_total AFTER INSERT OR UPDATE OR DELETE ON payment_allocations
    FOR EACH ROW EXECUTE FUNCTION maintain_payment_allocation_total();
INSERT INTO payment_allocations(id, payment_id, tenant_id, order_id, amount_vnd, created_at)
SELECT md5('allocation:' || p.id::text)::uuid, p.id, p.matched_tenant_id, p.matched_order_id, p.amount_vnd, p.created_at
FROM payment_transactions p WHERE p.status = 'ALLOCATED';

CREATE FUNCTION verify_payment_allocation_total() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE current_amount BIGINT;
BEGIN
    SELECT allocated_vnd INTO current_amount FROM payment_transactions WHERE id = NEW.id;
    IF FOUND AND current_amount <> (SELECT coalesce(sum(amount_vnd), 0) FROM payment_allocations WHERE payment_id = NEW.id) THEN
        RAISE EXCEPTION 'Payment allocation total differs from its ledger' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_payment_allocation_balance AFTER INSERT OR UPDATE ON payment_transactions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_payment_allocation_total();

CREATE FUNCTION protect_payment_facts() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.provider, NEW.provider_transaction_id, NEW.bank, NEW.account_number,
        NEW.reference_code, NEW.sub_account, NEW.payment_code, NEW.amount_vnd, NEW.currency, NEW.transfer_type, NEW.occurred_at)
        IS DISTINCT FROM ROW(OLD.id, OLD.provider, OLD.provider_transaction_id, OLD.bank, OLD.account_number,
        OLD.reference_code, OLD.sub_account, OLD.payment_code, OLD.amount_vnd, OLD.currency, OLD.transfer_type, OLD.occurred_at) THEN
        RAISE EXCEPTION 'Bank transaction facts are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payment_facts_immutable BEFORE UPDATE ON payment_transactions
    FOR EACH ROW EXECUTE FUNCTION protect_payment_facts();

CREATE FUNCTION protect_subscription_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.plan_code, NEW.name, NEW.cycle, NEW.amount_vnd, NEW.currency,
        NEW.shop_limit, NEW.monthly_orders, NEW.additional_members)
        IS DISTINCT FROM ROW(OLD.id, OLD.plan_code, OLD.name, OLD.cycle, OLD.amount_vnd, OLD.currency,
        OLD.shop_limit, OLD.monthly_orders, OLD.additional_members) THEN
        RAISE EXCEPTION 'Publish a new price instead of rewriting an existing price' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_subscription_price_immutable BEFORE UPDATE ON subscription_prices
    FOR EACH ROW EXECUTE FUNCTION protect_subscription_price();

CREATE FUNCTION protect_subscription_order_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.tenant_id, NEW.actor_id, NEW.price_id, NEW.plan_code, NEW.plan_name,
        NEW.cycle, NEW.amount_vnd, NEW.currency, NEW.shop_limit, NEW.monthly_orders,
        NEW.additional_members, NEW.payment_code, NEW.idempotency_key, NEW.expected_revision)
        IS DISTINCT FROM ROW(OLD.id, OLD.tenant_id, OLD.actor_id, OLD.price_id, OLD.plan_code, OLD.plan_name,
        OLD.cycle, OLD.amount_vnd, OLD.currency, OLD.shop_limit, OLD.monthly_orders,
        OLD.additional_members, OLD.payment_code, OLD.idempotency_key, OLD.expected_revision) THEN
        RAISE EXCEPTION 'Subscription order purchase snapshot is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_subscription_order_snapshot BEFORE UPDATE ON subscription_orders
    FOR EACH ROW EXECUTE FUNCTION protect_subscription_order_snapshot();

CREATE FUNCTION touch_subscription_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = clock_timestamp(); RETURN NEW; END;
$$;
CREATE TRIGGER trg_subscription_updated_at BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION touch_subscription_updated_at();
CREATE TRIGGER trg_subscription_order_updated_at BEFORE UPDATE ON subscription_orders
    FOR EACH ROW EXECUTE FUNCTION touch_subscription_updated_at();

-- Period columns on the order are a read snapshot. Validate against the authoritative term at
-- commit, after the application has written both rows in the same transaction.
CREATE FUNCTION verify_subscription_fulfillment() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE order_key UUID; current_order subscription_orders%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME = 'subscription_orders' THEN
        order_key = CASE WHEN TG_OP = 'DELETE' THEN OLD.id ELSE NEW.id END;
    ELSE
        order_key = CASE WHEN TG_OP = 'DELETE' THEN OLD.order_id ELSE NEW.order_id END;
    END IF;
    SELECT * INTO current_order FROM subscription_orders WHERE id = order_key;
    IF NOT FOUND THEN RETURN NULL; END IF;
    IF current_order.fulfillment_status = 'APPLIED' THEN
        IF NOT EXISTS (SELECT 1 FROM subscription_terms t WHERE t.order_id = order_key
            AND t.tenant_id = current_order.tenant_id AND t.starts_at = current_order.term_starts_at
            AND t.ends_at = current_order.term_ends_at) THEN
            RAISE EXCEPTION 'Applied subscription order must have the matching term' USING ERRCODE = '23514';
        END IF;
    ELSIF EXISTS (SELECT 1 FROM subscription_terms WHERE order_id = order_key) THEN
        RAISE EXCEPTION 'Subscription term requires applied fulfillment' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_subscription_order_term AFTER INSERT OR UPDATE ON subscription_orders
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_subscription_fulfillment();
CREATE CONSTRAINT TRIGGER trg_subscription_term_order AFTER INSERT OR UPDATE OR DELETE ON subscription_terms
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_subscription_fulfillment();

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM subscription_orders o WHERE o.fulfillment_status = 'APPLIED'
        AND NOT EXISTS (SELECT 1 FROM subscription_terms t WHERE t.order_id = o.id
            AND t.tenant_id = o.tenant_id AND t.starts_at = o.term_starts_at AND t.ends_at = o.term_ends_at)) THEN
        RAISE EXCEPTION 'Repair inconsistent legacy subscription terms before migrating';
    END IF;
END $$;
