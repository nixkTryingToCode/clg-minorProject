package com.nixk.recruit.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "jobs")
public class Job {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long recruiterId;
    /** RECRUITER, DEMO or EXTERNAL */
    public String source = "RECRUITER";
    public String title;
    public String company;
    @Column(length = 8000)
    public String description;
    @Column(length = 2000)
    public String requiredSkills;
    public String location;
    public String employmentType;
    public Double minExperience;
    public String externalUrl;
    /** Id of the job in the external source, used for de-duplication. */
    public String sourceJobId;
    public boolean active = true;
    public Instant createdAt = Instant.now();
}
