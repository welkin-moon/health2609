# 一餐一动：HarmonyOS 客户端

ArkTS / ArkUI Stage 工程，最低 API 12。使用 HarmonyOS SDK 单独构建；商用 HarmonyOS 6 设备使用这个工程，不能拿 OpenHarmony 的 HAP 替代。

支持午餐、手动运动、家庭餐照片识别、今日汇总和隐私说明。照片通过系统照片选择器选择 JPEG/PNG（最大 8 MB），真实 multipart 上传到 API；无网络时只显示已缓存数据，不生成菜单、活动或 AI 结果。手动运动和家庭餐提交超时会保留输入并提示先刷新汇总，不自动重发产生重复记录。

## 构建 HAP

安装 [Huawei Command Line Tools](https://developer.huawei.com/consumer/en/doc/harmonyos-guides/ide-commandline-get)，其中包含 SDK、ohpm 和 hvigorw；也可以使用 DevEco Studio 已安装的相应工具。

从仓库根目录运行：

```sh
export HARMONY_COMMANDLINE_TOOLS=/absolute/path/to/command-line-tools
node tools/build-hap.mjs --platform harmony --version-name 1.0.1 --version-code 1000001
```

默认 API 12；使用更新 SDK 时设置 `HAP_COMPILE_SDK_VERSION` 为已安装的 HarmonyOS SDK 版本字符串，例如 `6.0.0(20)`，兼容 API 仍为 12。如工具目录不符合上述布局，设置 `HVIGOR_EXECUTABLE` 和 `OHPM_EXECUTABLE` 为真实执行文件路径。SDK 可通过 `HOS_SDK_HOME` 提供。若使用不同 Hvigor 主版本，`HVIGOR_PLUGIN_VERSION` 应与安装的插件版本一致。

脚本会执行真实 `ohpm install` 和 release `assembleHap`，输出至 `dist/harmony`；缺少 SDK/构建工具或编译失败会停止，不会用源码压缩包冒充 HAP。

## 在自己的市售机安装

未签名 HAP 不能直接安装。需要在 Huawei 开发者账户中为 `uk.lunarlab.health2609.harmony` 建立调试签名，签发包含目标设备 UDID 的 profile，并取得证书与 keystore。DevEco Studio 的自动签名也可以完成这一流程。设备开启开发者模式和 USB 调试。

设置以下环境变量后加 `--signed`：

- `HAP_CERT_PATH`：证书文件的绝对路径。
- `HAP_PROFILE_PATH`：包含自己设备 UDID、匹配 bundleName 的 profile。
- `HAP_KEYSTORE_PATH`、`HAP_KEY_ALIAS`、`HAP_KEY_PASSWORD`、`HAP_STORE_PASSWORD`：签名密钥配置。

```sh
node tools/build-hap.mjs --platform harmony --signed --version-name 1.0.1 --version-code 1000001
hdc list targets
hdc install dist/harmony/health2609-harmony-1.0.1-signed.hap
hdc shell aa start -a EntryAbility -b uk.lunarlab.health2609.harmony
```

签名信息仅写入临时构建目录，结束后清除。不要把证书、密钥、profile 或密码提交到仓库。签名成功不代表任意市售机均可安装：设备必须被 profile 授权。

## 尚未接入的设备能力

系统照片选择器已接入，直接相机入口尚未实现。MotionService 只统计运行期间实际观察到的校外计步增量，不把步数换算成运动分钟或消耗热量，不提供完整历史运动数据。当前客户端身份仍沿用 API 的 demo 请求头；个人账号、学校选择和跨端同步身份流程需后续接入，不能把 demo 身份当作真实用户身份隔离。
