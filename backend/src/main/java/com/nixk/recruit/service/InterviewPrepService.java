package com.nixk.recruit.service;

import com.nixk.recruit.model.Job;
import java.util.*;
import org.springframework.stereotype.Service;

/** Interview preparation based on the selected job's required skills and the candidate's skill gaps. */
@Service
public class InterviewPrepService {
    private static final Map<String, List<String>> BANK = Map.ofEntries(
        Map.entry("Java", List.of("Explain the difference between an interface and an abstract class.", "How does HashMap work internally?", "What is the difference between checked and unchecked exceptions?")),
        Map.entry("Spring Boot", List.of("What does @SpringBootApplication do?", "How does dependency injection work in Spring?", "How would you secure a REST API with JWT in Spring Security?")),
        Map.entry("Hibernate", List.of("What is the N+1 select problem and how do you fix it?", "Explain lazy vs eager loading.")),
        Map.entry("SQL", List.of("Explain INNER vs LEFT JOIN with an example.", "What is an index and when can it hurt performance?", "What is the difference between WHERE and HAVING?")),
        Map.entry("PostgreSQL", List.of("What is a transaction isolation level?", "How would you find and fix a slow query?")),
        Map.entry("MySQL", List.of("Difference between InnoDB and MyISAM?", "How do foreign keys work?")),
        Map.entry("REST API", List.of("What makes an API RESTful?", "PUT vs PATCH — when do you use each?", "How do you version and paginate an API?")),
        Map.entry("Microservices", List.of("When would you NOT use microservices?", "How do services communicate and how do you handle failures?")),
        Map.entry("JavaScript", List.of("Explain closures and the event loop.", "What is the difference between var, let and const?")),
        Map.entry("TypeScript", List.of("What are generics and why use them?", "Interface vs type alias?")),
        Map.entry("React", List.of("Explain the virtual DOM and reconciliation.", "When do you use useEffect vs useMemo?", "How do you manage global state?")),
        Map.entry("Node.js", List.of("How does the Node.js event loop handle async I/O?", "How do you handle errors in async code?")),
        Map.entry("Python", List.of("Difference between a list and a tuple?", "What are decorators and generators?")),
        Map.entry("Docker", List.of("Image vs container?", "How do you reduce a Docker image's size?")),
        Map.entry("Kubernetes", List.of("Pod vs Deployment vs Service?", "How does a rolling update work?")),
        Map.entry("AWS", List.of("EC2 vs Lambda — how do you choose?", "How would you design a highly available web app on AWS?")),
        Map.entry("Git", List.of("Merge vs rebase?", "How do you resolve a merge conflict?")),
        Map.entry("Data Structures", List.of("When would you use a heap or a trie?", "Compare array, linked list and hash-table lookups.")),
        Map.entry("OOP", List.of("Explain the four pillars of OOP with examples.", "Composition vs inheritance?")),
        Map.entry("Machine Learning", List.of("Explain overfitting and how to prevent it.", "Precision vs recall — when does each matter?")),
        Map.entry("Selenium", List.of("How do you handle dynamic elements and waits?", "What is the Page Object Model?")),
        Map.entry("Linux", List.of("How do you find which process uses a port?", "Explain file permissions (chmod 755)."))
    );

    private static final List<String> BEHAVIOURAL = List.of(
        "Tell me about a project you are proud of and your exact contribution.",
        "Describe a time you debugged a hard problem. What was your process?",
        "How do you handle disagreement in a team?");

    public Map<String, Object> prepare(Job job, List<String> missing) {
        List<Map<String, Object>> technical = new ArrayList<>();
        for (String skill : Csv.split(job.requiredSkills)) {
            List<String> qs = BANK.get(skill);
            if (qs == null) qs = List.of("Describe where you have used " + skill + " and one challenge you faced.");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skill", skill);
            row.put("questions", qs);
            row.put("gap", missing.contains(skill));
            technical.add(row);
            if (technical.size() >= 8) break;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("job", job.title);
        out.put("technical", technical);
        out.put("behavioural", BEHAVIOURAL);
        List<String> tips = new ArrayList<>();
        for (String m : missing) tips.add("Be ready to talk honestly about " + m + ": what you know, what you are learning and how you would ramp up. " + LearningResources.link(m));
        out.put("gapTips", tips);
        return out;
    }
}
