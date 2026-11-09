package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeVersionRepo extends JpaRepository<ResumeVersion, Long> {
    List<ResumeVersion> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
