package com.nixk.recruit.service;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.service.MatchingService.MatchResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single place where applications are created (manual, automatic and external-assisted tracking), so validation,
 * duplicate protection, events, notifications and audit logging are identical for every mode.
 */
@Service
public class ApplicationWorkflow {
    public static final String MANUAL = "MANUAL";
    public static final String AUTO = "AUTO";
    public static final String EXTERNAL = "EXTERNAL_ASSISTED";

    private final ApplicationRepo applications;
    private final ApplicationEventRepo events;
    private final ResumeVersionRepo versions;
    private final ProfileService profiles;
    private final MatchingService matching;
    private final OptimizerService optimizer;
    private final NotificationService notifications;
    private final AuditService audit;
    private final UserRepo users;

    public ApplicationWorkflow(ApplicationRepo applications, ApplicationEventRepo events, ResumeVersionRepo versions,
                               ProfileService profiles, MatchingService matching, OptimizerService optimizer,
                               NotificationService notifications, AuditService audit, UserRepo users) {
        this.applications = applications;
        this.events = events;
        this.versions = versions;
        this.profiles = profiles;
        this.matching = matching;
        this.optimizer = optimizer;
        this.notifications = notifications;
        this.audit = audit;
        this.users = users;
    }

    @Transactional
    public JobApplication submit(AppUser candidate, Job job, String modeIn, String coverLetterIn, Long resumeVersionId) {
        boolean external = "EXTERNAL".equals(job.source);
        String mode = modeIn == null || modeIn.isBlank() ? (external ? EXTERNAL : MANUAL) : modeIn;

        if (!job.active) throw new ApiException(400, "This job is no longer accepting applications");
        if (external && !EXTERNAL.equals(mode)) {
            throw new ApiException(400, "External jobs cannot be submitted through this platform. Use the application package and apply on the original listing, then track it here.");
        }
        if (!external && EXTERNAL.equals(mode)) throw new ApiException(400, "Only external jobs can be tracked as externally applied");
        if (applications.findByCandidateIdAndJobId(candidate.id, job.id).isPresent()) {
            throw new ApiException(409, "You have already applied to this job");
        }
        CandidateProfile p = profiles.getOrCreate(candidate.id);
        if (Csv.nz(p.skills).isBlank()) throw new ApiException(400, "Please add your skills to your profile before applying");

        Long versionId = resumeVersionId;
        String cover = coverLetterIn;
        if (versionId != null) {
            ResumeVersion rv = versions.findById(versionId).orElseThrow(() -> new ApiException(404, "Resume version not found"));
            if (!candidate.id.equals(rv.candidateId)) throw new ApiException(403, "Not your resume version");
        } else if (AUTO.equals(mode)) {
            // automatic applications always carry a job-specific optimised resume and a draft cover letter
            OptimizerService.Optimized o = optimizer.forJob(candidate, p, job);
            ResumeVersion rv = new ResumeVersion();
            rv.candidateId = candidate.id;
            rv.jobId = job.id;
            rv.title = "Auto-apply: " + job.title;
            rv.content = o.resumeText();
            versionId = versions.save(rv).id;
            if (cover == null || cover.isBlank()) cover = o.coverLetter();
        }

        MatchResult m = matching.compute(p, job);
        JobApplication a = new JobApplication();
        a.candidateId = candidate.id;
        a.jobId = job.id;
        a.mode = mode;
        a.resumeVersionId = versionId;
        a.score = m.score();
        a.matchedSkills = Csv.join(m.matched());
        a.missingSkills = Csv.join(m.missing());
        a.coverLetter = cover == null ? null : (cover.length() > 5000 ? cover.substring(0, 5000) : cover);
        a = applications.save(a);

        String detail = switch (mode) {
            case AUTO -> "Automatically applied by your auto-apply rule (match " + m.score() + "%)";
            case EXTERNAL -> "Marked as applied on the external site (match " + m.score() + "%)";
            default -> "Applied with match score " + m.score() + "%";
        };
        event(a.id, EXTERNAL.equals(mode) ? "TRACKED_EXTERNAL" : (AUTO.equals(mode) ? "AUTO_APPLIED" : "APPLIED"), detail);
        audit.log(candidate.id, AUTO.equals(mode) ? "AUTO_APPLY" : "APPLY", "Application", a.id, "job=" + job.id + " mode=" + mode);
        if (AUTO.equals(mode)) {
            notifications.notify(candidate.id, "AUTO_APPLY", "Auto-applied to \"" + job.title + "\" at " + job.company + " (" + m.score() + "% match).", a.id);
        }
        if (job.recruiterId != null) {
            notifications.notify(job.recruiterId, "NEW_APPLICATION",
                    candidate.name + (AUTO.equals(mode) ? " (auto-apply)" : "") + " applied to \"" + job.title + "\" — " + m.score() + "% match.", a.id);
        }
        return a;
    }

    public void event(Long appId, String type, String details) {
        ApplicationEvent e = new ApplicationEvent();
        e.applicationId = appId;
        e.eventType = type;
        e.details = details;
        events.save(e);
    }
}
