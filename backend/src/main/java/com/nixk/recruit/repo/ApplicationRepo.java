package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepo extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
    List<JobApplication> findByJobId(Long jobId);
    Optional<JobApplication> findByCandidateIdAndJobId(Long candidateId, Long jobId);
}
