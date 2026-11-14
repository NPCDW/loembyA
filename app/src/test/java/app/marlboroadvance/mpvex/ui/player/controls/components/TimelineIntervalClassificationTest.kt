package app.marlboroadvance.mpvex.ui.player.controls.components

import app.marlboroadvance.mpvex.ui.player.CacheTimeRange
import app.marlboroadvance.mpvex.ui.player.CacheTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineIntervalClassificationTest {
  @Test
  fun `cached interval crossing playhead splits into played and unplayed`() {
    assertEquals(
      listOf(
        interval(0.0, 20.0, TimelineIntervalKind.PlayedUncached),
        interval(20.0, 30.0, TimelineIntervalKind.PlayedCached),
        interval(30.0, 40.0, TimelineIntervalKind.UnplayedCached),
        interval(40.0, 70.0, TimelineIntervalKind.UnplayedUncached),
        interval(70.0, 90.0, TimelineIntervalKind.UnplayedCached),
        interval(90.0, 100.0, TimelineIntervalKind.UnplayedUncached),
      ),
      classifyTimelineIntervals(timeline(), 100.0, 30.0),
    )
  }

  @Test
  fun `uncached interval crossing playhead splits without changing cache membership`() {
    assertEquals(
      listOf(
        interval(0.0, 20.0, TimelineIntervalKind.PlayedUncached),
        interval(20.0, 40.0, TimelineIntervalKind.PlayedCached),
        interval(40.0, 50.0, TimelineIntervalKind.PlayedUncached),
        interval(50.0, 70.0, TimelineIntervalKind.UnplayedUncached),
        interval(70.0, 90.0, TimelineIntervalKind.UnplayedCached),
        interval(90.0, 100.0, TimelineIntervalKind.UnplayedUncached),
      ),
      classifyTimelineIntervals(timeline(), 100.0, 50.0),
    )
  }

  @Test
  fun `reverse seeks reclassify from current playhead not accumulated played history`() {
    val timeline = timeline()
    classifyTimelineIntervals(timeline, 100.0, 85.0)
    val reversed = classifyTimelineIntervals(timeline, 100.0, 0.0)
    assertTrue(reversed.all {
      it.kind == TimelineIntervalKind.UnplayedCached || it.kind == TimelineIntervalKind.UnplayedUncached
    })
    assertEquals(
      listOf(interval(0.0, 60.0, TimelineIntervalKind.PlayedUncached)),
      classifyTimelineIntervals(CacheTimeline.fromMpv(null, 60.0), 60.0, 60.0),
    )
  }

  @Test
  fun `fully cached or fully uncached still split at playhead`() {
    val cached = CacheTimeline(60.0, listOf(CacheTimeRange(0.0, 60.0)), emptyList(), true, true)
    assertEquals(
      listOf(
        interval(0.0, 30.0, TimelineIntervalKind.PlayedCached),
        interval(30.0, 60.0, TimelineIntervalKind.UnplayedCached),
      ),
      classifyTimelineIntervals(cached, 60.0, 30.0),
    )
    assertEquals(
      listOf(
        interval(0.0, 30.0, TimelineIntervalKind.PlayedUncached),
        interval(30.0, 60.0, TimelineIntervalKind.UnplayedUncached),
      ),
      classifyTimelineIntervals(CacheTimeline.fromMpv(null, 60.0), 60.0, 30.0),
    )
  }

  @Test
  fun `positions clamp and exact boundaries never emit empty intervals`() {
    for (position in listOf(20.0, 40.0, 70.0, 90.0, 100.0, -1.0, 200.0, Double.NaN)) {
      val intervals = classifyTimelineIntervals(timeline(), 100.0, position)
      assertTrue(intervals.all { it.startSeconds < it.endSeconds })
      assertEquals(0.0, intervals.first().startSeconds, 0.0)
      assertEquals(100.0, intervals.last().endSeconds, 0.0)
      intervals.zipWithNext().forEach { (left, right) -> assertEquals(left.endSeconds, right.startSeconds, 0.0) }
    }
  }

  @Test
  fun `duration changes use only present cached ranges and derive a fresh complement`() {
    val snapshot = CacheTimeline(
      null,
      listOf(CacheTimeRange(20.0, 40.0), CacheTimeRange(70.0, 120.0)),
      emptyList(),
      false,
      false,
    )
    assertEquals(
      listOf(
        interval(0.0, 10.0, TimelineIntervalKind.PlayedUncached),
        interval(10.0, 20.0, TimelineIntervalKind.UnplayedUncached),
        interval(20.0, 40.0, TimelineIntervalKind.UnplayedCached),
        interval(40.0, 60.0, TimelineIntervalKind.UnplayedUncached),
      ),
      classifyTimelineIntervals(snapshot, 60.0, 10.0),
    )
  }

  @Test
  fun `cache eviction removes cached coloring on both sides of playhead`() {
    classifyTimelineIntervals(timeline(), 100.0, 30.0)
    val evicted = classifyTimelineIntervals(CacheTimeline.fromMpv(null, 100.0), 100.0, 30.0)
    assertEquals(
      listOf(
        interval(0.0, 30.0, TimelineIntervalKind.PlayedUncached),
        interval(30.0, 100.0, TimelineIntervalKind.UnplayedUncached),
      ),
      evicted,
    )
  }

  @Test
  fun `all four states have distinct color lookup`() {
    val colors = TimelineColorPalette("played-cached", "played-uncached", "unplayed-cached", "unplayed-uncached")
    assertEquals("played-cached", colors.colorFor(TimelineIntervalKind.PlayedCached))
    assertEquals("played-uncached", colors.colorFor(TimelineIntervalKind.PlayedUncached))
    assertEquals("unplayed-cached", colors.colorFor(TimelineIntervalKind.UnplayedCached))
    assertEquals("unplayed-uncached", colors.colorFor(TimelineIntervalKind.UnplayedUncached))
    assertEquals(4, TimelineIntervalKind.entries.map(colors::colorFor).distinct().size)
  }

  @Test
  fun `invalid durations have no drawable intervals`() {
    val timeline = CacheTimeline(null, emptyList(), emptyList(), false, false)
    for (duration in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
      assertTrue(classifyTimelineIntervals(timeline, duration, 0.0).isEmpty())
    }
  }

  private fun timeline() = CacheTimeline(
    durationSeconds = 100.0,
    cachedRanges = listOf(CacheTimeRange(20.0, 40.0), CacheTimeRange(70.0, 90.0)),
    uncachedRanges = listOf(CacheTimeRange(0.0, 20.0), CacheTimeRange(40.0, 70.0), CacheTimeRange(90.0, 100.0)),
    bofCached = false,
    eofCached = false,
  )

  private fun interval(start: Double, end: Double, kind: TimelineIntervalKind) =
    TimelineClassifiedInterval(start, end, kind)
}
