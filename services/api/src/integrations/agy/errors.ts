const known = new Set([
  'queue_full', 'agy_timeout', 'agy_auth_failed', 'agy_bootstrap_failed',
  'agy_model_failed', 'agy_model_unavailable', 'agy_config_invalid',
  'agy_output_invalid', 'agy_bridge_busy', 'agy_bridge_failed'
]);

export function upstreamError(status: number, body: unknown) {
  const raw = body && typeof body === 'object' && 'error' in body ? (body as { error: unknown }).error : null;
  const error = status === 401 || status === 403 || raw === 'unauthorized'
    ? 'agy_auth_failed'
    : typeof raw === 'string' && known.has(raw) ? raw
    : status === 429 ? 'queue_full'
    : [408, 504, 524].includes(status) ? 'agy_timeout'
    : 'agy_unreachable';
  const responseStatus = error === 'agy_timeout' ? 504
    : ['queue_full', 'agy_bridge_busy'].includes(error) ? 429
    : error === 'agy_output_invalid' ? 502 : 503;
  return { error, status: responseStatus as 429 | 502 | 503 | 504 };
}
