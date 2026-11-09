package com.nixk.recruit.service;

import com.nixk.recruit.model.CandidateProfile;
import com.nixk.recruit.model.Job;
import com.nixk.recruit.repo.JobRepo;
import java.util.*;
import org.springframework.stereotype.Service;

/** Career Trajectory: several role roadmaps with fit, skill gaps, learning links and next steps. */
@Service
public class TrajectoryService {
    public record Trajectory(String role, int fit, List<String> strengths, List<String> missingSkills,
                             List<String> suggestedProjects, boolean preferred, int openJobCount,
                             Map<String, String> learningLinks) {}

    private final SkillCatalog catalog;
    private final JobRepo jobs;

    public TrajectoryService(SkillCatalog catalog, JobRepo jobs) {
        this.catalog = catalog;
        this.jobs = jobs;
    }

    public List<Trajectory> generate(CandidateProfile p) {
        Set<String> have = Csv.lower(catalog.normalize(p.skills));
        have.addAll(Csv.lower(catalog.extract(Csv.nz(p.projects) + "\n" + Csv.nz(p.experience))));
        String prefs = (Csv.nz(p.preferredRoles) + " " + Csv.nz(p.headline)).toLowerCase(Locale.ROOT);
        List<Job> active = jobs.findByActiveTrueOrderByCreatedAtDesc();

        List<Trajectory> out = new ArrayList<>();
        for (var role : RoleCatalog.ROLES) {
            List<String> strengths = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (String s : role.skills()) {
                if (have.contains(s.toLowerCase(Locale.ROOT))) strengths.add(s); else missing.add(s);
            }
            int fit = (int) Math.round(100.0 * strengths.size() / role.skills().size());
            String main = role.name().toLowerCase(Locale.ROOT).split(" /")[0];
            boolean preferred = !prefs.isBlank() && (prefs.contains(main) || role.keywords().stream().anyMatch(prefs::contains));
            int open = 0;
            for (Job j : active) if (RoleCatalog.titleMatches(role, j.title)) open++;
            Map<String, String> links = new LinkedHashMap<>();
            for (String m : missing.subList(0, Math.min(5, missing.size()))) links.put(m, LearningResources.link(m));
            out.add(new Trajectory(role.name(), fit, strengths, missing, role.projects(), preferred, open, links));
        }
        out.sort(Comparator.comparing(Trajectory::preferred).reversed()
                .thenComparing(Comparator.comparingInt(Trajectory::fit).reversed()));
        return out.size() > 5 ? new ArrayList<>(out.subList(0, 5)) : out;
    }
}
