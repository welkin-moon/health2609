import test from 'node:test';
import assert from 'node:assert/strict';
import { app, membershipDb } from './worker-app.mjs';

const fixture = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aV1sAAAAASUVORK5CYII=', 'base64');
const env = { DB: membershipDb(), AGY_TASK_URL: 'https://bridge.invalid/health2609/agy', AGY_TASK_TOKEN: 'test-only-token' };
function request() {
  const form = new FormData();
  form.append('images', new Blob([fixture], { type: 'image/png' }), 'tiny.png');
  return { method: 'POST', body: form, headers: { 'X-Request-Id': 'fixture-correlation-1' } };
}

test('real Worker handler forwards Android-style multipart and correlates a validated result', async (t) => {
  let forwarded;
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    forwarded = { url, init };
    return Response.json({ schemaVersion: 1, items: [], notes: ['no food in fixture'] });
  });
  const response = await app.request('/v1/home-meals/analyze', request(), env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.requestId, 'fixture-correlation-1');
  assert.equal(response.headers.get('x-request-id'), body.requestId);
  assert.equal(forwarded.init.headers['X-Request-Id'], body.requestId);
  assert.equal(forwarded.init.headers.Authorization, 'Bearer test-only-token');
  assert.equal(forwarded.init.body.get('images').type, 'image/png');
  assert.equal(forwarded.init.body.get('schemaVersion'), '1');
  assert.ok(forwarded.init.body.get('prompt').includes('Do not give weight-loss'));
});

test('real Worker maps tunnel/auth/model/queue/timeouts without leaking bridge diagnostics', async (t) => {
  for (const [upstream, error, expectedStatus, expectedCode] of [
    [502, null, 503, 'agy_unreachable'],
    [401, 'unauthorized', 503, 'agy_auth_failed'],
    [403, null, 503, 'agy_auth_failed'],
    [503, 'agy_model_unavailable', 503, 'agy_model_unavailable'],
    [502, 'agy_bootstrap_failed', 503, 'agy_bootstrap_failed'],
    [502, 'agy_output_invalid', 502, 'agy_output_invalid'],
    [429, 'queue_full', 429, 'queue_full'],
    [504, 'agy_timeout', 504, 'agy_timeout'],
    [524, null, 504, 'agy_timeout']
  ]) {
    const mock = t.mock.method(globalThis, 'fetch', async () => error
      ? Response.json({ error, detail: 'private provider diagnostic' }, { status: upstream })
      : new Response('gateway unavailable', { status: upstream }));
    const response = await app.request('/v1/home-meals/analyze', request(), env);
    const body = await response.json();
    assert.equal(response.status, expectedStatus);
    assert.equal(body.error, expectedCode);
    assert.equal(body.requestId, 'fixture-correlation-1');
    assert.ok(!JSON.stringify(body).includes('private provider diagnostic'));
    assert.ok(!('items' in body));
    mock.mock.restore();
  }
});

test('real Worker rejects malformed multipart and schema-invalid model output', async (t) => {
  const malformed = await app.request('/v1/home-meals/analyze', {
    method: 'POST', headers: { 'Content-Type': 'multipart/form-data', 'X-Request-Id': 'bad-form' }, body: 'not multipart'
  }, env);
  assert.equal(malformed.status, 400);
  assert.equal((await malformed.json()).requestId, 'bad-form');
  t.mock.method(globalThis, 'fetch', async () => Response.json({ schemaVersion: 1, items: [{ name: 'invalid', confidence: 3 }], notes: [] }));
  const invalid = await app.request('/v1/home-meals/analyze', request(), env);
  assert.equal((await invalid.json()).error, 'agy_schema_invalid');
});

test('saved lunch portions are returned by the real menu handler', async () => {
  const DB = membershipDb([{ id: 'rice', name: '米饭', standard_serving_grams: 150,
    nutrition_per_serving_json: null, serving_multiplier: 0.75, consumed_grams: 112.5 }]);
  const result = await app.request('/v1/today/menu?date=2026-10-05', {}, { ...env, DB });
  const menu = await result.json();
  assert.equal(result.status, 200);
  assert.equal(menu.dishes[0].savedServingMultiplier, 0.75);
  assert.equal(menu.dishes[0].savedConsumedGrams, 112.5);
});

test('unfinished sync does not accept a spoofed user ID or claim backup success', async () => {
  for (const route of ['pull', 'devices', 'push', 'auth/register', 'auth/login']) {
    const result = await app.request(`/v1/sync/${route}`, { method: ['pull','devices'].includes(route) ? 'GET' : 'POST', headers: { 'x-sync-user-id': 'someone-else' } }, env);
    assert.equal(result.status, 503);
    assert.equal((await result.json()).error, 'sync_not_available');
  }
});

test('saved home meal details can be read and invalid calendar dates are rejected', async () => {
  const items = [{ name: '炒饭', grams: 180, nutrition: { energyKcal: 300 } }];
  const DB = membershipDb([{ meal_slot: 'dinner', confirmed_items_json: JSON.stringify(items) }]);
  const result = await app.request('/v1/home-meals?date=2026-10-05', {}, { ...env, DB });
  assert.equal(result.status, 200);
  assert.deepEqual((await result.json()).meals, [{ mealSlot: 'dinner', items }]);
  const invalid = await app.request('/v1/home-meals?date=2026-02-30', {}, env);
  assert.equal(invalid.status, 400);
});

test('Android Gson manual meal payload can be saved with nutrition absent', async () => {
  let savedJson;
  const DB = { prepare(sql) { return {
    bind(...args) { if (sql.includes('INSERT INTO home_meals')) savedJson = args[4]; return this; },
    async first() { return { id: 'member-fixture' }; }, async run() { return { success: true }; }
  }; } };
  const result = await app.request('/v1/home-meals', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ date: '2026-10-05', mealSlot: 'dinner', items: [{ name: '苹果', grams: 150 }] }) }, { ...env, DB });
  assert.equal(result.status, 200);
  assert.deepEqual(JSON.parse(savedJson), [{ name: '苹果', grams: 150, nutrition: null }]);
});
