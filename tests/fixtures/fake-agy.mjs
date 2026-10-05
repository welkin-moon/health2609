// Deterministic provider for bridge contract tests. This never calls a model.
import readline from 'node:readline';
let turn = 0;
const lines = readline.createInterface({ input: process.stdin });
lines.on('close', () => process.exit(0));
lines.on('line', (line) => {
  const input = JSON.parse(line);
  if (!input.message.content) return;
  turn += 1;
  if (process.env.FAKE_AGY_SCENARIO === 'location' || (turn > 1 && process.env.FAKE_AGY_SCENARIO === 'runtime-location')) {
    console.log(JSON.stringify({ event: 'result', result: { status: 'ERROR', error: 'FAILED_PRECONDITION: User location is not supported for the API use.' } }));
    return;
  }
  if (turn > 1 && process.env.FAKE_AGY_SCENARIO === 'timeout') return;
  const output = turn === 1 ? { schemaVersion: 1, items: [], notes: ['resident_ready'] }
    : process.env.FAKE_AGY_SCENARIO === 'invalid' ? { schemaVersion: 2 }
    : { schemaVersion: 1, items: [], notes: ['fixture contains no food'] };
  setTimeout(() => console.log(JSON.stringify({ event: 'result', result: { status: 'SUCCESS', structured_output: output } })), turn === 1 ? 0 : 50);
});
