import { z } from "zod";

const opaqueBase64Schema = z.string().min(8).max(1_000_000);
const fingerprintSchema = z.string().trim().min(8).max(200);
const deviceNameSchema = z.string().trim().min(1).max(120);
const verifierSchema = z.string().min(32).max(256);

export const syncKeyEpochEnvelopeSchema = z.object({
  epoch: z.number().int().min(1).max(1_000_000),
  passwordWrappedKey: opaqueBase64Schema,
  passwordNonce: opaqueBase64Schema,
  recoveryWrappedKey: opaqueBase64Schema,
  recoveryNonce: opaqueBase64Schema
});

export const syncRegisterSchema = z.object({
  username: z.string().trim().min(3).max(160),
  passwordSalt: opaqueBase64Schema,
  passwordVerifier: verifierSchema,
  recoverySalt: opaqueBase64Schema,
  recoveryVerifier: verifierSchema,
  keyEnvelope: syncKeyEpochEnvelopeSchema.extend({ epoch: z.literal(1) }),
  deviceFingerprint: fingerprintSchema,
  deviceName: deviceNameSchema
});

export const syncLoginSchema = z.object({
  username: z.string().trim().min(3).max(160),
  passwordVerifier: verifierSchema,
  deviceFingerprint: fingerprintSchema,
  deviceName: deviceNameSchema,
  recoveryVerifier: verifierSchema.optional()
});

export const syncRecordEnvelopeSchema = z.object({
  entityType: z.string().trim().min(1).max(64),
  entityId: z.string().trim().min(1).max(160),
  ciphertext: opaqueBase64Schema,
  nonce: opaqueBase64Schema,
  aad: z.string().min(1).max(1000),
  envelopeVersion: z.literal(1),
  keyEpoch: z.number().int().min(1).max(1_000_000),
  revision: z.number().int().min(1).max(Number.MAX_SAFE_INTEGER),
  deleted: z.boolean().default(false),
  clientUpdatedAt: z.string().datetime({ offset: true })
});

export const syncPushSchema = z.object({
  records: z.array(syncRecordEnvelopeSchema).max(200)
});

export const syncRotateKeySchema = syncKeyEpochEnvelopeSchema.extend({
  newEpoch: z.number().int().min(2).max(1_000_000),
  passwordVerifier: verifierSchema,
  recoveryVerifier: verifierSchema
}).refine((value) => value.newEpoch === value.epoch, {
  message: "newEpoch must match envelope epoch",
  path: ["newEpoch"]
});

export const syncChangePasswordSchema = z.object({
  currentPasswordVerifier: verifierSchema,
  passwordSalt: opaqueBase64Schema,
  passwordVerifier: verifierSchema,
  keyEnvelopes: z.array(z.object({
    epoch: z.number().int().min(1),
    passwordWrappedKey: opaqueBase64Schema,
    passwordNonce: opaqueBase64Schema
  })).min(1).max(1000)
});

export const syncRecoveryResetPasswordSchema = syncChangePasswordSchema.extend({
  username: z.string().trim().min(3).max(160),
  recoveryVerifier: verifierSchema
});

export type SyncKeyEpochEnvelope = z.infer<typeof syncKeyEpochEnvelopeSchema>;
export type SyncRegisterInput = z.infer<typeof syncRegisterSchema>;
export type SyncLoginInput = z.infer<typeof syncLoginSchema>;
export type SyncRecordEnvelope = z.infer<typeof syncRecordEnvelopeSchema>;
export type SyncPushInput = z.infer<typeof syncPushSchema>;


export const syncPulledRecordSchema = syncRecordEnvelopeSchema.extend({
  sourceDeviceId: z.string().min(1).max(200),
  serverReceivedAt: z.string().datetime({ offset: true })
});

export type SyncVersionStamp = {
  revision: number;
  clientUpdatedAt: string;
  sourceDeviceId: string;
};

/**
 * Deterministic last-writer ordering shared by clients and the Worker:
 * revision, then client timestamp, then source device id.
 */
export function compareSyncVersion(
  left: SyncVersionStamp,
  right: SyncVersionStamp
): number {
  if (left.revision !== right.revision) {
    return left.revision < right.revision ? -1 : 1;
  }
  if (left.clientUpdatedAt !== right.clientUpdatedAt) {
    return left.clientUpdatedAt < right.clientUpdatedAt ? -1 : 1;
  }
  if (left.sourceDeviceId === right.sourceDeviceId) return 0;
  return left.sourceDeviceId < right.sourceDeviceId ? -1 : 1;
}

export type SyncPulledRecord = z.infer<typeof syncPulledRecordSchema>;
