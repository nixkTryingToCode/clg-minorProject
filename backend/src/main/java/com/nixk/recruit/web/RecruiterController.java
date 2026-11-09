package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
import com.nixk.recruit.web.Dtos.CandidateCard;
import com.nixk.recruit.web.Dtos.RecruiterProfileRequest;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recruiter")
@PreAuthorize("hasRole('RECRUITER')")
public class RecruiterController {
    private final RecruiterProfileRepo profiles;
    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final UserRepo users;
    private final AuditService audit;
    private final CandidateProfileRepo candidateProfiles;
    private final EvidenceRepo evidenceRepo;
    private final EvidenceService evidenceService;
    private final MatchingService matching;
    private final SettingsService settings;
    private final SkillCatalog catalog;

    public RecruiterController(RecruiterProfileRepo profiles, JobRepo jobs, ApplicationRepo applications, UserRepo users,
                               AuditService audit, CandidateProfileRepo candidateProfiles, EvidenceRepo evidenceRepo,
                               EvidenceService evidenceService, MatchingService matching, SettingsService settings,
                               SkillCatalog catalog) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.applications = applications;
        this.users = users;
        this.audit = audit;
        this.candidateProfiles = candidateProfiles;
        this.evidenceRepo = evidenceRepo;
        this.evidenceService = evidenceService;
        this.matching = matching;
        this.settings = settings;
        this.catalog = catalog;
    }

    private RecruiterProfile mine() {
        Long uid = CurrentUser.get().id;
        return profiles.findByUserId(uid).orElseGet(() -> {
            RecruiterProfile r = new RecruiterProfile();
            r.userId = uid;
            r.companyName = CurrentUser.get().name + "'s Company";
            return profiles.save(r);
        });
    }

    @GetMapping("/profile")
    public RecruiterProfile profile() {
        return mine();
    }

    @PutMapping("/profile")
    public RecruiterProfile update(@RequestBody RecruiterProfileRequest r) {
        RecruiterProfile p = mine();
        if (r.companyName() != null && !r.companyName().isBlank()) p.companyName = r.companyName().trim();
        p.website = r.website();
        p.location = r.location();
        p.designation = r.designation();
        p = profiles.save(p);
        audit.log(CurrentUser.get().id, "RECRUITER_PROFILE_UPDATE", "RecruiterProfile", p.id, null);
        return p;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Long uid = CurrentUser.get().id;
        List<Job> mine = jobs.findByRecruiterIdOrderByCreatedAtDesc(uid);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) byStatus.put(s.name(), 0);
        List<Map<String, Object>> perJob = new ArrayList<>();
        List<Map<String, Object>> top = new ArrayList<>();
        Map<String, Integer> skillCounts = new HashMap<>();
        int total = 0;
        int scoreSum = 0;
        int verifiedApplicants = 0;
        for (Job j : mine) {
            List<JobApplication> apps = applications.findByJobId(j.id);
            total += apps.size();
            int jobScoreSum = 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", j.id);
            row.put("title", j.title);
            row.put("active", j.active);
            row.put("applicants", apps.size());
            for (JobApplication a : apps) {
                byStatus.merge(a.status.name(), 1, Integer::sum);
                jobScoreSum += a.score;
                scoreSum += a.score;
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("applicationId", a.id);
                t.put("jobId", j.id);
                t.put("jobTitle", j.title);
                t.put("candidate", users.findById(a.candidateId).map(u -> u.name).orElse("Unknown"));
                t.put("score", a.score);
                t.put("status", a.status.name());
                top.add(t);
                candidateProfiles.findByUserId(a.candidateId).ifPresent(cp -> {
                    for (String s : catalog.normalize(cp.skills)) skillCounts.merge(s, 1, Integer::sum);
                });
                if (evidenceService.evidenceScore(a.candidateId) >= 35) verifiedApplicants++;
            }
            row.put("avgScore", apps.isEmpty() ? 0 : Math.round((float) jobScoreSum / apps.size()));
            perJob.add(row);
        }
        top.sort((x, y) -> Integer.compare((Integer) y.get("score"), (Integer) x.get("score")));

        List<Map<String, Object>> topSkills = new ArrayList<>();
        skillCounts.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(8).forEach(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skill", e.getKey());
            m.put("count", e.getValue());
            topSkills.add(m);
        });

        int reviewed = total - byStatus.get("APPLIED") - byStatus.get("WITHDRAWN");
        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("applied", total);
        funnel.put("reviewed", Math.max(0, reviewed));
        funnel.put("shortlisted", byStatus.get("SHORTLISTED") + byStatus.get("INTERVIEW") + byStatus.get("SELECTED"));
        funnel.put("interview", byStatus.get("INTERVIEW") + byStatus.get("SELECTED"));
        funnel.put("selected", byStatus.get("SELECTED"));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeJobs", mine.stream().filter(j -> j.active).count());
        out.put("totalJobs", mine.size());
        out.put("totalApplications", total);
        out.put("averageScore", total == 0 ? 0 : Math.round((float) scoreSum / total));
        out.put("applicantsWithVerifiedEvidence", verifiedApplicants);
        out.put("byStatus", byStatus);
        out.put("funnel", funnel);
        out.put("perJob", perJob);
        out.put("topSkills", topSkills);
        out.put("topCandidates", top.size() > 5 ? new ArrayList<>(top.subList(0, 5)) : top);
        return out;
    }

    /** Candidate discovery: searches candidates who are open to recruiters; optionally ranks them against one of your jobs. */
    @GetMapping("/candidates")
    public List<CandidateCard> searchCandidates(@RequestParam(required = false) String skills,
                                                @RequestParam(required = false) Double minExp,
                                                @RequestParam(required = false) String location,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) Long jobId,
                                                @RequestParam(required = false, defaultValue = "false") boolean verifiedOnly) {
        AppUser me = CurrentUser.get();
        Job job = null;
        if (jobId != null) {
            job = jobs.findById(jobId).orElseThrow(() -> new ApiException(404, "Job not found"));
            if (!Objects.equals(job.recruiterId, me.id)) throw new ApiException(403, "Not your job");
        }
        List<String> required = catalog.normalize(skills);
        Map<Long, AppUser> byId = new HashMap<>();
        for (AppUser u : users.findAll()) byId.put(u.id, u);
        Map<String, Double> w = settings.weights();

        List<CandidateCard> out = new ArrayList<>();
        for (CandidateProfile p : candidateProfiles.findAll()) {
            if (Boolean.FALSE.equals(p.openToRecruiters)) continue;
            AppUser u = byId.get(p.userId);
            if (u == null || !u.enabled || u.role != Role.CANDIDATE) continue;
            if (Csv.nz(p.skills).isBlank()) continue;
            Set<String> have = Csv.lower(catalog.normalize(p.skills));
            boolean ok = true;
            for (String s : required) if (!have.contains(s.toLowerCase(Locale.ROOT))) { ok = false; break; }
            if (!ok) continue;
            if (minExp != null && (p.experienceYears == null ? 0 : p.experienceYears) < minExp) continue;
            if (location != null && !location.isBlank() && !Csv.nz(p.location).toLowerCase(Locale.ROOT).contains(location.trim().toLowerCase(Locale.ROOT))) continue;
            if (q != null && !q.isBlank()) {
                String hay = (u.name + " " + Csv.nz(p.headline) + " " + Csv.nz(p.summary)).toLowerCase(Locale.ROOT);
                if (!hay.contains(q.trim().toLowerCase(Locale.ROOT))) continue;
            }
            int verified = 0;
            for (Evidence e : evidenceRepo.findByCandidateIdOrderByCreatedAtDesc(p.userId)) if ("VERIFIED".equals(e.status)) verified++;
            if (verifiedOnly && verified == 0) continue;
            out.add(new CandidateCard(u.id, u.name, p.headline, p.location, p.experienceYears, p.skills, verified,
                    evidenceService.evidenceScore(u.id), job == null ? null : matching.compute(p, job, w)));
        }
        if (job != null) {
            out.sort(Comparator.comparingInt((CandidateCard c) -> c.match().score()).reversed());
        } else {
            out.sort(Comparator.comparingInt(CandidateCard::evidenceScore).reversed());
        }
        return out.size() > 50 ? new ArrayList<>(out.subList(0, 50)) : out;
    }
}
