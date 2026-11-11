package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Invite token handed to a recruiter so they can set their own password and join a company. */
@Entity
@Table(name = "invitations")
public class Invitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long companyId;
    public String email;
    /** Role the invitee receives on acceptance: RECRUITER or COMPANY_ADMIN. */
    public String role = "RECRUITER";
    /** Comma separated com.nixk.recruit.service.Permission names granted on acceptance. */
    @Column(length = 500)
    public String permissions;
    @Column(unique = true)
    public String token;
    public Long invitedBy;
    public Instant expiresAt;
    @Enumerated(EnumType.STRING)
    public InvitationStatus status = InvitationStatus.PENDING;
    public Instant acceptedAt;
    public Instant createdAt = Instant.now();
}
