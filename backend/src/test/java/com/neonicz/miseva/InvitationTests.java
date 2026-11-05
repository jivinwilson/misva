package com.neonicz.miseva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "miseva.invitations.expose-link=true")
@AutoConfigureMockMvc
class InvitationTests {

    static final String DOMAIN = "@invitetest.local";
    static final String PASSWORD = "correct-horse-battery";

    @Autowired
    MockMvc mvc;

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
        TestCleanup.removeEverythingFor(owner, DOMAIN);
    }

    @AfterEach
    void tearDown() {
        TestCleanup.removeEverythingFor(owner, DOMAIN);
    }

    record Org(String businessId, String branchId, String email, String bearer) {
    }

    private String email() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
    }

    private Org register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/businesses/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Invitetest %s","industry":"gym","countryCode":"IN","currency":"INR",
                                 "timezone":"Asia/Kolkata","ownerFirstName":"Asha","ownerLastName":"Rao",
                                 "email":"%s","password":"%s"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8), email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Org(JsonPath.read(body, "$.businessId"), JsonPath.read(body, "$.headOfficeBranchId"), email,
                "Bearer " + JsonPath.<String>read(body, "$.tokens.accessToken"));
    }

    private String roleId(Org org, String code) throws Exception {
        String body = mvc.perform(get("/api/v1/roles/assignable").header("Authorization", org.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.code=='" + code + "')].id");
        return ids.get(0);
    }

    private ResultActions invite(Org by, String email, String roleId, String branchesJson) throws Exception {
        return mvc.perform(post("/api/v1/employees/invitations").header("Authorization", by.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Dev","lastName":"Nair","email":"%s","jobTitle":"Therapist","roleIds":["%s"],%s}
                        """.formatted(email, roleId, branchesJson)));
    }

    private String hqOnly(Org org) {
        return "\"allBranches\":false,\"branchIds\":[\"%s\"]".formatted(org.branchId());
    }

    private static final String ALL = "\"allBranches\":true";

    private String tokenOf(ResultActions invited) throws Exception {
        String url = JsonPath.read(invited.andReturn().getResponse().getContentAsString(), "$.inviteUrl");
        return url.substring(url.indexOf("token=") + 6);
    }

    private ResultActions accept(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":%s}".formatted(token, password == null ? "null" : "\"" + password + "\"")));
    }

    private Org join(Org owner, String role, String branches) throws Exception {
        String email = email();
        String token = tokenOf(invite(owner, email, roleId(owner, role), branches).andExpect(status().isCreated()));
        String body = accept(token, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Org(owner.businessId(), owner.branchId(), email, "Bearer " + JsonPath.<String>read(body, "$.accessToken"));
    }

    @Test
    void inviteThenAcceptGivesTheInviteeTheirRoleAndBranches() throws Exception {
        Org org = register(email());
        mvc.perform(get("/api/v1/roles/assignable").header("Authorization", org.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("manager")))
                .andExpect(jsonPath("$[*].code", hasItem("staff")))
                .andExpect(jsonPath("$[*].code", not(hasItem("owner"))));

        String inviteeEmail = email();
        ResultActions invited = invite(org, inviteeEmail, roleId(org, "staff"), hqOnly(org)).andExpect(status().isCreated());
        String token = tokenOf(invited);

        mvc.perform(get("/api/v1/employees").header("Authorization", org.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email=='" + inviteeEmail + "')].status").value("invited"));

        mvc.perform(get("/api/v1/invitations/preview").param("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.existingAccount").value(false))
                .andExpect(jsonPath("$.firstName").value("Dev"));

        accept(token, "short").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        String accepted = accept(token, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String staff = "Bearer " + JsonPath.<String>read(accepted, "$.accessToken");

        mvc.perform(get("/api/v1/me").header("Authorization", staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessId").value(org.businessId()))
                .andExpect(jsonPath("$.permissions", hasItem("business.branch.read")))
                .andExpect(jsonPath("$.permissions", not(hasItem("employee.member.invite"))));
        mvc.perform(get("/api/v1/branches").header("Authorization", staff))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/employees").header("Authorization", staff)).andExpect(status().isForbidden());
        invite(Org.class.cast(new Org(org.businessId(), org.branchId(), inviteeEmail, staff)), email(), roleId(org, "staff"), ALL)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        accept(token, PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INVITATION"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(inviteeEmail, PASSWORD)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/employees").header("Authorization", org.bearer()))
                .andExpect(jsonPath("$[?(@.email=='" + inviteeEmail + "')].status").value("active"));
    }

    @Test
    void managerCannotGrantMoreThanTheyHave() throws Exception {
        Org org = register(email());
        Org manager = join(org, "manager", hqOnly(org));

        String ownerRoleId = owner.queryForObject("SELECT id::text FROM roles WHERE business_id IS NULL AND code = 'owner'", String.class);
        invite(manager, email(), ownerRoleId, hqOnly(org))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
        invite(manager, email(), roleId(manager, "manager"), ALL)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BRANCH_SCOPE_EXCEEDED"));
        invite(manager, email(), roleId(manager, "staff"), hqOnly(org)).andExpect(status().isCreated());
    }

    @Test
    void expiredResentAndRevokedInvitationsStopWorking() throws Exception {
        Org org = register(email());
        String inviteeEmail = email();
        ResultActions first = invite(org, inviteeEmail, roleId(org, "staff"), ALL).andExpect(status().isCreated());
        String oldToken = tokenOf(first);
        String membershipId = JsonPath.read(first.andReturn().getResponse().getContentAsString(), "$.membershipId");

        owner.update("UPDATE business_memberships SET invitation_expires_at = now() - interval '1 minute' WHERE id = ?::uuid", membershipId);
        mvc.perform(get("/api/v1/invitations/preview").param("token", oldToken)).andExpect(status().isBadRequest());

        ResultActions resent = mvc.perform(post("/api/v1/employees/" + membershipId + "/resend").header("Authorization", org.bearer()))
                .andExpect(status().isOk());
        String newToken = tokenOf(resent);
        assertThat(newToken).isNotEqualTo(oldToken);
        mvc.perform(get("/api/v1/invitations/preview").param("token", oldToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/invitations/preview").param("token", newToken)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/employees/" + membershipId + "/revoke").header("Authorization", org.bearer()))
                .andExpect(status().isNoContent());
        accept(newToken, PASSWORD).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/employees/" + membershipId + "/revoke").header("Authorization", org.bearer()))
                .andExpect(status().isNotFound());

        ResultActions again = invite(org, inviteeEmail, roleId(org, "staff"), ALL).andExpect(status().isCreated());
        accept(tokenOf(again), PASSWORD).andExpect(status().isOk());
        invite(org, inviteeEmail, roleId(org, "staff"), ALL)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    void planEmployeeLimitIsEnforced() throws Exception {
        Org org = register(email());
        owner.update("INSERT INTO business_entitlement_overrides (business_id, feature_key, limit_value, reason)"
                + " VALUES (?::uuid, 'max_employees', 1, 'test')", org.businessId());
        invite(org, email(), roleId(org, "staff"), ALL)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMPLOYEE_LIMIT_REACHED"));
    }

    @Test
    void existingAccountJoinsASecondBusinessAndMustChooseWhenSigningIn() throws Exception {
        Org a = register(email());
        String sharedEmail = email();
        register(sharedEmail);

        String token = tokenOf(invite(a, sharedEmail, roleId(a, "manager"), ALL).andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/invitations/preview").param("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.existingAccount").value(true));
        accept(token, "wrong-password-123").andExpect(status().isUnauthorized());
        accept(token, PASSWORD).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(sharedEmail, PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUSINESS_SELECTION_REQUIRED"))
                .andExpect(jsonPath("$.businesses.length()").value(2));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\",\"businessId\":\"%s\"}".formatted(sharedEmail, PASSWORD, a.businessId())))
                .andExpect(status().isOk());
    }

    @Test
    void anotherBusinessCannotSeeOrRevokeMyInvitations() throws Exception {
        Org a = register(email());
        Org b = register(email());
        String inviteeEmail = email();
        String membershipId = JsonPath.read(invite(a, inviteeEmail, roleId(a, "staff"), ALL).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.membershipId");

        mvc.perform(get("/api/v1/employees").header("Authorization", b.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].email", not(hasItem(inviteeEmail))));
        mvc.perform(post("/api/v1/employees/" + membershipId + "/revoke").header("Authorization", b.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/employees/" + membershipId + "/resend").header("Authorization", b.bearer()))
                .andExpect(status().isNotFound());
    }
}
