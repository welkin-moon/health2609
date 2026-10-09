# 一餐一动 Android 测试版构建与提交

## 构建要求

当前 Android 工具链使用 API 37、AGP 9.1.1、Gradle 9.3.1 和 JDK 17。Material 3 Expressive 来自 Compose alpha BOM，因此 debug 和 release 变体都需要同一套工具链。

## 比赛提交包

本次交付使用可安装的 Android debug 签名 APK。若比赛章程另有签名或包格式要求，再按对应要求提供；目前不假定 debug 签名已获得主办方认可。

本地构建：

```bash
gradle -p apps/android assembleDebug
```

输出路径：

```text
apps/android/app/build/outputs/apk/debug/app-debug.apk
```

Debug APK 默认已经使用 Android debug key 签名，可直接安装到演示机。若比赛平台要求记录校验值，可对该文件计算 SHA-256。

## API 地址

比赛 Demo 的默认 Worker 基址已经设置为：

```text
https://h2609.lunarlab.uk/
```

仍可通过 `HEALTH2609_API_BASE_URL` 在构建时覆盖；地址必须以 `/` 结尾。

示例：

```bash
gradle -p apps/android assembleDebug -PHEALTH2609_API_BASE_URL=https://h2609.lunarlab.uk/
```

## GitHub Actions

主 CI 在每次 push 后执行：

```bash
gradle -p apps/android assembleDebug
```

并上传 `health2609-debug-apk` artifact。普通提交不再需要 release 签名流程。

`.github/workflows/release.yml` 负责一餐一动测试版发布：每个测试版必须在 `demo/screenshots/<tag>/` 放入本版演示 PNG，workflow 才会构建 debug APK，并把 PNG 与 APK 一起作为 GitHub prerelease 资产发布。演示图直接作为 Release assets，不再只打进 ZIP。

## 可选：release 签名

只有在比赛或后续分发明确要求 release 签名时，才需要创建 keystore。不要把 `.jks`、密码或任何密钥提交到 GitHub。

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

成功后 release APK 位于 `apps/android/app/build/outputs/apk/release/`。
