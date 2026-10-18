# Online Store: Jaipuri razai and bed sheets

This repo is the build of the *Online Store: Launch Design Doc (HLD + LLD)*. It is a modular monolith: one Spring Boot API (Java 21), the same jar run as a background worker, a Next.js storefront and admin panel, and PostgreSQL.

```
backend/    Spring Boot API + worker (modules: auth, user, catalog, inventory, cart, promo, pricing,
            order, payment, shipping, notification, admin, content, jobs, common)
frontend/   Next.js 15 (App Router): storefront (SSR/ISR) + /admin
deploy/     Production docker compose, Caddyfile, deploy script
.github/    CI: build + tests, images to GHCR, optional SSH deploy to staging
```

## Build status against the design doc (section 11)

| Phase | Status |
|---|---|
| 1. Foundation: repo, Docker Compose, Flyway, base entities, error format, CI, staging deploy | Done (staging deploy is scripted; it needs a VM and secrets) |
| 2. Auth: Google login, email OTP, signup details page, tokens, admin role and seed | Done |
| 3. Catalog and admin products: CRUD, image upload, storefront listing and product pages | Done |
| 4. Inventory and cart: stock, reservations, cart, coupons, pricing/GST | Done |
| 5. Checkout and Razorpay: orders, payment, webhooks, reconciliation, confirmation emails | Done |
| 6. Order admin and Shiprocket: pack and ship, label, tracking webhooks, shipping failures | Next. `ShippingProvider` / `ShiprocketProvider` exist with serviceability only |
| 7. Returns and refunds: request flow, reverse pickup, QC, credit notes | Next. `RefundService` and `InventoryService.restockReturn` are ready |
| 8. Invoices, reports, polish: invoice PDFs, GST export, SEO, policy pages | Partly done: SEO (sitemap, JSON-LD, ISR) and CMS policy pages are in |
| 9. Hardening | Not started |

## Run it locally

You need Docker, Java 21 and Node 22.

```bash
cp .env.example .env                                  # set ADMIN_SEED_EMAILS to your email
docker compose up -d postgres mailpit minio minio-init

cd backend && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun          # API on :8080, runs Flyway
# second terminal, same jar as the worker (jobs, outbox, emails):
cd backend && SPRING_PROFILES_ACTIVE=local,worker ./gradlew bootRun

cd frontend && cp .env.example .env.local && npm install && npm run dev   # http://localhost:3000
```

- Emails (OTP codes, order confirmations) arrive in Mailpit at http://localhost:8025.
- Product images go to MinIO (console at http://localhost:9001, user `minio`, password `minio12345`).
- Log in with an email listed in `ADMIN_SEED_EMAILS`, then open `/admin`.
- Shiprocket is off locally (`SHIPROCKET_ENABLED=false`). A stub accepts every valid pincode.
- Razorpay: use **test mode** keys. To get webhooks on your machine, expose port 8080 (for example with `cloudflared tunnel` or `ngrok`). Then add `https://<tunnel>/api/v1/webhooks/razorpay` in the Razorpay dashboard with the events `payment.captured`, `payment.failed`, `order.paid`, `refund.processed` and `refund.failed`. Turn on **auto-capture** in Razorpay payment settings.

Alternatively, `docker compose up --build` runs everything in containers.

### Tests

```bash
cd backend && ./gradlew test      # unit + Testcontainers integration tests (Docker required)
cd frontend && npm run typecheck && npm run build
```

`CheckoutFlowIT` runs the full order path against a real Postgres: cart, checkout reservation, idempotent retry, client verify, duplicate webhooks, commit, user cancel with refund, overselling, and late capture after reservation expiry.

## How the important parts work

**Money.** Everything is stored in paise (`BIGINT`). Prices are GST-inclusive. `PricingCalculator` spreads the coupon discount across lines, so the shares add up exactly. It then works out each line's taxable value and tax backwards from the price. Same-state delivery splits the tax into CGST + SGST; otherwise it is IGST. Shipping is taxed at the highest item rate (composite supply). **Have your CA confirm this, and the HSN/GST rate on each product.**

**No overselling.** At checkout, a single conditional `UPDATE inventory SET reserved = reserved + :qty WHERE on_hand - reserved >= :qty` runs for each line, in variant-id order. If any line fails, the whole checkout rolls back. A `CHECK (reserved <= on_hand)` constraint backs this up. Every stock change appends a row to `inventory_movements`, and a trigger makes that table append-only.

**Payments.** `POST /checkout` (with an `Idempotency-Key`) creates the order in `PENDING_PAYMENT` and reserves stock in one transaction. It then creates the Razorpay order. `/payments/verify` only checks the signature and shows "processing". **Only the webhook marks an order PAID.** Webhooks are verified on the raw body and stored in `webhook_events`, which is unique on the event id. Each one is processed in its own transaction, and failures are retried by a job. Edge cases handled:
- A capture that lands after the 15-minute reservation expired re-takes the stock if it is still there; otherwise the payment is auto-refunded.
- A duplicate payment on an already-paid order is auto-refunded.
- If `payment.failed` is followed by a successful retry, the order is still marked PAID.

**Refunds.** These are saved as `PENDING` and executed by the worker. Before calling Razorpay, the worker looks for an existing refund carrying our receipt, so a crash can never refund twice. The total refunded can never exceed the captured amount. The `refund.processed` webhook finalises the refund.

**Outbox.** Emails, refund calls and storefront revalidation are written to `outbox_events` in the same transaction as the business change. The worker then runs them with exponential backoff.

**Order states.** All transitions are defined in `OrderStateMachine`, and every change writes to `order_status_history`.

**Auth.** Signup has two steps: verifying Google or an OTP never creates a user. Access JWTs last 15 minutes and are kept in memory. Refresh tokens are opaque, stored hashed, and rotated on every use. Reusing an old refresh token revokes all of that user's sessions.

**Admin.** Admins are users with `role = ADMIN`, set by the seed or by another admin. Every admin write goes to `audit_logs` with before/after values. Refunds and cancellations require a confirmation, a reason and an `Idempotency-Key`.

**Jobs** (worker only, ShedLock):

| Job | Schedule |
|---|---|
| Expire reservations | every minute |
| Outbox | every 3 s |
| Webhook retry | every minute |
| Payment reconciliation | daily 06:00 IST |
| Auto-complete delivered orders | daily |
| Cleanup | daily |
| Low-stock email | daily |

## Deploying

1. Create the managed Postgres (Mumbai) and one VM with Docker. Put `deploy/` and a filled `.env` on the VM in `~/store/deploy`, and set `SITE_DOMAIN` in that `.env`.
2. In GitHub, set these repository variables: `SITE_URL`, `SITE_NAME`, `GOOGLE_CLIENT_ID` and `DEPLOY_STAGING=true`. Set these secrets: `STAGING_HOST`, `STAGING_USER` and `STAGING_SSH_KEY`.
3. Every push to `main` builds, runs the tests, pushes the images to GHCR and deploys. Flyway migrates when the API starts.
4. To roll back, run `IMAGE_TAG=<previous sha> GH_OWNER=<owner> ./deploy.sh`. Keep migrations backward compatible.

## Decisions still needed from the founders

These live in **Admin → Settings**. The current values are placeholders:

- `returns.window_days`: 7
- `returns.customer_pays_pickup`: false
- `shipping.flat_fee_paise`: ₹99
- `shipping.free_threshold_paise`: ₹1,999
- `cart.max_qty_per_item`: 10
- The GST rate and HSN code on each product (confirm with your CA)
- Seller GSTIN and pickup address (Admin → Settings → Pickup address)
- Policy page text (Admin → Content). Razorpay KYC needs these pages live.
