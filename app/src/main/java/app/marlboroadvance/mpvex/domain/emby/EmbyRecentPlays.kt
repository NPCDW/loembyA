package app.marlboroadvance.mpvex.domain.emby

/**
 * 本地「最近播放」账本。
 *
 * ## 服务端时间戳与客户端兜底
 *
 * Resume 按最后观看时间降序返回，但批量接口未必带 `UserData.LastPlayedDate`，
 * 且不同服务端版本对播放时间戳的更新时机可能不同。
 * 起播时在本地记一笔；首页将最近播放却不在服务端结果中的条目通过 `Ids=` 补回，
 * 再按本地时间排到最前（见 `EmbyViewModel.mergeRecentPlays`）。
 *
 * ## 存储形态
 *
 * `id:时间戳,id:时间戳,...`（时间戳 = `System.currentTimeMillis()`）。
 * Emby 的条目 Id 是 32 位十六进制、不含 `,` 与 `:`，所以直接切分是安全的，
 * 不需要 JSON —— 与 `BrowserPreferences` 里那几个「逗号分隔小串」保持同一路数。
 */
object EmbyRecentPlays {
  /** 账本保留条数。够覆盖「这几天看过的」，又不至于无限长 */
  const val MAX_ENTRIES = 40

  private const val ITEM_SEP = ','
  private const val TS_SEP = ':'

  /** 解析成 (条目 Id, 记录时间) 列表；坏段静默丢弃，不抛异常 */
  fun parse(raw: String): List<Pair<String, Long>> {
    if (raw.isBlank()) return emptyList()
    return raw.split(ITEM_SEP).mapNotNull { seg ->
      val cut = seg.indexOf(TS_SEP)
      if (cut <= 0) return@mapNotNull null
      val id = seg.substring(0, cut)
      // 时间戳解析失败就当成 0：条目本身还有用（总比丢掉强），只是排最后
      val ts = seg.substring(cut + 1).toLongOrNull() ?: 0L
      if (id.isBlank()) null else id to ts
    }
  }

  fun serialize(entries: List<Pair<String, Long>>): String =
    entries.joinToString(ITEM_SEP.toString()) { "${it.first}$TS_SEP${it.second}" }

  /**
   * 记一笔：同 Id 去重（挪到最前）、按时间倒序、超过 [MAX_ENTRIES] 截断。
   *
   * 重复播放同一条不会让账本变长，只是把它的时间戳刷新 —— 否则连看三集就把
   * 前两集挤出账本了。
   */
  fun record(raw: String, itemId: String, now: Long): String {
    if (itemId.isBlank()) return raw
    val kept = parse(raw).filterNot { it.first == itemId }
    return serialize((listOf(itemId to now) + kept).take(MAX_ENTRIES))
  }

  /** 最近播放过的 Id，最近的在前 */
  fun recentIds(raw: String, limit: Int): List<String> =
    parse(raw)
      .sortedByDescending { it.second }
      .map { it.first }
      .distinct()
      .take(limit)
}
