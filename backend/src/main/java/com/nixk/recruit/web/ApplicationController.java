package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
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
    private final ProfileService profiles;
    private final MatchingService matching;
    private final SettingsService settings;
    private final AuditService audit;
    private final ApplicationWorkflow workflow;
    private final NotificationService notifications;
    private final EvidenceRepo evidenceRepo;
    private final EvidenceService evidenceService;
    private final CompanyService companyService;

    public ApplicationController(ApplicationRepo applications, ApplicationEventRepo events, JobRepo jobs, UserRepo users,
                                 ProfileService profiles, MatchingService matching, SettingsService settings,
                                 AuditService audit, ApplicationWorkflow workflow, NotificationService notifications,
                                 EvidenceRepo evidenceRepo, EvidenceService evidenceService, CompanyService companyService) {
        this.applications = applications;
        this.events = events;
        this.jobs = jobs;
        this.users = users;
        this.profiles = profiles;
        this.matching = matching;
        this.settings = settings;
        this.audit = audit;
        this.workflow = workflow;
        this.notifications = notifications;
        this.evidenceRepo = evidenceRepo;
        this.evidenceService = evidenceService;
        this.companyService = companyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public JobApplication apply(@Valid @RequestBody ApplyRequest r) {
        Job job = jobs.findById(r.jobId()).orElseThrow(() -> new ApiException(404, "Job not found"));
        String mode = r.mode();
        if (ApplicationWorkflow.AUTO.equals(mode)) throw new ApiException(400, "Automatic applications are created by your auto-apply rule only");
        return workflow.submit(CurrentUser.get(), job, mode, r.coverLetter(), r.resumeVersionId());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<MyApplicationView> mine() {
        List<MyApplicationView> out = new ArrayList<>();
        for (JobApplication a : applications.findByCandidateIdOrderByCreatedAtDesc(CurrentUser.get().id)) {
            Job j = jobs.findById(a.jobId).orElse(null);
            out.add(new MyApplicationView(a, j == null ? "(job removed)" : j.title, j == null ? "" : j.company,
                    j == null ? null : j.source, j == null ? null : j.externalUrl));
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
        workflow.event(a.id, "WITHDRAWN", "Candidate withdrew the application");
        audit.log(me.id, "WITHDRAW", "Application", a.id, null);
        Job job = jobs.findById(a.jobId).orElse(null);
        if (job != null && job.recruiterId != null) {
            notifications.notify(job.recruiterId, "WITHDRAWN", me.name + " withdrew their application for \"" + job.title + "\".", a.id, job.id);
        }
        return a;
    }

    @GetMapping("/{id}/events")
    public List<ApplicationEvent> events(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        JobApplication a = applications.findById(id).orElseThrow(() -> new ApiException(404, "Application not found"));
        if (me.role == Role.CANDIDATE && !Objects.equals(a.candidateId, me.id)) throw new ApiException(403, "Not your application");
        if (me.role == Role.RECRUITER || me.role == Role.COMPANY_ADMIN) {
            ownedJob(a.jobId, me);
            companyService.require(me, Permission.VIEW_APPLICANTS);
        }
        return events.findByApplicationIdOrderByCreatedAtAsc(id);
    }

    /** Recruiter: applicants for one job, ranked by job-specific compatibility blended with verified evidence (85/15). */
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN','ADMIN')")
    public List<ApplicantView> forJob(@PathVariable Long jobId) {
        AppUser me = CurrentUser.get();
        Job job = ownedJob(jobId, me);
        companyService.require(me, Permission.VIEW_APPLICANTS);
        Map<String, Double> w = settings.weights();
        List<ApplicantView> out = new ArrayList<>();
        for (JobApplication a : applications.findByJobId(job.id)) {
            AppUser cand = users.findById(a.candidateId).orElse(null);
            if (cand == null) continue;
            CandidateProfile p = profiles.getOrCreate(cand.id);
            var match = matching.compute(p, job, w);
            int evScore = evidenceService.evidenceScore(cand.id);
            int rank = (int) Math.round(0.85 * match.score() + 0.15 * evScore);
            out.add(new ApplicantView(a, cand.name, cand.email, p, match,
                    evidenceRepo.findByCandidateIdOrderByCreatedAtDesc(cand.id), evScore, rank));
        }
        out.sort(Comparator.comparingInt(ApplicantView::rankScore).reversed());
        return out;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
    @Transactional
    public JobApplication setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest r) {
        AppUser me = CurrentUser.get();
        JobApplication a = applications.findById(id).orElseThrow(() -> new ApiException(404, "Application not found"));
        Job job = ownedJob(a.jobId, me);
        companyService.require(me, Permission.UPDATE_STATUS);
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
        workflow.event(a.id, "STATUS_CHANGED", old + " -> " + s);
        audit.log(me.id, "APPLICATION_STATUS", "Application", a.id, old + " -> " + s);
        notifications.notify(a.candidateId, "STATUS", "Your application for \"" + job.title + "\" at " + job.company
                + " is now " + s.name().toLowerCase(Locale.ROOT) + ".", a.id, job.id);
        return a;
    }

    private Job ownedJob(Long jobId, AppUser me) {
        Job job = jobs.findById(jobId).orElseThrow(() -> new ApiException(404, "Job not found"));
        boolean own = me.role == Role.ADMIN || Objects.equals(job.recruiterId, me.id);
        if (!own && !(job.companyId != null
                && companyService.memberOf(me.id).filter(m -> m.companyId.equals(job.companyId)).isPresent())) {
            throw new ApiException(403, "Not your job");
        }
        return job;
    }
}
