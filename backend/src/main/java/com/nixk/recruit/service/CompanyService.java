package com.nixk.recruit.service;

import com.nixk.recruit.model.*;
import com.nixk.recruit.repo.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company lifecycle: registration, verification, memberships, invitations and the
 * permission checks that gate recruiting actions inside an organisation.
 */
@Service
public class CompanyService {
    /** Setting key for the admin "auto verify companies" toggle; absent means enabled. */
    public static final String AUTO_VERIFY_KEY = "company.auto_verify";
    private static final Duration INVITE_TTL = Duration.ofDays(7);

    /** A file that arrived with a registration or verification request. */
    public record Upload(String fileName, String mimeType, byte[] data) {}

    /** Result of changing the auto-verification toggle. */
    public record AutoVerify(boolean enabled, int verified) {}

    private final CompanyRepo companies;
    private final CompanyDocumentRepo documents;
    private final CompanyMemberRepo members;
    private final InvitationRepo invitations;
    private final UserRepo users;
    private final RecruiterProfileRepo recruiters;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final NotificationService notifications;
    private final EmailSender email;
    private final SettingRepo settings;

    public CompanyService(CompanyRepo companies, CompanyDocumentRepo documents, CompanyMemberRepo members,
                          InvitationRepo invitations, UserRepo users, RecruiterProfileRepo recruiters,
                          PasswordEncoder encoder,
                          AuditService audit, NotificationService notifications, EmailSender email, SettingRepo settings) {
        this.companies = companies;
        this.documents = documents;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.recruiters = recruiters;
        this.encoder = encoder;
        this.audit = audit;
        this.notifications = notifications;
        this.email = email;
        this.settings = settings;
    }

    /* ---------------- context & permissions ---------------- */

    public Optional<CompanyMember> memberOf(Long userId) {
        return userId == null ? Optional.empty() : members.findByUserId(userId);
    }

    public Optional<Company> companyOf(Long userId) {
        return memberOf(userId).flatMap(m -> companies.findById(m.companyId));
    }

    /** Fills the transient company context on a user before it is handed to the frontend. */
    public void attach(AppUser u) {
        if (u == null || u.role == Role.ADMIN) return;
        CompanyMember m = members.findByUserId(u.id).orElse(null);
        if (m == null) return;
        Company c = companies.findById(m.companyId).orElse(null);
        if (c == null) return;
        u.companyId = c.id;
        u.companyName = c.name;
        u.companyStatus = c.status;
        if (m.status != MemberStatus.ACTIVE) {
            u.permissions = List.of();
        } else if (u.role == Role.COMPANY_ADMIN) {
            u.permissions = Permission.allNames();
        } else {
            u.permissions = Permission.parse(m.permissions).stream().map(Enum::name).toList();
        }
    }

    /**
     * Membership-aware permission check. Platform admins and recruiters without a
     * company keep full access; company members need an active membership on an
     * ACTIVE company and the individual permission (admins hold them all).
     */
    public void require(AppUser u, Permission p) {
        if (u == null) throw new ApiException(403, "Authentication required");
        if (u.role == Role.ADMIN) return;
        CompanyMember m = members.findByUserId(u.id).orElse(null);
        if (m == null) return; // standalone recruiter from before the company workflow
        if (m.status != MemberStatus.ACTIVE) throw new ApiException(403, "Your company membership is suspended");
        Company c = companies.findById(m.companyId).orElseThrow(() -> new ApiException(403, "You don't belong to a company"));
        if (c.status != CompanyStatus.ACTIVE) throw new ApiException(403, "Your company is awaiting verification");
        if (u.role == Role.COMPANY_ADMIN) return;
        if (!Permission.parse(m.permissions).contains(p)) {
            throw new ApiException(403, "You don't have the " + p.name() + " permission in this company");
        }
    }

    public boolean can(AppUser u, Permission p) {
        try {
            require(u, p);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    /* ---------------- registration ---------------- */

    @Transactional
    public AppUser register(String ownerName, String email, String password, String name, String emailDomain,
                            String website, String description, String industry, String location,
                            Upload logo, List<Upload> docs) {
        String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String company = name == null ? "" : name.trim();
        String domain = normalizeDomain(emailDomain);
        if (company.isEmpty()) throw new ApiException(400, "Company name is required");
        if (domain.isEmpty()) throw new ApiException(400, "Company email domain is required");
        if (ownerName == null || ownerName.isBlank()) throw new ApiException(400, "Your name is required");
        if (mail.isEmpty()) throw new ApiException(400, "Email is required");
        if (password == null || password.length() < 8) throw new ApiException(400, "Password must be at least 8 characters");
        if (users.existsByEmail(mail)) throw new ApiException(409, "An account with this email already exists");
        if (companies.findFirstByEmailDomainIgnoreCase(domain).isPresent()) {
            throw new ApiException(409, "A company with the domain " + domain + " is already registered");
        }

        boolean autoVerify = autoVerifyEnabled();
        Company c = new Company();
        c.name = company;
        c.emailDomain = domain;
        c.website = blankToNull(website);
        c.description = blankToNull(description);
        c.industry = blankToNull(industry);
        c.location = blankToNull(location);
        if (logo != null && logo.data() != null && logo.data().length > 0) {
            c.logo = logo.data();
            c.logoMimeType = blankToNull(logo.mimeType());
        }
        c.status = autoVerify ? CompanyStatus.ACTIVE : CompanyStatus.PENDING_VERIFICATION;
        if (autoVerify) {
            c.verifiedBy = "AUTO";
            c.verifiedAt = Instant.now();
        }
        c = companies.save(c);

        AppUser u = new AppUser();
        u.email = mail;
        u.name = ownerName.trim();
        u.role = Role.COMPANY_ADMIN;
        u.passwordHash = encoder.encode(password);
        u = users.save(u);
        c.ownerId = u.id;
        c = companies.save(c);

        CompanyMember m = new CompanyMember();
        m.companyId = c.id;
        m.userId = u.id;
        m.permissions = String.join(",", Permission.allNames());
        m.invitedBy = null;
        members.save(m);

        RecruiterProfile rp = recruiters.findByUserId(u.id).orElseGet(RecruiterProfile::new);
        rp.userId = u.id;
        rp.companyName = c.name;
        if (rp.designation == null) rp.designation = "Company Admin";
        if (rp.website == null) rp.website = c.website;
        if (rp.location == null) rp.location = c.location;
        recruiters.save(rp);

        if (docs != null) {
            for (Upload d : docs) {
                if (d == null || d.data() == null || d.data().length == 0) continue;
                CompanyDocument doc = new CompanyDocument();
                doc.companyId = c.id;
                doc.fileName = blankToNull(d.fileName()) == null ? "document" : d.fileName();
                doc.mimeType = blankToNull(d.mimeType());
                doc.data = d.data();
                documents.save(doc);
            }
        }
        audit.log(u.id, "COMPANY_REGISTER", "Company", c.id, c.name + " status=" + c.status
                + (autoVerify ? " (auto-verified)" : ""));
        return u;
    }

    /* ---------------- invitations ---------------- */

    @Transactional
    public Invitation invite(AppUser me, String rawEmail, String rawRole, List<String> permissions) {
        CompanyMember mine = members.findByUserId(me.id)
                .orElseThrow(() -> new ApiException(403, "You don't belong to a company"));
        require(me, Permission.MANAGE_RECRUITERS);
        Company c = companies.findById(mine.companyId).orElseThrow(() -> new ApiException(404, "Company not found"));
        String mail = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (mail.isEmpty()) throw new ApiException(400, "Email is required");
        Role role = parseInviteRole(rawRole);
        Set<Permission> granted = role == Role.COMPANY_ADMIN ? EnumSet.allOf(Permission.class) : Permission.validated(permissions);

        AppUser existing = users.findByEmail(mail).orElse(null);
        if (existing != null) {
            CompanyMember m = members.findByUserId(existing.id).orElse(null);
            if (m != null) {
                throw new ApiException(409, m.companyId.equals(c.id)
                        ? mail + " is already a member of this company"
                        : mail + " already belongs to another company");
            }
        }
        for (Invitation old : invitations.findByCompanyIdAndEmailIgnoreCaseOrderByCreatedAtDesc(c.id, mail)) {
            if (old.status == InvitationStatus.PENDING) {
                old.status = InvitationStatus.REVOKED;
                invitations.save(old);
            }
        }
        Invitation inv = new Invitation();
        inv.companyId = c.id;
        inv.email = mail;
        inv.role = role.name();
        inv.permissions = Permission.join(granted);
        inv.token = UUID.randomUUID().toString().replace("-", "");
        inv.invitedBy = me.id;
        inv.expiresAt = Instant.now().plus(INVITE_TTL);
        inv = invitations.save(inv);

        email.send(mail, "Join " + c.name + " on SNAP-HIRE",
                me.name + " invited you to join " + c.name + " on SNAP-HIRE as " + role.name() + ".\n\n"
                        + "Invitation token: " + inv.token + "\n"
                        + "Open your invitation link to set your password; it expires " + inv.expiresAt + ".");
        audit.log(me.id, "COMPANY_INVITE", "Invitation", inv.id, mail + " role=" + role);
        return inv;
    }

    /** Lists invitations, lazily expiring any that ran past their deadline. */
    @Transactional
    public List<Invitation> listInvitations(Long companyId) {
        List<Invitation> out = invitations.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Instant now = Instant.now();
        for (Invitation inv : out) {
            if (inv.status == InvitationStatus.PENDING && inv.expiresAt != null && inv.expiresAt.isBefore(now)) {
                inv.status = InvitationStatus.EXPIRED;
                invitations.save(inv);
            }
        }
        return out;
    }

    @Transactional
    public void revoke(AppUser me, Long invitationId) {
        CompanyMember mine = members.findByUserId(me.id)
                .orElseThrow(() -> new ApiException(403, "You don't belong to a company"));
        require(me, Permission.MANAGE_RECRUITERS);
        Invitation inv = invitations.findById(invitationId)
                .orElseThrow(() -> new ApiException(404, "Invitation not found"));
        if (!inv.companyId.equals(mine.companyId)) throw new ApiException(404, "Invitation not found");
        if (inv.status == InvitationStatus.ACCEPTED) throw new ApiException(400, "This invitation was already accepted");
        if (inv.status == InvitationStatus.REVOKED) return;
        inv.status = InvitationStatus.REVOKED;
        invitations.save(inv);
        audit.log(me.id, "COMPANY_INVITE_REVOKED", "Invitation", inv.id, inv.email);
    }

    /**
     * Accepts an invitation. A brand new account is created with the invitee's chosen
     * password; an existing account must prove its own password first — an admin can
     * never set someone else's password.
     */
    @Transactional
    public AppUser accept(String token, String name, String password) {
        Invitation inv = invitations.findByToken(token)
                .orElseThrow(() -> new ApiException(404, "Invalid invitation link"));
        if (inv.status == InvitationStatus.PENDING && inv.expiresAt != null && inv.expiresAt.isBefore(Instant.now())) {
            inv.status = InvitationStatus.EXPIRED;
            invitations.save(inv);
        }
        if (inv.status == InvitationStatus.EXPIRED) throw new ApiException(400, "This invitation has expired");
        if (inv.status == InvitationStatus.REVOKED) throw new ApiException(400, "This invitation was revoked");
        if (inv.status == InvitationStatus.ACCEPTED) throw new ApiException(400, "This invitation was already used");
        if (password == null || password.isBlank()) throw new ApiException(400, "Password is required");
        Company c = companies.findById(inv.companyId).orElseThrow(() -> new ApiException(404, "Company not found"));
        if (c.status != CompanyStatus.ACTIVE) throw new ApiException(400, "This company is no longer active");
        Role role = parseInviteRole(inv.role);

        AppUser u = users.findByEmail(inv.email).orElse(null);
        if (u != null) {
            if (u.role == Role.ADMIN) throw new ApiException(403, "Administrator accounts cannot join companies");
            if (!encoder.matches(password, u.passwordHash)) throw new ApiException(401, "The password for this account is incorrect");
            if (members.findByUserId(u.id).isPresent()) throw new ApiException(409, "This account already belongs to a company");
            u.role = role;
            u = users.save(u);
        } else {
            if (name == null || name.isBlank()) throw new ApiException(400, "Your name is required");
            if (password.length() < 8) throw new ApiException(400, "Password must be at least 8 characters");
            u = new AppUser();
            u.email = inv.email;
            u.name = name.trim();
            u.role = role;
            u.passwordHash = encoder.encode(password);
            u = users.save(u);
        }

        CompanyMember m = new CompanyMember();
        m.companyId = c.id;
        m.userId = u.id;
        m.permissions = inv.permissions;
        m.invitedBy = inv.invitedBy;
        members.save(m);

        if (role == Role.RECRUITER && recruiters.findByUserId(u.id).isEmpty()) {
            RecruiterProfile rp = new RecruiterProfile();
            rp.userId = u.id;
            rp.companyName = c.name;
            if (c.website != null) rp.website = c.website;
            if (c.location != null) rp.location = c.location;
            recruiters.save(rp);
        }
        inv.status = InvitationStatus.ACCEPTED;
        inv.acceptedAt = Instant.now();
        invitations.save(inv);
        audit.log(u.id, "INVITE_ACCEPTED", "Company", c.id, inv.email + " joined as " + role);
        if (inv.invitedBy != null) {
            notifications.notify(inv.invitedBy, "INVITE_ACCEPTED",
                    u.name + " accepted your invitation to join " + c.name + ".", null, null);
        }
        attach(u);
        return u;
    }

    /* ---------------- verification ---------------- */

    @Transactional
    public Company setStatus(Long companyId, String rawStatus, AppUser admin) {
        Company c = companies.findById(companyId).orElseThrow(() -> new ApiException(404, "Company not found"));
        CompanyStatus s;
        try {
            s = CompanyStatus.valueOf(rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Unknown company status");
        }
        CompanyStatus old = c.status;
        c.status = s;
        if (s == CompanyStatus.ACTIVE) {
            c.verifiedBy = admin.name + " (" + admin.email + ")";
            c.verifiedAt = Instant.now();
        }
        c = companies.save(c);
        audit.log(admin.id, "COMPANY_" + s.name(), "Company", c.id,
                c.name + (old == s ? "" : " " + old + " -> " + s));
        if (s == CompanyStatus.ACTIVE && old != CompanyStatus.ACTIVE && c.ownerId != null) {
            notifications.notify(c.ownerId, "COMPANY_VERIFIED",
                    c.name + " has been verified. You can now invite recruiters and post jobs.", null, null);
        }
        return c;
    }

    public boolean autoVerifyEnabled() {
        return settings.findById(AUTO_VERIFY_KEY).map(s -> !"false".equalsIgnoreCase(s.value)).orElse(true);
    }

    /** Turns the toggle on or off; enabling flushes every company still awaiting verification. */
    @Transactional
    public AutoVerify setAutoVerify(boolean enabled, AppUser admin) {
        settings.save(new Setting(AUTO_VERIFY_KEY, String.valueOf(enabled)));
        int verified = 0;
        if (enabled) {
            for (Company c : companies.findByStatusOrderByCreatedAtDesc(CompanyStatus.PENDING_VERIFICATION)) {
                c.status = CompanyStatus.ACTIVE;
                c.verifiedBy = "AUTO";
                c.verifiedAt = Instant.now();
                companies.save(c);
                if (c.ownerId != null) {
                    notifications.notify(c.ownerId, "COMPANY_VERIFIED",
                            c.name + " has been verified. You can now invite recruiters and post jobs.", null, null);
                }
                verified++;
            }
        }
        audit.log(admin.id, enabled ? "COMPANY_AUTO_VERIFY_ON" : "COMPANY_AUTO_VERIFY_OFF", "Settings", null,
                verified + " compan(y/ies) verified");
        return new AutoVerify(enabled, verified);
    }

    /* ---------------- views ---------------- */

    /** Lightweight company representation (no bytes) shared by the admin and member views. */
    public Map<String, Object> companyView(Company c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id);
        m.put("name", c.name);
        m.put("emailDomain", c.emailDomain);
        m.put("website", c.website);
        m.put("description", c.description);
        m.put("industry", c.industry);
        m.put("location", c.location);
        m.put("status", c.status == null ? null : c.status.name());
        m.put("ownerId", c.ownerId);
        m.put("verifiedBy", c.verifiedBy);
        m.put("verifiedAt", c.verifiedAt);
        m.put("createdAt", c.createdAt);
        m.put("hasLogo", c.logo != null && c.logo.length > 0);
        m.put("logoMime", c.logoMimeType);
        m.put("documentCount", documents.countByCompanyId(c.id));
        m.put("memberCount", members.countByCompanyId(c.id));
        return m;
    }

    /* ---------------- helpers ---------------- */

    private static Role parseInviteRole(String raw) {
        String s = raw == null || raw.isBlank() ? "RECRUITER" : raw.trim().toUpperCase(Locale.ROOT);
        if (!s.equals("RECRUITER") && !s.equals("COMPANY_ADMIN")) {
            throw new ApiException(400, "Role must be RECRUITER or COMPANY_ADMIN");
        }
        return Role.valueOf(s);
    }

    static String normalizeDomain(String raw) {
        if (raw == null) return "";
        String d = raw.trim().toLowerCase(Locale.ROOT);
        int at = d.lastIndexOf('@');
        if (at >= 0) d = d.substring(at + 1);
        if (d.startsWith("www.")) d = d.substring(4);
        return d.replace("/", "").trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
