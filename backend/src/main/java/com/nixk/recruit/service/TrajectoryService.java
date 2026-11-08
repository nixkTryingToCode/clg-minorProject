package com.nixk.recruit.service;

import com.nixk.recruit.model.CandidateProfile;
import com.nixk.recruit.model.Job;
import com.nixk.recruit.repo.JobRepo;
import java.util.*;
import org.springframework.stereotype.Service;

/** Stage-1 Career Trajectory: several role roadmaps with fit, skill gaps and next steps. */
@Service
public class TrajectoryService {
    public record Trajectory(String role, int fit, List<String> strengths, List<String> missingSkills,
                             List<String> suggestedProjects, boolean preferred, List<Long> openJobIds) {}

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
            boolean preferred = prefs.contains(role.name().toLowerCase(Locale.ROOT).split(" /")[0])
                    || role.keywords().stream().anyMatch(prefs::contains);
            List<Long> jobIds = new ArrayList<>();
            for (Job j : active) {
                String title = Csv.nz(j.title).toLowerCase(Locale.ROOT);
                if (title.contains(role.name().toLowerCase(Locale.ROOT).split(" /")[0])
                        || role.keywords().stream().anyMatch(title::contains)) {
                    jobIds.add(j.id);
                    if (jobIds.size() == 3) break;
                }
            }
            out.add(new Trajectory(role.name(), fit, strengths, missing, role.projects(), preferred, jobIds));
        }
        out.sort(Comparator.comparing(Trajectory::preferred).reversed()
                .thenComparing(Comparator.comparingInt(Trajectory::fit).reversed()));
        return out.size() > 5 ? out.subList(0, 5) : out;
    }
}
