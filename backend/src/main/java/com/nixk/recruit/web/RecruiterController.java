package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.AuditService;
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

    public RecruiterController(RecruiterProfileRepo profiles, JobRepo jobs, ApplicationRepo applications, UserRepo users, AuditService audit) {
        this.profiles = profiles;
        this.jobs = jobs;
        this.applications = applications;
        this.users = users;
        this.audit = audit;
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
        int total = 0;
        for (Job j : mine) {
            List<JobApplication> apps = applications.findByJobId(j.id);
            total += apps.size();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", j.id);
            row.put("title", j.title);
            row.put("active", j.active);
            row.put("applicants", apps.size());
            perJob.add(row);
            for (JobApplication a : apps) {
                byStatus.merge(a.status.name(), 1, Integer::sum);
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("applicationId", a.id);
                t.put("jobId", j.id);
                t.put("jobTitle", j.title);
                t.put("candidate", users.findById(a.candidateId).map(u -> u.name).orElse("Unknown"));
                t.put("score", a.score);
                t.put("status", a.status.name());
                top.add(t);
            }
        }
        top.sort((x, y) -> Integer.compare((Integer) y.get("score"), (Integer) x.get("score")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeJobs", mine.stream().filter(j -> j.active).count());
        out.put("totalJobs", mine.size());
        out.put("totalApplications", total);
        out.put("byStatus", byStatus);
        out.put("perJob", perJob);
        out.put("topCandidates", top.size() > 5 ? top.subList(0, 5) : top);
        return out;
    }
}
