package com.nixk.recruit.service;

import com.nixk.recruit.model.CandidateProfile;
import com.nixk.recruit.model.Job;
import java.util.*;
import org.springframework.stereotype.Service;

/** Explainable, rule-based candidate-job compatibility scoring (weights are admin-configurable). */
@Service
public class MatchingService {
    public record MatchResult(int score, List<String> matched, List<String> missing,
                              List<String> reasons, Map<String, Integer> breakdown) {}

    private static final Set<String> STOP = Set.of("and", "the", "for", "of", "a", "in", "at", "sr", "jr", "senior", "junior", "with", "to");

    private final SkillCatalog catalog;
    private final SettingsService settings;

    public MatchingService(SkillCatalog catalog, SettingsService settings) {
        this.catalog = catalog;
        this.settings = settings;
    }

    public MatchResult compute(CandidateProfile p, Job j) {
        return compute(p, j, settings.weights());
    }

    public MatchResult compute(CandidateProfile p, Job j, Map<String, Double> w) {
        Set<String> have = Csv.lower(catalog.normalize(p.skills));
        Set<String> evidence = Csv.lower(catalog.extract(Csv.nz(p.projects) + "\n" + Csv.nz(p.experience)));
        List<String> required = catalog.normalize(j.requiredSkills);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int inEvidence = 0;
        for (String r : required) {
            String k = r.toLowerCase(Locale.ROOT);
            if (have.contains(k) || evidence.contains(k)) matched.add(r); else missing.add(r);
            if (evidence.contains(k)) inEvidence++;
        }

        double skillScore = required.isEmpty() ? 0.5 : (double) matched.size() / required.size();
        double projectScore = required.isEmpty()
                ? (Csv.nz(p.projects).isBlank() ? 0 : 0.5)
                : (double) inEvidence / required.size();

        double minExp = j.minExperience == null ? 0 : j.minExperience;
        double myExp = p.experienceYears == null ? 0 : p.experienceYears;
        double expScore = minExp <= 0 ? 1 : Math.min(1, myExp / minExp);

        Set<String> myRoleText = new HashSet<>();
        String roleText = (Csv.nz(p.preferredRoles) + " " + Csv.nz(p.headline) + " " + Csv.nz(p.experience)).toLowerCase(Locale.ROOT);
        for (String t : roleText.split("[^a-z0-9+#]+")) if (!t.isEmpty()) myRoleText.add(t);
        List<String> titleTokens = new ArrayList<>();
        for (String t : Csv.nz(j.title).toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+")) {
            if (t.length() > 1 && !STOP.contains(t)) titleTokens.add(t);
        }
        int hits = 0;
        for (String t : titleTokens) if (myRoleText.contains(t)) hits++;
        double roleScore = titleTokens.isEmpty() ? 0.5 : (double) hits / titleTokens.size();

        double eduScore = 0;
        if (!Csv.nz(p.education).isBlank()) eduScore += 0.6;
        if (!Csv.nz(p.certifications).isBlank()) eduScore += 0.4;

        double total = w.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) total = 1;
        double weighted = w.get("skills") * skillScore + w.get("experience") * expScore + w.get("projects") * projectScore
                + w.get("role") * roleScore + w.get("education") * eduScore;
        int score = (int) Math.round(100.0 * weighted / total);

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("Required skills", pct(skillScore));
        breakdown.put("Experience", pct(expScore));
        breakdown.put("Projects / evidence", pct(projectScore));
        breakdown.put("Role relevance", pct(roleScore));
        breakdown.put("Education & certifications", pct(eduScore));

        List<String> reasons = new ArrayList<>();
        if (!required.isEmpty()) reasons.add("Matches " + matched.size() + " of " + required.size() + " required skills.");
        if (minExp > 0) reasons.add(String.format(Locale.ROOT, "Experience: %.1f yrs vs %.1f yrs required.", myExp, minExp));
        if (!missing.isEmpty()) reasons.add("Missing: " + Csv.join(missing) + ".");
        if (inEvidence > 0) reasons.add(inEvidence + " required skill(s) are backed by your projects/experience.");
        if (roleScore >= 0.5) reasons.add("Your headline/preferred roles align with the job title.");
        return new MatchResult(Math.max(0, Math.min(100, score)), matched, missing, reasons, breakdown);
    }

    private static int pct(double d) {
        return (int) Math.round(d * 100);
    }
}
