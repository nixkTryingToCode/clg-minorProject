package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
import com.nixk.recruit.web.Dtos.EnabledRequest;
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

    public AdminController(UserRepo users, JobRepo jobs, ApplicationRepo applications, AuditLogRepo auditLogs,
                           SettingsService settings, SkillCatalog catalog, AuditService audit) {
        this.users = users;
        this.jobs = jobs;
        this.applications = applications;
        this.auditLogs = auditLogs;
        this.settings = settings;
        this.catalog = catalog;
        this.audit = audit;
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
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) byStatus.put(s.name(), 0);
        List<JobApplication> apps = applications.findAll();
        for (JobApplication a : apps) byStatus.merge(a.status.name(), 1, Integer::sum);
        out.put("applications", apps.size());
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
}
