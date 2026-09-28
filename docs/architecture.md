# Architecture

## 1. Context

health2609 has two user-facing clients:

- Android student app
- school administrator web app

Both use one Cloudflare Worker API. D1 is the system of record for structured data. Home-meal images are not persisted in R2. The Worker validates the request and forwards the image directly through the configured Cloudflare Tunnel to the AGY machine.

The application is intentionally modular but small enough for a competition demo.

## 2. Trust boundaries

### Trusted server-side

- Worker authentication and authorization
- school tenant enforcement
- D1 repositories
- aggregate statistics
- deterministic nutrition/activity calculations

### Untrusted

- all Android/web request bodies
- user-entered serving amounts
- Health Connect records before local normalization
- AGY/model output
- uploaded file metadata

All untrusted data is validated before entering domain logic.

## 3. Domain modules

### school

School, join code, class/group, school-time windows and configurable daily activity target.

### meals

Menu, meal slot, dish, nutrition metadata, standard serving, student consumption and home-meal entries.

### pe

PE timetable and administrator-confirmed actual activity minutes.

A PE record is explicit school data rather than phone inference.

### activity

Outside-school activity summary uploaded by Android after local exclusion of school time.

The server combines:

```text
daily activity minutes =
    confirmed school PE activity minutes
  + outside-school activity minutes
```

The implementation must avoid double-counting overlapping sources.

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

## 5. Worker architecture

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

## 6. AGY integration

There is no separate model service implementation in this repository.

The Worker-side integration is a small adapter that submits a task to the existing AGY bridge reachable through the configured Cloudflare path.

The task contains:

- system-style task instructions from `prompts/home-meal-analysis.md`
- image/object reference or supported attachment
- expected JSON schema/version
- no unrelated student history unless explicitly needed

The returned payload must validate against `HomeMealAnalysisResult`. Validation failure falls back to manual entry.

## 7. Security

- short-lived student session tokens
- separate admin role
- every server query scoped by authenticated school membership
- no client-controlled authorization decisions
- request/body size limits
- image MIME and size validation
- rate limit sensitive endpoints
- audit metadata for administrator writes
- no Cloudflare Tunnel secret exposed to clients

## 8. Statistics/privacy compromise for the demo

Administrators need useful statistics, so the system intentionally supports aggregate school/class views.

For the initial version, avoid a general-purpose screen for browsing an individual student's complete health timeline. If a specific record is needed for correction/support, make that an explicit later capability rather than a side effect of the dashboard.
