# HireLens – AI-Powered Intelligent Recruitment & Career Trajectory Platform

Two-sided recruitment platform (Candidate · Recruiter · Admin) built from the project PRD v3.0.
This repository implements **Stage 1 ("It works")** end to end, plus the explainable pieces of Stage 2 that fit without ML infrastructure (skill gaps, a basic Career Trajectory, cover-letter drafts, admin-tunable scoring weights).

**Stack:** Spring Boot 3 (Java 21) · PostgreSQL · JWT auth · React (Vite) · Docker · Render

## What is implemented (PRD stages)

**Stage 1 – "It works"**: registration/login with RBAC, manual profile + PDF/DOCX resume import (reviewable draft, never silently overwrites), skill normalisation, explainable profile score, job CRUD and search, rule-based matching, applications with duplicate protection, recruiter ranking, status pipeline, admin management and audit log.

**Stage 2 – "It is smart"**
- Weighted, explainable compatibility with a **semantic similarity** factor (TF-weighted cosine + skill-graph coverage; swappable for neural embeddings/pgvector later)
- **Skill-gap impact** ("adding Kafka would raise this match by +9%") with learning links
- **Career Trajectory**: several roadmaps with fit, gaps, projects, learning links and a count of open roles (click through to those jobs)
- **Job-specific resume optimisation** (re-orders/emphasises only what you already have), cover-letter drafts
- **Evidence & verification**: GitHub links auto-verified through GitHub's public API; certificates/other links reviewed by an admin; recruiters see *verified* vs *self-declared*
- **Evidence-aware ranking** (85% compatibility + 15% evidence) and recruiter **candidate search** with filters, recruiter analytics (funnel, average score, top skills)
- **External job ingestion** from a public job-board API (Arbeitnow) into the common Job model
- **Evaluation** page: Precision@3, Hit-rate@3, MRR, NDCG@5 on labelled demo personas

**Stage 3 – "It is automated"**
- Central **application workflow engine** (validation, duplicate protection, events, notifications, audit) used by every mode
- **Auto-apply rules** (min score, daily cap, locations, job types, title keywords) – internal jobs only, with an optimised resume + cover letter attached, run on job publish, on a schedule and on demand
- **External-assisted** applications: link + optimised package; you track it – the platform never submits to third-party sites
- **Notifications** (bell), **multiple resume versions** (job- and trajectory-specific), **interview preparation**, admin-tunable scoring weights

Still not built: real neural embeddings/pgvector, OAuth-based verification of third-party accounts, e-mail notifications.

## Demo data (loaded automatically, v2, idempotent)

18 candidates (fresher, senior, career-switcher, empty profile, keyword-stuffer with no evidence, private profile, strong-evidence, etc.), 3 recruiters/companies, 30 internal jobs (all levels/types/locations, one inactive) + 4 external listings, ~45 applications in every status plus auto-applied and externally-tracked ones, evidence in all statuses (verified / pending / rejected), auto-apply rules, resume versions and notifications.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@recruit.local` | `ADMIN_PASSWORD` (default `Admin@12345`) |
| Recruiters | `recruiter@demo.com`, `recruiter2@demo.com`, `recruiter3@demo.com` | `DEMO_PASSWORD` (default `Demo@1234`) |
| Candidates | `candidate@demo.com`, `priya.nair@demo.com`, `rohan.mehta@demo.com`, `sneha.kapoor@demo.com`, `arjun.reddy@demo.com`, `kavya.iyer@demo.com`, `vikram.singh@demo.com`, `ananya.das@demo.com`, `mohit.gupta@demo.com`, `ishita.joshi@demo.com`, `new.candidate@demo.com` (empty profile), `rahul.khanna@demo.com`, `neha.bansal@demo.com` (private), `sameer.ali@demo.com` (auto-apply on), `divya.menon@demo.com` (auto-apply on), `karan.malhotra@demo.com`, `pooja.sharma@demo.com`, `tarun.chawla@demo.com` | same demo password |

### "My login stopped working after deploying"
Accounts you registered on your **local** machine live in your local database, not in the deployed one – register again on the live site. Seeded accounts use the passwords from the environment variables you set on Render (blank values now fall back to the defaults above). To force-reset them, set `RESET_SEED_PASSWORDS=true` on the API service for one deploy, log in, then set it back to `false`.

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

**Seeded accounts**: see the table in *Demo data* above.


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
| `SEED_DEMO` `DEMO_PASSWORD` | true / Demo@1234 | Demo dataset (see above). Set `SEED_DEMO=false` for a real launch |
| `RESET_SEED_PASSWORDS` | false | `true` resets admin + `@demo.com` passwords to the configured values at startup |

## API overview

`POST /api/auth/register|login` · `GET /api/auth/me`
Candidate: `GET|PUT /api/candidate/profile` · `POST /api/candidate/resume/parse` · `GET /api/candidate/score|trajectories|recommendations` · `GET|POST|DELETE /api/candidate/evidence` · `GET|PUT /api/candidate/auto-apply` · `POST /api/candidate/auto-apply/run` · `GET|POST|DELETE /api/candidate/resume-versions`
Jobs: `GET /api/jobs?role=&source=` · `GET /api/jobs/{id}/impact` · `POST /api/jobs/{id}/optimize` · `GET /api/jobs/{id}/interview-prep` · `GET /api/jobs` · `GET /api/jobs/{id}` · `POST|PUT|DELETE /api/jobs` (recruiter) · `GET /api/jobs/mine` · `POST /api/jobs/{id}/cover-letter`
Applications: `POST /api/applications` · `GET /api/applications/mine` · `POST /api/applications/{id}/withdraw` · `GET /api/applications/job/{jobId}` (ranked) · `PATCH /api/applications/{id}/status` · `GET /api/applications/{id}/events`
Recruiter: `GET|PUT /api/recruiter/profile` · `GET /api/recruiter/stats` · `GET /api/recruiter/candidates`
Notifications: `GET /api/notifications` · `POST /api/notifications/read-all`
Admin: `POST /api/admin/ingest` · `GET /api/admin/evidence` · `PATCH /api/admin/evidence/{id}` · `GET /api/admin/evaluation` · `GET /api/admin/stats|users|jobs|audit|weights|skills` · `PATCH /api/admin/users/{id}/enabled` · `PATCH /api/admin/jobs/{id}/active` · `PUT /api/admin/weights`

## Scoring model (explainable)

`score = Σ weight × factor`, normalised to 0-100. Default weights: required skills 35, experience 20, projects/evidence 15, role relevance 10, education & certifications 10, semantic similarity 10 (admin-editable). An empty profile always scores 0%. This is a project-defined score, not an official ATS score, and never uses protected attributes.

## Project structure

```
backend/   Spring Boot API  (model · repo · security · service · web)
frontend/  React + Vite SPA (candidate / recruiter / admin pages)
render.yaml  docker-compose.yml
```
