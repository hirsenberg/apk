# 固定铃声音效来源与许可

核对日期：2026-11-10。官方分类页：https://mixkit.co/free-sound-effects/bell/ 。

从该页面的音效卡片按完整名称确认 Happy bells notification（ID 937）和 Achievement bell（ID 600）；访问卡片实际下载入口取得官方 WAV URL。未使用预览 MP3、同名站外文件或替代素材。

## 许可核对

- 官方许可：https://mixkit.co/license/#sfxFree
- 实际许可正文：https://mixkit.co/license/modal/sfxFree/
- 用户条款：https://mixkit.co/terms/
- 正文快照：`docs/licenses/MIXKIT_SOUND_EFFECTS_FREE_LICENSE.txt`。

Sound Effects Free License 允许免费用于商业和非商业项目，允许将音效与其他内容组合成范围更大、性质不同的 End Product。本应用作为带课程管理的学习应用，将音效作为内置试听／铃声组成部分，按该终端作品用途打包进 APK。

关键限制原文："You can’t redistribute the Item on its own, as stock, in a tool or template, or with source files." 不得单独再分发素材、作为素材库／模板／工具素材分发，或随源码分发；不能声称拥有素材版权。这里的授权不意味着音频已开源。音频只在本地工程内用于构建；本次源码交付排除这些音频文件，并提供官方获取脚本，不连同源码公开或打包再分发。公开仓库中的来源信息、校验值和脚本不包含音频本体。

## 格式处理

官方完整下载文件为 WAV，而用户要求 MP3。使用 FFmpeg libmp3lame 192 kbps 转码，去除元数据；没有剪辑、变速、混音、增益处理或替换内容。MP3 编码存在正常的有损压缩和编码填充。原 WAV 仅保存在工作区中间目录；APK 内只包含两段指定 MP3。

### Happy bells notification

- Mixkit item ID: 937
- 官方下载入口：https://mixkit.co/free-sound-effects/download/937/
- 入口返回的原文件：https://assets.mixkit.co/active_storage/sfx/937/937.wav
- 应用文件：`app/src/main/res/raw/class_start.mp3`，74021 字节。
- 原 WAV SHA-256：`c2be68e9eb59f744e7153678fc3e3c3c03692461a16cf41e7153b7e0478df248`
- 转码 MP3 SHA-256：`69d30ec6d5c5bfda26e4d11b2cbe0baea02f3690cc77defcf9edb41e89ff76bf`

### Achievement bell

- Mixkit item ID: 600
- 官方下载入口：https://mixkit.co/free-sound-effects/download/600/
- 入口返回的原文件：https://assets.mixkit.co/active_storage/sfx/600/600.wav
- 应用文件：`app/src/main/res/raw/class_end.mp3`，59025 字节。
- 原 WAV SHA-256：`7a5c0b2fb7f7cf77f6b3d36e9e165176a0b2f463c6ff3e6f3c133eda7fc945a1`
- 转码 MP3 SHA-256：`8740b0e3dfab4eaa23b9eb20ce4608a08c33a47ffce298e387fbca49e13392d1`

## 克隆后的本地获取步骤

自动方式：安装 Python 3.9+、curl、FFmpeg（含 libmp3lame），阅读并遵守上述许可后，在项目根目录运行 `python3 scripts/fetch_audio.py`。脚本下载本页列出的官方 WAV，逐一核验本页的原 WAV SHA-256，再以 libmp3lame 192 kbps 转码。只有两段均验证、转码成功才写入 raw 目录。

手动方式：

1. 打开官方 bell 分类页，按名称核对937与600，再使用各自官方下载入口取得 WAV。
2. 用 `sha256sum`（Windows 可使用 `Get-FileHash -Algorithm SHA256`）核对上文原 WAV 哈希；不一致则停止，不使用其他素材。
3. 在本地临时目录保存937.wav与600.wav，在项目根目录创建 `app/src/main/res/raw/` 后执行：

```sh
ffmpeg -nostdin -y -i /本地临时目录/937.wav -map_metadata -1 -codec:a libmp3lame -b:a 192k app/src/main/res/raw/class_start.mp3
ffmpeg -nostdin -y -i /本地临时目录/600.wav -map_metadata -1 -codec:a libmp3lame -b:a 192k app/src/main/res/raw/class_end.mp3
```

4. 执行 `./gradlew :app:assembleDebug :app:testDebugUnitTest`。音频只用于自己的本地构建，不提交到本公开源码仓库。不同 FFmpeg 版本输出的MP3字节可能与上方首次转码校验值不同，因此严格身份检查以官方WAV哈希为准。

仓库未提供这些 MP3，缺少它们时构建会因 raw 资源引用不存在而失败；这是明确的准备要求，不能以其他音效或静音文件绕过。获取完成后 APK 可完全离线试听。
