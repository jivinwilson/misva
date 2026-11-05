package com.neonicz.miseva.platform.employee;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EmployeeRepository {

    public record MembershipRef(UUID id, UUID userId, String status) {
    }

    public record EmployeeView(UUID membershipId, String status, String email, String firstName, String lastName,
                               String jobTitle, String employeeCode, boolean allBranches, OffsetDateTime invitationExpiresAt,
                               List<String> roles, List<String> branches, List<UUID> roleIds, List<UUID> branchIds,
                               boolean you, boolean manageable) {

        public EmployeeView withFlags(boolean you, boolean manageable) {
            return new EmployeeView(membershipId, status, email, firstName, lastName, jobTitle, employeeCode, allBranches,
                    invitationExpiresAt, roles, branches, roleIds, branchIds, you, manageable);
        }
    }

    public record MemberDetail(UUID id, UUID userId, String status, boolean allBranches, Set<UUID> roleIds,
                               Set<UUID> branchIds, boolean owner) {
    }

    public record InvitationRow(UUID membershipId, UUID userId, OffsetDateTime expiresAt, String email, String firstName,
                                String userStatus, String businessName) {
    }

    private final JdbcClient jdbc;

    public EmployeeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void lockBusiness(UUID businessId) {
        jdbc.sql("SELECT id FROM businesses WHERE id = :id FOR UPDATE").param("id", businessId).query(UUID.class).single();
    }

    public long countMembers(UUID businessId) {
        return jdbc.sql("SELECT count(*) FROM business_memberships WHERE business_id = :b AND status <> 'removed'")
                .param("b", businessId).query(Long.class).single();
    }

    public Optional<MembershipRef> findMembership(UUID businessId, UUID userId) {
        return jdbc.sql("SELECT id, user_id, status FROM business_memberships WHERE business_id = :b AND user_id = :u")
                .param("b", businessId).param("u", userId)
                .query((rs, n) -> new MembershipRef(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("status")))
                .optional();
    }

    public Optional<MembershipRef> findMembershipById(UUID businessId, UUID membershipId) {
        return jdbc.sql("SELECT id, user_id, status FROM business_memberships WHERE business_id = :b AND id = :id")
                .param("b", businessId).param("id", membershipId)
                .query((rs, n) -> new MembershipRef(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("status")))
                .optional();
    }

    public UUID insertInvited(UUID businessId, UUID userId, boolean allBranches, UUID invitedBy, String tokenHash,
                              OffsetDateTime expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO business_memberships (id, business_id, user_id, status, all_branches, invited_by,"
                        + " invitation_token_hash, invitation_expires_at, created_by)"
                        + " VALUES (:id, :b, :u, 'invited', :all, :by, :hash, :exp, :by)")
                .param("id", id).param("b", businessId).param("u", userId).param("all", allBranches)
                .param("by", invitedBy).param("hash", tokenHash).param("exp", expiresAt).update();
        return id;
    }

    public void reissue(UUID businessId, UUID membershipId, boolean allBranches, UUID invitedBy, String tokenHash,
                        OffsetDateTime expiresAt) {
        jdbc.sql("UPDATE business_memberships SET status = 'invited', all_branches = :all, invited_by = :by,"
                        + " invitation_token_hash = :hash, invitation_expires_at = :exp, accepted_at = NULL, updated_by = :by"
                        + " WHERE id = :id AND business_id = :b")
                .param("all", allBranches).param("by", invitedBy).param("hash", tokenHash).param("exp", expiresAt)
                .param("id", membershipId).param("b", businessId).update();
    }

    public void replaceToken(UUID businessId, UUID membershipId, UUID by, String tokenHash, OffsetDateTime expiresAt) {
        jdbc.sql("UPDATE business_memberships SET invitation_token_hash = :hash, invitation_expires_at = :exp, updated_by = :by"
                        + " WHERE id = :id AND business_id = :b AND status = 'invited'")
                .param("hash", tokenHash).param("exp", expiresAt).param("by", by).param("id", membershipId)
                .param("b", businessId).update();
    }

    public boolean revokeInvitation(UUID businessId, UUID membershipId, UUID by) {
        return jdbc.sql("UPDATE business_memberships SET status = 'removed', invitation_token_hash = NULL,"
                        + " invitation_expires_at = NULL, updated_by = :by WHERE id = :id AND business_id = :b AND status = 'invited'")
                .param("by", by).param("id", membershipId).param("b", businessId).update() == 1;
    }

    public void saveProfile(UUID businessId, UUID membershipId, String first, String last, String jobTitle, UUID by) {
        int updated = jdbc.sql("UPDATE employee_profiles SET first_name = :f, last_name = :l, job_title = :j, updated_by = :by"
                        + " WHERE membership_id = :m AND business_id = :b")
                .param("f", first).param("l", last).param("j", jobTitle).param("by", by).param("m", membershipId)
                .param("b", businessId).update();
        if (updated == 0) {
            long next = jdbc.sql("SELECT count(*) FROM employee_profiles WHERE business_id = :b")
                    .param("b", businessId).query(Long.class).single() + 1;
            jdbc.sql("INSERT INTO employee_profiles (business_id, membership_id, employee_code, first_name, last_name,"
                            + " job_title, created_by) VALUES (:b, :m, :code, :f, :l, :j, :by)")
                    .param("b", businessId).param("m", membershipId).param("code", String.format("E%04d", next))
                    .param("f", first).param("l", last).param("j", jobTitle).param("by", by).update();
        }
    }

    public void replaceRoles(UUID businessId, UUID membershipId, Collection<UUID> roleIds, UUID by) {
        jdbc.sql("DELETE FROM membership_roles WHERE membership_id = :m AND business_id = :b")
                .param("m", membershipId).param("b", businessId).update();
        for (UUID roleId : roleIds) {
            jdbc.sql("INSERT INTO membership_roles (membership_id, role_id, business_id, created_by) VALUES (:m, :r, :b, :by)")
                    .param("m", membershipId).param("r", roleId).param("b", businessId).param("by", by).update();
        }
    }

    public void replaceBranchAccess(UUID businessId, UUID membershipId, Collection<UUID> branchIds, UUID by) {
        jdbc.sql("DELETE FROM branch_access WHERE membership_id = :m AND business_id = :b")
                .param("m", membershipId).param("b", businessId).update();
        for (UUID branchId : branchIds) {
            jdbc.sql("INSERT INTO branch_access (membership_id, branch_id, business_id, created_by) VALUES (:m, :br, :b, :by)")
                    .param("m", membershipId).param("br", branchId).param("b", businessId).param("by", by).update();
        }
    }

    public long countExistingBranches(UUID businessId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.sql("SELECT count(*) FROM branches WHERE business_id = :b AND id IN (:ids) AND status = 'active'")
                .param("b", businessId).param("ids", ids).query(Long.class).single();
    }

    public List<EmployeeView> list(UUID businessId) {
        return jdbc.sql("SELECT m.id, m.status, m.all_branches, m.invitation_expires_at, u.email, p.first_name, p.last_name,"
                        + " p.job_title, p.employee_code,"
                        + " (SELECT array_agg(r.name ORDER BY r.name) FROM membership_roles mr JOIN roles r ON r.id = mr.role_id"
                        + "   WHERE mr.membership_id = m.id) AS role_names,"
                        + " (SELECT array_agg(b.name ORDER BY b.name) FROM branch_access ba JOIN branches b ON b.id = ba.branch_id"
                        + "   WHERE ba.membership_id = m.id) AS branch_names,"
                        + " (SELECT array_agg(mr.role_id) FROM membership_roles mr WHERE mr.membership_id = m.id) AS role_ids,"
                        + " (SELECT array_agg(ba.branch_id) FROM branch_access ba WHERE ba.membership_id = m.id) AS branch_ids"
                        + " FROM business_memberships m JOIN users u ON u.id = m.user_id"
                        + " LEFT JOIN employee_profiles p ON p.membership_id = m.id"
                        + " WHERE m.business_id = :b AND m.status <> 'removed'"
                        + " ORDER BY CASE m.status WHEN 'active' THEN 0 WHEN 'invited' THEN 1 ELSE 2 END, p.first_name")
                .param("b", businessId).query(this::mapView).list();
    }

    public Optional<InvitationRow> findInvitationByHash(UUID businessId, String tokenHash) {
        return jdbc.sql("SELECT m.id, m.user_id, m.invitation_expires_at, u.email, u.status AS user_status, p.first_name,"
                        + " b.display_name FROM business_memberships m JOIN users u ON u.id = m.user_id"
                        + " JOIN businesses b ON b.id = m.business_id"
                        + " LEFT JOIN employee_profiles p ON p.membership_id = m.id"
                        + " WHERE m.business_id = :b AND m.invitation_token_hash = :hash AND m.status = 'invited'"
                        + " AND m.invitation_expires_at > now()")
                .param("b", businessId).param("hash", tokenHash)
                .query((rs, n) -> new InvitationRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getObject("invitation_expires_at", OffsetDateTime.class), rs.getString("email"),
                        rs.getString("first_name"), rs.getString("user_status"), rs.getString("display_name")))
                .optional();
    }

    public Optional<MemberDetail> findDetail(UUID businessId, UUID membershipId) {
        return findMembershipById(businessId, membershipId).map(ref -> {
            boolean all = jdbc.sql("SELECT all_branches FROM business_memberships WHERE id = :id")
                    .param("id", membershipId).query(Boolean.class).single();
            Set<UUID> roleIds = new HashSet<>(jdbc.sql("SELECT role_id FROM membership_roles WHERE membership_id = :id")
                    .param("id", membershipId).query(UUID.class).list());
            Set<UUID> branchIds = new HashSet<>(jdbc.sql("SELECT branch_id FROM branch_access WHERE membership_id = :id")
                    .param("id", membershipId).query(UUID.class).list());
            boolean owner = jdbc.sql("SELECT count(*) FROM membership_roles mr JOIN roles r ON r.id = mr.role_id"
                            + " WHERE mr.membership_id = :id AND r.business_id IS NULL AND r.code = 'owner'")
                    .param("id", membershipId).query(Integer.class).single() > 0;
            return new MemberDetail(ref.id(), ref.userId(), ref.status(), all, roleIds, branchIds, owner);
        });
    }

    public void setAllBranches(UUID businessId, UUID membershipId, boolean allBranches, UUID by) {
        jdbc.sql("UPDATE business_memberships SET all_branches = :all, updated_by = :by WHERE id = :id AND business_id = :b")
                .param("all", allBranches).param("by", by).param("id", membershipId).param("b", businessId).update();
    }

    public boolean transitionStatus(UUID businessId, UUID membershipId, Collection<String> from, String to, UUID by) {
        return jdbc.sql("UPDATE business_memberships SET status = :to, updated_by = :by"
                        + " WHERE id = :id AND business_id = :b AND status IN (:from)")
                .param("to", to).param("by", by).param("id", membershipId).param("b", businessId).param("from", from)
                .update() == 1;
    }

    public Optional<String> emailOf(UUID businessId, UUID membershipId) {
        return jdbc.sql("SELECT u.email FROM business_memberships m JOIN users u ON u.id = m.user_id"
                        + " WHERE m.id = :m AND m.business_id = :b")
                .param("m", membershipId).param("b", businessId).query(String.class).optional();
    }

    public void activateMembership(UUID businessId, UUID membershipId, UUID by) {
        jdbc.sql("UPDATE business_memberships SET status = 'active', accepted_at = now(), invitation_token_hash = NULL,"
                        + " invitation_expires_at = NULL, updated_by = :by WHERE id = :id AND business_id = :b")
                .param("by", by).param("id", membershipId).param("b", businessId).update();
    }

    private EmployeeView mapView(ResultSet rs, int n) throws SQLException {
        return new EmployeeView(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("email"),
                rs.getString("first_name"), rs.getString("last_name"), rs.getString("job_title"),
                rs.getString("employee_code"), rs.getBoolean("all_branches"),
                rs.getObject("invitation_expires_at", OffsetDateTime.class),
                strings(rs.getArray("role_names")), strings(rs.getArray("branch_names")),
                uuids(rs.getArray("role_ids")), uuids(rs.getArray("branch_ids")), false, false);
    }

    private static List<UUID> uuids(java.sql.Array array) throws SQLException {
        return array == null ? List.of() : List.of((UUID[]) array.getArray());
    }

    private static List<String> strings(java.sql.Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }
}
