package com.nixk.recruit.service;

import java.util.List;

/** Target roles used by the Career Trajectory feature. */
public final class RoleCatalog {
    private RoleCatalog() {}

    public record RoleDef(String name, List<String> skills, List<String> keywords, List<String> projects) {}

    public static final List<RoleDef> ROLES = List.of(
        new RoleDef("Backend Developer",
            List.of("Java", "Spring Boot", "REST API", "SQL", "PostgreSQL", "Git", "Docker", "Microservices", "JUnit", "Hibernate", "Spring Security"),
            List.of("backend", "java", "spring", "server"),
            List.of("Build a REST API with JWT auth, PostgreSQL and Docker, then deploy it", "Add unit/integration tests and caching to an existing service")),
        new RoleDef("Full Stack Developer",
            List.of("JavaScript", "React", "Node.js", "REST API", "SQL", "HTML", "CSS", "Git", "Docker", "TypeScript"),
            List.of("full stack", "fullstack", "full-stack", "web developer"),
            List.of("Build and deploy a full-stack CRUD app with auth", "Add real-time features (WebSockets) to a side project")),
        new RoleDef("Frontend Developer",
            List.of("HTML", "CSS", "JavaScript", "React", "TypeScript", "Git", "Tailwind CSS", "Figma", "REST API"),
            List.of("frontend", "front-end", "front end", "ui developer"),
            List.of("Recreate a popular site's UI as a responsive React app", "Build a dashboard consuming a public REST API")),
        new RoleDef("Data Analyst",
            List.of("Python", "SQL", "Excel", "Pandas", "Tableau", "Power BI", "Statistics", "Data Analysis"),
            List.of("data analyst", "analytics", "business analyst"),
            List.of("Analyse a public dataset and publish a Tableau/Power BI dashboard", "Write SQL case studies on a sample database")),
        new RoleDef("Data Scientist",
            List.of("Python", "Machine Learning", "Pandas", "NumPy", "Scikit-learn", "Statistics", "SQL", "Deep Learning"),
            List.of("data scientist", "data science"),
            List.of("End-to-end ML project: data cleaning, model, evaluation, deployment", "Kaggle competition write-up")),
        new RoleDef("Machine Learning Engineer",
            List.of("Python", "Machine Learning", "Deep Learning", "TensorFlow", "PyTorch", "NLP", "Docker", "Git"),
            List.of("machine learning", "ml engineer", "ai engineer"),
            List.of("Deploy a model behind an API with Docker", "Fine-tune a small NLP model and document results")),
        new RoleDef("DevOps Engineer",
            List.of("Docker", "Kubernetes", "AWS", "Jenkins", "Linux", "Git", "Terraform", "CI/CD", "Ansible"),
            List.of("devops", "sre", "site reliability", "platform engineer"),
            List.of("Set up a CI/CD pipeline that builds, tests and deploys a container", "Provision cloud infra with Terraform")),
        new RoleDef("QA / Test Automation Engineer",
            List.of("Selenium", "Java", "JUnit", "Postman", "SQL", "Git", "Jira", "Agile"),
            List.of("qa", "test", "quality assurance", "sdet"),
            List.of("Automate UI tests for a public web app with Selenium", "Create a Postman collection with automated API tests")),
        new RoleDef("Android Developer",
            List.of("Kotlin", "Java", "Android", "Git", "REST API", "SQL"),
            List.of("android", "mobile"),
            List.of("Publish a small Android app using a REST API", "Add offline storage and tests to an app")),
        new RoleDef("Cloud Engineer",
            List.of("AWS", "Azure", "GCP", "Linux", "Docker", "Terraform", "Networking"),
            List.of("cloud"),
            List.of("Host a 3-tier app on AWS with infrastructure-as-code", "Earn a cloud associate certification"))
    );
}
