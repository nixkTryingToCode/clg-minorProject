# HireLens – AI-Powered Intelligent Recruitment & Career Trajectory Platform

Two-sided recruitment platform (Candidate · Recruiter · Admin) built from the project PRD v3.0.
This repository implements **Stage 1 ("It works")** end to end, plus the explainable pieces of Stage 2 that fit without ML infrastructure (skill gaps, a basic Career Trajectory, cover-letter drafts, admin-tunable scoring weights).

**Stack:** Spring Boot 3 (Java 21) · PostgreSQL · JWT auth · React (Vite) · Docker · Render

## Features by PRD requirement

| PRD | What is implemented |
|---|---|
| FR-01 Auth & RBAC | Register/login, BCrypt, JWT, server-side role checks (`CANDIDATE`, `RECRUITER`, `ADMIN`), admin can disable users |
| FR-02/06 Profile | Manual profile editing; skills normalised through an alias taxonomy |
| FR-04/05 Resume upload & parsing | PDF/DOCX (PDFBox / Apache POI), file-type + size validation, section detection, skill/email/phone/experience extraction |
| FR-06/Appendix B | Parsed data is a **draft**: blank fields are filled, differing values are only *suggested*; nothing is saved until the candidate clicks Save |
| FR-07 Provenance | Profile marks `RESUME_CONFIRMED` vs `MANUAL`; recruiters see skills labelled **self-declared** |
| FR-09 Profile score | Explainable 0-100 score with category breakdown and improvement tips |
| FR-10 Career Trajectory | Several role roadmaps with fit %, strengths, missing skills, suggested projects, matching open jobs |
| FR-11 Jobs | Recruiter create / edit / publish / unpublish / delete; search by keyword, location, type |
| FR-13/14/15 Matching | Rule-based, explainable compatibility (skills, experience, projects/evidence, role, education) with matched/missing skills and reasons; ranked recommendations |
| FR-16 Ranking | Recruiter sees applicants ranked by live job-specific score, with filters (status, min score, skill) |
| FR-18/23/25 Applications | Apply (duplicate protection), withdraw, recruiter pipeline (Screening → Shortlisted → Interview → Selected/Rejected), per-application event history, audit log |
| FR-22 | Template cover-letter draft built only from facts in the candidate profile |
| FR-24 Admin | Stats, user enable/disable, job moderation, audit log, scoring-weight configuration |

Not yet built (Stage 2/3 in the PRD): embeddings/pgvector semantic matching, external job ingestion, evidence verification integrations, auto-apply, notifications.

## Run locally

Requirements: JDK 21, Maven 3.9+, Node 20+. (PostgreSQL optional.)

```bash
# 1) Backend – in-memory H2 database, no setup needed
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev      # http://localhost:8080/api/health

# 2) Frontend
cd frontend
cp .env.example .env
npm install
npm run dev                                            # http://localhost:5173
```

With PostgreSQL instead: `docker compose up --build` (starts Postgres + API), then run the frontend as above.

**Seeded accounts** (first start, `SEED_DEMO=true`):

| Role | Email | Password |
|---|---|---|
| Admin | `admin@recruit.local` | `Admin@12345` (override with `ADMIN_PASSWORD`) |
| Recruiter | `recruiter@demo.com` | `Demo@1234` (override with `DEMO_PASSWORD`) |
| Candidate | `candidate@demo.com` | `Demo@1234` |

Run the tests: `cd backend && mvn test`

## Push to GitHub

```bash
cd recruitment-platform
git init -b main            # skip if the folder already has a .git
git add .
git commit -m "Initial commit: HireLens recruitment platform"
# create an EMPTY repo on github.com (no README), then:
git remote add origin https://github.com/<your-username>/<repo-name>.git
git push -u origin main
```

## Deploy (free tiers)

### Option A – Render Blueprint (API + DB + site in one go)

1. Push the repo to GitHub (above).
2. Render dashboard → **New → Blueprint** → pick the repo. Render reads `render.yaml` and creates the PostgreSQL database, `recruit-api` (Docker) and `recruit-web` (static).
3. When prompted, set `ADMIN_PASSWORD` and `DEMO_PASSWORD` to strong values. Leave `CORS_ORIGINS` and `VITE_API_URL` empty for now.
4. After the first deploy copy the two URLs, then set:
   - `recruit-web` → Environment → `VITE_API_URL` = `https://<your-api>.onrender.com` → **Manual Deploy**
   - `recruit-api` → Environment → `CORS_ORIGINS` = `https://<your-web>.onrender.com` (no trailing slash) → redeploy
5. Open the web URL and log in.

Notes: free Render web services sleep after ~15 min idle (first request takes ~30-60 s) and the free Postgres expires after 30 days – use Neon/Supabase for a longer-lived database (set `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require`, `DB_USER`, `DB_PASSWORD`).

### Option B – Frontend on Vercel/Netlify
Import the repo, set root directory to `frontend`, build command `npm run build`, output `dist`, env `VITE_API_URL=<api url>`. (`vercel.json` and `public/_redirects` already handle SPA routing.) Add the resulting URL to the API's `CORS_ORIGINS` (comma separate multiple origins).

## Environment variables (API)

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | localhost / 5432 / recruit / postgres / postgres | PostgreSQL connection (or set the full `DB_URL`) |
| `JWT_SECRET` | dev value | **Must** be a long random string (≥ 32 chars) in production |
| `JWT_EXPIRY_HOURS` | 24 | Token lifetime |
| `CORS_ORIGINS` | http://localhost:5173 | Allowed frontend origins, comma separated |
| `ADMIN_EMAIL` `ADMIN_PASSWORD` | admin@recruit.local / Admin@12345 | Admin created on first start |
| `SEED_DEMO` `DEMO_PASSWORD` | true / Demo@1234 | Demo recruiter, candidate and 8 sample jobs. Set `SEED_DEMO=false` for a real launch |

## API overview

`POST /api/auth/register|login` · `GET /api/auth/me`
Candidate: `GET|PUT /api/candidate/profile` · `POST /api/candidate/resume/parse` · `GET /api/candidate/score|trajectories|recommendations`
Jobs: `GET /api/jobs` · `GET /api/jobs/{id}` · `POST|PUT|DELETE /api/jobs` (recruiter) · `GET /api/jobs/mine` · `POST /api/jobs/{id}/cover-letter`
Applications: `POST /api/applications` · `GET /api/applications/mine` · `POST /api/applications/{id}/withdraw` · `GET /api/applications/job/{jobId}` (ranked) · `PATCH /api/applications/{id}/status` · `GET /api/applications/{id}/events`
Recruiter: `GET|PUT /api/recruiter/profile` · `GET /api/recruiter/stats`
Admin: `GET /api/admin/stats|users|jobs|audit|weights|skills` · `PATCH /api/admin/users/{id}/enabled` · `PATCH /api/admin/jobs/{id}/active` · `PUT /api/admin/weights`

## Scoring model (explainable)

`score = Σ weight × factor`, normalised to 0-100. Default weights: required skills 40, experience 20, projects/evidence 15, role relevance 15, education & certifications 10 (admin-editable). This is a project-defined score, not an official ATS score, and never uses protected attributes.

## Project structure

```
backend/   Spring Boot API  (model · repo · security · service · web)
frontend/  React + Vite SPA (candidate / recruiter / admin pages)
render.yaml  docker-compose.yml
```
