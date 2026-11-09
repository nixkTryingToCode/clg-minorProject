package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auto_apply_rules")
public class AutoApplyRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true)
    public Long candidateId;
    public Boolean enabled = false;
    public Integer minScore = 70;
    public Integer maxPerDay = 3;
    /** comma separated; empty = any. A job in a listed location, or a remote job, qualifies. */
    public String locations;
    public String jobTypes;
    /** comma separated words; job title must contain one of them. Empty = any. */
    public String roleKeywords;
    public Instant updatedAt = Instant.now();
}
