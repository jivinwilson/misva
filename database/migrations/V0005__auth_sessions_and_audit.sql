CREATE TABLE auth_sessions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid NOT NULL REFERENCES users (id),
    active_business_id  uuid REFERENCES businesses (id),
    refresh_token_hash  text NOT NULL,
    rotated_from_id     uuid REFERENCES auth_sessions (id),
    expires_at          timestamptz NOT NULL,
    revoked_at          timestamptz,
    last_used_at        timestamptz,
    ip                  inet,
    user_agent          text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_auth_sessions_token UNIQUE (refresh_token_hash)
);
CREATE INDEX ix_auth_sessions_user_live ON auth_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_auth_sessions_business ON auth_sessions (active_business_id);

CREATE TABLE audit_events (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id   uuid REFERENCES businesses (id),
    branch_id     uuid,
    actor_user_id uuid REFERENCES users (id),
    action        text NOT NULL,
    entity_type   text NOT NULL,
    entity_id     uuid,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    trace_id      text,
    ip            inet,
    metadata      jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT fk_audit_events_branch FOREIGN KEY (business_id, branch_id)
        REFERENCES branches (business_id, id)
);
CREATE INDEX ix_audit_events_business_time ON audit_events (business_id, occurred_at DESC);
CREATE INDEX ix_audit_events_entity ON audit_events (entity_type, entity_id);

CREATE FUNCTION forbid_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only' USING ERRCODE = 'insufficient_privilege';
END $$;
CREATE TRIGGER trg_audit_events_immutable BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_mutation();
CREATE TRIGGER trg_audit_events_no_truncate BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_audit_mutation();
