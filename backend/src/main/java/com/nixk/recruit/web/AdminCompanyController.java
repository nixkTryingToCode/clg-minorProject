package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.service.AuditService;
import com.nixk.recruit.service.ApiException;
import com.nixk.recruit.service.CompanyService;
import com.nixk.recruit.web.Dtos.CompanyStatusRequest;
import com.nixk.recruit.web.Dtos.EnabledRequest;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin side of the company workflow: reviewing registrations, flipping companies
 * between statuses and the "auto verify all" toggle.
 */
@RestController
@RequestMapping("/api/admin/companies")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompanyController {
    private final CompanyService companyService;
    private final CompanyRepo companies;
    private final CompanyDocumentRepo documents;
    private final JobRepo jobs;
    private final UserRepo users;

    public AdminCompanyController(CompanyService companyService, CompanyRepo companies,
                                  CompanyDocumentRepo documents, JobRepo jobs, UserRepo users) {
        this.companyService = companyService;
        this.companies = companies;
        this.documents = documents;
        this.jobs = jobs;
        this.users = users;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        List<Company> all = new ArrayList<>(companies.findAll());
        all.sort(Comparator.comparing((Company c) -> c.createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Company c : all) {
            Map<String, Object> row = companyService.companyView(c);
            AppUser owner = c.ownerId == null ? null : users.findById(c.ownerId).orElse(null);
            row.put("ownerName", owner == null ? null : owner.name);
            row.put("ownerEmail", owner == null ? null : owner.email);
            row.put("jobCount", jobs.findByCompanyIdOrderByCreatedAtDesc(c.id).size());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/{id}/documents")
    public List<CompanyDocument> documents(@PathVariable Long id) {
        requireCompany(id);
        return documents.findByCompanyIdOrderByUploadedAtDesc(id);
    }

    @GetMapping("/{id}/documents/{docId}")
    public ResponseEntity<byte[]> document(@PathVariable Long id, @PathVariable Long docId) {
        requireCompany(id);
        CompanyDocument d = documents.findById(docId).orElseThrow(() -> new ApiException(404, "Document not found"));
        if (!d.companyId.equals(id)) throw new ApiException(404, "Document not found");
        MediaType t = MediaType.APPLICATION_OCTET_STREAM;
        if (d.mimeType != null && !d.mimeType.isBlank()) {
            try { t = MediaType.parseMediaType(d.mimeType); } catch (Exception ignored) { /* keep octet-stream */ }
        }
        return ResponseEntity.ok().contentType(t).body(d.data);
    }

    @GetMapping("/auto-verify")
    public Map<String, Object> autoVerify() {
        return Map.of("enabled", companyService.autoVerifyEnabled());
    }

    @PutMapping("/auto-verify")
    public Map<String, Object> setAutoVerify(@RequestBody EnabledRequest r) {
        CompanyService.AutoVerify res = companyService.setAutoVerify(r.enabled(), CurrentUser.get());
        return Map.of("enabled", res.enabled(), "verified", res.verified());
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> setStatus(@PathVariable Long id, @RequestBody CompanyStatusRequest r) {
        Company c = companyService.setStatus(id, r.status(), CurrentUser.get());
        return companyService.companyView(c);
    }

    private void requireCompany(Long id) {
        companies.findById(id).orElseThrow(() -> new ApiException(404, "Company not found"));
    }
}
