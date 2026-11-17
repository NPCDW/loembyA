package app.marlboroadvance.mpvex.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlAutoHidePolicyTest {
  @Test
  fun `only the seven approved millisecond choices are accepted`() {
    assertEquals(listOf(1000, 3000, 5000, 8000, 10000, 20000, -1), PlayerControlAutoHidePolicy.VALUES)
    assertEquals(5000, PlayerControlAutoHidePolicy.DEFAULT_MILLIS)
    PlayerControlAutoHidePolicy.VALUES.forEach { value ->
      assertEquals(value, PlayerControlAutoHidePolicy.normalize(value))
      if (value == -1) {
        assertNull(PlayerControlAutoHidePolicy.delayMillis(value))
      } else {
        assertEquals(value.toLong(), PlayerControlAutoHidePolicy.delayMillis(value))
      }
    }
  }

  @Test
  fun `legacy and invalid values fall back to five seconds rather than skipping or negative delays`() {
    listOf(Int.MIN_VALUE, -100, -2, 0, 1, 500, 2000, 4000, 6000, 30000, Int.MAX_VALUE).forEach { value ->
      assertEquals(5000, PlayerControlAutoHidePolicy.normalize(value))
      assertEquals(5000L, PlayerControlAutoHidePolicy.delayMillis(value))
    }
  }
}

class PlaybackSessionSpeedTest {
  @Test
  fun `new logical item resets to explicit default while same item reload retains session speed`() {
    val speed = PlaybackSessionSpeed()
    assertEquals(1.25f, speed.beginItem("item-a", 1.25f))
    speed.changeSpeed(1.8f)
    val oldGeneration = speed.generation
    assertEquals(1.8f, speed.beginItem("item-a", 1.1f))
    assertEquals(oldGeneration, speed.generation)
    assertEquals(1.1f, speed.beginItem("item-b", 1.1f))
    assertFalse(speed.acceptsTemporarySpeed(oldGeneration))
  }

  @Test
  fun `reload and explicit default reset retire temporary hold without storing its speed`() {
    val speed = PlaybackSessionSpeed()
    speed.beginItem("item-a", 1f)
    speed.changeSpeed(1.5f)
    val holdGeneration = speed.generation
    assertTrue(speed.acceptsTemporarySpeed(holdGeneration))
    speed.invalidateTemporarySpeed()
    assertFalse(speed.acceptsTemporarySpeed(holdGeneration))
    assertEquals(1.5f, speed.beginItem("item-a", 1f))
    val secondHold = speed.generation
    speed.changeSpeed(1f)
    assertFalse(speed.acceptsTemporarySpeed(secondHold))
    assertEquals(1f, speed.speed)
  }

  @Test
  fun `malformed explicit defaults safely use normal speed`() {
    listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { value ->
      assertEquals(1f, PlaybackSessionSpeed().beginItem("item-a", value))
    }
  }
}
