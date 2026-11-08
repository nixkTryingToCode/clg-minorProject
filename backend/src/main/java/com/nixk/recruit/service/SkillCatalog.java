package com.nixk.recruit.service;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Skill taxonomy with alias normalisation. Format: "Canonical|alias1|alias2" (aliases lower-case). */
@Component
public class SkillCatalog {
    private static final String[] DATA = {
        "Java|java", "Spring Boot|spring boot|springboot", "Spring|spring framework", "Spring MVC|spring mvc",
        "Spring Security|spring security", "Hibernate|hibernate|jpa", "Python|python", "JavaScript|javascript|es6",
        "TypeScript|typescript", "C++|c++", "C#|c#", "C|c programming", "Go|golang", "Kotlin|kotlin", "PHP|php",
        "Ruby|ruby", "SQL|sql", "MySQL|mysql", "PostgreSQL|postgresql|postgres", "MongoDB|mongodb|mongo",
        "Redis|redis", "HTML|html|html5", "CSS|css|css3", "React|react|reactjs|react.js", "Angular|angular|angularjs",
        "Vue.js|vue|vuejs|vue.js", "Next.js|next.js|nextjs", "Node.js|node.js|nodejs|node",
        "Express.js|expressjs|express.js|express js", "Tailwind CSS|tailwind|tailwind css", "Bootstrap|bootstrap",
        "REST API|rest api|rest apis|restful|restful api|restful apis", "GraphQL|graphql",
        "Microservices|microservices|microservice", "Docker|docker", "Kubernetes|kubernetes|k8s",
        "AWS|aws|amazon web services", "Azure|azure", "GCP|gcp|google cloud", "Git|git", "GitHub|github",
        "CI/CD|ci/cd|cicd", "Jenkins|jenkins", "Linux|linux", "Terraform|terraform", "Ansible|ansible",
        "Maven|maven", "Gradle|gradle", "JUnit|junit", "Mockito|mockito", "Selenium|selenium", "Postman|postman",
        "Jira|jira", "Kafka|kafka", "RabbitMQ|rabbitmq", "Nginx|nginx", "Swagger|swagger|openapi", "JWT|jwt",
        "WebSockets|websocket|websockets", "Data Structures|data structures|dsa|data structures and algorithms",
        "Algorithms|algorithms", "OOP|oop|oops|object oriented programming|object-oriented programming",
        "System Design|system design", "Multithreading|multithreading|concurrency", "DBMS|dbms",
        "Operating Systems|operating systems", "Networking|computer networks|networking",
        "Machine Learning|machine learning", "Deep Learning|deep learning", "NLP|nlp|natural language processing",
        "TensorFlow|tensorflow", "PyTorch|pytorch", "Scikit-learn|scikit-learn|sklearn", "Pandas|pandas",
        "NumPy|numpy", "Data Analysis|data analysis|data analytics", "Tableau|tableau", "Power BI|power bi|powerbi",
        "Excel|ms excel|microsoft excel|advanced excel", "Statistics|statistics", "Hadoop|hadoop", "Spark|apache spark|pyspark",
        "Flask|flask", "Django|django", "FastAPI|fastapi", "Android|android", "Flutter|flutter",
        "React Native|react native", "Figma|figma", "Cybersecurity|cybersecurity|cyber security",
        "Agile|agile|scrum", "Communication|communication", "Problem Solving|problem solving|problem-solving",
        "Teamwork|teamwork|team player", "Leadership|leadership"
    };

    private final Map<String, String> aliasToCanonical = new LinkedHashMap<>();
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();
    private final List<String> canonicals = new ArrayList<>();

    public SkillCatalog() {
        for (String row : DATA) {
            String[] parts = row.split("\\|");
            canonicals.add(parts[0]);
            aliasToCanonical.put(parts[0].toLowerCase(Locale.ROOT), parts[0]);
            for (int i = 1; i < parts.length; i++) {
                String k = parts[i].toLowerCase(Locale.ROOT).trim();
                aliasToCanonical.put(k, parts[0]);
                patterns.put(k, Pattern.compile("(?<![a-z0-9+#])" + Pattern.quote(k) + "(?![a-z0-9+#])"));
            }
        }
    }

    public List<String> all() {
        return canonicals;
    }

    /** Finds known skills mentioned in free text. */
    public List<String> extract(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (var e : patterns.entrySet()) {
            if (e.getValue().matcher(lower).find()) found.add(aliasToCanonical.get(e.getKey()));
        }
        return new ArrayList<>(found);
    }

    /** Normalises a comma separated list of skills to canonical names, de-duplicated. */
    public List<String> normalize(String csv) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : Csv.split(csv)) {
            String key = raw.toLowerCase(Locale.ROOT);
            String canon = aliasToCanonical.getOrDefault(key, raw);
            out.putIfAbsent(canon.toLowerCase(Locale.ROOT), canon);
        }
        return new ArrayList<>(out.values());
    }
}
