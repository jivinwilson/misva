package com.neonicz.miseva;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BranchManagementTests {

    static final String DOMAIN = "@branchtest.local";
    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;
    @Autowired
    PasswordEncoder encoder;

    @Value("${spring.flyway.url}")
    String url;
    @Value("${spring.flyway.user}")
    String ownerUser;
    @Value("${spring.flyway.password}")
    String ownerPassword;

    JdbcTemplate owner;

    @BeforeEach
    void setUp() {
        owner = new JdbcTemplate(new DriverManagerDataSource(url, ownerUser, ownerPassword));
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        List<UUID> businesses = owner.queryForList(
                "SELECT DISTINCT m.business_id FROM business_memberships m JOIN users u ON u.id = m.user_id WHERE u.email LIKE ?",
                UUID.class, "%" + DOMAIN);
        owner.execute("ALTER TABLE audit_events DISABLE TRIGGER trg_audit_events_immutable");
        try {
            for (UUID b : businesses) {
                owner.update("DELETE FROM audit_events WHERE business_id = ?", b);
            }
        } finally {
            owner.execute("ALTER TABLE audit_events ENABLE TRIGGER trg_audit_events_immutable");
        }
        owner.update("DELETE FROM auth_sessions WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%" + DOMAIN);
        for (UUID b : businesses) {
            owner.update("DELETE FROM branch_access WHERE business_id = ?", b);
            owner.update("DELETE FROM membership_roles WHERE business_id = ?", b);
            owner.update("DELETE FROM employee_profiles WHERE business_id = ?", b);
            owner.update("DELETE FROM business_memberships WHERE business_id = ?", b);
            owner.update("DELETE FROM business_entitlement_overrides WHERE business_id = ?", b);
            owner.update("DELETE FROM subscriptions WHERE business_id = ?", b);
            owner.update("DELETE FROM branches WHERE business_id = ?", b);
            owner.update("DELETE FROM businesses WHERE id = ?", b);
        }
        owner.update("DELETE FROM users WHERE email LIKE ?", "%" + DOMAIN);
    }

    record Tenant(String businessId, String branchId, String access, String email) {
    }

    private Tenant register() throws Exception {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
        String body = mvc.perform(post("/api/v1/businesses/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Branchtest %s","industry":"retail","countryCode":"IN","currency":"INR",
                                 "timezone":"Asia/Kolkata","ownerFirstName":"Asha","ownerLastName":"Rao",
                                 "email":"%s","password":"%s"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8), email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Tenant(JsonPath.read(body, "$.businessId"), JsonPath.read(body, "$.headOfficeBranchId"),
                JsonPath.read(body, "$.tokens.accessToken"), email);
    }

    private void allowBranches(String businessId, int limit) {
        owner.update("INSERT INTO business_entitlement_overrides (business_id, feature_key, limit_value, reason)"
                + " VALUES (?::uuid, 'max_branches', ?, 'test')", businessId, limit);
    }

    private String bearer(Tenant t) {
        return "Bearer " + t.access();
    }

    private String createJson(String code) {
        return "{\"name\":\"Branch %s\",\"code\":\"%s\",\"city\":\"Kochi\"}".formatted(code, code);
    }

    @Test
    void planLimitBlocksExtraBranches() throws Exception {
        Tenant t = register();
        mvc.perform(post("/api/v1/branches").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("kochi")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BRANCH_LIMIT_REACHED"));
        mvc.perform(get("/api/v1/branches").header("Authorization", bearer(t)))
                .andExpect(jsonPath("$.maxBranches").value(1)).andExpect(jsonPath("$.used").value(1));
    }

    @Test
    void createUpdateAndToggleBranch() throws Exception {
        Tenant t = register();
        allowBranches(t.businessId(), 3);

        String created = mvc.perform(post("/api/v1/branches").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson("kochi")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("KOCHI"))
                .andExpect(jsonPath("$.countryCode").value("IN")).andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        int version = JsonPath.read(created, "$.version");

        mvc.perform(post("/api/v1/branches").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("KOCHI")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRANCH_CODE_TAKEN"));
        mvc.perform(post("/api/v1/branches").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"code\":\"a b\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String updated = mvc.perform(put("/api/v1/branches/" + id).header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d,\"name\":\"Kochi Marine Drive\",\"city\":\"Ernakulam\"}".formatted(version)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Kochi Marine Drive"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat((int) JsonPath.read(updated, "$.version")).isGreaterThan(version);

        mvc.perform(put("/api/v1/branches/" + id).header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d,\"name\":\"Stale edit\"}".formatted(version)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));

        mvc.perform(post("/api/v1/branches/" + id + "/deactivate").header("Authorization", bearer(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("inactive"));
        mvc.perform(post("/api/v1/branches/" + id + "/activate").header("Authorization", bearer(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        mvc.perform(post("/api/v1/branches/" + t.branchId() + "/deactivate").header("Authorization", bearer(t)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HEAD_OFFICE_CANNOT_BE_DEACTIVATED"));
    }

    @Test
    void anotherBusinessCannotReadOrChangeMyBranch() throws Exception {
        Tenant a = register();
        Tenant b = register();
        mvc.perform(get("/api/v1/branches/" + a.branchId()).header("Authorization", bearer(b)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/branches/" + a.branchId()).header("Authorization", bearer(b))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/branches/" + a.branchId() + "/deactivate").header("Authorization", bearer(b)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/branches/" + a.branchId()).header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Head Office"));
    }

    @Test
    void branchRestrictedManagerSeesAndEditsOnlyAssignedBranches() throws Exception {
        Tenant t = register();
        allowBranches(t.businessId(), 3);
        String second = JsonPath.read(mvc.perform(post("/api/v1/branches").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson("goa")))
                .andReturn().getResponse().getContentAsString(), "$.id");

        UUID userId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        String email = "mgr" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
        owner.update("INSERT INTO users (id, email, password_hash, full_name, status) VALUES (?, ?, ?, 'Manager', 'active')",
                userId, email, encoder.encode(PASSWORD));
        owner.update("INSERT INTO business_memberships (id, business_id, user_id, status, all_branches)"
                + " VALUES (?, ?::uuid, ?, 'active', false)", membershipId, t.businessId(), userId);
        owner.update("INSERT INTO membership_roles (membership_id, role_id, business_id)"
                + " SELECT ?, id, ?::uuid FROM roles WHERE business_id IS NULL AND code = 'manager'", membershipId, t.businessId());
        owner.update("INSERT INTO branch_access (membership_id, branch_id, business_id) VALUES (?, ?::uuid, ?::uuid)",
                membershipId, t.branchId(), t.businessId());

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String mgr = "Bearer " + JsonPath.<String>read(login, "$.accessToken");

        mvc.perform(get("/api/v1/branches").header("Authorization", mgr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(t.branchId()));
        mvc.perform(get("/api/v1/branches/" + second).header("Authorization", mgr)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/branches/" + second + "/deactivate").header("Authorization", mgr))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/branches").header("Authorization", mgr).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("delhi")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(put("/api/v1/branches/" + t.branchId()).header("Authorization", mgr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"name\":\"HQ renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("HQ renamed"));
    }
}
