import Foundation
import CryptoKit
import CommonCrypto
import Security

struct SyncFailure: LocalizedError {
    let message: String
    var errorDescription: String? { message }
    init(_ message: String) { self.message = message }
}

struct SyncSealed: Codable {
    let ciphertext: String
    let nonce: String
}

enum SyncCrypto {
    static func random(_ size: Int) throws -> Data {
        var bytes = [UInt8](repeating: 0, count: size)
        guard SecRandomCopyBytes(kSecRandomDefault, size, &bytes) == errSecSuccess else {
            throw SyncFailure("设备安全随机数不可用")
        }
        return Data(bytes)
    }

    static func unbase64(_ value: String) throws -> Data {
        guard let data = Data(base64Encoded: value) else { throw SyncFailure("加密数据格式无效") }
        return data
    }

    static func derive(_ secret: String, salt: Data, purpose: String) throws -> Data {
        guard !secret.isEmpty else { throw SyncFailure("密码或恢复短语为空") }
        var salted = salt
        salted.append(0)
        salted.append(Data("health2609:\(purpose):v1".utf8))
        let purposeSalt = Data(SHA256.hash(data: salted))
        let password = Array(secret.utf8)
        var output = [UInt8](repeating: 0, count: 32)
        let status = password.withUnsafeBytes { passwordBytes in
            purposeSalt.withUnsafeBytes { saltBytes in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                    passwordBytes.baseAddress!.assumingMemoryBound(to: Int8.self), password.count,
                    saltBytes.baseAddress!.assumingMemoryBound(to: UInt8.self), purposeSalt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 210_000, &output, 32)
            }
        }
        guard status == kCCSuccess else { throw SyncFailure("密钥派生失败") }
        return Data(output)
    }

    static func verifier(_ secret: String, salt: Data, kind: String) throws -> String {
        try derive(secret, salt: salt, purpose: "\(kind)-auth").base64EncodedString()
    }
    static func wrappingKey(_ secret: String, salt: Data, kind: String) throws -> Data {
        try derive(secret, salt: salt, purpose: "\(kind)-wrap")
    }
    static func recoveryNormalized(_ phrase: String) -> String {
        var normalized = ""
        for scalar in phrase.uppercased().unicodeScalars where CharacterSet.alphanumerics.contains(scalar) {
            normalized.unicodeScalars.append(scalar)
        }
        return normalized
    }
    static func recoveryPhrase() throws -> String {
        let hex = try random(20).map { String(format: "%02X", $0) }.joined()
        return stride(from: 0, to: hex.count, by: 5).map { offset in
            let start = hex.index(hex.startIndex, offsetBy: offset)
            let end = hex.index(start, offsetBy: 5)
            return String(hex[start..<end])
        }.joined(separator: "-")
    }
    static func keyAAD(_ username: String, epoch: Int, kind: String) -> String {
        "health2609|key|v1|\(username)|\(epoch)|\(kind)"
    }
    static func recordAAD(_ record: SyncEnvelope) -> String {
        let base = "health2609|record|v\(record.envelopeVersion)|epoch=\(record.keyEpoch)|type=\(record.entityType)|id=\(record.entityId)|revision=\(record.revision)|updated=\(record.clientUpdatedAt)"
        if record.envelopeVersion == 2 {
            return base + "|deleted=\(record.deleted ? "true" : "false")|sourceDeviceId=\(record.sourceDeviceId ?? "")"
        }
        return base
    }
    static func seal(_ plaintext: Data, key: Data, aad: String) throws -> SyncSealed {
        guard key.count == 32 else { throw SyncFailure("密钥长度无效") }
        let nonce = try AES.GCM.Nonce(data: random(12))
        let box = try AES.GCM.seal(plaintext, using: SymmetricKey(data: key), nonce: nonce, authenticating: Data(aad.utf8))
        var ciphertext = box.ciphertext
        ciphertext.append(box.tag) // Java/Android doFinal is ciphertext followed by the 16-byte tag.
        return SyncSealed(ciphertext: ciphertext.base64EncodedString(), nonce: nonce.withUnsafeBytes { Data($0) }.base64EncodedString())
    }
    static func open(_ sealed: SyncSealed, key: Data, aad: String) throws -> Data {
        let ciphertext = try unbase64(sealed.ciphertext)
        let iv = try unbase64(sealed.nonce)
        guard key.count == 32, iv.count == 12, ciphertext.count >= 16 else { throw SyncFailure("密文长度无效") }
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: ciphertext.dropLast(16), tag: ciphertext.suffix(16))
        return try AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: Data(aad.utf8))
    }
}

enum PrivateKeychain {
    private static let service = "uk.lunarlab.health2609.private-v1"
    static func read(_ name: String) throws -> Data? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: name,
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else {
            throw SyncFailure("本机密钥暂时无法读取，请解锁设备后重试；不要清除应用数据")
        }
        return data
    }
    static func write(_ name: String, data: Data) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: name]
        let attributes: [String: Any] = [kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]
        let updated = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updated == errSecItemNotFound {
            var added = query
            attributes.forEach { added[$0.key] = $0.value }
            guard SecItemAdd(added as CFDictionary, nil) == errSecSuccess else { throw SyncFailure("无法安全保存本机密钥") }
        } else if updated != errSecSuccess { throw SyncFailure("无法更新本机密钥") }
    }
}
