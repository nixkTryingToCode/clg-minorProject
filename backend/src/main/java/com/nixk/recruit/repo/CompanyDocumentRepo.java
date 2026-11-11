package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyDocumentRepo extends JpaRepository<CompanyDocument, Long> {
    List<CompanyDocument> findByCompanyIdOrderByUploadedAtDesc(Long companyId);
    long countByCompanyId(Long companyId);
}
