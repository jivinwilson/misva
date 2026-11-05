package com.neonicz.miseva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class TenantIsolationTests {

    @Autowired
    DataSource appDataSource;

    @Autowired
    TransactionTemplate tx;

    @Value("${spring.flyway.url}")
    String url;
    @Value("${spring.flyway.user}")
    String ownerUser;
    @Value("${spring.flyway.password}")
    String ownerPassword;

    JdbcTemplate app;
    JdbcTemplate owner;

    UUID businessA = UUID.randomUUID();
    UUID businessB = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID branchA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        app = new JdbcTemplate(appDataSource);
        owner = new JdbcTemplate(new DriverManagerDataSource(url, ownerUser, ownerPassword));
        cleanup();
        UUID industry = owner.queryForObject("SELECT id FROM industry_types WHERE code = 'salon'", UUID.class);
        for (UUID b : new UUID[] {businessA, businessB}) {
            owner.update("INSERT INTO businesses (id, slug, legal_name, display_name, industry_type_id, country_code,"
                    + " default_currency, timezone) VALUES (?, ?, 'L', 'D', ?, 'IN', 'INR', 'Asia/Kolkata')",
                    b, "t-" + b.toString().substring(0, 8), industry);
        }
        owner.update("INSERT INTO branches (id, business_id, code, name, country_code, timezone)"
                + " VALUES (?, ?, 'MAIN', 'Main', 'IN', 'Asia/Kolkata')", branchA, businessA);
        owner.update("INSERT INTO users (id, email, password_hash, full_name) VALUES (?, ?, 'x', 'User A')",
                userA, "a-" + userA + "@test.local");
        owner.update("INSERT INTO business_memberships (business_id, user_id, status) VALUES (?, ?, 'active')",
                businessA, userA);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        String testBusinesses = "(SELECT id FROM businesses WHERE slug LIKE 't-%')";
        owner.execute("ALTER TABLE audit_events DISABLE TRIGGER trg_audit_events_immutable");
        try {
            owner.update("DELETE FROM audit_events WHERE business_id IN " + testBusinesses);
        } finally {
            owner.execute("ALTER TABLE audit_events ENABLE TRIGGER trg_audit_events_immutable");
        }
        owner.update("DELETE FROM business_memberships WHERE business_id IN " + testBusinesses);
        owner.update("DELETE FROM branches WHERE business_id IN " + testBusinesses);
        owner.update("DELETE FROM users WHERE email LIKE '%@test.local'");
        owner.update("DELETE FROM businesses WHERE slug LIKE 't-%'");
    }

    private <T> T asTenant(UUID business, UUID user, java.util.function.Supplier<T> work) {
        return tx.execute(status -> {
            app.query("SELECT set_config('app.business_id', ?, true), set_config('app.user_id', ?, true)",
                    rs -> { }, business == null ? "" : business.toString(), user == null ? "" : user.toString());
            return work.get();
        });
    }

    private int branchCount() {
        return app.queryForObject("SELECT count(*) FROM branches", Integer.class);
    }

    @Test
    void tenantSeesOnlyItsOwnRows() {
        assertThat(asTenant(businessA, null, this::branchCount)).isEqualTo(1);
        assertThat(asTenant(businessB, null, this::branchCount)).isZero();
    }

    @Test
    void noContextSeesNothing() {
        assertThat(asTenant(null, null, this::branchCount)).isZero();
        assertThat(app.queryForObject("SELECT count(*) FROM branches", Integer.class)).isZero();
    }

    @Test
    void contextDoesNotLeakAcrossPooledConnections() {
        asTenant(businessA, null, this::branchCount);
        assertThat(app.queryForObject("SELECT count(*) FROM branches", Integer.class)).isZero();
    }

    @Test
    void tenantCannotWriteIntoAnotherBusiness() {
        assertThatThrownBy(() -> asTenant(businessB, null, () -> app.update(
                "INSERT INTO branches (business_id, code, name, country_code, timezone)"
                        + " VALUES (?, 'X', 'Evil', 'IN', 'Asia/Kolkata')", businessA)))
                .rootCause().hasMessageContaining("row-level security");
    }

    @Test
    void tenantCannotUpdateAnotherBusinessesRows() {
        int updated = asTenant(businessB, null, () -> app.update("UPDATE branches SET name = 'Hacked' WHERE id = ?", branchA));
        assertThat(updated).isZero();
    }

    @Test
    void crossTenantMembershipLinkIsRejectedByForeignKey() {
        UUID memberB = UUID.randomUUID();
        owner.update("INSERT INTO business_memberships (id, business_id, user_id, status) VALUES (?, ?, ?, 'active')",
                memberB, businessB, userA);
        assertThatThrownBy(() -> owner.update(
                "INSERT INTO branch_access (membership_id, branch_id, business_id) VALUES (?, ?, ?)",
                memberB, branchA, businessB))
                .rootCause().hasMessageContaining("fk_branch_access_branch");
    }

    @Test
    void userSeesOwnBusinessesWithoutTenantContext() {
        int visible = asTenant(null, userA, () -> app.queryForObject("SELECT count(*) FROM businesses", Integer.class));
        assertThat(visible).isEqualTo(1);
    }

    @Test
    void systemRolesAreReadableButNotWritableByTenants() {
        int roles = asTenant(businessA, null, () -> app.queryForObject(
                "SELECT count(*) FROM roles WHERE business_id IS NULL", Integer.class));
        assertThat(roles).isGreaterThanOrEqualTo(3);
        int updated = asTenant(businessA, null, () -> app.update("UPDATE roles SET name = 'Hacked' WHERE business_id IS NULL"));
        assertThat(updated).isZero();
    }

    @Test
    void auditEventsAreAppendOnly() {
        asTenant(businessA, null, () -> app.update(
                "INSERT INTO audit_events (business_id, action, entity_type) VALUES (?, 'test', 'branch')", businessA));
        assertThatThrownBy(() -> asTenant(businessA, null, () -> app.update("UPDATE audit_events SET action = 'x'")))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> asTenant(businessA, null, () -> app.update("DELETE FROM audit_events")))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> owner.update("UPDATE audit_events SET action = 'x'"))
                .rootCause().hasMessageContaining("append-only");
    }

    @Test
    void industryCannotBeChanged() {
        UUID other = owner.queryForObject("SELECT id FROM industry_types WHERE code = 'gym'", UUID.class);
        assertThatThrownBy(() -> owner.update("UPDATE businesses SET industry_type_id = ? WHERE id = ?", other, businessA))
                .rootCause().hasMessageContaining("cannot be changed");
    }

    @Test
    void appRoleCannotModifyReferenceData() {
        assertThatThrownBy(() -> app.update("UPDATE permissions SET description = 'x'"))
                .rootCause().hasMessageContaining("permission denied");
    }
}
