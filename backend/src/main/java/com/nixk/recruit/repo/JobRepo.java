package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepo extends JpaRepository<Job, Long> {
    List<Job> findByActiveTrueOrderByCreatedAtDesc();
    List<Job> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
    Optional<Job> findFirstBySourceJobId(String sourceJobId);
}
