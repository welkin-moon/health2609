# health2609 implementation plan

## Product goal

Build an installable Android app for students plus a lightweight school administrator web console.

The product should answer three daily questions:

1. What did the student actually eat at school and at home?
2. How much effective physical activity did the student complete in school PE and outside school?
3. What simple, evidence-based action is worth doing next?

This is a competition demo, so the implementation prioritizes a complete, stable main flow over production-scale complexity.

## Confirmed rules

### School meals

Administrators create menus in advance. Every dish has a standard serving and optional nutrition data. Students record only the dishes and proportion actually eaten.

Recommended portion choices:

- 0
- 1/4
- 1/2
- 3/4
- 1 serving
- custom multiplier

Daily nutrition totals are deterministic calculations from confirmed quantities.

### PE at school

School PE is not inferred from phone activity.

Administrators configure:

- class timetable
- lesson start/end
- actual effective activity minutes for that PE lesson

The student's school exercise contribution is derived from the relevant scheduled PE lesson plus the administrator-confirmed activity minutes.

### Exercise outside school

Only the outside-school portion is read from Android Health Connect.

The Android app should:

1. fetch supported exercise/activity records;
2. exclude school-time intervals locally;
3. aggregate a daily outside-school result;
4. upload only the aggregate needed for the product.

Raw GPS tracks are not required.

### Meals at home

The student may:

- take/upload a meal photo;
- manually edit the detected foods and serving amounts;
- save only after confirmation.

Image understanding is delegated to AGY using a constrained prompt. We do not build another model-serving product inside this repository.

AGY should return structured output matching the repository contract. Nutrition and activity totals remain deterministic code paths.

### Administrator statistics

The admin console includes school-level statistics.

Initial statistics:

- meal participation rate
- average serving completion by dish
- estimated daily nutrient averages where nutrition metadata exists
- PE attendance/record coverage
- average confirmed PE activity minutes
- percentage reaching the configured daily activity target
- trend by day/class

Statistics should favor aggregation over exposing unnecessary individual health detail.

## Architecture

```text
Android app
  ├─ UI: Compose + Material 3 Expressive (M3E)
  ├─ Domain
  ├─ Repositories
  ├─ Room / DataStore
  ├─ Health Connect
  └─ API client
           │
           ▼
Cloudflare Worker API
  ├─ auth / school tenant guard
  ├─ domain services
  ├─ statistics
  ├─ D1 repositories
  ├─ no persistent meal-image object storage
  └─ AGY task adapter
           │
           └─ Cloudflare Tunnel / existing bridge -> AGY with prompt

Admin Pages
  └─ same Worker API
```

## Repository layout

```text
apps/android/
apps/admin-web/
services/api/
packages/contracts/
prompts/
docs/
```

## Milestones

### M1 — foundation

- monorepo structure
- contracts
- D1 schema
- Worker API skeleton
- Android Compose shell
- Pages shell
- CI

### M2 — school meal loop

- administrator menu CRUD
- student today's menu
- portion selection
- deterministic nutrition aggregation
- sync/offline queue

### M3 — PE and outside-school activity

- timetable + PE actual activity minutes
- Health Connect permissions
- local exclusion of school-time data
- daily activity target calculation

### M4 — home meal + AGY

- photo selection/capture
- upload/temporary object path
- AGY structured task prompt
- editable detection result
- deterministic commit into daily nutrition

### M5 — administrator statistics

- aggregated class/school dashboard
- date/class filters
- basic trend charts
- minimum-cell/privacy guard where appropriate

### M6 — submission polish

- signed release APK
- technical documentation
- screenshots
- <=60 s demo video
- bug fixing and accessibility pass

## Engineering rules

- Kotlin coroutines/Flow; avoid business logic in Composables.
- TypeScript strict mode.
- Runtime validation at every untrusted boundary.
- Shared API schemas under `packages/contracts`.
- No secret or tunnel credential in Android/web bundles.
- D1 access only from Worker.
- Tenant checks happen server-side, never by trusting client school IDs.
- AI/AGY output is untrusted input and must pass schema validation.
- Health advice is bounded to general wellness suggestions, not diagnosis.
- Demo fixtures remain small and obvious; no elaborate fake-data subsystem unless necessary.

## First implementation slice

The first vertical slice should already be demonstrable:

1. admin creates a school and today's lunch dishes;
2. Android joins school with a code;
3. Android loads today's lunch;
4. student chooses portion amounts;
5. Worker stores the record;
6. Android displays today's nutrition summary;
7. admin statistics shows participation and per-dish consumption.

After this works, add PE and Health Connect.
