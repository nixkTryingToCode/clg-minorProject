package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepo extends JpaRepository<Company, Long> {
    Optional<Company> findFirstByEmailDomainIgnoreCase(String emailDomain);
    Optional<Company> findFirstByNameIgnoreCase(String name);
    List<Company> findByStatusOrderByCreatedAtDesc(CompanyStatus status);
    long countByStatus(CompanyStatus status);
}
