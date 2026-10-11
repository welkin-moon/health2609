import test from 'node:test';
import assert from 'node:assert/strict';
import { syncRecordAad } from '../packages/contracts/src/sync.ts';
import { app } from './worker-app.mjs';
import { sqliteDb } from './sqlite-db.mjs';

const passwordVerifier = Buffer.from('fixture-password-verifier-12345678901234567890').toString('base64');
const recoveryVerifier = Buffer.from('fixture-recovery-verifier-12345678901234567890').toString('base64');

function registration() {
  const b64 = (value) => Buffer.from(value).toString('base64');
  return {
    username: 'fixture:sync-contract-test',
    passwordSalt: b64('password-salt-01'),
    passwordVerifier,
    recoverySalt: b64('recovery-salt-01'),
    recoveryVerifier,
    keyEnvelope: {
      epoch: 1,
      passwordWrappedKey: b64('wrapped-account-key-password'),
      passwordNonce: b64('123456789012'),
      recoveryWrappedKey: b64('wrapped-account-key-recovery'),
      recoveryNonce: b64('ABCDEFGHIJKL')
    },
    deviceFingerprint: 'fixture-device-one-1234',
    deviceName: 'Isolated test device one'
  };
}

async function call(path, body, token) {
  const response = await app.request(`/v1/sync/${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  }, { DB: call.DB });
  return { response, body: await response.json() };
}

function versionTwoRecord({ entityId, sourceDeviceId, clientUpdatedAt, deleted = false }) {
  const record = {
    entityType: 'home_meal',
    entityId,
    ciphertext: Buffer.from(`opaque-ciphertext-${entityId}`).toString('base64'),
    nonce: Buffer.from('123456789012').toString('base64'),
    aad: '',
    envelopeVersion: 2,
    keyEpoch: 1,
    revision: 5,
    deleted,
    clientUpdatedAt,
    sourceDeviceId
  };
  record.aad = syncRecordAad(record);
  return record;
}

async function newAccount(t) {
  const db = sqliteDb();
  t.after(db.close);
  call.DB = db.DB;
  const { response, body } = await call('auth/register', registration());
  assert.equal(response.status, 201);
  return { ...db, firstDevice: body };
}

async function enrollSecondDevice() {
  const { response, body } = await call('auth/login', {
    username: 'fixture:sync-contract-test',
    passwordVerifier,
    recoveryVerifier,
    deviceFingerprint: 'fixture-device-two-5678',
    deviceName: 'Isolated test device two'
  });
  assert.equal(response.status, 200);
  return body;
}

test('sync v2 rejects unauthenticated metadata edits and v1 records remain readable', async (t) => {
  const { firstDevice, sqlite } = await newAccount(t);
  const legacy = {
    entityType: 'home_meal',
    entityId: 'legacy-record',
    ciphertext: Buffer.from('opaque-legacy-ciphertext').toString('base64'),
    nonce: Buffer.from('123456789012').toString('base64'),
    aad: 'health2609|record|v1|epoch=1|type=home_meal|id=legacy-record|revision=1|updated=2026-10-09T10:00:00.000Z',
    envelopeVersion: 1,
    keyEpoch: 1,
    revision: 1,
    deleted: false,
    clientUpdatedAt: '2026-10-09T10:00:00.000Z'
  };
  const legacyPush = await call('push', { records: [legacy] }, firstDevice.token);
  assert.equal(legacyPush.response.status, 200);
  const legacyPull = await call('pull?cursor=0', undefined, firstDevice.token);
  assert.equal(legacyPull.response.status, 200);
  assert.equal(legacyPull.body.records[0].envelopeVersion, 1);
  assert.equal(legacyPull.body.records[0].sourceDeviceId, firstDevice.deviceId);

  const base = versionTwoRecord({
    entityId: 'authenticated-tombstone',
    sourceDeviceId: firstDevice.deviceId,
    clientUpdatedAt: '2026-10-09T10:00:00.000Z'
  });
  const changedDeleted = { ...base, deleted: true };
  const deletedEdit = await call('push', { records: [changedDeleted] }, firstDevice.token);
  assert.equal(deletedEdit.response.status, 400);
  assert.equal(deletedEdit.body.error, 'invalid_sync_record_metadata');

  const wrongSource = versionTwoRecord({
    entityId: 'wrong-source',
    sourceDeviceId: 'spoofed-device-id',
    clientUpdatedAt: '2026-10-09T10:00:00.000Z'
  });
  const sourceEdit = await call('push', { records: [wrongSource] }, firstDevice.token);
  assert.equal(sourceEdit.response.status, 400);
  assert.equal(sourceEdit.body.error, 'invalid_sync_record_metadata');
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM sync_records_v2 WHERE entity_id IN (?, ?)').get(
    'authenticated-tombstone', 'wrong-source'
  ).n, 0);

  const tombstone = { ...base, deleted: true };
  tombstone.aad = syncRecordAad(tombstone);
  const accepted = await call('push', { records: [tombstone] }, firstDevice.token);
  assert.equal(accepted.response.status, 200);
  const pulled = await call('pull?cursor=0', undefined, firstDevice.token);
  const storedTombstone = pulled.body.records.find((record) => record.entityId === 'authenticated-tombstone');
  assert.equal(storedTombstone.deleted, true);
  assert.equal(storedTombstone.aad, tombstone.aad);
});

test('SQLite conflict ordering collapses sub-millisecond differences before using device id', async (t) => {
  const { firstDevice } = await newAccount(t);
  const secondDevice = await enrollSecondDevice();
  const devices = [firstDevice, secondDevice].sort((a, b) => a.deviceId.localeCompare(b.deviceId));
  const [lowerIdDevice, higherIdDevice] = devices;

  // These timestamps have the same UTC millisecond after offset conversion,
  // while their nanoseconds fall on opposite sides of SQLite's millisecond rounding.
  const lowerIdRecord = versionTwoRecord({
    entityId: 'sub-millisecond-conflict',
    sourceDeviceId: lowerIdDevice.deviceId,
    clientUpdatedAt: '2026-10-09T10:00:00.123999999Z'
  });
  const higherIdRecord = versionTwoRecord({
    entityId: 'sub-millisecond-conflict',
    sourceDeviceId: higherIdDevice.deviceId,
    clientUpdatedAt: '2026-10-09T12:00:00.123000001+02:00'
  });

  for (const { device, record } of [
    { device: lowerIdDevice, record: lowerIdRecord },
    { device: higherIdDevice, record: higherIdRecord }
  ]) {
    const pushed = await call('push', { records: [record] }, device.token);
    assert.equal(pushed.response.status, 200);
  }

  const pulled = await call('pull?cursor=0', undefined, higherIdDevice.token);
  assert.equal(pulled.response.status, 200);
  const visible = pulled.body.records.filter((record) => record.entityId === 'sub-millisecond-conflict');
  assert.ok(visible.length > 0);
  assert.ok(visible.every((record) => record.sourceDeviceId === higherIdDevice.deviceId));
  assert.ok(visible.every((record) => record.clientUpdatedAt === higherIdRecord.clientUpdatedAt));
});
