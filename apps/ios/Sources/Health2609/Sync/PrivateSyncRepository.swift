import Foundation

actor PrivateSyncRepository {
    static let shared = PrivateSyncRepository()
    static let defaultServer = "https://h2609.lunarlab.uk"
    private var vault: PrivateVault?
    private var localKey: Data?
    private var operationBusy = false
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private func vaultURL() throws -> URL {
        let folder = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        return folder.appendingPathComponent("health2609-private-v1.json.enc")
    }
    private func load() throws -> PrivateVault {
        if let vault = vault { return vault }
        let url = try vaultURL()
        let exists = FileManager.default.fileExists(atPath: url.path)
        let key: Data
        if let stored = try PrivateKeychain.read("vault-key") { key = stored }
        else {
            guard !exists else { throw SyncFailure("本机记录的保护密钥缺失，已保留原始文件；不要卸载或清除应用数据") }
            key = try SyncCrypto.random(32)
            try PrivateKeychain.write("vault-key", data: key)
        }
        localKey = key
        let loaded: PrivateVault
        if exists {
            do {
                let sealed = try decoder.decode(SyncSealed.self, from: Data(contentsOf: url))
                loaded = try decoder.decode(PrivateVault.self, from: SyncCrypto.open(sealed, key: key, aad: "health2609|ios-vault|v1"))
                guard loaded.version == 1 else { throw SyncFailure("本机记录版本不支持") }
            } catch { throw SyncFailure("本机加密记录暂时无法读取，原始数据已保留。请解锁后重试，不要清除应用数据。") }
        } else { loaded = PrivateVault() }
        vault = loaded
        return loaded
    }
    private func commit(_ value: PrivateVault) throws {
        guard let key = localKey else { throw SyncFailure("本机保护密钥不可用") }
        let sealed = try SyncCrypto.seal(encoder.encode(value), key: key, aad: "health2609|ios-vault|v1")
        var url = try vaultURL()
        try encoder.encode(sealed).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        vault = value // Once the atomic write succeeds, never keep a stale in-memory journal.
        try url.setResourceValues(values)
    }
    private func fingerprint() throws -> String {
        if let data = try PrivateKeychain.read("fingerprint"), let value = String(data: data, encoding: .utf8) { return value }
        let value = "ios-" + UUID().uuidString.lowercased()
        try PrivateKeychain.write("fingerprint", data: Data(value.utf8))
        return value
    }
    private func now() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        return formatter.string(from: Date())
    }
    private func parsedTime(_ value: String) throws -> SyncTimeStamp {
        let regex = try NSRegularExpression(pattern: "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?(Z|[+-]\\d{2}:\\d{2})$")
        let text = value as NSString
        guard let match = regex.firstMatch(in: value, range: NSRange(location: 0, length: text.length)),
              let date = ISO8601DateFormatter().date(from: text.substring(with: match.range(at: 1)) + text.substring(with: match.range(at: 3))) else {
            throw SyncFailure("同步记录时间格式无效")
        }
        let fraction = match.range(at: 2).location == NSNotFound ? "" : text.substring(with: match.range(at: 2))
        let nanos = Int64(fraction + String(repeating: "0", count: 9 - fraction.count)) ?? 0
        return SyncTimeStamp(seconds: Int64(date.timeIntervalSince1970.rounded()), nanos: nanos)
    }
    private func normalizeServer(_ raw: String) throws -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: trimmed), url.scheme == "https", url.host != nil,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil, !trimmed.contains("|") else {
            throw SyncFailure("服务地址必须是 HTTPS 地址，不含账号、查询或片段")
        }
        return trimmed
    }
    private func account(_ username: String) throws -> String {
        let name = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard (3...160).contains(name.count) else { throw SyncFailure("用户名需要 3–160 个字符") }
        return name
    }
    private func request(server: String, path: String, method: String = "GET", body: [String: Any]? = nil, token: String? = nil) async throws -> Data {
        guard let url = URL(string: server + path) else { throw SyncFailure("服务地址无效") }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 45
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body = body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            let serialized = try JSONSerialization.data(withJSONObject: body)
            guard serialized.count <= 4 * 1024 * 1024 else { throw SyncFailure("同步请求超出 4MiB，本机待同步记录已保留") }
            request.httpBody = serialized
        }
        if let token = token { request.setValue("Bearer " + token, forHTTPHeaderField: "Authorization") }
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw SyncFailure("同步响应无效") }
        if !(200...299).contains(http.statusCode) {
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            let error = json?["error"] as? String ?? "sync_failed"
            if error == "device_enrollment_requires_recovery" { throw SyncFailure("新设备或吊销后的设备需要恢复短语才能加入账号") }
            if error == "stale_key_epoch" { throw SyncFailure("账号密钥已轮换，请重新登录刷新所有历史密钥后同步") }
            throw HealthApiError.httpError(statusCode: http.statusCode, message: error)
        }
        return data
    }
    private func write(server: String, path: String, body: [String: Any], token: String? = nil) async throws {
        let data = try await request(server: server, path: path, method: "POST", body: body, token: token)
        let ack = try decoder.decode(SyncWriteAck.self, from: data)
        guard ack.ok else { throw SyncFailure("服务器没有确认处理，原有本机状态保留") }
    }
    private func challenge(_ username: String, server: String) async throws -> SyncChallenge {
        var parts = URLComponents()
        parts.queryItems = [URLQueryItem(name: "username", value: username)]
        return try decoder.decode(SyncChallenge.self, from: await request(server: server, path: "/v1/sync/auth/challenge?" + (parts.percentEncodedQuery ?? "")))
    }
    func snapshot() throws -> PrivateSnapshot {
        let value = try load()
        return PrivateSnapshot(generation: value.generation, owner: value.owner, session: value.session,
            records: Array((value.accounts[value.owner] ?? [:]).values).filter { !$0.deleted }.sorted { $0.clientUpdatedAt > $1.clientUpdatedAt },
            lastSync: value.lastSync[value.owner])
    }
    func enqueue<T: Encodable & Sendable>(_ type: String, id: String, payload: T, expectedOwner: String, expectedGeneration: Int) throws {
        var value = try load()
        try requireScope(value, owner: expectedOwner, generation: expectedGeneration)
        let recordId = type + "\u{001F}" + id
        var records = value.accounts[value.owner] ?? [:]
        let revision = (records[recordId]?.revision ?? 0) + 1
        guard revision <= 9_007_199_254_740_991 else { throw SyncFailure("记录版本超出协议上限") }
        let encoded = try encoder.encode(payload)
        try validatePayload(type: type, payload: encoded)
        records[recordId] = PrivateJournalRecord(entityType: type, entityId: id, payload: encoded,
            revision: revision, clientUpdatedAt: now(), deleted: false, dirty: true,
            sourceDeviceId: try value.session?.deviceId ?? fingerprint())
        value.accounts[value.owner] = records
        try commit(value)
    }
    func deleteRecord(id: String, expectedOwner: String, expectedGeneration: Int) throws {
        var value = try load()
        try requireScope(value, owner: expectedOwner, generation: expectedGeneration)
        guard var record = value.accounts[value.owner]?[id] else { return }
        record.deleted = true
        record.dirty = true
        guard record.revision < 9_007_199_254_740_991 else { throw SyncFailure("记录版本超出协议上限") }
        record.revision += 1
        record.clientUpdatedAt = now()
        record.sourceDeviceId = try value.session?.deviceId ?? fingerprint()
        value.accounts[value.owner]?[id] = record
        try commit(value)
    }
    private func requireScope(_ value: PrivateVault, owner: String, generation: Int) throws {
        guard value.owner == owner, value.generation == generation else {
            throw SyncFailure("记录期间账号状态已改变。本次未写入其他账号，请确认当前账号后重试。")
        }
    }
    private func enterOperation() throws {
        guard !operationBusy else { throw SyncFailure("另一项账号或同步操作正在进行，请稍后重试") }
        operationBusy = true
    }
    private func install(_ session: PrivateSession, importAnonymous: Bool) throws {
        var value = try load()
        guard !session.token.isEmpty, !session.userId.isEmpty, !session.deviceId.isEmpty else { throw SyncFailure("服务器登录响应缺少有效身份信息") }
        let owner = session.serverURL + "|account:" + session.username
        if importAnonymous {
            let anonymous = session.serverURL + "|anonymous"
            let pending = value.accounts[anonymous] ?? [:]
            var target = value.accounts[owner] ?? [:]
            var remaining = pending
            // Never import another account's journal. Same-ID records require resolution after pull.
            for (id, var record) in pending where target[id] == nil {
                record.dirty = true
                record.sourceDeviceId = session.deviceId
                target[id] = record
                remaining.removeValue(forKey: id)
            }
            value.accounts[owner] = target
            // Preserve anonymous collisions rather than silently discarding them.
            value.accounts[anonymous] = remaining
        }
        value.generation += 1
        value.owner = owner
        value.session = session
        try commit(value)
    }
    func register(server rawServer: String, username rawName: String, password: String, importAnonymous: Bool) async throws -> String {
        try enterOperation(); defer { operationBusy = false }
        _ = try load()
        let server = try normalizeServer(rawServer)
        let username = try account(rawName)
        guard password.count >= 8 else { throw SyncFailure("密码至少需要 8 个字符") }
        do {
            _ = try await challenge(username, server: server)
            throw SyncFailure("账号已经存在，请使用登录或恢复功能")
        } catch HealthApiError.httpError(let code, _) where code == 404 {
            // Only a confirmed missing account may enter the registration flow.
        }
        let passwordSalt = try SyncCrypto.random(16)
        let recoverySalt = try SyncCrypto.random(16)
        let phrase = try SyncCrypto.recoveryPhrase()
        let recovery = SyncCrypto.recoveryNormalized(phrase)
        let key = try SyncCrypto.random(32)
        let envelope = try wrapEpoch(key, epoch: 1, username: username, password: password, recovery: recovery,
            passwordSalt: passwordSalt, recoverySalt: recoverySalt)
        // Persist before server registration so a network/disk interruption cannot lose the only recovery phrase.
        try PrivateKeychain.write("pending-registration-" + server + "|" + username, data: Data(phrase.utf8))
        let body: [String: Any] = ["username": username, "passwordSalt": passwordSalt.base64EncodedString(),
            "passwordVerifier": try SyncCrypto.verifier(password, salt: passwordSalt, kind: "password"),
            "recoverySalt": recoverySalt.base64EncodedString(), "recoveryVerifier": try SyncCrypto.verifier(recovery, salt: recoverySalt, kind: "recovery"),
            "keyEnvelope": envelope, "deviceFingerprint": try fingerprint(), "deviceName": "iOS device"]
        let auth = try decoder.decode(SyncAuthResponse.self, from: await request(server: server, path: "/v1/sync/auth/register", method: "POST", body: body))
        try PrivateKeychain.write("registration-" + server + "|" + username, data: Data(phrase.utf8))
        try install(PrivateSession(serverURL: server, username: username, userId: auth.userId, deviceId: auth.deviceId,
            token: auth.token, passwordSalt: passwordSalt.base64EncodedString(), recoverySalt: recoverySalt.base64EncodedString(),
            currentKeyEpoch: 1, epochKeys: ["1": key]), importAnonymous: importAnonymous)
        return phrase
    }
    func pendingRegistrationPhrase(server: String, username: String) throws -> String? {
        let server = try normalizeServer(server)
        let username = try account(username)
        // A stable successful phrase takes precedence over an uncertain network attempt.
        let stable = try PrivateKeychain.read("registration-" + server + "|" + username)
        let pending = try PrivateKeychain.read("pending-registration-" + server + "|" + username)
        return (stable ?? pending).flatMap { String(data: $0, encoding: .utf8) }
    }
    func savedRegistrationPhrase() throws -> String? {
        let value = try load()
        guard let session = value.session else { return nil }
        return try PrivateKeychain.read("registration-" + session.serverURL + "|" + session.username).flatMap { String(data: $0, encoding: .utf8) }
    }
    func login(server rawServer: String, username rawName: String, password: String, recoveryPhrase: String, importAnonymous: Bool) async throws {
        try enterOperation(); defer { operationBusy = false }
        let value = try load()
        let server = try normalizeServer(rawServer)
        let username = try account(rawName)
        guard password.count >= 8 else { throw SyncFailure("密码至少需要 8 个字符") }
        let known = try await challenge(username, server: server)
        let passwordSalt = try SyncCrypto.unbase64(known.passwordSalt)
        var body: [String: Any] = ["username": username,
            "passwordVerifier": try SyncCrypto.verifier(password, salt: passwordSalt, kind: "password"),
            "deviceFingerprint": try fingerprint(), "deviceName": "iOS device"]
        if !recoveryPhrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            body["recoveryVerifier"] = try SyncCrypto.verifier(SyncCrypto.recoveryNormalized(recoveryPhrase), salt: SyncCrypto.unbase64(known.recoverySalt), kind: "recovery")
        }
        let oldSession = value.session
        let matchingToken = oldSession?.serverURL == server && oldSession?.username == username ? oldSession?.token : nil
        let auth = try decoder.decode(SyncAuthResponse.self, from: await request(server: server, path: "/v1/sync/auth/login", method: "POST", body: body, token: matchingToken))
        let wrap = try SyncCrypto.wrappingKey(password, salt: passwordSalt, kind: "password")
        let keys = try unwrapEpochs(auth.keyEnvelopes ?? [], key: wrap, username: username, kind: "password")
        let epoch = auth.currentKeyEpoch ?? known.currentKeyEpoch
        guard keys[String(epoch)] != nil else { throw SyncFailure("当前密钥包缺失，不能启用同步") }
        try install(PrivateSession(serverURL: server, username: username, userId: auth.userId, deviceId: auth.deviceId,
            token: auth.token, passwordSalt: known.passwordSalt, recoverySalt: known.recoverySalt,
            currentKeyEpoch: epoch, epochKeys: keys), importAnonymous: importAnonymous)
    }
    func logout() throws {
        guard !operationBusy else { throw SyncFailure("请等待当前同步或账号操作完成后退出") }
        var value = try load()
        value.generation += 1
        value.session = nil // Journal remains with the previous owner; never becomes anonymous.
        try commit(value)
    }
    func useAnonymous(server rawServer: String) throws {
        guard !operationBusy else { throw SyncFailure("请等待当前账号操作完成") }
        let server = try normalizeServer(rawServer)
        var value = try load()
        value.generation += 1
        value.owner = server + "|anonymous"
        value.session = nil
        try commit(value)
    }
    private func wrapEpoch(_ key: Data, epoch: Int, username: String, password: String, recovery: String,
        passwordSalt: Data, recoverySalt: Data) throws -> [String: Any] {
        let p = try SyncCrypto.seal(key, key: SyncCrypto.wrappingKey(password, salt: passwordSalt, kind: "password"), aad: SyncCrypto.keyAAD(username, epoch: epoch, kind: "password"))
        let r = try SyncCrypto.seal(key, key: SyncCrypto.wrappingKey(recovery, salt: recoverySalt, kind: "recovery"), aad: SyncCrypto.keyAAD(username, epoch: epoch, kind: "recovery"))
        return ["epoch": epoch, "passwordWrappedKey": p.ciphertext, "passwordNonce": p.nonce,
            "recoveryWrappedKey": r.ciphertext, "recoveryNonce": r.nonce]
    }
    private func unwrapEpochs(_ envelopes: [WrappedEpoch], key: Data, username: String, kind: String) throws -> [String: Data] {
        guard !envelopes.isEmpty else { throw SyncFailure("账号密钥包为空") }
        var keys: [String: Data] = [:]
        for envelope in envelopes {
            let raw = try SyncCrypto.open(SyncSealed(ciphertext: envelope.wrappedKey, nonce: envelope.nonce), key: key,
                aad: SyncCrypto.keyAAD(username, epoch: envelope.epoch, kind: kind))
            guard raw.count == 32, envelope.epoch >= 1 else { throw SyncFailure("账号密钥包无效") }
            keys[String(envelope.epoch)] = raw
        }
        return keys
    }
    private func requireSession(_ value: PrivateVault) throws -> PrivateSession {
        guard let session = value.session, value.owner == session.serverURL + "|account:" + session.username else {
            throw SyncFailure("请先登录私密同步账号")
        }
        return session
    }
    private func validatePayload(type: String, payload: Data) throws {
        switch type {
        case "manual_activity":
            let value = try decoder.decode(ManualActivityRequest.self, from: payload)
            guard (1...600).contains(value.durationMinutes), !value.activityType.isEmpty,
                  ["light", "moderate", "vigorous"].contains(value.intensity) else { throw SyncFailure("运动记录内容无效") }
        case "home_meal":
            let value = try decoder.decode(ConfirmedHomeMealRequest.self, from: payload)
            guard ["breakfast", "lunch", "dinner"].contains(value.mealSlot), !value.items.isEmpty, value.items.count <= 40 else { throw SyncFailure("家庭餐记录内容无效") }
            for item in value.items {
                guard !item.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                      item.grams == nil || (item.grams!.isFinite && (0...5000).contains(item.grams!)) else { throw SyncFailure("家庭餐分量无效") }
            }
        case "preference":
            let value = try decoder.decode(EnergyReferenceRequest.self, from: payload)
            guard value.dailyEnergyReferenceKcal == nil || (500...6000).contains(value.dailyEnergyReferenceKcal!) else { throw SyncFailure("参考能量记录无效") }
        case "outside_activity":
            let value = try decoder.decode(OutsideSchoolActivityRequest.self, from: payload)
            guard (0...1440).contains(value.exerciseMinutes) else { throw SyncFailure("健康汇总记录无效") }
        default: break
        }
    }
    private func merge(_ remote: SyncEnvelope, into records: inout [String: PrivateJournalRecord], session: PrivateSession) throws {
        guard (1...2).contains(remote.envelopeVersion), remote.revision >= 1, remote.keyEpoch >= 1,
              remote.aad == SyncCrypto.recordAAD(remote), let source = remote.sourceDeviceId, !source.isEmpty else {
            throw SyncFailure("同步记录版本或元数据校验失败")
        }
        guard let key = session.epochKeys[String(remote.keyEpoch)] else { throw SyncFailure("缺少历史密钥，请重新登录刷新密钥；本页同步游标没有前进") }
        let payload = try SyncCrypto.open(SyncSealed(ciphertext: remote.ciphertext, nonce: remote.nonce), key: key, aad: remote.aad)
        // Validate payload as JSON before advancing cursor, without dropping unknown future entity types.
        _ = try JSONSerialization.jsonObject(with: payload)
        try validatePayload(type: remote.entityType, payload: payload)
        let remoteTime = try parsedTime(remote.clientUpdatedAt)
        let id = remote.entityType + "\u{001F}" + remote.entityId
        if let local = records[id] {
            let localTime = try parsedTime(local.clientUpdatedAt)
            let wins = remote.revision > local.revision ||
                (remote.revision == local.revision && remoteTime > localTime) ||
                (remote.revision == local.revision && remoteTime == localTime && source > local.sourceDeviceId)
            if !wins { return }
        }
        records[id] = PrivateJournalRecord(entityType: remote.entityType, entityId: remote.entityId, payload: payload,
            revision: remote.revision, clientUpdatedAt: remote.clientUpdatedAt, deleted: remote.deleted, dirty: false, sourceDeviceId: source)
    }
    private func pull(session: PrivateSession, owner: String) async throws {
        var more = true
        while more {
            let value = try load()
            let cursor = value.cursors[owner] ?? 0
            let page = try decoder.decode(SyncPullResponse.self, from: await request(server: session.serverURL,
                path: "/v1/sync/pull?cursor=\(cursor)&limit=200", token: session.token))
            guard page.currentKeyEpoch <= session.currentKeyEpoch else { throw SyncFailure("账号已有新密钥，请重新登录；本页同步游标没有前进") }
            guard page.nextCursor >= cursor, !page.hasMore || page.nextCursor > cursor else { throw SyncFailure("服务器同步游标无效") }
            var latest = try load()
            guard latest.owner == owner else { throw SyncFailure("同步账号发生变化") }
            var records = latest.accounts[owner] ?? [:]
            for remote in page.records { try merge(remote, into: &records, session: session) }
            latest.accounts[owner] = records
            latest.cursors[owner] = page.nextCursor
            try commit(latest) // All records and cursor commit atomically.
            more = page.hasMore
        }
    }
    func sync() async throws {
        try enterOperation(); defer { operationBusy = false }
        let initial = try load()
        let session = try requireSession(initial)
        guard !session.rotationRequired else { throw SyncFailure("已吊销设备，请先轮换密钥，再上传新的私密记录") }
        let owner = initial.owner
        guard let key = session.epochKeys[String(session.currentKeyEpoch)] else { throw SyncFailure("当前加密密钥不可用，请重新登录") }
        // Pull first so a remote winner does not get overwritten by a stale local batch.
        try await pull(session: session, owner: owner)
        let dirty = Array((try load().accounts[owner] ?? [:]).values).filter { $0.dirty }
        var offset = 0
        while offset < dirty.count {
            var batch: [PrivateJournalRecord] = []
            var envelopes: [SyncEnvelope] = []
            var encodedBytes = 32
            while offset < dirty.count && batch.count < 200 {
                let record = dirty[offset]
                var envelope = SyncEnvelope(entityType: record.entityType, entityId: record.entityId, ciphertext: "", nonce: "", aad: "",
                    envelopeVersion: 2, keyEpoch: session.currentKeyEpoch, revision: record.revision, deleted: record.deleted,
                    clientUpdatedAt: record.clientUpdatedAt, sourceDeviceId: session.deviceId, serverReceivedAt: nil)
                envelope.aad = SyncCrypto.recordAAD(envelope)
                let sealed = try SyncCrypto.seal(record.payload, key: key, aad: envelope.aad)
                envelope.ciphertext = sealed.ciphertext; envelope.nonce = sealed.nonce
                let wireBytes = try encoder.encode(envelope).count + 1
                guard envelope.ciphertext.utf8.count <= 1_000_000, wireBytes <= 3_800_000 else { throw SyncFailure("单条私密记录超出同步上限，已保留本机待同步状态") }
                if !batch.isEmpty && encodedBytes + wireBytes > 3_800_000 { break }
                batch.append(record); envelopes.append(envelope); encodedBytes += wireBytes; offset += 1
            }
            let json = try JSONSerialization.jsonObject(with: encoder.encode(envelopes))
            let ackData = try await request(server: session.serverURL, path: "/v1/sync/push", method: "POST", body: ["records": json], token: session.token)
            let ack = try decoder.decode(SyncPushAck.self, from: ackData)
            guard ack.ok, ack.acceptedCount == batch.count, ack.currentKeyEpoch == session.currentKeyEpoch else {
                throw SyncFailure("服务器同步确认不完整，本批待同步状态已保留")
            }
            var latest = try load()
            var records = latest.accounts[owner] ?? [:]
            for sent in batch {
                if var current = records[sent.id], current.revision == sent.revision, current.clientUpdatedAt == sent.clientUpdatedAt {
                    current.dirty = false; current.sourceDeviceId = session.deviceId; records[sent.id] = current
                }
            }
            latest.accounts[owner] = records
            try commit(latest)
        }
        try await pull(session: session, owner: owner)
        var latest = try load()
        latest.lastSync[owner] = now()
        try commit(latest)
    }
    func devices() async throws -> [SyncDevice] {
        let session = try requireSession(load())
        return try decoder.decode(SyncDevicesResponse.self, from: await request(server: session.serverURL, path: "/v1/sync/devices", token: session.token)).devices
    }
    func revoke(_ id: String) async throws {
        try enterOperation(); defer { operationBusy = false }
        let session = try requireSession(load())
        guard id != session.deviceId else { throw SyncFailure("请从另一台设备吊销本机") }
        guard id.range(of: "^[A-Za-z0-9_-]{1,200}$", options: .regularExpression) != nil else { throw SyncFailure("设备编号无效") }
        try await write(server: session.serverURL, path: "/v1/sync/devices/\(id)/revoke", body: [:], token: session.token)
        var value = try load()
        value.session?.rotationRequired = true
        try commit(value)
    }
    func rotate(password: String, recoveryPhrase: String) async throws {
        try enterOperation(); defer { operationBusy = false }
        let session = try requireSession(load())
        let known = try await challenge(session.username, server: session.serverURL)
        guard known.currentKeyEpoch == session.currentKeyEpoch else { throw SyncFailure("账号密钥已改变，请先重新登录") }
        let pSalt = try SyncCrypto.unbase64(known.passwordSalt)
        let rSalt = try SyncCrypto.unbase64(known.recoverySalt)
        let recovery = SyncCrypto.recoveryNormalized(recoveryPhrase)
        guard password.count >= 8, !recovery.isEmpty else { throw SyncFailure("轮换需要当前密码和恢复短语") }
        let newEpoch = session.currentKeyEpoch + 1
        let key = try SyncCrypto.random(32)
        var body = try wrapEpoch(key, epoch: newEpoch, username: session.username, password: password, recovery: recovery, passwordSalt: pSalt, recoverySalt: rSalt)
        body["newEpoch"] = newEpoch
        body["passwordVerifier"] = try SyncCrypto.verifier(password, salt: pSalt, kind: "password")
        body["recoveryVerifier"] = try SyncCrypto.verifier(recovery, salt: rSalt, kind: "recovery")
        try await write(server: session.serverURL, path: "/v1/sync/keys/rotate", body: body, token: session.token)
        var value = try load()
        value.session?.epochKeys[String(newEpoch)] = key
        value.session?.currentKeyEpoch = newEpoch
        value.session?.passwordSalt = known.passwordSalt
        value.session?.recoverySalt = known.recoverySalt
        value.session?.rotationRequired = false
        try commit(value)
    }
    private func passwordEnvelopes(_ keys: [String: Data], password: String, salt: Data, username: String) throws -> [[String: Any]] {
        let wrap = try SyncCrypto.wrappingKey(password, salt: salt, kind: "password")
        return try keys.keys.sorted().map { name in
            guard let epoch = Int(name), let key = keys[name] else { throw SyncFailure("历史密钥编号无效") }
            let sealed = try SyncCrypto.seal(key, key: wrap, aad: SyncCrypto.keyAAD(username, epoch: epoch, kind: "password"))
            return ["epoch": epoch, "passwordWrappedKey": sealed.ciphertext, "passwordNonce": sealed.nonce]
        }
    }
    func changePassword(current: String, newPassword: String) async throws {
        try enterOperation(); defer { operationBusy = false }
        let session = try requireSession(load())
        guard current.count >= 8, newPassword.count >= 8 else { throw SyncFailure("密码至少需要 8 个字符") }
        let known = try await challenge(session.username, server: session.serverURL)
        guard known.currentKeyEpoch == session.currentKeyEpoch else { throw SyncFailure("账号有新密钥，请先重新登录刷新历史密钥") }
        let salt = try SyncCrypto.random(16)
        let body: [String: Any] = ["currentPasswordVerifier": try SyncCrypto.verifier(current, salt: SyncCrypto.unbase64(known.passwordSalt), kind: "password"),
            "passwordSalt": salt.base64EncodedString(), "passwordVerifier": try SyncCrypto.verifier(newPassword, salt: salt, kind: "password"),
            "keyEnvelopes": try passwordEnvelopes(session.epochKeys, password: newPassword, salt: salt, username: session.username)]
        try await write(server: session.serverURL, path: "/v1/sync/auth/change-password", body: body, token: session.token)
        var value = try load(); value.session?.passwordSalt = salt.base64EncodedString(); try commit(value)
    }
    func resetPassword(server rawServer: String, username rawName: String, recoveryPhrase: String, newPassword: String) async throws {
        try enterOperation(); defer { operationBusy = false }
        _ = try load()
        let server = try normalizeServer(rawServer)
        let username = try account(rawName)
        let recovery = SyncCrypto.recoveryNormalized(recoveryPhrase)
        guard !recovery.isEmpty, newPassword.count >= 8 else { throw SyncFailure("需要恢复短语和至少 8 个字符的新密码") }
        let known = try await challenge(username, server: server)
        let recoverySalt = try SyncCrypto.unbase64(known.recoverySalt)
        let verifier = try SyncCrypto.verifier(recovery, salt: recoverySalt, kind: "recovery")
        let response = try decoder.decode(SyncRecoveryResponse.self, from: await request(server: server, path: "/v1/sync/auth/recovery", method: "POST", body: ["username": username, "recoveryVerifier": verifier]))
        let keys = try unwrapEpochs(response.keyEnvelopes, key: SyncCrypto.wrappingKey(recovery, salt: recoverySalt, kind: "recovery"), username: username, kind: "recovery")
        let salt = try SyncCrypto.random(16)
        let body: [String: Any] = ["username": username, "recoveryVerifier": verifier,
            "passwordSalt": salt.base64EncodedString(), "passwordVerifier": try SyncCrypto.verifier(newPassword, salt: salt, kind: "password"),
            "keyEnvelopes": try passwordEnvelopes(keys, password: newPassword, salt: salt, username: username)]
        try await write(server: server, path: "/v1/sync/auth/reset-password", body: body)
        // Reset invalidates device sessions. Keep local records, require an explicit new login/enrollment.
        var value = try load()
        if value.session?.serverURL == server && value.session?.username == username { value.generation += 1; value.session = nil }
        try commit(value)
    }
}
