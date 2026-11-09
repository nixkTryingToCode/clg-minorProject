package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "evidence")
public class Evidence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long candidateId;
    /** GITHUB, LEETCODE, HACKERRANK, KAGGLE, LINKEDIN, CERTIFICATE, PORTFOLIO, OTHER */
    public String type;
    public String title;
    @Column(length = 1000)
    public String url;
    /** Skill this evidence supports (optional). */
    public String skill;
    /** PENDING, VERIFIED or REJECTED */
    public String status = "PENDING";
    /** e.g. AUTO:GITHUB_API or ADMIN */
    public String verifiedBy;
    @Column(length = 1000)
    public String metadata;
    public Instant createdAt = Instant.now();
    public Instant checkedAt;
}
