package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.JwtService;
import com.nixk.recruit.service.ApiException;
import com.nixk.recruit.service.CompanyService;
import com.nixk.recruit.service.Permission;
import com.nixk.recruit.web.Dtos.AuthResponse;
import com.nixk.recruit.web.Dtos.InviteAcceptRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Public invitation endpoints: anyone holding a link can inspect it and accept it,
 * setting their own password in the process.
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {
    private final CompanyService companyService;
    private final InvitationRepo invitations;
    private final CompanyRepo companies;
    private final JwtService jwt;

    public InvitationController(CompanyService companyService, InvitationRepo invitations,
                                CompanyRepo companies, JwtService jwt) {
        this.companyService = companyService;
        this.invitations = invitations;
        this.companies = companies;
        this.jwt = jwt;
    }

    @GetMapping("/{token}")
    public Map<String, Object> info(@PathVariable String token) {
        Invitation inv = invitations.findByToken(token)
                .orElseThrow(() -> new ApiException(404, "Invalid invitation link"));
        if (inv.status == InvitationStatus.PENDING && inv.expiresAt != null && inv.expiresAt.isBefore(Instant.now())) {
            inv.status = InvitationStatus.EXPIRED;
            invitations.save(inv);
        }
        Company c = companies.findById(inv.companyId).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", inv.token);
        out.put("email", inv.email);
        out.put("role", inv.role);
        out.put("permissions", Permission.parse(inv.permissions).stream().map(Enum::name).toList());
        out.put("status", inv.status == null ? null : inv.status.name());
        out.put("expiresAt", inv.expiresAt);
        out.put("companyName", c == null ? null : c.name);
        out.put("companyStatus", c == null || c.status == null ? null : c.status.name());
        out.put("valid", inv.status == InvitationStatus.PENDING && c != null && c.status == CompanyStatus.ACTIVE);
        return out;
    }

    @PostMapping("/{token}")
    public AuthResponse accept(@PathVariable String token, @RequestBody InviteAcceptRequest req) {
        AppUser u = companyService.accept(token, req == null ? null : req.name(), req == null ? null : req.password());
        return new AuthResponse(jwt.generate(u), u);
    }
}
