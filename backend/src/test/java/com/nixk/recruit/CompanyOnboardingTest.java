package com.nixk.recruit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The full company workflow end to end: a registration that waits for verification,
 * the admin approving it, invitations that grant exactly the chosen permissions,
 * permission gated job posting and the auto-verification flush.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompanyOnboardingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private static String adminToken;
    private static String ownerToken;
    private static String secondOwnerToken;
    private static Long companyId;
    private static String limitedInvite;
    private static String fullInvite;

    /* ---------------- helpers ---------------- */

    private static String auth(String token) {
        return "Bearer " + token;
    }

    private String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult r = mvc.perform(get(path).header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    private String registerCompany(String ownerName, String email, String password,
                                   String name, String domain) throws Exception {
        MockMultipartFile doc = new MockMultipartFile("documents", "incorporation.txt", "text/plain",
                "Incorporation certificate for the integration test".getBytes(StandardCharsets.UTF_8));
        MvcResult r = mvc.perform(multipart("/api/companies/register")
                        .file(doc)
                        .param("ownerName", ownerName)
                        .param("email", email)
                        .param("password", password)
                        .param("name", name)
                        .param("emailDomain", domain)
                        .param("website", "https://" + domain)
                        .param("industry", "QA")
                        .param("location", "Remote")
                        .param("description", "Company registered by the onboarding test"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private String acceptInvite(String token, String name, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/invitations/" + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private static String jobJson(String title) {
        return "{\"title\":\"" + title + "\",\"description\":\"Role created by the onboarding test\","
                + "\"requiredSkills\":\"Java\",\"location\":\"Remote\"}";
    }

    private String postJob(String token, String title) throws Exception {
        MvcResult r = mvc.perform(post("/api/jobs").header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(title)))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getContentAsString();
    }

    /* ---------------- the flow ---------------- */

    @Test
    @Order(1)
    void adminTurnsAutoVerificationOff() throws Exception {
        adminToken = login("admin@test.local", "Admin@12345");

        mvc.perform(get("/api/admin/companies/auto-verify").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(put("/api/admin/companies/auto-verify").header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @Order(2)
    void companyRegistrationWaitsForVerification() throws Exception {
        ownerToken = registerCompany("Olive Chen", "olive@northwind.test", "Northwind@123",
                "Northwind Labs", "northwind.test");

        JsonNode mine = getJson("/api/companies/mine", ownerToken);
        companyId = mine.get("company").get("id").asLong();
        assertEquals("PENDING_VERIFICATION", mine.get("company").get("status").asText());
        assertEquals("northwind.test", mine.get("company").get("emailDomain").asText());
        assertEquals(1, mine.get("members").size());
        assertEquals(1, mine.get("documents").size());
        assertEquals("incorporation.txt", mine.get("documents").get(0).get("fileName").asText());

        // even a company admin cannot recruit before verification
        mvc.perform(post("/api/jobs").header("Authorization", auth(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson("Blocked Role")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void adminVerifiesAndOwnerCanRecruit() throws Exception {
        mvc.perform(patch("/api/admin/companies/" + companyId + "/status")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.verifiedBy", containsString("admin@test.local")));

        JsonNode job = JSON.readTree(postJob(ownerToken, "QA Automation Engineer"));
        assertEquals("Northwind Labs", job.get("company").asText());
        assertEquals(companyId.longValue(), job.get("companyId").asLong());

        JsonNode after = getJson("/api/companies/mine", ownerToken);
        assertEquals("ACTIVE", after.get("company").get("status").asText());
        assertEquals(1, after.get("jobCount").asInt());
    }

    @Test
    @Order(4)
    void invitationsGrantExactlyTheChosenPermissions() throws Exception {
        // a recruiter who may only look at applicants
        MvcResult inv = mvc.perform(post("/api/companies/mine/invitations")
                        .header("Authorization", auth(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kai@northwind.test\",\"role\":\"RECRUITER\","
                                + "\"permissions\":[\"VIEW_APPLICANTS\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RECRUITER"))
                .andReturn();
        limitedInvite = JSON.readTree(inv.getResponse().getContentAsString()).get("token").asText();

        // the link works before it is accepted, without authentication
        mvc.perform(get("/api/invitations/" + limitedInvite))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.companyName").value("Northwind Labs"));

        String limited = acceptInvite(limitedInvite, "Kai Limited", "Limited@123");

        JsonNode mine = getJson("/api/companies/mine", limited);
        assertEquals(1, mine.get("myPermissions").size());
        assertEquals("VIEW_APPLICANTS", mine.get("myPermissions").get(0).asText());

        // ...but cannot post a job
        mvc.perform(post("/api/jobs").header("Authorization", auth(limited))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson("Should Be Blocked")))
                .andExpect(status().isForbidden());

        // a second recruiter with posting rights can create jobs
        MvcResult inv2 = mvc.perform(post("/api/companies/mine/invitations")
                        .header("Authorization", auth(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ria@northwind.test\",\"role\":\"RECRUITER\","
                                + "\"permissions\":[\"POST_JOB\",\"VIEW_APPLICANTS\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        fullInvite = JSON.readTree(inv2.getResponse().getContentAsString()).get("token").asText();
        String full = acceptInvite(fullInvite, "Ria Full", "Fullstack@123");
        JsonNode posted = JSON.readTree(postJob(full, "Backend Engineer"));
        assertEquals("Northwind Labs", posted.get("company").asText());

        // the owner sees both invitations
        JsonNode invites = getJson("/api/companies/mine/invitations", ownerToken);
        assertEquals(2, invites.size());
        assertEquals("ACCEPTED", invites.get(0).get("status").asText());
    }

    @Test
    @Order(5)
    void autoVerifyToggleVerifiesEverythingWaiting() throws Exception {
        secondOwnerToken = registerCompany("Ben Ortiz", "ben@pendulum.test", "Pendulum@123",
                "Pendulus Systems", "pendulum.test");
        JsonNode pending = getJson("/api/companies/mine", secondOwnerToken);
        assertEquals("PENDING_VERIFICATION", pending.get("company").get("status").asText());

        mvc.perform(put("/api/admin/companies/auto-verify").header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.verified", greaterThanOrEqualTo(1)));

        JsonNode flushed = getJson("/api/companies/mine", secondOwnerToken);
        assertEquals("ACTIVE", flushed.get("company").get("status").asText());
    }
}
