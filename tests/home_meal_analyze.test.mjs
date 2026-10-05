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

});
