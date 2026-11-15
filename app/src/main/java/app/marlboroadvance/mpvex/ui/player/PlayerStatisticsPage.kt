package app.marlboroadvance.mpvex.ui.player

import androidx.annotation.StringRes
import app.marlboroadvance.mpvex.R

/** Pages provided by the bundled mpv statistics script. */
enum class PlayerStatisticsPage(
  val number: Int,
  @StringRes val titleRes: Int,
) {
  Default(1, R.string.player_statistics_page_default),
  FrameTimings(2, R.string.player_statistics_page_frame_timings),
  Cache(3, R.string.player_statistics_page_cache),
  Tracks(5, R.string.player_statistics_page_tracks),
}
