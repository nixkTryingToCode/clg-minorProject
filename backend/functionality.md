# SNAP-HIRE Backend — Functionality Guide

Every function in this backend, what it is for, and — for the complex ones — exactly how it works.
The API is a standard Spring Boot layered application:

```
web/        Controllers: HTTP endpoints, validation, auth context
service/    Business logic: matching, scoring, verification, workflows
repo/       Spring Data JPA repositories (one interface per table)
model/      JPA entities and status enums
security/   JWT issuing, request filtering, route rules
```

A request flows **controller → service → repository → database**. Controllers never contain
business rules; services never touch HTTP. Cross-cutting concerns (audit log, notifications)
are injected as services and called from the services that change state.

---

## 1. Security & authentication

| Function | Use |
|---|---|
| `JwtService.generate(user)` | Signs a JWT with the user id + role, HMAC key from `app.jwt-secret`, expiry from `app.jwt-expiry-hours`. |
| `JwtService.parse(token)` | Verifies signature and expiry, returns the claims. |
| `JwtFilter` | Runs on every request: reads `Authorization: Bearer …`, parses the token, puts the user into the `CurrentUser` context. |
| `SecurityConfig` | Route rules: `/api/auth/**`, `/api/health`, `/api/companies/register`, `/api/invitations/**` and OPTIONS are public; everything else needs a valid token. CORS origins come from `app.cors-origins`. |
| `CurrentUser.get()` | Returns the authenticated `AppUser` for the current request (set by the filter). |

Roles: `CANDIDATE`, `RECRUITER`, `COMPANY_ADMIN`, `ADMIN`. Controllers additionally use
`@PreAuthorize` (e.g. `hasRole('ADMIN')` on all of `/api/admin`) and the service-layer
`CompanyService.require(...)` for per-company permissions (see §7).

`ApiException(status, message)` is thrown anywhere in services; `ApiExceptionHandler`
converts it into `{"error": "…"}` with the right HTTP status. Other handlers map validation
errors → 400, denied permissions → 403, duplicate keys → 409, oversized uploads → 400,
anything unexpected → 500.

---

## 2. Domain model

| Entity | Key fields | Notes |
|---|---|---|
| `AppUser` | email (unique), password (BCrypt), name, role, enabled | Self-signup creates `CANDIDATE` only. |
| `CandidateProfile` | headline, skills, experience, projects, education, certifications, resumeText, profileSource | One per candidate; created empty at registration. |
| `Job` | title, company, description, requiredSkills, location, employmentType, minExperience, active, source (`null`/`EXTERNAL`), sourceJobId, companyId, recruiterId | `source=EXTERNAL` = imported listing; never auto-applied. |
| `JobApplication` | candidateId, jobId, status, score, mode (`MANUAL`/`AUTO`/`EXTERNAL_ASSISTED`), resumeVersionId, coverLetter, matchedSkills, missingSkills | One per candidate+job (duplicate protected). |
| `ApplicationEvent` | applicationId, eventType, details | Timeline shown to candidate and recruiter. |
| `Evidence` | candidateId, type, url, skill, status, verifiedBy, metadata, checkedAt | See §8. |
| `Company` | name, emailDomain, website, industry, status, verifiedBy, verifiedAt | See §7. |
| `CompanyMember` | userId, companyId, role, status, permissions (CSV) | The 8 permissions live here. |
| `Invitation` | token, email, role, permissions, status, expiresAt | 7-day TTL, 32-hex token. |
| `AutoApplyRule` | candidateId, enabled, minScore, maxPerDay, locations, jobTypes, roleKeywords | One per candidate. |
| `ResumeVersion` | candidateId, jobId, title, content | Per-job tailored resumes. |
| `Setting` | key, value | All admin-configurable knobs (see §12). |
| `Notification` | userId, type, message, linkId, jobId, seen | Type drives the frontend deep link. |
| `AuditLog` | actorId, action, entity, entityId, details | Last 100 shown in admin. |

**Status enums**

- `ApplicationStatus`: `APPLIED → SCREENING → SHORTLISTED → INTERVIEW → SELECTED`, plus `REJECTED`, `WITHDRAWN`
- `CompanyStatus`: `PENDING_VERIFICATION`, `ACTIVE`, `REJECTED`, `SUSPENDED`
- Evidence: `PENDING`, `VERIFIED`, `REJECTED`
- `InvitationStatus`: `PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED` (expiry is checked lazily on read)

---

## 3. Matching engine (`MatchingService`) — how the score works

**Use:** every compatibility number in the product — job recommendations, applicant ranking,
auto-apply thresholds, evaluation — comes from `compute(profile, job)`.

**How it works (six weighted factors):**

1. **Required skills** — job's `requiredSkills` are normalized through `SkillCatalog`; a skill
   counts as *matched* if the candidate lists it **or** it appears in their projects/experience
   text. Score = matched / required (0.5 if the job requires nothing).
2. **Experience** — `min(1, candidateYears / jobMinYears)`; 1.0 when the job asks for none.
3. **Projects/evidence** — fraction of required skills found specifically in projects/experience
   (proves the skill was used, not just claimed).
4. **Role relevance** — token overlap between the job title and the candidate's headline,
   preferred roles and experience (stop-words removed).
5. **Education & certifications** — 0.6 if education present + 0.4 if certifications present.
6. **Semantic similarity** — from `SimilarityService` (below).

Final score = `round(100 × Σ(weightᵢ × factorᵢ) / Σweights)`, clamped 0–100, with the six
factors returned as a percentage `breakdown`, plus `matched`, `missing` and human-readable
`reasons` — that is what makes every score explainable in the UI.

**Admin weights** (`SettingsService`): defaults `skills 35, experience 20, projects 15,
role 10, education 10, semantic 10`, stored as `weight.<key>` settings, each 0–100 with a
sum > 0. Changing them instantly changes every score in the system.

**`skillImpact(profile, job)`** — "what if" analysis: for each missing skill, temporarily add
it to the profile, recompute, and report the point gain (sorted desc, top 6) with a learning
link. This powers the "improve your match" tips.

### Semantic similarity (`SimilarityService` + `SkillGraph`)

No external model — deliberately small so it can later be swapped for embeddings:

1. Both texts → word vectors, TF weight `1 + ln(count)`, stop-words and short tokens dropped.
2. Cosine similarity, then scaled `min(1, 2 × cosine)` (raw cosine on long texts is low).
3. Skill coverage: extract skills from both texts, **expand the profile's skills through
   `SkillGraph`** (having "Spring Boot" implies Java/Spring/REST/Hibernate/Maven), then take
   the fraction of the job's skills covered.
4. Result = `0.5 × scaledCosine + 0.5 × coverage`.

---

## 4. Profile quality score (`ProfileScorer`)

**Use:** the candidate's 0–100 "profile strength" ring with per-category bars and tips.

A transparent checklist, not a black box — 100 points across six categories:

| Category | Max | How points are earned |
|---|---|---|
| Basics | 15 | headline 4, location 3, phone 2, summary ≥ 60 chars 6 |
| Skills | 25 | `25 × min(skillCount, 10) / 10` (normalized via `SkillCatalog`) |
| Experience | 20 | ≥ 100 chars → 20, ≥ 30 → 10 |
| Projects | 15 | ≥ 150 chars → 15, ≥ 40 → 8 |
| Education | 15 | ≥ 20 chars |
| Certifications | 10 | ≥ 10 chars |

Any category not at max produces a concrete suggestion in `ScoreReport.suggestions`.

---

## 5. Applications (`ApplicationWorkflow`)

**Use:** the *single* entry point for creating applications — manual, auto-apply and
external-assisted tracking all go through `submit(...)`, so validation, duplicates, events,
notifications and audit are identical in every mode.

**How it works:**

1. Rejects inactive jobs; external jobs can only be *tracked* (`EXTERNAL_ASSISTED`) and
   internal jobs can never claim that mode.
2. Duplicate check: one application per candidate+job → 409.
3. Requires skills on the profile before applying.
4. **Auto mode** always attaches a job-specific optimised resume (`OptimizerService.forJob`)
   saved as a `ResumeVersion`, plus a draft cover letter.
5. Records the match score, matched/missing skills snapshot at apply time.
6. Writes an `ApplicationEvent` (`APPLIED` / `AUTO_APPLIED` / `TRACKED_EXTERNAL`), an audit
   entry, notifies the candidate (auto-apply) and the recruiter (new application).

**Status changes** (`PATCH /api/applications/{id}/status`): requires `UPDATE_STATUS`
permission; recruiters cannot set `WITHDRAWN` or reopen `APPLIED`; every change writes a
`STATUS_CHANGED` event, an audit entry and a `STATUS` notification to the candidate.
Withdraw is candidate-only and closes `SELECTED`/`REJECTED` applications.

**Applicant ranking:** `rank = round(0.85 × matchScore + 0.15 × evidenceScore)` — the blend
shown next to every applicant row.

---

## 6. Auto-apply (`AutoApplyService`)

**Use:** candidates opt in with a rule; the system applies to eligible jobs for them —
strictly for **internal** jobs, above their minimum score, within a daily cap.

**Rule defaults/clamps:** `minScore` default 70 (30–100), `maxPerDay` default 3 (1–10),
optional location / job-type / role-keyword filters (CSV; empty = allow all; job types match
exactly, role keywords match as substrings, "remote" locations always allowed).

**How a run works (`tryApply`):**

1. Skip external or inactive jobs; skip jobs already applied to.
2. Apply job-type, role-keyword and location filters.
3. Compute the match with current weights; below `minScore` → skip.
4. Submit through `ApplicationWorkflow` with `mode=AUTO` (so duplicates, events, resume and
   notifications all behave identically).
5. **Daily budget:** counts today's (UTC) `mode=AUTO` applications; never exceeds `maxPerDay`.

**Triggers:** every 15 minutes (`@Scheduled`, first run after 3 min), when a recruiter
publishes a new job (`runForJob`), manually (`POST /api/candidate/auto-apply/run`), and the
admin "run all" button. Each run is individually try/caught so one failure never stops the rest.

---

## 7. Companies & permissions (`CompanyService`)

**Use:** organisations with verified identity, an owner, invited recruiters and granular
permissions — the multi-tenant layer over jobs and applicants.

**Registration flow:** `POST /api/companies/register` (multipart: company fields, logo,
verification documents) creates the company as `PENDING_VERIFICATION` and logs the owner in.
Until an admin activates it, **no member can post jobs** — the service throws 403
"awaiting verification".

**Verification:** the admin reviews the documents and sets `ACTIVE`/`REJECTED`/`SUSPENDED`;
activation records `verifiedBy` (admin name + email) and `verifiedAt` and notifies the owner.
**Auto-verify toggle** (`company.auto_verify` setting): when On, new registrations are
activated immediately; when Off, everything waits for review — and turning it back On flushes
everything still pending in the same request (returns how many were verified). The admin UI
exposes this as an on/off switch on the Companies page.

**The 8 permissions** (stored as a CSV on the member):

```
POST_JOB, EDIT_JOB, EDIT_ANY_JOB, VIEW_APPLICANTS,
UPDATE_STATUS, MANAGE_RECRUITERS, REASSIGN_JOBS, VIEW_ANALYTICS
```

**`CompanyService.require(user, permission)` — the gate every company endpoint calls:**

1. `ADMIN` → always allowed.
2. No company membership → full access (stand-alone recruiter, unchanged behaviour).
3. Membership `SUSPENDED` or company not `ACTIVE` → 403.
4. Member is `COMPANY_ADMIN` → all 8 permissions.
5. Otherwise: the permission must be in the member's CSV → else 403 with the missing
   permission named.

**Invitations:** a company admin with `MANAGE_RECRUITERS` invites by email + role + a
checkbox list of permissions. The system emails a link containing a 32-hex token
(`EmailSender` / `LoggingEmailSender` — the logging implementation writes to the console in
development). **The invitee chooses their own password on acceptance — admins never set
passwords.** Tokens expire after 7 days; preview (`GET`) works unauthenticated so the invitee
can see the company before signing up; accepting creates the user + membership in one step and
notifies the inviter. Invitations can be revoked, and existing members' permissions/status
can be changed or suspended by someone with `MANAGE_RECRUITERS` (not yourself, and a
`COMPANY_ADMIN` target cannot be stripped down).

**Reassignment:** `REASSIGN_JOBS` moves a job to another *active* member (job ownership never
dangles). **Analytics:** `VIEW_ANALYTICS` returns company-wide job/application/hire stats.

---

## 8. Evidence verification (`EvidenceService`) — how the auto-checks work

**Use:** candidates back their skills with links (GitHub, LeetCode, LinkedIn, Kaggle,
HackerRank, certificates, portfolios); recruiters see them labelled verified vs self-declared.
Every link must be `https://` and, for the five profile types, must point at the matching
domain (`github.com` for GITHUB, etc.) — enforced before anything else runs.

**Verifier chain** — one strategy per type, run at add-time, never throws:

| Type | Strategy | Outcomes |
|---|---|---|
| `GITHUB` | `GET api.github.com/users/{name}` | 200 → **VERIFIED** `AUTO:GITHUB_API` (with repo/follower counts in metadata); 404 → **REJECTED** (no such account); anything else → PENDING |
| `LEETCODE` | `POST leetcode.com/graphql` profile query | `matchedUser` present → **VERIFIED** `AUTO:LEETCODE_API`; explicit `null` → **REJECTED**; rate-limit/other → PENDING |
| `LINKEDIN`, `KAGGLE`, `HACKERRANK` | domain liveness: GET the profile URL | 200 → **VERIFIED** `AUTO:URL_OK`; 404/410 → **REJECTED**; **redirects, 403/429/5xx, timeouts → PENDING** — a blocked or failed check can never falsely reject |
| `CERTIFICATE`, `PORTFOLIO`, `OTHER` | liveness only | a reachable URL is *annotated in metadata* but the status **always stays PENDING** — a URL cannot prove a claim, so a human reviews it |

**The admin toggle** (`evidence.auto_verify` setting, default **On**, switch on the admin
Evidence page):

- **On** — a conclusive result becomes the status directly, and the candidate gets an
  `EVIDENCE` notification ("marked verified/rejected automatically").
- **Off** — the check still runs but only *annotates* the metadata
  (`Auto-check (not applied — auto-verify is off): …`) and everything waits for review.

**HTTP access** goes through the `HttpGateway` interface (`JdkHttpGateway` in production:
4 s connect / 6 s request timeout, **no redirect following** so a login wall can never count
as "profile exists", identifying `User-Agent`). Tests inject a stub instead of the network.

**Manual review** (`review(...)`) always wins: any item can be set to VERIFIED/REJECTED/PENDING
by an admin with a note shown to the candidate; `verifiedBy = ADMIN`.

**Evidence score** (0–100, shown to the candidate and blended into applicant ranking):
`35` per API-verified item, `25` per URL-liveness-verified item (weaker proof), `10` per
pending item, capped at 100. The recruiter "applicants with verified evidence" stat counts
items with `status = VERIFIED` directly.

---

## 9. Resume, optimisation and preparation

| Function | Use / how it works |
|---|---|
| `ResumeParser` | Extracts text from PDF (PDFBox) or DOCX (POI), then structures it into name/contact/education/skills/projects sections and normalizes skills through `SkillCatalog`. Used as a *draft*: the candidate reviews before anything is saved. |
| `OptimizerService.forJob` | Builds a job-tailored resume by **re-ordering and emphasising** profile content around the job's skills — it never invents experience or skills. Returns tips: matching skills to lead with, project lines that hit the job's stack, missing skills to honestly add, JD keywords not listed, and a nudge when compatibility < 50 %. |
| `OptimizerService.forRole` | Same re-ordering for a target career role instead of a job. |
| `CoverLetterService.draft` | Template-free draft cover letter assembled from the profile + job (clamped to 5000 chars on save). |
| `InterviewPrepService` | Prep pack: the job's required skills vs the candidate's gaps, with practice points for each. |
| `TrajectoryService` | For each role in `RoleCatalog`, computes `fit = 100 × strengths / roleSkills` from the candidate's skills + extracted project/experience skills, marks roles matching their preferred roles/headline, counts open jobs whose title matches, and attaches learning links for the top 5 missing skills. Returns the top 5 trajectories sorted preferred-then-fit. |
| `LearningResources` | Curated learning link per skill (used by trajectories, skill-impact tips). |

---

## 10. Job ingestion (`JobIngestionService`)

**Use:** admins pull external listings from the public **Arbeitnow** job-board API into the
common job board (`POST /api/admin/ingest`, limit 1–50, default 20).

**How one import works:**

1. Fetch the feed (15 s timeout); non-200 → 502.
2. For each item: build `sourceJobId = "arbeitnow:<slug>"` — **skip if already imported**
   (that is the dedupe key).
3. Strip HTML from the description, decode entities, collapse whitespace.
4. Extract recognizable tech skills (`SkillCatalog.extract`) from title + tags +
   description; **no skills → skip** (not a role this platform can match).
5. Normalize into the `Job` model: `source = EXTERNAL`, title/company ≤ 250, description
   ≤ 7900, ≤ 10 skills, remote flag folded into the location, employment type mapped
   (intern/part-time/contract/full-time), `minExperience` scraped from "N years" text and
   capped at 15.

External jobs are excluded from matching evaluation and **never auto-applied**; candidates
get the original link plus an optimised application package to apply on the source site and
track back (`EXTERNAL_ASSISTED` mode).

---

## 11. Evaluation of the matching engine (`EvaluationService`)

**Use:** the admin "Evaluation" page — academic validation of the ranking on the labelled
demo personas (`GET /api/admin/evaluation`, re-runnable).

**How it works:** for each demo persona the relevant job *titles* are known in advance
(`DemoData.Persona.relevant` keywords). The engine ranks all active internal jobs for that
persona's profile with the current admin weights, then:

- **Precision@3** = relevant jobs in the top 3 ÷ 3
- **Hit-rate@3** = 1 if at least one relevant job appears in the top 3, else 0
- **MRR** = 1 ÷ rank of the first relevant job (scanning past position 5 if needed)
- **NDCG@5** = DCG over the top 5 (`Σ 1/log₂(rank+1)` for relevant) ÷ ideal DCG for the
  total number of relevant jobs

Results are returned per persona (top-3 with ✓ marks) and as means across personas, with the
method string explaining the binary relevance rule. This is how weight changes can be shown
to help or hurt ranking quality instead of being a guess.

---

## 12. Settings (all admin-tunable, stored in the `settings` table)

| Key | Default | Meaning |
|---|---|---|
| `weight.skills/experience/projects/role/education/semantic` | 35/20/15/10/10/10 | Matching engine weights (each 0–100, sum > 0) |
| `company.auto_verify` | `true` | Auto-activate new company registrations (turn Off to review each; turning On flushes the queue) |
| `evidence.auto_verify` | `true` | Auto-apply conclusive evidence checks as statuses (Off = annotate only, review everything) |
| `demo.seed.version` | `3` | Re-seeds demo data when bumped |

---

## 13. Administration

| Function | Use |
|---|---|
| `AdminController` `/stats` | Platform overview: role counts, jobs (active/external), applications (incl. auto), pending evidence, applications-by-status funnel. |
| `/users`, `/users/{id}/enabled` | List and enable/disable users (never yourself). |
| `/jobs`, `/jobs/{id}/active` | List and activate/deactivate any job. |
| `/weights` (GET/PUT) | Read/save matching weights (audited). |
| `/evidence` + `PATCH /evidence/{id}` | Evidence review queue (filter by status) and manual decisions. |
| `/evidence/auto-verify` (GET/PUT) | The evidence auto-check toggle (audited). |
| `/evaluation` | Matching-engine metrics (§11). |
| `/ingest` | External job import (§10). |
| `/auto-apply/run` | Run every enabled auto-apply rule now. |
| `/audit` | Last 100 audit-log entries. |
| `AdminCompanyController` | Company list, document download, status changes, company auto-verify toggle (§7). |

---

## 14. Notifications & audit

- **`NotificationService.notify(userId, type, message, linkId, jobId)`** — creates an in-app
  notification; the `type` decides the frontend deep link: `STATUS`/`AUTO_APPLY` → the
  application, `NEW_APPLICATION`/`WITHDRAWN` → the job's applicant list, `EVIDENCE` → the
  evidence page, `COMPANY_VERIFIED` → the company page. Messages are clipped to 900 chars.
  Sent on: application created/status changed/withdrawn, auto-apply, evidence decisions,
  company verified, invitation accepted.
- **`AuditService.log(actorId, action, entity, entityId, details)`** — every sensitive action
  (login, register, profile update, job create/update/delete, apply, status change, evidence
  decision, weight change, user enable/disable, company verification, invitations,
  reassignment, ingestion, toggles) leaves a trace, shown in the admin audit view.

---

## 15. Seed data (`DataSeeder` + `DemoData`)

On first start (and when `demo.seed.version` changes) the database is seeded with: the admin
account, 18 demo candidates, recruiter and company-admin accounts, 4 companies (one still
awaiting verification), 30 internal jobs, 4 imported external listings, applications in every
pipeline status, evidence items in all three states, auto-apply rules and demo personas for
the evaluation page. Seeding is idempotent — existing rows are skipped by natural keys
(email, title, evidence title…).

---

## 16. Numbers cheat-sheet

| Thing | Value |
|---|---|
| Match weights (default) | skills 35, experience 20, projects 15, role 10, education 10, semantic 10 |
| Applicant rank blend | `0.85 × match + 0.15 × evidenceScore` |
| Evidence score | 35 per API-verified, 25 per URL-verified, 10 per pending, cap 100 |
| Profile score | Basics 15 + Skills 25 + Experience 20 + Projects 15 + Education 15 + Certifications 10 |
| Semantic similarity | `0.5 × min(1, 2·cosine) + 0.5 × skillCoverage`, TF = `1 + ln(count)` |
| Auto-apply | minScore default 70 (30–100), maxPerDay default 3 (1–10), UTC-day budget, scheduler every 15 min |
| Ingestion | source key `arbeitnow:<slug>`, limit 1–50, ≤ 10 skills, description ≤ 7900, minExperience ≤ 15 |
| Invitations | 7-day TTL, 32-hex token, roles `RECRUITER`/`COMPANY_ADMIN` |
| Evidence checks | 4 s connect / 6 s request timeout, no redirects, `User-Agent: snap-hire-verifier` |
| Evaluation | Precision@3, Hit-rate@3, MRR, NDCG@5 over labelled demo personas |
| Limits | resume upload 5 MB, cover letter 5000, notifications 50, audit 100, recommendations 20, candidate search 50 |
