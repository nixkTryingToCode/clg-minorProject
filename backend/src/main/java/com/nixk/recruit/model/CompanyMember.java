package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Links a user to exactly one company, with the permissions they were granted. */
@Entity
@Table(name = "company_members", uniqueConstraints = @UniqueConstraint(columnNames = "userId"))
public class CompanyMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long companyId;
    public Long userId;
    /** Comma separated com.nixk.recruit.service.Permission names. */
    @Column(length = 500)
    public String permissions;
    @Enumerated(EnumType.STRING)
    public MemberStatus status = MemberStatus.ACTIVE;
    public Long invitedBy;
    public Instant joinedAt = Instant.now();
}
