package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
import com.nixk.recruit.service.MatchingService.MatchResult;
import com.nixk.recruit.web.Dtos.*;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationRepo applications;
    private final ApplicationEventRepo events;
    private final JobRepo jobs;
    private final UserRepo users;
    private final CandidateProfileRepo candidateProfiles;
    private final ProfileService profiles;
    private final MatchingService matching;
    private final SettingsService settings;
    private final AuditService audit;

    public ApplicationController(ApplicationRepo applications, ApplicationEventRepo events, JobRepo jobs, UserRepo users,
                                 CandidateProfileRepo candidateProfiles, ProfileService profiles, MatchingService matching,
                                 SettingsService settings, AuditService audit) {
        this.applications = applications;
        this.events = events;
        this.jobs = jobs;
        this.users = users;
        this.candidateProfiles = candidateProfiles;
        this.profiles = profiles;
        this.matching = matching;
        this.settings = settings;
        this.audit = audit;
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    @Transactional
    public JobApplication apply(@Valid @RequestBody ApplyRequest r) {
        AppUser me = CurrentUser.get();
        Job job = jobs.findById(r.jobId()).orElseThrow(() -> new ApiException(404, "Job not found"));
        if (!job.active) throw new ApiException(400, "This job is no longer accepting applications");
        if (applications.findByCandidateIdAndJobId(me.id, job.id).isPresent()) {
            throw new ApiException(409, "You have already applied to this job");
        }
        CandidateProfile p = profiles.getOrCreate(me.id);
        if (Csv.nz(p.skills).isBlank()) throw new ApiException(400, "Please add your skills to your profile before applying");
        MatchResult m = matching.compute(p, job);
        JobApplication a = new JobApplication();
        a.candidateId = me.id;
        a.jobId = job.id;
        a.score = m.score();
        a.matchedSkills = Csv.join(m.matched());
        a.missingSkills = Csv.join(m.missing());
        a.coverLetter = r.coverLetter() == null ? null : (r.coverLetter().length() > 5000 ? r.coverLetter().substring(0, 5000) : r.coverLetter());
        a = applications.save(a);
        event(a.id, "APPLIED", "Applied with match score " + m.score() + "%");
        audit.log(me.id, "APPLY", "Application", a.id, "job=" + job.id);
        return a;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<MyApplicationView> mine() {
        List<MyApplicationView> out = new ArrayList<>();
        for (JobApplication a : applications.findByCandidateIdOrderByCreatedAtDesc(CurrentUser.get().id)) {
            Job j = jobs.findById(a.jobId).orElse(null);
            out.add(new MyApplicationView(a, j == null ? "(job removed)" : j.title, j == null ? "" : j.company));
        }
        return out;
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Transactional
    public JobApplication withdraw(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        JobApplication a = applications.findById(id).orElseThrow(() -> new ApiException(404, "Application not found"));
        if (!Objects.equals(a.candidateId, me.id)) throw new ApiException(403, "Not your application");
        if (a.status == ApplicationStatus.WITHDRAWN) return a;
        if (a.status == ApplicationStatus.SELECTED || a.status == ApplicationStatus.REJECTED) {
            throw new ApiException(400, "This application is already closed");
        }
        a.status = ApplicationStatus.WITHDRAWN;
        a.updatedAt = Instant.now();
        a = applications.save(a);
        event(a.id, "WITHDRAWN", "Candidate withdrew the application");
        audit.log(me.id, "WITHDRAW", "Application", a.id, null);
        return a;
    }

    @GetMapping("/{id}/events")
    public List<ApplicationEvent> events(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        JobApplication a = applications.findById(id).orElseThrow(() -> new ApiException(404, "Application not found"));
        if (me.role == Role.CANDIDATE && !Objects.equals(a.candidateId, me.id)) throw new ApiException(403, "Not your application");
        if (me.role == Role.RECRUITER) ownedJob(a.jobId, me);
        return events.findByApplicationIdOrderByCreatedAtAsc(id);
    }

    /** Recruiter: applicants for one job, ranked by live job-specific compatibility. */
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public List<ApplicantView> forJob(@PathVariable Long jobId) {
        AppUser me = CurrentUser.get();
        Job job = ownedJob(jobId, me);
        Map<String, Double> w = settings.weights();
        List<ApplicantView> out = new ArrayList<>();
        for (JobApplication a : applications.findByJobId(job.id)) {
            AppUser cand = users.findById(a.candidateId).orElse(null);
            if (cand == null) continue;
            CandidateProfile p = profiles.getOrCreate(cand.id);
            out.add(new ApplicantView(a, cand.name, cand.email, p, matching.compute(p, job, w)));
        }
        out.sort(Comparator.comparingInt((ApplicantView v) -> v.match().score()).reversed());
        return out;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    @Transactional
    public JobApplication setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest r) {
        AppUser me = CurrentUser.get();
        JobApplication a = applications.findById(id).orElseThrow(() -> new ApiException(404, "Application not found"));
        ownedJob(a.jobId, me);
        ApplicationStatus s;
        try {
            s = ApplicationStatus.valueOf(r.status().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Unknown status");
        }
        if (s == ApplicationStatus.WITHDRAWN || s == ApplicationStatus.APPLIED) throw new ApiException(400, "Recruiters cannot set this status");
        if (a.status == ApplicationStatus.WITHDRAWN) throw new ApiException(400, "Candidate has withdrawn this application");
        ApplicationStatus old = a.status;
        a.status = s;
        a.updatedAt = Instant.now();
        a = applications.save(a);
        event(a.id, "STATUS_CHANGED", old + " -> " + s);
        audit.log(me.id, "APPLICATION_STATUS", "Application", a.id, old + " -> " + s);
        return a;
    }

    private Job ownedJob(Long jobId, AppUser me) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new ApiException(404, "Job not found"));
        if (me.role != Role.ADMIN && !Objects.equals(job.recruiterId, me.id)) throw new ApiException(403, "Not your job");
        return job;
    }

    private void event(Long appId, String type, String details) {
        ApplicationEvent e = new ApplicationEvent();
        e.applicationId = appId;
        e.eventType = type;
        e.details = details;
        events.save(e);
    }
}
