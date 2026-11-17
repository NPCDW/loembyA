package app.marlboroadvance.mpvex.preferences

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Removes only the retired demuxer directory; never follows links into other caches or files. */
object LegacyPlaybackCacheCleanup {
  private const val DIRECTORY = "mpv_stream_cache"

  fun removeFrom(cacheRoots: List<File>) {
    cacheRoots.map { it.toPath().toAbsolutePath().normalize() }.distinct().forEach { root ->
      if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return@forEach
      remove(root.resolve(DIRECTORY))
    }
  }

  private fun remove(path: Path) {
    if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
      Files.newDirectoryStream(path).use { children -> children.forEach(::remove) }
    }
    Files.deleteIfExists(path)
  }
}
