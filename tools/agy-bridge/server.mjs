import { createServer } from "node:http";
import { spawn, execSync } from "node:child_process";
import { promises as fs } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";
import os from "node:os";
import crypto from "node:crypto";
import readline from "node:readline";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(
  process.env.HEALTH2609_REPO_ROOT || path.join(here, "..", "..")
);
const schemaPath = path.join(here, "home-meal.schema.json");
const host = process.env.HEALTH2609_AGY_HOST || "127.0.0.1";
const port = Number(process.env.HEALTH2609_AGY_PORT || "18787");
const agyBin = process.env.AGY_BIN || "agy";
const bearerToken = process.env.HEALTH2609_AGY_TOKEN || "";
const maxQueued = Number(process.env.HEALTH2609_AGY_MAX_QUEUE || "4");
const maxBodyBytes = 9 * 1024 * 1024;
const taskTimeoutMs = Number(process.env.HEALTH2609_AGY_TASK_TIMEOUT_MS || "85000");

let agyProcess = null;
let agyReady = false;
let startPromise = null;
let pendingResult = null;
let queueDepth = 0;
let serial = Promise.resolve();

const bootstrapPrompt = [
  "You are the resident AGY coordinator for the health2609 student health project.",
  "Your only image-analysis job is estimating home-meal contents for later user confirmation; never treat estimates as medical diagnoses or exact measurements.",
  "Each request supplies the authoritative task instruction, image path, and schema version. Read that request instruction carefully before analysis.",
  "For EVERY later meal-image request, create a fresh child/subagent to inspect that image visually. Do not reuse food items, quantities, or assumptions from earlier requests.",
  "The resident parent must validate and normalize the child result, preserve uncertainty, and return only the configured JSON schema.",
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
  const args = [
    "--print",
    "--input-format", "stream-json",
    "--output-format", "stream-json",
    "--json-schema", schemaPath,
    "--dangerously-skip-permissions",
    "--print-timeout", "2m"
  ];

  const child = spawn(agyBin, args, {
    cwd: repoRoot,
    env: process.env,
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
    if (event?.event !== "result" || !pendingResult) return;
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
    if (pendingResult) {
      const pending = pendingResult;
      pendingResult = null;
      clearTimeout(pending.timer);
      pending.reject(new Error(`agy_exit:${code ?? "null"}:${signal ?? "none"}`));
    }
  });

  child.on("error", (error) => {
    log(`AGY spawn error: ${error.message}`);
  });
}

function sendRaw(prompt, timeoutMs) {
  if (!agyProcess || !agyProcess.stdin.writable) {
    return Promise.reject(new Error("agy_not_running"));
  }
  if (pendingResult) {
    return Promise.reject(new Error("agy_turn_already_active"));
  }

  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      if (pendingResult) pendingResult = null;
      stopAgy("turn_timeout");
      reject(new Error("agy_timeout"));
    }, timeoutMs);
    pendingResult = { resolve, reject, timer };
    const message = { event: "user", message: { content: prompt } };
    agyProcess.stdin.write(`${JSON.stringify(message)}\n`);
  });
}

async function ensureAgy() {
  if (agyProcess && agyReady) return;
  if (startPromise) return startPromise;

  startPromise = (async () => {
    if (!agyProcess) spawnAgy();
    const result = await sendRaw(bootstrapPrompt, 120000);
    const ready = result.structured_output || safeParseJson(result.response);
    if (!ready || ready.schemaVersion !== 1) {
      throw new Error("agy_bootstrap_invalid");
    }
    agyReady = true;
    log(`resident AGY ready; conversation=${result.conversation_id || "unknown"}`);
  })();

  try {
    await startPromise;
  } finally {
    startPromise = null;
  }
}

function safeParseJson(value) {
  try {
    let str = String(value || "").trim();
    if (str.startsWith("```") && str.endsWith("```")) {
      str = str.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "").trim();
    }
    return JSON.parse(str);
  } catch {
    return null;
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

async function analyzeImage(filePath, suppliedPrompt, schemaVersion) {
  await ensureAgy();
  const prompt = [
    "A new health2609 home-meal request has arrived.",
    "Create a FRESH child/subagent for this request and delegate the visual inspection to that child.",
    "The child must inspect the local image file visually. Do not infer from the filename.",
    `Image file: ${filePath}`,
    `schemaVersion: ${schemaVersion}`,
    "Use the following task instruction as the analysis contract:",
    "----- BEGIN TASK INSTRUCTION -----",
    suppliedPrompt,
    "----- END TASK INSTRUCTION -----",
    "After the child returns, validate/normalize it as the resident parent and return ONLY the configured structured output.",
    "Never carry food identity, quantity, or confidence assumptions from another request."
  ].join("\n");

  const result = await sendRaw(prompt, taskTimeoutMs);
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
  if (req.method === "GET" && req.url === "/healthz") {
    json(res, agyReady ? 200 : 503, { ok: agyReady, agyReady, queueDepth, port });
    return;
  }

  if (req.method !== "POST" || req.url !== "/health2609/agy") {
    json(res, 404, { error: "not_found" });
    return;
  }

  if (!bearerToken || req.headers.authorization !== `Bearer ${bearerToken}`) {
    json(res, 401, { error: "unauthorized" });
    return;
  }

  let imagePath;
  try {
    const body = await readRequestBody(req);
    const request = new Request(`http://127.0.0.1${req.url}`, {
      method: "POST",
      headers: req.headers,
      body
    });
    const form = await request.formData();
    const image = form.get("image");
    const suppliedPrompt = String(form.get("prompt") || "");
    const schemaVersion = String(form.get("schemaVersion") || "");

    if (!image || typeof image.arrayBuffer !== "function") {
      json(res, 400, { error: "image_required" });
      return;
    }
    if (!String(image.type || "").startsWith("image/")) {
      json(res, 415, { error: "invalid_image_type" });
      return;
    }
    if (image.size <= 0 || image.size > 8 * 1024 * 1024) {
      json(res, 413, { error: "image_size_invalid" });
      return;
    }
    if (!suppliedPrompt || suppliedPrompt.length > 30000 || schemaVersion !== "1") {
      json(res, 400, { error: "task_contract_invalid" });
      return;
    }

    const tempDir = path.join(os.tmpdir(), "health2609-agy");
    await fs.mkdir(tempDir, { recursive: true });
    imagePath = path.join(tempDir, `${crypto.randomUUID()}${extForMime(image.type)}`);
    await fs.writeFile(imagePath, Buffer.from(await image.arrayBuffer()));

    const output = await enqueue(() => analyzeImage(imagePath, suppliedPrompt, schemaVersion));
    json(res, 200, output);
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown_error";
    if (message === "queue_full") json(res, 429, { error: message });
    else if (message === "request_too_large") json(res, 413, { error: message });
    else json(res, 502, { error: "agy_bridge_failed", detail: message });
  } finally {
    if (imagePath) await fs.rm(imagePath, { force: true }).catch(() => {});
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
  if (agyProcess) agyProcess.kill();
  server.close(() => process.exit(0));
});

process.on("SIGTERM", () => {
  if (agyProcess) agyProcess.kill();
  server.close(() => process.exit(0));
});
