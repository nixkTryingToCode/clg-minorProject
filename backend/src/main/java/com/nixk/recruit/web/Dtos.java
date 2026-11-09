package com.nixk.recruit.web;

import com.nixk.recruit.model.*;
import com.nixk.recruit.service.MatchingService.MatchResult;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(@NotBlank String name, @NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
                                  String role, String companyName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, AppUser user) {}

    public record ProfileRequest(String headline, String phone, String location, Double experienceYears,
                                 String summary, String preferredRoles, String skills, String education,
                                 String experience, String projects, String certifications,
                                 String resumeFileName, String resumeText, String profileSource,
                                 Boolean openToRecruiters) {}

    public record RecruiterProfileRequest(String companyName, String website, String location, String designation) {}

    public record JobRequest(@NotBlank String title, String company, @NotBlank String description,
                             String requiredSkills, String location, String employmentType,
                             Double minExperience, String externalUrl, Boolean active) {}

    public record ApplyRequest(@NotNull Long jobId, String coverLetter, String mode, Long resumeVersionId) {}

    public record StatusRequest(@NotBlank String status) {}

    public record EnabledRequest(boolean enabled) {}

    public record EvidenceRequest(String type, String title, String url, String skill) {}

    public record ReviewRequest(String status, String note) {}

    public record AutoApplyRequest(Boolean enabled, Integer minScore, Integer maxPerDay, String locations,
                                   String jobTypes, String roleKeywords) {}

    public record IngestRequest(Integer limit) {}

    public record JobView(Job job, MatchResult match, boolean applied, Long applicationId) {}

    public record MyApplicationView(JobApplication application, String jobTitle, String company, String jobSource, String externalUrl) {}

    public record ApplicantView(JobApplication application, String candidateName, String candidateEmail,
                                CandidateProfile profile, MatchResult match, List<Evidence> evidence,
                                int evidenceScore, int rankScore) {}

    public record CandidateCard(Long userId, String name, String headline, String location, Double experienceYears,
                                String skills, int verifiedEvidence, int evidenceScore, MatchResult match) {}
}
