package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMemberRepo extends JpaRepository<CompanyMember, Long> {
    Optional<CompanyMember> findByUserId(Long userId);
    List<CompanyMember> findByCompanyIdOrderByJoinedAtAsc(Long companyId);
    long countByCompanyId(Long companyId);
}
