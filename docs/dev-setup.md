# Local development

## Prerequisites

- Node.js 22
- pnpm 10
- Cloudflare Wrangler login for remote resources, if needed
- JDK 17
- Android Studio / Android SDK 36

## Install JavaScript dependencies

```bash
pnpm install
```

## Create and migrate D1

Create a D1 database named `health2609`, then put its ID in
`services/api/wrangler.toml`.

Apply migrations:

```bash
cd services/api
pnpm exec wrangler d1 migrations apply health2609 --local
```

The second migration creates a tiny demo school/class/student membership.

## Run Worker

From the repository root:

```bash
pnpm dev:api
```

Default local URL: `http://127.0.0.1:8787`.

The current auth headers are intentionally a competition-demo shell. Do not
treat them as production authentication.

## Run administrator web app

In another terminal:

```bash
pnpm --filter @health2609/admin-web dev
```

The web app defaults to the local Worker. For a deployed Worker:

```bash
VITE_API_BASE_URL=https://your-worker.example pnpm --filter @health2609/admin-web build
```

The Pages build output is `apps/admin-web/dist`.

## Android

Open `apps/android` in Android Studio, or build with a Gradle installation:

```bash
gradle -p apps/android assembleDebug \
  -PHEALTH2609_API_BASE_URL=https://your-worker.example/
```

The API base URL must end with `/`.

The Android project currently implements the first vertical slice:

1. load today's lunch menu;
2. choose 0 / 1/4 / 1/2 / 3/4 / 1 serving per dish;
3. show deterministic nutrition totals from the selected servings;
4. submit the record to the Worker.

## Checks

```bash
pnpm check
pnpm --filter @health2609/admin-web build
gradle -p apps/android assembleDebug
```

CI runs the same JavaScript/TypeScript checks and an Android debug build.
