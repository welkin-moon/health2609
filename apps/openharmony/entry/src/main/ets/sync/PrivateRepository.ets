import common from '@ohos.app.ability.common';
import preferences from '@ohos.data.preferences';
import fs from '@ohos.file.fs';
import http from '@ohos.net.http';
import { ConfirmedHomeMealRequest, ManualActivityRequest, EnergyReferenceRequest } from '../model/ApiModels';
import { CipherBox, encode, decode, base64, unbase64, random, identifier, hash, derive, keyAad, recordAad, seal, open, ensureLocalKey, localSeal, localOpen } from './PrivateCrypto';

export interface PrivateRecord {
  entityType: string; entityId: string; payloadJson: string; revision: number;
  clientUpdatedAt: string; deleted: boolean; dirty: boolean; sourceDeviceId: string;
}
interface EpochKey { epoch: number; key: string; }
interface KeyEnvelope { epoch: number; wrappedKey: string; nonce: string; }
interface Challenge { passwordSalt: string; recoverySalt: string; currentKeyEpoch: number; }
interface AuthResponse { userId: string; deviceId: string; token: string; currentKeyEpoch: number; keyEnvelopes: KeyEnvelope[]; }
interface WrappedEpoch { epoch: number; passwordWrappedKey: string; passwordNonce: string; recoveryWrappedKey: string; recoveryNonce: string; }
interface RegisterRequest {
  username: string; passwordSalt: string; passwordVerifier: string; recoverySalt: string;
  recoveryVerifier: string; deviceFingerprint: string; deviceName: string; keyEnvelope: WrappedEpoch;
}
interface LoginRequest { username: string; passwordVerifier: string; deviceFingerprint: string; deviceName: string; recoveryVerifier?: string; }
interface RemoteRecord {
  entityType: string; entityId: string; ciphertext: string; nonce: string; aad: string;
  envelopeVersion: number; keyEpoch: number; revision: number; deleted: boolean;
  clientUpdatedAt: string; sourceDeviceId?: string;
}
interface PushRequest { records: RemoteRecord[]; }
interface PullResponse { records: RemoteRecord[]; nextCursor: number; hasMore: boolean; currentKeyEpoch: number; }
interface WriteResponse { ok: boolean; acceptedCount: number; currentKeyEpoch: number; }
interface Identity { server: string; username: string; }
export function validatePayload(type: string, value: string, writing: boolean = false): void {
  if (typeof value !== 'string' || value.length > 262144) throw new Error('个人记录内容无效');
  const parsed = JSON.parse(value) as Object;
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('个人记录内容无效');
  if (type === 'home_meal') {
    const meal = JSON.parse(value) as ConfirmedHomeMealRequest;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(meal.date) || !['breakfast', 'lunch', 'dinner', 'snack'].includes(meal.mealSlot) || !Array.isArray(meal.items)) throw new Error('家庭餐记录无效');
    if (writing && (!['breakfast', 'lunch', 'dinner'].includes(meal.mealSlot) || meal.items.length < 1 || meal.items.length > 30)) throw new Error('家庭餐需选择三餐之一，食物最多30项');
    for (const item of meal.items) {
      if (typeof item.name !== 'string' || !item.name.trim() || (item.grams !== null && item.grams !== undefined && (!Number.isFinite(item.grams) || item.grams < 0))) throw new Error('家庭餐食物内容无效');
      if (writing && (item.name.length > 120 || (item.grams !== null && item.grams !== undefined && item.grams > 5000))) throw new Error('家庭餐食物超出范围');
      if (item.nutrition && item.nutrition.energyKcal !== null && item.nutrition.energyKcal !== undefined && (!Number.isFinite(item.nutrition.energyKcal) || Number(item.nutrition.energyKcal) < 0)) throw new Error('家庭餐营养内容无效');
    }
  } else if (type === 'manual_activity') {
    const activity = JSON.parse(value) as ManualActivityRequest;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(activity.date) || typeof activity.activityType !== 'string' || !activity.activityType.trim() || !Number.isFinite(activity.durationMinutes) || activity.durationMinutes < 0 || activity.durationMinutes > 1440 || !['light', 'moderate', 'vigorous'].includes(activity.intensity)) throw new Error('运动记录无效');
    if (writing && (!Number.isInteger(activity.durationMinutes) || activity.durationMinutes < 1 || activity.durationMinutes > 600 || activity.activityType.length > 80)) throw new Error('运动时长需为1至600整数分钟');
  } else if (type === 'preference') {
    const preference = JSON.parse(value) as EnergyReferenceRequest;
    if (preference.dailyEnergyReferenceKcal !== null && preference.dailyEnergyReferenceKcal !== undefined && (!Number.isFinite(preference.dailyEnergyReferenceKcal) || Number(preference.dailyEnergyReferenceKcal) < 500 || Number(preference.dailyEnergyReferenceKcal) > 6000)) throw new Error('能量偏好记录无效');
  }
}
export class PrivateState {
  version: number = 1;
  server: string = 'https://h2609.lunarlab.uk';
  username: string = '';
  fingerprint: string = '';
  userId: string = '';
  deviceId: string = '';
  token: string = '';
  pendingRecovery: string = '';
  pendingRecoverySalt: string = '';
  epoch: number = 0;
  keys: EpochKey[] = [];
  cursor: number = 0;
  records: PrivateRecord[] = [];
  lastSync: string = '';
}
class ApiError extends Error { code: number; constructor(code: number) { super(`同步服务器拒绝请求 (${code})；新设备登录需恢复短语，密钥更新需重新登录`); this.code = code; } }

export class PrivateRepository {
  private static singleton: PrivateRepository = new PrivateRepository();
  private context: common.UIAbilityContext | null = null;
  private config: preferences.Preferences | null = null;
  private state: PrivateState = new PrivateState();
  private serial: Promise<void> = Promise.resolve();
  private ready: boolean = false;
  public static getInstance(): PrivateRepository { return PrivateRepository.singleton; }
  public snapshot(): PrivateState { return JSON.parse(JSON.stringify(this.state)) as PrivateState; }
  private async exclusive<T>(operation: () => Promise<T>): Promise<T> {
    const task = this.serial.then(operation);
    this.serial = task.then(() => {}, () => {});
    return task;
  }
  public async init(context: common.UIAbilityContext): Promise<void> {
    await this.exclusive(async () => {
      if (this.ready) return;
      this.context = context;
      this.config = await preferences.getPreferences(context, 'health2609_private_identity_v1');
      const identity = JSON.parse(String(await this.config.get('identity', '{"server":"https://h2609.lunarlab.uk","username":""}'))) as Identity;
      const server = identity.server;
      const username = identity.username;
      this.state = await this.load(server, username);
      this.ready = true;
    });
  }
  private validateServer(server: string): string {
    const value = server.trim().replace(/\/+$/, '');
    if (!/^https:\/\/[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::[0-9]{1,5})?$/.test(value)) throw new Error('服务地址需为 HTTPS 域名，不含路径或查询参数');
    return value;
  }
  private owner(state: PrivateState): string {
    const identity: Identity = { server: state.server, username: state.username };
    return JSON.stringify(identity);
  }
  private async filename(server: string, username: string): Promise<string> {
    if (!this.context) throw new Error('本地安全存储未初始化');
    const identity: Identity = { server: server, username: username };
    const digest = await hash(encode(JSON.stringify(identity)));
    let name = ''; for (const byte of digest) name += byte.toString(16).padStart(2, '0');
    return this.context.filesDir + '/private-v1-' + name + '.json';
  }
  private async load(server: string, username: string): Promise<PrivateState> {
    if (typeof username !== 'string' || username.length > 160) throw new Error('本机账号身份无效');
    const normalized = this.validateServer(server);
    const file = await this.filename(normalized, username);
    const exists = fs.accessSync(file);
    const existingVault = exists || (this.context !== null && fs.listFileSync(this.context.filesDir).some((name: string) => name.startsWith('private-v1-') && name.endsWith('.json')));
    await ensureLocalKey(existingVault);
    if (!exists) {
      const state = new PrivateState(); state.server = normalized; state.username = username; state.fingerprint = 'harmony-' + identifier();
      await this.persist(state); return state;
    }
    const box = JSON.parse(fs.readTextSync(file)) as CipherBox;
    const identity: Identity = { server: normalized, username: username };
    const state = JSON.parse(await localOpen(box, 'health2609|private-vault|v1|' + JSON.stringify(identity))) as PrivateState;
    if (state.version !== 1 || state.server !== normalized || state.username !== username || !Array.isArray(state.records) || !Array.isArray(state.keys)) throw new Error('本地私密数据格式无效，已保留原文件');
    if (typeof state.token !== 'string' || typeof state.fingerprint !== 'string' || !Number.isSafeInteger(state.cursor) || state.cursor < 0 || !Number.isSafeInteger(state.epoch) || state.epoch < 0) throw new Error('本机账号状态无效，原文件已保留');
    for (const key of state.keys) { if (!Number.isSafeInteger(key.epoch) || key.epoch < 1 || unbase64(key.key).length !== 32) throw new Error('本机加密密钥无效，原文件已保留'); }
    for (const record of state.records) {
      if (typeof record.entityType !== 'string' || typeof record.entityId !== 'string' || typeof record.deleted !== 'boolean' || typeof record.dirty !== 'boolean' || typeof record.sourceDeviceId !== 'string' || !Number.isSafeInteger(record.revision) || record.revision < 1 || !Number.isFinite(Date.parse(record.clientUpdatedAt))) throw new Error('本机记录格式无效，原文件已保留');
      validatePayload(record.entityType, record.payloadJson);
    }
    state.pendingRecovery = state.pendingRecovery || '';
    state.pendingRecoverySalt = state.pendingRecoverySalt || '';
    return state;
  }
  private async persist(state: PrivateState): Promise<void> {
    const file = await this.filename(state.server, state.username);
    const box = await localSeal(JSON.stringify(state), 'health2609|private-vault|v1|' + this.owner(state));
    const temp = file + '.tmp-' + identifier();
    const handle = fs.openSync(temp, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
    try {
      const bytes = encode(JSON.stringify(box));
      let offset = 0;
      while (offset < bytes.length) {
        const count = fs.writeSync(handle.fd, bytes.slice(offset).buffer);
        if (count <= 0) throw new Error('加密记录写入未完成');
        offset += count;
      }
      fs.fsyncSync(handle.fd);
    }
    finally { fs.closeSync(handle); }
    try { fs.renameSync(temp, file); } finally { if (fs.accessSync(temp)) fs.unlinkSync(temp); }
  }
  private async commit(state: PrivateState): Promise<void> { await this.persist(state); this.state = JSON.parse(JSON.stringify(state)) as PrivateState; }
  private requireReady(): void { if (!this.ready) throw new Error('私密存储未初始化'); }
  private async request<T>(server: string, path: string, method: http.RequestMethod, body: string = '', token: string = ''): Promise<T> {
    const connection = http.createHttp();
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    try {
      const result = await connection.request(server + path, { method: method, header: headers, extraData: body || undefined, readTimeout: 30000, connectTimeout: 15000 });
      if (result.responseCode < 200 || result.responseCode >= 300) throw new ApiError(result.responseCode);
      if (typeof result.result !== 'string') throw new Error('同步响应格式无效');
      return JSON.parse(result.result) as T;
    } finally { connection.destroy(); }
  }
  public async login(server: string, username: string, password: string, recovery: string = ''): Promise<string> {
    return this.exclusive(async () => {
      this.requireReady();
      const service = this.validateServer(server), account = username.trim().toLowerCase();
      if (account.length < 3 || account.length > 160 || password.length < 8) throw new Error('用户名至少3字符，密码至少8字符');
      let challenge: Challenge | null = null;
      try { challenge = await this.request<Challenge>(service, '/v1/sync/auth/challenge?username=' + encodeURIComponent(account), http.RequestMethod.GET); }
      catch (error) { if (!(error instanceof ApiError) || error.code !== 404) throw error as Error; }
      const candidate = await this.load(service, account);
      let phrase = candidate.pendingRecovery;
      let response: AuthResponse;
      if (!challenge) {
        let raw = ''; for (const byte of random(20)) raw += byte.toString(16).padStart(2, '0').toUpperCase();
        const parts: string[] = []; for (let index = 0; index < raw.length; index += 5) parts.push(raw.slice(index, index + 5));
        phrase = parts.join('-');
        candidate.pendingRecovery = phrase;
        const passwordSalt = base64(random(16)), recoverySalt = base64(random(16)), key = random(32);
        candidate.pendingRecoverySalt = recoverySalt;
        const passwordBox = await seal(key, await derive(password, passwordSalt, 'password-wrap'), keyAad(account, 1, 'password'));
        const recoveryBox = await seal(key, await derive(raw, recoverySalt, 'recovery-wrap'), keyAad(account, 1, 'recovery'));
        const envelope: WrappedEpoch = { epoch: 1, passwordWrappedKey: passwordBox.ciphertext, passwordNonce: passwordBox.nonce, recoveryWrappedKey: recoveryBox.ciphertext, recoveryNonce: recoveryBox.nonce };
        const payload: RegisterRequest = { username: account, passwordSalt: passwordSalt, passwordVerifier: base64(await derive(password, passwordSalt, 'password-auth')),
          recoverySalt: recoverySalt, recoveryVerifier: base64(await derive(raw, recoverySalt, 'recovery-auth')),
          deviceFingerprint: candidate.fingerprint, deviceName: 'HarmonyOS / OpenHarmony', keyEnvelope: envelope };
        candidate.keys = [{ epoch: 1, key: base64(key) }];
        // Persist recovery material before the request: an ambiguous response
        // must not strand a newly created account without its enrollment secret.
        await this.persist(candidate);
        response = await this.request<AuthResponse>(service, '/v1/sync/auth/register', http.RequestMethod.POST, JSON.stringify(payload));
      } else {
        // A pending registration can race another device. Only reuse its
        // recovery material when the server still has that enrollment salt.
        if (candidate.pendingRecovery && candidate.pendingRecoverySalt !== challenge.recoverySalt) {
          candidate.pendingRecovery = ''; candidate.pendingRecoverySalt = ''; phrase = '';
        }
        const payload: LoginRequest = { username: account, passwordVerifier: base64(await derive(password, challenge.passwordSalt, 'password-auth')), deviceFingerprint: candidate.fingerprint, deviceName: 'HarmonyOS / OpenHarmony' };
        const enrollment = recovery.trim() || candidate.pendingRecovery;
        if (enrollment) payload.recoveryVerifier = base64(await derive(enrollment.toUpperCase().replace(/[^A-Z0-9]/g, ''), challenge.recoverySalt, 'recovery-auth'));
        response = await this.request<AuthResponse>(service, '/v1/sync/auth/login', http.RequestMethod.POST, JSON.stringify(payload), candidate.token);
        if (recovery.trim() && candidate.pendingRecovery.toUpperCase().replace(/[^A-Z0-9]/g, '') !== recovery.toUpperCase().replace(/[^A-Z0-9]/g, '')) {
          candidate.pendingRecovery = ''; candidate.pendingRecoverySalt = ''; phrase = '';
        }
        const wrappingKey = await derive(password, challenge.passwordSalt, 'password-wrap');
        if (!Array.isArray(response.keyEnvelopes) || response.keyEnvelopes.length === 0) throw new Error('账号缺少加密密钥包');
        const keys: EpochKey[] = [];
        for (const envelope of response.keyEnvelopes) {
          const key = await open({ ciphertext: envelope.wrappedKey, nonce: envelope.nonce }, wrappingKey, keyAad(account, envelope.epoch, 'password'));
          if (key.length !== 32) throw new Error('账号密钥格式无效');
          keys.push({ epoch: envelope.epoch, key: base64(key) });
        }
        candidate.keys = keys;
      }
      if (!response.token || !response.deviceId || !response.userId || !Number.isSafeInteger(response.currentKeyEpoch)) throw new Error('登录响应不完整');
      candidate.token = response.token; candidate.deviceId = response.deviceId; candidate.userId = response.userId; candidate.epoch = response.currentKeyEpoch;
      if (!candidate.keys.some((key: EpochKey) => key.epoch === candidate.epoch)) throw new Error('账号缺少当前加密密钥');
      await this.persist(candidate);
      if (!this.config) throw new Error('身份存储未初始化');
      const identity: Identity = { server: service, username: account };
      await this.config.put('identity', JSON.stringify(identity)); await this.config.flush();
      this.state = candidate;
      return phrase;
    });
  }
  public async acknowledgeRecovery(): Promise<void> {
    await this.exclusive(async () => {
      this.requireReady();
      const candidate = this.snapshot(); candidate.pendingRecovery = ''; candidate.pendingRecoverySalt = '';
      await this.commit(candidate);
    });
  }
  public async logout(): Promise<void> {
    await this.exclusive(async () => {
      this.requireReady();
      const candidate = this.snapshot(); candidate.token = ''; await this.persist(candidate);
      const local = await this.load(candidate.server, '');
      if (!this.config) throw new Error('身份存储未初始化');
      const identity: Identity = { server: candidate.server, username: '' };
      await this.config.put('identity', JSON.stringify(identity)); await this.config.flush(); this.state = local;
    });
  }
  public async save(entityType: string, entityId: string, payloadJson: string, deleted: boolean = false): Promise<void> {
    const expectedOwner = this.owner(this.state);
    await this.exclusive(async () => {
      this.requireReady();
      if (expectedOwner !== this.owner(this.state)) throw new Error('账号已切换，内容未保存，请在正确账号重新确认');
      if (!/^[a-z0-9_-]{1,64}$/.test(entityType) || entityId.length === 0 || entityId.length > 160) throw new Error('记录标识无效');
      validatePayload(entityType, payloadJson, !deleted);
      const candidate = this.snapshot();
      const index = candidate.records.findIndex((record: PrivateRecord) => record.entityType === entityType && record.entityId === entityId);
      const revision = index < 0 ? 1 : candidate.records[index].revision + 1;
      if (!Number.isSafeInteger(revision)) throw new Error('记录版本溢出');
      const record: PrivateRecord = { entityType: entityType, entityId: entityId, payloadJson: payloadJson, revision: revision, clientUpdatedAt: new Date().toISOString(), deleted: deleted, dirty: true, sourceDeviceId: candidate.deviceId };
      if (index < 0) candidate.records.push(record); else candidate.records[index] = record;
      await this.commit(candidate);
    });
  }
  public records(): PrivateRecord[] { this.requireReady(); return this.snapshot().records.filter((item: PrivateRecord) => !item.deleted); }
  public async sync(): Promise<void> {
    await this.exclusive(async () => {
      this.requireReady();
      const candidate = this.snapshot();
      if (!candidate.token) throw new Error('请先登录账号；本地记录不会自动上传');
      const currentKey = candidate.keys.find((entry: EpochKey) => entry.epoch === candidate.epoch);
      if (!currentKey) throw new Error('缺少当前密钥，请重新登录');
      const dirty = candidate.records.filter((entry: PrivateRecord) => entry.dirty);
      let offset = 0;
      while (offset < dirty.length) {
        const batch: PrivateRecord[] = [], records: RemoteRecord[] = [];
        while (offset + batch.length < dirty.length && batch.length < 200) {
          const record = dirty[offset + batch.length];
          const aad = recordAad(record.entityType, record.entityId, candidate.epoch, record.revision, record.clientUpdatedAt, 2, record.deleted, candidate.deviceId);
          const box = await seal(encode(record.payloadJson), unbase64(currentKey.key), aad);
          if (box.ciphertext.length > 1000000) throw new Error('单条记录超过同步限制，已保留本机记录');
          records.push({ entityType: record.entityType, entityId: record.entityId, ciphertext: box.ciphertext, nonce: box.nonce, aad: aad,
            envelopeVersion: 2, sourceDeviceId: candidate.deviceId, keyEpoch: candidate.epoch, revision: record.revision, deleted: record.deleted, clientUpdatedAt: record.clientUpdatedAt });
          const sized: PushRequest = { records: records };
          if (encode(JSON.stringify(sized)).length > 4 * 1024 * 1024) {
            records.pop();
            if (batch.length === 0) throw new Error('单条记录超过同步请求限制，记录仍在本机');
            break;
          }
          batch.push(record);
        }
        const payload: PushRequest = { records: records };
        const result = await this.request<WriteResponse>(candidate.server, '/v1/sync/push', http.RequestMethod.POST, JSON.stringify(payload), candidate.token);
        if (result.ok !== true || result.acceptedCount !== batch.length || result.currentKeyEpoch !== candidate.epoch) throw new Error('服务器未确认保存，记录仍待同步');
        for (const record of batch) { record.dirty = false; record.sourceDeviceId = candidate.deviceId; }
        await this.commit(candidate);
        offset += batch.length;
      }
      let more = true;
      while (more) {
        const page = await this.request<PullResponse>(candidate.server, '/v1/sync/pull?cursor=' + candidate.cursor + '&limit=200', http.RequestMethod.GET, '', candidate.token);
        if (typeof page.hasMore !== 'boolean' || !Array.isArray(page.records) || page.records.length > 200 || !Number.isSafeInteger(page.currentKeyEpoch) || page.currentKeyEpoch < 1 || !Number.isSafeInteger(page.nextCursor) || page.nextCursor < candidate.cursor || (page.hasMore && page.nextCursor === candidate.cursor)) throw new Error('同步分页游标无效');
        const previousCursor = candidate.cursor;
        for (const remote of page.records) {
          if ((remote.envelopeVersion !== 1 && remote.envelopeVersion !== 2) || !Number.isSafeInteger(remote.revision) || remote.revision < 1 || !Number.isFinite(Date.parse(remote.clientUpdatedAt))) throw new Error('同步记录版本无效');
          if (typeof remote.clientUpdatedAt !== 'string' || typeof remote.entityType !== 'string' || !/^[a-z0-9_-]{1,64}$/.test(remote.entityType) || typeof remote.entityId !== 'string' || !remote.entityId || remote.entityId.length > 160 || !Number.isSafeInteger(remote.keyEpoch) || remote.keyEpoch < 1 || typeof remote.deleted !== 'boolean') throw new Error('同步记录标识无效');
          if (remote.envelopeVersion === 2 && (typeof remote.sourceDeviceId !== 'string' || !remote.sourceDeviceId)) throw new Error('同步认证元数据缺失');
          const aad = recordAad(remote.entityType, remote.entityId, remote.keyEpoch, remote.revision, remote.clientUpdatedAt, remote.envelopeVersion, remote.deleted, remote.sourceDeviceId || '');
          if (remote.aad !== aad) throw new Error('同步记录元数据验证失败');
          const epoch = candidate.keys.find((entry: EpochKey) => entry.epoch === remote.keyEpoch);
          if (!epoch) throw new Error('账号密钥已更新，请重新登录后继续同步');
          const plaintext = decode(await open({ ciphertext: remote.ciphertext, nonce: remote.nonce }, unbase64(epoch.key), aad));
          validatePayload(remote.entityType, plaintext);
          const index = candidate.records.findIndex((item: PrivateRecord) => item.entityType === remote.entityType && item.entityId === remote.entityId);
          const local = index < 0 ? null : candidate.records[index];
          const timeOrder = local ? Date.parse(remote.clientUpdatedAt) - Date.parse(local.clientUpdatedAt) : 1;
          const wins = !local || remote.revision > local.revision || (remote.revision === local.revision && (timeOrder > 0 || (timeOrder === 0 && (remote.sourceDeviceId || '') > local.sourceDeviceId)));
          if (wins) {
            const record: PrivateRecord = { entityType: remote.entityType, entityId: remote.entityId, payloadJson: plaintext,
              revision: remote.revision, clientUpdatedAt: remote.clientUpdatedAt, deleted: remote.deleted, dirty: false, sourceDeviceId: remote.sourceDeviceId || '' };
            if (index < 0) candidate.records.push(record); else candidate.records[index] = record;
          }
        }
        // Whole page must decrypt and persist before its cursor is advanced.
        candidate.cursor = page.nextCursor;
        try { await this.commit(candidate); } catch (error) { candidate.cursor = previousCursor; throw error as Error; }
        more = page.hasMore;
        if (page.currentKeyEpoch > candidate.epoch) throw new Error('账号密钥已轮换，请重新登录');
      }
      candidate.lastSync = new Date().toISOString(); await this.commit(candidate);
    });
  }
}
