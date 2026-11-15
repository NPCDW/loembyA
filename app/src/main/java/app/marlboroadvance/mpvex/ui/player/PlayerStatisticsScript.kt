package app.marlboroadvance.mpvex.ui.player

import java.io.File

internal object PlayerStatisticsScript {
  const val ASSET_PATH = "mpvex/stats.lua"
  const val READY_PROPERTY = "user-data/mpvex/stats-ready"
  const val SHOW_MESSAGE = "mpvex-show"
  const val HIDE_MESSAGE = "mpvex-hide"

  fun deploy(filesDir: File, content: ByteArray): File {
    val target = File(filesDir, ASSET_PATH)
    check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
    if (!target.isFile || !target.readBytes().contentEquals(content)) target.writeBytes(content)
    return target
  }
}
