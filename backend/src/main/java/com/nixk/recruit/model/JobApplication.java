package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(columnNames = {"candidateId", "jobId"}))
public class JobApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long candidateId;
    public Long jobId;
    @Enumerated(EnumType.STRING)
    public ApplicationStatus status = ApplicationStatus.APPLIED;
    public int score;
    @Column(length = 2000)
    public String matchedSkills;
    @Column(length = 2000)
    public String missingSkills;
    @Column(length = 5000)
    public String coverLetter;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
}
