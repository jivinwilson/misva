CREATE TABLE businesses (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             text NOT NULL,
    legal_name       text NOT NULL,
    display_name     text NOT NULL,
    industry_type_id uuid NOT NULL REFERENCES industry_types (id),
    country_code     char(2) NOT NULL,
    default_currency char(3) NOT NULL,
    timezone         text NOT NULL,
    status           text NOT NULL DEFAULT 'pending',
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    updated_by       uuid,
    version          bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_businesses_slug UNIQUE (slug),
    CONSTRAINT ck_businesses_slug CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    CONSTRAINT ck_businesses_status CHECK (status IN ('pending', 'active', 'suspended', 'closed'))
);
CREATE INDEX ix_businesses_industry ON businesses (industry_type_id);
CREATE TRIGGER trg_businesses_touch BEFORE UPDATE ON businesses
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE FUNCTION forbid_industry_change() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.industry_type_id <> OLD.industry_type_id THEN
        RAISE EXCEPTION 'The industry of a business cannot be changed' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_businesses_industry_immutable BEFORE UPDATE ON businesses
    FOR EACH ROW EXECUTE FUNCTION forbid_industry_change();

CREATE TABLE branches (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id    uuid NOT NULL REFERENCES businesses (id),
    code           text NOT NULL,
    name           text NOT NULL,
    address_line1  text,
    address_line2  text,
    city           text,
    region         text,
    postal_code    text,
    country_code   char(2) NOT NULL,
    timezone       text NOT NULL,
    status         text NOT NULL DEFAULT 'active',
    is_head_office boolean NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    updated_by     uuid,
    version        bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_branches_business_code UNIQUE (business_id, code),
    CONSTRAINT uq_branches_business_id UNIQUE (business_id, id),
    CONSTRAINT ck_branches_status CHECK (status IN ('active', 'inactive', 'closed'))
);
CREATE UNIQUE INDEX uq_branches_one_head_office ON branches (business_id) WHERE is_head_office;
CREATE TRIGGER trg_branches_touch BEFORE UPDATE ON branches
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE subscriptions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id          uuid NOT NULL REFERENCES businesses (id),
    plan_id              uuid NOT NULL REFERENCES plans (id),
    status               text NOT NULL,
    current_period_start timestamptz NOT NULL,
    current_period_end   timestamptz NOT NULL,
    trial_ends_at        timestamptz,
    cancel_at            timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_subscriptions_status CHECK (status IN ('trialing', 'active', 'past_due', 'cancelled', 'expired')),
    CONSTRAINT ck_subscriptions_period CHECK (current_period_end > current_period_start)
);
CREATE UNIQUE INDEX uq_subscriptions_one_live ON subscriptions (business_id)
    WHERE status IN ('trialing', 'active', 'past_due');
CREATE INDEX ix_subscriptions_plan ON subscriptions (plan_id);
CREATE TRIGGER trg_subscriptions_touch BEFORE UPDATE ON subscriptions
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE business_entitlement_overrides (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id uuid NOT NULL REFERENCES businesses (id),
    feature_key text NOT NULL,
    limit_value integer,
    reason      text NOT NULL,
    expires_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_entitlement_overrides UNIQUE (business_id, feature_key),
    CONSTRAINT ck_entitlement_overrides_limit CHECK (limit_value IS NULL OR limit_value >= 0)
);
CREATE TRIGGER trg_entitlement_overrides_touch BEFORE UPDATE ON business_entitlement_overrides
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
