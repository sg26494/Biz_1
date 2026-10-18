-- V1: foundation tables shared by every module.
-- Conventions: money in paise (BIGINT), timestamps in UTC (TIMESTAMPTZ), FKs everywhere.

CREATE TABLE app_settings (
    key         VARCHAR(100) PRIMARY KEY,
    value       JSONB        NOT NULL,
    description TEXT,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  BIGINT
);

CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(320) NOT NULL,
    name              VARCHAR(80)  NOT NULL,
    phone             VARCHAR(10),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED')),
    terms_accepted_at TIMESTAMPTZ,
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE UNIQUE INDEX ux_users_phone ON users (phone) WHERE phone IS NOT NULL;

CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users (id),
    action        VARCHAR(100) NOT NULL,
    entity_type   VARCHAR(60)  NOT NULL,
    entity_id     VARCHAR(60),
    before_state  JSONB,
    after_state   JSONB,
    reason        TEXT,
    ip            VARCHAR(64),
    request_id    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_logs_created ON audit_logs (created_at DESC);

CREATE TABLE idempotency_keys (
    id              BIGSERIAL PRIMARY KEY,
    scope           VARCHAR(60)  NOT NULL,
    user_id         BIGINT REFERENCES users (id),
    idem_key        VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status INT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_idempotency_scope_user_key ON idempotency_keys (scope, COALESCE(user_id, 0), idem_key);

CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(60)  NOT NULL,
    aggregate_id    VARCHAR(60)  NOT NULL,
    event_type      VARCHAR(80)  NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_events (next_attempt_at) WHERE status = 'PENDING';

CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT REFERENCES users (id),
    channel             VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    to_address          VARCHAR(320) NOT NULL,
    template            VARCHAR(80)  NOT NULL,
    subject             VARCHAR(300),
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('SENT', 'FAILED')),
    provider_message_id VARCHAR(200),
    error               TEXT,
    outbox_event_id     BIGINT REFERENCES outbox_events (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);

CREATE TABLE webhook_events (
    id           BIGSERIAL PRIMARY KEY,
    source       VARCHAR(30)  NOT NULL,
    event_id     VARCHAR(200) NOT NULL,
    event_type   VARCHAR(80)  NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')),
    attempts     INT          NOT NULL DEFAULT 0,
    error        TEXT,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT ux_webhook_events_source_event UNIQUE (source, event_id)
);
CREATE INDEX ix_webhook_events_unprocessed ON webhook_events (received_at) WHERE status IN ('RECEIVED', 'FAILED');

-- ShedLock: one instance runs each scheduled job.
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Content: banners and static pages (About, policies) — needed early for Razorpay KYC.
CREATE TABLE banners (
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    subtitle   VARCHAR(300),
    image_url  TEXT         NOT NULL,
    link_url   TEXT,
    placement  VARCHAR(40)  NOT NULL DEFAULT 'HOME_HERO',
    sort_order INT          NOT NULL DEFAULT 0,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at  TIMESTAMPTZ,
    ends_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cms_pages (
    id           BIGSERIAL PRIMARY KEY,
    slug         VARCHAR(100) NOT NULL UNIQUE,
    title        VARCHAR(200) NOT NULL,
    body_html    TEXT         NOT NULL DEFAULT '',
    is_published BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Business rules live here, editable from admin. Values are placeholders until the founders decide.
INSERT INTO app_settings (key, value, description) VALUES
 ('shipping.flat_fee_paise',            '9900',   'Flat shipping fee per order (paise), GST-inclusive'),
 ('shipping.free_threshold_paise',      '199900', 'Orders at or above this subtotal ship free (paise). null = never free'),
 ('shipping.default_eta_days',          '5',      'Shown when courier serviceability ETA is unavailable'),
 ('returns.window_days',                '7',      'Days after delivery a return can be requested'),
 ('returns.customer_pays_pickup',       'false',  'Whether reverse pickup cost is deducted from refund'),
 ('checkout.reservation_ttl_minutes',   '15',     'How long stock is held for an unpaid order'),
 ('cart.max_qty_per_item',              '10',     'Max quantity of one variant per cart/order'),
 ('inventory.low_stock_default',        '3',      'Default low-stock alert threshold per variant'),
 ('seller.legal_name',                  '"Your Company Pvt Ltd"', 'Shown on invoices'),
 ('seller.gstin',                       '""',     'Seller GSTIN, shown on invoices');

INSERT INTO cms_pages (slug, title, body_html, is_published) VALUES
 ('about',    'About us',         '<p>Replace with your story.</p>', TRUE),
 ('contact',  'Contact us',       '<p>Email / phone / address.</p>', TRUE),
 ('privacy',  'Privacy policy',   '<p>Draft pending.</p>', TRUE),
 ('terms',    'Terms of service', '<p>Draft pending.</p>', TRUE),
 ('shipping', 'Shipping policy',  '<p>Draft pending.</p>', TRUE),
 ('returns',  'Returns & refunds','<p>Draft pending.</p>', TRUE);
