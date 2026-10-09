import { DatabaseSync } from 'node:sqlite';
import { readFileSync, readdirSync } from 'node:fs';

// Execute the real application SQL, foreign keys, uniqueness rules and migrations.
// This is a D1-shaped local SQLite adapter, not a Cloudflare deployment test.
export function sqliteDb() {
  const sqlite = new DatabaseSync(':memory:');
  const dir = new URL('../services/api/migrations/', import.meta.url);
  for (const name of readdirSync(dir).sort()) sqlite.exec(readFileSync(new URL(name, dir), 'utf8'));
  const DB = {
    prepare(sql) {
      return { args: [], bind(...args) { this.args = args; return this; },
        async first() { return sqlite.prepare(sql).get(...this.args) ?? null; },
        async all() { return { results: sqlite.prepare(sql).all(...this.args), success: true }; },
        async run() { sqlite.prepare(sql).run(...this.args); return { success: true }; }
      };
    },
    async batch(statements) {
      sqlite.exec('BEGIN');
      try { const results = []; for (const item of statements) results.push(await item.run()); sqlite.exec('COMMIT'); return results; }
      catch (error) { sqlite.exec('ROLLBACK'); throw error; }
    }
  };
  return { DB, sqlite, close: () => sqlite.close() };
}
