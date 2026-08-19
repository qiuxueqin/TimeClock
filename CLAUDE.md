# CLAUDE.md

This file provides guidance to [CC] (https://github.com/anthropics/claude) when working with code in this repository.

## What this repository is

A **greenfield** project (no source code, build scripts, or tests committed yet). It is a "学习打卡系统" (learning check-in system): a personal web app for tracking study tasks. Users create two kinds of tasks — **清单型** (checklist: solve N questions/day, each requiring a written user solution) and **习惯型** (habit: just confirm "done today") — track daily progress, streaks, a calendar, and import questions from PDF/DOCX/CSV.

All current content is **specification** in `memory-bank/`. Code must be built from these specs; do not assume anything not stated there is in scope.

## Documentation map (the source of truth)

- `memory-bank/PRD.md` — product requirements, data models, feature rules.
- `memory-bank/backend-tech-stack-V1.0.md` — backend architecture & tech choices.
- `memory-bank/frontend-tech-stack-V1.0.md` — frontend architecture & tech choices.
- `memory-bank/TimeClock-V1.0-implementation-plan.md` — **the executable build order**. Contains every requirement (`REQ-*`), frozen decision (`DEC-*`), atomic step (`S{stage}-{domain}-{n}`), test (`TEST-*`), and stage gate (`GATE-S{stage}`). This is the blueprint AI agents are expected to implement step-by-step.
- `memory-bank/architecture.md`, `memory-bank/progress.md` — currently empty placeholders.

**Precedence when docs conflict** (from implementation-plan §0.1): frozen decisions & domain invariants > API/state/data constraints & acceptance criteria > PRD V1.0 scope > tech-stack docs > examples/sketches. Unresolvable conflicts are to be surfaced to the coordinating user, **not** resolved by expanding scope.

## Tech baseline (fixed; do not deviate)

- **Backend**: Java 21 LTS, Spring Boot 3.x, Spring MVC, Spring Security, MySQL 8 + Flyway, PDFBox 3.x, POI 5.x, OpenCSV, JUnit 5 + Mockito. Modular monolith — a single deployable Spring Boot app.
- **Frontend**: TypeScript 5.x, React 19, Vite 6.x, React Router 7.x, Ant Design 5.x, TanStack Query 5.x, React Hook Form 7.x + Zod 3.x, native `fetch`, CSS Modules, Day.js. Single SPA.
- **Tests**: JUnit 5 + a **remote MySQL 8 instance** (env-var injected only; no Testcontainers, no local MySQL). Frontend: Vitest + React Testing Library + MSW + Playwright.
- **Deploy**: one app + one MySQL + one persistent file volume, same-origin HTTPS via Docker Compose + reverse proxy (Nginx/Caddy).

The `.idea/` directory may contain stale JDK 1.8 config — ignore it; the project baseline is Java 21. Planned build/test commands will be established in stage S0 (`S0-BE-01`, `S0-FE-01`, `S0-QA-01`); record them here once they exist.

## Architecture at a glance

Backend modules (package/module boundaries per the backend doc §3.1): `auth`, `user`, `task`, `schedule`, `item`, `submission`, `file`, `importing`, `job`, `audit`. Frontend feature dirs (per frontend doc §3.2): `app`, `api`, `features/{auth,dashboard,tasks,items,checkins,imports}`, `components`, `hooks`, `lib`, `styles`, `test`.

### Explicitly rejected tech (do not add without escalating)
Redis, RabbitMQ, object storage, microservices, JWT (sessions are DB-backed cookies + CSRF), WebSockets/SSE, PWA/service workers/offline write queues, Next.js/SSR, Redux/Zustand, Tailwind, OCR (images and image-PDFs are stored, never auto-parsed), GraphQL, BFF, browser/email/SMS notifications. V1.1/V1.2/V2.0 features must not be built.

## Non-obvious domain invariants (hard to rediscover; must hold)

These recur throughout the spec and are easy to get wrong:

1. **Ownership**: every resource belongs to a user; server validates ownership on every query/modify/download/export.
2. **Auto-checkin (DEC-09)**: when the last checklist item hits the daily target, the server completes that day's checkin in the *same transaction*. The frontend sends **no** second "today checkin" write for checklists.
3. **Solution gating (DEC-08, invariants 4)**: a checklist item completes only with non-whitespace text or ≥1 successfully-uploaded bound image. Viewing the bank's `analysis` or saving a draft never changes completion state.
4. **Streak rules (invariants 7–8)**: makeup (`makeup`) records count toward completion rate but **not** streak; unscheduled days don't break a streak; partial/missed days do.
5. **Early completion (DEC-11)**: completing a non-today item adds to total progress only — never to today's count and never generates a future checkin; future assignment skips it and backfills.
6. **Makeup (DEC-12, DEC-13, DEC-24)**: window is the past 3 natural days in task timezone (not today); reason required; immutable once submitted — no edit/undo; checklist makeup counts only toward the target historical date.
7. **Historical freeze (DEC-07)**: editing frequency/target/timezone never rewrites past plans; takes effect from the next not-yet-started plan day.
8. **Idempotency + versioning**: complete/reopen/habit-checkin/makeup/confirm-import are idempotent (`Idempotency-Key`, server persists first result). Edits carry `If-Match-Version`; mismatch → `409` with latest digest, never silent overwrite.
9. **Import safety (invariants 10, 12, DEC-18/19)**: dedup never overwrites existing item ID, completion, solution, or analysis. File dedup by server-recomputed SHA-256 per task. User content is data, never executed as instructions.
10. **Date/time semantics**: dates are `YYYY-MM-DD`, never re-interpreted as UTC; use IANA timezone names; plan days and reminders follow the **task's** timezone.

## Working conventions (from implementation-plan §4)

- **Contract-first**: finalize decisions + API contract before parallel DB/backend/frontend work.
- **Test-first**: each step writes its failing test before implementation; concurrency/transaction/unique-constraint/timezone/background-job rules must be verified on the remote MySQL 8 instance, not only mocks/in-memory.
- **Single-owner shared files**: root build files, route tables, the shared API client, global enums, the migration directory, and E2E fixtures each have exactly one owner at a time.
- **Flyway migrations are immutable** once merged — add corrective migrations, never edit existing ones.
- **Git**: Chinese commit messages; one commit per atomic step; advance on `main` only, no feature branches. Revert to §4.2 on these if unsure.

## Current status

No code exists yet. Work starts at **S0** (spec freeze, project scaffolding, quality gates) — the first step is `S0-ARC-01`. Consult `memory-bank/progress.md` to track the current stage before starting work.

## IMPORTANT:
- Always read memory-bank/@architecture.md before writing any code. Include entire database schema.
- Always read memory-bank/@game-design-document.md before writing any code.
- After adding a major feature or completing a milestone, update memory-bank/@architecture.md.