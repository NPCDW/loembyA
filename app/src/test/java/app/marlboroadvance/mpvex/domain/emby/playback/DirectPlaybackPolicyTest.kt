package app.marlboroadvance.mpvex.domain.emby.playback

import app.marlboroadvance.mpvex.domain.emby.EmbyMediaSource
import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.ui.browser.emby.forLaunchQueueEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class DirectPlaybackPolicyTest {
  private val server = EmbyServer(name = "fixture", host = "example.org", username = "", password = "")
  private val remote = EmbyMediaSource(
    Id = "source",
    Container = "mkv",
    IsRemote = true,
    Path = "https://media.example.org/video.mkv",
    DirectStreamUrl = "/Videos/item/stream.mkv",
  )

  @Test
  fun `ordinary playback uses Emby stream while explicit direct playback uses approved remote source`() {
    val ordinary = PlaybackSpec(server, "item")
    assertEquals("/emby/Videos/item/stream.mkv", LoembyMediaSelection.selectedVideoUrl(ordinary, remote, "session"))
    assertEquals(
      remote.Path,
      LoembyMediaSelection.selectedVideoUrl(ordinary.copy(useDirectLink = true), remote, "session"),
    )
  }

  @Test
  fun `launch choice affects current and following items without bypassing internal source protection`() {
    for (direct in listOf(false, true)) {
      for (isCurrent in listOf(false, true)) {
        val entry = PlaybackSpec(server, "item", useDirectLink = direct)
          .forLaunchQueueEntry(isCurrent, selectionCurrentItemOnly = true)
        val expected = if (direct) remote.Path else "/emby/Videos/item/stream.mkv"
        assertEquals(expected, LoembyMediaSelection.selectedVideoUrl(entry, remote, "session"))
        assertEquals(
          "/emby/Videos/item/stream.mkv",
          LoembyMediaSelection.selectedVideoUrl(entry, remote.copy(Path = "http://192.168.1.2/video.mkv"), "session"),
        )
      }
    }
  }
}
