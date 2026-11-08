package com.nixk.recruit.service;

import com.nixk.recruit.model.AppUser;
import com.nixk.recruit.model.CandidateProfile;
import com.nixk.recruit.model.Job;
import java.util.List;
import org.springframework.stereotype.Service;

/** Template-based cover letter draft built ONLY from facts already in the candidate's profile (no invented claims). */
@Service
public class CoverLetterService {
    private final MatchingService matching;

    public CoverLetterService(MatchingService matching) {
        this.matching = matching;
    }

    public String draft(AppUser user, CandidateProfile p, Job job) {
        var m = matching.compute(p, job);
        StringBuilder sb = new StringBuilder();
        sb.append("Dear Hiring Team at ").append(Csv.nz(job.company).isBlank() ? "your company" : job.company).append(",\n\n");
        sb.append("I am writing to apply for the ").append(job.title).append(" position. ");
        if (!Csv.nz(p.headline).isBlank()) sb.append("I am a ").append(p.headline).append(". ");
        if (!Csv.nz(p.summary).isBlank()) sb.append(p.summary.trim()).append(" ");
        sb.append("\n\n");
        List<String> matched = m.matched();
        if (!matched.isEmpty()) {
            sb.append("My profile covers ").append(matched.size()).append(" of the skills you are looking for, including ")
              .append(Csv.join(matched.subList(0, Math.min(6, matched.size())))).append(". ");
        }
        if (p.experienceYears != null && p.experienceYears > 0) {
            sb.append("I bring ").append(p.experienceYears).append(" year(s) of relevant experience. ");
        }
        if (!Csv.nz(p.projects).isBlank()) {
            String first = p.projects.trim().split("\n")[0];
            sb.append("Among my projects: ").append(first.length() > 160 ? first.substring(0, 160) + "..." : first).append("\n\n");
        } else {
            sb.append("\n\n");
        }
        if (!m.missing().isEmpty()) {
            sb.append("I am also actively building skills in ").append(Csv.join(m.missing().subList(0, Math.min(3, m.missing().size())))).append(".\n\n");
        }
        sb.append("I would welcome the opportunity to discuss how I can contribute to your team.\n\nSincerely,\n").append(user.name);
        return sb.toString();
    }
}
