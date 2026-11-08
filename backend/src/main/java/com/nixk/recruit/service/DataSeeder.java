package com.nixk.recruit.service;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the admin account and (optionally) demo recruiter, candidate and jobs on first start. */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepo users;
    private final RecruiterProfileRepo recruiters;
    private final CandidateProfileRepo candidates;
    private final JobRepo jobs;
    private final PasswordEncoder encoder;
    private final SkillCatalog catalog;
    @Value("${app.admin-email}") private String adminEmail;
    @Value("${app.admin-password}") private String adminPassword;
    @Value("${app.seed-demo}") private boolean seedDemo;
    @Value("${app.demo-password}") private String demoPassword;

    public DataSeeder(UserRepo users, RecruiterProfileRepo recruiters, CandidateProfileRepo candidates, JobRepo jobs,
                      PasswordEncoder encoder, SkillCatalog catalog) {
        this.users = users;
        this.recruiters = recruiters;
        this.candidates = candidates;
        this.jobs = jobs;
        this.encoder = encoder;
        this.catalog = catalog;
    }

    @Override
    public void run(String... args) {
        if (!users.existsByEmail(adminEmail.toLowerCase())) {
            makeUser("Platform Admin", adminEmail.toLowerCase(), adminPassword, Role.ADMIN);
            log.info("Created admin account {}", adminEmail);
        }
        if (!seedDemo || jobs.count() > 0) return;

        AppUser rec = makeUser("Riya Sharma", "recruiter@demo.com", demoPassword, Role.RECRUITER);
        RecruiterProfile rp = new RecruiterProfile();
        rp.userId = rec.id;
        rp.companyName = "TechNova Solutions";
        rp.website = "https://technova.example.com";
        rp.location = "Bengaluru";
        rp.designation = "Talent Acquisition Lead";
        recruiters.save(rp);

        AppUser cand = makeUser("Aman Verma", "candidate@demo.com", demoPassword, Role.CANDIDATE);
        CandidateProfile cp = new CandidateProfile();
        cp.userId = cand.id;
        cp.headline = "Java Backend Developer";
        cp.location = "Delhi";
        cp.phone = "+91 90000 00000";
        cp.experienceYears = 0.5;
        cp.summary = "Final-year CSE student who enjoys building REST APIs with Spring Boot and PostgreSQL, looking for a backend developer role.";
        cp.preferredRoles = "Backend Developer, Java Developer";
        cp.skills = Csv.join(catalog.normalize("Java, Spring Boot, MySQL, PostgreSQL, REST API, Git, Hibernate, Data Structures, OOP"));
        cp.education = "B.Tech Computer Science and Engineering, GGSIPU, 2023-2027";
        cp.experience = "Java Developer Intern - built REST endpoints with Spring Boot and wrote JUnit tests for service classes.";
        cp.projects = "Library Management System - Spring Boot, MySQL, REST API with role-based access.\nExpense Tracker - Java, Hibernate, PostgreSQL.";
        cp.certifications = "Oracle Java SE certification (in progress)";
        candidates.save(cp);

        job(rec, "Java Backend Developer", "Build and maintain Spring Boot microservices powering our hiring products. Work with PostgreSQL, write clean tested code and participate in code reviews.",
                "Java, Spring Boot, REST API, PostgreSQL, Git, Hibernate, JUnit", "Bengaluru", "Full-time", 1.0);
        job(rec, "Junior Java Developer (Fresher)", "Great first role for graduates. Learn Spring Boot, SQL and REST design while shipping real features with a mentor.",
                "Java, SQL, OOP, Data Structures, Git, Spring Boot", "Remote", "Full-time", 0.0);
        job(rec, "Full Stack Developer", "Own features end to end across a React frontend and Node.js/REST backend.",
                "JavaScript, React, Node.js, REST API, SQL, HTML, CSS, Git", "Hyderabad", "Full-time", 2.0);
        job(rec, "Frontend Developer Intern", "Build responsive UI components in React and collaborate with designers.",
                "HTML, CSS, JavaScript, React, Git", "Remote", "Internship", 0.0);
        job(rec, "Data Analyst", "Turn raw data into insight using SQL, Python and dashboards for business teams.",
                "Python, SQL, Excel, Pandas, Tableau, Statistics", "Gurugram", "Full-time", 1.0);
        job(rec, "DevOps Engineer", "Automate builds and deployments, manage containers and cloud infrastructure.",
                "Docker, Kubernetes, AWS, Jenkins, Linux, Git, CI/CD", "Pune", "Full-time", 2.0);
        job(rec, "QA Automation Engineer", "Design and automate API and UI tests for our platform.",
                "Selenium, Java, JUnit, Postman, SQL, Jira", "Noida", "Full-time", 1.0);
        job(rec, "Machine Learning Engineer", "Build and deploy NLP models that power resume understanding.",
                "Python, Machine Learning, Deep Learning, PyTorch, NLP, Docker", "Bengaluru", "Full-time", 2.0);
        log.info("Seeded demo recruiter, candidate and jobs (password from DEMO_PASSWORD)");
    }

    private AppUser makeUser(String name, String email, String password, Role role) {
        AppUser u = new AppUser();
        u.name = name;
        u.email = email;
        u.role = role;
        u.passwordHash = encoder.encode(password);
        return users.save(u);
    }

    private void job(AppUser rec, String title, String desc, String skills, String loc, String type, double exp) {
        Job j = new Job();
        j.recruiterId = rec.id;
        j.source = "DEMO";
        j.title = title;
        j.company = "TechNova Solutions";
        j.description = desc;
        j.requiredSkills = Csv.join(catalog.normalize(skills));
        j.location = loc;
        j.employmentType = type;
        j.minExperience = exp;
        jobs.save(j);
    }
}
