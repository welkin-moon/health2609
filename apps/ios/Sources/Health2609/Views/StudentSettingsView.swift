import SwiftUI

struct StudentSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var schoolId = UserDefaults.standard.string(forKey: "schoolId") ?? "demo-school"
    @State private var participantId = UserDefaults.standard.string(forKey: "participantId") ?? "demo-student"
    let onSave: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("学校与记录身份") {
                    TextField("学校编号", text: $schoolId)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    TextField("学生编号", text: $participantId)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    Text("请填写管理员分配的学校和学生编号。默认值为演示身份；同一身份在多台设备上共享校园记录。")
                        .font(.footnote)
                }
                Section("个人跨设备同步") {
                    NavigationLink("私密账号、加密同步与设备管理") { AccountSyncView() }
                    Text("校园演示身份与私密同步账号相互独立。家庭餐、手动运动和能量偏好先保存到本机加密日记。")
                        .font(.footnote)
                }
                Section("Apple 健康") {
                    Text(HealthKitManager.shared.isAvailable
                         ? "点击今天页面的同步按钮，由系统请求健康读取授权。"
                         : "通用重签安装包可使用手动运动和照片记录。Apple 健康需要具有 HealthKit 权限的签名配置。")
                        .font(.footnote)
                }
            }
            .navigationTitle("设置")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        UserDefaults.standard.set(schoolId.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "schoolId")
                        UserDefaults.standard.set(participantId.trimmingCharacters(in: .whitespacesAndNewlines), forKey: "participantId")
                        onSave()
                        dismiss()
                    }.disabled(schoolId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || participantId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
