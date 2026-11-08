package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import com.nixk.recruit.security.CurrentUser;
import com.nixk.recruit.security.JwtService;
import com.nixk.recruit.service.ApiException;
import com.nixk.recruit.service.AuditService;
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
    private final RecruiterProfileRepo recruiters;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthController(UserRepo users, CandidateProfileRepo candidates, RecruiterProfileRepo recruiters,
                          PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.candidates = candidates;
        this.recruiters = recruiters;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        Role role;
        try {
            role = Role.valueOf(req.role() == null ? "CANDIDATE" : req.role().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Role must be CANDIDATE or RECRUITER");
        }
        if (role == Role.ADMIN) throw new ApiException(400, "Role must be CANDIDATE or RECRUITER");
        if (users.existsByEmail(email)) throw new ApiException(409, "An account with this email already exists");

        AppUser u = new AppUser();
        u.email = email;
        u.name = req.name().trim();
        u.role = role;
        u.passwordHash = encoder.encode(req.password());
        u = users.save(u);

        if (role == Role.CANDIDATE) {
            CandidateProfile p = new CandidateProfile();
            p.userId = u.id;
            candidates.save(p);
        } else {
            RecruiterProfile r = new RecruiterProfile();
            r.userId = u.id;
            r.companyName = req.companyName() == null || req.companyName().isBlank() ? u.name + "'s Company" : req.companyName().trim();
            recruiters.save(r);
        }
        audit.log(u.id, "REGISTER", "User", u.id, "role=" + role);
        return new AuthResponse(jwt.generate(u), u);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser u = users.findByEmail(req.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (u == null || !encoder.matches(req.password(), u.passwordHash)) throw new ApiException(401, "Invalid email or password");
        if (!u.enabled) throw new ApiException(403, "This account has been disabled");
        audit.log(u.id, "LOGIN", "User", u.id, null);
        return new AuthResponse(jwt.generate(u), u);
    }

    @GetMapping("/me")
    public AppUser me() {
        return CurrentUser.get();
    }
}
