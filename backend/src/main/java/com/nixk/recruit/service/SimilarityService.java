package com.nixk.recruit.service;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Lightweight semantic similarity (no external model needed): a TF-weighted cosine over the words of both texts,
 * blended with skill-graph coverage (related skills count, e.g. Spring Boot implies Java/Spring/REST).
 * The interface is deliberately small so it can later be swapped for neural embeddings / pgvector.
 */
@Service
public class SimilarityService {
    private static final Pattern SPLIT = Pattern.compile("[^a-z0-9+#]+");
    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "you", "our", "are", "will", "have", "has", "from",
            "that", "this", "your", "their", "can", "all", "not", "but", "use", "using", "used", "work", "working", "team",
            "teams", "role", "job", "experience", "years", "year", "strong", "good", "ability", "etc", "such", "into", "also", "who");

    private final SkillCatalog catalog;

    public SimilarityService(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public double similarity(String profileText, String jobText) {
        Map<String, Double> a = vector(profileText);
        Map<String, Double> b = vector(jobText);
        double cos = cosine(a, b);
        Set<String> profileSkills = SkillGraph.expand(catalog.extract(profileText));
        Set<String> jobSkills = Csv.lower(catalog.extract(jobText));
        double coverage = 0;
        if (!jobSkills.isEmpty()) {
            int hit = 0;
            for (String s : jobSkills) if (profileSkills.contains(s)) hit++;
            coverage = (double) hit / jobSkills.size();
        }
        double scaledCos = Math.min(1.0, cos * 2.0);
        return Math.max(0, Math.min(1, 0.5 * scaledCos + 0.5 * coverage));
    }

    private Map<String, Double> vector(String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String t : SPLIT.split(Csv.nz(text).toLowerCase(Locale.ROOT))) {
            if (t.length() < 3 || STOP.contains(t)) continue;
            counts.merge(t, 1, Integer::sum);
        }
        Map<String, Double> v = new HashMap<>();
        for (var e : counts.entrySet()) v.put(e.getKey(), 1 + Math.log(e.getValue()));
        return v;
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        double dot = 0;
        for (var e : a.entrySet()) {
            Double o = b.get(e.getKey());
            if (o != null) dot += e.getValue() * o;
        }
        double na = 0, nb = 0;
        for (double x : a.values()) na += x * x;
        for (double x : b.values()) nb += x * x;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
