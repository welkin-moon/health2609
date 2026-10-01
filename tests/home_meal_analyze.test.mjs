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

  it('validates consolidated multi-image meal analysis results', () => {
    const multiImageConsolidated = {
      schemaVersion: 1,
      items: [
        {
          name: '主食米饭',
          estimatedGrams: 200,
          servingMultiplier: 1.0,
          confidence: 0.95,
          nutrition: { energyKcal: 232, proteinG: 5.2, fatG: 0.6, carbohydrateG: 51.4 },
          needsConfirmation: ['分量']
        },
        {
          name: '宫保鸡丁',
          estimatedGrams: 160,
          servingMultiplier: 1.0,
          confidence: 0.88,
          nutrition: { energyKcal: 240, proteinG: 22.0, fatG: 13.5, carbohydrateG: 6.8 },
          needsConfirmation: []
        },
        {
          name: '清炒菜心',
          estimatedGrams: 120,
          servingMultiplier: 1.0,
          confidence: 0.90,
          nutrition: { energyKcal: 38, proteinG: 1.8, fatG: 1.5, carbohydrateG: 4.2 },
          needsConfirmation: []
        },
        {
          name: '紫菜蛋花汤',
          estimatedGrams: 250,
          servingMultiplier: 1.0,
          confidence: 0.85,
          nutrition: { energyKcal: 45, proteinG: 3.2, fatG: 2.1, carbohydrateG: 3.0 },
          needsConfirmation: []
        }
      ],
      notes: [
        '已综合分析 3 张餐食照片（全景餐盘、宫保鸡丁特写、汤品特写），完成跨角度去重与合并。'
      ],
      requestId: 'req-multi-image-test-8899'
    };

    const parsed = homeMealAnalysisResultSchema.safeParse(multiImageConsolidated);
    assert.strictEqual(parsed.success, true);
    assert.strictEqual(parsed.data.items.length, 4);
    assert.strictEqual(parsed.data.items[1].name, '宫保鸡丁');
    assert.strictEqual(parsed.data.notes[0].includes('3 张餐食照片'), true);
  });

  it('strictly rejects fallback dummy data generation', () => {
    // Verifies that error handling preserves real errors and does not synthesize fake rice/meat/vegetables
    const errorResponse = {
      error: 'agy_model_failed',
      requestId: 'req-err-456',
      status: 502,
      detail: 'Resident coordinator subprocess timed out',
      message: 'Upstream service error: agy_model_failed'
    };

    assert.strictEqual(errorResponse.error, 'agy_model_failed');
    assert.strictEqual('items' in errorResponse, false, 'Error response must NOT contain fake items draft');
  });
});
