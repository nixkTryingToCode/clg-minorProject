package com.nixk.recruit.service;

import com.nixk.recruit.model.CandidateProfile;
import com.nixk.recruit.repo.CandidateProfileRepo;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ProfileService {
    private final CandidateProfileRepo repo;

    public ProfileService(CandidateProfileRepo repo) {
        this.repo = repo;
    }

    public CandidateProfile getOrCreate(Long userId) {
        return repo.findByUserId(userId).orElseGet(() -> {
            CandidateProfile p = new CandidateProfile();
            p.userId = userId;
            return repo.save(p);
        });
    }

    public CandidateProfile save(CandidateProfile p) {
        p.updatedAt = Instant.now();
        return repo.save(p);
    }
}
