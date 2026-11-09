package com.nixk.recruit.repo;

import com.nixk.recruit.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoApplyRuleRepo extends JpaRepository<AutoApplyRule, Long> {
    Optional<AutoApplyRule> findByCandidateId(Long candidateId);
    List<AutoApplyRule> findByEnabledTrue();
}
