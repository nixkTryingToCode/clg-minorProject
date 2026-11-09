package com.nixk.recruit.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Official documentation / learning hubs for common skills. */
public final class LearningResources {
    private LearningResources() {}

    private static final Map<String, String> LINKS = Map.ofEntries(
        Map.entry("Java", "https://dev.java/learn/"),
        Map.entry("Spring Boot", "https://spring.io/guides"),
        Map.entry("Spring", "https://spring.io/guides"),
        Map.entry("Hibernate", "https://hibernate.org/orm/documentation/"),
        Map.entry("Python", "https://docs.python.org/3/tutorial/"),
        Map.entry("JavaScript", "https://developer.mozilla.org/en-US/docs/Learn/JavaScript"),
        Map.entry("TypeScript", "https://www.typescriptlang.org/docs/"),
        Map.entry("SQL", "https://www.postgresql.org/docs/current/tutorial.html"),
        Map.entry("PostgreSQL", "https://www.postgresql.org/docs/current/tutorial.html"),
        Map.entry("MySQL", "https://dev.mysql.com/doc/refman/8.0/en/tutorial.html"),
        Map.entry("MongoDB", "https://learn.mongodb.com/"),
        Map.entry("Redis", "https://redis.io/learn"),
        Map.entry("React", "https://react.dev/learn"),
        Map.entry("Angular", "https://angular.dev/tutorials"),
        Map.entry("Vue.js", "https://vuejs.org/tutorial/"),
        Map.entry("Node.js", "https://nodejs.org/en/learn"),
        Map.entry("HTML", "https://developer.mozilla.org/en-US/docs/Learn/HTML"),
        Map.entry("CSS", "https://developer.mozilla.org/en-US/docs/Learn/CSS"),
        Map.entry("Tailwind CSS", "https://tailwindcss.com/docs"),
        Map.entry("Docker", "https://docs.docker.com/get-started/"),
        Map.entry("Kubernetes", "https://kubernetes.io/docs/tutorials/"),
        Map.entry("AWS", "https://aws.amazon.com/getting-started/"),
        Map.entry("Azure", "https://learn.microsoft.com/training/azure/"),
        Map.entry("GCP", "https://cloud.google.com/learn"),
        Map.entry("Git", "https://git-scm.com/book"),
        Map.entry("Terraform", "https://developer.hashicorp.com/terraform/tutorials"),
        Map.entry("Linux", "https://linuxjourney.com/"),
        Map.entry("Jenkins", "https://www.jenkins.io/doc/tutorials/"),
        Map.entry("JUnit", "https://junit.org/junit5/docs/current/user-guide/"),
        Map.entry("Selenium", "https://www.selenium.dev/documentation/"),
        Map.entry("Postman", "https://learning.postman.com/"),
        Map.entry("Kafka", "https://kafka.apache.org/quickstart"),
        Map.entry("Machine Learning", "https://scikit-learn.org/stable/tutorial/index.html"),
        Map.entry("Deep Learning", "https://pytorch.org/tutorials/"),
        Map.entry("PyTorch", "https://pytorch.org/tutorials/"),
        Map.entry("TensorFlow", "https://www.tensorflow.org/tutorials"),
        Map.entry("Pandas", "https://pandas.pydata.org/docs/getting_started/index.html"),
        Map.entry("Tableau", "https://www.tableau.com/learn/training"),
        Map.entry("Power BI", "https://learn.microsoft.com/power-bi/fundamentals/"),
        Map.entry("Kotlin", "https://kotlinlang.org/docs/getting-started.html"),
        Map.entry("Android", "https://developer.android.com/courses"),
        Map.entry("REST API", "https://restfulapi.net/"),
        Map.entry("Microservices", "https://microservices.io/")
    );

    public static String link(String skill) {
        String l = LINKS.get(skill);
        if (l != null) return l;
        return "https://www.freecodecamp.org/news/search?query=" + URLEncoder.encode(skill, StandardCharsets.UTF_8);
    }
}
