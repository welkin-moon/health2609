import Foundation
import SwiftUI

extension Notification.Name {
    static let privateJournalChanged = Notification.Name("health2609.privateJournalChanged")
}

@MainActor
final class PrivateSyncState: ObservableObject {
    static let shared = PrivateSyncState()
    @Published private(set) var records: [PrivateJournalRecord] = []
    @Published private(set) var serverURL = PrivateSyncRepository.defaultServer
    @Published private(set) var username: String?
    @Published private(set) var loggedIn = false
    @Published private(set) var owner = ""
    @Published private(set) var generation = 0
    @Published private(set) var lastSync: String?
    @Published private(set) var devices: [SyncDevice] = []
    @Published private(set) var busy = false
    @Published private(set) var rotationRequired = false
    @Published var message: String?
    @Published var recoveryNotice: String?
    @Published private(set) var storageReady = false
    let repository = PrivateSyncRepository.shared

    private init() { Task { await reload() } }
    func reload() async {
        do {
            let snapshot = try await repository.snapshot()
            records = snapshot.records
            owner = snapshot.owner
            generation = snapshot.generation
            let scope = snapshot.owner.split(separator: "|", maxSplits: 1).map(String.init)
            serverURL = snapshot.session?.serverURL ?? scope.first ?? PrivateSyncRepository.defaultServer
            username = snapshot.session?.username ?? (scope.count == 2 && scope[1].hasPrefix("account:") ? String(scope[1].dropFirst(8)) : nil)
            loggedIn = snapshot.session != nil
            rotationRequired = snapshot.session?.rotationRequired ?? false
            lastSync = snapshot.lastSync
            if snapshot.session != nil { recoveryNotice = try await repository.savedRegistrationPhrase() }
            storageReady = true
            NotificationCenter.default.post(name: .privateJournalChanged, object: nil)
        } catch {
            storageReady = false
            message = error.localizedDescription
            // Never display a failed read as an empty successfully loaded journal.
        }
    }
    func perform(_ operation: @escaping () async throws -> String) {
        guard !busy else { return }
        busy = true
        message = nil
        Task {
            do { message = try await operation() }
            catch { message = error.localizedDescription }
            await reload()
            busy = false
        }
    }
    func login(server: String, username: String, password: String, recovery: String, importAnonymous: Bool) {
        perform {
            try await self.repository.login(server: server, username: username, password: password, recoveryPhrase: recovery, importAnonymous: importAnonymous)
            return "已登录，私密记录保存在本机加密存储中。点击同步获取跨设备记录。"
        }
    }
    func register(server: String, username: String, password: String, importAnonymous: Bool) {
        perform {
            self.recoveryNotice = try await self.repository.register(server: server, username: username, password: password, importAnonymous: importAnonymous)
            return "账号已创建。请保存恢复短语，丢失后服务器无法找回私密记录。"
        }
    }
    func revealPendingPhrase(server: String, username: String) {
        perform {
            self.recoveryNotice = try await self.repository.pendingRegistrationPhrase(server: server, username: username)
            return self.recoveryNotice == nil ? "此设备没有保存该账号的注册短语" : "已显示本机保存的注册短语。网络未确认的注册尝试需用登录验证。"
        }
    }
    func sync() {
        perform {
            try await self.repository.sync()
            return "私密记录同步完成"
        }
    }
    func logout() {
        perform {
            try await self.repository.logout()
            self.devices = []
            self.recoveryNotice = nil
            return "已退出同步。本机记录仍归原账号所有，重新登录该账号后可继续同步。"
        }
    }
    func useAnonymous(server: String) {
        perform {
            try await self.repository.useAnonymous(server: server)
            self.devices = []
            self.recoveryNotice = nil
            return "已切换到该服务的匿名本机记录。原账号记录仍单独保留。"
        }
    }
    func loadDevices() {
        perform {
            self.devices = try await self.repository.devices()
            return "设备列表已更新"
        }
    }
    func revoke(_ id: String) {
        perform {
            try await self.repository.revoke(id)
            self.devices = try await self.repository.devices()
            return "设备已吊销。请立即用当前密码和恢复短语轮换密钥，保护未来记录。"
        }
    }
    func rotate(password: String, recovery: String) {
        perform {
            try await self.repository.rotate(password: password, recoveryPhrase: recovery)
            return "未来记录加密密钥已轮换；历史记录保留原密钥。其他设备需重新登录。"
        }
    }
    func changePassword(current: String, newPassword: String) {
        perform {
            try await self.repository.changePassword(current: current, newPassword: newPassword)
            return "密码已更新，全部历史密钥已重新包装。"
        }
    }
    func recover(server: String, username: String, phrase: String, newPassword: String) {
        perform {
            try await self.repository.resetPassword(server: server, username: username, recoveryPhrase: phrase, newPassword: newPassword)
            self.devices = []
            return "密码已重置。请使用新密码和恢复短语重新登录，再同步历史记录。"
        }
    }
    func delete(_ id: String) {
        let expectedOwner = owner
        let expectedGeneration = generation
        perform {
            try await self.repository.deleteRecord(id: id, expectedOwner: expectedOwner, expectedGeneration: expectedGeneration)
            return "已在本机删除记录；下次同步会同步删除状态。"
        }
    }
}
