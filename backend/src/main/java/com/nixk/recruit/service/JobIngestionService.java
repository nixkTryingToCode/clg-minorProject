package com.nixk.recruit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nixk.recruit.model.Job;
import com.nixk.recruit.repo.JobRepo;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Imports jobs from the public Arbeitnow job-board API (an official, key-less API intended for this use).
 * Jobs are normalised into the common Job model, de-duplicated by source id and marked source=EXTERNAL.
 * External jobs are never auto-applied; candidates get the job link plus an optimised application package.
 */
@Service
public class JobIngestionService {
    private static final String API = "https://www.arbeitnow.com/api/job-board-api";
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\+?\\s*(?:years|yrs)", Pattern.CASE_INSENSITIVE);

    private final JobRepo jobs;
    private final SkillCatalog catalog;
    private final ObjectMapper mapper = new ObjectMapper();

    public JobIngestionService(JobRepo jobs, SkillCatalog catalog) {
        this.jobs = jobs;
        this.catalog = catalog;
    }

    public Map<String, Object> importJobs(int limit) {
        int cap = Math.max(1, Math.min(limit, 50));
        int imported = 0, skipped = 0;
        JsonNode data;
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(API)).header("User-Agent", "snap-hire-ingest")
                    .header("Accept", "application/json").timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new ApiException(502, "The job feed returned HTTP " + res.statusCode());
            data = mapper.readTree(res.body()).path("data");
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "Job import was interrupted");
        } catch (Exception e) {
            throw new ApiException(502, "Could not reach the external job feed: " + e.getMessage());
        }
        for (JsonNode n : data) {
            if (imported >= cap) break;
            String slug = n.path("slug").asText("");
            if (slug.isEmpty()) continue;
            String sourceId = "arbeitnow:" + slug;
            if (jobs.findFirstBySourceJobId(sourceId).isPresent()) { skipped++; continue; }

            String description = clean(n.path("description").asText(""));
            StringBuilder tags = new StringBuilder();
            for (JsonNode t : n.path("tags")) tags.append(t.asText()).append(' ');
            List<String> skills = catalog.extract(n.path("title").asText("") + " " + tags + " " + description);
            if (skills.isEmpty()) { skipped++; continue; }   // not a tech role we can match against

            Job j = new Job();
            j.source = "EXTERNAL";
            j.sourceJobId = sourceId;
            j.title = clip(n.path("title").asText("Untitled"), 250);
            j.company = clip(n.path("company_name").asText("Unknown company"), 250);
            j.description = clip(description, 7900);
            j.requiredSkills = Csv.join(skills.subList(0, Math.min(10, skills.size())));
            String loc = n.path("location").asText("");
            j.location = clip(n.path("remote").asBoolean(false) ? (loc.isBlank() ? "Remote" : loc + " / Remote") : loc, 250);
            j.employmentType = employmentType(n.path("job_types"));
            j.externalUrl = n.path("url").asText(null);
            var m = YEARS.matcher(description);
            j.minExperience = m.find() ? Math.min(15, Double.parseDouble(m.group(1))) : 0.0;
            jobs.save(j);
            imported++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("imported", imported);
        out.put("skipped", skipped);
        out.put("source", "Arbeitnow public job-board API");
        return out;
    }

    private static String employmentType(JsonNode types) {
        for (JsonNode t : types) {
            String s = t.asText("").toLowerCase(Locale.ROOT);
            if (s.contains("intern")) return "Internship";
            if (s.contains("part")) return "Part-time";
            if (s.contains("contract") || s.contains("freelance")) return "Contract";
            if (s.contains("full")) return "Full-time";
        }
        return "Full-time";
    }

    private static String clean(String html) {
        String s = TAGS.matcher(html).replaceAll(" ");
        s = s.replace("&amp;", "&").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String clip(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
