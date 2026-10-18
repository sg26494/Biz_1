-- V4: inventory, cart, coupons.

-- Single row at launch (pickup address + GSTIN). More locations later need no schema change.
CREATE TABLE warehouses (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(30)  NOT NULL UNIQUE,
    name                 VARCHAR(120) NOT NULL,
    pickup_location_name VARCHAR(120), -- Shiprocket pickup location nickname
    contact_name         VARCHAR(80),
    phone                VARCHAR(10),
    email                VARCHAR(320),
    line1                VARCHAR(200) NOT NULL,
    line2                VARCHAR(200),
    city                 VARCHAR(80)  NOT NULL,
    state                VARCHAR(80)  NOT NULL,
    pincode              VARCHAR(6)   NOT NULL,
    gstin                VARCHAR(15),
    is_default           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_warehouses_default ON warehouses (is_default) WHERE is_default;

INSERT INTO warehouses (code, name, pickup_location_name, line1, city, state, pincode, is_default)
VALUES ('MAIN', 'Main warehouse', 'Primary', 'Update address in admin', 'Jaipur', 'Rajasthan', '302001', TRUE);

CREATE TABLE inventory (
    variant_id          BIGINT      NOT NULL REFERENCES product_variants (id),
    warehouse_id        BIGINT      NOT NULL REFERENCES warehouses (id),
    on_hand             INT         NOT NULL DEFAULT 0,
    reserved            INT         NOT NULL DEFAULT 0,
    low_stock_threshold INT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (variant_id, warehouse_id),
    CONSTRAINT ck_inventory_non_negative CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved >= 0 AND reserved <= on_hand)
);

CREATE TABLE inventory_movements (
    id             BIGSERIAL PRIMARY KEY,
    variant_id     BIGINT      NOT NULL REFERENCES product_variants (id),
    warehouse_id   BIGINT      NOT NULL REFERENCES warehouses (id),
    movement_type  VARCHAR(20) NOT NULL CHECK (movement_type IN
        ('RESERVE', 'RELEASE', 'SALE', 'RETURN', 'CANCEL_RESTOCK', 'PURCHASE', 'DAMAGE', 'CORRECTION')),
    delta_on_hand  INT         NOT NULL DEFAULT 0,
    delta_reserved INT         NOT NULL DEFAULT 0,
    on_hand_after  INT         NOT NULL,
    reserved_after INT         NOT NULL,
    ref_type       VARCHAR(30),
    ref_id         VARCHAR(60),
    note           TEXT,
    actor_user_id  BIGINT REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_inventory_movements_variant ON inventory_movements (variant_id, created_at DESC);

-- Append-only: block updates and deletes on the ledger.
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_movements_append_only
    BEFORE UPDATE OR DELETE ON inventory_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE carts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT UNIQUE REFERENCES users (id),
    session_id  VARCHAR(64) UNIQUE,
    coupon_code VARCHAR(40),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_cart_owner CHECK (user_id IS NOT NULL OR session_id IS NOT NULL)
);
CREATE INDEX ix_carts_updated ON carts (updated_at);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT      NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    variant_id BIGINT      NOT NULL REFERENCES product_variants (id),
    qty        INT         NOT NULL CHECK (qty > 0),
    seen_price_paise BIGINT, -- price the shopper last saw; drives the "price changed" warning
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_cart_items_variant UNIQUE (cart_id, variant_id)
);

CREATE TABLE coupons (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(40)  NOT NULL,
    description          VARCHAR(300),
    discount_type        VARCHAR(10)  NOT NULL CHECK (discount_type IN ('PERCENT', 'FLAT')),
    discount_value       BIGINT       NOT NULL CHECK (discount_value > 0), -- PERCENT: basis points; FLAT: paise
    min_order_paise      BIGINT       NOT NULL DEFAULT 0,
    max_discount_paise   BIGINT,
    starts_at            TIMESTAMPTZ,
    ends_at              TIMESTAMPTZ,
    usage_limit_total    INT,
    usage_limit_per_user INT,
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_coupon_percent CHECK (discount_type <> 'PERCENT' OR discount_value <= 10000)
);
CREATE UNIQUE INDEX ux_coupons_code ON coupons (upper(code));
