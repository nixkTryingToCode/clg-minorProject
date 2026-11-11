package com.nixk.recruit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true, nullable = false)
    public String email;
    @JsonIgnore
    public String passwordHash;
    public String name;
    @Enumerated(EnumType.STRING)
    public Role role;
    public boolean enabled = true;
    public Instant createdAt = Instant.now();

    /* ---- company context, filled on every auth response (never persisted) ---- */
    @Transient public Long companyId;
    @Transient public String companyName;
    @Transient public CompanyStatus companyStatus;
    /** Permission names granted by the company membership; null when the user has no company. */
    @Transient public List<String> permissions;
}
