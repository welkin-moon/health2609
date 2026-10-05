import { readFile } from 'node:fs/promises';

const base = process.env.HEALTH2609_SMOKE_URL || 'https://h2609.lunarlab.uk';
const id = `smoke-${crypto.randomUUID()}`;
const fixture = process.argv[2]
  ? await readFile(process.argv[2])
  : Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aV1sAAAAASUVORK5CYII=', 'base64');
const form = new FormData();
form.append('images', new Blob([fixture], { type: /\.jpe?g$/i.test(process.argv[2] || '') ? 'image/jpeg' : 'image/png' }), 'meal-fixture');
const response = await fetch(`${base}/v1/home-meals/analyze`, {
  method: 'POST', body: form, headers: { 'X-Request-Id': id }, signal: AbortSignal.timeout(150000)
});
const body = await response.json();
console.log(JSON.stringify({ status: response.status, requestId: body.requestId, error: body.error,
  itemCount: Array.isArray(body.items) ? body.items.length : null,
  itemNames: body.items?.map(item => item.name), notes: body.notes }));
if (!response.ok || body.requestId !== id || body.schemaVersion !== 1 || !Array.isArray(body.items)) process.exitCode = 1;
