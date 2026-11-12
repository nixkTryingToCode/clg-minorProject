# SNAP-HIRE

![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![React 18](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![Vite 5](https://img.shields.io/badge/Vite-5-646CFF?logo=vite&logoColor=white)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)
[![Live demo](https://img.shields.io/badge/Live%20demo-Render-46E3B7)](https://recruit-web-ok6s.onrender.com/)

SNAP-HIRE is a recruitment platform with three roles: candidates, recruiters and administrators.
Candidates build a profile from a resume, browse jobs and follow their applications from applied to
selected. Recruiters post jobs and get a ranked list of applicants for each opening, either as
independent recruiters or as members of a verified company organisation. Administrators run the
platform: users, company verification, evidence verification, audit trail and the weights used for
scoring.

Match scores are explainable. Every score can be broken down into the factors behind it, and
supporting evidence such as GitHub links and certificates is labelled as verified or self-declared
so a recruiter knows what they are looking at.

## Live demo

| | |
|---|---|
| **Web app** | https://recruit-web-ok6s.onrender.com/ |
| **API** | https://recruit-api-w632.onrender.com |
| **API health** | https://recruit-api-w632.onrender.com/api/health |

Log in with any of the [demo accounts](#demo-accounts) below.

> **Note:** both services run on Render's free tier, so the API sleeps after a period of inactivity.
> The first request after idle can take up to a minute while it wakes up; the web app itself loads
> immediately.

## Features

**Candidates**
- Profile created by hand or imported from a PDF/DOCX resume, always shown as a draft for review before it is saved
- Profile score and per-job compatibility broken down by factor, with the skills that would improve a match
- Job search and applications with duplicate protection and a full status history
- Career trajectories: recommended paths with the gaps, projects and learning links for each
- Job-specific resume versions, cover-letter drafts and interview preparation
- Auto-apply rules for internal jobs (minimum score, daily cap, location and title filters)
- Evidence section for profile links and certificates: GitHub and LeetCode are verified through
  their public APIs, other profile links through reachability checks, and certificates always
  through administrator review

**Recruiters and companies**
- Post, edit and close jobs; jobs posted from inside a company carry its verified name
- Applicant pipeline with status changes and events
- Candidate search with filters, ranked by compatibility and evidence
- Funnel, average score and top-skill analytics
- Company registration with domain, website, description, industry, location, logo and verification documents
- Admin verification queue with an "auto verify" toggle that can be turned off again at any time
- Company admins invite recruiters by email with a role and granular permissions; invitees choose their own password
- Membership management: grant or revoke the eight permissions, suspend and reactivate members
- Company-wide job, application and hire analytics

**Administrators**
- User and job management, audit log
- Evidence review with an automatic-check toggle that can be turned off again at any time
- Company verification: review registrations and their documents, activate, reject or suspend
- Tunable scoring weights and evaluation metrics
- Ingestion of external listings into the common job board

## Tech stack

- Java 21 and Spring Boot 3.3.5, PostgreSQL (in-memory H2 for local development)
- JWT authentication with role-based access control
- React 18 with Vite 5, React Router
- Docker, deployed on Render (blueprint in `render.yaml`)

## Architecture

```
┌────────────────────────────┐
│  React SPA (Render static) │  recruit-web-ok6s.onrender.com
└─────────────┬──────────────┘
              │  HTTPS, VITE_API_URL
              ▼
┌────────────────────────────┐        ┌─────────────────────┐
│  Spring Boot API (Docker)  │───────►│  PostgreSQL         │
│  recruit-api-w632...       │        │  Render database    │
└─────────────┬──────────────┘        └─────────────────────┘
              │
              ├── GitHub / LeetCode public APIs  (evidence auto-checks)
              └── Arbeitnow API                  (external job ingestion)

Local dev: docker-compose.yml runs Postgres + the API in containers,
or the backend can run alone on an in-memory H2 profile.
```

The backend is a monolithic Spring Boot app organised as `web` (11 controllers, ~75 endpoints),
`service` (31 services: matching, scoring, verification, resume parsing, auto-apply, ingestion,
seeding), `repo` (16 Spring Data repositories) and `model` (16 JPA entities). See
[`backend/functionality.md`](backend/functionality.md) for a function-by-function breakdown.

## Quick start

### Prerequisites

- Java 21 + Maven (or use the included `mvnw` wrapper)
- Node.js 18+
- Docker Desktop (only for the full local stack)

### Option A — full stack with Docker

```bash
docker compose up --build          # Postgres on :5432, API on :8080
cd frontend
npm install
npm run dev                        # http://localhost:5173
```

The API seeds demo data on first start (`SEED_DEMO=true`), so you can log in immediately
with the demo accounts below.

### Option B — backend alone (no Docker, in-memory H2)

```bash
cd backend
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev      # Windows
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev          # macOS/Linux
```

Then run the frontend as above. Data lives in memory and resets on restart.

### Verify

```bash
curl http://localhost:8080/api/health     # {"status":"UP"}
cd backend && ./mvnw.cmd test             # 26 tests
cd frontend && npm run build              # production bundle in dist/
```

## Configuration

| Variable | Component | Default | Purpose |
|---|---|---|---|
| `PORT` | backend | `8080` | HTTP port (Render sets this automatically) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | backend | `localhost` / `recruit` / `postgres` | PostgreSQL connection |
| `DB_URL` | backend | derived from the `DB_*` values | full JDBC URL override |
| `JWT_SECRET` | backend | dev-only fallback | token signing key |
| `JWT_EXPIRY_HOURS` | backend | `24` | access-token lifetime |
| `CORS_ORIGINS` | backend | `http://localhost:5173` | allowed browser origins (comma separated) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | backend | `admin@recruit.local` / `Admin@12345` | seeded administrator |
| `SEED_DEMO` | backend | `true` | seed the demo users, companies and jobs |
| `DEMO_PASSWORD` | backend | `Demo@1234` | password given to every seeded demo user |
| `VITE_API_URL` | frontend (build time) | `http://localhost:8080` | API base URL baked into the bundle |

`frontend/.env.example` documents the frontend variable; all backend variables have safe
local defaults in `backend/src/main/resources/application.yml`.

## Deployment (Render)

[`render.yaml`](render.yaml) is a blueprint that provisions everything from one push:

| Resource | Type | Notes |
|---|---|---|
| `recruit-db` | PostgreSQL (free) | connection injected into the API via `fromDatabase` |
| `recruit-api` | Web service (Docker) | builds `backend/Dockerfile`, health check on `/api/health` |
| `recruit-web` | Static site | `npm install && npm run build`, publishes `frontend/dist`, SPA rewrite to `index.html` |

To deploy your own copy:

1. Fork the repository and create a **Blueprint** in Render pointing at it.
2. Set the environment variables marked `sync: false` in the dashboard:
   - `CORS_ORIGINS` → your web URL (e.g. `https://recruit-web-ok6s.onrender.com`)
   - `VITE_API_URL` → your API URL (e.g. `https://recruit-api-w632.onrender.com`)
   - optionally `ADMIN_PASSWORD` and `DEMO_PASSWORD` to override the seeded credentials
3. Deploy — the API, database and site are created together, and subsequent pushes to the main
   branch redeploy automatically.

## API overview

All routes are under `/api` and require a JWT except `register`, `login`, `health` and the
public job listing.

| Group | Base path | Highlights |
|---|---|---|
| Health | `/api/health` | liveness probe used by Render |
| Auth | `/api/auth` | register, login, current user |
| Jobs | `/api/jobs` | search and list, apply, edit/close, per-job impact, cover letter, resume optimisation, interview prep |
| Applications | `/api/applications` | apply, my applications, withdraw, status changes, event history |
| Candidate | `/api/candidate` | profile, PDF/DOCX resume parse, score, trajectories, evidence, auto-apply rules, resume versions |
| Recruiter | `/api/recruiter` | recruiter profile, pipeline stats, ranked candidate search |
| Companies | `/api/companies` | registration, logo and documents, invitations, member permissions, analytics |
| Invitations | `/api/invitations` | look up and accept an invitation by token |
| Admin | `/api/admin` | stats, users, jobs, audit log, scoring weights, skills, evidence, evaluation, external ingestion |
| Admin companies | `/api/admin/companies` | verification queue, documents, auto-verify toggle |
| Notifications | `/api/notifications` | list notifications, mark all read |

## Demo accounts

On first start the database is seeded with 18 candidates, recruiter and company-admin accounts,
4 companies (one still awaiting verification), 30 internal jobs, 4 external listings and
applications in every stage of the pipeline.

| Role | Email | Password |
|---|---|---|
| Admin | `admin@recruit.local` | `Admin@12345` |
| Company admin | `owner@demo.com` (TechNova Solutions), `recruiter2@demo.com` (FinEdge Capital), `recruiter3@demo.com` (CloudKart), `zenith@demo.com` (Zenith Labs, awaiting verification) | `Demo@1234` |
| Recruiter | `recruiter@demo.com` (member of TechNova Solutions) | `Demo@1234` |
| Candidate | `candidate@demo.com`, `priya.nair@demo.com`, `rohan.mehta@demo.com`, `sneha.kapoor@demo.com`, `kavya.iyer@demo.com`, `sameer.ali@demo.com` and the other seeded candidates | `Demo@1234` |

## Testing

```bash
cd backend
./mvnw.cmd test
```

26 tests across four suites: resume parsing, evidence auto-check verification (unit and
`@SpringBootTest` integration), and company onboarding. Tests run against an in-memory H2
database with demo seeding disabled, so no PostgreSQL or network access is needed.

## Project structure

```
backend/     Spring Boot API (model, repositories, security, services, controllers)
             backend/functionality.md documents every backend function and how the
             complex ones (matching, verification, auto-apply, ingestion) work
frontend/    React + Vite single page app (candidate, recruiter and admin views)
render.yaml  Deployment blueprint for the API, database and site
docker-compose.yml  Local full stack: Postgres + API
```
