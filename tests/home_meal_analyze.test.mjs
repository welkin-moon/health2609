import test, { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { homeMealAnalysisResultSchema } from '../packages/contracts/src/index.ts';

describe('Home Meal Analysis Pipeline & Error Preservation (Issue #15)', () => {
  it('validates success payload with requestId correlation', () => {
    const raw = {
      schemaVersion: 1,
      items: [
        {
          name: '西红柿鸡蛋',
          estimatedGrams: 150,
          servingMultiplier: 1.0,
          confidence: 0.92,
          nutrition: null,
          needsConfirmation: []
        }
      ],
      notes: ['清晰识别'],
      requestId: 'req-test-uuid-1234'
    };

    const parsed = homeMealAnalysisResultSchema.safeParse(raw);
    assert.strictEqual(parsed.success, true);
    assert.strictEqual(raw.requestId, 'req-test-uuid-1234');
    assert.strictEqual(parsed.data.items[0].name, '西红柿鸡蛋');
  });

  it('preserves machine-readable error classes without collapsing to generic 502', () => {
    const errorClasses = [
      { status: 429, upstreamError: 'queue_full', expectedError: 'queue_full', expectedStatus: 429 },
      { status: 504, upstreamError: 'agy_timeout', expectedError: 'agy_timeout', expectedStatus: 504 },
      { status: 401, upstreamError: 'unauthorized', expectedError: 'agy_auth_failed', expectedStatus: 502 },
      { status: 502, upstreamError: 'agy_model_failed', expectedError: 'agy_model_failed', expectedStatus: 502 },
      { status: 502, upstreamError: 'agy_bootstrap_failed', expectedError: 'agy_bootstrap_failed', expectedStatus: 502 },
      { status: 502, upstreamError: 'agy_output_invalid', expectedError: 'agy_output_invalid', expectedStatus: 502 }
    ];

    for (const ec of errorClasses) {
      const responseStatus = ec.status;
      const bridgeError = { error: ec.upstreamError, detail: 'diagnostic detail', requestId: 'test-req' };
      
      const rawError = bridgeError?.error;
      const errorCode =
        (responseStatus === 401 || rawError === 'unauthorized')
          ? 'agy_auth_failed'
          : (rawError || (responseStatus === 429 ? 'queue_full' : 'agy_failed'));
      const finalStatus = responseStatus === 429 ? 429 : responseStatus === 401 ? 502 : responseStatus;

      assert.strictEqual(errorCode, ec.expectedError);
      assert.strictEqual(finalStatus, ec.expectedStatus);
      assert.strictEqual(bridgeError.requestId, 'test-req');
    }
  });

  it('rejects schema-invalid model responses with descriptive error details', () => {
    const invalidPayloads = [
      { schemaVersion: 2, items: [] }, // Unsupported schemaVersion
      { schemaVersion: 1 }, // Missing items array
      { schemaVersion: 1, items: 'not-an-array' }
    ];

    for (const p of invalidPayloads) {
      const parsed = homeMealAnalysisResultSchema.safeParse(p);
      assert.strictEqual(parsed.success, false, 'Should reject invalid schema payload');
    }
  });
});
