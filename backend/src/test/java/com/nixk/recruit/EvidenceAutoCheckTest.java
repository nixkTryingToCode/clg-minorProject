package com.nixk.recruit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nixk.recruit.model.Evidence;
import com.nixk.recruit.model.Setting;
import com.nixk.recruit.repo.EvidenceRepo;
import com.nixk.recruit.repo.SettingRepo;
import com.nixk.recruit.service.ApiException;
import com.nixk.recruit.service.AuditService;
import com.nixk.recruit.service.EvidenceService;
import com.nixk.recruit.service.HttpGateway;
import com.nixk.recruit.service.NotificationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The evidence verifier chain with a stubbed HTTP layer: what each evidence type does with a
 * 200 / 404 / bot-block / unreachable response, and how the admin auto-verify toggle changes
 * whether a conclusive check sets the status or only annotates metadata.
 */
class EvidenceAutoCheckTest {

    /** URL-keyed canned responses; anything not stubbed comes back unreachable (-1). */
    private static class StubHttp implements HttpGateway {
        final Map<String, Response> gets = new HashMap<>();
        final Map<String, Response> posts = new HashMap<>();

        void get(String url, int status, String body) {
            gets.put(url, new Response(status, body));
        }

        void post(String url, int status, String body) {
            posts.put(url, new Response(status, body));
        }

        @Override
        public Response get(String url) {
            return gets.getOrDefault(url, new Response(-1, null));
        }

        @Override
        public Response postJson(String url, String jsonBody) {
            return posts.getOrDefault(url, new Response(-1, null));
        }
    }

    private final EvidenceRepo repo = mock(EvidenceRepo.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final SettingRepo settings = mock(SettingRepo.class);
    private final StubHttp http = new StubHttp();
    private final EvidenceService service = new EvidenceService(repo, notifications, audit, settings, http);
    /** Simulated settings table so the toggle test can read back what it wrote. */
    private final Map<String, String> storedSettings = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(repo.save(any(Evidence.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settings.findById(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return Optional.ofNullable(storedSettings.get(key)).map(v -> new Setting(key, v));
        });
        when(settings.save(any(Setting.class))).thenAnswer(inv -> {
            Setting s = inv.getArgument(0);
            storedSettings.put(s.key, s.value);
            return s;
        });
        // default: toggle not configured -> enabled
    }

    private void autoVerifyOff() {
        storedSettings.put(EvidenceService.AUTO_VERIFY_KEY, "false");
    }

    /* ---------------- GitHub (public API) ---------------- */

    @Test
    void githubProfileExistsIsVerifiedThroughTheApi() {
        http.get("https://api.github.com/users/octocat", 200,
                "{\"login\":\"octocat\",\"public_repos\":7,\"followers\":3}");

        Evidence e = service.add(1L, "GITHUB", "My GitHub", "https://github.com/octocat", "Git");

        assertEquals("VERIFIED", e.status);
        assertEquals("AUTO:GITHUB_API", e.verifiedBy);
        assertTrue(e.metadata.contains("public repos: 7"), e.metadata);
        assertNotNull(e.checkedAt);
        verify(notifications).notify(eq(1L), eq("EVIDENCE"), contains("verified automatically"), any(), isNull());
    }

    @Test
    void githubMissingAccountIsRejected() {
        http.get("https://api.github.com/users/ghost404", 404, "{\"message\":\"Not Found\"}");

        Evidence e = service.add(1L, "GITHUB", "Ghost", "https://github.com/ghost404", null);

        assertEquals("REJECTED", e.status);
        assertEquals("AUTO:GITHUB_API", e.verifiedBy);
        assertTrue(e.metadata.contains("No GitHub account named 'ghost404'"), e.metadata);
        verify(notifications).notify(eq(1L), eq("EVIDENCE"), contains("rejected automatically"), any(), isNull());
    }

    @Test
    void githubApiOutageStaysPendingAndSaysWhy() {
        // no stub -> unreachable (-1)

        Evidence e = service.add(1L, "GITHUB", "My GitHub", "https://github.com/octocat", null);

        assertEquals("PENDING", e.status);
        assertNull(e.verifiedBy);
        assertTrue(e.metadata.contains("GitHub API unreachable"), e.metadata);
        verify(notifications, never()).notify(anyLong(), anyString(), anyString(), any(), any());
    }

    /* ---------------- LeetCode (public GraphQL) ---------------- */

    @Test
    void leetcodeProfileExistsIsVerified() {
        http.post("https://leetcode.com/graphql", 200, "{\"data\":{\"matchedUser\":{\"username\":\"coder\"}}}");

        Evidence e = service.add(1L, "LEETCODE", "LC", "https://leetcode.com/coder/", "Algorithms");

        assertEquals("VERIFIED", e.status);
        assertEquals("AUTO:LEETCODE_API", e.verifiedBy);
        assertTrue(e.metadata.contains("LeetCode profile 'coder'"), e.metadata);
    }

    @Test
    void leetcodeMissingProfileIsRejected() {
        http.post("https://leetcode.com/graphql", 200, "{\"data\":{\"matchedUser\":null}}");

        Evidence e = service.add(1L, "LEETCODE", "LC", "https://leetcode.com/no-such-user", null);

        assertEquals("REJECTED", e.status);
        assertTrue(e.metadata.contains("No LeetCode profile named 'no-such-user'"), e.metadata);
    }

    @Test
    void leetcodeRateLimitStaysPending() {
        http.post("https://leetcode.com/graphql", 429, "slow down");

        Evidence e = service.add(1L, "LEETCODE", "LC", "https://leetcode.com/coder/", null);

        assertEquals("PENDING", e.status);
        assertTrue(e.metadata.contains("rate-limited"), e.metadata);
    }

    /* ---------------- profile liveness (LinkedIn / Kaggle / HackerRank) ---------------- */

    @Test
    void linkedinLiveProfileIsVerifiedByUrlLiveness() {
        http.get("https://www.linkedin.com/in/jane-doe", 200, "<html>profile</html>");

        Evidence e = service.add(1L, "LINKEDIN", "LinkedIn", "https://www.linkedin.com/in/jane-doe", null);

        assertEquals("VERIFIED", e.status);
        assertEquals("AUTO:URL_OK", e.verifiedBy);
        assertTrue(e.metadata.contains("HTTP 200 on www.linkedin.com"), e.metadata);
    }

    @Test
    void linkedinBotBlockNeverRejects() {
        http.get("https://www.linkedin.com/in/jane-doe", 403, "blocked");

        Evidence e = service.add(1L, "LINKEDIN", "LinkedIn", "https://www.linkedin.com/in/jane-doe", null);

        assertEquals("PENDING", e.status);
        assertTrue(e.metadata.contains("bot-block"), e.metadata);
    }

    @Test
    void linkedinMissingProfileIsRejected() {
        http.get("https://www.linkedin.com/in/no-such-person", 404, "not found");

        Evidence e = service.add(1L, "LINKEDIN", "LinkedIn", "https://www.linkedin.com/in/no-such-person", null);

        assertEquals("REJECTED", e.status);
        assertEquals("AUTO:URL_OK", e.verifiedBy);
        assertTrue(e.metadata.contains("404"), e.metadata);
    }

    /* ---------------- claims a URL can't prove ---------------- */

    @Test
    void portfolioNeverAutoVerifiesEvenWhenTheUrlIsLive() {
        http.get("https://example.com/portfolio", 200, "<html>portfolio</html>");

        Evidence e = service.add(1L, "PORTFOLIO", "Portfolio", "https://example.com/portfolio", "Design");

        assertEquals("PENDING", e.status);
        assertTrue(e.metadata.contains("cannot be proven by a link"), e.metadata);
    }

    @Test
    void certificateOnTheWrongDomainIsRejectedBeforeAnyCheck() {
        assertThrows(ApiException.class,
                () -> service.add(1L, "CERTIFICATE", "Cert", "http://insecure.example.com/cert", null));
        assertThrows(ApiException.class,
                () -> service.add(1L, "GITHUB", "Wrong host", "https://gitlab.com/someone", null));
    }

    /* ---------------- auto-verify toggle ---------------- */

    @Test
    void toggleOffMeansChecksOnlyAnnotateAndEverythingWaits() {
        autoVerifyOff();
        http.get("https://api.github.com/users/octocat", 200,
                "{\"login\":\"octocat\",\"public_repos\":7,\"followers\":3}");

        Evidence e = service.add(1L, "GITHUB", "My GitHub", "https://github.com/octocat", null);

        assertEquals("PENDING", e.status);
        assertNull(e.verifiedBy);
        assertTrue(e.metadata.contains("auto-verify is off"), e.metadata);
        assertTrue(e.metadata.contains("public repos: 7"), e.metadata);
        verify(notifications, never()).notify(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void togglePersistsAndReadsBack() {
        assertTrue(service.autoVerifyEnabled());
        assertFalse(service.setAutoVerify(false));
        assertFalse(service.autoVerifyEnabled());
        verify(settings).save(argThat(s -> EvidenceService.AUTO_VERIFY_KEY.equals(s.key) && "false".equals(s.value)));
        assertTrue(service.setAutoVerify(true));
        assertTrue(service.autoVerifyEnabled());
    }

    /* ---------------- scoring ---------------- */

    @Test
    void evidenceScoreWeightsApiProofAboveUrlLiveness() {
        Evidence apiVerified = new Evidence();
        apiVerified.status = "VERIFIED";
        apiVerified.verifiedBy = "AUTO:GITHUB_API";
        Evidence urlVerified = new Evidence();
        urlVerified.status = "VERIFIED";
        urlVerified.verifiedBy = "AUTO:URL_OK";
        Evidence pending = new Evidence();
        pending.status = "PENDING";
        Evidence rejected = new Evidence();
        rejected.status = "REJECTED";
        when(repo.findByCandidateIdOrderByCreatedAtDesc(9L))
                .thenReturn(List.of(apiVerified, urlVerified, pending, rejected));

        // 35 (API proof) + 25 (URL liveness) + 10 (pending) + 0 (rejected)
        assertEquals(70, service.evidenceScore(9L));
    }

    @Test
    void evidenceScoreIsCappedAt100() {
        Evidence v = new Evidence();
        v.status = "VERIFIED";
        v.verifiedBy = "ADMIN";
        when(repo.findByCandidateIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(v, v, v, v));

        assertEquals(100, service.evidenceScore(9L));
    }
}
