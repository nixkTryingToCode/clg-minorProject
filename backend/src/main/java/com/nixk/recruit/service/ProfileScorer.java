package com.nixk.recruit.service;

import com.nixk.recruit.model.CandidateProfile;
import java.util.*;
import org.springframework.stereotype.Service;

/** Explainable 0-100 profile quality score (a project-defined score, not an official ATS score). */
@Service
public class ProfileScorer {
    public record ScoreReport(int score, Map<String, Integer> categories, Map<String, Integer> maxPoints, List<String> suggestions) {}

    private final SkillCatalog catalog;

    public ProfileScorer(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public ScoreReport score(CandidateProfile p) {
        Map<String, Integer> cat = new LinkedHashMap<>();
        Map<String, Integer> max = new LinkedHashMap<>();
        List<String> tips = new ArrayList<>();

        int basics = 0;
        if (!Csv.nz(p.headline).isBlank()) basics += 4; else tips.add("Add a professional headline (e.g. 'Java Backend Developer').");
        if (!Csv.nz(p.location).isBlank()) basics += 3; else tips.add("Add your location so location-based jobs can match you.");
        if (!Csv.nz(p.phone).isBlank()) basics += 2;
        if (Csv.nz(p.summary).length() >= 60) basics += 6; else tips.add("Write a summary of at least 2-3 sentences about your goals and strengths.");
        cat.put("Basics", basics); max.put("Basics", 15);

        int skillCount = catalog.normalize(p.skills).size();
        int skills = Math.min(25, (int) Math.round(25.0 * Math.min(skillCount, 10) / 10.0));
        if (skillCount < 8) tips.add("List at least 8 relevant skills (you have " + skillCount + ").");
        cat.put("Skills", skills); max.put("Skills", 25);

        int exp = 0;
        String e = Csv.nz(p.experience);
        if (e.length() >= 100) exp = 20; else if (e.length() >= 30) exp = 10;
        if (exp < 20) tips.add("Describe your experience or internships with responsibilities and results (use numbers where possible).");
        cat.put("Experience", exp); max.put("Experience", 20);

        int proj = 0;
        String pr = Csv.nz(p.projects);
        if (pr.length() >= 150) proj = 15; else if (pr.length() >= 40) proj = 8;
        if (proj < 15) tips.add("Add 2-3 projects with the tech stack used and what you achieved.");
        cat.put("Projects", proj); max.put("Projects", 15);

        int edu = Csv.nz(p.education).length() >= 20 ? 15 : 0;
        if (edu == 0) tips.add("Add your education (degree, institution, year, CGPA).");
        cat.put("Education", edu); max.put("Education", 15);

        int cert = Csv.nz(p.certifications).length() >= 10 ? 10 : 0;
        if (cert == 0) tips.add("Add certifications, courses or achievements to strengthen credibility.");
        cat.put("Certifications", cert); max.put("Certifications", 10);

        int total = basics + skills + exp + proj + edu + cert;
        return new ScoreReport(total, cat, max, tips);
    }
}
