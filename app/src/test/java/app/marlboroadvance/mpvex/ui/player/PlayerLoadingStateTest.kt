package app.marlboroadvance.mpvex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLoadingStateTest {
  @Test
  fun `startup waits for actual first frame not file loaded`() {
    val initial = PlayerLoadingState()
    assertTrue(initial.visible)
    assertNull(initial.percent)
    assertFalse(initial.restarted().visible)
    assertTrue(initial.restarted().newFile().visible)
  }

  @Test
  fun `cache starvation after first frame shows loading without controls dependency`() {
    val playing = PlayerLoadingState().restarted()
    val buffering = playing.cachePaused(true)
    assertTrue(buffering.visible)
    assertFalse(buffering.awaitingFirstFrame)
    assertNull(buffering.percent)
    assertFalse(buffering.cachePaused(false).visible)
  }

  @Test
  fun `only valid native percentages are used without clamping or fabrication`() {
    val buffering = PlayerLoadingState().restarted().cachePaused(true)
    for (value in listOf(0L, 1L, 42L, 99L, 100L)) {
      assertEquals(value.toInt(), buffering.progress(value).percent)
    }
    for (value in listOf(null, -1L, 101L, Long.MIN_VALUE, Long.MAX_VALUE)) {
      assertNull(buffering.progress(42L).progress(value).percent)
    }
  }

  @Test
  fun `manual pause is not loading even if native cache pause remains true`() {
    val buffering = PlayerLoadingState().restarted().cachePaused(true).progress(42)
    val manual = buffering.manualPause(true)
    assertFalse(manual.visible)
    assertNull(manual.percent)
    assertFalse(PlayerLoadingState().manualPause(true).visible)
    assertTrue(manual.manualPause(false).visible)
    assertFalse(manual.restarted().manualPause(false).visible)
  }

  @Test
  fun `seek loading clears on recovery and preserves explicit manual pause`() {
    val seeking = PlayerLoadingState().restarted().seekChanged(true)
    assertTrue(seeking.visible)
    assertFalse(seeking.seekChanged(false).visible)
    assertFalse(seeking.manualPause(true).visible)
    val cacheWait = seeking.cachePaused(true).progress(12).seekChanged(false)
    assertTrue(cacheWait.visible)
    assertEquals(12, cacheWait.percent)
    assertFalse(cacheWait.restarted().visible)
    assertNull(cacheWait.restarted().nativePercent)
  }

  @Test
  fun `new file errors exit and restart clear previous buffering state`() {
    val old = PlayerLoadingState().restarted().cachePaused(true).seekChanged(true).progress(88)
    val new = old.newFile()
    assertTrue(new.visible)
    assertTrue(new.awaitingFirstFrame)
    assertFalse(new.pausedForCache)
    assertFalse(new.seeking)
    assertNull(new.nativePercent)
    val restarted = old.restarted()
    assertFalse(restarted.visible)
    assertNull(restarted.nativePercent)
    val stopped = old.stopped()
    assertFalse(stopped.visible)
    assertEquals(stopped, stopped.cachePaused(true).seekChanged(true).progress(100).restarted())
    assertTrue(stopped.newFile().visible)
  }

  @Test
  fun `clearing cache starvation invalidates previous progress`() {
    val cached = PlayerLoadingState().restarted().cachePaused(true).progress(75)
    val recovered = cached.cachePaused(false)
    assertNull(recovered.nativePercent)
    assertNull(recovered.cachePaused(true).percent)
  }
}
