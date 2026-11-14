package app.marlboroadvance.mpvex.ui.player

/** Native buffering progress is not cache occupancy or downloaded bytes. Null means indeterminate. */
data class PlayerLoadingState(
  val active: Boolean = true,
  val awaitingFirstFrame: Boolean = true,
  val pausedForCache: Boolean = false,
  val seeking: Boolean = false,
  val manuallyPaused: Boolean = false,
  val nativePercent: Int? = null,
) {
  val visible: Boolean
    get() = active && !manuallyPaused && (awaitingFirstFrame || pausedForCache || seeking)

  val percent: Int?
    get() = nativePercent.takeIf { visible }

  fun newFile(): PlayerLoadingState = PlayerLoadingState()

  fun cachePaused(paused: Boolean): PlayerLoadingState = if (!active) this else copy(
    pausedForCache = paused,
    nativePercent = if (paused) nativePercent else null,
  )

  fun seekChanged(value: Boolean): PlayerLoadingState = if (!active) this else copy(
    seeking = value,
    nativePercent = if (value || pausedForCache) nativePercent else null,
  )

  fun progress(value: Long?): PlayerLoadingState = if (!active) this else copy(
    nativePercent = value?.takeIf { awaitingFirstFrame || pausedForCache || seeking }
      ?.takeIf { it in 0L..100L }?.toInt(),
  )

  fun manualPause(value: Boolean): PlayerLoadingState = if (!active) this else copy(manuallyPaused = value)

  /** Restarts include seek completion/cache recovery, not only the first frame of a file. */
  fun restarted(): PlayerLoadingState = if (!active) this else copy(
    awaitingFirstFrame = false,
    pausedForCache = false,
    seeking = false,
    nativePercent = null,
  )

  /** Errors and exit cannot leave a spinner behind or revive it with a late native callback. */
  fun stopped(): PlayerLoadingState = copy(
    active = false,
    awaitingFirstFrame = false,
    pausedForCache = false,
    seeking = false,
    manuallyPaused = false,
    nativePercent = null,
  )
}
