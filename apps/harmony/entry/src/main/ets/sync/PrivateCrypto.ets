import crypto from '@ohos.security.cryptoFramework';
import huks from '@ohos.security.huks';
import util from '@ohos.util';

export interface CipherBox { ciphertext: string; nonce: string; }
const b64 = new util.Base64Helper();
export function encode(value: string): Uint8Array { return new util.TextEncoder().encodeInto(value); }
export function decode(value: Uint8Array): string { return util.TextDecoder.create('utf-8', { fatal: true }).decodeToString(value); }
export function base64(value: Uint8Array): string { return b64.encodeToStringSync(value); }
export function unbase64(value: string): Uint8Array { return b64.decodeSync(value); }
function concat(left: Uint8Array, right: Uint8Array): Uint8Array {
  const result = new Uint8Array(left.length + right.length); result.set(left); result.set(right, left.length); return result;
}
export function random(size: number): Uint8Array { return crypto.createRandom().generateRandomSync(size).data; }
export function identifier(): string {
  let result = ''; for (const byte of random(16)) result += byte.toString(16).padStart(2, '0'); return result;
}
export async function hash(value: Uint8Array): Promise<Uint8Array> {
  const digest = crypto.createMd('SHA256'); await digest.update({ data: value }); return (await digest.digest()).data;
}
export async function derive(secret: string, salt: string, purpose: string): Promise<Uint8Array> {
  const saltBytes = unbase64(salt);
  if (saltBytes.length !== 16) throw new Error('账号盐长度无效');
  const purposeSalt = await hash(concat(concat(saltBytes, new Uint8Array([0])), encode(`health2609:${purpose}:v1`)));
  // Official API 12 PBKDF2Spec.keySize is in bytes.
  const spec: crypto.PBKDF2Spec = { algName: 'PBKDF2', password: encode(secret), salt: purposeSalt, iterations: 210000, keySize: 32 };
  return (await crypto.createKdf('PBKDF2|SHA256').generateSecret(spec)).data;
}
export function keyAad(username: string, epoch: number, kind: string): string { return `health2609|key|v1|${username}|${epoch}|${kind}`; }
export function recordAad(type: string, id: string, epoch: number, revision: number, updated: string, version: number, deleted: boolean, source: string): string {
  if (version !== 1 && version !== 2) throw new Error('不支持的加密版本');
  const prefix = `health2609|record|v${version}|epoch=${epoch}|type=${type}|id=${id}|revision=${revision}|updated=${updated}`;
  return version === 1 ? prefix : prefix + `|deleted=${deleted ? 'true' : 'false'}|sourceDeviceId=${source}`;
}
export async function seal(data: Uint8Array, key: Uint8Array, aad: string): Promise<CipherBox> {
  if (key.length !== 32) throw new Error('账号密钥长度无效');
  const nonce = random(12);
  const spec: crypto.GcmParamsSpec = { algName: 'GcmParamsSpec', iv: { data: nonce }, aad: { data: encode(aad) }, authTag: { data: new Uint8Array(16) } };
  const cipher = crypto.createCipher('AES256|GCM|NoPadding');
  const symKey = await crypto.createSymKeyGenerator('AES256').convertKey({ data: key });
  await cipher.init(crypto.CryptoMode.ENCRYPT_MODE, symKey, spec);
  const ciphertext = await cipher.update({ data: data });
  const tag = await cipher.doFinal(null);
  if (!ciphertext || !tag || tag.data.length !== 16) throw new Error('账号加密未能完成');
  return { ciphertext: base64(concat(ciphertext.data, tag.data)), nonce: base64(nonce) };
}
export async function open(box: CipherBox, key: Uint8Array, aad: string): Promise<Uint8Array> {
  const data = unbase64(box.ciphertext), nonce = unbase64(box.nonce);
  if (key.length !== 32 || nonce.length !== 12 || data.length < 16) throw new Error('加密包格式无效');
  const spec: crypto.GcmParamsSpec = { algName: 'GcmParamsSpec', iv: { data: nonce }, aad: { data: encode(aad) }, authTag: { data: data.slice(data.length - 16) } };
  const cipher = crypto.createCipher('AES256|GCM|NoPadding');
  const symKey = await crypto.createSymKeyGenerator('AES256').convertKey({ data: key });
  await cipher.init(crypto.CryptoMode.DECRYPT_MODE, symKey, spec);
  const plaintext = await cipher.update({ data: data.slice(0, data.length - 16) });
  await cipher.doFinal(null); // Do not expose plaintext before authentication succeeds.
  if (!plaintext) throw new Error('账号解密未能完成');
  return plaintext.data;
}

const LOCAL_ALIAS = 'health2609.private-vault.v1';
function properties(purpose: number): huks.HuksParam[] {
  return [
    { tag: huks.HuksTag.HUKS_TAG_ALGORITHM, value: huks.HuksKeyAlg.HUKS_ALG_AES },
    { tag: huks.HuksTag.HUKS_TAG_KEY_SIZE, value: huks.HuksKeySize.HUKS_AES_KEY_SIZE_256 },
    { tag: huks.HuksTag.HUKS_TAG_PURPOSE, value: purpose },
    { tag: huks.HuksTag.HUKS_TAG_PADDING, value: huks.HuksKeyPadding.HUKS_PADDING_NONE },
    { tag: huks.HuksTag.HUKS_TAG_BLOCK_MODE, value: huks.HuksCipherMode.HUKS_MODE_GCM }
  ];
}
export async function ensureLocalKey(existingVault: boolean): Promise<void> {
  const exists = await huks.isKeyItemExist(LOCAL_ALIAS, { properties: [] });
  if (!exists) {
    if (existingVault) throw new Error('本机密钥不可用，已保留加密记录；请勿清除应用数据');
    // Symmetric keys generated by HUKS are non-exportable. Never generate a
    // raw software key or request KEY_EXPORT_ALLOWED as a fallback.
    await huks.generateKeyItem(LOCAL_ALIAS, { properties: properties(huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_ENCRYPT | huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_DECRYPT) });
  }
}
async function localOperation(data: Uint8Array, nonce: Uint8Array, aad: string, decrypt: boolean): Promise<Uint8Array> {
  const params = properties(decrypt ? huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_DECRYPT : huks.HuksKeyPurpose.HUKS_KEY_PURPOSE_ENCRYPT);
  params.push({ tag: huks.HuksTag.HUKS_TAG_NONCE, value: nonce });
  params.push({ tag: huks.HuksTag.HUKS_TAG_ASSOCIATED_DATA, value: encode(aad) });
  let input = data;
  if (decrypt) {
    if (data.length < 16) throw new Error('本地加密文件不完整');
    params.push({ tag: huks.HuksTag.HUKS_TAG_AE_TAG, value: data.slice(data.length - 16) });
    input = data.slice(0, data.length - 16);
  }
  const session = await huks.initSession(LOCAL_ALIAS, { properties: params });
  let completed = false;
  let output = new Uint8Array(0);
  try {
    let offset = 0;
    while (input.length - offset > 65536) {
      const part = await huks.updateSession(session.handle, { properties: params, inData: input.slice(offset, offset + 65536) });
      if (part.outData) output = concat(output, part.outData);
      offset += 65536;
    }
    const result = await huks.finishSession(session.handle, { properties: params, inData: input.slice(offset) });
    completed = true;
    if (result.outData) output = concat(output, result.outData);
    return output;
  } finally {
    if (!completed) await huks.abortSession(session.handle, { properties: [] }).catch(() => {});
  }
}
export async function localSeal(value: string, aad: string): Promise<CipherBox> {
  const nonce = random(12); return { nonce: base64(nonce), ciphertext: base64(await localOperation(encode(value), nonce, aad, false)) };
}
export async function localOpen(box: CipherBox, aad: string): Promise<string> {
  const nonce = unbase64(box.nonce); if (nonce.length !== 12) throw new Error('本机加密文件格式无效');
  return decode(await localOperation(unbase64(box.ciphertext), nonce, aad, true));
}
