package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepo extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByToken(String token);
    List<Invitation> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<Invitation> findByCompanyIdAndEmailIgnoreCaseOrderByCreatedAtDesc(Long companyId, String email);
}
