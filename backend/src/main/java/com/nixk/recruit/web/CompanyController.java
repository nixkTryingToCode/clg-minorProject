package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.security.JwtService;
import com.nixk.recruit.service.*;
import com.nixk.recruit.web.Dtos.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints for a company's own members: company profile, invitations, membership
 * management, job reassignment and company-wide analytics.
 */
@RestController
@RequestMapping("/api/companies")
@PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
public class CompanyController {
    private final CompanyService companyService;
    private final CompanyRepo companies;
    private final CompanyMemberRepo members;
    private final CompanyDocumentRepo documents;
    private final JobRepo jobs;
    private final ApplicationRepo applications;
    private final UserRepo users;
    private final AuditService audit;
    private final JwtService jwt;

    public CompanyController(CompanyService companyService, CompanyRepo companies, CompanyMemberRepo members,
                             CompanyDocumentRepo documents, JobRepo jobs, ApplicationRepo applications,
                             UserRepo users, AuditService audit, JwtService jwt) {
        this.companyService = companyService;
        this.companies = companies;
        this.members = members;
        this.documents = documents;
        this.jobs = jobs;
        this.applications = applications;
        this.users = users;
        this.audit = audit;
        this.jwt = jwt;
    }

    /* ---------------- registration (public) ---------------- */

    @PreAuthorize("permitAll")
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthResponse register(@RequestParam String ownerName,
                                 @RequestParam String email,
                                 @RequestParam String password,
                                 @RequestParam String name,
                                 @RequestParam String emailDomain,
                                 @RequestParam(required = false) String website,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) String industry,
                                 @RequestParam(required = false) String location,
                                 @RequestParam(required = false) MultipartFile logo,
                                 @RequestParam(required = false) MultipartFile[] documents) {
        AppUser u = companyService.register(ownerName, email, password, name, emailDomain, website,
                description, industry, location, upload(logo), uploads(documents));
        companyService.attach(u);
        return new AuthResponse(jwt.generate(u), u);
    }

    /* ---------------- company profile ---------------- */

    @GetMapping("/mine")
    public Map<String, Object> mine() {
        AppUser me = CurrentUser.get();
        CompanyMember m = requireCompany(me);
        Company c = companies.findById(m.companyId).orElseThrow(() -> new ApiException(404, "Company not found"));
        companyService.attach(me);
        Map<String, Object> view = companyService.companyView(c);
        view.put("logoBase64", c.logo == null || c.logo.length == 0 ? null : Base64.getEncoder().encodeToString(c.logo));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("company", view);
        out.put("members", memberViews(c.id, me));
        out.put("jobCount", jobs.findByCompanyIdOrderByCreatedAtDesc(c.id).size());
        List<Map<String, Object>> docs = new ArrayList<>();
        for (CompanyDocument d : documents.findByCompanyIdOrderByUploadedAtDesc(c.id)) docs.add(docView(d));
        out.put("documents", docs);
        out.put("myPermissions", me.permissions);
        return out;
    }

    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        CompanyMember m = companyService.memberOf(me.id)
                .filter(x -> x.companyId.equals(id))
                .orElseThrow(() -> new ApiException(403, "Not your company"));
        Company c = companies.findById(id).orElseThrow(() -> new ApiException(404, "Company not found"));
        if (c.logo == null || c.logo.length == 0) throw new ApiException(404, "This company has no logo");
        return ResponseEntity.ok().contentType(imageType(c.logoMimeType)).body(c.logo);
    }

    /** Lets a member re-download their own company's verification documents. */
    @GetMapping("/mine/documents/{docId}")
    public ResponseEntity<byte[]> ownDocument(@PathVariable Long docId) {
        CompanyMember m = requireCompany(CurrentUser.get());
        CompanyDocument d = documents.findById(docId).orElseThrow(() -> new ApiException(404, "Document not found"));
        if (!d.companyId.equals(m.companyId)) throw new ApiException(404, "Document not found");
        return ResponseEntity.ok().contentType(mediaType(d.mimeType)).body(d.data);
    }

    /* ---------------- invitations ---------------- */

    @GetMapping("/mine/invitations")
    public List<Map<String, Object>> invitations() {
        AppUser me = CurrentUser.get();
        CompanyMember mine = requireCompany(me);
        companyService.require(me, Permission.MANAGE_RECRUITERS);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Invitation inv : companyService.listInvitations(mine.companyId)) out.add(inviteView(inv));
        return out;
    }

    @PostMapping("/mine/invitations")
    public Map<String, Object> invite(@Valid @RequestBody InvitationRequest r) {
        AppUser me = CurrentUser.get();
        requireCompany(me);
        return inviteView(companyService.invite(me, r.email(), r.role(), r.permissions()));
    }

    @DeleteMapping("/mine/invitations/{id}")
    public Map<String, String> revoke(@PathVariable Long id) {
        AppUser me = CurrentUser.get();
        requireCompany(me);
        companyService.revoke(me, id);
        return Map.of("status", "revoked");
    }

    /* ---------------- membership ---------------- */

    @PatchMapping("/mine/members/{id}")
    public Map<String, Object> updateMember(@PathVariable Long id, @RequestBody MemberUpdateRequest r) {
        AppUser me = CurrentUser.get();
        CompanyMember mine = requireCompany(me);
        companyService.require(me, Permission.MANAGE_RECRUITERS);
        CompanyMember m = members.findById(id).orElseThrow(() -> new ApiException(404, "Member not found"));
        if (!m.companyId.equals(mine.companyId)) throw new ApiException(404, "Member not found");
        if (m.userId.equals(me.id)) throw new ApiException(400, "You can't change your own membership");
        AppUser target = users.findById(m.userId).orElseThrow(() -> new ApiException(404, "User not found"));
        if (r.permissions() != null && !r.permissions().isEmpty() && target.role == Role.COMPANY_ADMIN) {
            throw new ApiException(400, "Company admins always hold every permission");
        }
        if (r.permissions() != null) m.permissions = Permission.join(Permission.validated(r.permissions()));
        if (r.status() != null && !r.status().isBlank()) {
            try {
                m.status = MemberStatus.valueOf(r.status().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException(400, "Unknown member status");
            }
        }
        m = members.save(m);
        audit.log(me.id, "COMPANY_MEMBER_UPDATE", "CompanyMember", m.id,
                target.email + " status=" + m.status + " permissions=" + m.permissions);
        return memberView(m, me);
    }

    /* ---------------- jobs across the company ---------------- */

    @PostMapping("/mine/reassign")
    public Job reassign(@Valid @RequestBody ReassignRequest r) {
        AppUser me = CurrentUser.get();
        CompanyMember mine = requireCompany(me);
        companyService.require(me, Permission.REASSIGN_JOBS);
        Job j = jobs.findById(r.jobId()).orElseThrow(() -> new ApiException(404, "Job not found"));
        if (!mine.companyId.equals(j.companyId)) throw new ApiException(403, "That job doesn't belong to your company");
        CompanyMember target = members.findByUserId(r.toUserId())
                .orElseThrow(() -> new ApiException(400, "That user is not a member of your company"));
        if (!target.companyId.equals(mine.companyId) || target.status != MemberStatus.ACTIVE) {
            throw new ApiException(400, "Jobs can only be reassigned to active members of your company");
        }
        j.recruiterId = r.toUserId();
        j = jobs.save(j);
        audit.log(me.id, "JOB_REASSIGNED", "Job", j.id, "to user " + r.toUserId());
        return j;
    }

    @GetMapping("/mine/analytics")
    public Map<String, Object> analytics() {
        AppUser me = CurrentUser.get();
        CompanyMember mine = requireCompany(me);
        companyService.require(me, Permission.VIEW_ANALYTICS);
        List<Job> companyJobs = jobs.findByCompanyIdOrderByCreatedAtDesc(mine.companyId);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) byStatus.put(s.name(), 0);
        List<Map<String, Object>> perJob = new ArrayList<>();
        int total = 0;
        for (Job j : companyJobs) {
            List<JobApplication> apps = applications.findByJobId(j.id);
            Map<String, Integer> jobStatus = new LinkedHashMap<>();
            for (ApplicationStatus s : ApplicationStatus.values()) jobStatus.put(s.name(), 0);
            for (JobApplication a : apps) {
                byStatus.merge(a.status.name(), 1, Integer::sum);
                jobStatus.merge(a.status.name(), 1, Integer::sum);
            }
            total += apps.size();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("jobId", j.id);
            row.put("title", j.title);
            row.put("active", j.active);
            row.put("recruiterId", j.recruiterId);
            row.put("applicants", apps.size());
            row.put("byStatus", jobStatus);
            perJob.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalJobs", companyJobs.size());
        out.put("activeJobs", companyJobs.stream().filter(j -> j.active).count());
        out.put("totalApplications", total);
        out.put("byStatus", byStatus);
        out.put("hires", byStatus.get(ApplicationStatus.SELECTED.name()));
        out.put("members", members.countByCompanyId(mine.companyId));
        out.put("perJob", perJob);
        return out;
    }

    /* ---------------- helpers ---------------- */

    private CompanyMember requireCompany(AppUser me) {
        return companyService.memberOf(me.id).orElseThrow(() -> new ApiException(403, "You don't belong to a company"));
    }

    private List<Map<String, Object>> memberViews(Long companyId, AppUser me) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CompanyMember m : members.findByCompanyIdOrderByJoinedAtAsc(companyId)) out.add(memberView(m, me));
        return out;
    }

    private Map<String, Object> memberView(CompanyMember m, AppUser me) {
        AppUser u = users.findById(m.userId).orElse(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.id);
        row.put("userId", m.userId);
        row.put("name", u == null ? "Unknown" : u.name);
        row.put("email", u == null ? "" : u.email);
        row.put("role", u == null ? "" : u.role.name());
        row.put("permissions", Permission.parse(m.permissions).stream().map(Enum::name).toList());
        row.put("status", m.status.name());
        row.put("joinedAt", m.joinedAt);
        row.put("isMe", u != null && u.id.equals(me.id));
        row.put("enabled", u != null && u.enabled);
        return row;
    }

    private static Map<String, Object> inviteView(Invitation inv) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", inv.id);
        row.put("email", inv.email);
        row.put("role", inv.role);
        row.put("permissions", Permission.parse(inv.permissions).stream().map(Enum::name).toList());
        row.put("status", inv.status == null ? null : inv.status.name());
        row.put("expiresAt", inv.expiresAt);
        row.put("acceptedAt", inv.acceptedAt);
        row.put("createdAt", inv.createdAt);
        row.put("token", inv.token);
        return row;
    }

    private static Map<String, Object> docView(CompanyDocument d) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.id);
        row.put("fileName", d.fileName);
        row.put("mimeType", d.mimeType);
        row.put("uploadedAt", d.uploadedAt);
        return row;
    }

    private static CompanyService.Upload upload(MultipartFile f) {
        if (f == null || f.isEmpty()) return null;
        try {
            return new CompanyService.Upload(f.getOriginalFilename(), f.getContentType(), f.getBytes());
        } catch (IOException e) {
            throw new ApiException(400, "Could not read the uploaded file "
                    + (f.getOriginalFilename() == null ? "" : f.getOriginalFilename()));
        }
    }

    private static List<CompanyService.Upload> uploads(MultipartFile[] files) {
        if (files == null || files.length == 0) return List.of();
        List<CompanyService.Upload> out = new ArrayList<>();
        for (MultipartFile f : files) {
            CompanyService.Upload u = upload(f);
            if (u != null) out.add(u);
        }
        return out;
    }

    private static MediaType mediaType(String mime) {
        if (mime == null || mime.isBlank()) return MediaType.APPLICATION_OCTET_STREAM;
        try {
            return MediaType.parseMediaType(mime);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static MediaType imageType(String mime) {
        MediaType t = mediaType(mime);
        return t.getType().equals("image") ? t : MediaType.IMAGE_PNG;
    }
}
