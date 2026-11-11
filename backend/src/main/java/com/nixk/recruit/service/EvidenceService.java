package com.nixk.recruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nixk.recruit.model.Evidence;
import com.nixk.recruit.repo.EvidenceRepo;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Candidate evidence (profile links, certificates). Verification is explicit and auditable:
 * GitHub profiles are checked automatically through GitHub's public API; everything else is PENDING until an admin reviews it.
 */
@Service
public class EvidenceService {
    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);
    private static final Pattern GH_USER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");
    public static final Map<String, String> DOMAINS = Map.of(
            "GITHUB", "github.com", "LEETCODE", "leetcode.com", "HACKERRANK", "hackerrank.com",
            "KAGGLE", "kaggle.com", "LINKEDIN", "linkedin.com");
    public static final Set<String> TYPES = Set.of("GITHUB", "LEETCODE", "HACKERRANK", "KAGGLE", "LINKEDIN", "CERTIFICATE", "PORTFOLIO", "OTHER");

    private final EvidenceRepo repo;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvidenceService(EvidenceRepo repo, NotificationService notifications, AuditService audit) {
        this.repo = repo;
        this.notifications = notifications;
        this.audit = audit;
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
        if ("GITHUB".equals(t)) autoVerifyGithub(e, uri);
        e = repo.save(e);
        audit.log(candidateId, "EVIDENCE_ADDED", "Evidence", e.id, t + " -> " + e.status);
        return e;
    }

    private void autoVerifyGithub(Evidence e, URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] parts = path.split("/");
        String user = parts.length > 1 ? parts[1] : "";
        if (!GH_USER.matcher(user).matches()) return;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/users/" + user))
                    .header("User-Agent", "snap-hire-verifier").header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            e.checkedAt = Instant.now();
            if (res.statusCode() == 200) {
                JsonNode n = mapper.readTree(res.body());
                e.status = "VERIFIED";
                e.verifiedBy = "AUTO:GITHUB_API";
                e.metadata = "GitHub account '" + n.path("login").asText(user) + "' exists; public repos: "
                        + n.path("public_repos").asInt() + "; followers: " + n.path("followers").asInt();
            } else if (res.statusCode() == 404) {
                e.status = "REJECTED";
                e.verifiedBy = "AUTO:GITHUB_API";
                e.metadata = "No GitHub account named '" + user + "' was found";
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("GitHub verification unavailable: {}", ex.toString());
        }
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

    /** 0-100: how much of the profile is backed by evidence (verified counts most). */
    public int evidenceScore(Long candidateId) {
        int v = 0, p = 0;
        for (Evidence e : repo.findByCandidateIdOrderByCreatedAtDesc(candidateId)) {
            if ("VERIFIED".equals(e.status)) v++; else if ("PENDING".equals(e.status)) p++;
        }
        return Math.min(100, v * 35 + p * 10);
    }
}
