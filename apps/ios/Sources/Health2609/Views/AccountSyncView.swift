import SwiftUI

struct AccountSyncView: View {
    @ObservedObject private var sync = PrivateSyncState.shared
    @State private var server = PrivateSyncRepository.defaultServer
    @State private var username = ""
    @State private var password = ""
    @State private var recovery = ""
    @State private var newPassword = ""
    @State private var importAnonymous = false
    @State private var revokeCandidate: SyncDevice?
    @State private var showRotateConfirmation = false

    var body: some View {
        Form {
            Section("私密同步状态") {
                Text(sync.loggedIn ? "已登录：\(sync.username ?? "")" : "当前为本机记录模式")
                if let time = sync.lastSync { Text("上次同步：\(time)").font(.footnote) }
                Text("家庭餐、手动运动与能量偏好先保存在本机加密日记。服务器只收到密文；学校编号用于校园菜单，不是私密账号。")
                    .font(.footnote)
                if !sync.owner.isEmpty && !sync.loggedIn {
                    Text("本机记录归属：\(sync.owner)").font(.caption)
                }
                if let message = sync.message { Text(message).font(.footnote).foregroundStyle(M3E.Colors.primary) }
                if !sync.storageReady { Text("本机加密存储尚未就绪，请根据提示重试。").foregroundStyle(M3E.Colors.error) }
            }
            if let phrase = sync.recoveryNotice {
                Section("保存恢复短语") {
                    Text(phrase).font(.system(.body, design: .monospaced)).textSelection(.enabled)
                    Text("请另存到安全位置。新设备加入、忘记密码和吊销后轮换密钥都需要它。此设备暂时保存该注册短语，卸载或更换设备不能替代你的备份。")
                        .font(.footnote)
                }
            }
            Section(sync.loggedIn ? "账号与密钥" : "登录或创建账号") {
                TextField("HTTPS 服务地址", text: $server).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                TextField("用户名", text: $username).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("当前密码，至少 8 个字符", text: $password)
                SecureField("恢复短语：新设备/恢复/轮换需要", text: $recovery)
                Toggle("将当前服务的匿名记录迁入此账号", isOn: $importAnonymous)
                Text("不会迁入其他账号的记录。相同日期餐次发生冲突时按共享版本顺序合并，匿名冲突记录会保留。")
                    .font(.caption)
                Button("登录 / 刷新历史密钥") {
                    sync.login(server: server, username: username, password: password, recovery: recovery, importAnonymous: importAnonymous)
                }
                if !sync.loggedIn {
                    Button("创建新账号") { sync.register(server: server, username: username, password: password, importAnonymous: importAnonymous) }
                    Button("查看本机保存的注册短语") { sync.revealPendingPhrase(server: server, username: username) }
                    Button("切换到此服务的匿名本机记录") { sync.useAnonymous(server: server) }
                }
            }
            if sync.loggedIn {
                Section("同步与设备") {
                    Button("立即同步私密日记") { sync.sync() }
                    Button("查看已授权设备") { sync.loadDevices() }
                    ForEach(sync.devices) { device in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(device.deviceName + (device.current ? "（本机）" : ""))
                                Text(device.revokedAt == nil ? "已授权" : "已吊销").font(.caption)
                            }
                            Spacer()
                            if !device.current && device.revokedAt == nil {
                                Button("吊销", role: .destructive) { revokeCandidate = device }
                            }
                        }
                    }
                    if sync.rotationRequired { Text("已吊销设备：上传前需要轮换密钥。").foregroundStyle(M3E.Colors.error) }
                    Button("轮换未来记录密钥") { showRotateConfirmation = true }
                    Button("退出同步账号") { sync.logout() }
                }
            }
            Section("密码修改与恢复") {
                SecureField("新密码，至少 8 个字符", text: $newPassword)
                if sync.loggedIn {
                    Button("使用当前密码修改密码") { sync.changePassword(current: password, newPassword: newPassword) }
                }
                Button("使用恢复短语重置密码") { sync.recover(server: server, username: username, phrase: recovery, newPassword: newPassword) }
                Text("恢复会重新包装所有历史密钥并使旧设备会话失效。完成后重新登录。原始餐食照片不在同步日记内。")
                    .font(.footnote)
            }
        }
        .disabled(sync.busy)
        .overlay { if sync.busy { ProgressView("正在处理加密账号…").padding().background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18)) } }
        .navigationTitle("私密账号与同步")
        .task { await sync.reload(); username = sync.username ?? ""; server = sync.serverURL }
        .onChange(of: sync.busy) { old, new in
            if old && !new { password = ""; newPassword = ""; recovery = "" }
        }
        .confirmationDialog("吊销此设备？它将无法继续同步，随后须轮换密钥。", isPresented: Binding(get: { revokeCandidate != nil }, set: { if !$0 { revokeCandidate = nil } })) {
            if let device = revokeCandidate { Button("吊销设备", role: .destructive) { sync.revoke(device.id); revokeCandidate = nil } }
        }
        .confirmationDialog("轮换后其他设备需要重新登录获取新密钥。", isPresented: $showRotateConfirmation) {
            Button("确认轮换") { sync.rotate(password: password, recovery: recovery) }
        }
    }
}

struct PrivateHistoryView: View {
    @ObservedObject private var sync = PrivateSyncState.shared
    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("这里显示本机与跨设备私密日记，校园演示数据不在其中。")
                        .font(.footnote)
                    if let message = sync.message { Text(message).font(.footnote) }
                    if sync.loggedIn { Button("同步最新记录") { sync.sync() } }
                }
                if sync.records.isEmpty && sync.storageReady {
                    Text("尚无私密记录。今天页面的家庭餐和手动运动会先保存到这里。")
                }
                ForEach(sync.records) { record in
                    VStack(alignment: .leading, spacing: 5) {
                        Text(title(record)).font(.headline)
                        Text(details(record)).font(.subheadline)
                        Text(record.clientUpdatedAt + (record.dirty ? " · 待同步" : " · 已同步"))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    .swipeActions {
                        Button("删除", role: .destructive) { sync.delete(record.id) }
                    }
                }
            }
            .navigationTitle("私密历史")
            .task { await sync.reload() }
            .padding(.bottom, 85)
        }
    }
    private func title(_ record: PrivateJournalRecord) -> String {
        switch record.entityType {
        case "home_meal": return "家庭餐 · " + record.entityId
        case "manual_activity": return "手动运动 · " + String(record.entityId.prefix(10))
        case "preference": return "每日能量参考"
        case "outside_activity": return "Apple 健康 · " + record.entityId
        default: return record.entityType + " · " + record.entityId
        }
    }
    private func details(_ record: PrivateJournalRecord) -> String {
        if record.entityType == "manual_activity", let activity = try? record.decode(ManualActivityRequest.self) {
            return "\(activity.activityType) · \(activity.durationMinutes) 分钟 · \(activity.intensity)"
        }
        if record.entityType == "home_meal", let meal = try? record.decode(ConfirmedHomeMealRequest.self) {
            return meal.items.map(\.name).joined(separator: "、")
        }
        if record.entityType == "preference", let value = try? record.decode(EnergyReferenceRequest.self) {
            return value.dailyEnergyReferenceKcal.map { "\($0) kcal" } ?? "已清除参考值"
        }
        if record.entityType == "outside_activity", let value = try? record.decode(OutsideSchoolActivityRequest.self) {
            return "\(value.exerciseMinutes) 分钟 · \(value.steps ?? 0) 步"
        }
        return "已保存加密记录（此版本暂无该类型的详情显示）"
    }
}
