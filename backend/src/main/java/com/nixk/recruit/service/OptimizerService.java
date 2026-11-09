package com.nixk.recruit.service;

import com.nixk.recruit.model.AppUser;
import com.nixk.recruit.model.CandidateProfile;
import com.nixk.recruit.model.Job;
import com.nixk.recruit.service.MatchingService.MatchResult;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Job-specific / role-specific resume optimisation. It only RE-ORDERS and EMPHASISES what is already in the profile
 * and never invents experience, skills, certifications or projects.
 */
@Service
public class OptimizerService {
    public record Optimized(List<String> suggestions, String resumeText, MatchResult match, String coverLetter) {}

    private final MatchingService matching;
    private final CoverLetterService coverLetters;
    private final SkillCatalog catalog;

    public OptimizerService(MatchingService matching, CoverLetterService coverLetters, SkillCatalog catalog) {
        this.matching = matching;
        this.coverLetters = coverLetters;
        this.catalog = catalog;
    }

    public Optimized forJob(AppUser user, CandidateProfile p, Job job) {
        MatchResult m = matching.compute(p, job);
        Set<String> emphasis = Csv.lower(catalog.normalize(job.requiredSkills));
        String text = build(user, p, emphasis, "Application for: " + job.title + (Csv.nz(job.company).isBlank() ? "" : " at " + job.company));

        List<String> tips = new ArrayList<>();
        if (!m.matched().isEmpty()) {
            tips.add("Lead with your matching skills: " + Csv.join(m.matched().subList(0, Math.min(6, m.matched().size()))) + ".");
        }
        List<String> projectHits = new ArrayList<>();
        for (String line : Csv.nz(p.projects).split("\n")) {
            String l = line.toLowerCase(Locale.ROOT);
            for (String s : emphasis) if (l.contains(s)) { projectHits.add(line.trim()); break; }
        }
        if (!projectHits.isEmpty()) tips.add(projectHits.size() + " of your projects use this job's skills; they are listed first in the optimised resume. Add measurable results (users, speed-ups, test coverage).");
        if (!m.missing().isEmpty()) {
            tips.add("The job asks for " + Csv.join(m.missing()) + ", which is not in your profile. Add a skill ONLY if you genuinely have it (e.g. from a project or course) — the optimiser never adds skills for you.");
        }
        Set<String> have = Csv.lower(catalog.normalize(p.skills));
        List<String> jdOnly = new ArrayList<>();
        for (String s : catalog.extract(job.description)) {
            if (!have.contains(s.toLowerCase(Locale.ROOT)) && !m.missing().contains(s)) jdOnly.add(s);
        }
        if (!jdOnly.isEmpty()) tips.add("Other keywords in the job description you don't list: " + Csv.join(jdOnly) + ".");
        if (Csv.nz(p.summary).isBlank()) tips.add("Add a 2-3 sentence summary tailored to this role.");
        if (Csv.nz(p.experience).isBlank()) tips.add("Add an internship or experience entry with responsibilities and results.");
        if (m.score() < 50) tips.add("Compatibility is " + m.score() + "%. Consider building the missing skills first (see the learning links), or apply to closer matches.");
        return new Optimized(tips, text, m, coverLetters.draft(user, p, job));
    }

    public String forRole(AppUser user, CandidateProfile p, RoleCatalog.RoleDef role) {
        return build(user, p, Csv.lower(role.skills()), "Target role: " + role.name());
    }

    private String build(AppUser u, CandidateProfile p, Set<String> emphasisLower, String targetLine) {
        StringBuilder sb = new StringBuilder();
        sb.append(u.name.toUpperCase(Locale.ROOT)).append("\n");
        List<String> contact = new ArrayList<>();
        if (!Csv.nz(p.headline).isBlank()) contact.add(p.headline);
        if (!Csv.nz(p.location).isBlank()) contact.add(p.location);
        if (!Csv.nz(p.phone).isBlank()) contact.add(p.phone);
        contact.add(u.email);
        sb.append(String.join(" | ", contact)).append("\n");
        sb.append(targetLine).append("\n\n");
        if (!Csv.nz(p.summary).isBlank()) sb.append("SUMMARY\n").append(p.summary.trim()).append("\n\n");

        List<String> skills = catalog.normalize(p.skills);
        List<String> key = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String s : skills) (emphasisLower.contains(s.toLowerCase(Locale.ROOT)) ? key : other).add(s);
        if (!key.isEmpty()) sb.append("KEY SKILLS FOR THIS ROLE\n").append(Csv.join(key)).append("\n\n");
        if (!other.isEmpty()) sb.append("OTHER SKILLS\n").append(Csv.join(other)).append("\n\n");
        if (!Csv.nz(p.experience).isBlank()) sb.append("EXPERIENCE\n").append(p.experience.trim()).append("\n\n");
        if (!Csv.nz(p.projects).isBlank()) {
            List<String> lines = new ArrayList<>();
            for (String l : p.projects.split("\n")) if (!l.isBlank()) lines.add(l.trim());
            lines.sort(Comparator.comparing((String l) -> !mentions(l, emphasisLower)));
            sb.append("PROJECTS\n").append(String.join("\n", lines)).append("\n\n");
        }
        if (!Csv.nz(p.education).isBlank()) sb.append("EDUCATION\n").append(p.education.trim()).append("\n\n");
        if (!Csv.nz(p.certifications).isBlank()) sb.append("CERTIFICATIONS & ACHIEVEMENTS\n").append(p.certifications.trim()).append("\n");
        return sb.toString().trim();
    }

    private static boolean mentions(String line, Set<String> emphasisLower) {
        String l = line.toLowerCase(Locale.ROOT);
        for (String s : emphasisLower) if (l.contains(s)) return true;
        return false;
    }
}
