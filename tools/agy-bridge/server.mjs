import { createServer } from "node:http";
import { spawn, execSync } from "node:child_process";
import { promises as fs } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import os from "node:os";
import crypto from "node:crypto";
import readline from "node:readline";
import { safeParseJson, stripCodeFences } from "./parser.mjs";
import { modelConfig, resolveAgyBin, agyEnvironment, classifyAgyError, errorStatus } from "./runtime.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(
  process.env.HEALTH2609_REPO_ROOT || path.join(here, "..", "..")
);
const schemaPath = path.join(here, "home-meal.schema.json");
const host = process.env.HEALTH2609_AGY_HOST || "127.0.0.1";
const port = Number(process.env.HEALTH2609_AGY_PORT || "18788");
const agyBin = resolveAgyBin();
const { model, effort } = modelConfig();
const bearerToken = process.env.HEALTH2609_AGY_TOKEN || "";
const maxQueued = Number(process.env.HEALTH2609_AGY_MAX_QUEUE || "4");
const maxBodyBytes = 40 * 1024 * 1024;
const taskTimeoutMs = Number(process.env.HEALTH2609_AGY_TASK_TIMEOUT_MS || "120000");

let agyProcess = null;
let agyReady = false;
let startPromise = null;
let pendingResult = null;
let queueDepth = 0;
let serial = Promise.resolve();
let lastError = null;
let shuttingDown = false;

const bootstrapPrompt = [
  "You are the resident AGY coordinator for the health2609 student health project.",
  "Your only image-analysis job is estimating home-meal contents for later user confirmation; never treat estimates as medical diagnoses or exact measurements.",
  "Each request supplies the authoritative task instruction, image path, and schema version. Read that request instruction carefully before analysis.",
  "Inspect each meal-image request directly in a single pass without spawning nested child subagents or entering recursive delegation loops.",
  "Do not reuse food items, quantities, or assumptions from earlier requests.",
  "Preserve uncertainty, and return only the configured JSON schema.",
  "If an item or amount is ambiguous, lower confidence and add a concise needsConfirmation entry instead of inventing precision.",
  "For this bootstrap turn only, return schemaVersion=1, an empty items array, and notes=[\"resident_ready\"]."
].join("\n");

function log(message) {
  process.stdout.write(`[health2609-agy] ${message}\n`);
}

function stopAgy(reason) {
  if (agyProcess) {
    log(`restarting AGY process: ${reason}`);
    if (process.platform === "win32" && agyProcess?.pid) {
      try { execSync(`taskkill /pid ${agyProcess.pid} /T /F`, { stdio: "ignore" }); } catch {}
    }
    agyProcess.kill();
  }
  agyProcess = null;
  agyReady = false;
  if (pendingResult) {
    const pending = pendingResult;
    pendingResult = null;
    clearTimeout(pending.timer);
    pending.reject(new Error(`agy_stopped:${reason}`));
  }
}

function spawnAgy() {
  const prefixArgs = JSON.parse(process.env.AGY_BIN_PREFIX_ARGS || '[]');
  if (!Array.isArray(prefixArgs) || prefixArgs.some((x) => typeof x !== 'string')) throw new Error('agy_config_invalid');
  const args = [
    ...prefixArgs,
    "--model", model,
    "--effort", effort,
    "--print=",
    "--input-format", "stream-json",
    "--output-format", "stream-json",
    "--json-schema", schemaPath,
    "--dangerously-skip-permissions",
    "--print-timeout", "2m"
  ];

  const child = spawn(agyBin, args, {
    cwd: repoRoot,
    env: agyEnvironment(),
    stdio: ["pipe", "pipe", "pipe"],
    windowsHide: true
  });
  agyProcess = child;

  const lines = readline.createInterface({ input: child.stdout });
  lines.on("line", (line) => {
    let event;
    try {
      event = JSON.parse(line);
    } catch {
      return;
    }
    if (agyProcess !== child || pendingResult?.child !== child || event?.event !== "result") return;
    const pending = pendingResult;
    pendingResult = null;
    clearTimeout(pending.timer);
    const result = event.result;
    if (result?.status === "SUCCESS") pending.resolve(result);
    else pending.reject(new Error(result?.error || `agy_status:${result?.status || "unknown"}`));
  });

  child.stderr.on("data", (chunk) => {
    process.stderr.write(`[health2609-agy][agy] ${String(chunk)}`);
  });

  child.stdin.on("error", (err) => log("stdin error: " + err.message));

  child.on("exit", (code, signal) => {
    const wasCurrent = agyProcess === child;
    if (wasCurrent) {
      agyProcess = null;
      agyReady = false;
    }
    if (wasCurrent && pendingResult?.child === child) {
      const pending = pendingResult;
      pendingResult = null;
      clearTimeout(pending.timer);
      pending.reject(new Error(`agy_exit:${code ?? "null"}:${signal ?? "none"}`));
    }
  });

  child.on("error", (error) => {
    log(`AGY spawn error: ${error.message}`);
    if (agyProcess === child) stopAgy("spawn_failed");
  });
}

function sendRaw(prompt, timeoutMs) {
  if (!agyProcess || !agyProcess.stdin.writable) {
    return Promise.reject(new Error("agy_not_running"));
  }
  if (pendingResult) {
    return Promise.reject(new Error("agy_turn_already_active"));
  }

  const child = agyProcess;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      if (pendingResult?.child !== child) return;
      pendingResult = null;
      log(`turn timeout (${timeoutMs}ms) exceeded; stopping AGY and triggering background recovery`);
      stopAgy("turn_timeout");
      reject(new Error("agy_timeout"));
    }, timeoutMs);
    pendingResult = { resolve, reject, timer, child };
    const message = { event: "user", message: { content: prompt } };
    child.stdin.write(`${JSON.stringify(message)}\n`, (error) => {
      if (error && pendingResult?.child === child) stopAgy("stdin_failed");
    });
  });
}

async function ensureAgy(maxRetries = 2) {
  if (agyProcess && agyReady) return;
  if (startPromise) return startPromise;

  startPromise = (async () => {
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        if (!agyProcess) spawnAgy();
        const result = await sendRaw(bootstrapPrompt, 120000);
        const ready = result.structured_output || safeParseJson(result.response);
        if (!ready || ready.schemaVersion !== 1) {
          throw new Error("agy_bootstrap_invalid");
        }
        agyReady = true;
        lastError = null;
        log(`resident AGY ready; conversation=${result.conversation_id || "unknown"}`);
        return;
      } catch (error) {
        lastError = classifyAgyError(error.message);
        log(`bootstrap attempt ${attempt}/${maxRetries} failed: ${error.message}`);
        stopAgy(`bootstrap_attempt_${attempt}_failed`);
        if (attempt === maxRetries || ['agy_auth_failed', 'agy_model_unavailable', 'agy_config_invalid'].includes(lastError)) {
          throw error;
        }
        await new Promise((r) => setTimeout(r, 1000));
      }
    }
  })();

  try {
    await startPromise;
  } finally {
    startPromise = null;
  }
}


function extForMime(type) {
  const map = {
    "image/jpeg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/gif": ".gif",
    "image/bmp": ".bmp",
    "image/tiff": ".tiff"
  };
  return map[type] || ".img";
}

async function analyzeImages(imagePaths, suppliedPrompt, schemaVersion) {
  await ensureAgy();
  const imageList = imagePaths.map((p, idx) => `Image ${idx + 1}: ${p}`).join("\n");
  const prompt = [
    "A new health2609 home-meal request has arrived.",
    "Inspect the local image file(s) directly and generate the structured analysis in this single pass.",
    "Do NOT spawn nested child subagents, recursive loops, or repetitive delegation chains.",
    imagePaths.length > 1
      ? "This meal request contains multiple photos of the SAME meal (such as full plate overview, dish close-ups, different angles, side dishes or beverages). Carefully synthesize visual information across all images to produce a single consolidated, deduplicated list of meal items."
      : "This meal request contains 1 photo of the meal.",
    imageList,
    `schemaVersion: ${schemaVersion}`,
    "Use the following task instruction as the analysis contract:",
    "----- BEGIN TASK INSTRUCTION -----",
    suppliedPrompt,
    "----- END TASK INSTRUCTION -----",
    "Return ONLY the configured structured output conforming to the schema.",
    "Never carry food identity, quantity, or confidence assumptions from another request."
  ].join("\n");

  let result;
  try {
    result = await sendRaw(prompt, taskTimeoutMs);
  } catch (error) {
    lastError = classifyAgyError(error.message);
    // A bootstrap success does not mean the provider remains available.
    stopAgy(lastError);
    throw error;
  }
  const output = result.structured_output || safeParseJson(result.response);
  if (!output || output.schemaVersion !== 1 || !Array.isArray(output.items) || !Array.isArray(output.notes)) {
    throw new Error("agy_output_invalid");
  }
  return output;
}

function enqueue(task) {
  if (queueDepth >= maxQueued) return Promise.reject(new Error("queue_full"));
  queueDepth += 1;
  const job = serial.catch(() => {}).then(task);
  serial = job.catch(() => {});
  return job.finally(() => { queueDepth -= 1; });
}

async function readRequestBody(req) {
  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    total += chunk.length;
    if (total > maxBodyBytes) throw new Error("request_too_large");
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

function json(res, status, body) {
  const data = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": String(data.length),
    "cache-control": "no-store"
  });
  res.end(data);
}

const server = createServer(async (req, res) => {
  const requestId = req.headers["x-request-id"] || crypto.randomUUID();
  res.setHeader("X-Request-Id", requestId);

  if (req.method === "GET" && req.url === "/healthz") {
    json(res, agyReady ? 200 : 503, { ok: agyReady, agyReady, queueDepth, port, model, effort, error: lastError, requestId });
    return;
  }

  if (req.method !== "POST" || req.url !== "/health2609/agy") {
    json(res, 404, { error: "not_found", requestId });
    return;
  }

  if (!bearerToken || req.headers.authorization !== `Bearer ${bearerToken}`) {
    log(`[${requestId}] unauthorized request attempt`);
    json(res, 401, { error: "unauthorized", requestId });
    return;
  }

  let imagePaths = [];
  try {
    const body = await readRequestBody(req);
    const request = new Request(`http://127.0.0.1${req.url}`, {
      method: "POST",
      headers: req.headers,
      body
    });
    const form = await request.formData();
    const images = [
      ...form.getAll("images"),
      ...form.getAll("image")
    ].filter((item) => item && typeof item.arrayBuffer === "function");

    const suppliedPrompt = String(form.get("prompt") || "");
    const schemaVersion = String(form.get("schemaVersion") || "");

    if (images.length === 0) {
      json(res, 400, { error: "image_required", requestId });
      return;
    }
    if (images.length > 5) {
      json(res, 400, { error: "too_many_images", message: "最多支持 5 张图片", requestId });
      return;
    }

    for (const image of images) {
      if (!String(image.type || "").startsWith("image/")) {
        json(res, 415, { error: "invalid_image_type", requestId });
        return;
      }
      if (image.size <= 0 || image.size > 8 * 1024 * 1024) {
        json(res, 413, { error: "image_size_invalid", requestId });
        return;
      }
    }

    if (!suppliedPrompt || suppliedPrompt.length > 30000 || schemaVersion !== "1") {
      json(res, 400, { error: "task_contract_invalid", requestId });
      return;
    }

    const tempDir = path.join(os.tmpdir(), "health2609-agy");
    await fs.mkdir(tempDir, { recursive: true });

    imagePaths = [];
    let totalBytes = 0;
    for (let i = 0; i < images.length; i++) {
      const img = images[i];
      totalBytes += img.size;
      const filePath = path.join(tempDir, `${crypto.randomUUID()}_${i}${extForMime(img.type)}`);
      await fs.writeFile(filePath, Buffer.from(await img.arrayBuffer()));
      imagePaths.push(filePath);
    }

    log(`[${requestId}] enqueuing analysis for ${images.length} image(s) (${totalBytes} bytes)`);
    const output = await enqueue(() => analyzeImages(imagePaths, suppliedPrompt, schemaVersion));
    log(`[${requestId}] analysis completed successfully; ${output.items?.length ?? 0} items identified`);
    json(res, 200, { ...output, requestId });
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown_error";
    log(`[${requestId}] analysis failed: ${message}`);
    if (message === "queue_full") {
      json(res, 429, { error: "queue_full", requestId, detail: message });
    } else if (message === "request_too_large") {
      json(res, 413, { error: "request_too_large", requestId, detail: message });
    } else {
      const code = classifyAgyError(message);
      json(res, errorStatus(code), { error: code, requestId });
    }
  } finally {
    if (imagePaths.length > 0) {
      await Promise.allSettled(imagePaths.map((p) => fs.rm(p, { force: true }).catch(() => {})));
    }
  }
});

server.listen(port, host, async () => {
  log(`listening on http://${host}:${port}`);
  if (!bearerToken) log("error: HEALTH2609_AGY_TOKEN is not set; analysis requests will be rejected");
  try {
    await ensureAgy();
  } catch (error) {
    log(`resident AGY bootstrap failed; next request will retry: ${error.message}`);
  }
});

process.on("SIGINT", () => {
  shuttingDown = true;
  stopAgy("shutdown");
  server.close(() => process.exit(0));
});

process.on("SIGTERM", () => {
  shuttingDown = true;
  stopAgy("shutdown");
  server.close(() => process.exit(0));
});

// Retry only when no request/bootstrap is active. Never recursively bootstrap
// from a bootstrap timeout or accept events from a terminated generation.
setInterval(() => {
  if (!shuttingDown && !agyReady && !startPromise && !pendingResult && queueDepth === 0) {
    ensureAgy().catch((error) => log(`readiness retry failed: ${classifyAgyError(error.message)}`));
  }
}, 30000).unref();
