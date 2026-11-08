package com.nixk.recruit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

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
}
