# Native sync interoperability: implementation snapshot

This is a source audit of the Android client, `packages/contracts/src/sync.ts`, and the Worker v2 routes, as of 2026-10-10. No runtime crypto vectors, device sync tests, or release installation tests were performed. The iOS and Harmony clients are being implemented separately; this document does not certify their completion.

## Status and authority

| Area | Implemented reference | Native client requirement / remaining work |
| --- | --- | --- |
| Wire encryption | Android `core/sync/E2eeCrypto.kt`; contract v1/v2 | Match bytes below; native OS local storage formats need not match Android |
| Authentication / conflict store | Worker `/v1/sync/*`, migrations 0013 and 0014 | Use token proof or recovery for device login; preserve account isolation |
| Local encrypted records | Android `SyncRepository.kt` journal in DataStore | Fail closed on damaged storage; never replace damaged data with an empty journal |
| Application payloads | Android `TodayRepository.kt`, `core/network/ApiModels.kt` | Read and write the existing entity IDs and JSON shapes |
| Native clients | Initial iOS `SyncCrypto.swift` / `SyncModels.swift` inspected | End-to-end native implementation and installed-device behavior are not certified here |
| Account migration | Android archives records on owner changes | Automatic rename of legacy school-prefixed usernames is **not implemented** |

At the audit snapshot there was no separate Android `LocalJournal` class. The local journal implementation lives in `SyncRepository`; do not assume a separate portable file format exists.

## Secret and byte encoding

An account username is trimmed and lowercased before challenge/register/login and before constructing key AAD. Current Android deliberately does **not** prefix school ID; school membership is a separate context. To retrieve an old account whose username included a school prefix, use that exact existing canonical username; silently dropping its prefix creates a different account and invalidates key AAD.

Passwords are used exactly as entered. Do not trim, lowercase, NFC/NFKC-normalize, truncate at a NUL, or encode as UTF-16 bytes. Android calls Java `PBEKeySpec(secret.toCharArray())` with `PBKDF2WithHmacSHA256`; its password-to-byte conversion is delegated to the Android provider rather than explicitly implemented in this repository. Native interoperability targets UTF-8 password bytes, including non-ASCII characters, with no Unicode normalization. This provider correspondence has not been confirmed with runtime Unicode vectors. Do not claim vector-verified Unicode compatibility. Isolated UTF-16 surrogates are outside the portable input profile.

Android generates a recovery phrase from 20 random bytes: uppercase hex, 40 characters, shown as eight groups of five separated by `-`. Example formatting only: `01234-56789-ABCDE-F0123-45678-9ABCD-EF012-34567`. Normalization uppercases the input and keeps letters/digits. For actual generated phrases the normalized form is exactly 40 ASCII hexadecimal characters. Android's broad Unicode `isLetterOrDigit` and native Unicode character sets need not agree outside that generated ASCII profile; avoid inventing a second normalization scheme for genuine phrases.

Password salt and recovery salt are independently generated, 16 random bytes each. Wire salts, derived verifiers, account keys, ciphertext and nonces use standard RFC 4648 Base64, with padding and no line breaks. Do **not** Base64url-encode these fields. Server-issued bearer tokens are opaque strings and already use Base64url; transmit them unchanged.

## Key derivation

For each purpose, first derive a 32-byte salt:

```text
purposeSalt = SHA256(rawSaltBytes || byte(0x00) || UTF8("health2609:" + purpose + ":v1"))
derived = PBKDF2-HMAC-SHA256(passwordBytes, purposeSalt, 210000 iterations, 32 output bytes)
```

| Purpose | Secret | Salt | Derived bytes / transmitted value |
| --- | --- | --- | --- |
| `password-auth` | Exact password | Password salt | Base64(derived); send as `passwordVerifier` |
| `password-wrap` | Exact password | Password salt | Raw AES-256 wrapping key; never send |
| `recovery-auth` | Normalized recovery phrase | Recovery salt | Base64(derived); send as `recoveryVerifier` |
| `recovery-wrap` | Normalized recovery phrase | Recovery salt | Raw AES-256 wrapping key; never send |

The Worker additionally stores Base64(SHA256(UTF8(verifierString))). Clients send the PBKDF2-derived Base64 verifier **before** this server hash. Verifiers are reusable authentication secrets and must be protected and transported over HTTPS. Never confuse the verifier with the wrapping key.

## AES-GCM envelopes

Every account epoch key is 32 independent random bytes. Encryption uses AES-256-GCM, a fresh random 12-byte nonce for every encryption, and a 16-byte authentication tag.

```text
ciphertextField = Base64(ciphertextBytes || tagBytes)
nonceField = Base64(nonceBytes)
AADBytes = UTF8(AADString)
```

Android `Cipher.doFinal` already returns ciphertext followed by tag. CryptoKit `combined` contains nonce as well and is **not** the wire format: concatenate `sealedBox.ciphertext + sealedBox.tag` and send nonce separately. Harmony GCM APIs must likewise preserve the full tag, whether returned inline or separately. Decryption must authenticate before exposing or saving plaintext. A nonce must never be reused with the same key.

Wrapping plaintext is the raw 32-byte epoch key, not its Base64 text or JSON. Exact key AAD:

```text
health2609|key|v1|<canonicalUsername>|<decimalEpoch>|password
health2609|key|v1|<canonicalUsername>|<decimalEpoch>|recovery
```

Record plaintext is UTF-8 application JSON. New native/Android writes use envelope v2. Exact record AAD, with no spaces or trailing newline:

```text
health2609|record|v2|epoch=<N>|type=<entityType>|id=<entityId>|revision=<R>|updated=<clientUpdatedAt>|deleted=<true-or-false>|sourceDeviceId=<pushingDeviceId>
```

Here `<true-or-false>` is exactly lowercase `true` or `false`, and `<pushingDeviceId>` is the authenticated session's server-issued device ID, not an installation fingerprint or a prelogin local source ID. The Worker requires both metadata fields explicitly, requires their values to match canonical v2 AAD, and rejects a source ID different from the bearer device. Do not overwrite either field after encryption. DB schema already stores arbitrary envelope versions; this v2 rollout does not require a DB migration.

Historical envelope v1 must remain readable with its original AAD:

```text
health2609|record|v1|epoch=<N>|type=<entityType>|id=<entityId>|revision=<R>|updated=<clientUpdatedAt>
```

The timestamp string is authenticated **verbatim**. When decrypting a pulled record, build AAD from its original `clientUpdatedAt`, not a newly formatted equivalent instant. Read only supported versions 1 and 2 and reject all others. The Worker still accepts legacy v1 pushes, including an omitted `envelopeVersion` (default 1), to preserve old-client compatibility. New clients must explicitly write version 2. No existing ciphertext, timestamp or v1 AAD is rewritten by this rollout. Do not silently ignore metadata/AAD discrepancies.

### Existing v1 integrity limits

The v1 record AAD does **not** include `deleted`, `sourceDeviceId`, account/user ID, or `serverReceivedAt`. Old Android journal formats also store local deletion/conflict metadata outside their encrypted payload. Therefore v1 GCM does not authenticate those fields; a malicious storage/server layer could change a deletion flag or conflict tie-breaker without invalidating its tag. Existing record keys are account-specific, but this is not complete metadata authentication. Do not describe v1 as authenticating all record metadata. Version 2 authenticates deletion and source identity for newly written envelopes; historical v1 remains subject to its old limits. V2 still does not include `userId` or server receipt time in record AAD. Local vault authentication remains a separate requirement from the network envelope.

## Route shapes and enrollment

All bodies are JSON. All authenticated routes use `Authorization: Bearer <opaqueToken>`; `x-sync-token` is also accepted by the Worker, but Bearer is the portable convention.

| Route | Request essentials | Response essentials |
| --- | --- | --- |
| `GET /v1/sync/auth/challenge?username=...` | URL-encoded canonical username | `passwordSalt`, `recoverySalt`, `currentKeyEpoch`; 404 means account absent |
| `POST /v1/sync/auth/register` | username, salts, both verifiers, deviceFingerprint/deviceName, `keyEnvelope` | `userId`, `deviceId`, `token`, `currentKeyEpoch: 1` |
| `POST /v1/sync/auth/login` | username, passwordVerifier, fingerprint/name; optional recoveryVerifier and existing bearer | user/device/token/currentKeyEpoch plus `keyEnvelopes` |
| `POST /v1/sync/auth/recovery` | username, recoveryVerifier | recoverySalt/currentKeyEpoch plus recovery `keyEnvelopes` |
| `GET /v1/sync/keys` | Bearer | passwordSalt/currentKeyEpoch plus password `keyEnvelopes` |
| `POST /v1/sync/auth/change-password` | Bearer, currentPasswordVerifier, new passwordSalt/passwordVerifier, complete keyEnvelopes | `ok` |
| `POST /v1/sync/auth/reset-password` | username, recoveryVerifier, new passwordSalt/passwordVerifier, complete keyEnvelopes | `ok`; all existing device sessions revoked |
| `POST /v1/sync/keys/rotate` | Bearer, both verifiers, new epoch envelope | `ok`, `currentKeyEpoch` |
| `POST /v1/sync/push` | Bearer, `{records: [...]}` | `ok`, `acceptedCount`, `currentKeyEpoch` |
| `GET /v1/sync/pull?cursor=...&limit=200` | Bearer | `ok`, `records`, `nextCursor`, `hasMore`, `currentKeyEpoch` |
| `GET /v1/sync/devices` | Bearer | `devices` |
| `POST /v1/sync/devices/<deviceId>/revoke` | Bearer; cannot revoke current device | `ok`, `rotationRequired: true`, `currentKeyEpoch` |

Registration's `keyEnvelope` contains `{epoch:1,passwordWrappedKey,passwordNonce,recoveryWrappedKey,recoveryNonce}`. Password change/reset `keyEnvelopes` entries contain `{epoch,passwordWrappedKey,passwordNonce}` and must cover **every** stored epoch exactly once. Recovery reset does not require the forgotten password or `currentPasswordVerifier`.

Login, recovery and GET keys responses use a different envelope shape: `{epoch,wrappedKey,nonce}`. Login/GET keys provide password-wrapped keys; recovery provides recovery-wrapped keys. Do not decode those responses using registration field names. Retain all unwrapped historical epoch keys, not only the current epoch.

An installation fingerprint is a stable random installation identifier (Android `android-<UUID>`), not a hardware ID or authorization proof. Existing-device login without recovery works only when a currently valid bearer proves possession of the **same** user/device that the fingerprint names. New/revoked/lost-token device login needs recovery, even with a correct password. Attach an existing bearer only when server URL and account match. A successful login rotates that device token; persist the returned replacement securely.

Password reset revokes all device sessions. Its client must subsequently login using the new password and recovery phrase. Password changes and rotation are protected by atomic account-state guards. `409 account_state_changed` means another operation changed credentials, token or epoch: preserve local data, refresh/retry deliberately. `409 stale_key_epoch` on push requires downloading/unwrapping new key envelopes before reencrypting dirty records.

Rotation requires `epoch == newEpoch == currentKeyEpoch + 1`, both current verifiers, and both wrapped copies of a freshly generated epoch key. Old record ciphertext is not automatically rewritten. Device revocation alone does not change the epoch key; show the remaining rotation requirement honestly.

## Push, acknowledgement and pull

Each record includes `entityType`, `entityId`, `ciphertext`, `nonce`, `aad`, `envelopeVersion`, `keyEpoch`, `revision`, `deleted`, `clientUpdatedAt`. V2 must additionally include `sourceDeviceId`, and `deleted` must be an explicit JSON boolean; strings or omitted values are rejected. Its source must match the authenticated session's device ID. V1 does not require a source field; the Worker supplies the authenticated device ID as before. Both versions are stored with the authenticated device ID.

Current limits are 200 records per push, 4 MiB total HTTP body, ciphertext string at most 1,000,000 characters, positive integer epoch up to 1,000,000, and revision up to JavaScript's `9007199254740991`. Android currently chunks by count only, **not by encoded body size**. Native clients should size-bound batches too; on 413 keep dirty records rather than acknowledging them.

A 2xx push means the server processed the submitted batch. `acceptedCount` currently counts submitted records, not necessarily winning conflict writes. It is **not** a per-record winning-version acknowledgement. After successful push, mark only the exact unchanged submitted local version clean; a local edit made during the network call must remain dirty. Pull afterward to obtain the authoritative conflict winners. Never acknowledge on HTTP failure or malformed success response.

Pull returns the latest stored state for each matching change-log entry, not historical snapshots. The same entity may appear repeatedly, and an entry may reference its newer version. Merge idempotently. Change IDs are globally monotonic; each account cursor may contain gaps. Use the returned `nextCursor` directly; never increment cursor by record count. `hasMore` is true when the result filled the requested limit, so an extra empty page is possible.

Decrypt/validate/apply an entire page before advancing its durable cursor. Missing epoch keys, bad tags, unknown versions, malformed application payloads or disk/keychain failure must prevent cursor advancement. Android's journal edit is transactional; its cursor is saved after successful apply, so a crash between them replays the page safely. If a page has missing epochs, Android sets key-refresh-required and throws without advancing cursor. Do not skip an undecryptable record while committing the rest of the page's cursor.

Version ordering is descending `(revision, timestamp instant, sourceDeviceId lexical order)`. Keep dirty local records if they win; a winning newer remote record can replace an older local version. Do not use serialized timestamp lexical ordering for offset timestamps. Server SQL uses `julianday`, shared JavaScript comparator uses `Date.parse`, and Android uses `Instant`: **submillisecond timestamp precision is not identical across these implementations**. For new native writes use canonical UTC millisecond timestamps, e.g. `2026-10-10T06:29:18.123Z`. Do not reformat pulled timestamps, because that changes AAD. Aligning all writers to milliseconds remains a compatibility requirement, not a runtime-verified property of existing Android records.

## Application records used by Android

| Entity type | Entity ID | Plaintext JSON shape | Android behavior |
| --- | --- | --- | --- |
| `home_meal` | `<date>:<mealSlot>` | `{date,mealSlot,items:[{name,grams,nutrition}]}` | Local encrypted journal; replaces same date/slot in displayed home meals |
| `manual_activity` | `<date>:<UUID>` | `{date,activityType,durationMinutes,intensity}` | Local encrypted journal; adds to the displayed daily activity summary |
| `preference` | `energy_reference` | `{dailyEnergyReferenceKcal: number|null}` | Local encrypted journal; null is an explicit cleared reference |
| `school_meal` | `<date>:lunch` | `{date,mealSlot:"lunch",items:[{dishId,servingMultiplier,consumedGrams}]}` | Enqueued privately **and** sent to the school API |
| `outside_activity` | `<date>` | `{date,exerciseMinutes,steps:number|null,activeEnergyKcal:number|null}` | Enqueued privately **and** sent to the school API |

`date` is `YYYY-MM-DD`; meal slots come from the shared application contract. Nutrition object fields are `energyKcal`, `proteinG`, `fatG`, `carbohydrateG`, `fiberG`, `sodiumMg`, `sugarG`, `saturatedFatG`, each nullable. Android's current home-meal writer emits all fields with explicit nulls. JSON key order is not a protocol requirement: encrypt bytes as produced, decrypt to JSON and decode by field names. Do not rescale the stored nutrition again solely because grams are present.

Manual activity readers support an optional `estimatedActiveEnergyKcal`, although the current Android writer above does not emit it. `intensity` values are `light`, `moderate`, `vigorous`. Do not discard valid unknown record types during journal merge: retain their authenticated payload even if the current UI cannot display them.

Android currently overlays home meals, manual activity and energy preference from the private journal. It does not replay pulled `school_meal` or `outside_activity` records into the school API. Sync completion therefore does not imply every synced entity is reflected in every native screen. Cross-account E2EE isolation also does not automatically isolate the legacy school/demo API's data.

Tombstones remain versioned records; preserve them for convergence. Filtering a tombstoned local home meal without suppressing the legacy server copy can make deleted data reappear in a merged screen. Native overlays need explicit replacement/deletion semantics rather than simply appending nondeleted local records.

## Local storage and scope changes

Portable scope is the canonical service URL plus canonical account username. Do not key record ownership by school ID alone, and do not share record/cursor/token/epoch-key state across servers. Normalize the service URL consistently before scope creation; current Android compares its session URL to current configured `baseUrl` exactly.

Android seals tokens, epoch keys and individual local payloads with a non-exportable Android Keystore key. Those local ciphertexts cannot be copied directly to iOS/Harmony; migrate records through authenticated account envelopes, not by copying device storage. Native implementations must provide their own protected local key and authenticated encrypted vault or equivalent OS protected storage.

On Android account switch, the previous encrypted local journal is archived by owner and the destination journal is restored if present; active token and epoch-key set are replaced, and pull cursor/sync metadata reset when owner changes. First-login records without a previous owner are adopted into the newly logged-in account. These are existing client choices, not server-enforced migration rules. Native implementations should retain an explicit anonymous scope and ask for deliberate adoption, or clearly document equivalent first-login adoption.

Persist a successful decrypted new session and its journal selection atomically before exposing it to writes. Serialize account switches, recovery, synchronization and user edits or bind edits to a captured owner. Switching account while a save is pending must not upload the old account's record to the new account. A successful credential change that cannot be saved locally is recoverable via the server/recovery flow; never erase the old journal as an error fallback.

Legacy school-prefixed accounts and old local files require explicit migration/account selection. No automatic server account merge, cloud ciphertext import from migration 0012, or username rename is established by this document.

## Native source-review checklist

1. Match purpose-salt bytes, PBKDF2 iteration/output size, GCM ciphertext/tag layout, exact AAD and response envelope field names.
2. Never trim passwords or save password/verifiers in ordinary preferences/logs; protect tokens and all account epoch keys.
3. Authenticate before decoding records; reject unsupported versions and mismatched AAD. Keep original pulled timestamp strings.
4. Persist all historical keys, page merge and cursor safely; fail closed on storage errors. Do not turn corrupt encrypted storage into a new empty account.
5. Use account/server scope for journals and cursors; do not merge another account's dirty records on login.
6. Preserve dirty versions after failed pushes, concurrent local edits, 413, or stale epoch responses; do not interpret acceptedCount as a winning-version acknowledgement.
7. Confirm the private journal is actually connected to native meal/activity/preferences save paths and displayed summaries before reporting platform sync complete.

The initial iOS crypto source inspected during this audit matches the purpose-salt layout, 210,000-round HMAC-SHA256 derivation, 12-byte nonce, 16-byte appended tag and v1 key/record AAD strings. V2 updates are owned by each platform's implementation agent and must use the definition above. This is source-level agreement only; it does not prove SDK compilation, Unicode provider compatibility, account recovery or installed-device synchronization.
