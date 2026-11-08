package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
import com.nixk.recruit.service.MatchingService.MatchResult;
import com.nixk.recruit.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final RecruiterProfileRepo recruiters;
    private final ProfileService profiles;
    private final MatchingService matching;
    private final SettingsService settings;
    private final SkillCatalog catalog;
    private final CoverLetterService coverLetters;
    private final AuditService audit;

    public JobController(JobRepo jobs, ApplicationRepo applications, RecruiterProfileRepo recruiters, ProfileService profiles,
                         MatchingService matching, SettingsService settings, SkillCatalog catalog,
                         CoverLetterService coverLetters, AuditService audit) {
        this.jobs = jobs;
        this.applications = applications;
        this.recruiters = recruiters;
        this.profiles = profiles;
        this.matching = matching;
        this.settings = settings;
        this.catalog = catalog;
        this.coverLetters = coverLetters;
        this.audit = audit;
    }

    @GetMapping
    public List<JobView> list(@RequestParam(required = false) String q, @RequestParam(required = false) String location,
                              @RequestParam(required = false) String type, @RequestParam(required = false) String skill) {
        AppUser me = CurrentUser.get();
        CandidateProfile p = me.role == Role.CANDIDATE ? profiles.getOrCreate(me.id) : null;
        Map<Long, Long> applied = new HashMap<>();
        if (p != null) for (JobApplication a : applications.findByCandidateIdOrderByCreatedAtDesc(me.id)) applied.put(a.jobId, a.id);
        Map<String, Double> w = settings.weights();
        List<JobView> out = new ArrayList<>();
        for (Job j : jobs.findByActiveTrueOrderByCreatedAtDesc()) {
            if (!matches(j, q, location, type, skill)) continue;
            MatchResult m = p == null ? null : matching.compute(p, j, w);
            out.add(new JobView(j, m, applied.containsKey(j.id), applied.get(j.id)));
        }
        if (p != null) out.sort(Comparator.comparingInt((JobView v) -> v.match().score()).reversed());
        return out;
    }

    private boolean matches(Job j, String q, String location, String type, String skill) {
        if (q != null && !q.isBlank()) {
            String hay = (Csv.nz(j.title) + " " + Csv.nz(j.company) + " " + Csv.nz(j.description)).toLowerCase(Locale.ROOT);
            if (!hay.contains(q.trim().toLowerCase(Locale.ROOT))) return false;
        }
        if (location != null && !location.isBlank() && !Csv.nz(j.location).toLowerCase(Locale.ROOT).contains(location.trim().toLowerCase(Locale.ROOT))) return false;
        if (type != null && !type.isBlank() && !type.equalsIgnoreCase(j.employmentType)) return false;
        if (skill != null && !skill.isBlank() && !Csv.lower(catalog.normalize(j.requiredSkills)).contains(catalog.normalize(skill).stream().findFirst().orElse(skill).toLowerCase(Locale.ROOT))) return false;
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

    @PostMapping("/{id}/cover-letter")
    @PreAuthorize("hasRole('CANDIDATE')")
    public Map<String, String> coverLetter(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        Job j = jobs.findById(id).orElseThrow(() -> new ApiException(404, "Job not found"));
        return Map.of("coverLetter", coverLetters.draft(me, profiles.getOrCreate(me.id), j));
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
