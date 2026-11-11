package com.nixk.recruit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/** A company that registered on the platform and waits for (or has passed) verification. */
@Entity
@Table(name = "companies")
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public String name;
    /** Official email domain, lower case and without the @. */
    @Column(unique = true)
    public String emailDomain;
    public String website;
    @Column(length = 4000)
    public String description;
    public String industry;
    public String location;
    /** Logo bytes are served by a dedicated endpoint, never embedded in JSON. */
    @JsonIgnore
    @Column(columnDefinition = "bytea")
    public byte[] logo;
    public String logoMimeType;
    @Enumerated(EnumType.STRING)
    public CompanyStatus status = CompanyStatus.PENDING_VERIFICATION;
    /** User who registered the company (always a COMPANY_ADMIN). */
    public Long ownerId;
    public String verifiedBy;
    public Instant verifiedAt;
    public Instant createdAt = Instant.now();
}
