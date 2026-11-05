CREATE TABLE business_memberships (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id           uuid NOT NULL REFERENCES businesses (id),
    user_id               uuid NOT NULL REFERENCES users (id),
    status                text NOT NULL DEFAULT 'invited',
    all_branches          boolean NOT NULL DEFAULT false,
    invited_by            uuid REFERENCES users (id),
    invitation_token_hash text,
    invitation_expires_at timestamptz,
    accepted_at           timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_at            timestamptz NOT NULL DEFAULT now(),
    updated_by            uuid,
    version               bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_memberships_business_user UNIQUE (business_id, user_id),
    CONSTRAINT uq_memberships_business_id UNIQUE (business_id, id),
    CONSTRAINT uq_memberships_invitation_token UNIQUE (invitation_token_hash),
    CONSTRAINT ck_memberships_status CHECK (status IN ('invited', 'active', 'suspended', 'removed'))
);
CREATE INDEX ix_memberships_user ON business_memberships (user_id);
CREATE TRIGGER trg_memberships_touch BEFORE UPDATE ON business_memberships
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE employee_profiles (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id      uuid NOT NULL REFERENCES businesses (id),
    membership_id    uuid NOT NULL,
    employee_code    text NOT NULL,
    first_name       text NOT NULL,
    last_name        text NOT NULL,
    phone            text,
    job_title        text,
    hire_date        date,
    termination_date date,
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    updated_by       uuid,
    version          bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_employee_profiles_membership UNIQUE (membership_id),
    CONSTRAINT uq_employee_profiles_code UNIQUE (business_id, employee_code),
    CONSTRAINT fk_employee_profiles_membership FOREIGN KEY (business_id, membership_id)
        REFERENCES business_memberships (business_id, id),
    CONSTRAINT ck_employee_profiles_dates CHECK (termination_date IS NULL OR hire_date IS NULL OR termination_date >= hire_date)
);
CREATE TRIGGER trg_employee_profiles_touch BEFORE UPDATE ON employee_profiles
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE roles (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id uuid REFERENCES businesses (id),
    code        text NOT NULL,
    name        text NOT NULL,
    description text,
    is_system   boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_roles_code CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_roles_system_scope CHECK (NOT is_system OR business_id IS NULL)
);
CREATE UNIQUE INDEX uq_roles_system_code ON roles (code) WHERE business_id IS NULL;
CREATE UNIQUE INDEX uq_roles_business_code ON roles (business_id, code) WHERE business_id IS NOT NULL;
CREATE TRIGGER trg_roles_touch BEFORE UPDATE ON roles
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TABLE role_permissions (
    role_id       uuid NOT NULL REFERENCES roles (id),
    permission_id uuid NOT NULL REFERENCES permissions (id),
    business_id   uuid REFERENCES businesses (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

CREATE FUNCTION sync_role_permission_business() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    SELECT business_id INTO NEW.business_id FROM roles WHERE id = NEW.role_id;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_role_permissions_sync BEFORE INSERT ON role_permissions
    FOR EACH ROW EXECUTE FUNCTION sync_role_permission_business();

CREATE TABLE membership_roles (
    membership_id uuid NOT NULL,
    role_id       uuid NOT NULL REFERENCES roles (id),
    business_id   uuid NOT NULL REFERENCES businesses (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    PRIMARY KEY (membership_id, role_id),
    CONSTRAINT fk_membership_roles_membership FOREIGN KEY (business_id, membership_id)
        REFERENCES business_memberships (business_id, id)
);
CREATE INDEX ix_membership_roles_role ON membership_roles (role_id);

CREATE FUNCTION check_membership_role_scope() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    role_business uuid;
BEGIN
    SELECT business_id INTO role_business FROM roles WHERE id = NEW.role_id;
    IF role_business IS NOT NULL AND role_business <> NEW.business_id THEN
        RAISE EXCEPTION 'Role belongs to a different business' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_membership_roles_scope BEFORE INSERT OR UPDATE ON membership_roles
    FOR EACH ROW EXECUTE FUNCTION check_membership_role_scope();

CREATE TABLE branch_access (
    membership_id uuid NOT NULL,
    branch_id     uuid NOT NULL,
    business_id   uuid NOT NULL REFERENCES businesses (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    PRIMARY KEY (membership_id, branch_id),
    CONSTRAINT fk_branch_access_membership FOREIGN KEY (business_id, membership_id)
        REFERENCES business_memberships (business_id, id),
    CONSTRAINT fk_branch_access_branch FOREIGN KEY (business_id, branch_id)
        REFERENCES branches (business_id, id)
);
CREATE INDEX ix_branch_access_branch ON branch_access (branch_id);
