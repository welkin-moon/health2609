# 一餐一动 E2EE 多设备同步

## 安全目标

账号同步只把端侧加密后的个人记录写入 D1。Worker 负责认证设备、保存密文、维护增量游标和冲突版本，但不持有任何可直接解密个人记录的账号数据密钥。

默认不同步家庭餐原图。未来如开启跨设备预览，只允许端侧生成最长边约 192 px 的低质量缩略图，再与普通私密记录一样加密；原始照片不进入同步存储。

## 数据边界

### 端到端加密的个人数据

- 家庭餐确认记录
- 手动运动记录
- 个人能量参考等偏好
- 个人跨设备 journal
- 可选微缩略图

Android 先写入本地 journal，本地 journal 由 Android Keystore 中不可导出的 AES-GCM 密钥保护。登录同步账号后，dirty record 再使用账号当前 key epoch 的 AES-256-GCM 密钥加密上传。

### 学校可读数据

课程、菜单、体育课确认结果以及学校统计所需的聚合数据不强制进入 E2EE。它们与个人同步账号分离。客户端不会把 GPS 轨迹、原始 Health Connect 样本或家庭餐原图作为学校统计数据上传。

## 密钥层级

1. 密码只在端侧使用。PBKDF2-HMAC-SHA256，210,000 次迭代；域分离后分别生成认证 verifier 和 password wrapping key。Worker 只保存 verifier 的 SHA-256，不保存密码、PBKDF2 wrapping key 或明文 verifier。
2. 恢复短语注册时由端侧随机生成 160 bit 恢复材料，以分组字符串显示一次；端侧用独立 salt / domain 生成 recovery verifier 与 recovery wrapping key。Worker 同样只保存 recovery verifier 的 SHA-256。新设备注册、恢复密码和密钥轮换需要恢复短语。
3. 每个 key epoch 使用独立随机 256 bit AES 账号数据密钥；同一 epoch 分别用 password wrapping key 和 recovery wrapping key 包装。Worker 只保存包装后的密钥和 nonce。
4. Android 使用 Android Keystore 中不可导出的 AES-256-GCM 本机密钥；device token、已解包的账号 epoch key 和尚未上传的本地 journal 都不会明文落入 DataStore。

## Envelope

当前 envelopeVersion = 1。记录 AAD 包含 envelope version、key epoch、entity type、entity id、revision 和 clientUpdatedAt。AES-GCM 会认证密文和 AAD；任意一项被改写都会导致解密失败。未知 envelope 版本默认拒绝，不进行宽松降级解析。

## 设备认证

注册或登录成功后，Worker 生成 256 bit 随机 device token。客户端仅保存由本机 Keystore 加密后的 token；D1 只保存 token 的 SHA-256。

所有 push / pull / device / key 管理接口都要求 Bearer device token。旧版可以伪造的 x-sync-user-id 不再作为认证依据。

新设备即使知道账号密码，也必须额外证明持有恢复短语。被吊销设备重新加入同样需要恢复短语。

## 增量同步与冲突

D1 使用单调递增 change_id 作为增量 cursor。客户端只 pull 上次 cursor 之后的变化。

同一 (user, entityType, entityId) 的冲突顺序固定为：revision 大者优先；相同时 clientUpdatedAt 较新者优先；两者仍相同时 sourceDeviceId 字典序较大者优先。客户端和 Worker 使用同一顺序，避免两个设备反复覆盖产生振荡。

## 吊销与轮换

吊销设备会立即使它的 token 失效，因此无法继续 pull 新密文。

吊销后当前设备会进入 rotationRequired 状态。用户输入当前密码和恢复短语后生成新的随机账号密钥 epoch。之后的新记录只使用新 epoch；被吊销设备没有新 epoch 的包装密钥，因此无法解密未来数据。

历史记录仍保留原 epoch，这样授权设备仍能读取历史数据，而不需要重新加密全部数据库。

## 密码修改与恢复

普通改密码在已授权设备上完成：本机已经持有历史 epoch key；为新密码生成新 salt / wrapping key；重新包装所有历史 epoch key；Worker 原子替换密码 verifier 和 password-wrapped envelopes。

忘记密码时必须使用恢复短语：恢复短语解开所有 recovery-wrapped epoch key；端侧选择新密码并重新包装全部 epoch；Worker 不需要、也得不到任何明文账号密钥。

如果用户同时丢失所有仍持有账号 epoch key 的受信设备和恢复短语，历史 E2EE 数据不可恢复。这是端到端加密的设计结果，服务器没有后门恢复能力。

## 退出

普通退出只撤销本机在线会话，不主动销毁本机账号密钥，便于重新登录。“退出并清除本机账号密钥”会删除本机 token 和已解包的账号 epoch key。UI 在执行前明确提示：若没有其他受信设备或恢复短语，之后无法恢复云端历史密文。

## 威胁模型

保护范围：D1 数据库泄露不能直接得到家庭餐、手动运动、个人偏好等 E2EE 明文；修改 ciphertext、nonce 或 AAD 会被 AES-GCM 拒绝；伪造 user id 不能读取他人同步数据；被吊销设备无法继续获取新记录或新 key epoch；单纯取得 Android DataStore 文件不能直接得到 device token 或账号 epoch key。

不保护：已解锁且被完全控制的受信终端可以读取该终端当前可访问的明文；截图、键盘记录器或恶意无障碍服务可能窃取用户主动输入的密码/恢复短语；Worker 在学校可读公共/统计接口中处理的数据不属于 E2EE 私密 journal；流量时间、密文大小、entity type/id、revision 和更新时间属于同步所需最小元数据。

## 版本迁移

新协议使用独立的 sync_*_v2 D1 表，不复用旧 0012 的不一致 schema。这样已部署数据库可以安全向前迁移，而无需猜测旧字段含义。

旧 Android 版本曾保存 e2ee_passkey_cached；新客户端迁移路径只执行删除，不再写入该字段。

## 验收

CI 覆盖 register/login/recovery/rotation schema、envelope version fail-closed、AES-GCM 正常解密和篡改拒绝、deterministic conflict ordering、Bearer token 身份认证、Android Keystore 本机密钥保护、密码/通行密钥不落 DataStore，以及设备吊销后的 key rotation 要求。
