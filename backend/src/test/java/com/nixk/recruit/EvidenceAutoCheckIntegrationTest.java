package com.nixk.recruit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nixk.recruit.service.HttpGateway;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Evidence auto-check end to end through the API: a candidate adds links, the stubbed HTTP
 * layer answers as GitHub/LinkedIn would, and the admin toggle decides whether those answers
 * become statuses or just annotations. No real network access happens here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EvidenceAutoCheckIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Canned upstream answers: GitHub exists, LinkedIn is live, everything else unreachable. */
    @TestConfiguration
    static class StubHttpConfig {
        @Bean
        @Primary
        HttpGateway stubHttp() {
            return new HttpGateway() {
                @Override
                public Response get(String url) {
                    if (url.equals("https://api.github.com/users/integration-dev")) {
                        return new Response(200, "{\"login\":\"integration-dev\",\"public_repos\":4,\"followers\":9}");
                    }
                    if (url.equals("https://www.linkedin.com/in/integration-dev")) {
                        return new Response(200, "<html>profile</html>");
                    }
                    return new Response(-1, null);
                }

                @Override
                public Response postJson(String url, String jsonBody) {
                    return new Response(-1, null);
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    private static String adminToken;
    private static String candidateToken;

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

    private String addEvidence(String token, String type, String title, String url) throws Exception {
        MvcResult r = mvc.perform(post("/api/candidate/evidence").header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"url\":\"" + url + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getContentAsString();
    }

    /* ---------------- the flow ---------------- */

    @Test
    @Order(1)
    void toggleDefaultsToOnAndAdminCanTurnItOff() throws Exception {
        adminToken = login("admin@test.local", "Admin@12345");

        mvc.perform(get("/api/admin/evidence/auto-verify").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(put("/api/admin/evidence/auto-verify").header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @Order(2)
    void candidateRegistersAndEvidenceWaitsWhileToggleIsOff() throws Exception {
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Integration Candidate\",\"email\":\"evidence-candidate@test.local\","
                                + "\"password\":\"Evidence@123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        candidateToken = JSON.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        // toggle is off: the GitHub answer is conclusive but only annotates the metadata
        JsonNode e = JSON.readTree(addEvidence(candidateToken, "GITHUB", "My GitHub",
                "https://github.com/integration-dev"));
        assertEquals("PENDING", e.get("status").asText());
        assertTrue(e.get("metadata").asText().contains("auto-verify is off"), e.get("metadata").asText());
        assertTrue(e.get("metadata").asText().contains("public repos: 4"), e.get("metadata").asText());
    }

    @Test
    @Order(3)
    void turningToggleOnAppliesChecksDirectlyAndNotifiesTheCandidate() throws Exception {
        mvc.perform(put("/api/admin/evidence/auto-verify").header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        JsonNode gh = JSON.readTree(addEvidence(candidateToken, "GITHUB", "GitHub again",
                "https://github.com/integration-dev"));
        assertEquals("VERIFIED", gh.get("status").asText());
        assertEquals("AUTO:GITHUB_API", gh.get("verifiedBy").asText());

        // liveness-verified link gets the weaker URL_OK marker
        JsonNode li = JSON.readTree(addEvidence(candidateToken, "LINKEDIN", "LinkedIn",
                "https://www.linkedin.com/in/integration-dev"));
        assertEquals("VERIFIED", li.get("status").asText());
        assertEquals("AUTO:URL_OK", li.get("verifiedBy").asText());

        // unreachable link never rejects — it waits
        JsonNode gh2 = JSON.readTree(addEvidence(candidateToken, "GITHUB", "Unreachable",
                "https://github.com/other-dev"));
        assertEquals("PENDING", gh2.get("status").asText());
        assertTrue(gh2.get("metadata").asText().contains("unreachable"), gh2.get("metadata").asText());

        // the candidate was notified about the automatic status change
        MvcResult notifs = mvc.perform(get("/api/notifications").header("Authorization", auth(candidateToken)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = JSON.readTree(notifs.getResponse().getContentAsString()).get("items");
        boolean notified = false;
        for (JsonNode n : items) {
            if ("EVIDENCE".equals(n.get("type").asText()) && n.get("message").asText().contains("automatically")) {
                notified = true;
                break;
            }
        }
        assertTrue(notified, "expected an automatic EVIDENCE notification");

        // score: 10 (order-2 pending GitHub) + 35 (GitHub API) + 25 (LinkedIn URL_OK) + 10 (pending GitHub) = 80
        MvcResult ev = mvc.perform(get("/api/candidate/evidence").header("Authorization", auth(candidateToken)))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(80, JSON.readTree(ev.getResponse().getContentAsString()).get("evidenceScore").asInt());
    }

    @Test
    @Order(4)
    void adminStillReviewsManuallyRegardlessOfToggle() throws Exception {
        mvc.perform(get("/api/admin/evidence?status=PENDING").header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evidence.status").value("PENDING"));
    }
}
