package com.nixk.recruit.service;

import com.nixk.recruit.model.AppUser;
import com.nixk.recruit.model.CandidateProfile;
import com.nixk.recruit.model.Job;
import com.nixk.recruit.repo.CandidateProfileRepo;
import com.nixk.recruit.repo.JobRepo;
import com.nixk.recruit.repo.UserRepo;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Academic validation of the matching engine on the labelled demo personas: for each persona the job titles that a
 * human would call "relevant" are known (DemoData.Persona.relevant); we measure how well the ranking surfaces them.
 */
@Service
public class EvaluationService {
    private static final int K = 3;
    private final UserRepo users;
    private final CandidateProfileRepo profiles;
    private final JobRepo jobs;
    private final MatchingService matching;
    private final SettingsService settings;

    public EvaluationService(UserRepo users, CandidateProfileRepo profiles, JobRepo jobs, MatchingService matching, SettingsService settings) {
        this.users = users;
        this.profiles = profiles;
        this.jobs = jobs;
        this.matching = matching;
        this.settings = settings;
    }

    public Map<String, Object> run() {
        Map<String, Double> w = settings.weights();
        List<Job> active = new ArrayList<>();
        for (Job j : jobs.findByActiveTrueOrderByCreatedAtDesc()) if (!"EXTERNAL".equals(j.source)) active.add(j);

        List<Map<String, Object>> rows = new ArrayList<>();
        double sumP = 0, sumMrr = 0, sumNdcg = 0, sumHit = 0;
        for (DemoData.Persona persona : DemoData.PERSONAS) {
            if (persona.relevant().isEmpty()) continue;
            AppUser u = users.findByEmail(persona.email()).orElse(null);
            if (u == null) continue;
            CandidateProfile p = profiles.findByUserId(u.id).orElse(null);
            if (p == null) continue;

            List<Job> ranked = new ArrayList<>(active);
            Map<Long, Integer> scores = new HashMap<>();
            for (Job j : ranked) scores.put(j.id, matching.compute(p, j, w).score());
            ranked.sort(Comparator.comparingInt((Job j) -> scores.get(j.id)).reversed());

            int totalRelevant = 0;
            for (Job j : ranked) if (isRelevant(persona, j)) totalRelevant++;
            if (totalRelevant == 0) continue;

            int hits = 0;
            double dcg = 0;
            int firstRank = 0;
            List<String> top = new ArrayList<>();
            for (int i = 0; i < Math.min(5, ranked.size()); i++) {
                Job j = ranked.get(i);
                boolean rel = isRelevant(persona, j);
                if (i < K) {
                    top.add(j.title + " (" + scores.get(j.id) + "%)" + (rel ? " ✓" : ""));
                    if (rel) hits++;
                }
                if (rel) {
                    dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
                    if (firstRank == 0) firstRank = i + 1;
                }
            }
            if (firstRank == 0) {
                for (int i = 5; i < ranked.size(); i++) if (isRelevant(persona, ranked.get(i))) { firstRank = i + 1; break; }
            }
            double idcg = 0;
            for (int i = 0; i < Math.min(5, totalRelevant); i++) idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            double pAtK = (double) hits / K;
            double mrr = firstRank == 0 ? 0 : 1.0 / firstRank;
            double ndcg = idcg == 0 ? 0 : dcg / idcg;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("candidate", persona.name());
            row.put("expectedKeywords", persona.relevant());
            row.put("relevantJobs", totalRelevant);
            row.put("top3", top);
            row.put("precisionAt3", round(pAtK));
            row.put("hitAt3", hits > 0);
            row.put("mrr", round(mrr));
            row.put("ndcgAt5", round(ndcg));
            rows.add(row);
            sumP += pAtK;
            sumMrr += mrr;
            sumNdcg += ndcg;
            if (hits > 0) sumHit++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        int n = rows.size();
        out.put("personasEvaluated", n);
        out.put("meanPrecisionAt3", n == 0 ? 0 : round(sumP / n));
        out.put("hitRateAt3", n == 0 ? 0 : round(sumHit / n));
        out.put("meanMRR", n == 0 ? 0 : round(sumMrr / n));
        out.put("meanNdcgAt5", n == 0 ? 0 : round(sumNdcg / n));
        out.put("method", "Binary relevance: a job is relevant when its title contains a keyword the persona's target role implies. Metrics: Precision@3, Hit-rate@3, MRR, NDCG@5.");
        out.put("rows", rows);
        return out;
    }

    private static boolean isRelevant(DemoData.Persona p, Job j) {
        String t = Csv.nz(j.title).toLowerCase(Locale.ROOT);
        for (String k : p.relevant()) if (t.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
