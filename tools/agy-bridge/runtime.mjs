import { existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import path from 'node:path';
import os from 'node:os';

export function modelConfig(env = process.env) {
  const model = env.HEALTH2609_AGY_MODEL || 'gemini-3.8-flash-low';
  const suffix = model.match(/-(low|medium|high|xhigh|max)$/)?.[1];
  const effort = env.HEALTH2609_AGY_EFFORT || suffix || 'low';
  if (suffix && suffix !== effort) throw new Error('agy_config_invalid');
  return { model, effort };
}

export function resolveAgyBin(env = process.env) {
  if (env.AGY_BIN) return env.AGY_BIN;
  if (process.platform !== 'win32') return 'agy';
  const installed = path.join(os.homedir(), 'AppData', 'Local', 'agy', 'bin', 'agy.exe');
  if (existsSync(installed)) return installed;
  try {
    return execFileSync('where.exe', ['agy.exe'], { encoding: 'utf8', windowsHide: true }).trim().split(/\r?\n/)[0];
  } catch {
    throw new Error('agy_bootstrap_failed');
  }
}

export function classifyAgyError(message) {
  if (/agy_config_invalid|invalid model selection/i.test(message)) return 'agy_config_invalid';
  if (/location.*not supported|FAILED_PRECONDITION|model.*not (available|found)|RESOURCE_EXHAUSTED/i.test(message)) return 'agy_model_unavailable';
  if (/UNAUTHENTICATED|not signed in|authentication|sign.in|unauthorized/i.test(message)) return 'agy_auth_failed';
  if (/agy_timeout|turn_timeout|print timeout/i.test(message)) return 'agy_timeout';
  if (/agy_output_invalid/i.test(message)) return 'agy_output_invalid';
  if (/agy_bootstrap|ENOENT|spawn/i.test(message)) return 'agy_bootstrap_failed';
  if (/agy_not_running|agy_turn_already_active/i.test(message)) return 'agy_bridge_busy';
  return 'agy_model_failed';
}

export function errorStatus(code) {
  if (code === 'agy_timeout') return 504;
  if (code === 'queue_full' || code === 'agy_bridge_busy') return 429;
  if (code === 'agy_output_invalid') return 502;
  return 503;
}
