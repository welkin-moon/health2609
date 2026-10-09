# Architecture

## 1. Context

health2609 has two user-facing clients:

- Android student app
- school administrator web app on Cloudflare Pages

Both use one Cloudflare Worker API. D1 is the system of record for structured data. Home-meal images are not persisted in R2. The Worker validates the request and triggers the existing AGY CLI runner through the configured Cloudflare Tunnel / HTTP ingress.

The application is intentionally modular but small enough for a competition demo.

## 2. Trust boundaries

### Trusted server-side

- Demo-role routing (client-supplied headers; not production authentication)
- school tenant enforcement
- D1 repositories
- aggregate statistics
- deterministic nutrition/activity calculations

### Untrusted

- all Android/web request bodies
- user-entered serving amounts
- Health Connect records before local normalization
- manually entered activity records before validation
- AGY/model output
- uploaded file metadata

All untrusted data is validated before entering domain logic.

## 3. Domain modules

### school

School, preset demo membership, class/group, school-time windows and configurable daily activity target. Student registration/join-code enrollment is not part of the accepted demo.

### meals

Menu, meal slot, dish, nutrition metadata, standard serving, student consumption and home-meal entries.

### pe

PE timetable and administrator-confirmed actual activity minutes.

A PE record is explicit school data rather than phone inference.

### activity

Outside-school activity summary uploaded by Android after local exclusion of school time, plus optional manually entered activity sessions.

The server combines:

```text
daily activity minutes =
    selected school PE minutes (school record OR phone PE-window record)
  + max(outside-school Health Connect minutes, summed manual minutes)
```

The demo treats manual activity as a daily fallback/correction. It takes the larger of phone and manual totals, rather than claiming per-session overlap detection. This may undercount disjoint sessions; exact interval-based merging is deferred. Overlapping PE windows are merged locally before reading Health Connect.

### insights

Deterministic facts first, then optional wording assistance.

Examples:

- "今日记录蔬菜类较少"
- "今日活动 128 / 120 分钟"

The model may turn facts into concise natural-language suggestions, but may not rewrite the underlying totals.

### statistics

Admin-facing aggregation by school/class/date.

Statistics are read models derived from domain records rather than separate manually-maintained truth.

## 4. Android architecture

Recommended package shape:

```text
app
core/
  model
  network
  database
  health
  designsystem
  common
feature/
  onboarding
  today
  meals
  activity
  history
  settings
```

Each feature uses:

```text
UI -> ViewModel -> use case/domain -> repository -> local/remote source
```

Room is the local cache and outbox. DataStore stores settings and non-sensitive preferences. Secrets/tokens should use Android platform-protected storage.

Health Connect is used only on-device. The app removes configured school-time windows locally and uploads daily outside-school aggregates. A manual activity form covers exercise that the Android health data interface cannot read or that the student wants to correct for the demo.

## 5. Cloudflare architecture

```text
Cloudflare Pages admin web
  └─ calls Worker API through custom domain

Cloudflare Worker API
  ├─ auth / school tenant guard
  ├─ domain validation
  ├─ D1 repositories
  ├─ statistics read models
  └─ AGY task adapter

D1
  └─ school/menu/meal/PE/activity/home-meal records
```

The Pages project serves the Vite/React administrator UI. The Worker owns all API routes and D1 access. The competition entry should use custom domains such as `h2609-admin.lunarlab.uk` for Pages and `h2609.lunarlab.uk` for the Worker rather than `pages.dev` or `workers.dev`.

## 6. Worker architecture

Recommended shape:

```text
src/
  index.ts
  routes/
  middleware/
  domain/
  repositories/
  statistics/
  integrations/
    agy/
  db/
```

Routes only handle HTTP concerns. Domain services own business rules. Repositories own D1 queries.

## 7. AGY integration

There is no separate model service implementation in this repository.

The AGY runtime is expected to be started before the demo. It should read the task requirements and `prompts/home-meal-analysis.md`, then remain open in the CLI. For each home-meal image request, the Worker-side adapter submits a structured task to the AGY ingress. The resident AGY process creates a new child agent for that task and returns a JSON-only candidate result.

The task contains:

- system-style task instructions from `prompts/home-meal-analysis.md`
- the image attachment or supported image reference
- expected JSON schema/version
- no unrelated student history unless explicitly needed

The returned payload must validate against `HomeMealAnalysisResult`. Validation failure falls back to manual entry.

## 8. Security

- short-lived student session tokens
- separate admin role
- every server query scoped by authenticated school membership
- no client-controlled authorization decisions
- request/body size limits
- image MIME and size validation
- rate limit sensitive endpoints
- audit metadata for administrator writes
- no Cloudflare Tunnel secret exposed to clients

## 9. Statistics/privacy compromise for the demo

Administrators need useful statistics, so the system intentionally supports aggregate school/class views.

For the initial version, avoid a general-purpose screen for browsing an individual student's complete health timeline. If a specific record is needed for correction/support, make that an explicit later capability rather than a side effect of the dashboard.
