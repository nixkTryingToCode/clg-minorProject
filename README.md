# SNAP-HIRE

SNAP-HIRE is a recruitment platform with three roles: candidates, recruiters and administrators.
Candidates build a profile from a resume, browse jobs and follow their applications from applied to
selected. Recruiters post jobs and get a ranked list of applicants for each opening. Administrators
run the platform: users, evidence verification, audit trail and the weights used for scoring.

Match scores are explainable. Every score can be broken down into the factors behind it, and
supporting evidence such as GitHub links and certificates is labelled as verified or self-declared
so a recruiter knows what they are looking at.

## Features

**Candidates**
- Profile created by hand or imported from a PDF/DOCX resume, always shown as a draft for review before it is saved
- Profile score and per-job compatibility broken down by factor, with the skills that would improve a match
- Job search and applications with duplicate protection and a full status history
- Career trajectories: recommended paths with the gaps, projects and learning links for each
- Job-specific resume versions, cover-letter drafts and interview preparation
- Auto-apply rules for internal jobs (minimum score, daily cap, location and title filters)
- Evidence section for GitHub links and certificates, reviewed by an administrator

**Recruiters**
- Post, edit and close jobs
- Applicant pipeline with status changes and events
- Candidate search with filters, ranked by compatibility and evidence
- Funnel, average score and top-skill analytics

**Administrators**
- User and job management, evidence verification, audit log
- Tunable scoring weights and evaluation metrics
- Ingestion of external listings into the common job board

## Tech stack

- Java 21 and Spring Boot 3, PostgreSQL (in-memory H2 for local development)
- JWT authentication with role-based access control
- React 18 with Vite
- Docker, deployed on Render

## Demo accounts

On first start the database is seeded with 18 candidates, 3 recruiters, 30 internal jobs,
4 external listings and applications in every stage of the pipeline.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@recruit.local` | `Admin@12345` |
| Recruiter | `recruiter@demo.com`, `recruiter2@demo.com`, `recruiter3@demo.com` | `Demo@1234` |
| Candidate | `candidate@demo.com`, `priya.nair@demo.com`, `rohan.mehta@demo.com`, `sneha.kapoor@demo.com`, `kavya.iyer@demo.com`, `sameer.ali@demo.com` and the other seeded candidates | `Demo@1234` |

## Project structure

```
backend/     Spring Boot API (model, repositories, security, services, controllers)
frontend/    React + Vite single page app (candidate, recruiter and admin views)
render.yaml  Deployment blueprint for the API, database and site
```
