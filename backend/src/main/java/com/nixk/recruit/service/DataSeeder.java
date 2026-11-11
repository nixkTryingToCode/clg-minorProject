package com.nixk.recruit.service;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * First-start setup: admin account + a large, versioned and idempotent demo dataset.
 * Safe to run on a database that already contains the first release's demo data.
 */
@Component
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String SEED_KEY = "demo.seed.version";
    private static final String SEED_VERSION = "3";
    private static final List<ApplicationStatus> PIPELINE = List.of(ApplicationStatus.SCREENING, ApplicationStatus.SHORTLISTED,
            ApplicationStatus.INTERVIEW, ApplicationStatus.SELECTED);

    /** {email, enabled, minScore, maxPerDay, locations, jobTypes, roleKeywords} */
    private static final String[][] RULES = {
        {"sameer.ali@demo.com", "true", "60", "3", "Hyderabad", "", "cloud,devops"},
        {"divya.menon@demo.com", "true", "55", "2", "Bengaluru,Delhi", "Full-time", "java,backend,spring"},
        {"tarun.chawla@demo.com", "true", "65", "2", "", "", "python,backend"},
        {"ananya.das@demo.com", "false", "70", "3", "Remote", "Internship,Full-time", "frontend,react"}
    };

    private final UserRepo users;
    private final RecruiterProfileRepo recruiters;
    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final EvidenceRepo evidenceRepo;
    private final AutoApplyRuleRepo ruleRepo;
    private final ResumeVersionRepo versions;
    private final SettingRepo settingRepo;
    private final CompanyRepo companies;
    private final CompanyMemberRepo members;
    private final CompanyDocumentRepo documents;
    private final PasswordEncoder encoder;
    private final SkillCatalog catalog;
    private final ProfileService profiles;
    private final ApplicationWorkflow workflow;
    private final OptimizerService optimizer;
    private final AutoApplyService autoApply;
    private final NotificationService notifications;

    @Value("${app.admin-email}") private String adminEmail;
    @Value("${app.admin-password}") private String adminPassword;
    @Value("${app.seed-demo}") private boolean seedDemo;
    @Value("${app.demo-password}") private String demoPassword;

    public DataSeeder(UserRepo users, RecruiterProfileRepo recruiters, JobRepo jobs, ApplicationRepo applications,
                      EvidenceRepo evidenceRepo, AutoApplyRuleRepo ruleRepo, ResumeVersionRepo versions, SettingRepo settingRepo,
                      CompanyRepo companies, CompanyMemberRepo members, CompanyDocumentRepo documents,
                      PasswordEncoder encoder, SkillCatalog catalog, ProfileService profiles, ApplicationWorkflow workflow,
                      OptimizerService optimizer, AutoApplyService autoApply, NotificationService notifications) {
        this.users = users;
        this.recruiters = recruiters;
        this.jobs = jobs;
        this.applications = applications;
        this.evidenceRepo = evidenceRepo;
        this.ruleRepo = ruleRepo;
        this.versions = versions;
        this.settingRepo = settingRepo;
        this.companies = companies;
        this.members = members;
        this.documents = documents;
        this.encoder = encoder;
        this.catalog = catalog;
        this.profiles = profiles;
        this.workflow = workflow;
        this.optimizer = optimizer;
        this.autoApply = autoApply;
        this.notifications = notifications;
    }

    private static String orDefault(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }

    @Override
    public void run(String... args) {
        String adminMail = orDefault(adminEmail, "admin@recruit.local").toLowerCase(Locale.ROOT);
        String adminPw = orDefault(adminPassword, "Admin@12345");
        String demoPw = orDefault(demoPassword, "Demo@1234");

        AppUser admin = users.findByEmail(adminMail).orElse(null);
        if (admin == null) {
            makeUser("Platform Admin", adminMail, adminPw, Role.ADMIN);
            log.info("Created admin account {}", adminMail);
        } else if (syncPassword(admin, adminPw)) {
            log.warn("Admin password realigned to the configured ADMIN_PASSWORD");
        }
        if (!seedDemo) return;

        // Seeded accounts always match the configured demo password, so the credentials
        // published on the login screen keep working on a fresh or redeployed database.
        int realigned = 0;
        for (String email : DemoData.demoEmails()) {
            AppUser u = users.findByEmail(email).orElse(null);
            if (u != null && syncPassword(u, demoPw)) realigned++;
        }
        if (realigned > 0) log.warn("Realigned the password of {} demo accounts to DEMO_PASSWORD", realigned);

        if (SEED_VERSION.equals(settingRepo.findById(SEED_KEY).map(s -> s.value).orElse(""))) return;
        try {
            seedDemoData(demoPw);
            settingRepo.save(new Setting(SEED_KEY, SEED_VERSION));
            log.info("Demo dataset v{} loaded", SEED_VERSION);
        } catch (Exception e) {
            log.error("Demo data seeding failed (the app still works, restart to retry): {}", e.toString(), e);
        }
    }

    private void seedDemoData(String pw) {
        // ---- recruiters ----
        Map<String, AppUser> recruiterUsers = new HashMap<>();
        Map<String, String> companyByEmail = new HashMap<>();
        for (DemoData.Recruiter r : DemoData.RECRUITERS) {
            AppUser u = getOrCreateUser(r.name(), r.email(), pw, Role.RECRUITER);
            recruiterUsers.put(r.email(), u);
            RecruiterProfile rp = recruiters.findByUserId(u.id).orElseGet(RecruiterProfile::new);
            if (rp.id == null) {
                rp.userId = u.id;
                rp.companyName = r.company();
                rp.website = r.website();
                rp.location = r.location();
                rp.designation = r.designation();
                recruiters.save(rp);
            }
            companyByEmail.put(r.email(), rp.companyName);
        }

        // ---- companies (company workflow, seed version 3) ----
        Map<String, Long> companyNameToId = seedCompanies(pw, recruiterUsers);

        // ---- candidates ----
        Map<String, AppUser> candidates = new HashMap<>();
        for (DemoData.Persona p : DemoData.PERSONAS) {
            AppUser u = getOrCreateUser(p.name(), p.email(), pw, Role.CANDIDATE);
            candidates.put(p.email(), u);
            CandidateProfile cp = profiles.getOrCreate(u.id);
            if (Csv.nz(cp.skills).isBlank() && !p.skills().isBlank()) {
                cp.headline = p.headline();
                cp.location = p.location();
                cp.experienceYears = p.years();
                cp.summary = p.summary();
                cp.preferredRoles = p.preferredRoles();
                cp.skills = Csv.join(catalog.normalize(p.skills()));
                cp.education = p.education();
                cp.experience = p.experience();
                cp.projects = p.projects();
                cp.certifications = p.certifications();
                cp.openToRecruiters = p.open();
                cp.profileSource = "MANUAL";
                profiles.save(cp);
            }
        }

        // ---- jobs ----
        Set<String> existing = new HashSet<>();
        for (Job j : jobs.findAll()) existing.add((j.title + "|" + j.company).toLowerCase(Locale.ROOT));
        for (DemoData.JobSpec s : DemoData.JOBS) {
            AppUser rec = recruiterUsers.get(s.recruiter());
            String company = companyByEmail.get(s.recruiter());
            if (existing.contains((s.title() + "|" + company).toLowerCase(Locale.ROOT))) continue;
            Job j = new Job();
            j.recruiterId = rec.id;
            j.source = "DEMO";
            j.title = s.title();
            j.company = company;
            j.companyId = companyNameToId.get(company);
            j.description = s.desc();
            j.requiredSkills = Csv.join(catalog.normalize(s.skills()));
            j.location = s.location();
            j.employmentType = s.type();
            j.minExperience = s.minExp();
            j.active = s.active();
            jobs.save(j);
        }
        for (DemoData.ExternalJob e : DemoData.EXTERNAL) {
            if (jobs.findFirstBySourceJobId(e.id()).isPresent()) continue;
            Job j = new Job();
            j.source = "EXTERNAL";
            j.sourceJobId = e.id();
            j.title = e.title();
            j.company = e.company();
            j.description = e.desc();
            j.requiredSkills = Csv.join(catalog.normalize(e.skills()));
            j.location = e.location();
            j.employmentType = "Full-time";
            j.minExperience = 0.0;
            j.externalUrl = e.url();
            jobs.save(j);
        }
        Map<String, Job> byTitle = new HashMap<>();
        // link jobs from earlier seed versions to their companies (idempotent backfill)
        for (Job j : jobs.findAll()) {
            if (j.companyId == null && j.company != null) {
                Long cid = companyNameToId.get(j.company);
                if (cid != null) {
                    j.companyId = cid;
                    jobs.save(j);
                }
            }
        }
        for (Job j : jobs.findAll()) byTitle.putIfAbsent(j.title, j);

        // ---- evidence ----
        for (DemoData.Evi e : DemoData.EVIDENCE) {
            AppUser u = candidates.get(e.email());
            if (u == null) continue;
            boolean dup = evidenceRepo.findByCandidateIdOrderByCreatedAtDesc(u.id).stream().anyMatch(x -> x.title.equals(e.title()));
            if (dup) continue;
            Evidence ev = new Evidence();
            ev.candidateId = u.id;
            ev.type = e.type();
            ev.title = e.title();
            ev.url = e.url();
            ev.skill = e.skill();
            ev.status = e.status();
            ev.verifiedBy = e.by();
            ev.metadata = e.meta();
            if (!"PENDING".equals(e.status())) ev.checkedAt = Instant.now();
            evidenceRepo.save(ev);
        }

        // ---- applications in every status ----
        int idx = 0;
        for (String[] row : DemoData.APPLICATIONS) {
            idx++;
            AppUser u = candidates.get(row[0]);
            Job job = byTitle.get(row[1]);
            if (u == null || job == null) continue;
            try {
                JobApplication a = workflow.submit(u, job, null, null, null);
                a.createdAt = Instant.now().minus(Duration.ofDays((idx * 5L) % 30 + 1));
                a = applications.save(a);
                advance(a, job, u, ApplicationStatus.valueOf(row[2]));
            } catch (ApiException e) {
                log.debug("Skipped demo application {} -> {}: {}", row[0], row[1], e.getMessage());
            }
        }
        for (String[] row : DemoData.EXTERNAL_TRACKED) {
            AppUser u = candidates.get(row[0]);
            Job job = byTitle.get(row[1]);
            if (u == null || job == null) continue;
            try {
                workflow.submit(u, job, ApplicationWorkflow.EXTERNAL, null, null);
            } catch (ApiException e) {
                log.debug("Skipped external tracking: {}", e.getMessage());
            }
        }

        // ---- resume versions (job-specific and trajectory-specific) ----
        attachJobResume(candidates.get("candidate@demo.com"), byTitle.get("Java Backend Developer"));
        roleResume(candidates.get("priya.nair@demo.com"), "Backend Developer");
        roleResume(candidates.get("rohan.mehta@demo.com"), "Full Stack Developer");
        roleResume(candidates.get("sneha.kapoor@demo.com"), "Data Analyst");
        roleResume(candidates.get("kavya.iyer@demo.com"), "DevOps Engineer");

        // ---- auto-apply rules (then run them once so AUTO applications exist) ----
        for (String[] r : RULES) {
            AppUser u = candidates.get(r[0]);
            if (u == null) continue;
            AutoApplyRule rule = ruleRepo.findByCandidateId(u.id).orElseGet(AutoApplyRule::new);
            rule.candidateId = u.id;
            rule.enabled = Boolean.parseBoolean(r[1]);
            rule.minScore = Integer.parseInt(r[2]);
            rule.maxPerDay = Integer.parseInt(r[3]);
            rule.locations = r[4];
            rule.jobTypes = r[5];
            rule.roleKeywords = r[6];
            ruleRepo.save(rule);
        }
        for (String[] r : RULES) {
            AppUser u = candidates.get(r[0]);
            if (u != null && Boolean.parseBoolean(r[1])) autoApply.runForCandidate(u.id);
        }
    }

    /**
     * Company workflow demo data: three verified companies, one still awaiting
     * verification, a dedicated company-admin account and memberships for the demo
     * recruiters. Returns company name -> id so demo jobs can be linked to them.
     */
    private Map<String, Long> seedCompanies(String pw, Map<String, AppUser> recruiterUsers) {
        Map<String, Long> byName = new HashMap<>();
        AppUser owner = getOrCreateUser("Anita Rao", "owner@demo.com", pw, Role.COMPANY_ADMIN);
        AppUser zenith = getOrCreateUser("Kabir Sethi", "zenith@demo.com", pw, Role.COMPANY_ADMIN);
        // the leads of FinEdge and CloudKart run their own companies
        promoteToCompanyAdmin(recruiterUsers.get("recruiter2@demo.com"));
        promoteToCompanyAdmin(recruiterUsers.get("recruiter3@demo.com"));

        Company technova = ensureCompany("TechNova Solutions", "technova.example.com", "https://technova.example.com",
                "Cloud and platform engineering services for fast growing product teams.", "IT Services", "Bengaluru",
                CompanyStatus.ACTIVE, owner.id, byName);
        Company finedge = ensureCompany("FinEdge Capital", "finedge.example.com", "https://finedge.example.com",
                "Investment and lending products for small businesses.", "Financial Services", "Mumbai",
                CompanyStatus.ACTIVE, recruiterUsers.get("recruiter2@demo.com").id, byName);
        Company cloudkart = ensureCompany("CloudKart", "cloudkart.example.com", "https://cloudkart.example.com",
                "Cloud native commerce infrastructure for online brands.", "E-commerce", "Hyderabad",
                CompanyStatus.ACTIVE, recruiterUsers.get("recruiter3@demo.com").id, byName);
        Company zenithLabs = ensureCompany("Zenith Labs", "zenithlabs.example.com", "https://zenithlabs.example.com",
                "Applied AI research and tooling. Registered on the demo platform, still awaiting verification.",
                "AI / ML", "Pune", CompanyStatus.PENDING_VERIFICATION, zenith.id, byName);

        ensureMember(technova, owner);
        ensureMember(technova, recruiterUsers.get("recruiter@demo.com"));
        ensureMember(finedge, recruiterUsers.get("recruiter2@demo.com"));
        ensureMember(cloudkart, recruiterUsers.get("recruiter3@demo.com"));
        ensureMember(zenithLabs, zenith);

        // something for the admin to look at in the verification queue
        if (documents.findByCompanyIdOrderByUploadedAtDesc(zenithLabs.id).isEmpty()) {
            CompanyDocument doc = new CompanyDocument();
            doc.companyId = zenithLabs.id;
            doc.fileName = "incorporation-certificate.txt";
            doc.mimeType = "text/plain";
            doc.data = ("Zenith Labs Pvt Ltd - sample incorporation certificate for the SNAP-HIRE demo.\n"
                    + "Domain: zenithlabs.example.com\n").getBytes(StandardCharsets.UTF_8);
            documents.save(doc);
        }
        return byName;
    }

    private Company ensureCompany(String name, String domain, String website, String description, String industry,
                                  String location, CompanyStatus status, Long ownerId, Map<String, Long> byName) {
        Company c = companies.findFirstByEmailDomainIgnoreCase(domain).orElse(null);
        if (c == null) {
            c = new Company();
            c.emailDomain = domain;
            c.status = status;
            c.ownerId = ownerId;
            if (status == CompanyStatus.ACTIVE) {
                c.verifiedBy = "SEED";
                c.verifiedAt = Instant.now();
            }
        }
        c.name = name;
        c.website = website;
        c.description = description;
        c.industry = industry;
        c.location = location;
        if (c.ownerId == null) c.ownerId = ownerId;
        c = companies.save(c);
        byName.put(c.name, c.id);
        return c;
    }

    private void ensureMember(Company c, AppUser u) {
        if (u == null || c == null) return;
        CompanyMember m = members.findByUserId(u.id).orElse(null);
        if (m == null) {
            m = new CompanyMember();
            m.companyId = c.id;
            m.userId = u.id;
        }
        if (!m.companyId.equals(c.id)) return; // already belongs to a different company
        m.permissions = String.join(",", Permission.allNames());
        m.status = MemberStatus.ACTIVE;
        members.save(m);
    }

    private void promoteToCompanyAdmin(AppUser u) {
        if (u != null && u.role != Role.COMPANY_ADMIN) {
            u.role = Role.COMPANY_ADMIN;
            users.save(u);
        }
    }

    private void advance(JobApplication a, Job job, AppUser cand, ApplicationStatus target) {        if (target == ApplicationStatus.APPLIED) return;
        List<ApplicationStatus> steps = new ArrayList<>();
        if (target == ApplicationStatus.WITHDRAWN) {
            steps.add(ApplicationStatus.WITHDRAWN);
        } else if (target == ApplicationStatus.REJECTED) {
            steps.add(ApplicationStatus.SCREENING);
            steps.add(ApplicationStatus.REJECTED);
        } else {
            for (ApplicationStatus s : PIPELINE) {
                steps.add(s);
                if (s == target) break;
            }
        }
        for (ApplicationStatus s : steps) {
            ApplicationStatus old = a.status;
            a.status = s;
            a.updatedAt = Instant.now();
            a = applications.save(a);
            workflow.event(a.id, s == ApplicationStatus.WITHDRAWN ? "WITHDRAWN" : "STATUS_CHANGED", old + " -> " + s);
        }
        if (target != ApplicationStatus.WITHDRAWN) {
            notifications.notify(cand.id, "STATUS", "Your application for \"" + job.title + "\" at " + job.company
                    + " is now " + target.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ".", a.id, job.id);
        }
    }

    private void attachJobResume(AppUser u, Job job) {
        if (u == null || job == null) return;
        applications.findByCandidateIdAndJobId(u.id, job.id).ifPresent(a -> {
            var o = optimizer.forJob(u, profiles.getOrCreate(u.id), job);
            ResumeVersion rv = new ResumeVersion();
            rv.candidateId = u.id;
            rv.jobId = job.id;
            rv.title = "Tailored for: " + job.title;
            rv.content = o.resumeText();
            a.resumeVersionId = versions.save(rv).id;
            applications.save(a);
        });
    }

    private void roleResume(AppUser u, String role) {
        RoleCatalog.RoleDef def = RoleCatalog.byName(role);
        if (u == null || def == null) return;
        ResumeVersion rv = new ResumeVersion();
        rv.candidateId = u.id;
        rv.targetRole = role;
        rv.title = "Trajectory resume: " + role;
        rv.content = optimizer.forRole(u, profiles.getOrCreate(u.id), def);
        versions.save(rv);
    }

    private AppUser getOrCreateUser(String name, String email, String password, Role role) {
        return users.findByEmail(email).orElseGet(() -> makeUser(name, email, password, role));
    }

    /**
     * Brings an existing account back in line with the configured password and re-enables it
     * if it was disabled. Returns true when something actually changed.
     */
    private boolean syncPassword(AppUser u, String password) {
        if (u.enabled && encoder.matches(password, u.passwordHash)) return false;
        u.passwordHash = encoder.encode(password);
        u.enabled = true;
        users.save(u);
        return true;
    }

    private AppUser makeUser(String name, String email, String password, Role role) {
        AppUser u = new AppUser();
        u.name = name;
        u.email = email;
        u.role = role;
        u.passwordHash = encoder.encode(password);
        return users.save(u);
    }
}
