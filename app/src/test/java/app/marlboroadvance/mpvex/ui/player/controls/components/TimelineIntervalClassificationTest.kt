package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.ui.graphics.Color
import app.marlboroadvance.mpvex.ui.player.CacheTimeRange
import app.marlboroadvance.mpvex.ui.player.CacheTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineIntervalClassificationTest {
  @Test
  fun `track splits only at cache boundaries`() {
    assertEquals(
      listOf(
        interval(0.0, 20.0, TimelineIntervalKind.Uncached),
        interval(20.0, 40.0, TimelineIntervalKind.Cached),
        interval(40.0, 70.0, TimelineIntervalKind.Uncached),
        interval(70.0, 90.0, TimelineIntervalKind.Cached),
        interval(90.0, 100.0, TimelineIntervalKind.Uncached),
      ),
      classifyTimelineIntervals(timeline(), 100.0),
    )
  }

  @Test
  fun `fully cached or fully uncached track has one interval`() {
    val cached = CacheTimeline(60.0, listOf(CacheTimeRange(0.0, 60.0)), emptyList(), true, true)
    assertEquals(
      listOf(interval(0.0, 60.0, TimelineIntervalKind.Cached)),
      classifyTimelineIntervals(cached, 60.0),
    )
    assertEquals(
      listOf(interval(0.0, 60.0, TimelineIntervalKind.Uncached)),
      classifyTimelineIntervals(CacheTimeline.fromMpv(null, 60.0), 60.0),
    )
  }

  @Test
  fun `intervals cover the whole duration without gaps overlaps or empty segments`() {
    val intervals = classifyTimelineIntervals(timeline(), 100.0)
    assertTrue(intervals.all { it.startSeconds < it.endSeconds })
    assertEquals(0.0, intervals.first().startSeconds, 0.0)
    assertEquals(100.0, intervals.last().endSeconds, 0.0)
    intervals.zipWithNext().forEach { (left, right) ->
      assertEquals(left.endSeconds, right.startSeconds, 0.0)
      assertTrue(left.kind != right.kind)
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
        interval(0.0, 20.0, TimelineIntervalKind.Uncached),
        interval(20.0, 40.0, TimelineIntervalKind.Cached),
        interval(40.0, 60.0, TimelineIntervalKind.Uncached),
      ),
      classifyTimelineIntervals(snapshot, 60.0),
    )
    assertEquals(
      listOf(
        interval(0.0, 20.0, TimelineIntervalKind.Uncached),
        interval(20.0, 40.0, TimelineIntervalKind.Cached),
        interval(40.0, 70.0, TimelineIntervalKind.Uncached),
        interval(70.0, 100.0, TimelineIntervalKind.Cached),
      ),
      classifyTimelineIntervals(snapshot, 100.0),
    )
  }

  @Test
  fun `invalid overlapping and touching cached ranges do not produce extra seams`() {
    val snapshot = CacheTimeline(
      null,
      listOf(
        CacheTimeRange(40.0, 70.0),
        CacheTimeRange(Double.NaN, 10.0),
        CacheTimeRange(20.0, 50.0),
        CacheTimeRange(25.0, 35.0),
        CacheTimeRange(70.0, 120.0),
        CacheTimeRange(5.0, 5.0),
        CacheTimeRange(10.0, 5.0),
        CacheTimeRange(10.0, Double.POSITIVE_INFINITY),
      ),
      emptyList(),
      false,
      false,
    )
    assertEquals(
      listOf(
        interval(0.0, 20.0, TimelineIntervalKind.Uncached),
        interval(20.0, 100.0, TimelineIntervalKind.Cached),
      ),
      classifyTimelineIntervals(snapshot, 100.0),
    )
  }

  @Test
  fun `cache eviction removes cached coloring from the whole track`() {
    val evicted = classifyTimelineIntervals(CacheTimeline.fromMpv(null, 100.0), 100.0)
    assertEquals(listOf(interval(0.0, 100.0, TimelineIntervalKind.Uncached)), evicted)
    assertTrue(evicted.all { CacheTimelineColors.colorFor(it.kind) == CacheTimelineColors.uncached })
  }

  @Test
  fun `production palette uses exactly two whites based only on cache membership`() {
    assertEquals(Color.White, CacheTimelineColors.colorFor(TimelineIntervalKind.Cached))
    assertEquals(Color.White.copy(alpha = 0.20f), CacheTimelineColors.colorFor(TimelineIntervalKind.Uncached))
    assertEquals(2, TimelineIntervalKind.entries.map(CacheTimelineColors::colorFor).distinct().size)
    assertTrue(CacheTimelineColors.uncached.alpha < CacheTimelineColors.cached.alpha)

    val intervals = classifyTimelineIntervals(timeline(), 100.0)
    for (time in listOf(0.0, 10.0, 30.0, 50.0, 85.0, 95.0)) {
      val kind = intervals.single { time >= it.startSeconds && time < it.endSeconds }.kind
      val isCached = timeline().cachedRanges.any { time >= it.startSeconds && time < it.endSeconds }
      assertEquals(
        if (isCached) CacheTimelineColors.cached else CacheTimelineColors.uncached,
        CacheTimelineColors.colorFor(kind),
      )
    }
  }

  @Test
  fun `invalid durations have no drawable intervals`() {
    for (duration in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
      assertTrue(classifyTimelineIntervals(timeline(), duration).isEmpty())
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
