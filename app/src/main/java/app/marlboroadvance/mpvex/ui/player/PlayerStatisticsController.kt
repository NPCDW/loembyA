package app.marlboroadvance.mpvex.ui.player

internal sealed interface PlayerStatisticsAction {
  data class Show(val page: PlayerStatisticsPage) : PlayerStatisticsAction
  data object Hide : PlayerStatisticsAction
}

/** Keeps the latest touch action until the script has registered its message handlers. */
internal class PlayerStatisticsController(
  private val send: (PlayerStatisticsAction) -> Unit,
  private val onUnavailable: () -> Unit,
) {
  private enum class State { Unavailable, Loading, Ready, Failed }

  private var state = State.Unavailable
  private var pending: PlayerStatisticsAction? = null

  fun beginLoading() {
    state = State.Loading
    pending = null
  }

  fun request(action: PlayerStatisticsAction) {
    when (state) {
      State.Unavailable, State.Failed -> onUnavailable()
      State.Loading -> pending = action
      State.Ready -> send(action)
    }
  }

  fun onReady() {
    if (state != State.Loading && state != State.Failed) return
    state = State.Ready
    val action = pending
    pending = null
    if (action != null) send(action)
  }

  fun onLoadTimeout() {
    if (state != State.Loading) return
    state = State.Failed
    val hadPendingAction = pending != null
    pending = null
    if (hadPendingAction) onUnavailable()
  }

  fun stop() {
    state = State.Unavailable
    pending = null
  }
}
