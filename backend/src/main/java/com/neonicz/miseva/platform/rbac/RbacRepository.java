package com.neonicz.miseva.platform.rbac;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RbacRepository {

    private final JdbcClient jdbc;

    public RbacRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void assignSystemRole(UUID businessId, UUID membershipId, String roleCode) {
        int rows = jdbc.sql("INSERT INTO membership_roles (membership_id, role_id, business_id)"
                        + " SELECT :membership, id, :business FROM roles WHERE business_id IS NULL AND code = :code")
                .param("membership", membershipId).param("business", businessId).param("code", roleCode).update();
        if (rows != 1) {
            throw new IllegalStateException("System role not found: " + roleCode);
        }
    }

    public record RoleView(UUID id, String code, String name, boolean system) {
    }

    /** Roles an inviter may hand out: every visible role except the owner role. */
    public List<RoleView> assignableRoles() {
        return jdbc.sql("SELECT id, code, name, business_id IS NULL AS system FROM roles"
                        + " WHERE NOT (business_id IS NULL AND code = 'owner') ORDER BY system DESC, name")
                .query((rs, n) -> new RoleView(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getString("name"), rs.getBoolean("system")))
                .list();
    }

    public Set<String> permissionsOfRole(UUID roleId) {
        return new HashSet<>(jdbc.sql("SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id"
                        + " WHERE rp.role_id = :role").param("role", roleId).query(String.class).list());
    }

    public Set<String> permissionsOf(UUID membershipId) {
        return new HashSet<>(jdbc.sql("SELECT DISTINCT p.code FROM membership_roles mr"
                        + " JOIN role_permissions rp ON rp.role_id = mr.role_id"
                        + " JOIN permissions p ON p.id = rp.permission_id WHERE mr.membership_id = :membership")
                .param("membership", membershipId).query(String.class).list());
    }
}
