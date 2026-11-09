package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "resume_versions")
public class ResumeVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long candidateId;
    /** Set for job-specific versions. */
    public Long jobId;
    /** Set for career-trajectory-specific versions. */
    public String targetRole;
    public String title;
    @Column(length = 20000)
    public String content;
    public Instant createdAt = Instant.now();
}
