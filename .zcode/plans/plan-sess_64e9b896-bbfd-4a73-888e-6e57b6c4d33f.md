## Context
这一轮需要修正播放器缓存/缓冲反馈/缓存进度条/横竖屏行为，同步 loemby 新增的反代线路支持，并整理关于页和旧更新日志。

已确认：
- 缓存预算来自共享 PostgreSQL `global_config`，经 `PlaybackConfigurationRepository → LoopbackPlaybackServer → LoembyPlaybackCoordinator` 生效，不是读取 Room 配置。Debug/Release 的本地数据库位于不同应用沙箱；如果手动配置相同 PostgreSQL，它们仍可连接同一共享库，不能从源码推断实际连接目标。
- Android 覆盖了缓存字节上限，却保留 `MPVView` 的 45 秒（低内存 8 秒）预读上限；约 10Mbps × 45 秒 ≈ 56MB。这与本地 loemby 不额外设置该时间上限的实现不同，是当前最明确的排查点。2GB 文件不意味着应缓存整个文件。
- `paused-for-cache` 已被监听，但没有驱动加载层；缓存进度条目前只有缓存/未缓存两类，并忽略空缓存节点。
- 目前自动横竖屏仅锁定一次，没有版本分辨率的临时判断，同集换版本也不能重新判断。
- 本地 loemby 停在 `7173864`，没有所述最新反代实现。你已授权更新本地参考库，实施时先更新，再以实际最新代码为准，不猜共享字段。

## 实施方案

### 1. 缓存配置与 loemby 对齐
- 保留共享 PostgreSQL 的 typed cache 配置、来源检查和字节预算计算；不新增 Room 配置，不迁移数据库，也不读取或打印实际连接凭据。
- 移除 Android 遗留的 45/8 秒有限预读限制，统一初始化与 `configureCache()` 的职责，使有效读取窗口由共享配置计算出的前向/回看字节预算约束。核对 native mpv 的选项默认值/运行时读回，避免移除上限后反而落到较小默认值。
- 继续区分 demux 缓存窗口和本地磁盘缓存：不把文件大小、磁盘占用或网络下载速度当作缓存范围。核查当前落盘选项是否被 native mpv 接受，以及播放中缓存清理是否会干扰活动流；仅修正实际存在的问题，不实现新的离线下载功能。
- 增加脱敏诊断：实际版本码率、前向/回看有效限额、缓存秒数、seekable ranges、缓存 EOF/等待状态，不输出 URL、token 或连接配置。

关键文件：`ui/player/MPVView.kt`、`ui/player/LoembyPlaybackCoordinator.kt`、`domain/postgres/PlaybackConfigurationRepository.kt`，必要时 `ui/player/MpvDiskCache.kt`。

### 2. 缓存不足时展示加载状态
- 将 `paused-for-cache`、`cache-buffering-state` 等实际 native 状态接入播放器状态模型。
- 区分首帧加载、缓存不足、用户暂停、切片/seek 和播放错误。首帧以后再次缺缓存，也显示加载层，不受控制栏显隐影响。
- native 提供有效百分比时显示进度；未知时显示不定进度，不伪造数值。恢复播放、换集、退出或错误后及时清理状态；用户主动暂停不显示“加载中”。

关键文件：`ui/player/MPVView.kt`、`PlayerActivity.kt`、`PlayerViewModel.kt` 和现有加载层；若另一个仍在使用的内部播放器有同样状态缺口，同步其共享行为。

### 3. 已播放缓存区域动态更新
- 每次使用当前 `demuxer-cache-state.seekable-ranges`，空节点清空快照，不把历史范围累积成永久缓存。
- 按播放头切分为“已播放且缓存 / 已播放但未缓存 / 未播放且缓存 / 未播放未缓存”，以可辨识样式绘制；回看缓存被 mpv 丢弃后，对应区域立即变化。
- BOF/EOF 标记不再把明显不存在的范围无限延长，尤其不能将提前 EOF 误画成整部片已缓存。
- 保留拖动预览、点击跳转、无障碍操作及现有 EOF 恢复逻辑。

关键文件：`ui/player/CacheTimeline.kt`、`ui/player/controls/components/Seekbar.kt`、`PlayerViewModel.kt`。

### 4. 仅静默指定的非致命失败
- 播放进度上报失败与字幕预缓存失败改为脱敏日志，不弹 Toast/通知；失败不能中断视频播放。
- 在实际产生失败的位置按能力分类处理，不通过匹配中文提示文本或屏蔽所有错误实现。
- 保留真正的媒体流、播放初始化、认证等错误反馈；不扩大重试或 PostgreSQL 写入能力，不改变未知写入结果的只读核对规则。

关键文件：`ui/player/LoembyPlaybackCoordinator.kt`、`domain/emby/playback/LoopbackPlaybackServer.kt`、`LoopbackSubtitleCache.kt`。

### 5. 默认横屏 → 版本分辨率 → native 实际尺寸
- 播放器启动默认固定横屏，缺失或无效版本尺寸也保持横屏。
- 获取所选版本后，用其视频宽高作临时横竖屏判断；native 获得真实、旋转校正后的显示比例后，重新采用真实比例。
- 自动状态按实际播放条目/版本重置，不只按 item ID；手动旋转保持用户控制，不被迟到的元数据抢回。
- 更新 manifest/policy 与相应测试，普通应用页面仍保持原来的方向策略。

关键文件：`AndroidManifest.xml`、`ui/player/AppOrientationPolicy.kt`、`PlayerActivity.kt`，复用 `MPVView` 的 native 比例计算及现有 `EmbyMediaStream` 模型。

### 6. 同步 loemby 最新反代支持
- 先检查 `/root/loemby` 工作树，再从规定 CNB 来源更新并记录本次核对的提交；不改参考库业务代码。
- 核对最新的应用数据/图标、Trakt、Simkl、YamTrack 反代选择字段与 URL 拼接规则。按实际实现增加最小 typed 只读配置，复用来源版本检查、`reverse_proxy_server` 映射和现有 URL 规范化工具。
- 公共图标/应用数据走各自配置的反代，不能错误借用当前 Emby 服务器身份；追踪服务分别应用其线路，覆盖实际调用的 token/scrobble/webhook 路径。
- 无线路保持直连；无效或过期来源按既有边界失败。防止双重套反代、跨目标泄露凭据和错误缓存复用；不新增共享配置编辑、认证 UI 或 forward-proxy 产品能力。
- 如果最新实现与上述描述有实质冲突，会明确指出后再调整，不用旧 schema 或猜测字段顶替。

主要涉及：`domain/postgres/PlaybackConfigurationRepository.kt`、`PostgresEmbyStore.kt`、`domain/emby/EmbyUrls.kt`、`ui/browser/emby/components/EmbyServerIcon.kt`、`domain/emby/playback/PlaybackScrobbler.kt`、`LoopbackHttpPool.kt`。

### 7. 关于页与旧更新日志
- 关于页顶部突出 `https://github.com/NPCDW/loembyA` 项目入口；原项目和其他项目/许可按钮移到下方致谢，保留必要开源许可证和署名。
- 清除原项目更新日志文件、资源、文档和历史版本注释，包括 `app/build.gradle.kts` 中巨大的旧版本历史块；保留真实版本配置与仍有效的技术说明，不删除代码逻辑。
- 检查更新入口是否仍请求原项目并展示其 Release 内容；改为本项目发布来源，确保关于/更新界面不再重新带回原项目日志。仅处理相关来源和展示，不扩展整套更新系统。
- 不重写 Git 历史，不删除参考库内容，也不将 `AGENTS.md` 用作交付记录。

关键文件：`ui/preferences/AboutScreen.kt`、相关字符串/致谢数据、`utils/update/UpdateFeature.kt`、`app/build.gradle.kts`，以及搜索确认的旧日志载体。

## 验证
- 补测试：缓存所有有效选项与 shared source 隔离；缓存范围收缩、空快照、提前 EOF；四类进度区间；缓冲进入/恢复/暂停/换集；指定失败静默但致命错误保留；版本临时比例和 native 覆盖、同集换版本、手动旋转；各反代服务直连/套前缀/重定向/来源失效/凭据隔离；关于入口和旧日志清理。
- 保留上一轮的详情后台跟随与后置签名修改及测试。
- 按仓库规定运行单元测试、arm64 Debug/Release 构建及 lint，并检查 APK 实际包名、ABI 和签名；不加 `--max-workers`。
- native 缓存 plateau、缓冲加载层和横竖屏最终还需真实播放验证：本次先完成自动化及构建验证，不自行安装设备或连接媒体/追踪服务。需要真机验证时另行确认设备及授权，无法验证的项目明确注明。
- 不提交、推送、触发 Actions 或发布；不输出秘密。若必须变更开发环境，同步修改 `dev-android-Dockerfile`。