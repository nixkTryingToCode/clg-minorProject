package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.*;
import com.nixk.recruit.service.MatchingService.MatchResult;
import com.nixk.recruit.service.ProfileScorer.ScoreReport;
import com.nixk.recruit.service.ResumeParser.ParsedResume;
import com.nixk.recruit.service.TrajectoryService.Trajectory;
import com.nixk.recruit.web.Dtos.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/candidate")
@PreAuthorize("hasRole('CANDIDATE')")
public class CandidateController {
    private final ProfileService profiles;
    private final ResumeParser parser;
    private final ProfileScorer scorer;
    private final SkillCatalog catalog;
    private final TrajectoryService trajectories;
    private final MatchingService matching;
    private final SettingsService settings;
    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final AuditService audit;

    public CandidateController(ProfileService profiles, ResumeParser parser, ProfileScorer scorer, SkillCatalog catalog,
                               TrajectoryService trajectories, MatchingService matching, SettingsService settings,
                               JobRepo jobs, ApplicationRepo applications, AuditService audit) {
        this.profiles = profiles;
        this.parser = parser;
        this.scorer = scorer;
        this.catalog = catalog;
        this.trajectories = trajectories;
        this.matching = matching;
        this.settings = settings;
        this.jobs = jobs;
        this.applications = applications;
        this.audit = audit;
    }

    @GetMapping("/profile")
    public CandidateProfile profile() {
        return profiles.getOrCreate(CurrentUser.get().id);
    }

    @PutMapping("/profile")
    public Map<String, Object> update(@RequestBody ProfileRequest r) {
        CandidateProfile p = profiles.getOrCreate(CurrentUser.get().id);
        p.headline = clip(r.headline(), 250);
        p.phone = clip(r.phone(), 250);
        p.location = clip(r.location(), 250);
        p.experienceYears = r.experienceYears() == null ? null : Math.max(0, Math.min(50, r.experienceYears()));
        p.summary = clip(r.summary(), 2000);
        p.preferredRoles = clip(r.preferredRoles(), 1000);
        p.skills = clip(Csv.join(catalog.normalize(r.skills())), 3000);
        p.education = clip(r.education(), 5000);
        p.experience = clip(r.experience(), 8000);
        p.projects = clip(r.projects(), 8000);
        p.certifications = clip(r.certifications(), 3000);
        if (r.resumeText() != null) {
            p.resumeText = clip(r.resumeText(), 20000);
            p.resumeFileName = clip(r.resumeFileName(), 250);
        }
        p.profileSource = "RESUME_CONFIRMED".equals(r.profileSource()) ? "RESUME_CONFIRMED" : (p.profileSource == null ? "MANUAL" : p.profileSource);
        p = profiles.save(p);
        audit.log(CurrentUser.get().id, "PROFILE_UPDATE", "CandidateProfile", p.id, "source=" + p.profileSource);
        return Map.of("profile", p, "score", scorer.score(p));
    }

    /** Parses a resume and returns a DRAFT. Nothing is saved until the candidate reviews and saves the profile. */
    @PostMapping("/resume/parse")
    public ParsedResume parse(@RequestParam("file") MultipartFile file) {
        ParsedResume parsed = parser.parse(file);
        audit.log(CurrentUser.get().id, "RESUME_PARSED", "CandidateProfile", null, parsed.fileName());
        return parsed;
    }

    @GetMapping("/score")
    public ScoreReport score() {
        return scorer.score(profiles.getOrCreate(CurrentUser.get().id));
    }

    @GetMapping("/trajectories")
    public List<Trajectory> trajectories() {
        return trajectories.generate(profiles.getOrCreate(CurrentUser.get().id));
    }

    @GetMapping("/recommendations")
    public List<JobView> recommendations() {
        Long uid = CurrentUser.get().id;
        CandidateProfile p = profiles.getOrCreate(uid);
        Map<Long, Long> applied = new HashMap<>();
        for (JobApplication a : applications.findByCandidateIdOrderByCreatedAtDesc(uid)) applied.put(a.jobId, a.id);
        Map<String, Double> w = settings.weights();
        List<JobView> out = new ArrayList<>();
        for (Job j : jobs.findByActiveTrueOrderByCreatedAtDesc()) {
            MatchResult m = matching.compute(p, j, w);
            out.add(new JobView(j, m, applied.containsKey(j.id), applied.get(j.id)));
        }
        out.sort(Comparator.comparingInt((JobView v) -> v.match().score()).reversed());
        return out.size() > 20 ? out.subList(0, 20) : out;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
