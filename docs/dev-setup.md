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

The migrations also create a tiny demo school/class/student membership and weekday 08:00–17:00 school windows so Health Connect exclusion can be demonstrated immediately.

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

The Android demo currently includes the main vertical flow:

1. load today's school menu and enter quick portions or exact consumed grams;
2. show macro composition, fiber/sodium and the optional daily energy-reference gap;
3. combine administrator-confirmed PE minutes with outside-school activity;
4. sync Health Connect only outside the administrator-configured school window;
5. allow manual duration/intensity correction;
6. select a home-meal photo, forward it directly through the Worker/Tunnel to AGY, edit the structured result, and persist only the confirmed meal data.

## Checks

```bash
pnpm check
pnpm --filter @health2609/admin-web build
gradle -p apps/android assembleDebug
```

CI runs the same JavaScript/TypeScript checks and an Android debug build.
