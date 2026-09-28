# MSDT AGY bridge

This bridge is the local origin for `h2609-agy.lunarlab.uk`.

- Listen address: `127.0.0.1:18787`
- Public route: `https://h2609-agy.lunarlab.uk/health2609/agy`
- AGY model process: one resident `agy` stream-json session
- Per image: the resident coordinator is instructed to create a fresh child/subagent and then validate the child result
- Output: JSON constrained by `home-meal.schema.json`

Start on MSDT from a clone of this repository:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\agy-bridge\start.ps1
```

Optional bearer auth:

```powershell
$env:HEALTH2609_AGY_TOKEN = "<same secret as Worker AGY_TASK_TOKEN>"
powershell -ExecutionPolicy Bypass -File .\tools\agy-bridge\start.ps1
```

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:18787/healthz
```

The bridge deliberately does not use port 8787; that port belongs to the existing MSDT PC agent.
