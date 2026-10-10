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

新客户端写入 `envelopeVersion = 2`，读取兼容历史版本 1 和 2，拒绝其他版本。版本 2 的记录 AAD 精确包含 envelope version、key epoch、entity type、entity id、revision、原始 clientUpdatedAt 字符串、deleted 布尔值和 sourceDeviceId。AES-GCM 认证密文与这些 AAD 字节；修改任意受认证字段会导致解密失败。

```text
health2609|record|v2|epoch=<N>|type=<type>|id=<id>|revision=<R>|updated=<原始时间字符串>|deleted=true或false|sourceDeviceId=<已认证设备ID>
```

其中删除值只能是小写 `true` 或 `false`；不能把“true或false”作为实际 AAD。后端要求版本 2 请求显式提供 JSON 布尔值 deleted 和非空 sourceDeviceId，并要求 sourceDeviceId 等于 Bearer 所属设备、aad 等于元数据生成的规范字符串，否则拒绝写入。

历史版本 1 的 AAD 和密文保持原样；其 AAD 不含 deleted 与 sourceDeviceId，因此这些历史字段没有 AES-GCM 完整性保护。版本 2 的新增保护不追溯修复版本 1，也不认证 userId、serverReceivedAt。本机 journal 的完整性保护是另一层要求，不能由网络 envelope 代替。后端为旧客户端保留版本 1 写入兼容；未提供 envelopeVersion 的旧请求默认按版本 1 解析。

跨平台字节格式、字段名和迁移边界见 [sync-native-interop.md](sync-native-interop.md)。

## 设备认证

注册或登录成功后，Worker 生成 256 bit 随机 device token。客户端仅保存由本机 Keystore 加密后的 token；D1 只保存 token 的 SHA-256。

所有 push / pull / device / key 管理接口都要求 Bearer device token。旧版可以伪造的 x-sync-user-id 不再作为认证依据。

新设备即使知道账号密码，也必须额外证明持有恢复短语。被吊销设备重新加入同样需要恢复短语。既有设备免恢复短语重新登录时，必须附带该账号、该设备当前有效的 Bearer；fingerprint 只是安装标识，不能代替设备持有证明。登录会替换该设备 token，客户端须安全保存返回的新 token。写入的原子 guard 同时检查凭据、epoch 和原 token，避免认证后被吊销或被其他登录替换的旧 token 继续修改数据。

## 增量同步与冲突

D1 使用单调递增 change_id 作为增量 cursor。客户端只 pull 上次 cursor 之后的变化。

同一 (user, entityType, entityId) 的冲突顺序固定为：revision 大者优先；相同时 clientUpdatedAt 代表的时刻较新者优先；两者仍相同时 sourceDeviceId 字典序较大者优先。新写入统一使用 UTC 毫秒时间戳，保留历史/拉取记录的原始字符串用于 AAD。Android Instant、JavaScript Date 与 SQLite 的亚毫秒精度存在历史差异，不能把旧数据的不同精度视为已验证一致。

push 成功响应的 acceptedCount 是提交条数，不表示每条都赢得冲突。客户端只清除仍等于提交版本的 dirty 标记，随后拉取服务器的获胜版本。pull 页面必须全部校验、解密和持久化后才前进 cursor；缺密钥、认证失败或存储错误时保留原 cursor。页面重放和重复实体应幂等处理。每批最多 200 条，HTTP 请求体最多 4 MiB；批次超限不能清除 dirty。

## 吊销与轮换

吊销设备会立即使它的 token 失效，因此无法继续 pull 新密文。

吊销后当前设备会进入 rotationRequired 状态。用户输入当前密码和恢复短语后生成新的随机账号密钥 epoch。之后的新记录只使用新 epoch；被吊销设备没有新 epoch 的包装密钥，因此无法解密未来数据。

历史记录仍保留原 epoch，这样授权设备仍能读取历史数据，而不需要重新加密全部数据库。

## 密码修改与恢复

普通改密码在已授权设备上完成：本机已经持有历史 epoch key；为新密码生成新 salt / wrapping key；重新包装所有历史 epoch key；Worker 原子替换密码 verifier 和 password-wrapped envelopes。

忘记密码时必须使用恢复短语：恢复短语解开所有 recovery-wrapped epoch key；端侧选择新密码并重新包装全部 epoch；Worker 不需要、也得不到任何明文账号密钥。恢复重置不要求旧密码，并吊销全部既有设备会话；之后需要用新密码和恢复短语重新加入。改密码、恢复重置和轮换在数据库事务内检查原始凭据/epoch 状态，冲突时整批失败而不是写入不一致的密钥包。

如果用户同时丢失所有仍持有账号 epoch key 的受信设备和恢复短语，历史 E2EE 数据不可恢复。这是端到端加密的设计结果，服务器没有后门恢复能力。

## 退出

Android 普通退出停用本机同步并删除本机 token，不主动销毁本机 journal 与账号 epoch key；这不是服务器端设备吊销，服务器中的 token 不会因本地退出操作自动失效。再次加入没有现有 Bearer 的设备时仍需恢复短语。“退出并清除本机账号密钥”会另外删除已解包的账号 epoch key。不要清除 journal 作为错误恢复；用户需保留受信设备或恢复短语以便恢复云端历史密文。

## 威胁模型

保护范围：D1 数据库泄露不能直接得到家庭餐、手动运动、个人偏好等 E2EE 明文；修改 ciphertext、nonce 或受认证 AAD 会被 AES-GCM 拒绝；版本 2 额外认证 deleted/sourceDeviceId，历史版本 1 保留前述元数据完整性限制；伪造 user id 不能读取他人同步数据；被吊销 token 不能继续获取新记录或新 key epoch；单纯取得 Android DataStore 文件不能直接得到 device token 或账号 epoch key。

不保护：已解锁且被完全控制的受信终端可以读取该终端当前可访问的明文；截图、键盘记录器或恶意无障碍服务可能窃取用户主动输入的密码/恢复短语；Worker 在学校可读公共/统计接口中处理的数据不属于 E2EE 私密 journal；流量时间、密文大小、entity type/id、revision 和更新时间属于同步所需最小元数据。

## 版本迁移

同步服务使用独立的 sync_*_v2 D1 表，不复用旧 0012 的不一致 schema。迁移 0013 建表，0014 增加事务状态 guard。表名中的 v2 是服务版本，不等于记录 envelopeVersion；同一表可以保存 envelope 1 和 2，本轮 envelope 2 不需要新增数据库迁移。0012 旧密文没有自动导入、账号也没有自动重命名；原学校前缀用户名须按实际原用户名读取，不能默默改为无前缀的新账号。

旧 Android 版本曾保存 e2ee_passkey_cached；新客户端迁移路径只执行删除，不再写入该字段。

## 验收

本轮问题发现和修复依据为源码推理，没有运行测试，也没有完成原生设备间同步验收。仓库保留 `tests/e2ee_sync.test.mjs` 的 schema、通用 AES-GCM 和源码断言；本轮仅维护其中版本 2 契约相关期望，未执行它们。源码字符串断言不能证明 Worker 鉴权的运行效果、Android Keystore 真实行为、跨平台 Unicode PBKDF2 兼容性或设备吊销后实际密钥访问。

发布验收应区分生产构建/类型检查、协议向量与真实安装同步结果；生产构建通过也不等于后两项已验证。不得依据本轮源码审查声称 iOS/Harmony 与 Android 的实际双向同步已测试通过。
