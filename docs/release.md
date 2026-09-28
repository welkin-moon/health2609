# Android release 与签名

## 构建要求

当前 Android 工具链使用 API 37、AGP 9.1.1、Gradle 9.3.1 和 JDK 17。Material 3 Expressive 来自 Compose alpha BOM，因此 release 构建也需要同一套工具链。

调试 APK：

```bash
gradle -p apps/android assembleDebug
```

未提供签名参数时，release 任务仍可用于检查 release 变体是否能编译，但输出不会作为最终比赛提交包。

## 创建比赛 keystore

只需创建一次，并把 keystore 放在仓库外：

```bash
keytool -genkeypair -v \
  -keystore health2609-release.jks \
  -alias health2609 \
  -keyalg RSA \
  -keysize 3072 \
  -validity 3650
```

不要把 `.jks`、密码或任何密钥提交到 GitHub。

## 签名构建

支持 Gradle property 或同名环境变量：

- `HEALTH2609_KEYSTORE_PATH`
- `HEALTH2609_KEYSTORE_PASSWORD`
- `HEALTH2609_KEY_ALIAS`
- `HEALTH2609_KEY_PASSWORD`

PowerShell 示例：

```powershell
$env:HEALTH2609_KEYSTORE_PATH="C:\keys\health2609-release.jks"
$env:HEALTH2609_KEYSTORE_PASSWORD="..."
$env:HEALTH2609_KEY_ALIAS="health2609"
$env:HEALTH2609_KEY_PASSWORD="..."
gradle -p apps/android assembleRelease -PHEALTH2609_API_BASE_URL=https://h2609.lunarlab.uk/
```

成功后 APK 位于 `apps/android/app/build/outputs/apk/release/`。

## API 地址

比赛 Demo 的默认 Worker 基址已经设置为 `https://h2609.lunarlab.uk/`。仍可通过 `HEALTH2609_API_BASE_URL` 在构建时覆盖；地址必须以 `/` 结尾。

## GitHub Actions

`.github/workflows/release.yml` 只在手动触发或 `v*` tag 时运行，因此普通 push 不会不断产生 APK artifact。

默认 workflow 生成的是 release 变体编译产物。若要让 GitHub Actions 直接签名，先把 keystore 以 base64 secret 方式安全注入 runner，再设置上述四个签名变量；仓库本身不保存密钥。

本比赛 Demo 更推荐在可信本机完成最终签名，然后把 SHA-256 记录进提交材料。
