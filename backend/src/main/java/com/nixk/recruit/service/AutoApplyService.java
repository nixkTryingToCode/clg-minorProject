package com.nixk.recruit.service;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Controlled automatic application — ONLY for internally hosted jobs (never external platforms), only above the
 * candidate's minimum score, within their daily limit, with duplicate protection and full audit trail.
 */
@Service
public class AutoApplyService {
    private static final Logger log = LoggerFactory.getLogger(AutoApplyService.class);

    private final AutoApplyRuleRepo rules;
    private final UserRepo users;
    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final ProfileService profiles;
    private final MatchingService matching;
    private final SettingsService settings;
    private final ApplicationWorkflow workflow;

    public AutoApplyService(AutoApplyRuleRepo rules, UserRepo users, JobRepo jobs, ApplicationRepo applications,
                            ProfileService profiles, MatchingService matching, SettingsService settings, ApplicationWorkflow workflow) {
        this.rules = rules;
        this.users = users;
        this.jobs = jobs;
        this.applications = applications;
        this.profiles = profiles;
        this.matching = matching;
        this.settings = settings;
        this.workflow = workflow;
    }

    /** Applies to every currently eligible job for one candidate; returns titles applied to. */
    public List<String> runForCandidate(Long userId) {
        List<String> done = new ArrayList<>();
        AutoApplyRule rule = rules.findByCandidateId(userId).orElse(null);
        if (rule == null || !Boolean.TRUE.equals(rule.enabled)) return done;
        AppUser u = users.findById(userId).orElse(null);
        if (u == null || !u.enabled || u.role != Role.CANDIDATE) return done;
        CandidateProfile p = profiles.getOrCreate(userId);
        Map<String, Double> w = settings.weights();
        int[] budget = {remainingToday(rule)};
        for (Job j : jobs.findByActiveTrueOrderByCreatedAtDesc()) {
            if (budget[0] <= 0) break;
            if (tryApply(u, p, rule, j, w)) {
                budget[0]--;
                done.add(j.title);
            }
        }
        return done;
    }

    /** Called when a recruiter publishes a new job. */
    public int runForJob(Job job) {
        int count = 0;
        Map<String, Double> w = settings.weights();
        for (AutoApplyRule rule : rules.findByEnabledTrue()) {
            try {
                AppUser u = users.findById(rule.candidateId).orElse(null);
                if (u == null || !u.enabled || u.role != Role.CANDIDATE) continue;
                if (remainingToday(rule) <= 0) continue;
                if (tryApply(u, profiles.getOrCreate(u.id), rule, job, w)) count++;
            } catch (Exception e) {
                log.warn("Auto-apply skipped for candidate {}: {}", rule.candidateId, e.toString());
            }
        }
        return count;
    }

    public int runAll() {
        int total = 0;
        for (AutoApplyRule rule : rules.findByEnabledTrue()) {
            try {
                total += runForCandidate(rule.candidateId).size();
            } catch (Exception e) {
                log.warn("Auto-apply failed for candidate {}: {}", rule.candidateId, e.toString());
            }
        }
        return total;
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 180_000)
    public void scheduled() {
        try {
            int n = runAll();
            if (n > 0) log.info("Scheduled auto-apply submitted {} application(s)", n);
        } catch (Exception e) {
            log.warn("Scheduled auto-apply failed: {}", e.toString());
        }
    }

    private int remainingToday(AutoApplyRule rule) {
        Instant start = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        int used = 0;
        for (JobApplication a : applications.findByCandidateIdOrderByCreatedAtDesc(rule.candidateId)) {
            if (ApplicationWorkflow.AUTO.equals(a.mode) && !a.createdAt.isBefore(start)) used++;
        }
        int max = rule.maxPerDay == null ? 3 : rule.maxPerDay;
        return Math.max(0, max - used);
    }

    private boolean tryApply(AppUser u, CandidateProfile p, AutoApplyRule rule, Job j, Map<String, Double> w) {
        if (!j.active || "EXTERNAL".equals(j.source)) return false;
        if (applications.findByCandidateIdAndJobId(u.id, j.id).isPresent()) return false;
        if (!allowed(rule.jobTypes, j.employmentType, false)) return false;
        if (!Csv.split(rule.roleKeywords).isEmpty() && !allowed(rule.roleKeywords, j.title, true)) return false;
        List<String> locs = Csv.split(rule.locations);
        if (!locs.isEmpty()) {
            String jl = Csv.nz(j.location).toLowerCase(Locale.ROOT);
            boolean ok = jl.contains("remote");
            for (String l : locs) if (jl.contains(l.toLowerCase(Locale.ROOT))) ok = true;
            if (!ok) return false;
        }
        int min = rule.minScore == null ? 70 : rule.minScore;
        if (matching.compute(p, j, w).score() < min) return false;
        try {
            workflow.submit(u, j, ApplicationWorkflow.AUTO, null, null);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    /** csv list of allowed values; exact match, or "contains" match when substring=true. Empty list = allow all. */
    private static boolean allowed(String csv, String value, boolean substring) {
        List<String> list = Csv.split(csv);
        if (list.isEmpty()) return true;
        String v = Csv.nz(value).toLowerCase(Locale.ROOT);
        for (String s : list) {
            String x = s.toLowerCase(Locale.ROOT);
            if (substring ? v.contains(x) : v.equals(x)) return true;
        }
        return false;
    }
}
