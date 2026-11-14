package app.marlboroadvance.mpvex.ui.browser.emby

import androidx.lifecycle.ViewModel
import app.marlboroadvance.mpvex.domain.emby.EmbyItem
import app.marlboroadvance.mpvex.domain.emby.EmbyItemPolicy
import app.marlboroadvance.mpvex.domain.emby.EmbyRepository
import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.domain.emby.playback.PlaybackDetailOrigin
import app.marlboroadvance.mpvex.domain.emby.playback.PlaybackSpec
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.MainScreen
import app.marlboroadvance.mpvex.ui.browser.emby.components.locateEpisodeInSeason
import app.marlboroadvance.mpvex.ui.browser.emby.components.resolveEpisodeParents
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Activity-retained, memory-only follower shared by Classic and Cine. Call on the navigation thread.
 * Queue origins use reference identity; neither sessions nor follower state belong in saved state.
 */
internal class EmbyPlaybackDetailFollower : ViewModel() {
  private var lastOrigin: PlaybackDetailOrigin? = null
  private val retiredOrigins = WeakHashMap<PlaybackDetailOrigin, Unit>()
  private val acceptedItemIds = linkedSetOf<String>()
  private var attachedStack: WeakReference<MutableList<Screen>>? = null
  private var expectedItemId: String? = null
  private var disarmed = false

  /** Returns true only for an actual top-entry replacement, never for a push or a same-item replay. */
  fun followPlaybackDetail(
    stack: MutableList<Screen>,
    spec: PlaybackSpec,
    compatibilityGateway: EmbyServerGateway?,
    isCurrent: (EmbyServer) -> Boolean,
  ): Boolean {
    val origin = spec.detailOrigin ?: return false
    if (!spec.itemType.equals("Episode", ignoreCase = true) || spec.itemId.isBlank() ||
      origin.detailEntryId.isBlank() || origin.itemId.isBlank()
    ) return false
    if (origin in retiredOrigins || lastOrigin === origin && disarmed) return false

    val top = stack.lastOrNull()
    val detail = when (top) {
      is EmbyDetailScreen -> top
      is EmbyServerGateway -> top.destination as? EmbyDetailScreen
      else -> null
    } ?: return false
    if (detail.detailEntryId != origin.detailEntryId) return false
    val gateway = playbackDetailGateway(stack, compatibilityGateway) ?: return false
    val ref = EmbyServerRef.from(spec.server)
    if (ref.sourceKey.isBlank() || ref.remoteId.isBlank() || ref.sourceRevision < 0 ||
      gateway.serverRef != ref || !isCurrent(spec.server)
    ) return false
    if (lastOrigin !== origin) {
      lastOrigin?.let { retiredOrigins[it] = Unit }
      lastOrigin = origin
      expectedItemId = origin.itemId
      acceptedItemIds.clear()
      attachedStack = WeakReference(stack)
      disarmed = false
    } else if (attachedStack?.get() !== stack) {
      // A recreated root may restore an older snapshot saved before background following.
      // Only known queue items can rebase; a same-list manual switch never gets this exception.
      if (detail.itemId == origin.itemId || detail.itemId in acceptedItemIds) expectedItemId = detail.itemId
      attachedStack = WeakReference(stack)
    }

    // Check before same-item handling: a manual switch to the replayed item is not acceptance.
    if (detail.itemId != expectedItemId) {
      disarmed = true
      return false
    }
    if (detail.itemId == spec.itemId) return false
    val replaced = replaceTopEmbyDetailEntry(
      stack,
      origin.detailEntryId,
      spec.itemId,
      spec.title.ifBlank { spec.itemName.ifBlank { spec.itemId } },
    )
    if (replaced) {
      expectedItemId = spec.itemId
      acceptedItemIds.remove(spec.itemId)
      acceptedItemIds.add(spec.itemId)
      if (acceptedItemIds.size > 128) acceptedItemIds.remove(acceptedItemIds.first())
    }
    return replaced
  }
}

/** Compatibility belongs only to the nearest singleton home, not an arbitrary unbound detail. */
private fun playbackDetailGateway(
  stack: List<Screen>,
  compatibilityGateway: EmbyServerGateway?,
): EmbyServerGateway? {
  nearestEmbyServerGateway(stack, stack.lastIndex)?.let { return it }
  if (compatibilityGateway?.target != EmbyServerDestination.Home) return null
  for (entry in stack.asReversed()) {
    when (entry) {
      EmbyServerHomeScreen -> return compatibilityGateway
      MainScreen -> return null
      is EmbyServerGateway -> return null
      else -> Unit
    }
  }
  return null
}

/**
 * Read-only preloading after a successful follow, even while the player covers the media root.
 * Cancel the preceding caller job on a new loaded spec. Shared cache flights may finish warming
 * their own query after cancellation, but this caller cannot fetch further pages or publish a snapshot.
 * Failures propagate for the caller's best-effort handling; cancellation must not be swallowed.
 */
internal suspend fun preloadPlaybackDetail(spec: PlaybackSpec, repository: EmbyRepository) {
  if (spec.detailOrigin == null || !spec.itemType.equals("Episode", ignoreCase = true) || spec.itemId.isBlank()) return
  currentCoroutineContext().ensureActive()
  val server = repository.sessionServer(spec.server)
  suspend fun loadItem(id: String): EmbyItem {
    val item = EmbyDetailCache.loadItem(server, id) {
      repository.getItem(server, id).also { fetched ->
        require(fetched.Id == id) { "Unexpected media detail identity" }
        EmbyItemPolicy.requireSupported(fetched)
        if (id == spec.itemId) require(fetched.Type.equals("Episode", ignoreCase = true)) {
          "Playback detail is not an episode"
        }
      }
    }
    currentCoroutineContext().ensureActive()
    require(item.Id == id) { "Unexpected cached media detail identity" }
    return EmbyItemPolicy.requireSupported(item)
  }
  val episode = loadItem(spec.itemId)
  require(episode.Type.equals("Episode", ignoreCase = true)) { "Playback detail is not an episode" }
  val owner = EmbyBrowseCache.owner(server)
  val parents = EmbyBrowseCache.results.load(owner, EmbyDetailQueries.parentsForEpisode(spec.itemId)) {
    resolveEpisodeParents(episode, ::loadItem)
  }
  currentCoroutineContext().ensureActive()
  val seriesId = requireNotNull(parents.series.Id).also { require(it.isNotBlank()) }
  require(parents.series.Type.equals("Series", ignoreCase = true) && parents.seasonId.isNotBlank()) {
    "Unexpected episode parent identity"
  }
  EmbyItemPolicy.requireSupported(parents.series)
  val page = PagedList<EmbyItem>(keyOf = { it.Id ?: it })
  EmbyBrowseCache.results.peek<PagedListSnapshot<EmbyItem>>(
    owner,
    "page:${EmbyDetailQueries.episodes(seriesId, parents.seasonId)}",
  )?.let(page::restore)
  val loader: suspend (Int, Int) -> PagedResult<EmbyItem> = { start, limit ->
    currentCoroutineContext().ensureActive()
    EmbyDetailCache.loadEpisodes(server, seriesId, parents.seasonId, start, limit) {
      val result = repository.getEpisodes(server, seriesId, parents.seasonId, start, limit)
      PagedResult(
        items = result.Items.filter { it.Type.equals("Episode", ignoreCase = true) && EmbyItemPolicy.isSupported(it) },
        totalCount = result.TotalRecordCount.takeIf { it > 0 },
        pageSize = limit,
        consumedCount = result.consumedCount,
      )
    }
  }
  // Reuse already-located snapshots; otherwise fetch only up to this episode, with the UI's bound.
  if (!page.loaded) page.reload(loader)
  locateEpisodeInSeason(page, spec.itemId, loader)
  currentCoroutineContext().ensureActive()
  repository.sessionServer(server)
  if (page.loaded) EmbyDetailCache.putEpisodes(server, seriesId, parents.seasonId, page.snapshot())
}
