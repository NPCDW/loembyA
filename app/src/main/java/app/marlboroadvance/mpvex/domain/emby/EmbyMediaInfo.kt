package app.marlboroadvance.mpvex.domain.emby

import app.marlboroadvance.mpvex.domain.emby.playback.LoembyMediaSelection
import kotlin.math.abs
import kotlin.math.round

/**
 * Emby 媒体信息表 —— **详情页与播放页共用**的唯一事实源。
 *
 * 原先这套东西（`MediaInfoEntry` + 组装逻辑 + 一堆格式化函数）是 `EmbyDetailScreen`
 * 的私有实现，播放页要用只能复制一份；两份一旦各自演进就会「同一个条目、两个页面
 * 显示不一样」。所以整体搬到这里，两边都调 [buildEmbyMediaInfoEntries]。
 *
 * 纯 Kotlin、不依赖 Compose —— 播放页那个弹窗只负责把返回的 [MediaInfoEntry] 画出来。
 */

/** 信息表的一行：分区标题（[Section]）或键值行（[Row]） */
sealed interface MediaInfoEntry {
  data class Section(
    val title: String,
  ) : MediaInfoEntry

  data class Row(
    val label: String,
    val value: String,
  ) : MediaInfoEntry
}

/**
 * 把 Emby 返回的字段整理成「分区 + 键值行」。
 *
 * 顺序：基本信息（含首次入库 / 文件路径）→ 每个视频轨道 → 每个音频轨道 → 字幕轨道。
 * 值为空的项直接跳过，不会出现「未知 / N/A」这种噪音行。
 *
 * @param selectedMediaSourceId valid manual selection, otherwise the shared automatic policy applies
 * @param automaticVersionPolicy shared loemby playback policy
 */
fun buildEmbyMediaInfoEntries(
  item: EmbyItem,
  selectedMediaSourceId: String? = null,
  automaticVersionPolicy: String = "high-resolution",
): List<MediaInfoEntry> {
  val out = mutableListOf<MediaInfoEntry>()

  fun add(label: String, value: String?) {
    if (!value.isNullOrBlank()) out += MediaInfoEntry.Row(label, value)
  }

  fun section(title: String) {
    out += MediaInfoEntry.Section(title)
  }

  // A valid manual ID wins; missing/stale IDs use the same automatic ranking as playback.
  val allSources = item.MediaSources.orEmpty()
  val source = allSources.firstOrNull { !selectedMediaSourceId.isNullOrBlank() && it.Id == selectedMediaSourceId }
    ?: allSources.takeIf { it.isNotEmpty() }?.let {
      it[LoembyMediaSelection.selectAutomaticSourceIndex(it, automaticVersionPolicy)]
    }

  if (allSources.count { !it.Id.isNullOrBlank() } > 1 && source != null) {
    add("版本", embyMediaSourceLabel(source))
  }
  // ⚠️ 这里只能给**条目级**的首次入库时间：Emby 的 `MediaSourceInfo` 里根本没有日期字段
  // （官方文档的属性清单、本项目 `embyclient-reference` 生成的 MediaSourceInfo 都没有
  // `DateCreated` / `DateModified`）—— **服务端不提供「每个版本各自的入库时间」**。
  // 所以给老条目补一个新版本后这里仍是老日期；措辞用「首次入库」，别让人当成当前版本的时间。
  add("首次入库", formatIsoDate(item.DateCreated))
  add("首播日期", formatIsoDate(item.PremiereDate))
  add("时长", (source?.RunTimeTicks ?: item.RunTimeTicks)?.takeIf { it > 0 }?.let { formatEmbyDurationFull(it) })
  add("年份", item.ProductionYear?.toString())
  add("分级", item.OfficialRating)
  add("评分", item.CommunityRating?.let { "★ %.1f".format(it) })
  add("制作", item.Studios?.firstOrNull()?.Name)
  add("容器", source?.Container?.uppercase())
  add("文件大小", source?.Size?.takeIf { it > 0 }?.let { formatFileSize(it) })
  add("总码率", source?.Bitrate?.takeIf { it > 0 }?.let { formatBitrate(it) })
  // ⚠️ 文件路径是**每个版本各自**的属性（EmbyMediaSource.Path）：
  // 默认版本的 Path 恰好等于 item.Path，所以拿 item.Path 时「换到另一个版本仍是原路径」。
  // 多版本切换后要显示当前选中版本的盘路径，回退才用条目级 item.Path。
  add("文件路径", source?.Path ?: item.Path)
  if (out.isEmpty()) {
    add("名称", item.Name)
  }

  val streams = source?.MediaStreams.orEmpty()

  // ── 视频轨道 ──
  val videos = streams.filter { it.Type == "Video" }
  videos.forEachIndexed { index, v ->
    section(if (videos.size > 1) "视频轨道 ${index + 1}" else "视频")
    add("类型", "视频 (Video)")
    add("语言", languageLabel(v.Language))
    add("编码器", codecLine(v))
    add("编码标识", v.CodecTag)
    if (v.Width != null && v.Height != null) add("分辨率", "${v.Width}×${v.Height}")
    add("画面比例", v.AspectRatio)
    add("帧率", frameRateLabel(v))
    add("动态范围", (v.VideoRangeType ?: v.VideoRange)?.uppercase())
    add("比特率", v.BitRate?.takeIf { it > 0 }?.let { formatBitrate(it) })
    add("位深度", v.BitDepth?.let { "$it bit" })
    add("像素格式", v.PixelFormat)
    add("色彩空间", v.ColorSpace)
    add("传输特性", v.ColorTransfer)
    add("色彩原色", v.ColorPrimaries)
    v.IsInterlaced?.let { add("扫描方式", if (it) "隔行 (Interlaced)" else "逐行 (Progressive)") }
    v.RefFrames?.takeIf { it > 0 }?.let { add("参考帧", "$it 帧") }
  }

  // ── 音频轨道 ──
  val audios = streams.filter { it.Type == "Audio" }
  audios.forEachIndexed { index, a ->
    section(if (audios.size > 1) "音频轨道 ${index + 1}" else "音频")
    add("类型", "音频 (Audio)")
    add("语言", languageLabel(a.Language))
    add("编码器", codecLine(a))
    add("编码标识", a.CodecTag)
    add("声道", a.Channels?.let { c -> a.ChannelLayout?.let { "$c ($it)" } ?: "$c" })
    add("采样率", a.SampleRate?.takeIf { it > 0 }?.let { "$it Hz" })
    add("比特率", a.BitRate?.takeIf { it > 0 }?.let { formatBitrate(it) })
    add("位深度", a.BitDepth?.let { "$it bit" })
    a.IsDefault?.let { add("默认音轨", if (it) "是" else "否") }
  }

  // ── 字幕轨道 ──
  val subtitles = streams.filter { it.Type == "Subtitle" }
  if (subtitles.isNotEmpty()) {
    section("字幕（共 ${subtitles.size} 条）")
    subtitles.forEachIndexed { index, s ->
      val tags = buildList {
        if (s.IsDefault == true) add("默认")
        if (s.IsForced == true) add("强制")
        add(if (s.IsExternal == true) "外挂" else "内嵌")
      }
      add(
        "字幕 ${index + 1}",
        listOfNotNull(
          languageLabel(s.Language) ?: s.DisplayTitle,
          s.Codec?.uppercase(),
        ).joinToString(" · ") + " (${tags.joinToString("/")})",
      )
    }
  }

  return out
}

/**
 * Effective multiversion ID for detail information and local version progress.
 * A valid manual selection wins; otherwise use the same shared automatic policy as playback.
 * A single source needs no explicit ID. Unknown policy values retain server response order.
 */
fun effectiveEmbyMediaSourceId(
  item: EmbyItem?,
  selected: String?,
  automaticVersionPolicy: String = "high-resolution",
): String? {
  val sources = item?.MediaSources.orEmpty()
  if (sources.size <= 1) return null
  sources.firstOrNull { !selected.isNullOrBlank() && it.Id == selected }?.let { return it.Id }
  val index = LoembyMediaSelection.selectAutomaticSourceIndex(sources, automaticVersionPolicy)
  return sources[index].Id?.takeIf { it.isNotBlank() }
}

/**
 * 一个媒体源的显示名（如 `4K · HEVC · 12.3 GB`）。
 *
 * 优先用服务端给的 `Name`（网页端下拉显示的也是它）—— 两边显示同一个词，
 * 用户才不会觉得「选的和播的不是一个版本」。没有 `Name` 才按分辨率 / 编码 / 体积拼。
 */
fun embyMediaSourceLabel(source: EmbyMediaSource): String {
  source.Name?.takeIf { it.isNotBlank() }?.let { return it }
  val video = source.MediaStreams?.firstOrNull { it.Type == "Video" }
  val parts = mutableListOf<String>()
  video?.Height?.takeIf { it > 0 }?.let { h ->
    parts += when {
      h >= 2000 -> "4K"
      h >= 1000 -> "1080P"
      h >= 700 -> "720P"
      h >= 500 -> "576P"
      else -> "${h}P"
    }
  }
  video?.Codec?.takeIf { it.isNotBlank() }?.let { parts += it.uppercase() }
  source.Size?.takeIf { it > 0 }?.let { parts += formatFileSize(it) }
  if (parts.isEmpty()) source.Container?.uppercase()?.let { parts += it }
  return parts.joinToString(" · ").ifBlank { "版本" }
}

/** 单集页按版本分组；旧媒体信息表保持原排列。 */
data class EmbyVersionMediaInfo(
  val title: String,
  val basic: List<MediaInfoEntry.Row>,
  val tracks: List<EmbyTrackMediaInfo>,
)

data class EmbyTrackMediaInfo(val title: String, val rows: List<MediaInfoEntry.Row>)

fun buildEmbyVersionMediaInfo(item: EmbyItem): List<EmbyVersionMediaInfo> =
  item.MediaSources.orEmpty().mapIndexed { sourceIndex, source ->
    val single = item.copy(MediaSources = listOf(source), RunTimeTicks = source.RunTimeTicks)
    val basic = buildEmbyMediaInfoEntries(single).takeWhile { it !is MediaInfoEntry.Section }
      .filterIsInstance<MediaInfoEntry.Row>()
    val tracks = source.MediaStreams.orEmpty().filter { it.Type in listOf("Video", "Audio", "Subtitle") }
      .mapIndexed { index, stream ->
        val title = when (stream.Type) { "Video" -> "视频"; "Audio" -> "音频"; else -> "字幕" } + " ${index + 1}"
        val entries = buildEmbyMediaInfoEntries(single.copy(MediaSources = listOf(source.copy(MediaStreams = listOf(stream)))))
        val rows = entries.dropWhile { it !is MediaInfoEntry.Section }.filterIsInstance<MediaInfoEntry.Row>().toMutableList()
        fun add(label: String, value: String?) { if (!value.isNullOrBlank()) rows += MediaInfoEntry.Row(label, value) }
        add("轨道索引", stream.Index?.toString())
        add("标题", stream.DisplayTitle ?: stream.Title)
        add("显示语言", stream.DisplayLanguage)
        add("外置", if (stream.IsExternal == true) "是（外置）" else "否")
        if (stream.Type == "Subtitle") {
          add("编码器", stream.Codec?.uppercase())
          add("语言", languageLabel(stream.Language))
          stream.IsDefault?.let { add("默认", if (it) "是" else "否") }
          stream.IsForced?.let { add("强制", if (it) "是" else "否") }
        }
        EmbyTrackMediaInfo(title, rows)
      }
    EmbyVersionMediaInfo(source.Name?.takeIf { it.isNotBlank() } ?: "版本 ${sourceIndex + 1}", basic, tracks)
  }

/** `HEVC (Main 10 · L4.1)` 这样的编码器描述；没有档次/级别时只给编码名 */
private fun codecLine(stream: EmbyMediaStream): String? {
  val codec = stream.Codec?.uppercase() ?: return null
  val extras = buildList {
    stream.Profile?.takeIf { it.isNotBlank() }?.let { add(it) }
    stream.Level?.let { add("L$it") }
  }
  return if (extras.isEmpty()) codec else "$codec (${extras.joinToString(" · ")})"
}

/** 帧率：优先标称值，其次实际值；整数帧率不带小数点 */
private fun frameRateLabel(stream: EmbyMediaStream): String? {
  val fps = stream.FrameRate ?: stream.RealFrameRate ?: stream.AverageFrameRate ?: return null
  if (fps <= 0.0) return null
  val rounded = round(fps * 100) / 100.0
  return if (abs(rounded - rounded.toInt()) < 0.005) "${rounded.toInt()} fps" else "%.2f fps".format(rounded)
}

/** `2024-01-15T10:23:45.0000000Z` → `2024-01-15` */
private fun formatIsoDate(raw: String?): String? {
  if (raw.isNullOrBlank()) return null
  val datePart = raw.substringBefore('T')
  return datePart.takeIf { it.length >= 8 }
}

/** ISO 639-2/1 语言码 → 中文名（带原码），未知码原样返回 */
private fun languageLabel(code: String?): String? {
  if (code.isNullOrBlank()) return null
  return when (code.lowercase()) {
    "chi", "zho", "zh", "chs", "cht" -> "中文 ($code)"
    "eng", "en" -> "英语 ($code)"
    "jpn", "ja" -> "日语 ($code)"
    "kor", "ko" -> "韩语 ($code)"
    "fra", "fre", "fr" -> "法语 ($code)"
    "deu", "ger", "de" -> "德语 ($code)"
    "spa", "es" -> "西班牙语 ($code)"
    "rus", "ru" -> "俄语 ($code)"
    "und" -> "未指定"
    else -> code
  }
}

internal fun formatBitrate(bps: Long): String = when {
  bps >= 1_000_000 -> "%.1f Mbps".format(bps / 1_000_000.0)
  bps >= 1_000 -> "%d kbps".format(bps / 1_000)
  else -> "$bps bps"
}

/**
 * tick 时长 → 中文可读文本，**秒级也一定有数字**。
 *
 * 与首页/卡片用的 `formatDuration`（只到分钟、不足 1 分钟返回空串）的区别就在这里：
 * 详情页 / 播放页的「时长」如果不足 1 分钟会显示成空括号，看起来像没数据。
 */
fun formatEmbyDurationFull(ticks: Long): String {
  val totalSeconds = EmbyTicks.ticksToSeconds(ticks).coerceAtLeast(0L)
  if (totalSeconds <= 0L) return "0 秒"
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return when {
    hours > 0 -> "${hours}小时${minutes}分"
    minutes > 0 -> "${minutes}分${seconds}秒"
    else -> "${seconds}秒"
  }
}

internal fun formatFileSize(bytes: Long): String {
  val mb = bytes / (1024.0 * 1024.0)
  return when {
    mb >= 1024 -> "%.2f GB".format(mb / 1024)
    else -> "%.0f MB".format(mb)
  }
}
