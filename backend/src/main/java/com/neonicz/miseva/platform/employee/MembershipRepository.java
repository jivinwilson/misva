package com.neonicz.miseva.platform.employee;

import com.neonicz.miseva.platform.identity.MembershipDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository implements MembershipDirectory {

    private final JdbcClient jdbc;

    public MembershipRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BusinessRef> activeBusinessesOf(UUID userId) {
        return jdbc.sql("SELECT b.id, b.display_name, b.slug FROM business_memberships m"
                        + " JOIN businesses b ON b.id = m.business_id"
                        + " WHERE m.user_id = :user AND m.status = 'active' AND b.status = 'active' ORDER BY b.display_name")
                .param("user", userId)
                .query((rs, n) -> new BusinessRef(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("slug")))
                .list();
    }

    @Override
    public Optional<UUID> activeMembershipId(UUID userId, UUID businessId) {
        return jdbc.sql("SELECT m.id FROM business_memberships m JOIN businesses b ON b.id = m.business_id"
                        + " WHERE m.user_id = :user AND m.business_id = :business AND m.status = 'active' AND b.status = 'active'")
                .param("user", userId).param("business", businessId)
                .query(UUID.class).optional();
    }

    public UUID insertActiveOwner(UUID businessId, UUID userId, String firstName, String lastName) {
        UUID membershipId = UUID.randomUUID();
        jdbc.sql("INSERT INTO business_memberships (id, business_id, user_id, status, all_branches, accepted_at, created_by)"
                        + " VALUES (:id, :business, :user, 'active', true, now(), :user)")
                .param("id", membershipId).param("business", businessId).param("user", userId).update();
        jdbc.sql("INSERT INTO employee_profiles (business_id, membership_id, employee_code, first_name, last_name, created_by)"
                        + " VALUES (:business, :membership, 'E0001', :first, :last, :user)")
                .param("business", businessId).param("membership", membershipId)
                .param("first", firstName).param("last", lastName).param("user", userId).update();
        return membershipId;
    }
}
