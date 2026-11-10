# 考研自习铃

面向小米平板5（Android 13）的原生 Android 平板应用。目前已实现每日16节预置课程、Room 本地存储、当前／下一课程及交接倒计时、横竖屏时间轴、系统浅色／暗色主题，以及课程新增、编辑、启停、确认删除、固定本地铃声试听与音量保存。使用 Kotlin、Jetpack Compose 和 Material 3。

## 工具链

| 项目 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.13.0 |
| Gradle Wrapper | 8.13 |
| Kotlin / Compose 编译器插件 | 2.2.20 |
| Compose BOM | 2025.09.01 |
| Activity Compose | 1.11.0 |
| minSdk / targetSdk / compileSdk | 26 / 33 / 36 |
| Java / Kotlin 字节码目标 | 17 |

Compose UI 1.9.2 与 Material 3 1.4.0 由 BOM 统一管理。compileSdk 36 用于匹配依赖及 AGP 支持范围，不要求设备升级到 Android 16；targetSdk 暂定为 33，发布前需重新评估。

## 开发环境

建议安装完整 JDK 17（包含 javac）。使用 Gradle 8.13 时也可使用完整 JDK 21。当前云端已安装完整 JDK 17 与所需 Android SDK，并成功构建 Debug APK。使用 `source /workspace/work/android-env.sh` 载入当前云端环境；环境重建后需重新准备。

无需安装完整 Android Studio：从 https://developer.android.com/studio#command-tools 获取 Linux Android SDK Command-line Tools，放置到 `$ANDROID_HOME/cmdline-tools/latest/`，将其 `bin` 和 `$ANDROID_HOME/platform-tools` 加入 PATH。设置 JAVA_HOME 指向完整 JDK。

使用 sdkmanager 安装依赖并阅读、接受许可：

```sh
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
sdkmanager --licenses
```

AGP 8.13 默认使用 Build Tools 35.0.0。通过 ANDROID_HOME 指定 SDK，或在本地 `local.properties` 中配置 `sdk.dir=/绝对路径/android-sdk`，该文件不提交。

## 音频资源准备（克隆后必须执行）

本公开源码仓库**不包含 Mixkit 音频文件或 APK**。直接克隆后缺少 `R.raw.class_start`、`R.raw.class_end`，需要先准备音频才能编译；不提供替代或静音占位素材。

先阅读官方 [Sound Effects Free License](https://mixkit.co/license/#sfxFree) 及 [资源来源说明](docs/AUDIO_SOURCES.md)。安装 Python 3.9+、curl 和带 libmp3lame 编码器的 FFmpeg，再在项目根目录运行：

```sh
python3 scripts/fetch_audio.py
```

脚本仅从官方 Mixkit 下载两段指定 WAV，验证固定 SHA-256，然后转码为 `app/src/main/res/raw/class_start.mp3` 和 `class_end.mp3`。哈希不匹配会停止，不替换音效。MP3/WAV 已被 `.gitignore` 排除，不能强制提交。不同 FFmpeg 版本的 MP3 字节可能不同，身份核验以官方 WAV 哈希为准。

手动获取步骤也见 [AUDIO_SOURCES.md](docs/AUDIO_SOURCES.md)。音频获取是开发者本地构建准备，应用运行无需联网。

## 构建与验证命令

环境准备好后，在项目根目录执行：

```sh
./gradlew --version
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Windows 使用 `gradlew.bat`。Wrapper 首次运行从 Gradle 官方分发源下载固定版本，并校验 SHA-256；不依赖全局 Gradle。依赖来自 Google Maven 和 Maven Central。

已执行 `./gradlew :app:assembleDebug :app:testDebugUnitTest`，构建成功，29项时间、管理与音量逻辑测试通过。Room 使用 2.8.3、Lifecycle Compose 使用 2.9.4；数据库首次初始化采用事务与持久化标记，后续启动不重复插入。时间默认 Asia/Shanghai，课程结束时间不包含在当前课程区间内。

当前未配置 GitHub Actions，未实现闹钟、通知、自动定时响铃、假日暂停、单次延长、时区设置界面或学习统计。尚未进行真机 UI 和跨进程数据库重启验证。

进度见 `docs/PROGRESS.md`。

云端网络注意：已通过工作区 Gradle 用户配置设置 JVM 代理和系统 Java 信任库，Wrapper 已从官方分发源下载并校验成功。复用环境脚本即可使用现有配置；不要把代理凭据提交到仓库。

## 课程管理

点击时间轴课程进入编辑，或通过“管理课程”查看所有课程（包括停用课程）。可修改名称、HH:mm起止时间、启用状态；课程固定每天重复。结束早于开始表示跨夜，起止时间不得相同。旧星期字段保留以兼容数据库，旧课程会按每天重复使用，其他字段不变。删除需再次确认。

保存时检查启用课程的时间冲突；冲突会明确提示并拒绝保存，不覆盖其他课程。停用课程允许时间重叠，重新启用时再次校验。相邻课程首尾相接允许。保存与冲突检查在同一个 Room 事务中完成，列表及倒计时随数据库变更更新。

## 固定铃声试听

通过首页“铃声设置”试听 Mixkit 的 Happy bells notification（上课铃）和 Achievement bell（下课铃）。音效打包在 APK 内，无需联网；再次点击会停止并释放上一段，不叠加播放。离开设置页或应用进入后台会停止试听。

滑块控制0%–100%的本地播放器音量，默认70%，使用 SharedPreferences 持久化；系统媒体音量也会影响听感。没有自动定时响铃。

素材来源、校验值与许可限制见 `docs/AUDIO_SOURCES.md`。允许按 End Product 用途嵌入应用；不得单独或随源码再分发音频，本源码仓库已排除音频文件，请按上述步骤从官方获取。
