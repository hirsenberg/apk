# 开发进度

## 2026-11-10：第四阶段源码交付准备

- 目标为现有公开仓库 `https://github.com/hirsenberg/apk`；交付前核实仓库为空、无文件和分支，当前账号具有 push 权限。
- 排除全部MP3/WAV、APK、构建缓存、本机SDK配置和签名密钥；只交付源码、Gradle Wrapper、Room schema、测试与文档。
- 新增 `scripts/fetch_audio.py`，仅由开发者本地运行，从官方获取937／600 WAV、校验固定SHA-256，再转码为所需MP3；无替代音效。
- README明确克隆后需先获取音频才能构建；AUDIO_SOURCES文档补充自动与手动步骤及源码分发限制。
- 第四阶段的29项测试及Debug构建结果保持有效；本次只处理源码交付与资源获取说明，不开发第五阶段功能。
- 下面历史记录中“已打包音频”等描述指本地验证用APK，不代表音频已包含在公开源码中。

---

## 2026-11-10：固定本地铃声试听、音量及每天重复课程

已实现：

- 官方 Mixkit 分类页面精确匹配 Happy bells notification（937）和 Achievement bell（600），由各自下载入口取得 WAV 原文件，再用 FFmpeg 192 kbps 转码为 `res/raw/class_start.mp3` 与 `class_end.mp3`。未裁剪／替换内容，文件分别为74,021和59,025字节，约3.06秒和2.43秒。
- 核对 Sound Effects Free License，按将素材融入更大范围 End Product 的许可用于本应用。许可不允许独立素材或随源码再分发；将来发布源码需排除音频并指引从官方获取。来源、原文件及转码校验值详见 `docs/AUDIO_SOURCES.md`；许可正文快照在 `docs/licenses/MIXKIT_SOUND_EFFECTS_FREE_LICENSE.txt`。
- 首页“铃声设置”进入独立简洁设置区域，提供“试听上课铃”“试听下课铃”“停止试听”和0%–100%音量滑块（整数百分比，默认70%），显示当前百分比及播放状态。
- 音量通过应用私有 SharedPreferences 保存；设置页重新打开、旋转或应用正常重启时从保存值恢复；不改 Room 表结构。音量控制播放器相对增益，实际响度还受系统媒体音量影响，0%静音。
- Android MediaPlayer 使用本地 raw 文件描述符异步准备，单实例播放；连续点击先释放旧播放器。完成、错误、音频焦点丢失、离开页面、Activity停止时释放资源并放弃音频焦点。无后台播放或网络访问。
- 移除星期选择器及相关用户提示，课程固定每天重复。保留旧 weekdays 列，初始化事务将旧值更新为127，新增／编辑也固定写127；时间计算和冲突检查不再依赖旧星期值。其他课程字段及预置初始化标记保持原样，不删课程，不进行 schema 迁移。
- 若原来不同星期的课程改为每天后发生重叠，管理页明确提示调整或停用，保留全部原课程；保存时仍拒绝新增冲突。

文件变化：

- 新增 `audio/VolumeSettings.kt`、`audio/BellPreviewPlayer.kt`、`ui/SoundSettingsScreen.kt`、两段 raw MP3、`VolumeSettingsTest.kt`、音频来源和许可文档。
- 修改 Course 模型注释、StudyDatabase、CourseManager、ScheduleCalculator、CourseEditorState、ScheduleViewModel、CourseEditor、ScheduleScreen、原有两份测试文件、README 和本进度文档。
- 没有增加依赖或改变工具链版本，没有改变 Room 数据库版本。

验证：

```sh
source /workspace/work/android-env.sh
cd /workspace/kaoyan-studybell
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain
```

- 最终 `BUILD SUCCESSFUL in 30s`，48任务，11执行、37 up-to-date。
- 保留原25项测试方法，将已取消的星期专属行为调整为旧数据按每天重复、每天检查冲突及编辑固定写127；新增4项音量设置测试（恢复值、静音／最大值、边界限制、不重复写入）。合计29项，失败0、错误0。
- 确认APK中两段MP3与项目文件逐字节一致且未压缩，可用 raw 文件描述符离线访问；音频由 ffprobe 验证编码及有效时长。
- APK：`/workspace/kaoyan-studybell/app/build/outputs/apk/debug/app-debug.apk`，13,367,994字节；日志：`/workspace/work/sound-build-final.log`。
- 首轮构建测试通过后，修正遗留的重复日期错误提示、补充旧数据重叠提示及滑块无障碍标签，再执行上述最终验证。
- 尚未真机验证实际听感、系统音频焦点、连续点击、进后台／旋转资源释放或 SharedPreferences 跨进程重启；音量单元测试验证的是同一设置逻辑及存储接口，非 Android 设备文件系统。代码路径使用标准 SharedPreferences 持久化。
- 原有统计目录及 kapt 测试参数的非阻断提示仍存在。

未实现 AlarmManager、自动定时响铃、后台通知、一分钟测试闹钟、学习统计、假日暂停、网络或账号功能。

---

## 2026-11-10：课程编辑与管理

已实现：

- 点击当天课程卡片进入编辑界面；新增“新增课程”“管理课程”入口，管理列表包含所有课程（含停用及非当天重复课程），支持空列表。
- 编辑名称、HH:mm 起止时间、启用状态、周一至周日重复日期；新增默认每天重复，至少选择一天。支持跨夜，结束早于开始时按次日解释，星期按开始日计算。
- 删除前展示课程名称和不可撤销提示，需再次点击“确认删除”；取消不写入数据库。保存／删除期间禁止重复提交。
- 编辑草稿保存在 ViewModel，旋转时保留；编辑面板居中、限制宽度、内容滚动并避让输入法，使用现有 Material 3 日夜主题。
- DAO 新增显式 insert / update / delete，保留已有行 ID；Room Flow 触发管理列表、时间轴、当前／下一课程及倒计时刷新。
- 校验与写入在同一 Room 事务中完成。启用课程按一周循环检查重叠，包括跨夜及周日到周一；首尾相接不算冲突。冲突会显示对方课程名并拒绝保存，不覆盖任何其他课程。
- 停用课程可保留与其他课程相同的时间；重新启用时必须通过冲突校验。校验还包括空名称、无效时间、相同起止时间及未选星期。
- 未改变数据库表结构／版本，未改预置课程播种事务与持久化标记；删除全部课程也不会重新插入预置数据。

文件变化：

- 新增 `data/CourseManager.kt`（可测试的管理规则及存储接口）、`schedule/CourseEditorState.kt`、`ui/CourseEditor.kt`、`app/src/test/java/com/kaoyan/studybell/schedule/CourseManagementTest.kt`。
- 修改 `data/StudyDatabase.kt`、`schedule/ScheduleViewModel.kt`、`ui/ScheduleScreen.kt`、`README.md` 和本进度文档。
- 工具链及所有依赖版本保持不变；无新依赖。

验证：

```sh
source /workspace/work/android-env.sh
cd /workspace/kaoyan-studybell
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain
```

- `BUILD SUCCESSFUL in 38s`，48 个任务，15 执行、33 up-to-date。
- 共25项 JVM 单元测试全部通过：原时间计算9项，加课程管理16项；覆盖新增、编辑、删除、启停、星期重复、输入校验、编辑已删除记录、拒绝冲突后数据不变、周末跨夜、端点相接，以及变更后当前／下一课程与倒计时。
- 新测试通过内存 CourseStore 验证实际 CourseManager 规则；Room DAO 实现及编辑 UI 已通过编译。尚未用 Android 真机／模拟器验证编辑交互、删除确认、旋转及数据库跨进程重启持久化。
- APK：`/workspace/kaoyan-studybell/app/build/outputs/apk/debug/app-debug.apk`，13,367,858 字节。日志：`/workspace/work/course-management-build.log`。
- 原有非阻断的统计目录与 kapt 测试参数提示仍存在。

不包含铃声、通知、AlarmManager、假日暂停、单次延长、学习统计及 GitHub Actions。

---

## 2026-11-10：课程表界面与 Room 基础课程数据

已实现：

- 独立 Course 数据模型，保存课程名称、起止分钟、启用状态、星期位掩码（周一到周日）、排序和稳定 ID，支持将来跨夜课程。
- Room 2.8.3 数据库、DAO 和 Repository；16 节预置课程与用户提供的时间、名称一致。初始化插入与完成标记在同一事务提交；后续读取持久化标记，不按课程数量重新播种，插入还采用主键冲突忽略。未来删除全部课程也不会再次初始化。
- 数据库设置表保存默认时区 Asia/Shanghai，为未来时区设置预留字段；当前没有设置或编辑界面。
- Material 3 中文课程时间轴，当前课程边框、背景和“进行中”文字高亮，显示所有当天启用课程；列表可滚动。
- 顶部摘要显示当前日期／时间、当前科目、下一课程及日期、下次课程开始或结束的交接倒计时；课间空档和日程外显示“自由安排”。
- 宽横屏为摘要／时间轴双栏，竖屏及窄窗口为单栏可滚动布局，支持字体随系统缩放、系统浅色／暗色及安全区域。
- Room Flow 驱动界面，使用生命周期感知订阅与每秒时钟刷新，后台不持续刷新界面。

构建与测试：

```sh
source /workspace/work/android-env.sh
cd /workspace/kaoyan-studybell
./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain
```

- 实际结果：`BUILD SUCCESSFUL in 1m 19s`，48 个任务，27 执行、21 up-to-date。`:app` 是模块路径，因此使用明确的 APK 构建任务。
- JVM 单元测试 9 项，失败 0、错误 0：预置完整性与连续性、08:00 前、全部16个课程起点、23:00 后下一天课程、午夜、跨夜课程沿用起始星期、按周查找下一课程、全部停用／空表、空档及指定时区。
- 当前课程区间为 `[开始, 结束)`，交接瞬间切换；末课结束后倒计时至次日08:00，午夜更新当天时间轴。下一课程查找覆盖未来7天，跨夜检查前一天开始的课程。
- Room 注解处理、SQL 和生成 DAO 编译成功，导出 `app/schemas/com.kaoyan.studybell.data.StudyDatabase/1.json`，供将来迁移使用；未使用破坏性迁移。
- APK：`/workspace/kaoyan-studybell/app/build/outputs/apk/debug/app-debug.apk`，13,367,858 字节。构建日志在 `/workspace/work/schedule-build.log`。

文件变化：

- 新增 data/Course.kt、PresetCourses.kt、StudyDatabase.kt；schedule/ScheduleCalculator.kt、ScheduleViewModel.kt；ui/ScheduleScreen.kt。
- 新增 ScheduleCalculatorTest.kt 与 Room schema 1.json。
- 修改 MainActivity.kt、根与 app 的 build.gradle.kts、README.md、本进度文档。
- 保留所有现有依赖版本；仅增加所需 Room 2.8.3、与 Kotlin 同版本的 kapt 2.2.20、Lifecycle Compose 2.9.4，以及测试用 JUnit 4.13.2。未更改 SDK 或 Gradle Wrapper。

验证限制与剩余工作：

- 未在真机／模拟器执行 UI、旋转、首次启动及跨进程数据库持久化测试；初始化幂等性目前依据事务、持久化标记与主键冲突保护进行代码检查。
- 单元测试 kapt 任务提示未使用的 schema 参数，属于测试源码无 Room 注解的非阻断提示；原 Android 统计目录提示仍存在。
- 不包含铃声、AlarmManager、通知、编辑界面、学习统计、时区设置界面或 GitHub Actions。

---

## 2026-11-10：首次实际构建验证成功

- 执行 `./gradlew :app:assembleDebug --console=plain`，退出码 0，输出 `BUILD SUCCESSFUL in 2m 59s`，37 个任务执行。`:app` 仅为模块路径，本次明确运行生成 Debug APK 的任务。
- APK：`/workspace/kaoyan-studybell/app/build/outputs/apk/debug/app-debug.apk`，大小 **10,296,100 字节**（约 9.82 MiB）。
- SHA-256：`6001e08fee0aa8d8156c41853f3b4515cc3a642718407d72bdadc2cd1d765e68`。
- 使用 SDK `apksigner verify --verbose` 验证签名通过；`aapt dump badging` 确认包名 `com.kaoyan.studybell`、minSdk 26、targetSdk 33、compileSdk 36 和中文应用名称。
- 本次只修改本进度文档，未修改依赖版本、业务源码或项目构建配置，未配置 CI。

### 工具链与兼容性

- 安装完整 Eclipse Temurin JDK **17.0.21+9**，确认 `java` 与 `javac` 可用。
- 使用 Android 官方 Command-line Tools 安装 Platform 36（revision 2）、Build Tools 35.0.0 和 Platform Tools，必要 SDK 许可已接受。
- 保持 AGP 8.13.0、Gradle 8.13、Kotlin / Compose 编译器插件 2.2.20、Compose BOM 2025.09.01（UI 1.9.2、Material 3 1.4.0）。
- AGP 8.13 官方要求 Gradle 8.13 / JDK 17 / Build Tools 35.0.0，支持当前 compileSdk 36。
- Kotlin 2.2.20 官方完整支持范围为 Gradle 7.6.3–8.14、AGP 7.3.1–8.11.1；当前 AGP 8.13.0 超出其完整测试范围。本项目本次实际构建通过，未据此宣称所有构建场景均有官方兼容保证。
- 参考：https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes 、https://kotlinlang.org/docs/gradle-configure-project.html 。

### 云端环境配置及排查

工具链在 `/workspace/work/toolchains/`，Gradle 用户目录为 `/workspace/work/android-gradle-home/`，未全局安装 Gradle 或 Android Studio。

本环境中复用配置：

```sh
source /workspace/work/android-env.sh
cd /workspace/kaoyan-studybell
./gradlew :app:assembleDebug
```

- Java 不自动采用 shell 的 HTTP(S) 代理：在工作区的 Gradle 用户配置中设置 HTTP / HTTPS 代理，并使用系统 Java 信任库 `/etc/ssl/certs/java/cacerts`；没有关闭 TLS 校验。
- 配置后，Wrapper 已用 JDK 17 从原官方 URL 完整下载 Gradle 8.13，校验固定 SHA-256 并成功启动，原下载问题已解决。
- SDK 初次安装失败的具体原因是默认 `.android/cache` 不可写（`NoSuchFileException`），并非下载源不可达。设置 `ANDROID_USER_HOME=/workspace/work/android-user-home` 后安装成功。
- 网络配置和缓存放在项目外，不把代理环境配置加入应用工程。环境若被重建，需恢复上述工具链及配置。
- 构建日志：`/workspace/work/assemble-debug.log`。

### 剩余事项

- 非阻断提示：Android 工具统计配置 `/home/agent/.android/analytics.settings` 不可写；不影响此次构建。
- 非阻断提示：`libandroidx.graphics.path.so` 未剥离调试符号，原样打包。
- 本次验证 Debug 构建和 APK 签名，未验证 Release 构建，也未在小米平板5或模拟器上运行；尚不能确认设备实际显示效果。

---

以下为前阶段记录，环境缺项和未构建状态已由上方最新结果更新。

## 2026-11-10：最小项目框架

已完成：

- 标准单模块 Android 目录及 Gradle Kotlin DSL 配置。
- 包名、namespace、applicationId 统一为 `com.kaoyan.studybell`。
- minSdk 26、targetSdk 33、compileSdk 36。
- Kotlin、Compose 编译器插件、Compose BOM 与 Material 3 依赖配置。
- MainActivity 和居中显示“考研自习铃”的中文首页。
- 应用名称、清单、基础日间／夜间主题。
- Gradle Wrapper、忽略规则及中文开发说明。

验证范围：检查配置、源码路径、资源引用和 Wrapper 文件一致性；不代表 Android 编译或设备运行通过。未执行 assembleDebug，未生成 APK。

环境缺项：完整 JDK、Android SDK Command-line Tools、Platform 36 和 Build Tools 35.0.0；安装与许可步骤见 README。

下一步建议：补齐工具链后执行首次构建，在小米平板5上验证启动与文字居中效果，再确定下一阶段需求。

本阶段未实现课程表、Room、AlarmManager、通知、铃声、复杂 UI，也未配置 GitHub Actions。

云端网络注意：curl 已成功下载官方 Gradle 分发包并验证官方 SHA-256；临时 Gradle 的分发 URL 预检查失败。生成 Wrapper 时使用了 `--no-validate-url`，保留官方 URL 和 SHA-256 校验。Java/Gradle 不一定自动读取 HTTP(S)_PROXY，首次联网构建前需按环境代理要求配置 JVM 代理及 CA 信任；不要把代理凭据提交到仓库。
