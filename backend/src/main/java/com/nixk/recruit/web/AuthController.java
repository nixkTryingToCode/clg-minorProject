package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.security.JwtService;
import com.nixk.recruit.service.ApiException;
import com.nixk.recruit.service.AuditService;
import com.nixk.recruit.service.CompanyService;
import com.nixk.recruit.web.Dtos.*;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepo users;
    private final CandidateProfileRepo candidates;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final CompanyService companies;

    public AuthController(UserRepo users, CandidateProfileRepo candidates,
                          PasswordEncoder encoder, JwtService jwt, AuditService audit, CompanyService companies) {
        this.users = users;
        this.candidates = candidates;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.companies = companies;
    }

    /** Self-signup is candidate-only; recruiters and company admins join through a company invitation. */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String requested = req.role() == null ? "" : req.role().trim();
        if (!requested.isEmpty() && !"CANDIDATE".equalsIgnoreCase(requested)) {
            throw new ApiException(400, "Recruiters join through a company. Use \"Register your company\" instead.");
        }
        if (users.existsByEmail(email)) throw new ApiException(409, "An account with this email already exists");

        AppUser u = new AppUser();
        u.email = email;
        u.name = req.name().trim();
        u.role = Role.CANDIDATE;
        u.passwordHash = encoder.encode(req.password());
        u = users.save(u);

        CandidateProfile p = new CandidateProfile();
        p.userId = u.id;
        candidates.save(p);

        audit.log(u.id, "REGISTER", "User", u.id, "role=CANDIDATE");
        companies.attach(u);
        return new AuthResponse(jwt.generate(u), u);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser u = users.findByEmail(req.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.passwordHash)) throw new ApiException(401, "Invalid email or password");
        if (!u.enabled) throw new ApiException(403, "This account has been disabled");
        audit.log(u.id, "LOGIN", "User", u.id, null);
        companies.attach(u);
        return new AuthResponse(jwt.generate(u), u);
    }

    @GetMapping("/me")
    public AppUser me() {
        AppUser u = CurrentUser.get();
        companies.attach(u);
        return u;
    }
}
