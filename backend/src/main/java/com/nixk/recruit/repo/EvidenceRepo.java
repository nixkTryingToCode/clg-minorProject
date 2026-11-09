package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepo extends JpaRepository<Evidence, Long> {
    List<Evidence> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
    List<Evidence> findByStatusOrderByCreatedAtDesc(String status);
}
