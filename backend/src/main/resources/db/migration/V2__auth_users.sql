-- V2: auth and user profile.

CREATE TABLE auth_identities (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    provider         VARCHAR(20)  NOT NULL CHECK (provider IN ('GOOGLE', 'EMAIL_OTP')),
    provider_user_id VARCHAR(320) NOT NULL,
    email            VARCHAR(320) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_auth_identities_provider UNIQUE (provider, provider_user_id)
);
CREATE INDEX ix_auth_identities_user ON auth_identities (user_id);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by BIGINT REFERENCES refresh_tokens (id),
    user_agent  VARCHAR(300),
    ip          VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE otp_requests (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(320) NOT NULL,
    code_hash   VARCHAR(64)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    ip          VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp_requests_email ON otp_requests (lower(email), created_at DESC);
CREATE INDEX ix_otp_requests_ip ON otp_requests (ip, created_at DESC);

-- Signup tokens are single use: we record each jti once it is consumed.
CREATE TABLE used_signup_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    used_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE user_addresses (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    name       VARCHAR(80)  NOT NULL,
    phone      VARCHAR(10)  NOT NULL,
    line1      VARCHAR(200) NOT NULL,
    line2      VARCHAR(200),
    landmark   VARCHAR(120),
    city       VARCHAR(80)  NOT NULL,
    state      VARCHAR(80)  NOT NULL,
    pincode    VARCHAR(6)   NOT NULL CHECK (pincode ~ '^[1-9][0-9]{5}$'),
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_user_addresses_user ON user_addresses (user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_user_addresses_default ON user_addresses (user_id) WHERE is_default AND deleted_at IS NULL;
