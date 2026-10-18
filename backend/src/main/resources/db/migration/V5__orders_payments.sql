-- V5: orders, payments, refunds, reservations, coupon redemptions.

CREATE TABLE order_number_counters (
    day      DATE PRIMARY KEY,
    last_seq INT  NOT NULL
);

CREATE TABLE orders (
    id                 BIGSERIAL PRIMARY KEY,
    order_number       VARCHAR(20)  NOT NULL UNIQUE,  -- ORD-YYMMDD-XXXX
    user_id            BIGINT       NOT NULL REFERENCES users (id),
    status             VARCHAR(30)  NOT NULL CHECK (status IN
        ('PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID', 'PACKED', 'SHIPPED', 'DELIVERED',
         'RETURN_IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'RTO')),
    idempotency_key    VARCHAR(100) NOT NULL UNIQUE,
    warehouse_id       BIGINT       NOT NULL REFERENCES warehouses (id),
    email              VARCHAR(320) NOT NULL,
    phone              VARCHAR(10)  NOT NULL,
    shipping_address   JSONB        NOT NULL,  -- snapshot at purchase time
    subtotal_paise     BIGINT       NOT NULL,  -- sum(unit price x qty), GST-inclusive
    discount_paise     BIGINT       NOT NULL DEFAULT 0,
    shipping_paise     BIGINT       NOT NULL DEFAULT 0,
    total_paise        BIGINT       NOT NULL CHECK (total_paise > 0),
    taxable_paise      BIGINT       NOT NULL,
    cgst_paise         BIGINT       NOT NULL DEFAULT 0,
    sgst_paise         BIGINT       NOT NULL DEFAULT 0,
    igst_paise         BIGINT       NOT NULL DEFAULT 0,
    is_intra_state     BOOLEAN      NOT NULL,
    coupon_code        VARCHAR(40),
    payment_expires_at TIMESTAMPTZ,
    paid_at            TIMESTAMPTZ,
    packed_at          TIMESTAMPTZ,
    shipped_at         TIMESTAMPTZ,
    delivered_at       TIMESTAMPTZ,
    cancelled_at       TIMESTAMPTZ,
    cancel_reason      TEXT,
    cancel_code        VARCHAR(30), -- USER, ADMIN, PAYMENT_TIMEOUT
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_orders_total CHECK (total_paise = subtotal_paise - discount_paise + shipping_paise)
);
CREATE INDEX ix_orders_user ON orders (user_id, created_at DESC);
CREATE INDEX ix_orders_status ON orders (status, created_at DESC);
CREATE INDEX ix_orders_pending_expiry ON orders (payment_expires_at) WHERE status = 'PENDING_PAYMENT';

CREATE TABLE order_items (
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT       NOT NULL REFERENCES orders (id),
    product_id        BIGINT       NOT NULL REFERENCES products (id),
    variant_id        BIGINT       NOT NULL REFERENCES product_variants (id),
    -- snapshots: later catalog edits never change past orders
    sku               VARCHAR(64)  NOT NULL,
    product_title     VARCHAR(200) NOT NULL,
    variant_title     VARCHAR(120) NOT NULL,
    attributes        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    image_url         TEXT,
    hsn_code          VARCHAR(8)   NOT NULL,
    gst_rate_bp       INT          NOT NULL,
    is_returnable     BOOLEAN      NOT NULL,
    weight_grams      INT          NOT NULL,
    mrp_paise         BIGINT       NOT NULL,
    unit_price_paise  BIGINT       NOT NULL,
    qty               INT          NOT NULL CHECK (qty > 0),
    line_total_paise  BIGINT       NOT NULL, -- unit x qty
    discount_paise    BIGINT       NOT NULL DEFAULT 0, -- allocated share of coupon
    taxable_paise     BIGINT       NOT NULL,
    cgst_paise        BIGINT       NOT NULL DEFAULT 0,
    sgst_paise        BIGINT       NOT NULL DEFAULT 0,
    igst_paise        BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT      NOT NULL REFERENCES orders (id),
    from_status   VARCHAR(30),
    to_status     VARCHAR(30) NOT NULL,
    reason        TEXT,
    actor_type    VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'ADMIN', 'SYSTEM', 'WEBHOOK')),
    actor_user_id BIGINT REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_status_history_order ON order_status_history (order_id, created_at);

CREATE TABLE inventory_reservations (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT      NOT NULL REFERENCES orders (id),
    variant_id   BIGINT      NOT NULL REFERENCES product_variants (id),
    warehouse_id BIGINT      NOT NULL REFERENCES warehouses (id),
    qty          INT         NOT NULL CHECK (qty > 0),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reservations_order ON inventory_reservations (order_id);
CREATE INDEX ix_reservations_active_expiry ON inventory_reservations (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE coupon_redemptions (
    id             BIGSERIAL PRIMARY KEY,
    coupon_id      BIGINT      NOT NULL REFERENCES coupons (id),
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    order_id       BIGINT      NOT NULL UNIQUE REFERENCES orders (id),
    discount_paise BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVERSED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reversed_at    TIMESTAMPTZ
);
CREATE INDEX ix_coupon_redemptions_coupon_user ON coupon_redemptions (coupon_id, user_id) WHERE status = 'ACTIVE';

CREATE TABLE payments (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT       NOT NULL REFERENCES orders (id),
    gateway            VARCHAR(20)  NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id   VARCHAR(64)  NOT NULL UNIQUE,
    gateway_payment_id VARCHAR(64) UNIQUE,
    amount_paise       BIGINT       NOT NULL CHECK (amount_paise > 0),
    currency           VARCHAR(3)   NOT NULL DEFAULT 'INR',
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('CREATED', 'AUTHORIZED', 'CAPTURED', 'FAILED')),
    method             VARCHAR(30),
    error_code         VARCHAR(80),
    error_description  TEXT,
    client_verified_at TIMESTAMPTZ,
    captured_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_payments_order ON payments (order_id);
CREATE INDEX ix_payments_created ON payments (created_at);

CREATE TABLE refunds (
    id                BIGSERIAL PRIMARY KEY,
    payment_id        BIGINT       NOT NULL REFERENCES payments (id),
    order_id          BIGINT       NOT NULL REFERENCES orders (id),
    amount_paise      BIGINT       NOT NULL CHECK (amount_paise > 0),
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    gateway_refund_id VARCHAR(64) UNIQUE,
    receipt           VARCHAR(60)  NOT NULL UNIQUE,
    reason            TEXT         NOT NULL,
    source            VARCHAR(30)  NOT NULL CHECK (source IN ('ORDER_CANCEL', 'RETURN', 'LATE_CAPTURE', 'MANUAL')),
    source_ref_id     BIGINT,
    initiated_by      BIGINT REFERENCES users (id),
    error             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at      TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_refunds_payment ON refunds (payment_id);
CREATE INDEX ix_refunds_order ON refunds (order_id);

-- Daily reconciliation output, shown on the admin dashboard.
CREATE TABLE payment_mismatches (
    id                 BIGSERIAL PRIMARY KEY,
    run_date           DATE        NOT NULL,
    gateway_payment_id VARCHAR(64),
    gateway_order_id   VARCHAR(64),
    order_id           BIGINT REFERENCES orders (id),
    kind               VARCHAR(40) NOT NULL,
    detail             TEXT,
    resolved_at        TIMESTAMPTZ,
    resolved_by        BIGINT REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_payment_mismatch UNIQUE (run_date, gateway_payment_id, kind)
);
