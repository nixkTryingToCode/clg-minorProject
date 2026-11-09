package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
import com.nixk.recruit.web.Dtos.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepo users;
    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final AuditLogRepo auditLogs;
    private final SettingsService settings;
    private final SkillCatalog catalog;
    private final AuditService audit;
    private final EvidenceRepo evidenceRepo;
    private final EvidenceService evidenceService;
    private final JobIngestionService ingestion;
    private final EvaluationService evaluation;
    private final AutoApplyService autoApply;

    public AdminController(UserRepo users, JobRepo jobs, ApplicationRepo applications, AuditLogRepo auditLogs,
                           SettingsService settings, SkillCatalog catalog, AuditService audit, EvidenceRepo evidenceRepo,
                           EvidenceService evidenceService, JobIngestionService ingestion, EvaluationService evaluation,
                           AutoApplyService autoApply) {
        this.users = users;
        this.jobs = jobs;
        this.applications = applications;
        this.auditLogs = auditLogs;
        this.settings = settings;
        this.catalog = catalog;
        this.audit = audit;
        this.evidenceRepo = evidenceRepo;
        this.evidenceService = evidenceService;
        this.ingestion = ingestion;
        this.evaluation = evaluation;
        this.autoApply = autoApply;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<AppUser> all = users.findAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("candidates", all.stream().filter(u -> u.role == Role.CANDIDATE).count());
        out.put("recruiters", all.stream().filter(u -> u.role == Role.RECRUITER).count());
        out.put("admins", all.stream().filter(u -> u.role == Role.ADMIN).count());
        List<Job> js = jobs.findAll();
        out.put("jobs", js.size());
        out.put("activeJobs", js.stream().filter(j -> j.active).count());
        out.put("externalJobs", js.stream().filter(j -> "EXTERNAL".equals(j.source)).count());
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) byStatus.put(s.name(), 0);
        List<JobApplication> apps = applications.findAll();
        int auto = 0;
        for (JobApplication a : apps) {
            byStatus.merge(a.status.name(), 1, Integer::sum);
            if (ApplicationWorkflow.AUTO.equals(a.mode)) auto++;
        }
        out.put("applications", apps.size());
        out.put("autoApplications", auto);
        out.put("pendingEvidence", evidenceRepo.findByStatusOrderByCreatedAtDesc("PENDING").size());
        out.put("applicationsByStatus", byStatus);
        return out;
    }

    @GetMapping("/users")
    public List<AppUser> users() {
        return users.findAll();
    }

    @PatchMapping("/users/{id}/enabled")
    public AppUser setEnabled(@PathVariable Long id, @RequestBody EnabledRequest r) {
        AppUser me = CurrentUser.get();
        AppUser u = users.findById(id).orElseThrow(() -> new ApiException(404, "User not found"));
        if (u.id.equals(me.id)) throw new ApiException(400, "You cannot disable your own account");
        u.enabled = r.enabled();
        u = users.save(u);
        audit.log(me.id, r.enabled() ? "USER_ENABLED" : "USER_DISABLED", "User", u.id, u.email);
        return u;
    }

    @GetMapping("/jobs")
    public List<Job> jobs() {
        return jobs.findAll();
    }

    @PatchMapping("/jobs/{id}/active")
    public Job setActive(@PathVariable Long id, @RequestBody EnabledRequest r) {
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        j.active = r.enabled();
        j = jobs.save(j);
        audit.log(CurrentUser.get().id, r.enabled() ? "JOB_ACTIVATED" : "JOB_DEACTIVATED", "Job", j.id, j.title);
        return j;
    }

    /** Imports jobs from the public Arbeitnow API (external source, never auto-applied). */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody(required = false) IngestRequest r) {
        int limit = r == null || r.limit() == null ? 20 : r.limit();
        Map<String, Object> res = ingestion.importJobs(limit);
        audit.log(CurrentUser.get().id, "JOBS_INGESTED", "Job", null, res.toString());
        return res;
    }

    @GetMapping("/audit")
    public List<AuditLog> audit() {
        return auditLogs.findTop100ByOrderByCreatedAtDesc();
    }

    @GetMapping("/weights")
    public Map<String, Double> weights() {
        return settings.weights();
    }

    @PutMapping("/weights")
    public Map<String, Double> saveWeights(@RequestBody Map<String, Double> in) {
        Map<String, Double> saved = settings.saveWeights(in);
        audit.log(CurrentUser.get().id, "WEIGHTS_UPDATED", "Settings", null, saved.toString());
        return saved;
    }

    @GetMapping("/skills")
    public List<String> skills() {
        return catalog.all();
    }

    /* ---------------- evidence review ---------------- */

    @GetMapping("/evidence")
    public List<Map<String, Object>> evidence(@RequestParam(required = false) String status) {
        List<Evidence> list = status == null || status.isBlank() ? evidenceRepo.findAll() : evidenceRepo.findByStatusOrderByCreatedAtDesc(status.toUpperCase(Locale.ROOT));
        Map<Long, AppUser> byId = new HashMap<>();
        for (AppUser u : users.findAll()) byId.put(u.id, u);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Evidence e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("evidence", e);
            AppUser u = byId.get(e.candidateId);
            m.put("candidate", u == null ? "Unknown" : u.name);
            out.add(m);
        }
        return out;
    }

    @PatchMapping("/evidence/{id}")
    public Evidence review(@PathVariable Long id, @RequestBody ReviewRequest r) {
        Evidence e = evidenceRepo.findById(id).orElseThrow(() -> new ApiException(404, "Evidence not found"));
        return evidenceService.review(e, r.status() == null ? "" : r.status().toUpperCase(Locale.ROOT), CurrentUser.get().id, r.note());
    }

    /* ---------------- evaluation & automation ---------------- */

    @GetMapping("/evaluation")
    public Map<String, Object> evaluation() {
        return evaluation.run();
    }

    @PostMapping("/auto-apply/run")
    public Map<String, Object> runAutoApply() {
        int n = autoApply.runAll();
        audit.log(CurrentUser.get().id, "AUTO_APPLY_RUN_ALL", "AutoApply", null, n + " application(s)");
        return Map.of("applications", n);
    }
}
