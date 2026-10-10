import test, { describe, it } from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import fs from "node:fs";
import {
  compareSyncVersion,
  syncLoginSchema,
  syncPushSchema,
  syncRecordEnvelopeSchema,
  syncRecordAad,
  syncRegisterSchema,
  syncRotateKeySchema
} from "../packages/contracts/src/sync.ts";

const b64 = (value) => Buffer.from(value).toString("base64");

function validRegister() {
  return {
    username: "demo-school:student-001",
    passwordSalt: b64("password-salt-01"),
    passwordVerifier: b64("password-verifier-012345678901234567890123456789"),
    recoverySalt: b64("recovery-salt-01"),
    recoveryVerifier: b64("recovery-verifier-012345678901234567890123456789"),
    keyEnvelope: {
      epoch: 1,
      passwordWrappedKey: b64("wrapped-account-key-password"),
      passwordNonce: b64("123456789012"),
      recoveryWrappedKey: b64("wrapped-account-key-recovery"),
      recoveryNonce: b64("ABCDEFGHIJKL")
    },
    deviceFingerprint: "android-12345678-1234-1234-1234-123456789012",
    deviceName: "Test Android"
  };
}

describe("E2EE sync v2 contracts", () => {
  it("requires recovery material when creating an account", () => {
    assert.equal(syncRegisterSchema.safeParse(validRegister()).success, true);
    const missing = validRegister();
    delete missing.recoveryVerifier;
    assert.equal(syncRegisterSchema.safeParse(missing).success, false);
  });

  it("allows recovery proof only as an optional device-enrollment proof on login", () => {
    const payload = {
      username: "demo-school:student-001",
      passwordVerifier: b64("password-verifier-012345678901234567890123456789"),
      deviceFingerprint: "android-12345678-1234-1234-1234-123456789012",
      deviceName: "Second device",
      recoveryVerifier: b64("recovery-verifier-012345678901234567890123456789")
    };
    assert.equal(syncLoginSchema.safeParse(payload).success, true);
    delete payload.recoveryVerifier;
    assert.equal(syncLoginSchema.safeParse(payload).success, true);
  });

  it("uses explicit versioned authenticated record envelopes", () => {
    const record = {
      entityType: "home_meal",
      entityId: "2026-10-09:dinner",
      ciphertext: b64("opaque-ciphertext-payload"),
      nonce: b64("123456789012"),
      aad: "health2609|record|v1|epoch=1|type=home_meal|id=2026-10-09:dinner|revision=1|updated=2026-10-09T10:00:00.000Z",
      envelopeVersion: 1,
      keyEpoch: 1,
      revision: 1,
      deleted: false,
      clientUpdatedAt: "2026-10-09T10:00:00.000Z"
    };
    assert.equal(syncRecordEnvelopeSchema.safeParse(record).success, true);
    assert.equal(syncPushSchema.safeParse({ records: [record] }).success, true);
    const v2 = { ...record, envelopeVersion: 2, sourceDeviceId: "device-a" };
    v2.aad = syncRecordAad(v2);
    assert.equal(syncRecordEnvelopeSchema.safeParse(v2).success, true);
    assert.equal(syncPushSchema.safeParse({ records: [v2] }).success, true);
    assert.equal(
      syncRecordEnvelopeSchema.safeParse({ ...record, envelopeVersion: 2 }).success,
      false,
      "v2 requires an explicit source device"
    );
    const noDeleted = { ...v2 };
    delete noDeleted.deleted;
    assert.equal(syncRecordEnvelopeSchema.safeParse(noDeleted).success, false);
    assert.equal(syncRecordEnvelopeSchema.safeParse({ ...v2, deleted: "false" }).success, false);
    assert.equal(
      syncRecordEnvelopeSchema.safeParse({ ...v2, envelopeVersion: 3 }).success,
      false,
      "unsupported envelope versions must fail closed"
    );
  });

  it("requires monotonic key epochs for rotation payloads", () => {
    const payload = {
      epoch: 2,
      newEpoch: 2,
      passwordWrappedKey: b64("password-wrapped-new-key"),
      passwordNonce: b64("123456789012"),
      recoveryWrappedKey: b64("recovery-wrapped-new-key"),
      recoveryNonce: b64("ABCDEFGHIJKL"),
      passwordVerifier: b64("password-verifier-012345678901234567890123456789"),
      recoveryVerifier: b64("recovery-verifier-012345678901234567890123456789")
    };
    assert.equal(syncRotateKeySchema.safeParse(payload).success, true);
    assert.equal(syncRotateKeySchema.safeParse({ ...payload, newEpoch: 3 }).success, false);
  });

  it("orders conflicts deterministically by revision, time, then device id", () => {
    const base = {
      revision: 7,
      clientUpdatedAt: "2026-10-09T10:00:00.000Z",
      sourceDeviceId: "device-a"
    };
    assert.equal(compareSyncVersion({ ...base, revision: 8 }, base), 1);
    assert.equal(
      compareSyncVersion(
        { ...base, clientUpdatedAt: "2026-10-09T10:00:01.000Z" },
        base
      ),
      1
    );
    assert.equal(
      compareSyncVersion({ ...base, sourceDeviceId: "device-b" }, base),
      1
    );
    assert.equal(compareSyncVersion(base, { ...base }), 0);
  });
});

describe("AES-GCM envelope behavior", () => {
  it("decrypts intact data and rejects a modified ciphertext", async () => {
    const key = await webcrypto.subtle.generateKey(
      { name: "AES-GCM", length: 256 },
      false,
      ["encrypt", "decrypt"]
    );
    const iv = webcrypto.getRandomValues(new Uint8Array(12));
    const aad = new TextEncoder().encode("health2609|record|v1|test");
    const plaintext = new TextEncoder().encode('{"durationMinutes":30}');

    const ciphertext = new Uint8Array(
      await webcrypto.subtle.encrypt(
        { name: "AES-GCM", iv, additionalData: aad },
        key,
        plaintext
      )
    );

    const decrypted = await webcrypto.subtle.decrypt(
      { name: "AES-GCM", iv, additionalData: aad },
      key,
      ciphertext
    );
    assert.equal(new TextDecoder().decode(decrypted), '{"durationMinutes":30}');

    const tampered = ciphertext.slice();
    tampered[0] ^= 0x01;
    await assert.rejects(() =>
      webcrypto.subtle.decrypt(
        { name: "AES-GCM", iv, additionalData: aad },
        key,
        tampered
      )
    );
  });
});

describe("E2EE implementation safety guards", () => {
  const worker = fs.readFileSync(
    new URL("../services/api/src/index.ts", import.meta.url),
    "utf8"
  );
  const migration = fs.readFileSync(
    new URL("../services/api/migrations/0013_sync_v2.sql", import.meta.url),
    "utf8"
  );
  const androidSync = fs.readFileSync(
    new URL(
      "../apps/android/app/src/main/java/uk/lunarlab/health2609/core/sync/SyncRepository.kt",
      import.meta.url
    ),
    "utf8"
  );
  const deviceBox = fs.readFileSync(
    new URL(
      "../apps/android/app/src/main/java/uk/lunarlab/health2609/core/sync/DeviceKeyStoreBox.kt",
      import.meta.url
    ),
    "utf8"
  );

  it("authenticates sync by bearer token rather than caller supplied user id", () => {
    assert.match(worker, /Authorization/);
    assert.match(worker, /token_hash/);
    assert.doesNotMatch(worker, /x-sync-user-id/);
  });

  it("stores ciphertext, key epochs and cursor changes in the v2 schema", () => {
    assert.match(migration, /sync_records_v2/);
    assert.match(migration, /ciphertext TEXT NOT NULL/);
    assert.match(migration, /key_epoch INTEGER NOT NULL/);
    assert.match(migration, /sync_changes_v2/);
    assert.doesNotMatch(migration, /plaintext/i);
  });

  it("does not persist the account password or passphrase in DataStore", () => {
    assert.doesNotMatch(androidSync, /KEY_PASSKEY_CACHED/);
    assert.doesNotMatch(androidSync, /prefs\s*\[KEY_LEGACY_PASSKEY_CACHED\]\s*=/);
    assert.match(androidSync, /removeLegacyPassword/);
  });

  it("protects local tokens and account keys with Android Keystore", () => {
    assert.match(deviceBox, /AndroidKeyStore/);
    assert.match(deviceBox, /AES\/GCM\/NoPadding/);
    assert.match(androidSync, /localBox\.seal/);
  });

  it("revokes a device token and requires a future key rotation", () => {
    assert.match(worker, /revoked_at = datetime\('now'\)/);
    assert.match(worker, /rotationRequired: true/);
    assert.match(worker, /current_key_epoch/);
  });
});
