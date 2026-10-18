-- V6: Gulmohar Fabrics launch catalog and store rules (from the homepage design).
-- Prices, HSN codes and GST rates are placeholders: confirm GST/HSN with your CA, then edit in Admin.
-- Starting stock of 10 per variant is for testing; correct it in Admin -> Inventory before launch.

-- Categories: rename the V3 seed to the "Rajai" spelling used on the site, add dohars and cushions.
UPDATE categories SET name = 'Jaipuri Rajai', slug = 'jaipuri-rajai',
       description = 'Hand block printed cotton rajai from Sanganer and Bagru'
WHERE slug = 'jaipuri-razai';
UPDATE categories SET description = 'Hand block printed cotton bed sheet sets', sort_order = 3 WHERE slug = 'bed-sheets';

INSERT INTO categories (name, slug, description, attribute_defs, sort_order) VALUES
 ('Mulmul Dohars', 'mulmul-dohar', 'Light mulmul cotton dohars for every season',
  '[{"key":"size","label":"Size","type":"enum","options":["Single","Double"],"filterable":true},
    {"key":"colour","label":"Colour","type":"text","filterable":true}]', 2),
 ('Cushions & More', 'cushions-and-more', 'Cushion covers, pillow covers and gift sets',
  '[{"key":"colour","label":"Colour","type":"text","filterable":true}]', 4)
ON CONFLICT (slug) DO NOTHING;

-- Products
INSERT INTO products (category_id, title, slug, description, status, hsn_code, gst_rate_bp, attributes) VALUES
 ((SELECT id FROM categories WHERE slug = 'jaipuri-rajai'), 'The Shahi Marusthal Baagh', 'shahi-marusthal-baagh',
  'Indigo and grey Mughal florals, hand block printed on soft white cotton with a striped border. Hand quilted, light and warm.',
  'ACTIVE', '9404', 500, '{"fabric":"Cotton","print":"Hand block print","care":"Gentle hand or machine wash cold, dry in shade","badge":"Bestseller"}'),
 ((SELECT id FROM categories WHERE slug = 'jaipuri-rajai'), 'The Raj-Gharana Phool-Patri', 'raj-gharana-phool-patri',
  'Hand block printed bouquets in sage and lavender with a fine blue pinstripe border, on breathable cotton.',
  'ACTIVE', '9404', 500, '{"fabric":"Cotton","print":"Hand block print","care":"Gentle hand or machine wash cold, dry in shade","badge":"New"}'),
 ((SELECT id FROM categories WHERE slug = 'jaipuri-rajai'), 'The Shahi Rang-Makaranda', 'shahi-rang-makaranda',
  'Red, blue and yellow bouquets with cypress leaves and a green jaal border, hand block printed in Jaipur.',
  'ACTIVE', '9404', 500, '{"fabric":"Cotton","print":"Hand block print","care":"Gentle hand or machine wash cold, dry in shade"}'),
 ((SELECT id FROM categories WHERE slug = 'mulmul-dohar'), 'Gulabi Buti Mulmul Dohar', 'gulabi-buti-mulmul-dohar',
  'A feather-light mulmul dohar with small pink butis, perfect for summer nights and air-conditioned rooms.',
  'ACTIVE', '6304', 500, '{"fabric":"Mulmul cotton","print":"Hand block print","badge":"Summer"}'),
 ((SELECT id FROM categories WHERE slug = 'mulmul-dohar'), 'Amer Chakra Dohar', 'amer-chakra-dohar',
  'Chakra motifs inspired by Amer Fort, block printed on soft layered mulmul.',
  'ACTIVE', '6304', 500, '{"fabric":"Mulmul cotton","print":"Hand block print"}'),
 ((SELECT id FROM categories WHERE slug = 'bed-sheets'), 'Rose Jaal Bed Sheet Set', 'rose-jaal-bed-sheet-set',
  'Queen size cotton bed sheet with two pillow covers, printed with a rose jaal pattern.',
  'ACTIVE', '6302', 500, '{"fabric":"Cotton","print":"Hand block print","includes":"1 bed sheet, 2 pillow covers","badge":"New"}')
ON CONFLICT (slug) DO NOTHING;

-- One variant each (sizes from the homepage). Price is GST-inclusive, in paise.
INSERT INTO product_variants (product_id, sku, title, attributes, mrp_paise, price_paise, weight_grams,
                              length_cm, breadth_cm, height_cm)
SELECT p.id, v.sku, v.title, v.attrs::jsonb, v.mrp, v.price, v.weight, v.l, v.b, v.h
FROM (VALUES
  ('shahi-marusthal-baagh',   'GF-RJ-MB-D',  'Double', '{"size":"Double","colour":"Indigo"}',   540000, 450000, 2600, 45, 35, 20),
  ('raj-gharana-phool-patri', 'GF-RJ-PP-D',  'Double', '{"size":"Double","colour":"Sage"}',     520000, 520000, 2600, 45, 35, 20),
  ('shahi-rang-makaranda',    'GF-RJ-RM-D',  'Double', '{"size":"Double","colour":"Multicolour"}', 420000, 420000, 2600, 45, 35, 20),
  ('gulabi-buti-mulmul-dohar','GF-DH-GB-S',  'Single', '{"size":"Single","colour":"Pink"}',     290000, 240000, 900, 35, 30, 8),
  ('amer-chakra-dohar',       'GF-DH-AC-D',  'Double', '{"size":"Double","colour":"Terracotta"}', 310000, 310000, 1400, 40, 30, 10),
  ('rose-jaal-bed-sheet-set', 'GF-BS-RJ-Q',  'Queen',  '{"size":"Queen","colour":"Rose"}',     360000, 360000, 1100, 35, 28, 6)
) AS v(slug, sku, title, attrs, mrp, price, weight, l, b, h)
JOIN products p ON p.slug = v.slug
ON CONFLICT (sku) DO NOTHING;

-- Photos (served by the website from /images). Rajai 1: 5 photos, Rajai 2: 6, Rajai 3: 5.
INSERT INTO product_images (product_id, storage_key, url, alt, width, height, sort_order)
SELECT p.id, 'static/' || i.file, '/images/' || i.file, i.alt, 1000, 1339, i.ord
FROM (VALUES
  ('shahi-marusthal-baagh', 'rajai-1-1.jpg', 'Indigo floral block print rajai on a bed', 0),
  ('shahi-marusthal-baagh', 'rajai-1-2.jpg', 'Rajai with matching quilted pillow covers', 1),
  ('shahi-marusthal-baagh', 'rajai-1-3.jpg', 'Rajai border detail in warm evening light', 2),
  ('shahi-marusthal-baagh', 'rajai-1-4.jpg', 'Rajai and pillows on a bed', 3),
  ('shahi-marusthal-baagh', 'rajai-1-5.jpg', 'Close-up of hand block printed floral motif and quilting', 4),
  ('raj-gharana-phool-patri', 'rajai-2-1.jpg', 'Bouquet block print rajai and pillow, seen from above', 0),
  ('raj-gharana-phool-patri', 'rajai-2-2.jpg', 'Rajai with blue striped border falling over the bed', 1),
  ('raj-gharana-phool-patri', 'rajai-2-3.jpg', 'Bouquet print rajai set on a double bed', 2),
  ('raj-gharana-phool-patri', 'rajai-2-4.jpg', 'Matching pillow cover and folded rajai', 3),
  ('raj-gharana-phool-patri', 'rajai-2-5.jpg', 'Close-up of the hand block printed bouquet and stripe border', 4),
  ('raj-gharana-phool-patri', 'rajai-2-6.jpg', 'Rajai set in a warm lamp-lit bedroom', 5),
  ('shahi-rang-makaranda', 'rajai-3-4.jpg', 'Full rajai set with red, blue and yellow bouquets', 0),
  ('shahi-rang-makaranda', 'rajai-3-1.jpg', 'Close-up of the colourful bouquet and green jaal border', 1),
  ('shahi-rang-makaranda', 'rajai-3-2.jpg', 'Floral rajai with matching pillow covers by a bedside lamp', 2),
  ('shahi-rang-makaranda', 'rajai-3-3.jpg', 'Floral rajai set on a double bed in a sunlit room', 3),
  ('shahi-rang-makaranda', 'rajai-3-5.jpg', 'Rajai corner with bouquet and leaf motifs', 4)
) AS i(slug, file, alt, ord)
JOIN products p ON p.slug = i.slug
WHERE NOT EXISTS (SELECT 1 FROM product_images x WHERE x.product_id = p.id AND x.url = '/images/' || i.file);

-- Test stock: 10 of each, recorded in the movement ledger like any purchase.
INSERT INTO inventory (variant_id, warehouse_id, on_hand, reserved)
SELECT v.id, w.id, 10, 0
FROM product_variants v CROSS JOIN warehouses w
WHERE w.is_default AND v.sku LIKE 'GF-%'
ON CONFLICT (variant_id, warehouse_id) DO NOTHING;

INSERT INTO inventory_movements (variant_id, warehouse_id, movement_type, delta_on_hand, delta_reserved,
                                 on_hand_after, reserved_after, ref_type, note)
SELECT i.variant_id, i.warehouse_id, 'CORRECTION', 10, 0, 10, 0, 'SEED', 'Opening test stock (V6 migration)'
FROM inventory i JOIN product_variants v ON v.id = i.variant_id
WHERE v.sku LIKE 'GF-%'
  AND NOT EXISTS (SELECT 1 FROM inventory_movements m WHERE m.variant_id = i.variant_id);

-- Store rules shown on the homepage: free shipping above ₹2,999, PINKCITY10 = 10% off the first order.
UPDATE app_settings SET value = '299900' WHERE key = 'shipping.free_threshold_paise';
UPDATE app_settings SET value = '"Gulmohar Fabrics"' WHERE key = 'seller.legal_name';

INSERT INTO coupons (code, description, discount_type, discount_value, min_order_paise, usage_limit_per_user, is_active)
VALUES ('PINKCITY10', '10% off your first order', 'PERCENT', 1000, 0, 1, TRUE)
ON CONFLICT DO NOTHING;

-- Newsletter sign-ups from the homepage form.
CREATE TABLE newsletter_subscribers (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(320) NOT NULL,
    source     VARCHAR(40)  NOT NULL DEFAULT 'homepage',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    unsubscribed_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_newsletter_email ON newsletter_subscribers (lower(email));

UPDATE cms_pages SET body_html =
 '<p>Gulmohar Fabrics brings hand block printed rajai, dohars and bed linen from the artisan families of Sanganer and Bagru to homes across India.</p><p>Every piece begins with a hand-carved teak block and natural dyes, and is washed and sun-dried under the Rajasthan sky.</p>'
WHERE slug = 'about' AND body_html LIKE '<p>Replace%';
