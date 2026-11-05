package com.neonicz.miseva;

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

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {

    static final String DOMAIN = "@authtest.local";
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
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        TestCleanup.removeEverythingFor(owner, DOMAIN);
    }

    private String registerJson(String email, String businessName) {
        return """
                {"businessName":"%s","industry":"salon","countryCode":"IN","currency":"INR","timezone":"Asia/Kolkata",
                 "ownerFirstName":"Asha","ownerLastName":"Rao","email":"%s","password":"%s"}
                """.formatted(businessName, email, PASSWORD);
    }

    private String register(String email, String businessName) throws Exception {
        return mvc.perform(post("/api/v1/businesses/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, businessName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN;
    }

    private String uniqueName() {
        return "Authtest Salon " + UUID.randomUUID().toString().substring(0, 8);
    }

    private String login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void registrationCreatesBusinessOwnerAndHeadOffice() throws Exception {
        String body = register(uniqueEmail(), uniqueName());
        String access = JsonPath.read(body, "$.tokens.accessToken");
        String businessId = JsonPath.read(body, "$.businessId");

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("rbac.role.manage")));

        mvc.perform(get("/api/v1/branches").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].code").value("HQ"))
                .andExpect(jsonPath("$.items[0].headOffice").value(true));
    }

    @Test
    void businessCannotSeeAnotherBusinessesBranches() throws Exception {
        String a = register(uniqueEmail(), uniqueName());
        String b = register(uniqueEmail(), uniqueName());
        String branchA = JsonPath.read(a, "$.headOfficeBranchId");
        String accessB = JsonPath.read(b, "$.tokens.accessToken");

        String listB = mvc.perform(get("/api/v1/branches").header("Authorization", "Bearer " + accessB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(listB).doesNotContain(branchA);
    }

    @Test
    void registrationRejectsDuplicateEmailAndBadInput() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueName());
        mvc.perform(post("/api/v1/businesses/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, uniqueName())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(post("/api/v1/businesses/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("not-an-email", uniqueName())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void protectedEndpointsRejectMissingAndTamperedTokens() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        String body = register(uniqueEmail(), uniqueName());
        String access = JsonPath.read(body, "$.tokens.accessToken");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSucceedsAndFailsWithoutRevealingWhichPartWasWrong() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueName());

        String ok = login(email, PASSWORD);
        String access = JsonPath.read(ok, "$.accessToken");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrong-password-1\"}".formatted(email)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody%s\",\"password\":\"wrong-password-1\"}".formatted(DOMAIN)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        String email = uniqueEmail();
        register(email, uniqueName());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"wrong-password-1\"}".formatted(email)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void refreshRotatesAndReuseRevokesTheWholeFamily() throws Exception {
        String body = register(uniqueEmail(), uniqueName());
        String refresh1 = JsonPath.read(body, "$.tokens.refreshToken");

        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh1)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh2 = JsonPath.read(rotated, "$.refreshToken");
        String access2 = JsonPath.read(rotated, "$.accessToken");
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access2)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh2)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access2)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        String body = register(uniqueEmail(), uniqueName());
        String access = JsonPath.read(body, "$.tokens.accessToken");
        String refresh = JsonPath.read(body, "$.tokens.refreshToken");

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }
}
