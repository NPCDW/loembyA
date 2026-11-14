package app.marlboroadvance.mpvex.ui.player

import android.content.Context
import java.io.File

/**
 * Directory bookkeeping for libmpv's temporary demuxer disk buffer.
 *
 * The bundled native options are `cache-on-disk`, `demuxer-cache-dir`, and
 * `demuxer-cache-unlink-files`. There is no verified `cache-persize` or persistent URL-indexed
 * replay. Files are temporary streaming buffers, not downloads or offline media. Seekable byte
 * windows do not promise a hard physical disk-file limit.
 *
 * The application-specific external cache directory needs no storage permission; internal cache
 * is the fallback. Prepare an absolute directory before handing it to mpv.
 */
object MpvDiskCache {
  /** 缓存根目录名。放在 `cacheDir` 下，与截图等临时文件分开。 */
  private const val DIR_NAME = "mpv_stream_cache"

  /** Legacy residue-cleanup threshold; not a native per-stream limit. */
  private const val CLEANUP_THRESHOLD = 0.9

  /** Application-specific external cache when available, internal cache otherwise. */
  fun cacheRoot(context: Context): File {
    val base = context.externalCacheDir ?: context.cacheDir
    return File(base, DIR_NAME)
  }

  /** 确保目录存在，返回它；建不出来（比如存储满）返回 null。 */
  fun ensureDir(context: Context): File? =
    runCatching {
      val dir = cacheRoot(context)
      if (dir.isDirectory || dir.mkdirs()) dir else null
    }.getOrNull()

  /** 当前占用（字节）。目录不存在时返回 0。 */
  fun sizeBytes(context: Context): Long =
    runCatching {
      val dir = cacheRoot(context)
      if (!dir.isDirectory) return@runCatching 0L
      dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

  /** 删掉整个缓存目录。返回是否真的清掉了（目录本来就不算清成功 —— 那是幂等的）。 */
  fun clear(context: Context): Boolean =
    runCatching {
      val dir = cacheRoot(context)
      !dir.exists() || dir.deleteRecursively()
    }.getOrDefault(false)

  /**
   * Pre-play cleanup of directory-visible residue using the legacy size preference.
   * This is not a live disk cap: unlinked native buffers are not included by directory enumeration.
   *
   * @return whether cleanup was attempted
   */
  fun trimIfNeeded(
    context: Context,
    limitMb: Int,
  ): Boolean {
    val limit = limitMb.coerceIn(MIN_SIZE_MB, MAX_SIZE_MB).toLong() * 1024L * 1024L
    val current = sizeBytes(context)
    // 当前用量已在阈值内就不动 —— 每部片子开播都扫一遍目录是白费 IO
    if (current <= (limit * CLEANUP_THRESHOLD).toLong()) return false
    clear(context)
    ensureDir(context)
    return true
  }

  const val MIN_SIZE_MB = 64
  const val MAX_SIZE_MB = 2048

  /** 默认上限（MB）。与 [app.marlboroadvance.mpvex.preferences.PlayerPreferences.diskCacheSizeMb] 一致。 */
  const val DEFAULT_SIZE_MB = 512
}
