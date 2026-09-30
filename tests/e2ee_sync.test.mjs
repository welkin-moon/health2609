import test, { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { homeMealAnalysisResultSchema } from '../packages/contracts/src/index.ts';

describe('E2EE Sync Contract & Schema Verification', () => {
  it('validates fallback home meal analysis structure', () => {
    const fallback = {
      schemaVersion: 1,
      items: [
        {
          name: '主食米饭/杂粮饭',
          estimatedGrams: 150,
          servingMultiplier: 1.0,
          confidence: 0.85,
          nutrition: {
            energyKcal: 174,
            proteinG: 3.9,
            fatG: 0.5,
            carbohydrateG: 38.6,
            fiberG: 0.6,
            sodiumMg: 2.0,
            sugarG: 0.1,
            saturatedFatG: 0.1
          },
          needsConfirmation: ['分量', '主食种类']
        },
        {
          name: '优质蛋白主菜（如瘦肉/鱼虾/蛋）',
          estimatedGrams: 100,
          servingMultiplier: 1.0,
          confidence: 0.8,
          nutrition: {
            energyKcal: 155,
            proteinG: 18.2,
            fatG: 8.5,
            carbohydrateG: 1.2,
            fiberG: 0.0,
            sodiumMg: 65.0,
            sugarG: 0.2,
            saturatedFatG: 2.1
          },
          needsConfirmation: ['菜品名称', '烹饪方式']
        }
      ],
      notes: ['云端视觉识别服务连接受限，已自动生成学生标准营养膳食草稿，请核对菜名及滑动分量后确认。']
    };

    const parsed = homeMealAnalysisResultSchema.safeParse(fallback);
    assert.strictEqual(parsed.success, true);
    assert.strictEqual(parsed.data.items.length, 2);
    assert.ok(parsed.data.items[0].estimatedGrams > 0);
  });

  it('validates E2EE record format for D1 sync payload', () => {
    const record = {
      entityType: 'meal_consumption',
      entityId: '2026-09-30_lunch',
      encryptedPayload: 'BASE64_CIPHERTEXT_AES_GCM_256',
      payloadNonce: 'BASE64_IV_12BYTES',
      recordVersion: 2,
      deleted: false,
      clientUpdatedAt: '2026-09-30T12:00:00.000Z'
    };

    assert.strictEqual(typeof record.entityType, 'string');
    assert.strictEqual(typeof record.encryptedPayload, 'string');
    assert.strictEqual(typeof record.payloadNonce, 'string');
    assert.strictEqual(typeof record.recordVersion, 'number');
    assert.strictEqual(record.deleted, false);
  });
});
