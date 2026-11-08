package com.nixk.recruit.model;

import jakarta.persistence.*;

@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true, nullable = false)
    public Long userId;
    public String companyName;
    public String website;
    public String location;
    public String designation;
}
