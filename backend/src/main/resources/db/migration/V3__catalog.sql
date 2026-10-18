-- V3: catalog. category -> product -> variant -> images.
-- New category types (e.g. antiques) only need attribute_defs on the category and attributes on variants.

CREATE TABLE categories (
    id             BIGSERIAL PRIMARY KEY,
    parent_id      BIGINT REFERENCES categories (id),
    name           VARCHAR(120) NOT NULL,
    slug           VARCHAR(140) NOT NULL UNIQUE,
    description    TEXT,
    image_url      TEXT,
    -- e.g. [{"key":"size","label":"Size","type":"enum","options":["Single","Double"],"filterable":true}]
    attribute_defs JSONB        NOT NULL DEFAULT '[]'::jsonb,
    sort_order     INT          NOT NULL DEFAULT 0,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    category_id   BIGINT       NOT NULL REFERENCES categories (id),
    title         VARCHAR(200) NOT NULL,
    slug          VARCHAR(220) NOT NULL UNIQUE,
    description   TEXT         NOT NULL DEFAULT '',
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    hsn_code      VARCHAR(8)   NOT NULL,
    gst_rate_bp   INT          NOT NULL CHECK (gst_rate_bp BETWEEN 0 AND 2800), -- basis points: 500 = 5%
    is_returnable BOOLEAN      NOT NULL DEFAULT TRUE,
    attributes    JSONB        NOT NULL DEFAULT '{}'::jsonb, -- product-level facts (fabric, care)
    seo_title     VARCHAR(200),
    seo_description VARCHAR(400),
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(description, '')), 'B')
    ) STORED,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_products_search ON products USING GIN (search_vector);
CREATE INDEX ix_products_category_status ON products (category_id, status);
CREATE INDEX ix_products_status_created ON products (status, created_at DESC);

CREATE TABLE product_variants (
    id           BIGSERIAL PRIMARY KEY,
    product_id   BIGINT       NOT NULL REFERENCES products (id),
    sku          VARCHAR(64)  NOT NULL UNIQUE,
    title        VARCHAR(120) NOT NULL, -- e.g. "Double / Indigo"
    attributes   JSONB        NOT NULL DEFAULT '{}'::jsonb, -- {"size":"Double","colour":"Indigo","fabric":"Cotton"}
    mrp_paise    BIGINT       NOT NULL CHECK (mrp_paise > 0),
    price_paise  BIGINT       NOT NULL CHECK (price_paise > 0),
    weight_grams INT          NOT NULL CHECK (weight_grams > 0),
    length_cm    NUMERIC(6, 1) NOT NULL CHECK (length_cm > 0),
    breadth_cm   NUMERIC(6, 1) NOT NULL CHECK (breadth_cm > 0),
    height_cm    NUMERIC(6, 1) NOT NULL CHECK (height_cm > 0),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order   INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_variant_price_le_mrp CHECK (price_paise <= mrp_paise)
);
CREATE INDEX ix_variants_product ON product_variants (product_id);
CREATE INDEX ix_variants_attributes ON product_variants USING GIN (attributes);
CREATE INDEX ix_variants_price ON product_variants (price_paise);

CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT      NOT NULL REFERENCES products (id),
    variant_id  BIGINT REFERENCES product_variants (id),
    storage_key TEXT        NOT NULL,
    url         TEXT        NOT NULL,
    alt         VARCHAR(200),
    width       INT,
    height      INT,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_product_images_product ON product_images (product_id, sort_order);

INSERT INTO categories (name, slug, description, attribute_defs, sort_order) VALUES
 ('Jaipuri Razai', 'jaipuri-razai', 'Hand-block printed cotton quilts from Jaipur',
  '[{"key":"size","label":"Size","type":"enum","options":["Single","Double"],"filterable":true},
    {"key":"colour","label":"Colour","type":"text","filterable":true},
    {"key":"fabric","label":"Fabric","type":"text","filterable":true}]', 1),
 ('Bed Sheets', 'bed-sheets', 'Printed cotton bed sheets',
  '[{"key":"size","label":"Size","type":"enum","options":["Single","Double","King"],"filterable":true},
    {"key":"colour","label":"Colour","type":"text","filterable":true},
    {"key":"fabric","label":"Fabric","type":"text","filterable":true}]', 2);
