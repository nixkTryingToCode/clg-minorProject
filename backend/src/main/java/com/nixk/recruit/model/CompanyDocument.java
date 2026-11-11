package com.nixk.recruit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

/** Verification document uploaded with a company registration. */
@Entity
@Table(name = "company_documents")
public class CompanyDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long companyId;
    public String fileName;
    public String mimeType;
    /** Document bytes are served by a dedicated endpoint, never embedded in JSON. */
    @JsonIgnore
    @Column(columnDefinition = "bytea")
    public byte[] data;
    public Instant uploadedAt = Instant.now();
}
