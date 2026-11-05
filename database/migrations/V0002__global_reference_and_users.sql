CREATE FUNCTION touch_updated_at() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    NEW.version := OLD.version + 1;
    RETURN NEW;
END $$;

CREATE TABLE industry_types (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code       text NOT NULL,
    name       text NOT NULL,
    is_active  boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version    bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_industry_types_code UNIQUE (code),
    CONSTRAINT ck_industry_types_code CHECK (code ~ '^[a-z_]+$')
);
CREATE TRIGGER trg_industry_types_touch BEFORE UPDATE ON industry_types
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

INSERT INTO industry_types (code, name) VALUES
    ('restaurant', 'Restaurant'),
    ('hotel', 'Hotel / Resort'),
    ('salon', 'Salon / Spa'),
    ('retail', 'Retail'),
    ('cafe', 'Café / Bakery'),
    ('gym', 'Gym / Fitness'),
    ('clinic', 'Clinic');

CREATE TABLE permissions (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code          text NOT NULL,
    module        text NOT NULL,
    description   text NOT NULL,
    is_restricted boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid,
    version       bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_permissions_code UNIQUE (code),
    CONSTRAINT ck_permissions_code CHECK (code ~ '^[a-z]+(\.[a-z_]+){2}$')
);
CREATE TRIGGER trg_permissions_touch BEFORE UPDATE ON permissions
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE plans (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code             text NOT NULL,
    name             text NOT NULL,
    billing_interval text NOT NULL,
    is_active        boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    updated_by       uuid,
    version          bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_plans_code UNIQUE (code),
    CONSTRAINT ck_plans_interval CHECK (billing_interval IN ('monthly', 'yearly'))
);
CREATE TRIGGER trg_plans_touch BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE plan_entitlements (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id     uuid NOT NULL REFERENCES plans (id),
    feature_key text NOT NULL,
    limit_value integer,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_plan_entitlements UNIQUE (plan_id, feature_key),
    CONSTRAINT ck_plan_entitlements_limit CHECK (limit_value IS NULL OR limit_value >= 0)
);
CREATE TRIGGER trg_plan_entitlements_touch BEFORE UPDATE ON plan_entitlements
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE users (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email              citext NOT NULL,
    password_hash      text NOT NULL,
    full_name          text NOT NULL,
    status             text NOT NULL DEFAULT 'pending',
    email_verified_at  timestamptz,
    failed_login_count integer NOT NULL DEFAULT 0,
    locked_until       timestamptz,
    last_login_at      timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    updated_by         uuid,
    version            bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('pending', 'active', 'locked', 'disabled')),
    CONSTRAINT ck_users_failed_logins CHECK (failed_login_count >= 0)
);
CREATE TRIGGER trg_users_touch BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
