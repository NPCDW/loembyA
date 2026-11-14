package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.player.CacheTimeline
import app.marlboroadvance.mpvex.ui.player.controls.LocalPlayerButtonsClickEvent
import app.marlboroadvance.mpvex.ui.theme.spacing
import `is`.xyz.mpv.Utils

/** Played means before the current playhead, not accumulated playback history. */
internal enum class TimelineIntervalKind {
  PlayedCached,
  PlayedUncached,
  UnplayedCached,
  UnplayedUncached,
}

internal data class TimelineClassifiedInterval(
  val startSeconds: Double,
  val endSeconds: Double,
  val kind: TimelineIntervalKind,
)

internal data class TimelineColorPalette<T>(
  val playedCached: T,
  val playedUncached: T,
  val unplayedCached: T,
  val unplayedUncached: T,
)

internal fun <T> TimelineColorPalette<T>.colorFor(kind: TimelineIntervalKind): T = when (kind) {
  TimelineIntervalKind.PlayedCached -> playedCached
  TimelineIntervalKind.PlayedUncached -> playedUncached
  TimelineIntervalKind.UnplayedCached -> unplayedCached
  TimelineIntervalKind.UnplayedUncached -> unplayedUncached
}

internal fun classifyTimelineIntervals(
  timeline: CacheTimeline,
  durationSeconds: Double,
  positionSeconds: Double,
): List<TimelineClassifiedInterval> {
  if (!durationSeconds.isFinite() || durationSeconds <= 0.0) return emptyList()
  val position = if (positionSeconds.isFinite()) positionSeconds.coerceIn(0.0, durationSeconds) else 0.0
  // Derive the complement against today's duration, not a possibly stale snapshot duration.
  // Sorting/clamping also handles snapshots captured before native duration became available.
  val cached = timeline.cachedRanges.filter { it.startSeconds.isFinite() && it.endSeconds.isFinite() }
    .mapNotNull { range ->
      val start = range.startSeconds.coerceIn(0.0, durationSeconds)
      val end = range.endSeconds.coerceIn(0.0, durationSeconds)
      if (start < end) start to end else null
    }.sortedBy { it.first }

  return buildList {
    fun interval(start: Double, end: Double, isCached: Boolean) {
      if (start >= end) return
      val playedEnd = minOf(end, position)
      if (start < playedEnd) {
        add(TimelineClassifiedInterval(
          start,
          playedEnd,
          if (isCached) TimelineIntervalKind.PlayedCached else TimelineIntervalKind.PlayedUncached,
        ))
      }
      val unplayedStart = maxOf(start, position)
      if (unplayedStart < end) {
        add(TimelineClassifiedInterval(
          unplayedStart,
          end,
          if (isCached) TimelineIntervalKind.UnplayedCached else TimelineIntervalKind.UnplayedUncached,
        ))
      }
    }
    var cursor = 0.0
    for ((start, end) in cached) {
      interval(cursor, start, isCached = false)
      interval(maxOf(cursor, start), end, isCached = true)
      cursor = maxOf(cursor, end)
    }
    interval(cursor, durationSeconds, isCached = false)
  }
}

@Composable
internal fun SeekbarWithTimers(
  position: Float,
  duration: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
  timersInverted: Pair<Boolean, Boolean>,
  positionTimerOnClick: () -> Unit,
  durationTimerOnCLick: () -> Unit,
  cacheTimeline: CacheTimeline,
  modifier: Modifier = Modifier,
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  val liveDuration by rememberUpdatedState(duration)
  var isInteracting by remember { mutableStateOf(false) }
  var userPosition by remember { mutableFloatStateOf(position) }
  val shownPosition = if (isInteracting) userPosition else position
  val progressDescription = stringResource(
    R.string.player_seek_progress_description,
    Utils.prettyTime(shownPosition.toInt(), false),
    Utils.prettyTime(duration.coerceAtLeast(0f).toInt(), false),
  )

  Row(
    modifier = modifier.height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    VideoTimer(
      value = shownPosition,
      isInverted = timersInverted.first,
      onClick = { clickEvent(); positionTimerOnClick() },
      modifier = Modifier.width(92.dp),
    )
    Box(
      Modifier
        .weight(1f)
        .fillMaxHeight()
        .semantics {
          val total = duration.coerceAtLeast(0f)
          progressBarRangeInfo = ProgressBarRangeInfo(shownPosition.coerceIn(0f, total), 0f..total.coerceAtLeast(0.1f))
          contentDescription = progressDescription
          setProgress { target ->
            if (total <= 0f) return@setProgress false
            val clamped = target.coerceIn(0f, total)
            userPosition = clamped
            isInteracting = false
            onValueChange(clamped)
            onValueChangeFinished()
            true
          }
        }
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val width = size.width.toFloat()
            val total = liveDuration
            if (width <= 0f || total <= 0f) return@awaitEachGesture
            fun target(x: Float) = (x / width * total).coerceIn(0f, total)
            isInteracting = true
            userPosition = target(down.position.x)
            onValueChange(userPosition)
            down.consume()
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              if (!change.pressed) break
              userPosition = target(change.position.x)
              onValueChange(userPosition)
              change.consume()
            }
            isInteracting = false
            onValueChangeFinished()
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      CachedTimelineCanvas(
        position = shownPosition,
        duration = duration,
        timeline = cacheTimeline,
        modifier = Modifier.fillMaxWidth().height(28.dp),
        lineWidth = 5.dp,
        thumbRadius = 7.dp,
      )
    }
    VideoTimer(
      value = if (timersInverted.second) shownPosition - duration else duration,
      isInverted = timersInverted.second,
      onClick = { clickEvent(); durationTimerOnCLick() },
      modifier = Modifier.width(92.dp),
    )
  }
}

@Composable
internal fun CachedTimelineCanvas(
  position: Float,
  duration: Float,
  timeline: CacheTimeline,
  modifier: Modifier = Modifier,
  lineWidth: androidx.compose.ui.unit.Dp = 5.dp,
  thumbRadius: androidx.compose.ui.unit.Dp = 7.dp,
) {
  Canvas(modifier) {
    val centerY = size.height / 2f
    val stroke = lineWidth.toPx()
    val total = duration.takeIf { it.isFinite() && it > 0f } ?: 0f
    val point = position.takeIf { it.isFinite() }?.coerceIn(0f, total) ?: 0f
    val pointFraction = if (total > 0f) point / total else 0f

    val colors = TimelineColorPalette(
      playedCached = Color.White,
      playedUncached = Color.White.copy(alpha = 0.70f),
      unplayedCached = Color.White.copy(alpha = 0.46f),
      unplayedUncached = Color.White.copy(alpha = 0.20f),
    )

    if (total > 0f) {
      classifyTimelineIntervals(timeline, total.toDouble(), point.toDouble()).forEach { interval ->
        drawLine(
          color = colors.colorFor(interval.kind),
          start = Offset((interval.startSeconds / total * size.width).toFloat(), centerY),
          end = Offset((interval.endSeconds / total * size.width).toFloat(), centerY),
          strokeWidth = stroke,
          cap = StrokeCap.Round,
        )
      }
    } else {
      drawLine(
        color = colors.unplayedUncached,
        start = Offset(0f, centerY),
        end = Offset(size.width, centerY),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
      )
    }
    drawCircle(
      color = Color.White,
      radius = thumbRadius.toPx(),
      center = Offset(size.width * pointFraction, centerY),
    )
  }
}

@Composable
fun VideoTimer(
  value: Float,
  isInverted: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit = {},
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .fillMaxHeight()
      .clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = Utils.prettyTime(value.toInt(), isInverted),
      color = Color.White,
      textAlign = TextAlign.Center,
      fontFamily = FontFamily.Monospace,
      maxLines = 1,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun PreviewSeekBar() {
  SeekbarWithTimers(
    position = 30f,
    duration = 180f,
    onValueChange = {},
    onValueChangeFinished = {},
    timersInverted = false to true,
    positionTimerOnClick = {},
    durationTimerOnCLick = {},
    cacheTimeline = CacheTimeline.fromMpv(null, 180.0),
  )
}
