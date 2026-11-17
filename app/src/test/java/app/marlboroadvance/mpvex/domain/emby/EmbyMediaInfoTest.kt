package app.marlboroadvance.mpvex.domain.emby

import app.marlboroadvance.mpvex.domain.emby.playback.LoembyMediaSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbyMediaInfoTest {
  private fun source(
    id: String?,
    width: Int,
    height: Int,
    size: Long,
    bitrate: Long = 0,
    ticks: Long = EmbyTicks.secondsToTicks(120),
  ) = EmbyMediaSource(
    Id = id,
    Name = id,
    Size = size,
    Bitrate = bitrate,
    RunTimeTicks = ticks,
    Path = "/media/$id.mkv",
    MediaStreams = listOf(EmbyMediaStream(Type = "Video", Width = width, Height = height)),
  )

  private val sources = listOf(
    source("small", 1280, 720, 10, bitrate = 9_000_000),
    source("high-resolution", 3840, 2160, 20, bitrate = 1_000_000),
    source("largest", 1920, 1080, 30, bitrate = 500_000),
  )
  private val item = EmbyItem(Id = "media", MediaSources = sources)

  @Test
  fun automaticDetailSourceUsesExactlySharedPlaybackRanking() {
    for (policy in listOf("high-resolution", "high-bitrate", "unknown")) {
      val playbackSource = sources[LoembyMediaSelection.selectAutomaticSourceIndex(sources, policy)]
      assertEquals(playbackSource.Id, effectiveEmbyMediaSourceId(item, null, policy))
    }
    assertEquals("high-resolution", effectiveEmbyMediaSourceId(item, null, "high-resolution"))
    // loemby's high-bitrate policy ranks by file size, not the source Bitrate field.
    assertEquals("largest", effectiveEmbyMediaSourceId(item, null, "high-bitrate"))
    assertEquals("small", effectiveEmbyMediaSourceId(item, null, "unknown"))
  }

  @Test
  fun validManualSelectionWinsAndStaleSelectionReappliesSharedPolicy() {
    assertEquals("small", effectiveEmbyMediaSourceId(item, "small", "high-resolution"))
    assertEquals("largest", effectiveEmbyMediaSourceId(item, "missing", "high-bitrate"))
    assertEquals("high-resolution", effectiveEmbyMediaSourceId(item, "", "high-resolution"))
  }

  @Test
  fun emptyAndSingleSourcesNeedNoExplicitVersionId() {
    assertNull(effectiveEmbyMediaSourceId(null, null))
    assertNull(effectiveEmbyMediaSourceId(EmbyItem(), null))
    assertNull(effectiveEmbyMediaSourceId(item.copy(MediaSources = listOf(sources.first())), null))
  }

  @Test
  fun missingIdDoesNotChangeAutomaticCandidateRanking() {
    val highest = source(null, 7680, 4320, 40)
    val withoutId = item.copy(MediaSources = sources + highest)
    assertNull(effectiveEmbyMediaSourceId(withoutId, null, "high-resolution"))
    val rows = buildEmbyMediaInfoEntries(withoutId, automaticVersionPolicy = "high-resolution")
      .filterIsInstance<MediaInfoEntry.Row>()
    assertEquals("7680×4320", rows.first { it.label == "分辨率" }.value)
  }

  @Test
  fun mediaInfoAndDurationFollowSharedOrManualSource() {
    val longer = sources.last().copy(RunTimeTicks = EmbyTicks.secondsToTicks(300))
    val versions = item.copy(MediaSources = sources.dropLast(1) + longer)
    fun value(policy: String, selected: String?, label: String): String =
      buildEmbyMediaInfoEntries(versions, selected, policy).filterIsInstance<MediaInfoEntry.Row>()
        .first { it.label == label }.value

    assertEquals("/media/largest.mkv", value("high-bitrate", null, "文件路径"))
    assertEquals(formatEmbyDurationFull(longer.RunTimeTicks!!), value("high-bitrate", null, "时长"))
    assertEquals("/media/small.mkv", value("high-bitrate", "small", "文件路径"))
    assertEquals("3840×2160", value("high-resolution", "missing", "分辨率"))
  }
}
