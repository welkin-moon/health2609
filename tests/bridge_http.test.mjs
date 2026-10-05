import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { modelConfig, classifyAgyError } from '../tools/agy-bridge/runtime.mjs';

const root = fileURLToPath(new URL('..', import.meta.url));
async function start(t, scenario = 'success') {
  const socket = createServer().listen(0, '127.0.0.1');
  await once(socket, 'listening');
  const port = socket.address().port;
  await new Promise((resolve) => socket.close(resolve));
  const child = spawn(process.execPath, ['tools/agy-bridge/server.mjs'], { cwd: root, windowsHide: true,
    env: { ...process.env, HEALTH2609_AGY_PORT: String(port), HEALTH2609_AGY_TOKEN: 'fixture-only',
      HEALTH2609_AGY_MODEL: 'gemini-3.8-flash-low', HEALTH2609_AGY_EFFORT: 'low',
      HEALTH2609_AGY_TASK_TIMEOUT_MS: '120', HEALTH2609_AGY_MAX_QUEUE: '1', AGY_BIN: process.execPath,
      AGY_BIN_PREFIX_ARGS: JSON.stringify([fileURLToPath(new URL('./fixtures/fake-agy.mjs', import.meta.url))]),
      FAKE_AGY_SCENARIO: scenario }
  });
  let logs = '';
  child.stdout.on('data', (chunk) => { logs += chunk; });
  child.stderr.on('data', (chunk) => { logs += chunk; });
  t.after(async () => { child.kill(); if (child.exitCode == null) await once(child, 'exit'); });
  const url = `http://127.0.0.1:${port}`;
  for (let i = 0; i < 80; i++) {
    if (logs.includes('resident AGY ready') || logs.includes('resident AGY bootstrap failed')) return url;
    if (child.exitCode != null) throw new Error(logs);
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
  throw new Error(`bridge startup did not complete: ${logs}`);
}
function upload(url, authorized = true) {
  const form = new FormData();
  form.append('images', new Blob([Buffer.from('tiny-fixture')], { type: 'image/png' }), 'fixture.png');
  form.set('prompt', 'Inspect the supplied fixture image.');
  form.set('schemaVersion', '1');
  return fetch(`${url}/health2609/agy`, { method: 'POST', body: form,
    headers: { 'X-Request-Id': 'bridge-fixture', ...(authorized ? { Authorization: 'Bearer fixture-only' } : {}) } });
}

test('model effort follows its suffix and rejects conflicting configuration', () => {
  assert.deepEqual(modelConfig({ HEALTH2609_AGY_MODEL: 'gemini-3.8-flash-medium' }), { model: 'gemini-3.8-flash-medium', effort: 'medium' });
  assert.throws(() => modelConfig({ HEALTH2609_AGY_MODEL: 'gemini-3.8-flash-medium', HEALTH2609_AGY_EFFORT: 'low' }), /agy_config_invalid/);
  assert.equal(classifyAgyError('FAILED_PRECONDITION: User location is not supported'), 'agy_model_unavailable');
});

test('actual bridge HTTP success/auth/overload uses a deterministic provider', async (t) => {
  const url = await start(t);
  assert.equal((await fetch(`${url}/healthz`)).status, 200);
  assert.equal((await upload(url, false)).status, 401);
  const [a, b] = await Promise.all([upload(url), upload(url)]);
  assert.deepEqual([a.status, b.status].sort(), [200, 429]);
  const success = a.status === 200 ? a : b;
  assert.equal((await success.json()).requestId, 'bridge-fixture');
  assert.equal((await upload(url)).status, 200);
});

test('actual bridge classifies provider regional rejection and reports not-ready', async (t) => {
  const url = await start(t, 'location');
  const health = await fetch(`${url}/healthz`);
  assert.equal(health.status, 503);
  assert.equal((await health.json()).error, 'agy_model_unavailable');
  const result = await upload(url);
  assert.equal(result.status, 503);
  assert.equal((await result.json()).error, 'agy_model_unavailable');
});

test('actual bridge preserves output-invalid and timeout classes', async (t) => {
  for (const [scenario, status, code] of [['invalid', 502, 'agy_output_invalid'], ['timeout', 504, 'agy_timeout']]) {
    const url = await start(t, scenario);
    const response = await upload(url);
    assert.equal(response.status, status);
    assert.equal((await response.json()).error, code);
    if (scenario === 'timeout') {
      const retry = await upload(url);
      assert.equal(retry.status, 504, 'old-process exit must not fail the next generation');
      assert.equal((await retry.json()).error, 'agy_timeout');
    }
  }
});
