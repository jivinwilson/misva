package com.neonicz.miseva;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "miseva.invitations.expose-link=true")
@AutoConfigureMockMvc
class MemberManagementTests {

    static final String DOMAIN = "@membertest.local";
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
        TestCleanup.removeEverythingFor(owner, DOMAIN);
    }

    @AfterEach
    void tearDown() {
        TestCleanup.removeEverythingFor(owner, DOMAIN);
    }

    record Person(String businessId, String branchId, String email, String bearer, String membershipId) {
    }

    private String email() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
    }

    private Person register() throws Exception {
        String email = email();
        String body = mvc.perform(post("/api/v1/businesses/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"businessName":"Membertest %s","industry":"clinic","countryCode":"IN","currency":"INR",
                                 "timezone":"Asia/Kolkata","ownerFirstName":"Asha","ownerLastName":"Rao",
                                 "email":"%s","password":"%s"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8), email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.<String>read(body, "$.tokens.accessToken");
        Person p = new Person(JsonPath.read(body, "$.businessId"), JsonPath.read(body, "$.headOfficeBranchId"), email, bearer, null);
        return new Person(p.businessId(), p.branchId(), email, bearer, membershipIdOf(p, email));
    }

    private String membershipIdOf(Person viewer, String email) throws Exception {
        String list = mvc.perform(get("/api/v1/employees").header("Authorization", viewer.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(list, "$[?(@.email=='" + email + "')].membershipId");
        return ids.get(0);
    }

    private void raiseLimit(Person org, String feature, int limit) {
        owner.update("INSERT INTO business_entitlement_overrides (business_id, feature_key, limit_value, reason)"
                + " VALUES (?::uuid, ?, ?, 'test')", org.businessId(), feature, limit);
    }

    private String roleId(Person by, String code) throws Exception {
        String body = mvc.perform(get("/api/v1/roles/assignable").header("Authorization", by.bearer()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<List<String>>read(body, "$[?(@.code=='" + code + "')].id").get(0);
    }

    private String branchesJson(String... ids) {
        return "\"allBranches\":false,\"branchIds\":[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]";
    }

    private static final String ALL = "\"allBranches\":true";

    private Person join(Person org, String role, String branches) throws Exception {
        String email = email();
        String invited = mvc.perform(post("/api/v1/employees/invitations").header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Dev","lastName":"Nair","email":"%s","roleIds":["%s"],%s}
                                """.formatted(email, roleId(org, role), branches)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String link = JsonPath.read(invited, "$.inviteUrl");
        String token = link.substring(link.indexOf("token=") + 6);
        String accepted = mvc.perform(post("/api/v1/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Person(org.businessId(), org.branchId(), email, "Bearer " + JsonPath.<String>read(accepted, "$.accessToken"),
                JsonPath.read(invited, "$.membershipId"));
    }

    private ResultActions edit(Person by, String membershipId, String roleId, String branches) throws Exception {
        return mvc.perform(put("/api/v1/employees/" + membershipId).header("Authorization", by.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Dev","lastName":"Nair","jobTitle":"Lead","roleIds":["%s"],%s}
                        """.formatted(roleId, branches)));
    }

    private ResultActions act(Person by, String membershipId, String action) throws Exception {
        return mvc.perform(post("/api/v1/employees/" + membershipId + "/" + action).header("Authorization", by.bearer()));
    }

    private String createBranch(Person by, String code) throws Exception {
        String body = mvc.perform(post("/api/v1/branches").header("Authorization", by.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Branch " + code + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void ownerChangesARolesAndBranchesAndItTakesEffectImmediately() throws Exception {
        Person org = register();
        raiseLimit(org, "max_branches", 3);
        String goa = createBranch(org, "GOA");
        Person staff = join(org, "staff", branchesJson(org.branchId()));

        edit(org, staff.membershipId(), roleId(org, "manager"), branchesJson(goa)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").header("Authorization", staff.bearer()))
                .andExpect(jsonPath("$.permissions", hasItem("employee.member.invite")));
        mvc.perform(get("/api/v1/branches").header("Authorization", staff.bearer()))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(goa));
        mvc.perform(get("/api/v1/employees").header("Authorization", org.bearer()))
                .andExpect(jsonPath("$[?(@.email=='" + staff.email() + "')].jobTitle").value("Lead"))
                .andExpect(jsonPath("$[?(@.email=='" + staff.email() + "')].roles[0]").value(hasItem("Manager")));
    }

    @Test
    void suspendCutsAccessAtOnceAndReactivateRestoresIt() throws Exception {
        Person org = register();
        Person staff = join(org, "staff", ALL);

        act(org, staff.membershipId(), "suspend").andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", staff.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(staff.email(), PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NO_ACTIVE_BUSINESS"));
        act(org, staff.membershipId(), "suspend").andExpect(status().isConflict());

        act(org, staff.membershipId(), "reactivate").andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(staff.email(), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void removeEndsAccessFreesTheSeatAndAllowsReinvite() throws Exception {
        Person org = register();
        raiseLimit(org, "max_employees", 2);
        Person staff = join(org, "staff", ALL);

        mvc.perform(post("/api/v1/employees/invitations").header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"%s\",\"roleIds\":[\"%s\"],%s}"
                                .formatted(email(), roleId(org, "staff"), ALL)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMPLOYEE_LIMIT_REACHED"));

        act(org, staff.membershipId(), "remove").andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", staff.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/employees").header("Authorization", org.bearer()))
                .andExpect(jsonPath("$[*].email", not(hasItem(staff.email()))));

        mvc.perform(post("/api/v1/employees/invitations").header("Authorization", org.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Dev\",\"lastName\":\"Nair\",\"email\":\"%s\",\"roleIds\":[\"%s\"],%s}"
                                .formatted(staff.email(), roleId(org, "staff"), ALL)))
                .andExpect(status().isCreated());
    }

    @Test
    void guardrailsStopSelfEditsAndEditingMorePowerfulPeople() throws Exception {
        Person org = register();
        Person manager = join(org, "manager", ALL);
        Person staff = join(org, "staff", ALL);
        String staffRole = roleId(org, "staff");

        edit(org, org.membershipId(), staffRole, ALL).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
        act(org, org.membershipId(), "suspend").andExpect(status().isForbidden());
        act(org, org.membershipId(), "remove").andExpect(status().isForbidden());

        edit(manager, org.membershipId(), staffRole, ALL).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TARGET_EXCEEDS_YOUR_ACCESS"));
        act(manager, staff.membershipId(), "remove").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        act(manager, staff.membershipId(), "suspend").andExpect(status().isNoContent());

        UUID secondOwner = UUID.randomUUID();
        UUID secondMembership = UUID.randomUUID();
        owner.update("INSERT INTO users (id, email, password_hash, full_name, status) VALUES (?, ?, ?, 'Second Owner', 'active')",
                secondOwner, email(), encoder.encode(PASSWORD));
        owner.update("INSERT INTO business_memberships (id, business_id, user_id, status, all_branches) VALUES (?, ?::uuid, ?, 'active', true)",
                secondMembership, org.businessId(), secondOwner);
        owner.update("INSERT INTO membership_roles (membership_id, role_id, business_id)"
                + " SELECT ?, id, ?::uuid FROM roles WHERE business_id IS NULL AND code = 'owner'", secondMembership, org.businessId());
        edit(org, secondMembership.toString(), staffRole, ALL).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OWNER_ROLE_IMMUTABLE"));
    }

    @Test
    void branchRestrictedManagerOnlyManagesPeopleInTheirOwnBranches() throws Exception {
        Person org = register();
        raiseLimit(org, "max_branches", 3);
        String goa = createBranch(org, "GOA");
        Person manager = join(org, "manager", branchesJson(org.branchId()));
        Person hqStaff = join(org, "staff", branchesJson(org.branchId()));
        Person goaStaff = join(org, "staff", branchesJson(goa));
        Person everywhere = join(org, "staff", ALL);
        String staffRole = roleId(org, "staff");

        edit(manager, hqStaff.membershipId(), staffRole, branchesJson(org.branchId())).andExpect(status().isNoContent());
        edit(manager, goaStaff.membershipId(), staffRole, branchesJson(org.branchId())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BRANCH_SCOPE_EXCEEDED"));
        act(manager, everywhere.membershipId(), "suspend").andExpect(status().isForbidden());
        edit(manager, hqStaff.membershipId(), staffRole, ALL).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/employees").header("Authorization", manager.bearer()))
                .andExpect(jsonPath("$[?(@.email=='" + hqStaff.email() + "')].manageable").value(true))
                .andExpect(jsonPath("$[?(@.email=='" + goaStaff.email() + "')].manageable").value(false))
                .andExpect(jsonPath("$[?(@.email=='" + org.email() + "')].manageable").value(false))
                .andExpect(jsonPath("$[?(@.email=='" + manager.email() + "')].you").value(true));
    }

    @Test
    void anotherBusinessCannotTouchMyTeam() throws Exception {
        Person a = register();
        Person staff = join(a, "staff", ALL);
        Person b = register();

        edit(b, staff.membershipId(), roleId(b, "staff"), ALL).andExpect(status().isNotFound());
        act(b, staff.membershipId(), "suspend").andExpect(status().isNotFound());
        act(b, staff.membershipId(), "remove").andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me").header("Authorization", staff.bearer())).andExpect(status().isOk());
    }
}
