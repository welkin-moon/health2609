import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const compiled = await build({
  entryPoints: [fileURLToPath(new URL('../services/api/src/index.ts', import.meta.url))],
  bundle: true, platform: 'node', format: 'esm', write: false, minify: true
});
export const app = (await import(`data:text/javascript;base64,${Buffer.from(compiled.outputFiles[0].contents).toString('base64')}`)).default;

export function membershipDb(rows = []) {
  return { prepare(sql) { return {
    bind(...args) { return this; },
    async first() { return { id: 'member-fixture', class_group_id: null }; },
    async all() { return { results: rows }; },
    async run() { return { success: true }; }
  }; } };
}
