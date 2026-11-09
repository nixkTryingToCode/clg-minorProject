package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
import com.nixk.recruit.service.MatchingService.Impact;
import com.nixk.recruit.service.MatchingService.MatchResult;
import com.nixk.recruit.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final RecruiterProfileRepo recruiters;
    private final ProfileService profiles;
    private final MatchingService matching;
    private final SettingsService settings;
    private final SkillCatalog catalog;
    private final CoverLetterService coverLetters;
    private final AuditService audit;
    private final OptimizerService optimizer;
    private final ResumeVersionRepo versions;
    private final InterviewPrepService interviewPrep;
    private final AutoApplyService autoApply;

    public JobController(JobRepo jobs, ApplicationRepo applications, RecruiterProfileRepo recruiters, ProfileService profiles,
                         MatchingService matching, SettingsService settings, SkillCatalog catalog,
                         CoverLetterService coverLetters, AuditService audit, OptimizerService optimizer,
                         ResumeVersionRepo versions, InterviewPrepService interviewPrep, AutoApplyService autoApply) {
        this.jobs = jobs;
        this.applications = applications;
        this.recruiters = recruiters;
        this.profiles = profiles;
        this.matching = matching;
        this.settings = settings;
        this.catalog = catalog;
        this.coverLetters = coverLetters;
        this.audit = audit;
        this.optimizer = optimizer;
        this.versions = versions;
        this.interviewPrep = interviewPrep;
        this.autoApply = autoApply;
    }

    @GetMapping
    public List<JobView> list(@RequestParam(required = false) String q, @RequestParam(required = false) String location,
                              @RequestParam(required = false) String type, @RequestParam(required = false) String skill,
                              @RequestParam(required = false) String role, @RequestParam(required = false) String source) {
        AppUser me = CurrentUser.get();
        CandidateProfile p = me.role == Role.CANDIDATE ? profiles.getOrCreate(me.id) : null;
        Map<Long, Long> applied = new HashMap<>();
        if (p != null) for (JobApplication a : applications.findByCandidateIdOrderByCreatedAtDesc(me.id)) applied.put(a.jobId, a.id);
        Map<String, Double> w = settings.weights();
        RoleCatalog.RoleDef roleDef = role == null || role.isBlank() ? null : RoleCatalog.byName(role);
        List<JobView> out = new ArrayList<>();
        for (Job j : jobs.findByActiveTrueOrderByCreatedAtDesc()) {
            if (!matches(j, q, location, type, skill, roleDef, source)) continue;
            MatchResult m = p == null ? null : matching.compute(p, j, w);
            out.add(new JobView(j, m, applied.containsKey(j.id), applied.get(j.id)));
        }
        if (p != null) out.sort(Comparator.comparingInt((JobView v) -> v.match().score()).reversed());
        return out;
    }

    private boolean matches(Job j, String q, String location, String type, String skill, RoleCatalog.RoleDef role, String source) {
        if (role != null && !RoleCatalog.titleMatches(role, j.title)) return false;
        if (source != null && !source.isBlank()) {
            boolean external = "EXTERNAL".equals(j.source);
            if ("EXTERNAL".equalsIgnoreCase(source) && !external) return false;
            if ("INTERNAL".equalsIgnoreCase(source) && external) return false;
        }
        if (q != null && !q.isBlank()) {
            String hay = (Csv.nz(j.title) + " " + Csv.nz(j.company) + " " + Csv.nz(j.description)).toLowerCase(Locale.ROOT);
            if (!hay.contains(q.trim().toLowerCase(Locale.ROOT))) return false;
        }
        if (location != null && !location.isBlank() && !Csv.nz(j.location).toLowerCase(Locale.ROOT).contains(location.trim().toLowerCase(Locale.ROOT))) return false;
        if (type != null && !type.isBlank() && !type.equalsIgnoreCase(j.employmentType)) return false;
        if (skill != null && !skill.isBlank()) {
            String wanted = catalog.normalize(skill).stream().findFirst().orElse(skill).toLowerCase(Locale.ROOT);
            if (!Csv.lower(catalog.normalize(j.requiredSkills)).contains(wanted)) return false;
        }
        return true;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('RECRUITER')")
    public List<Map<String, Object>> mine() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Job j : jobs.findByRecruiterIdOrderByCreatedAtDesc(CurrentUser.get().id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("job", j);
            m.put("applicantCount", applications.findByJobId(j.id).size());
            out.add(m);
        }
        return out;
    }

    @GetMapping("/{id}")
    public JobView get(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        if (!j.active && !(me.role == Role.ADMIN || Objects.equals(j.recruiterId, me.id))) throw new ApiException(404, "Job not found");
        if (me.role != Role.CANDIDATE) return new JobView(j, null, false, null);
        CandidateProfile p = profiles.getOrCreate(me.id);
        var existing = applications.findByCandidateIdAndJobId(me.id, id);
        return new JobView(j, matching.compute(p, j), existing.isPresent(), existing.map(a -> a.id).orElse(null));
    }

    @GetMapping("/{id}/impact")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<Impact> impact(@PathVariable Long id) {
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        return matching.skillImpact(profiles.getOrCreate(CurrentUser.get().id), j);
    }

    @PostMapping("/{id}/cover-letter")
    @PreAuthorize("hasRole('CANDIDATE')")
    public Map<String, String> coverLetter(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        return Map.of("coverLetter", coverLetters.draft(me, profiles.getOrCreate(me.id), j));
    }

    /** Job-specific resume optimisation: suggestions + an optimised resume version (saved) + cover letter draft. */
    @PostMapping("/{id}/optimize")
    @PreAuthorize("hasRole('CANDIDATE')")
    public Map<String, Object> optimize(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        OptimizerService.Optimized o = optimizer.forJob(me, profiles.getOrCreate(me.id), j);
        ResumeVersion rv = new ResumeVersion();
        rv.candidateId = me.id;
        rv.jobId = j.id;
        rv.title = "Tailored for: " + j.title;
        rv.content = o.resumeText();
        rv = versions.save(rv);
        audit.log(me.id, "RESUME_OPTIMIZED", "ResumeVersion", rv.id, "job=" + j.id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("suggestions", o.suggestions());
        out.put("resumeText", o.resumeText());
        out.put("coverLetter", o.coverLetter());
        out.put("resumeVersionId", rv.id);
        out.put("match", o.match());
        return out;
    }

    @GetMapping("/{id}/interview-prep")
    @PreAuthorize("hasRole('CANDIDATE')")
    public Map<String, Object> interviewPrep(@PathVariable Long id) {
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        MatchResult m = matching.compute(profiles.getOrCreate(CurrentUser.get().id), j);
        return interviewPrep.prepare(j, m.missing());
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public Job create(@Valid @RequestBody JobRequest r) {
        AppUser me = CurrentUser.get();
        Job j = new Job();
        j.recruiterId = me.id;
        j.source = "RECRUITER";
        apply(j, r, me);
        j = jobs.save(j);
        audit.log(me.id, "JOB_CREATE", "Job", j.id, j.title);
        if (j.active) {
            try {
                int n = autoApply.runForJob(j);
                if (n > 0) audit.log(me.id, "AUTO_APPLY_TRIGGERED", "Job", j.id, n + " candidate(s) auto-applied");
            } catch (Exception e) {
                log.warn("Auto-apply for new job failed: {}", e.toString());
            }
        }
        return j;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public Job update(@PathVariable Long id, @Valid @RequestBody JobRequest r) {
        AppUser me = CurrentUser.get();
        Job j = owned(id, me);
        apply(j, r, me);
        j = jobs.save(j);
        audit.log(me.id, "JOB_UPDATE", "Job", j.id, j.title);
        return j;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public Map<String, String> delete(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        Job j = owned(id, me);
        for (JobApplication a : applications.findByJobId(id)) {
            if (a.status != ApplicationStatus.WITHDRAWN) { a.status = ApplicationStatus.WITHDRAWN; applications.save(a); }
        }
        jobs.delete(j);
        audit.log(me.id, "JOB_DELETE", "Job", id, j.title);
        return Map.of("status", "deleted");
    }

    private Job owned(Long id, AppUser me) {
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        if (me.role != Role.ADMIN && !Objects.equals(j.recruiterId, me.id)) throw new ApiException(403, "You can only manage your own jobs");
        return j;
    }

    private void apply(Job j, JobRequest r, AppUser me) {
        j.title = r.title().trim();
        String company = r.company();
        if (company == null || company.isBlank()) {
            company = recruiters.findByUserId(me.id).map(x -> x.companyName).orElse(me.name);
        }
        j.company = company.trim();
        j.description = r.description().length() > 8000 ? r.description().substring(0, 8000) : r.description();
        j.requiredSkills = Csv.join(catalog.normalize(r.requiredSkills()));
        j.location = r.location();
        j.employmentType = r.employmentType() == null || r.employmentType().isBlank() ? "Full-time" : r.employmentType();
        j.minExperience = r.minExperience() == null ? 0 : Math.max(0, r.minExperience());
        j.externalUrl = r.externalUrl();
        if (r.active() != null) j.active = r.active();
    }
}
