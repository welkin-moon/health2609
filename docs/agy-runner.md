# AGY runner contract

health2609 does not run its own model service. The home-meal photo feature expects a small bridge to a resident AGY CLI session.

## Runtime shape

```text
Android photo upload
  -> Worker /v1/home-meals/analyze
  -> AGY_TASK_URL through Cloudflare Tunnel / local ingress
  -> resident AGY CLI
  -> fresh child agent for this image
  -> JSON result validated by Worker
  -> student confirmation UI
```

On MSDT, `tools/agy-bridge/server.mjs` starts one resident AGY stream session before the demo. The bridge listens only on `127.0.0.1:18787`; Cloudflare Tunnel publishes it as `https://h2609-agy.lunarlab.uk/`. Port 8787 is intentionally left to the existing PC agent.\n\nThe resident AGY session reads:

- `docs/submission.md`
- `docs/architecture.md`
- `prompts/home-meal-analysis.md`
- the expected `HomeMealAnalysisResult` schema in `packages/contracts/src/index.ts`

Then keep the CLI alive and wait for tasks.

## HTTP ingress expected by the Worker

The Worker sends a `POST` request to `AGY_TASK_URL`.

Request format: `multipart/form-data`

Fields:

- `image`: the meal image file
- `prompt`: the complete home-meal analysis prompt
- `schemaVersion`: currently `1`

Optional header:

- `Authorization: Bearer <AGY_TASK_TOKEN>` when the Worker binding is set

Response format: JSON only, matching `HomeMealAnalysisResult`.

```json
{
  "schemaVersion": 1,
  "items": [
    {
      "name": "番茄炒蛋",
      "estimatedGrams": 160,
      "servingMultiplier": 1,
      "confidence": 0.72,
      "nutrition": {
        "energyKcal": 220,
        "proteinG": 12,
        "fatG": 14,
        "carbohydrateG": 10
      },
      "needsConfirmation": ["estimatedGrams"]
    }
  ],
  "notes": ["图片无法判断实际用油量"]
}
```

## Child-agent instruction

For each request, create a fresh child agent/task context. Do not reuse food items, quantities, hidden assumptions or user-specific details from previous tasks.

Suggested task text for the resident CLI bridge:

```text
You are the resident AGY runner for health2609.
For this single request, create a child agent to analyze the attached meal image.
Use the supplied prompt exactly as the task instruction.
Return JSON only, matching schemaVersion=1.
Do not include markdown fences, prose outside JSON, medical conclusions, or unconfirmed diet advice.
```

## Worker bindings

Production Worker binding names:

- `AGY_TASK_URL`: required for photo analysis; should point to the Cloudflare Tunnel / ingress that can reach the resident AGY CLI.
- `AGY_TASK_TOKEN`: optional shared bearer token for the bridge.

These values must stay server-side. Android and Pages never receive the tunnel secret or AGY token.

## Failure behavior

If the AGY bridge is unavailable, times out, or returns invalid JSON, the Worker returns an error and the Android UI falls back to manual entry / retry. The app must not persist model output until the student confirms the food names and quantities.


## MSDT start command

From the health2609 repository on MSDT:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\agy-bridge\start.ps1
```

The bridge uses AGY CLI's persistent stream-json mode and the repository JSON schema. It serializes requests so one resident coordinator handles the queue, but each meal request explicitly requires a fresh child/subagent for visual analysis. If AGY exits or a turn times out, the bridge discards that process and bootstraps a fresh resident session on the next request.

Local health check:

```powershell
Invoke-RestMethod http://127.0.0.1:18787/healthz
```
