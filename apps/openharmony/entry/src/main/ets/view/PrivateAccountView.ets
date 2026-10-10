import { PrivateRepository, PrivateRecord, PrivateDevice } from '../sync/PrivateRepository';
import { ConfirmedHomeMealRequest, ConfirmedHomeMealItemRequest, ManualActivityRequest, EnergyReferenceRequest, OutsideSchoolActivityRequest } from '../model/ApiModels';
import { M3EColors, M3EShapes } from '../theme/M3ETheme';

@Component
export struct PrivateAccountView {
  @State server: string = 'https://h2609.lunarlab.uk';
  @State username: string = '';
  @State password: string = '';
  @State recovery: string = '';
  @State newPassword: string = '';
  @State devices: PrivateDevice[] = [];
  @State rotationRequired: boolean = false;
  @State recoveryPhrase: string = '';
  @State message: string = '';
  @State busy: boolean = false;
  @State authenticated: boolean = false;
  @State records: PrivateRecord[] = [];
  @State lastSync: string = '';
  private repository: PrivateRepository = PrivateRepository.getInstance();

  aboutToAppear(): void { this.refresh(); }
  private refresh(): void {
    const state = this.repository.snapshot();
    this.server = state.server; this.username = state.username; this.authenticated = state.token.length > 0;
    this.records = this.repository.records().sort((left: PrivateRecord, right: PrivateRecord) => Date.parse(right.clientUpdatedAt) - Date.parse(left.clientUpdatedAt));
    this.rotationRequired = state.rotationRequired; this.lastSync = state.lastSync; this.recoveryPhrase = state.pendingRecovery;
  }
  private async login(): Promise<void> {
    if (this.busy) return; this.busy = true; this.message = '';
    try {
      this.recoveryPhrase = await this.repository.login(this.server, this.username, this.password, this.recovery);
      this.devices = []; this.message = '账号已登录；手动同步只上传加密个人记录'; this.refresh();
    } catch (error) { this.message = (error as Error).message || '登录未能完成，请核对服务地址和账号'; }
    finally { this.password = ''; this.recovery = ''; this.busy = false; }
  }
  private async sync(): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.sync(); this.message = '私密记录同步完成'; this.refresh(); }
    catch (error) { this.message = (error as Error).message || '同步失败，本机记录已保留'; }
    finally { this.busy = false; }
  }
  private async logout(): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.logout(); this.recoveryPhrase = ''; this.devices = []; this.message = '已退出账号，原账号记录仍在本机加密保存'; this.refresh(); }
    catch (error) { this.message = (error as Error).message || '退出失败，记录未删除'; }
    finally { this.busy = false; }
  }
  private async remove(record: PrivateRecord): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.save(record.entityType, record.entityId, record.payloadJson, true); this.message = '已本地删除；手动同步后其它设备更新'; this.refresh(); }
    catch (error) { this.message = (error as Error).message || '删除未保存'; }
    finally { this.busy = false; }
  }
  private async loadDevices(): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { this.devices = await this.repository.devices(); this.message = '设备列表已刷新'; }
    catch (error) { this.message = (error as Error).message || '设备列表加载失败'; }
    finally { this.busy = false; }
  }
  private async revoke(device: PrivateDevice): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.revoke(device.id); this.devices = await this.repository.devices(); this.message = '设备已撤销，请填写当前密码和恢复短语轮换密钥'; }
    catch (error) { this.message = (error as Error).message || '撤销结果未确认，请刷新设备列表，并完成密钥轮换'; }
    finally { this.refresh(); this.busy = false; }
  }
  private async rotateAccountKey(): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.rotate(this.password, this.recovery); this.message = '新密钥已启用，历史记录仍可解密'; }
    catch (error) { this.message = (error as Error).message || '轮换结果未确认，请重新登录刷新密钥'; }
    finally { this.password = ''; this.recovery = ''; this.refresh(); this.busy = false; }
  }
  private async changePassword(): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.changePassword(this.password, this.newPassword); this.message = '密码已修改，历史记录已用新密码保护'; }
    catch (error) { this.message = (error as Error).message || '修改结果未确认，请用新旧密码确认登录'; }
    finally { this.password = ''; this.newPassword = ''; this.refresh(); this.busy = false; }
  }
  private async resetPassword(): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.resetPassword(this.server, this.username, this.recovery, this.newPassword); this.devices = []; this.message = '密码已重设，所有设备已退出；请用新密码和恢复短语重新登录'; }
    catch (error) { this.message = (error as Error).message || '恢复结果未确认，请用新旧密码确认登录'; }
    finally { this.password = ''; this.newPassword = ''; this.recovery = ''; this.refresh(); this.busy = false; }
  }
  private async acknowledgeRecovery(): Promise<void> {
    if (this.busy) return; this.busy = true;
    try { await this.repository.acknowledgeRecovery(); this.refresh(); }
    catch (error) { this.message = '恢复短语仍安全保留，请稍后重试'; }
    finally { this.busy = false; }
  }
  private description(record: PrivateRecord): string {
    if (record.entityType === 'home_meal') {
      const meal = JSON.parse(record.payloadJson) as ConfirmedHomeMealRequest;
      return meal.date + ' · ' + meal.items.map((item: ConfirmedHomeMealItemRequest) => item.name + (item.grams === null || item.grams === undefined ? '（重量未知）' : ' ' + item.grams + '克')).join('、');
    }
    if (record.entityType === 'manual_activity') {
      const activity = JSON.parse(record.payloadJson) as ManualActivityRequest;
      const intensity = activity.intensity === 'vigorous' ? '高强度' : activity.intensity === 'moderate' ? '中等强度' : '轻度';
      return `${activity.date} · ${activity.activityType} ${activity.durationMinutes}分钟 · ${intensity}`;
    }
    if (record.entityType === 'outside_activity') {
      const activity = JSON.parse(record.payloadJson) as OutsideSchoolActivityRequest;
      return `${activity.date} · 设备运动 ${activity.exerciseMinutes} 分钟 · 步数 ${activity.steps ?? '未提供'} · 活动消耗 ${activity.activeEnergyKcal ?? '未提供'} 千卡`;
    }
    if (record.entityType === 'preference') {
      const preference = JSON.parse(record.payloadJson) as EnergyReferenceRequest;
      return preference.dailyEnergyReferenceKcal === null || preference.dailyEnergyReferenceKcal === undefined ? '已清除个人参考热量' : `每日参考热量 ${preference.dailyEnergyReferenceKcal} 千卡`;
    }
    return '来自其它设备的个人记录';
  }
  private label(type: string): string {
    if (type === 'home_meal') return '家庭餐'; if (type === 'manual_activity') return '手动运动';
    if (type === 'outside_activity') return '设备运动';
    if (type === 'preference') return '个人偏好'; if (type === 'school_meal') return '个人午餐记录'; return '个人记录';
  }
  build() {
    Scroll() {
      Column({ space: 16 }) {
        Text('账号与私密记录').fontSize(26).fontWeight(FontWeight.Bold)
        Text(this.authenticated ? `当前账号：${this.username}` : '本地模式：记录先加密保存到本机')
        Text('学校演示统计与个人私密记录分开。服务地址与用户名共同决定记录所属账号；退出账号后可查看本地模式记录。未登录记录不会自动迁入任何账号。')
          .fontSize(13).fontColor(M3EColors.onSurfaceVariant)
        TextInput({ placeholder: 'HTTPS 同步服务地址', text: this.server }).onChange((value: string) => { this.server = value; }).enabled(!this.busy)
        TextInput({ placeholder: '用户名（自动去空格、转小写）', text: this.username }).onChange((value: string) => { this.username = value; }).enabled(!this.busy)
        TextInput({ placeholder: '密码（至少8字符）', text: this.password }).type(InputType.Password).onChange((value: string) => { this.password = value; }).enabled(!this.busy)
        TextInput({ placeholder: '新设备加入账号需要恢复短语', text: this.recovery }).type(InputType.Password).onChange((value: string) => { this.recovery = value; }).enabled(!this.busy)
        Button(this.busy ? '处理中' : '注册 / 登录 / 刷新密钥').onClick(() => { this.login(); }).enabled(!this.busy)
        if (this.recoveryPhrase.length > 0) {
          Text('请立即抄下恢复短语，新设备登录会需要它。').fontColor(M3EColors.error)
          Text(this.recoveryPhrase).fontSize(18).copyOption(CopyOptions.LocalDevice)
          Button('我已保存，隐藏短语').onClick(() => { this.acknowledgeRecovery(); }).enabled(!this.busy)
        }
        if (this.authenticated) {
          Button('手动同步私密记录').onClick(() => { this.sync(); }).enabled(!this.busy)
          Button('退出账号（保留加密记录）').onClick(() => { this.logout(); }).enabled(!this.busy)
        }
        Text('账号安全').fontSize(20).fontWeight(FontWeight.Medium)
        TextInput({ placeholder: '新密码（修改或恢复时填写）', text: this.newPassword }).type(InputType.Password).onChange((value: string) => { this.newPassword = value; }).enabled(!this.busy)
        Button('用恢复短语重设密码并退出全部设备').onClick(() => { this.resetPassword(); }).enabled(!this.busy)
        if (this.authenticated) {
          if (this.rotationRequired) { Text('设备撤销后需轮换密钥；完成前暂停同步新记录。').fontColor(M3EColors.error) }
          Text('修改密码填写上方当前密码与新密码；轮换密钥填写当前密码与恢复短语。').fontSize(12)
          Button('修改密码').onClick(() => { this.changePassword(); }).enabled(!this.busy)
          Button('轮换个人记录密钥').onClick(() => { this.rotateAccountKey(); }).enabled(!this.busy)
          Button('刷新登录设备').onClick(() => { this.loadDevices(); }).enabled(!this.busy)
          ForEach(this.devices, (device: PrivateDevice) => {
            Column({ space: 4 }) {
              Text(device.deviceName + (device.current ? '（本机）' : device.revokedAt ? '（已撤销）' : ''))
              Text('最近在线：' + device.lastSeenAt).fontSize(12)
              if (!device.current && !device.revokedAt) {
                Button('撤销此设备').onClick(() => { this.revoke(device); }).enabled(!this.busy)
              }
            }.alignItems(HorizontalAlign.Start)
          }, (device: PrivateDevice) => device.id)
        }
        if (this.lastSync.length > 0) { Text(`最后同步：${this.lastSync}`).fontSize(12) }
        if (this.message.length > 0) { Text(this.message).fontColor(M3EColors.primary) }
        Text(`历史记录（${this.records.length}）`).fontSize(20).fontWeight(FontWeight.Bold)
        ForEach(this.records, (record: PrivateRecord) => {
          Column({ space: 6 }) {
            Text(this.label(record.entityType)).fontWeight(FontWeight.Medium)
            Text(record.clientUpdatedAt).fontSize(12)
            Text(record.dirty ? '等待手动同步' : '已同步').fontSize(12).fontColor(M3EColors.primary)
            Text(this.description(record)).fontSize(13)
            Button('删除此记录').onClick(() => { this.remove(record); }).enabled(!this.busy)
          }.alignItems(HorizontalAlign.Start).width('100%').padding(16)
            .backgroundColor(M3EColors.surfaceContainer).borderRadius(M3EShapes.sectionRadius)
        }, (record: PrivateRecord) => record.entityType + ':' + record.entityId)
      }.width('100%').padding({ left: 20, right: 20, top: 20, bottom: 112 }).alignItems(HorizontalAlign.Start)
    }.width('100%').height('100%').backgroundColor(M3EColors.surface)
  }
}
