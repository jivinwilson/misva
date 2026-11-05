package com.neonicz.miseva.platform.rbac;

import java.util.HashSet;
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

    public Set<String> permissionsOf(UUID membershipId) {
        return new HashSet<>(jdbc.sql("SELECT DISTINCT p.code FROM membership_roles mr"
                        + " JOIN role_permissions rp ON rp.role_id = mr.role_id"
                        + " JOIN permissions p ON p.id = rp.permission_id WHERE mr.membership_id = :membership")
                .param("membership", membershipId).query(String.class).list());
    }
}
