package com.nixk.recruit.service;

import java.util.ArrayList;
import java.util.List;

/** Demo dataset: many different candidate / job / application / evidence combinations for demonstrations and evaluation. */
public final class DemoData {
    private DemoData() {}

    /** Company admin accounts created by the seeder (seed version 3). */
    public static final List<String> COMPANY_ACCOUNTS = List.of("owner@demo.com", "zenith@demo.com");

    /** Every email owned by the demo dataset; the seeder keeps their passwords aligned with DEMO_PASSWORD. */
    public static List<String> demoEmails() {
        List<String> emails = new ArrayList<>();
        RECRUITERS.forEach(r -> emails.add(r.email()));
        PERSONAS.forEach(p -> emails.add(p.email()));
        emails.addAll(COMPANY_ACCOUNTS);
        return emails;
    }

    public record Persona(String email, String name, String headline, String location, double years, String summary,
                          String preferredRoles, String skills, String education, String experience, String projects,
                          String certifications, boolean open, List<String> relevant) {}

    public record JobSpec(String recruiter, String title, String desc, String skills, String location, String type,
                          double minExp, boolean active) {}

    public record ExternalJob(String id, String title, String company, String desc, String skills, String location, String url) {}

    public record Recruiter(String email, String name, String company, String website, String location, String designation) {}

    public record Evi(String email, String type, String title, String url, String skill, String status, String by, String meta) {}

    public static final List<Recruiter> RECRUITERS = List.of(
        new Recruiter("recruiter@demo.com", "Riya Sharma", "TechNova Solutions", "https://technova.example.com", "Bengaluru", "Talent Acquisition Lead"),
        new Recruiter("recruiter2@demo.com", "Meera Kulkarni", "FinEdge Capital", "https://finedge.example.com", "Mumbai", "Senior Recruiter"),
        new Recruiter("recruiter3@demo.com", "Sanjay Rao", "CloudKart", "https://cloudkart.example.com", "Hyderabad", "Head of Engineering Hiring"));

    public static final List<Persona> PERSONAS = List.of(
        new Persona("candidate@demo.com", "Aman Verma", "Java Backend Developer", "Delhi", 0.5,
            "Final-year CSE student who enjoys building REST APIs with Spring Boot and PostgreSQL, looking for a backend developer role.",
            "Backend Developer, Java Developer",
            "Java, Spring Boot, MySQL, PostgreSQL, REST API, Git, Hibernate, Data Structures, OOP",
            "B.Tech Computer Science and Engineering, GGSIPU, 2023-2027",
            "Java Developer Intern - built REST endpoints with Spring Boot and wrote JUnit tests for service classes.",
            "Library Management System - Spring Boot, MySQL, REST API with role-based access.\nExpense Tracker - Java, Hibernate, PostgreSQL.",
            "Oracle Java SE certification (in progress)", true, List.of("java", "backend")),
        new Persona("priya.nair@demo.com", "Priya Nair", "Senior Backend Engineer", "Bengaluru", 5.5,
            "Backend engineer with 5+ years building high-throughput payment and order services in Java and Spring Boot. Comfortable owning services end to end, from design to on-call.",
            "Backend Developer, Senior Backend Engineer, Tech Lead",
            "Java, Spring Boot, Spring Security, Hibernate, PostgreSQL, Kafka, Microservices, Docker, AWS, REST API, JUnit, Mockito, System Design, Git",
            "B.E. Information Technology, VIT Vellore, 2019",
            "Software Engineer II, PayStream (2021-2024): designed order-settlement microservices on Kafka processing 2M events/day with Spring Boot; cut p99 latency by 40%.\nSoftware Engineer, CodeCraft (2019-2021): built REST APIs in Spring Boot and PostgreSQL, wrote JUnit/Mockito tests (85% coverage).",
            "Event-driven Order Platform - Java, Spring Boot, Kafka, PostgreSQL, Docker on AWS.\nRate-limiter Library - Java, Redis, open source (200+ GitHub stars).",
            "AWS Certified Developer - Associate", true, List.of("backend", "java")),
        new Persona("rohan.mehta@demo.com", "Rohan Mehta", "Full Stack Developer", "Hyderabad", 2.5,
            "Full stack developer who ships polished React front-ends backed by Node.js and SQL APIs. Cares about performance and accessibility.",
            "Full Stack Developer, Frontend Developer",
            "JavaScript, React, Node.js, Express.js, REST API, SQL, PostgreSQL, HTML, CSS, Git, Docker, TypeScript",
            "B.Tech Computer Science, JNTU Hyderabad, 2022",
            "Software Developer, ShopSphere (2022-present): built a React + Node.js seller dashboard used by 5,000 merchants; reduced page load from 4s to 1.5s.",
            "Realtime Chat App - React, Node.js, WebSockets, PostgreSQL.\nPortfolio CMS - Next.js, TypeScript.",
            "Meta Front-End Developer Professional Certificate", true, List.of("full stack", "frontend")),
        new Persona("sneha.kapoor@demo.com", "Sneha Kapoor", "Data Analyst", "Gurugram", 1.5,
            "Data analyst who turns messy operational data into clear dashboards and decisions using SQL, Python and Tableau.",
            "Data Analyst, Business Analyst",
            "Python, SQL, Pandas, Excel, Tableau, Power BI, Statistics, Data Analysis, Communication",
            "B.Sc. Statistics, University of Delhi, 2022; PG Diploma in Data Analytics, 2023",
            "Data Analyst, RetailMax (2023-present): built weekly sales dashboards in Tableau and automated reporting with Pandas, saving 10 analyst-hours per week.",
            "Customer Churn Analysis - Python, Pandas, SQL.\nCOVID Trends Dashboard - Power BI.",
            "Tableau Desktop Specialist", true, List.of("analyst")),
        new Persona("arjun.reddy@demo.com", "Arjun Reddy", "Machine Learning Engineer", "Bengaluru", 3,
            "ML engineer focused on NLP and taking models from notebook to production APIs.",
            "Machine Learning Engineer, Data Scientist",
            "Python, Machine Learning, Deep Learning, TensorFlow, PyTorch, NLP, Scikit-learn, Pandas, NumPy, Docker, Git, SQL",
            "M.Tech Artificial Intelligence, IIIT Hyderabad, 2021",
            "ML Engineer, TextSense (2021-present): shipped a resume-classification NLP model (F1 0.91) served via FastAPI and Docker.",
            "Sentiment Analysis API - PyTorch, FastAPI, Docker.\nMovie Recommender - Scikit-learn, Pandas.",
            "Deep Learning Specialization", true, List.of("machine learning", "data scientist")),
        new Persona("kavya.iyer@demo.com", "Kavya Iyer", "DevOps Engineer", "Pune", 3,
            "DevOps engineer who automates delivery pipelines and keeps Kubernetes clusters boring and reliable.",
            "DevOps Engineer, Site Reliability Engineer",
            "Docker, Kubernetes, AWS, Jenkins, CI/CD, Linux, Terraform, Ansible, Git, Prometheus",
            "B.Tech Electronics and Telecommunication, COEP Pune, 2021",
            "DevOps Engineer, LogiTrack (2021-present): moved 25 services to Kubernetes with Jenkins pipelines; deployment time down from 2 hours to 12 minutes.",
            "Infra-as-Code Starter - Terraform, AWS.\nMonitoring Stack - Prometheus, Docker.",
            "Certified Kubernetes Administrator (CKA)", true, List.of("devops", "cloud")),
        new Persona("vikram.singh@demo.com", "Vikram Singh", "QA Automation Engineer", "Noida", 2,
            "QA automation engineer building reliable UI and API test suites.",
            "QA Automation Engineer, SDET",
            "Selenium, Java, JUnit, Postman, SQL, Jira, Agile, Git, REST API",
            "B.Tech CSE, AKTU, 2022",
            "QA Engineer, FinServe (2022-present): automated 300+ regression tests with Selenium and Java, cutting regression time from 3 days to 4 hours.",
            "Test Automation Framework - Java, Selenium, JUnit, Page Object Model.\nAPI test collection - Postman.",
            "ISTQB Foundation Level", true, List.of("qa", "test")),
        new Persona("ananya.das@demo.com", "Ananya Das", "Frontend Developer", "Kolkata", 0,
            "Recent graduate passionate about clean, accessible interfaces and design systems.",
            "Frontend Developer",
            "HTML, CSS, JavaScript, React, Tailwind CSS, Figma, Git",
            "B.Tech Information Technology, Jadavpur University, 2025",
            "Frontend Intern, PixelCraft (3 months): built reusable React components and fixed 40+ UI bugs.",
            "Recipe Finder - React, public REST API.\nPersonal Portfolio - HTML, CSS, JavaScript, Tailwind CSS.",
            "", true, List.of("frontend")),
        new Persona("mohit.gupta@demo.com", "Mohit Gupta", "Android Developer", "Jaipur", 1,
            "Android developer building Kotlin apps with clean architecture.",
            "Android Developer, Mobile Developer",
            "Kotlin, Java, Android, REST API, Git, SQL",
            "B.Tech CSE, MNIT Jaipur, 2024",
            "Junior Android Developer, AppForge (2024-present): shipped 2 apps with 50k+ installs.",
            "Expense Splitter - Kotlin, Android, Room.\nNews Reader - Kotlin, REST API.",
            "Android Developer Fundamentals (Google)", true, List.of("android", "mobile")),
        new Persona("ishita.joshi@demo.com", "Ishita Joshi", "Aspiring Data Analyst (career switcher)", "Delhi", 0,
            "Mechanical engineer moving into data analytics after completing several online courses and personal projects.",
            "Data Analyst",
            "Python, SQL, Excel",
            "B.Tech Mechanical Engineering, DTU, 2022",
            "",
            "Sales Data Cleanup - Python, Excel.",
            "Google Data Analytics Certificate (in progress)", true, List.of("analyst", "intern")),
        new Persona("new.candidate@demo.com", "Nikhil New", "", "", 0, "", "", "", "", "", "", "", true, List.of()),
        new Persona("rahul.khanna@demo.com", "Rahul Khanna", "Software Developer", "Chandigarh", 0,
            "Developer.",
            "Software Developer",
            "Java, Python, JavaScript, React, Angular, Node.js, Spring Boot, Docker, Kubernetes, AWS, SQL, MongoDB, Machine Learning, TensorFlow",
            "B.Tech CSE, 2024", "", "", "", true, List.of()),
        new Persona("neha.bansal@demo.com", "Neha Bansal", "Engineering Lead - Full Stack", "Mumbai", 8,
            "Engineering lead with 8 years of experience building SaaS products and growing teams of 6-10 engineers. Prefers to stay private while passively exploring roles.",
            "Tech Lead, Senior Full Stack Engineer",
            "JavaScript, TypeScript, React, Node.js, PostgreSQL, AWS, Microservices, System Design, Leadership, Agile, Docker, REST API, Git",
            "B.E. Computer Engineering, Mumbai University, 2016",
            "Engineering Lead, SaaSly (2020-present): led a team of 8 delivering a multi-tenant billing platform; improved uptime to 99.95%.\nSenior Developer, WebWorks (2016-2020): full stack features in React and Node.js.",
            "Multi-tenant Billing Platform - Node.js, PostgreSQL, AWS.\nDesign System - React, TypeScript.",
            "", false, List.of("full stack", "tech lead")),
        new Persona("sameer.ali@demo.com", "Sameer Ali", "Cloud Engineer", "Hyderabad", 2,
            "Cloud engineer designing secure, cost-aware AWS and Azure infrastructure as code.",
            "Cloud Engineer, DevOps Engineer",
            "AWS, Azure, Terraform, Linux, Docker, Networking, Git, CI/CD",
            "B.Tech CSE, Osmania University, 2022",
            "Cloud Engineer, SkyBridge (2022-present): built landing zones with Terraform across 3 AWS accounts; cut monthly cloud spend by 18%.",
            "Serverless Image Pipeline - AWS, Terraform.\nSite-to-site VPN Lab - Networking, Linux.",
            "AWS Solutions Architect - Associate", true, List.of("cloud", "devops")),
        new Persona("divya.menon@demo.com", "Divya Menon", "Spring Boot Developer", "Bengaluru", 1,
            "Backend developer with one year of experience building Spring Boot services; keen to grow into microservices.",
            "Backend Developer, Java Developer",
            "Java, Spring Boot, Hibernate, MySQL, REST API, JUnit, Git, Maven, OOP",
            "B.Tech CSE, RV College of Engineering, 2024",
            "Associate Software Engineer, CartLine (2024-present): built order and inventory REST APIs with Spring Boot and MySQL; added JUnit tests.",
            "Hostel Booking API - Spring Boot, MySQL, Hibernate.\nURL Shortener - Java, Spring Boot, Redis.",
            "", true, List.of("java", "backend")),
        new Persona("karan.malhotra@demo.com", "Karan Malhotra", "Aspiring Data Scientist", "Delhi", 0,
            "Final-year student competing on Kaggle and learning to productionise machine learning models.",
            "Data Scientist, Machine Learning Intern",
            "Python, Machine Learning, Pandas, NumPy, Scikit-learn, Statistics, SQL, Data Analysis",
            "B.Tech CSE, IIIT Delhi, 2026",
            "ML Research Intern, IIIT Delhi lab (4 months): built a text-classification baseline with Scikit-learn.",
            "House Price Prediction - Scikit-learn, Pandas.\nHandwritten Digit Classifier - Python, NumPy.",
            "", true, List.of("data scientist", "machine learning", "analyst")),
        new Persona("pooja.sharma@demo.com", "Pooja Sharma", "Full Stack Developer", "Remote (Lucknow)", 2,
            "Product-minded full stack developer who likes owning features from idea to production.",
            "Full Stack Developer, Frontend Developer",
            "JavaScript, TypeScript, React, Next.js, Node.js, PostgreSQL, REST API, HTML, CSS, Tailwind CSS, Git",
            "B.Tech IT, AKGEC, 2022",
            "Developer, LaunchPad (2022-present): built a Next.js booking product from scratch, 12k monthly users.",
            "Booking Platform - Next.js, PostgreSQL.\nAdmin Dashboard - React, TypeScript.",
            "Meta Front-End Developer Certificate; Full Stack Open (Helsinki)", true, List.of("full stack", "frontend")),
        new Persona("tarun.chawla@demo.com", "Tarun Chawla", "Python Backend Developer", "Gurugram", 2,
            "Python backend developer building APIs with Django and PostgreSQL.",
            "Python Developer, Backend Developer",
            "Python, Django, Flask, REST API, PostgreSQL, Docker, Git, SQL",
            "B.Tech CSE, Amity University, 2022",
            "Backend Developer, DataBridge (2022-present): built Django REST services handling 1M requests/day; containerised with Docker.",
            "Inventory API - Django, PostgreSQL.\nTask Queue Demo - Flask, Redis.",
            "HackerRank Python (Gold)", true, List.of("python", "backend")));

    /** TechNova's first 8 jobs keep the exact titles from the first release so upgrades never duplicate them. */
    public static final List<JobSpec> JOBS = List.of(
        new JobSpec("recruiter@demo.com", "Java Backend Developer", "Build and maintain Spring Boot microservices powering our hiring products. Work with PostgreSQL, write clean tested code and participate in code reviews.", "Java, Spring Boot, REST API, PostgreSQL, Git, Hibernate, JUnit", "Bengaluru", "Full-time", 1.0, true),
        new JobSpec("recruiter@demo.com", "Junior Java Developer (Fresher)", "Great first role for graduates. Learn Spring Boot, SQL and REST design while shipping real features with a mentor.", "Java, SQL, OOP, Data Structures, Git, Spring Boot", "Remote", "Full-time", 0.0, true),
        new JobSpec("recruiter@demo.com", "Full Stack Developer", "Own features end to end across a React frontend and Node.js/REST backend.", "JavaScript, React, Node.js, REST API, SQL, HTML, CSS, Git", "Hyderabad", "Full-time", 2.0, true),
        new JobSpec("recruiter@demo.com", "Frontend Developer Intern", "Build responsive UI components in React and collaborate with designers.", "HTML, CSS, JavaScript, React, Git", "Remote", "Internship", 0.0, true),
        new JobSpec("recruiter@demo.com", "Data Analyst", "Turn raw data into insight using SQL, Python and dashboards for business teams.", "Python, SQL, Excel, Pandas, Tableau, Statistics", "Gurugram", "Full-time", 1.0, true),
        new JobSpec("recruiter@demo.com", "DevOps Engineer", "Automate builds and deployments, manage containers and cloud infrastructure.", "Docker, Kubernetes, AWS, Jenkins, Linux, Git, CI/CD", "Pune", "Full-time", 2.0, true),
        new JobSpec("recruiter@demo.com", "QA Automation Engineer", "Design and automate API and UI tests for our platform.", "Selenium, Java, JUnit, Postman, SQL, Jira", "Noida", "Full-time", 1.0, true),
        new JobSpec("recruiter@demo.com", "Machine Learning Engineer", "Build and deploy NLP models that power resume understanding.", "Python, Machine Learning, Deep Learning, PyTorch, NLP, Docker", "Bengaluru", "Full-time", 2.0, true),
        new JobSpec("recruiter@demo.com", "Spring Boot Developer", "Join our platform team building Spring Boot and Hibernate services on MySQL.", "Java, Spring Boot, Hibernate, MySQL, REST API, Git", "Delhi", "Full-time", 1.0, true),
        new JobSpec("recruiter@demo.com", "SDET", "Build the test automation backbone for our release pipeline: UI, API and performance checks.", "Selenium, Java, Postman, SQL, Git, Agile", "Remote", "Full-time", 2.0, true),

        new JobSpec("recruiter2@demo.com", "Senior Backend Engineer", "Design low-latency trading and settlement services. You will own services end to end, mentor engineers and improve reliability.", "Java, Spring Boot, Microservices, Kafka, PostgreSQL, AWS, Docker", "Mumbai", "Full-time", 4.0, true),
        new JobSpec("recruiter2@demo.com", "Java Developer Intern", "Six-month internship working with senior engineers on internal Java tools.", "Java, SQL, OOP, Git, Data Structures", "Mumbai", "Internship", 0.0, true),
        new JobSpec("recruiter2@demo.com", "Backend Contract Developer", "6-month contract to build REST APIs for a new lending product.", "Java, Spring Boot, REST API, PostgreSQL", "Remote", "Contract", 2.0, true),
        new JobSpec("recruiter2@demo.com", "Risk Data Scientist", "Build credit-risk models and monitor model drift in production.", "Python, Machine Learning, Statistics, SQL, Pandas", "Mumbai", "Full-time", 2.0, true),
        new JobSpec("recruiter2@demo.com", "Data Analyst Intern", "Support the finance analytics team with SQL queries and Power BI dashboards.", "SQL, Excel, Power BI, Python", "Mumbai", "Internship", 0.0, true),
        new JobSpec("recruiter2@demo.com", "QA Lead", "Lead a team of 5 SDETs and own the quality strategy for our payments platform.", "Selenium, Java, JUnit, Postman, Jira, Agile, Leadership", "Pune", "Full-time", 5.0, true),
        new JobSpec("recruiter2@demo.com", "Android Developer", "Build our customer-facing Android banking app in Kotlin.", "Kotlin, Android, REST API, Git", "Mumbai", "Full-time", 1.0, true),
        new JobSpec("recruiter2@demo.com", "Cybersecurity Analyst", "Monitor, triage and respond to security events across cloud and on-prem systems.", "Cybersecurity, Linux, Networking, Python", "Mumbai", "Full-time", 2.0, true),
        new JobSpec("recruiter2@demo.com", "Legacy PHP Developer", "Maintain a legacy PHP CRM (position paused).", "PHP, MySQL, HTML, CSS", "Mumbai", "Full-time", 3.0, false),

        new JobSpec("recruiter3@demo.com", "Cloud Engineer", "Design and run our multi-account AWS and Azure environments using Terraform.", "AWS, Azure, Terraform, Linux, Docker, Networking", "Hyderabad", "Full-time", 2.0, true),
        new JobSpec("recruiter3@demo.com", "Junior DevOps Engineer", "Entry-level role maintaining CI/CD pipelines and container deployments.", "Docker, Linux, Git, Jenkins, CI/CD", "Remote", "Full-time", 0.0, true),
        new JobSpec("recruiter3@demo.com", "Frontend Developer", "Build fast, accessible storefront experiences in React and TypeScript.", "React, TypeScript, Tailwind CSS, HTML, CSS, Git", "Hyderabad", "Full-time", 1.0, true),
        new JobSpec("recruiter3@demo.com", "Next.js Full Stack Developer", "Build customer-facing features with Next.js, Node.js and PostgreSQL.", "Next.js, React, Node.js, PostgreSQL, TypeScript, REST API", "Remote", "Full-time", 2.0, true),
        new JobSpec("recruiter3@demo.com", "Python Backend Developer", "Build Django REST APIs for our logistics platform.", "Python, Django, REST API, PostgreSQL, Docker, Git", "Bengaluru", "Full-time", 2.0, true),
        new JobSpec("recruiter3@demo.com", "Senior Full Stack Engineer", "Lead complex product features across React, Node.js and AWS.", "React, Node.js, TypeScript, AWS, PostgreSQL, System Design, Microservices", "Hyderabad", "Full-time", 6.0, true),
        new JobSpec("recruiter3@demo.com", "Data Engineer", "Build batch and streaming pipelines on Spark and Kafka.", "Python, SQL, Spark, Hadoop, Kafka, AWS", "Hyderabad", "Full-time", 3.0, true),
        new JobSpec("recruiter3@demo.com", "Tech Lead - Platform", "Lead the platform team: architecture, mentoring and delivery.", "Java, Spring Boot, Microservices, System Design, Kubernetes, AWS, Leadership", "Hyderabad", "Full-time", 8.0, true),
        new JobSpec("recruiter3@demo.com", "UI/UX Design Intern", "Help design and prototype new storefront flows in Figma.", "Figma, HTML, CSS, Communication", "Remote", "Internship", 0.0, true),
        new JobSpec("recruiter3@demo.com", "Part-time Python Tutor", "Teach Python to school students for 10 hours a week.", "Python, Communication, Teamwork", "Remote", "Part-time", 0.0, true),
        new JobSpec("recruiter3@demo.com", "Machine Learning Intern", "Work with the data team on demand-forecasting models.", "Python, Machine Learning, NumPy, Pandas", "Remote", "Internship", 0.0, true));

    public static final List<ExternalJob> EXTERNAL = List.of(
        new ExternalJob("demo-ext-1", "Backend Engineer (Java)", "Orbit Labs", "Demo external listing (placeholder link). Build Java services for a logistics start-up.", "Java, Spring Boot, PostgreSQL, Docker, REST API", "Remote", "https://example.com/careers/orbit-backend"),
        new ExternalJob("demo-ext-2", "React Developer", "Nimbus Systems", "Demo external listing (placeholder link). Build dashboards with React and TypeScript.", "React, TypeScript, JavaScript, HTML, CSS, Git", "Bengaluru / Hybrid", "https://example.com/careers/nimbus-react"),
        new ExternalJob("demo-ext-3", "Data Analyst (SQL/Tableau)", "Quanta Retail", "Demo external listing (placeholder link). Analyse retail sales with SQL and Tableau.", "SQL, Tableau, Excel, Python, Statistics", "Gurugram", "https://example.com/careers/quanta-analyst"),
        new ExternalJob("demo-ext-4", "DevOps Engineer (Kubernetes)", "Nimbus Systems", "Demo external listing (placeholder link). Run Kubernetes clusters on AWS.", "Kubernetes, Docker, AWS, Terraform, Linux, CI/CD", "Remote", "https://example.com/careers/nimbus-devops"));

    /** {candidate email, job title, final status} */
    public static final String[][] APPLICATIONS = {
        {"candidate@demo.com", "Java Backend Developer", "SHORTLISTED"},
        {"candidate@demo.com", "Junior Java Developer (Fresher)", "INTERVIEW"},
        {"candidate@demo.com", "Java Developer Intern", "APPLIED"},
        {"candidate@demo.com", "Spring Boot Developer", "SCREENING"},
        {"candidate@demo.com", "Backend Contract Developer", "REJECTED"},
        {"priya.nair@demo.com", "Senior Backend Engineer", "INTERVIEW"},
        {"priya.nair@demo.com", "Tech Lead - Platform", "SCREENING"},
        {"priya.nair@demo.com", "Java Backend Developer", "SELECTED"},
        {"rohan.mehta@demo.com", "Full Stack Developer", "SHORTLISTED"},
        {"rohan.mehta@demo.com", "Next.js Full Stack Developer", "APPLIED"},
        {"rohan.mehta@demo.com", "Frontend Developer", "SCREENING"},
        {"rohan.mehta@demo.com", "Senior Full Stack Engineer", "REJECTED"},
        {"sneha.kapoor@demo.com", "Data Analyst", "INTERVIEW"},
        {"sneha.kapoor@demo.com", "Data Analyst Intern", "SELECTED"},
        {"sneha.kapoor@demo.com", "Risk Data Scientist", "APPLIED"},
        {"arjun.reddy@demo.com", "Machine Learning Engineer", "SHORTLISTED"},
        {"arjun.reddy@demo.com", "Risk Data Scientist", "INTERVIEW"},
        {"arjun.reddy@demo.com", "Data Engineer", "APPLIED"},
        {"kavya.iyer@demo.com", "DevOps Engineer", "SELECTED"},
        {"kavya.iyer@demo.com", "Cloud Engineer", "SHORTLISTED"},
        {"kavya.iyer@demo.com", "Junior DevOps Engineer", "WITHDRAWN"},
        {"vikram.singh@demo.com", "QA Automation Engineer", "INTERVIEW"},
        {"vikram.singh@demo.com", "SDET", "SHORTLISTED"},
        {"vikram.singh@demo.com", "QA Lead", "REJECTED"},
        {"ananya.das@demo.com", "Frontend Developer Intern", "SELECTED"},
        {"ananya.das@demo.com", "Frontend Developer", "SCREENING"},
        {"ananya.das@demo.com", "UI/UX Design Intern", "APPLIED"},
        {"mohit.gupta@demo.com", "Android Developer", "APPLIED"},
        {"ishita.joshi@demo.com", "Data Analyst Intern", "SCREENING"},
        {"ishita.joshi@demo.com", "Data Analyst", "APPLIED"},
        {"ishita.joshi@demo.com", "Part-time Python Tutor", "SHORTLISTED"},
        {"rahul.khanna@demo.com", "Full Stack Developer", "REJECTED"},
        {"rahul.khanna@demo.com", "Spring Boot Developer", "APPLIED"},
        {"rahul.khanna@demo.com", "Frontend Developer", "REJECTED"},
        {"neha.bansal@demo.com", "Senior Full Stack Engineer", "INTERVIEW"},
        {"neha.bansal@demo.com", "Tech Lead - Platform", "APPLIED"},
        {"sameer.ali@demo.com", "Cloud Engineer", "SCREENING"},
        {"karan.malhotra@demo.com", "Machine Learning Intern", "SHORTLISTED"},
        {"karan.malhotra@demo.com", "Risk Data Scientist", "APPLIED"},
        {"pooja.sharma@demo.com", "Full Stack Developer", "SCREENING"},
        {"pooja.sharma@demo.com", "Next.js Full Stack Developer", "SHORTLISTED"},
        {"pooja.sharma@demo.com", "Frontend Developer", "INTERVIEW"},
        {"tarun.chawla@demo.com", "Python Backend Developer", "INTERVIEW"},
        {"tarun.chawla@demo.com", "Java Backend Developer", "REJECTED"},
        {"tarun.chawla@demo.com", "Backend Contract Developer", "APPLIED"}
    };

    /** {candidate email, external job title} tracked as externally applied */
    public static final String[][] EXTERNAL_TRACKED = {
        {"candidate@demo.com", "Backend Engineer (Java)"},
        {"ananya.das@demo.com", "React Developer"},
        {"sneha.kapoor@demo.com", "Data Analyst (SQL/Tableau)"}
    };

    public static final List<Evi> EVIDENCE = List.of(
        new Evi("priya.nair@demo.com", "GITHUB", "GitHub: priya-nair-dev", "https://github.com/priya-nair-dev", "Java", "VERIFIED", "AUTO:GITHUB_API", "Demo data: GitHub account exists; public repos: 34; followers: 120"),
        new Evi("priya.nair@demo.com", "CERTIFICATE", "AWS Certified Developer - Associate", "https://example.com/certs/aws-dev-assoc", "AWS", "VERIFIED", "ADMIN", "Credential ID checked by admin (demo data)"),
        new Evi("priya.nair@demo.com", "LEETCODE", "LeetCode profile (450+ solved)", "https://leetcode.com/priya-nair-dev", "Data Structures", "PENDING", null, null),
        new Evi("candidate@demo.com", "GITHUB", "GitHub: aman-verma-dev", "https://github.com/aman-verma-dev", "Java", "PENDING", null, null),
        new Evi("candidate@demo.com", "CERTIFICATE", "Oracle Java SE (in progress)", "https://example.com/certs/oracle-java-se", "Java", "REJECTED", "ADMIN", "Certificate not issued yet - re-submit once you have it"),
        new Evi("rohan.mehta@demo.com", "GITHUB", "GitHub: rohan-mehta-dev", "https://github.com/rohan-mehta-dev", "React", "VERIFIED", "AUTO:GITHUB_API", "Demo data: GitHub account exists; public repos: 21; followers: 56"),
        new Evi("rohan.mehta@demo.com", "PORTFOLIO", "Portfolio website", "https://rohan-mehta.example.com", null, "PENDING", null, null),
        new Evi("sneha.kapoor@demo.com", "KAGGLE", "Kaggle profile (Expert)", "https://www.kaggle.com/snehakapoor", "Data Analysis", "VERIFIED", "ADMIN", "Profile checked by admin (demo data)"),
        new Evi("sneha.kapoor@demo.com", "CERTIFICATE", "Tableau Desktop Specialist", "https://example.com/certs/tableau-ds", "Tableau", "VERIFIED", "ADMIN", "Credential checked by admin (demo data)"),
        new Evi("arjun.reddy@demo.com", "KAGGLE", "Kaggle Competitions Master", "https://www.kaggle.com/arjunreddy", "Machine Learning", "VERIFIED", "ADMIN", "Profile checked by admin (demo data)"),
        new Evi("arjun.reddy@demo.com", "GITHUB", "GitHub: arjun-reddy-ml", "https://github.com/arjun-reddy-ml", "PyTorch", "VERIFIED", "AUTO:GITHUB_API", "Demo data: GitHub account exists; public repos: 18; followers: 80"),
        new Evi("arjun.reddy@demo.com", "CERTIFICATE", "Deep Learning Specialization", "https://example.com/certs/dl-spec", "Deep Learning", "PENDING", null, null),
        new Evi("kavya.iyer@demo.com", "CERTIFICATE", "CKA - Certified Kubernetes Administrator", "https://example.com/certs/cka", "Kubernetes", "VERIFIED", "ADMIN", "Credential checked by admin (demo data)"),
        new Evi("kavya.iyer@demo.com", "GITHUB", "GitHub: kavya-iyer-devops", "https://github.com/kavya-iyer-devops", "Terraform", "PENDING", null, null),
        new Evi("rahul.khanna@demo.com", "LINKEDIN", "LinkedIn profile", "https://www.linkedin.com/in/rahul-khanna-demo", null, "PENDING", null, null),
        new Evi("neha.bansal@demo.com", "LINKEDIN", "LinkedIn profile", "https://www.linkedin.com/in/neha-bansal-demo", "Leadership", "VERIFIED", "ADMIN", "Profile checked by admin (demo data)"),
        new Evi("neha.bansal@demo.com", "PORTFOLIO", "Engineering blog", "https://blog.neha-bansal.example.com", "System Design", "VERIFIED", "ADMIN", "Content reviewed by admin (demo data)"),
        new Evi("pooja.sharma@demo.com", "CERTIFICATE", "Meta Front-End Developer Certificate", "https://example.com/certs/meta-fe", "React", "REJECTED", "ADMIN", "Certificate link did not resolve (demo data)"),
        new Evi("sameer.ali@demo.com", "CERTIFICATE", "AWS Solutions Architect - Associate", "https://example.com/certs/aws-saa", "AWS", "VERIFIED", "ADMIN", "Credential checked by admin (demo data)"),
        new Evi("karan.malhotra@demo.com", "KAGGLE", "Kaggle profile (Contributor)", "https://www.kaggle.com/karanmalhotra", "Machine Learning", "PENDING", null, null),
        new Evi("divya.menon@demo.com", "GITHUB", "GitHub: divya-menon-dev", "https://github.com/divya-menon-dev", "Spring Boot", "VERIFIED", "AUTO:GITHUB_API", "Demo data: GitHub account exists; public repos: 9; followers: 14"),
        new Evi("tarun.chawla@demo.com", "HACKERRANK", "HackerRank Python (Gold)", "https://www.hackerrank.com/tarunchawla", "Python", "PENDING", null, null),
        new Evi("tarun.chawla@demo.com", "GITHUB", "GitHub: tarun-chawla-py", "https://github.com/tarun-chawla-py", "Django", "PENDING", null, null));
}
