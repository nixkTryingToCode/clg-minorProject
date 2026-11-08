package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true, nullable = false)
    public Long userId;
    public String headline;
    public String phone;
    public String location;
    public Double experienceYears;
    @Column(length = 2000)
    public String summary;
    @Column(length = 1000)
    public String preferredRoles;
    @Column(length = 3000)
    public String skills;
    @Column(length = 5000)
    public String education;
    @Column(length = 8000)
    public String experience;
    @Column(length = 8000)
    public String projects;
    @Column(length = 3000)
    public String certifications;
    public String resumeFileName;
    @Column(length = 20000)
    public String resumeText;
    /** MANUAL or RESUME_CONFIRMED (resume-derived data the candidate reviewed and saved). */
    public String profileSource = "MANUAL";
    public Instant updatedAt = Instant.now();
}
