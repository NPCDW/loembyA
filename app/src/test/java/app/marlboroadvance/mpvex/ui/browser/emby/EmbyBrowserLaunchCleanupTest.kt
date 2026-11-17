package app.marlboroadvance.mpvex.ui.browser.emby

import app.marlboroadvance.mpvex.domain.emby.EmbyItem
import app.marlboroadvance.mpvex.domain.emby.EmbyMediaSource
import app.marlboroadvance.mpvex.domain.emby.EmbyMediaStream
import app.marlboroadvance.mpvex.domain.emby.EmbyPrimaryImageRef
import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.domain.emby.playback.LoembyMediaSelection
import app.marlboroadvance.mpvex.domain.emby.playback.PlaybackSpec
import app.marlboroadvance.mpvex.domain.emby.primaryImageRef
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyBrowserLaunchCleanupTest {
  private val server = EmbyServer(name = "test", host = "example.org", username = "", password = "")

  @Test fun ordinaryPlayDoesNotDefaultToDirectLink() {
    assertFalse(PlaybackSpec(server, "item").useDirectLink)
  }

  @Test fun directLinkChoiceAppliesToEveryQueueEntryRegardlessOfSelectionScope() {
    for (direct in listOf(false, true)) {
      for (currentOnly in listOf(false, true)) {
        val entries = (0..2).map { index ->
          PlaybackSpec(
            server = server,
            itemId = "item-$index",
            useDirectLink = direct,
            selectPolicy = "manual",
            versionSelect = 2,
            videoSelect = 2,
            audioSelect = 3,
            subtitleSelect = -1,
          ).forLaunchQueueEntry(isCurrent = index == 1, selectionCurrentItemOnly = currentOnly)
        }
        assertEquals(listOf(direct, direct, direct), entries.map { it.useDirectLink })
        assertEquals(listOf("auto", "manual", "auto"), entries.map { it.selectPolicy })
        assertEquals(listOf(1, 2, 1), entries.map { it.versionSelect })
        assertEquals(listOf(0, 2, 0), entries.map { it.videoSelect })
        assertEquals(listOf(0, 3, 0), entries.map { it.audioSelect })
        assertEquals(listOf(0, -1, 0), entries.map { it.subtitleSelect })
      }
    }
  }

  @Test fun clickedExplicitVersionStaysScopedWhileAutomaticEntriesUseSharedPolicy() {
    val selected = PlaybackSpec(
      server = server,
      itemId = "clicked",
      selectPolicy = "manual",
      versionSelect = 1,
      explicitMediaSourceId = "large",
      useDirectLink = true,
    )
    val current = selected.forLaunchQueueEntry(isCurrent = true, selectionCurrentItemOnly = true)
    val other = selected.forLaunchQueueEntry(isCurrent = false, selectionCurrentItemOnly = false)
    assertEquals("large", current.explicitMediaSourceId)
    assertNull(other.explicitMediaSourceId)
    val sources = listOf(
      EmbyMediaSource(
        Id = "large",
        Size = 2_000,
        MediaStreams = listOf(EmbyMediaStream(Type = "Video", Width = 1280, Height = 720)),
      ),
      EmbyMediaSource(
        Id = "sharp",
        Size = 1_000,
        MediaStreams = listOf(EmbyMediaStream(Type = "Video", Width = 3840, Height = 2160)),
      ),
    )
    assertEquals(0, LoembyMediaSelection.selectSourceIndex(sources, current, autoPolicy = "high-resolution"))
    assertEquals(1, LoembyMediaSelection.selectSourceIndex(sources, other, autoPolicy = "high-resolution"))
    assertEquals(0, LoembyMediaSelection.selectSourceIndex(sources, other, autoPolicy = "high-bitrate"))
    assertTrue(current.useDirectLink)
    assertTrue(other.useDirectLink)
  }

  @Test fun automaticClickedSelectionDoesNotFreezeAStaleExplicitVersion() {
    val automatic = PlaybackSpec(
      server = server,
      itemId = "clicked",
      selectPolicy = "auto",
      explicitMediaSourceId = "stale",
      versionSelect = 2,
      useDirectLink = false,
    ).forLaunchQueueEntry(isCurrent = true, selectionCurrentItemOnly = true)
    assertNull(automatic.explicitMediaSourceId)
    assertEquals("auto", automatic.selectPolicy)
    assertFalse(automatic.useDirectLink)
  }

  @Test fun containersUseOwnOrServerSuppliedPrimaryImageWithoutChildSampling() {
    for (type in listOf("Folder", "CollectionFolder", "UserView", "BoxSet", "PhotoAlbum")) {
      val item = EmbyItem(Id = "container", Type = type)
      assertEquals(EmbyPrimaryImageRef("container", null), item.primaryImageRef())
      assertEquals(
        EmbyPrimaryImageRef("image-owner", "borrowed-tag"),
        item.copy(PrimaryImageItemId = "image-owner", PrimaryImageTag = "borrowed-tag").primaryImageRef(),
      )
      assertEquals(
        EmbyPrimaryImageRef("container", "own-tag"),
        item.copy(
          ImageTags = mapOf("Primary" to "own-tag"),
          PrimaryImageItemId = "image-owner",
          PrimaryImageTag = "borrowed-tag",
        ).primaryImageRef(),
      )
      assertEquals(EmbyPrimaryImageRef("container", null), item.copy(PrimaryImageItemId = " ").primaryImageRef())
    }
    assertNull(EmbyItem(PrimaryImageItemId = "image-owner").primaryImageRef())
  }

  @Test fun deletedBrowserEntrypointsAndPreferencesHaveNoRuntimeCallers() {
    val removed = listOf(
      "startRandomPlayback", "startRandomPhotoPlayback", "startFeedPlayback", "loadRandomForView",
      "EmbyGachaDrawDialog", "GachaCard", "EmbyFavoriteRandomIcon", "VideoFeedIcon", "ui.player.feed",
      "randomPlayCount", "embyGachaDrawCount", "embyGachaStyle", "embyRandomDrillDownOnTap", "embyRandomGestureToast",
    )
    for (path in browserScreens) {
      val text = source(path)
      for (symbol in removed) assertFalse("$path still references $symbol", text.contains(symbol))
    }
    for (name in listOf("EmbyGachaDrawDialog.kt", "EmbyFavoriteRandomIcon.kt", "VideoFeedIcon.kt")) {
      assertFalse("Exclusive component $name still exists", sourceFile("$browser/components/$name").exists())
    }
  }

  @Test fun directImageRenderingDoesNotQueryOrCacheSyntheticCovers() {
    val paths = listOf(
      "$browser/EmbyLibraryScreen.kt",
      "$browser/EmbyViewModel.kt",
      "$browser/components/EmbyMediaCard.kt",
    )
    val removed = listOf(
      "loadFolderCoverBatch", "loadFolderCoverUrls", "folderCoverUrlFor", "EmbyFolderCoverCache",
      "needsFolderMosaic", "FolderMosaicCover", "MosaicTiles", "mosaicUrls", "prewarmMosaicTile",
      "embyFolderRandomCover", "embyFolderCover", "shouldResynthesizeCover",
    )
    for (path in paths) {
      val text = source(path)
      for (symbol in removed) assertFalse("$path still references $symbol", text.contains(symbol))
    }
    val cell = source("$browser/EmbyLibraryScreen.kt")
      .substringAfter("private fun EmbyLibraryMediaCell(").substringBefore("private fun EmbyCompactRow(")
    assertTrue(cell.contains("viewModel.imageUrl(server, item, \"Primary\", 480)"))
    assertFalse(cell.contains("LaunchedEffect("))
    assertFalse(cell.contains("loadItems("))
  }

  @Test fun orderedQueuePlaybackAndRecommendationSamplingRemainAvailable() {
    val model = source("$browser/EmbyViewModel.kt")
    val ordered = model.substringAfter("fun launchFromStartPlayback(").substringBefore("fun launchPlaylist(")
    assertTrue(ordered.contains("launchPlaylist(server, items, playFromStartAll = true)"))
    assertTrue(ordered.contains("launchPlaylistAwait(server, items, playFromStartAll = true)"))
    assertFalse(ordered.contains("shuffled"))
    assertFalse(model.contains("launchRandomPlayback"))
    assertFalse(model.contains("fun loadRandom("))
    assertTrue(source("$browser/discover/SimilarRecommendationViewModel.kt").contains("repository.getRandomItems("))
    assertTrue(source("$browser/EmbyLibraryScreen.kt").contains("\"Random\" to \"随机\""))
    assertTrue(source("$browser/EmbyPersonScreen.kt").contains("list.shuffled(Random(seed))"))
    assertTrue(source("$browser/photo/EmbyPhotoGridScreen.kt").contains("\"Random\" to \"随机\""))
  }

  @Test fun queueRegistrationUsesLaunchOptionInsteadOfLocalToggleOrResolvedUrlMap() {
    val model = source("$browser/EmbyViewModel.kt")
    val queue = model.substringAfter("private suspend fun launchPlaylistAwait(").substringBefore("fun isContainerOnly(")
    assertTrue(queue.contains("useDirectLink = useDirectLink,"))
    assertTrue(queue.contains("forLaunchQueueEntry(index == startIndex, selectionCurrentItemOnly)"))
    assertFalse(model.contains("shouldResolveDirectLink"))
    assertFalse(model.contains("resolveDirectUrl"))
    assertFalse(model.contains("embyResolveDirectLink"))
    assertFalse(model.contains("directUrls"))
  }

  @Test fun browserPagingUsesNearEndDefaultsWithoutPredictivePreferenceReads() {
    assertTrue(source("$browser/EmbyLibraryScreen.kt").contains("lastVisible >= items.size - 2"))
    assertTrue(source("$browser/photo/EmbyPhotoGridScreen.kt").contains("last >= info.totalItemsCount - 2"))
    assertTrue(source("$browser/EmbyTagItemsScreen.kt").contains(
      "page.AutoLoadMore(source = gridSource, enabled = server != null, loader = loader)",
    ))
    for (path in browserScreens) assertFalse(source(path).contains("rememberPredictivePreloadDistance"))
  }

  private fun source(path: String): String = sourceFile(path).readText()

  private fun sourceFile(path: String): File = sequenceOf(File("src/main/java/$path"), File("app/src/main/java/$path"))
    .firstOrNull { it.isFile } ?: File("src/main/java/$path")

  private val browser = "app/marlboroadvance/mpvex/ui/browser/emby"
  private val browserScreens = listOf(
    "$browser/EmbyLibraryScreen.kt",
    "$browser/EmbyPersonScreen.kt",
    "$browser/EmbyTagItemsScreen.kt",
    "$browser/photo/EmbyPhotoGridScreen.kt",
  )
}
