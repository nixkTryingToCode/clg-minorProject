package com.nixk.recruit.service;

import java.util.*;

/** Related-skill graph: having the key skill implies familiarity with the listed ones (used for semantic similarity). */
public final class SkillGraph {
    private SkillGraph() {}

    public static final Map<String, List<String>> RELATED = Map.ofEntries(
        Map.entry("Spring Boot", List.of("Java", "Spring", "REST API", "Hibernate", "Maven")),
        Map.entry("Spring", List.of("Java", "Spring Boot", "Hibernate")),
        Map.entry("Hibernate", List.of("Java", "SQL", "Spring Boot")),
        Map.entry("PostgreSQL", List.of("SQL", "DBMS")),
        Map.entry("MySQL", List.of("SQL", "DBMS")),
        Map.entry("MongoDB", List.of("DBMS")),
        Map.entry("React", List.of("JavaScript", "HTML", "CSS")),
        Map.entry("Next.js", List.of("React", "JavaScript", "Node.js")),
        Map.entry("Angular", List.of("TypeScript", "JavaScript", "HTML", "CSS")),
        Map.entry("Vue.js", List.of("JavaScript", "HTML", "CSS")),
        Map.entry("TypeScript", List.of("JavaScript")),
        Map.entry("Node.js", List.of("JavaScript", "REST API")),
        Map.entry("Express.js", List.of("Node.js", "JavaScript", "REST API")),
        Map.entry("Django", List.of("Python", "REST API")),
        Map.entry("Flask", List.of("Python", "REST API")),
        Map.entry("FastAPI", List.of("Python", "REST API")),
        Map.entry("Kubernetes", List.of("Docker", "Linux")),
        Map.entry("Docker", List.of("Linux")),
        Map.entry("Terraform", List.of("AWS", "Linux")),
        Map.entry("Jenkins", List.of("CI/CD", "Git")),
        Map.entry("GitHub", List.of("Git")),
        Map.entry("Machine Learning", List.of("Python", "Statistics")),
        Map.entry("Deep Learning", List.of("Machine Learning", "Python")),
        Map.entry("TensorFlow", List.of("Deep Learning", "Python")),
        Map.entry("PyTorch", List.of("Deep Learning", "Python")),
        Map.entry("Pandas", List.of("Python", "Data Analysis")),
        Map.entry("NumPy", List.of("Python")),
        Map.entry("Scikit-learn", List.of("Python", "Machine Learning")),
        Map.entry("Kotlin", List.of("Android", "Java")),
        Map.entry("Selenium", List.of("JUnit")),
        Map.entry("Microservices", List.of("REST API", "Docker")),
        Map.entry("Kafka", List.of("Microservices"))
    );

    /** Skills (lower-case) plus everything they imply. */
    public static Set<String> expand(Collection<String> skills) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : skills) {
            out.add(s.toLowerCase(Locale.ROOT));
            for (String r : RELATED.getOrDefault(s, List.of())) out.add(r.toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
