package app.marlboroadvance.mpvex.ui.browser.emby

import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.domain.emby.playback.PlaybackDetailOrigin
import app.marlboroadvance.mpvex.domain.emby.playback.PlaybackSpec
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.MainScreen
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure navigation tests: no composition, authentication, database, or player needed. */
class EmbyPlaybackDetailFollowTest {
  private val server = EmbyServer(
    id = 1,
    name = "Server A",
    host = "example.test",
    username = "",
    password = "",
    remoteId = "server-A",
    sourceKey = "source-A",
    sourceRevision = 3,
  )
  private val ref = EmbyServerRef.from(server)
  private val origin = PlaybackDetailOrigin("detail-entry", "episode-1")
  private val initial = EmbyDetailScreen(origin.itemId, "Episode 1", origin.detailEntryId)
  private val home = EmbyServerGateway(ref, "visit-A", EmbyServerHomeScreen)
  private val follower = EmbyPlaybackDetailFollower()

  private fun spec(
    itemId: String = "episode-2",
    origin: PlaybackDetailOrigin? = this.origin,
  ): PlaybackSpec = PlaybackSpec(
    server = server,
    itemId = itemId,
    title = "Title $itemId",
    itemType = "Episode",
    detailOrigin = origin,
  )

  private fun stack(vararg entries: Screen): MutableList<Screen> = mutableListOf(MainScreen, *entries)

  private fun follow(
    stack: MutableList<Screen>,
    spec: PlaybackSpec = spec(),
    compatibility: EmbyServerGateway? = null,
    current: Boolean = true,
  ): Boolean = follower.followPlaybackDetail(stack, spec, compatibility) { current }

  private fun assertRejected(
    stack: MutableList<Screen>,
    spec: PlaybackSpec = spec(),
    compatibility: EmbyServerGateway? = null,
    current: Boolean = true,
  ) {
    val before = stack.toList()
    assertFalse(follow(stack, spec, compatibility, current))
    assertEquals(before, stack)
    before.indices.forEach { assertSame(before[it], stack[it]) }
  }

  @Test fun sameItemIsAnIdentityPreservingNoOpThenAutomaticNextEpisodeFollows() {
    val stack = stack(home, initial)
    assertRejected(stack, spec(origin.itemId))
    assertTrue(follow(stack))
    assertEquals("episode-2", (stack.last() as EmbyDetailScreen).itemId)
    assertRejected(stack)
    assertTrue(follow(stack, spec("episode-3")))
  }

  @Test fun ordinaryDetailReplacementPreservesContentKeyDepthAndAncestors() {
    val parent = EmbyDetailScreen("series", "Series", "parent-entry")
    val stack = stack(home, parent, initial)
    val before = stack.toList()
    val key = embyNavigationContentKey(initial, home, stack.lastIndex)
    assertTrue(follow(stack))
    val detail = stack.last() as EmbyDetailScreen
    assertEquals("episode-2", detail.itemId)
    assertEquals("Title episode-2", detail.title)
    assertEquals(origin.detailEntryId, detail.detailEntryId)
    assertEquals(key, embyNavigationContentKey(detail, home, stack.lastIndex))
    assertEquals(before.size, stack.size)
    before.dropLast(1).indices.forEach { assertSame(before[it], stack[it]) }
    stack.removeAt(stack.lastIndex)
    assertEquals(before.dropLast(1), stack)
  }

  @Test fun gatewayReplacementPreservesVisitReferenceEntryKeyAndRestrictedSavedPayload() {
    val gateway = EmbyServerGateway(ref, "visit-A", initial)
    val stack = stack(gateway)
    val key = embyNavigationContentKey(gateway, gateway)
    assertTrue(follow(stack))
    val replaced = stack.last() as EmbyServerGateway
    val detail = replaced.target as EmbyServerDestination.Detail
    assertEquals(2, stack.size)
    assertEquals(ref, replaced.serverRef)
    assertEquals(gateway.visitId, replaced.visitId)
    assertEquals(origin.detailEntryId, detail.detailEntryId)
    assertEquals("episode-2", detail.itemId)
    assertEquals("Title episode-2", detail.title)
    assertEquals(key, embyNavigationContentKey(replaced, replaced))
    val payload = Json.encodeToString(EmbyServerGateway.serializer(), replaced)
    listOf("password", "apiToken", "baseUrl", "host", "detailOrigin").forEach { assertFalse(payload.contains(it)) }
  }

  @Test fun rapidLoadedEpisodesUseLastAcceptedItemWithoutGrowingTheStack() {
    val stack = stack(home, initial)
    val key = embyNavigationContentKey(initial, home, stack.lastIndex)
    for (index in 2..20) {
      assertTrue(follow(stack, spec("episode-$index")))
      assertEquals("episode-$index", (stack.last() as EmbyDetailScreen).itemId)
      assertEquals(key, embyNavigationContentKey(stack.last(), home, stack.lastIndex))
      assertEquals(3, stack.size)
    }
  }

  @Test fun firstLoadedObservationCanAlreadyBeALaterEpisode() {
    val stack = stack(home, initial)
    assertTrue(follow(stack, spec("episode-8")))
    assertTrue(follow(stack, spec("episode-10")))
    assertEquals("episode-10", (stack.last() as EmbyDetailScreen).itemId)
  }

  @Test fun queueCanMoveBackwardsBecauseLoadedEventsNotEpisodeNumbersAreAuthoritative() {
    val stack = stack(home, initial)
    assertTrue(follow(stack, spec("episode-3")))
    assertTrue(follow(stack, spec("episode-2")))
    assertTrue(follow(stack, spec(origin.itemId)))
    assertEquals(initial.itemId, (stack.last() as EmbyDetailScreen).itemId)
  }

  @Test fun nearestGatewayWinsEvenWhenOlderGatewayAndCompatibilityMatchPlayback() {
    val other = EmbyServerGateway(ref.copy(remoteId = "server-B"), "visit-B", EmbyServerHomeScreen)
    val stack = stack(home, other, initial)
    assertRejected(stack, compatibility = home)
    assertTrue(follow(stack, spec().copy(server = server.copy(remoteId = "server-B"))))
  }

  @Test fun nearestTopGatewayCannotBorrowAMatchingOlderVisit() {
    val other = EmbyServerGateway(ref.copy(sourceKey = "source-B"), "visit-B", initial)
    assertRejected(stack(home, other), compatibility = home)
  }

  @Test fun serverIdentityIncludesSourceRevisionAndRemoteIdNotLocalRowId() {
    listOf(server.copy(sourceKey = "source-B"), server.copy(sourceRevision = 4), server.copy(remoteId = "server-B"))
      .forEach { assertRejected(stack(home, initial), spec().copy(server = it)) }
    assertTrue(follow(stack(home, initial), spec().copy(server = server.copy(id = 99))))
  }

  @Test fun invalidServerReferenceFailsEvenWhenGatewayHasTheSameInvalidReference() {
    val invalidServers = listOf(
      server.copy(sourceKey = ""),
      server.copy(remoteId = ""),
      server.copy(sourceRevision = -1),
    )
    invalidServers.forEach { invalid ->
      val gateway = home.copy(serverRef = EmbyServerRef.from(invalid))
      assertRejected(stack(gateway, initial), spec().copy(server = invalid))
    }
  }

  @Test fun liveCurrentCheckReceivesPlaybackServerAndRejectsWithoutPoisoningState() {
    val stack = stack(home, initial)
    var checked: EmbyServer? = null
    assertFalse(follower.followPlaybackDetail(stack, spec(), null) { checked = it; false })
    assertSame(server, checked)
    assertSame(initial, stack.last())
    assertTrue(follow(stack))
    assertRejected(stack, spec("episode-3"), current = false)
    assertTrue(follow(stack, spec("episode-3")))
  }

  @Test fun staleEntryIdNeverChangesANewerEntryWithTheSameMediaId() {
    assertRejected(stack(home, initial.copy(detailEntryId = "new-entry")))
  }

  @Test fun nonDetailTopsAndHiddenDetailsNeverBecomePushFallbacks() {
    listOf<Screen>(MainScreen, EmbyServerHomeScreen, EmbyPersonScreen("person", "Person"), home,
      EmbyServerGateway(ref, "person-visit", EmbyPersonScreen("person", "Person")))
      .forEach { assertRejected(stack(home, initial, it), compatibility = home) }
    assertRejected(mutableListOf())
  }

  @Test fun poppingOverlayAllowsLatestLoadedEpisodeToFollowOnlyTheMatchingTopEntry() {
    val stack = stack(home, initial)
    assertTrue(follow(stack))
    stack += EmbyPersonScreen("person", "Person")
    assertRejected(stack, spec("episode-4"))
    stack.removeAt(stack.lastIndex)
    assertTrue(follow(stack, spec("episode-4")))
  }

  @Test fun missingOriginWrongTypeAndBlankIdsAreRejectedWithoutNavigation() {
    val stack = stack(home, initial)
    assertRejected(stack, spec(origin = null))
    listOf(null, "", "Movie", "Series", "Season", "Playlist", "TvChannel").forEach { type ->
      assertRejected(stack, spec().copy(itemType = type))
    }
    assertRejected(stack, spec(" "))
    assertRejected(stack, spec(origin = PlaybackDetailOrigin(" ", initial.itemId)))
    assertRejected(stack, spec(origin = PlaybackDetailOrigin(initial.detailEntryId, " ")))
    assertTrue(follow(stack, spec().copy(itemType = "episode")))
  }

  @Test fun manualSwitchBeforeFirstObservationCannotBeHijackedByReplay() {
    val stack = stack(home, initial.copy(itemId = "manual-episode"))
    assertRejected(stack)
    // Returning to the initial item does not re-arm an origin already observed as manually changed.
    stack[stack.lastIndex] = initial
    assertRejected(stack, spec("episode-3"))
  }

  @Test fun manualSwitchAfterSameItemObservationDisarmsEvenWhenReplayMatchesManualItem() {
    val stack = stack(home, initial)
    assertRejected(stack, spec(origin.itemId))
    stack[stack.lastIndex] = initial.copy(itemId = "episode-2")
    assertRejected(stack)
    assertRejected(stack, spec("episode-3"))
    stack[stack.lastIndex] = initial
    assertRejected(stack)
  }

  @Test fun manualSwitchAfterAutomaticFollowPermanentlyRejectsThatQueue() {
    val stack = stack(home, initial)
    assertTrue(follow(stack))
    val accepted = stack.last()
    stack[stack.lastIndex] = initial.copy(itemId = "manual-episode")
    assertRejected(stack, spec("episode-3"))
    stack[stack.lastIndex] = accepted
    assertRejected(stack, spec("episode-4"))
  }

  @Test fun manualSwitchOfGatewayDetailIsAlsoRejected() {
    val stack = stack(EmbyServerGateway(ref, "visit-A", initial))
    assertTrue(follow(stack))
    val gateway = stack.last() as EmbyServerGateway
    val detail = gateway.target as EmbyServerDestination.Detail
    stack[stack.lastIndex] = gateway.copy(target = detail.copy(itemId = "manual-episode"))
    assertRejected(stack, spec("episode-3"))
  }

  @Test fun newQueueOriginReferenceCanRearmManuallyChangedStableEntry() {
    val stack = stack(home, initial)
    assertTrue(follow(stack))
    stack[stack.lastIndex] = initial.copy(itemId = "manual-episode")
    assertRejected(stack, spec("episode-3"))
    val newOrigin = PlaybackDetailOrigin(origin.detailEntryId, "manual-episode")
    assertTrue(follow(stack, spec("episode-5", newOrigin)))
    // Replay of a previously matched queue neither hijacks nor disarms the new one.
    assertRejected(stack, spec("episode-3"))
    assertTrue(follow(stack, spec("episode-6", newOrigin)))
  }

  @Test fun equalOriginFieldsWithNewReferenceStillRepresentANewQueue() {
    val stack = stack(home, initial)
    assertRejected(stack, spec(initial.itemId))
    val newOrigin = PlaybackDetailOrigin(origin.detailEntryId, origin.itemId)
    assertRejected(stack, spec(initial.itemId, newOrigin))
    assertRejected(stack, spec())
    assertTrue(follow(stack, spec("episode-3", newOrigin)))
  }

  @Test fun unrelatedOriginOrServerReplaysCannotResetTheLastAcceptedItem() {
    val stack = stack(home, initial)
    assertTrue(follow(stack))
    assertRejected(stack, spec("episode-9", PlaybackDetailOrigin("unrelated-entry", "episode-9")))
    assertRejected(stack, spec(origin = PlaybackDetailOrigin(origin.detailEntryId, "episode-9"))
      .copy(server = server.copy(sourceRevision = 4)))
    assertTrue(follow(stack, spec("episode-3")))
  }

  @Test fun compatibilityGatewayIsUsedOnlyForDescendantsOfTheNearestSingletonHome() {
    val stack = stack(EmbyServerHomeScreen, EmbyPersonScreen("person", "Person"), initial)
    val key = embyNavigationContentKey(initial, home, stack.lastIndex)
    assertTrue(follow(stack, compatibility = home))
    assertEquals(key, embyNavigationContentKey(stack.last(), home, stack.lastIndex))
    assertEquals(4, stack.size)
  }

  @Test fun compatibilityCannotBindAnUnrelatedDetailOrCrossANewLayerRoot() {
    assertRejected(stack(initial), compatibility = home)
    assertRejected(stack(EmbyServerHomeScreen, MainScreen, initial), compatibility = home)
    assertRejected(stack(EmbyServerHomeScreen, initial), compatibility = null)
    val wrongTarget = home.copy(target = EmbyServerDestination.Detail("other", "Other", "other-entry"))
    assertRejected(stack(EmbyServerHomeScreen, initial), compatibility = wrongTarget)
    val wrongServer = home.copy(serverRef = ref.copy(remoteId = "B"))
    assertRejected(stack(EmbyServerHomeScreen, initial), compatibility = wrongServer)
  }

  @Test fun singletonHomeBoundaryDoesNotBorrowAnOlderGateway() {
    val stack = stack(home, EmbyServerHomeScreen, initial)
    assertRejected(stack)
    assertTrue(follow(stack, compatibility = home))
  }

  @Test fun retainedControllerRebasesOriginalAndIntermediateRecreatedSnapshotsToLatestLoadedItem() {
    val originalStack = stack(home, initial)
    assertTrue(follow(originalStack))
    val intermediate = (originalStack.last() as EmbyDetailScreen).copy()
    assertTrue(follow(originalStack, spec("episode-3")))
    val restoredInitial = stack(home.copy(), initial.copy())
    assertTrue(follow(restoredInitial, spec("episode-3")))
    val restoredIntermediate = stack(home.copy(), intermediate)
    assertTrue(follow(restoredIntermediate, spec("episode-4")))
    assertEquals("episode-4", (restoredIntermediate.last() as EmbyDetailScreen).itemId)
    assertRejected(restoredIntermediate, spec("episode-4"))
  }

  @Test fun retainedControllerRejectsUnknownRecreatedItemAndCannotRearmDisarmedOrigin() {
    val originalStack = stack(home, initial)
    assertTrue(follow(originalStack))
    val unknownRestoration = stack(home.copy(), initial.copy(itemId = "unknown-episode"))
    assertRejected(unknownRestoration, spec("episode-3"))
    assertRejected(stack(home.copy(), initial.copy()), spec("episode-3"))
    assertRejected(originalStack, spec("episode-3"))
  }

  @Test fun sameStackSwitchToKnownOriginalOrAcceptedItemStillDisarms() {
    val stack = stack(home, initial)
    assertTrue(follow(stack))
    val accepted = stack.last()
    assertTrue(follow(stack, spec("episode-3")))
    stack[stack.lastIndex] = accepted
    assertRejected(stack, spec("episode-4"))
    assertRejected(stack(home.copy(), initial.copy()), spec("episode-4"))
  }

  @Test fun failedCurrentGateCannotAttachOrRebaseARecreatedStack() {
    val originalStack = stack(home, initial)
    assertTrue(follow(originalStack))
    val restored = stack(home.copy(), initial.copy())
    assertRejected(restored, spec("episode-3"), current = false)
    assertTrue(follow(restored, spec("episode-3")))
  }

  @Test fun oldIntermediateOutsideBoundedAcceptedWindowFailsClosedButOriginalRemainsKnown() {
    val stack = stack(home, initial)
    for (index in 2..132) assertTrue(follow(stack, spec("episode-$index")))
    assertTrue(follow(stack(home.copy(), initial.copy()), spec("episode-132")))
    assertRejected(stack(home.copy(), initial.copy(itemId = "episode-2")), spec("episode-133"))
  }

  @Test fun replacementTitleUsesPlaybackTitleThenItemNameThenItemId() {
    val stack = stack(home, initial)
    assertTrue(follow(stack, spec().copy(title = " ", itemName = "Named episode")))
    assertEquals("Named episode", (stack.last() as EmbyDetailScreen).title)
    assertTrue(follow(stack, spec("episode-3").copy(title = "", itemName = "")))
    assertEquals("episode-3", (stack.last() as EmbyDetailScreen).title)
  }
}
