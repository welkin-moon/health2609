import Foundation

struct SyncEnvelope: Codable {
    var entityType: String
    var entityId: String
    var ciphertext: String
    var nonce: String
    var aad: String
    var envelopeVersion: Int
    var keyEpoch: Int
    var revision: Int64
    var deleted: Bool
    var clientUpdatedAt: String
    var sourceDeviceId: String?
    var serverReceivedAt: String?
}

struct PrivateJournalRecord: Codable, Identifiable {
    var entityType: String
    var entityId: String
    var payload: Data
    var revision: Int64
    var clientUpdatedAt: String
    var deleted: Bool
    var dirty: Bool
    var sourceDeviceId: String
    var id: String { entityType + "\u{001F}" + entityId }
    func decode<T: Decodable>(_ type: T.Type) throws -> T { try JSONDecoder().decode(type, from: payload) }
}

struct PrivateSession: Codable {
    var serverURL: String
    var username: String
    var userId: String
    var deviceId: String
    var token: String
    var passwordSalt: String
    var recoverySalt: String
    var currentKeyEpoch: Int
    var epochKeys: [String: Data]
    var rotationRequired: Bool = false
}
struct PrivateVault: Codable {
    var version = 1
    var generation = 0
    var owner = "https://h2609.lunarlab.uk|anonymous"
    var session: PrivateSession?
    var accounts: [String: [String: PrivateJournalRecord]] = [:]
    var cursors: [String: Int64] = [:]
    var lastSync: [String: String] = [:]
    private enum CodingKeys: String, CodingKey {
        case version, generation, owner, session, accounts, cursors, lastSync
    }
    init() {}
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decode(Int.self, forKey: .version)
        generation = try c.decodeIfPresent(Int.self, forKey: .generation) ?? 0
        owner = try c.decode(String.self, forKey: .owner)
        session = try c.decodeIfPresent(PrivateSession.self, forKey: .session)
        accounts = try c.decode([String: [String: PrivateJournalRecord]].self, forKey: .accounts)
        cursors = try c.decode([String: Int64].self, forKey: .cursors)
        lastSync = try c.decode([String: String].self, forKey: .lastSync)
    }
}
struct SyncChallenge: Decodable {
    let passwordSalt: String
    let recoverySalt: String
    let currentKeyEpoch: Int
}
struct WrappedEpoch: Decodable {
    let epoch: Int
    let wrappedKey: String
    let nonce: String
}
struct SyncAuthResponse: Decodable {
    let userId: String
    let deviceId: String
    let token: String
    let currentKeyEpoch: Int?
    let keyEnvelopes: [WrappedEpoch]?
}
struct SyncRecoveryResponse: Decodable { let keyEnvelopes: [WrappedEpoch] }
struct SyncPullResponse: Decodable {
    let records: [SyncEnvelope]
    let nextCursor: Int64
    let hasMore: Bool
    let currentKeyEpoch: Int
}
struct SyncDevice: Decodable, Identifiable {
    let id: String
    let deviceName: String
    let lastSeenAt: String?
    let revokedAt: String?
    let current: Bool
}
struct SyncDevicesResponse: Decodable { let devices: [SyncDevice] }
struct PrivateSnapshot {
    let generation: Int
    let owner: String
    let session: PrivateSession?
    let records: [PrivateJournalRecord]
    let lastSync: String?
}

struct SyncWriteAck: Decodable { let ok: Bool }
struct SyncTimeStamp: Comparable {
    let seconds: Int64
    let nanos: Int64
    static func < (left: SyncTimeStamp, right: SyncTimeStamp) -> Bool {
        left.seconds != right.seconds ? left.seconds < right.seconds : left.nanos < right.nanos
    }
}

struct SyncPushAck: Decodable {
    let ok: Bool
    let acceptedCount: Int
    let currentKeyEpoch: Int
}
