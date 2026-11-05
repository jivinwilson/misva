INSERT INTO permissions (code, module, description) VALUES
    ('business.business.read',    'business',     'View business profile'),
    ('business.business.update',  'business',     'Edit business profile'),
    ('business.branch.read',      'business',     'View branches'),
    ('business.branch.create',    'business',     'Create branches'),
    ('business.branch.update',    'business',     'Edit branches'),
    ('employee.member.read',      'employee',     'View employees'),
    ('employee.member.invite',    'employee',     'Invite employees'),
    ('employee.member.update',    'employee',     'Edit employees and their access'),
    ('employee.member.remove',    'employee',     'Remove employees'),
    ('rbac.role.read',            'rbac',         'View roles and permissions'),
    ('rbac.role.manage',          'rbac',         'Create and edit custom roles'),
    ('subscription.plan.read',    'subscription', 'View subscription and entitlements'),
    ('audit.event.read',          'audit',        'View the audit trail');

INSERT INTO roles (business_id, code, name, description, is_system) VALUES
    (NULL, 'owner',   'Owner',   'Full access to the business', true),
    (NULL, 'manager', 'Manager', 'Operate branches and staff',  true),
    (NULL, 'staff',   'Staff',   'Basic read access',           true);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.business_id IS NULL AND r.code = 'owner';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'business.business.read', 'business.branch.read', 'business.branch.update',
    'employee.member.read', 'employee.member.invite', 'employee.member.update',
    'rbac.role.read', 'subscription.plan.read')
WHERE r.business_id IS NULL AND r.code = 'manager';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'business.business.read', 'business.branch.read')
WHERE r.business_id IS NULL AND r.code = 'staff';

INSERT INTO plans (code, name, billing_interval) VALUES
    ('starter', 'Starter', 'monthly'),
    ('growth',  'Growth',  'monthly'),
    ('scale',   'Scale',   'monthly');

INSERT INTO plan_entitlements (plan_id, feature_key, limit_value)
SELECT p.id, e.feature_key, e.limit_value
FROM plans p JOIN (VALUES
    ('starter', 'max_branches', 1),
    ('starter', 'max_employees', 10),
    ('growth',  'max_branches', 5),
    ('growth',  'max_employees', 50),
    ('scale',   'max_branches', NULL),
    ('scale',   'max_employees', NULL)
) AS e(plan_code, feature_key, limit_value) ON e.plan_code = p.code;
