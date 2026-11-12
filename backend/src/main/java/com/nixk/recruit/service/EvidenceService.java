package com.nixk.recruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nixk.recruit.model.Evidence;
import com.nixk.recruit.model.Setting;
import com.nixk.recruit.repo.EvidenceRepo;
import com.nixk.recruit.repo.SettingRepo;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Candidate evidence (profile links, certificates) with tiered automatic checks:
 *
 * <ul>
 *   <li>GITHUB — public GitHub API: account exists / does not exist (definitive).</li>
 *   <li>LEETCODE — public GraphQL profile query: exists / does not exist (definitive).</li>
 *   <li>LINKEDIN, KAGGLE, HACKERRANK — domain liveness: HTTP 200 on the right host means the
 *       profile page exists (AUTO:URL_OK); bot-blocks (403/429/999), redirects and timeouts stay
 *       PENDING — a check never falsely rejects.</li>
 *   <li>CERTIFICATE, PORTFOLIO, OTHER — liveness is only annotated in metadata; the claim itself
 *       cannot be proven by a URL, so these always wait for manual review.</li>
 * </ul>
 *
 * The admin toggle {@code evidence.auto_verify} decides whether conclusive checks set the status
 * directly (On) or only annotate metadata while everything waits for review (Off).
 */
@Service
public class EvidenceService {
    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);
    public static final String AUTO_VERIFY_KEY = "evidence.auto_verify";
    private static final Pattern GH_USER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");
    private static final Pattern LC_USER = Pattern.compile("[A-Za-z0-9_.-]{1,60}");
    public static final Map<String, String> DOMAINS = Map.of(
            "GITHUB", "github.com", "LEETCODE", "leetcode.com", "HACKERRANK", "hackerrank.com",
            "KAGGLE", "kaggle.com", "LINKEDIN", "linkedin.com");
    public static final Set<String> TYPES = Set.of("GITHUB", "LEETCODE", "HACKERRANK", "KAGGLE", "LINKEDIN", "CERTIFICATE", "PORTFOLIO", "OTHER");

    private final EvidenceRepo repo;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SettingRepo settings;
    private final HttpGateway http;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Outcome of one automatic check: a conclusive status, or null to stay PENDING. */
    record Check(String status, String verifiedBy, String note) {
        static Check pending(String note) {
            return new Check(null, null, note);
        }
    }

    public EvidenceService(EvidenceRepo repo, NotificationService notifications, AuditService audit,
                           SettingRepo settings, HttpGateway http) {
        this.repo = repo;
        this.notifications = notifications;
        this.audit = audit;
        this.settings = settings;
        this.http = http;
    }

    public Evidence add(Long candidateId, String type, String title, String url, String skill) {
        String t = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(t)) throw new ApiException(400, "Unknown evidence type");
        if (title == null || title.isBlank()) throw new ApiException(400, "Please give this evidence a title");
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "That is not a valid URL");
        }
        if (uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())) throw new ApiException(400, "Please use a full https:// link");
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String required = DOMAINS.get(t);
        if (required != null && !(host.equals(required) || host.endsWith("." + required))) {
            throw new ApiException(400, "A " + t + " link must point to " + required);
        }
        Evidence e = new Evidence();
        e.candidateId = candidateId;
        e.type = t;
        e.title = title.trim();
        e.url = url.trim();
        e.skill = skill == null || skill.isBlank() ? null : skill.trim();
        e.status = "PENDING";
        applyCheck(e, runCheck(t, uri));
        e = repo.save(e);
        audit.log(candidateId, "EVIDENCE_ADDED", "Evidence", e.id, t + " -> " + e.status);
        if (!"PENDING".equals(e.status)) {
            notifications.notify(candidateId, "EVIDENCE",
                    "Your evidence \"" + e.title + "\" was marked " + e.status.toLowerCase(Locale.ROOT)
                            + " automatically (" + e.verifiedBy + ").", e.id, null);
        }
        return e;
    }

    /**
     * Runs the per-type verifier and applies it: when auto-verify is On a conclusive result
     * becomes the status directly; when Off (or the check is inconclusive) only the metadata
     * is annotated and the evidence waits for review.
     */
    private void applyCheck(Evidence e, Check c) {
        e.checkedAt = Instant.now();
        boolean conclusive = c.status() != null;
        if (conclusive && autoVerifyEnabled()) {
            e.status = c.status();
            e.verifiedBy = c.verifiedBy();
            e.metadata = clip(c.note());
        } else {
            String prefix = conclusive ? "Auto-check (not applied — auto-verify is off): " : "Auto-check: ";
            e.metadata = clip(prefix + c.note());
        }
    }

    /** Verifier chain: one strategy per evidence type; never throws. */
    private Check runCheck(String type, URI uri) {
        try {
            return switch (type) {
                case "GITHUB" -> checkGithub(uri);
                case "LEETCODE" -> checkLeetcode(uri);
                case "LINKEDIN", "KAGGLE", "HACKERRANK" -> checkLiveness(uri, true);
                default -> checkLiveness(uri, false); // CERTIFICATE / PORTFOLIO / OTHER
            };
        } catch (Exception ex) {
            log.warn("Evidence check failed for {}: {}", uri, ex.toString());
            return Check.pending("check failed (" + ex.getClass().getSimpleName() + ") — needs manual review");
        }
    }

    /** GitHub: GET api.github.com/users/{name} — 200 exists, 404 does not, anything else inconclusive. */
    private Check checkGithub(URI uri) {
        String user = firstSegment(uri);
        if (user.isEmpty() || !GH_USER.matcher(user).matches()) {
            return Check.pending("could not read a GitHub username from the URL");
        }
        HttpGateway.Response res = http.get("https://api.github.com/users/" + user);
        if (res.unreachable()) return Check.pending("GitHub API unreachable — needs manual review");
        if (res.status() == 200) {
            try {
                JsonNode n = mapper.readTree(res.body());
                return new Check("VERIFIED", "AUTO:GITHUB_API",
                        "GitHub account '" + n.path("login").asText(user) + "' exists; public repos: "
                                + n.path("public_repos").asInt() + "; followers: " + n.path("followers").asInt());
            } catch (Exception ex) {
                return Check.pending("GitHub API returned an unreadable response — needs manual review");
            }
        }
        if (res.status() == 404) {
            return new Check("REJECTED", "AUTO:GITHUB_API", "No GitHub account named '" + user + "' was found");
        }
        return Check.pending("GitHub API returned HTTP " + res.status() + " — needs manual review");
    }

    /** LeetCode: POST the public GraphQL profile query — matchedUser present means the profile exists. */
    private Check checkLeetcode(URI uri) {
        String user = firstSegment(uri);
        if (user.isEmpty() || !LC_USER.matcher(user).matches()) {
            return Check.pending("could not read a LeetCode username from the URL");
        }
        String body;
        try {
            body = mapper.writeValueAsString(Map.of(
                    "query", "query userProfile($username: String!) { matchedUser(username: $username) { username } }",
                    "variables", Map.of("username", user)));
        } catch (Exception ex) {
            return Check.pending("could not build the LeetCode query");
        }
        HttpGateway.Response res = http.postJson("https://leetcode.com/graphql", body);
        if (res.unreachable()) return Check.pending("LeetCode unreachable — needs manual review");
        if (res.status() == 200) {
            try {
                JsonNode n = mapper.readTree(res.body());
                JsonNode matched = n.path("data").path("matchedUser");
                if (matched.isMissingNode() || matched.isNull()) {
                    return new Check("REJECTED", "AUTO:LEETCODE_API", "No LeetCode profile named '" + user + "' was found");
                }
                return new Check("VERIFIED", "AUTO:LEETCODE_API",
                        "LeetCode profile '" + matched.path("username").asText(user) + "' exists");
            } catch (Exception ex) {
                return Check.pending("LeetCode returned an unreadable response — needs manual review");
            }
        }
        if (res.status() == 403 || res.status() == 429) {
            return Check.pending("LeetCode rate-limited the check — needs manual review");
        }
        return Check.pending("LeetCode returned HTTP " + res.status() + " — needs manual review");
    }

    /**
     * Domain liveness for profile links (and annotation-only for certificates/portfolios).
     * 200 on the expected host means the page exists; bot-blocks, redirects and timeouts are
     * inconclusive on purpose so a check can never falsely reject. Only a definitive 404/410
     * on the profile URL rejects.
     */
    private Check checkLiveness(URI uri, boolean profileType) {
        HttpGateway.Response res = http.get(uri.toString());
        if (res.unreachable()) return Check.pending("URL unreachable (timeout/DNS) — needs manual review");
        if (res.status() == 200) {
            String note = "URL responded HTTP 200 on " + uri.getHost();
            if (profileType) return new Check("VERIFIED", "AUTO:URL_OK", note);
            return Check.pending(note + "; the claim itself cannot be proven by a link — needs manual review");
        }
        if (res.status() == 404 || res.status() == 410) {
            if (profileType) return new Check("REJECTED", "AUTO:URL_OK", "URL returned HTTP " + res.status() + " — page does not exist");
            return Check.pending("URL returned HTTP " + res.status() + " — needs manual review");
        }
        if (res.status() == 301 || res.status() == 302 || res.status() == 303 || res.status() == 307 || res.status() == 308) {
            return Check.pending("URL redirects (login wall or moved) — needs manual review");
        }
        return Check.pending("URL returned HTTP " + res.status() + " (bot-block or server error) — needs manual review");
    }

    public Evidence review(Evidence e, String status, Long adminId, String note) {
        if (!List.of("VERIFIED", "REJECTED", "PENDING").contains(status)) throw new ApiException(400, "Status must be VERIFIED, REJECTED or PENDING");
        e.status = status;
        e.verifiedBy = "ADMIN";
        e.checkedAt = Instant.now();
        if (note != null && !note.isBlank()) e.metadata = note.length() > 900 ? note.substring(0, 900) : note;
        e = repo.save(e);
        audit.log(adminId, "EVIDENCE_" + status, "Evidence", e.id, e.type + " " + e.title);
        notifications.notify(e.candidateId, "EVIDENCE", "Your evidence \"" + e.title + "\" was marked " + status.toLowerCase(Locale.ROOT) + ".", e.id, null);
        return e;
    }

    /* ---------------- auto-verify toggle ---------------- */

    public boolean autoVerifyEnabled() {
        return settings.findById(AUTO_VERIFY_KEY).map(s -> !"false".equalsIgnoreCase(s.value)).orElse(true);
    }

    /** Turns automatic status changes on or off; conclusive checks always run and annotate metadata. */
    public boolean setAutoVerify(boolean enabled) {
        settings.save(new Setting(AUTO_VERIFY_KEY, String.valueOf(enabled)));
        return enabled;
    }

    /* ---------------- scoring ---------------- */

    /**
     * 0-100: how much of the profile is backed by evidence. API-proven verification counts
     * most (35); URL-liveness verification counts a little less (25) because a reachable page
     * is weaker proof; pending evidence still counts a little (10).
     */
    public int evidenceScore(Long candidateId) {
        int total = 0;
        for (Evidence e : repo.findByCandidateIdOrderByCreatedAtDesc(candidateId)) {
            if ("VERIFIED".equals(e.status)) {
                total += "AUTO:URL_OK".equals(e.verifiedBy) ? 25 : 35;
            } else if ("PENDING".equals(e.status)) {
                total += 10;
            }
        }
        return Math.min(100, total);
    }

    private static String firstSegment(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.split("/");
        return parts.length > 1 ? parts[1] : "";
    }

    private static String clip(String s) {
        return s != null && s.length() > 990 ? s.substring(0, 990) : s;
    }
}
