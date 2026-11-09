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
import java.time.Instant;
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
    private final EvidenceRepo evidenceRepo;
    private final EvidenceService evidenceService;
    private final AutoApplyRuleRepo ruleRepo;
    private final AutoApplyService autoApply;
    private final ResumeVersionRepo versions;
    private final OptimizerService optimizer;

    public CandidateController(ProfileService profiles, ResumeParser parser, ProfileScorer scorer, SkillCatalog catalog,
                               TrajectoryService trajectories, MatchingService matching, SettingsService settings,
                               JobRepo jobs, ApplicationRepo applications, AuditService audit, EvidenceRepo evidenceRepo,
                               EvidenceService evidenceService, AutoApplyRuleRepo ruleRepo, AutoApplyService autoApply,
                               ResumeVersionRepo versions, OptimizerService optimizer) {
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
        this.evidenceRepo = evidenceRepo;
        this.evidenceService = evidenceService;
        this.ruleRepo = ruleRepo;
        this.autoApply = autoApply;
        this.versions = versions;
        this.optimizer = optimizer;
    }

    /* ---------------- profile ---------------- */

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
        if (r.openToRecruiters() != null) p.openToRecruiters = r.openToRecruiters();
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
        return out.size() > 20 ? new ArrayList<>(out.subList(0, 20)) : out;
    }

    /* ---------------- evidence / verification ---------------- */

    @GetMapping("/evidence")
    public Map<String, Object> evidence() {
        Long uid = CurrentUser.get().id;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", evidenceRepo.findByCandidateIdOrderByCreatedAtDesc(uid));
        out.put("evidenceScore", evidenceService.evidenceScore(uid));
        return out;
    }

    @PostMapping("/evidence")
    public Evidence addEvidence(@RequestBody EvidenceRequest r) {
        return evidenceService.add(CurrentUser.get().id, r.type(), r.title(), r.url(), r.skill());
    }

    @DeleteMapping("/evidence/{id}")
    public Map<String, String> deleteEvidence(@PathVariable Long id) {
        Evidence e = evidenceRepo.findById(id).orElseThrow(() -> new ApiException(404, "Evidence not found"));
        if (!e.candidateId.equals(CurrentUser.get().id)) throw new ApiException(403, "Not your evidence");
        evidenceRepo.delete(e);
        audit.log(CurrentUser.get().id, "EVIDENCE_REMOVED", "Evidence", id, e.title);
        return Map.of("status", "deleted");
    }

    /* ---------------- auto-apply ---------------- */

    @GetMapping("/auto-apply")
    public AutoApplyRule rule() {
        Long uid = CurrentUser.get().id;
        return ruleRepo.findByCandidateId(uid).orElseGet(() -> {
            AutoApplyRule r = new AutoApplyRule();
            r.candidateId = uid;
            return r;
        });
    }

    @PutMapping("/auto-apply")
    public AutoApplyRule saveRule(@RequestBody AutoApplyRequest r) {
        Long uid = CurrentUser.get().id;
        AutoApplyRule rule = ruleRepo.findByCandidateId(uid).orElseGet(AutoApplyRule::new);
        rule.candidateId = uid;
        rule.enabled = Boolean.TRUE.equals(r.enabled());
        rule.minScore = r.minScore() == null ? 70 : Math.max(30, Math.min(100, r.minScore()));
        rule.maxPerDay = r.maxPerDay() == null ? 3 : Math.max(1, Math.min(10, r.maxPerDay()));
        rule.locations = clip(r.locations(), 250);
        rule.jobTypes = clip(r.jobTypes(), 250);
        rule.roleKeywords = clip(r.roleKeywords(), 250);
        rule.updatedAt = Instant.now();
        rule = ruleRepo.save(rule);
        audit.log(uid, "AUTO_APPLY_RULE_SAVED", "AutoApplyRule", rule.id, "enabled=" + rule.enabled + " min=" + rule.minScore);
        return rule;
    }

    @PostMapping("/auto-apply/run")
    public Map<String, Object> runNow() {
        List<String> applied = autoApply.runForCandidate(CurrentUser.get().id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applied", applied);
        out.put("count", applied.size());
        return out;
    }

    /* ---------------- resume versions ---------------- */

    @GetMapping("/resume-versions")
    public List<ResumeVersion> resumeVersions() {
        return versions.findByCandidateIdOrderByCreatedAtDesc(CurrentUser.get().id);
    }

    /** Creates a trajectory-specific resume version that emphasises the skills of the chosen role. */
    @PostMapping("/resume-versions/role")
    public ResumeVersion roleVersion(@RequestBody Map<String, String> body) {
        AppUser me = CurrentUser.get();
        RoleCatalog.RoleDef def = RoleCatalog.byName(body.getOrDefault("role", ""));
        if (def == null) throw new ApiException(400, "Unknown role");
        ResumeVersion rv = new ResumeVersion();
        rv.candidateId = me.id;
        rv.targetRole = def.name();
        rv.title = "Trajectory resume: " + def.name();
        rv.content = optimizer.forRole(me, profiles.getOrCreate(me.id), def);
        rv = versions.save(rv);
        audit.log(me.id, "RESUME_VERSION_CREATED", "ResumeVersion", rv.id, rv.title);
        return rv;
    }

    @DeleteMapping("/resume-versions/{id}")
    public Map<String, String> deleteVersion(@PathVariable Long id) {
        ResumeVersion rv = versions.findById(id).orElseThrow(() -> new ApiException(404, "Resume version not found"));
        if (!rv.candidateId.equals(CurrentUser.get().id)) throw new ApiException(403, "Not your resume version");
        versions.delete(rv);
        return Map.of("status", "deleted");
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
