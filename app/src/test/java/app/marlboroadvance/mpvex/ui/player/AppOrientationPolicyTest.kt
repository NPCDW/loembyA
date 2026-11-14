package app.marlboroadvance.mpvex.ui.player

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOrientationPolicyTest {
  @Test
  fun `non playback always uses normal portrait`() {
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, AppOrientationPolicy.APP)
    orientationRequests.forEach { request ->
      assertEquals(AppOrientationPolicy.APP, AppOrientationPolicy.constrainRequest(false, request))
    }
  }

  @Test
  fun `other video playback keeps its existing sensor landscape policy`() {
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, AppOrientationPolicy.VIDEO)
    orientationRequests.forEach { request ->
      assertEquals(AppOrientationPolicy.VIDEO, AppOrientationPolicy.constrainRequest(true, request))
    }
  }

  @Test
  fun `mpv accepts fixed orientations and otherwise defaults to landscape`() {
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, AppOrientationPolicy.MPV)
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      AppOrientationPolicy.constrainMpvRequest(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      AppOrientationPolicy.constrainMpvRequest(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    )
    orientationRequests
      .filterNot { it in setOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) }
      .forEach { request -> assertEquals(AppOrientationPolicy.MPV, AppOrientationPolicy.constrainMpvRequest(request)) }
  }

  @Test
  fun `video aspect chooses one fixed orientation`() {
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, AppOrientationPolicy.orientationForVideo(16.0 / 9.0))
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, AppOrientationPolicy.orientationForVideo(9.0 / 16.0))
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, AppOrientationPolicy.orientationForVideo(1.0))
    assertNull(AppOrientationPolicy.orientationForVideo(null))
    assertNull(AppOrientationPolicy.orientationForVideo(0.0))
    assertNull(AppOrientationPolicy.orientationForVideo(Double.NaN))
  }

  @Test
  fun `manual rotate alternates fixed portrait and landscape`() {
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
      AppOrientationPolicy.rotatedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      AppOrientationPolicy.rotatedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    )
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      AppOrientationPolicy.rotatedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED),
    )
  }

  @Test
  fun `exiting landscape video playback restores portrait instead of carrying its request`() {
    val playback = AppOrientationPolicy.constrainRequest(true, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    assertEquals(AppOrientationPolicy.APP, AppOrientationPolicy.constrainRequest(false, playback))
  }

  @Test
  fun `startup already in landscape enters fullscreen without a rotation callback`() {
    assertTrue(AppOrientationPolicy.shouldEnterVideoFullscreen(true, false, false, false, false))
    assertFalse(AppOrientationPolicy.shouldEnterVideoFullscreen(true, false, false, false, true))
  }

  @Test
  fun `fullscreen gate excludes PiP portrait exiting and destroyed windows`() {
    val states = listOf(false, true)
    for (landscape in states) for (pip in states) for (finishing in states)
      for (destroyed in states) for (fullscreen in states) {
        assertEquals(
          landscape && !pip && !finishing && !destroyed && !fullscreen,
          AppOrientationPolicy.shouldEnterVideoFullscreen(landscape, pip, finishing, destroyed, fullscreen),
        )
      }
  }

  @Test
  fun `missing invalid metadata stays landscape before native initialization`() {
    val initial = PlayerOrientationState()
    assertEquals(AppOrientationPolicy.MPV, initial.orientation)
    assertEquals(PlayerOrientationState.Owner.DEFAULT, initial.owner)
    val prepared = initial.prepareFile("version-a")
    listOf(null to null, 0 to 1080, 1920 to -1).forEach { (width, height) ->
      assertEquals(prepared, prepared.metadata("version-a", width, height))
    }
    assertEquals(prepared, prepared.metadata("other", 1080, 1920))
    assertEquals(prepared, prepared.nativeAspect("version-a", 9.0 / 16.0))
  }

  @Test
  fun `selected metadata is provisional until rotation corrected native geometry`() {
    val provisional = PlayerOrientationState().prepareFile("version-a").metadata("version-a", 1920, 1080)
    assertEquals(PlayerOrientationState.Owner.METADATA, provisional.owner)
    val started = provisional.startFile("version-a")
    assertEquals(provisional.orientation, started.orientation)
    assertEquals(started, started.nativeAspect("version-a", 9.0 / 16.0))
    val native = started.firstFrame("version-a").nativeAspect("version-a", 9.0 / 16.0)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, native.orientation)
    assertEquals(PlayerOrientationState.Owner.NATIVE, native.owner)
    assertEquals(native, native.metadata("version-a", 1920, 1080))
    assertEquals(native, native.nativeAspect("other", 16.0 / 9.0))
    assertEquals(native, native.nativeAspect("version-a", Double.NaN))
    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      native.nativeAspect("version-a", 16.0 / 9.0).orientation,
    )
  }

  @Test
  fun `same item version switch and same url reload discard previous native authority`() {
    val native = PlayerOrientationState().prepareFile("item-version-a").startFile("item-version-a")
      .firstFrame("item-version-a").nativeAspect("item-version-a", 9.0 / 16.0)
    for (identity in listOf("item-version-b", "item-version-a")) {
      val pending = native.prepareFile(identity)
      assertEquals(AppOrientationPolicy.MPV, pending.orientation)
      assertFalse(pending.nativeStarted)
      assertFalse(pending.nativeReady)
      assertEquals(pending, pending.nativeAspect(identity, 9.0 / 16.0))
      assertEquals(pending, pending.firstFrame(identity))
    }
    val repeatedStart = native.startFile("item-version-a")
    assertEquals(AppOrientationPolicy.MPV, repeatedStart.orientation)
    assertEquals(PlayerOrientationState.Owner.DEFAULT, repeatedStart.owner)
    assertFalse(repeatedStart.nativeReady)
  }

  @Test
  fun `late native restart cannot open geometry gate while preparing a new file`() {
    val prepared = PlayerOrientationState().prepareFile("version-b")
    assertEquals(prepared, prepared.firstFrame("version-b"))
    assertEquals(prepared, prepared.firstFrame("version-a"))
    val started = prepared.startFile("version-b")
    assertEquals(started, started.firstFrame("version-a"))
    assertEquals(started, started.nativeAspect("version-b", 9.0 / 16.0))
  }

  @Test
  fun `manual orientation remains user owned across versions and native updates`() {
    val manual = PlayerOrientationState().rotate()
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, manual.orientation)
    val next = manual.prepareFile("version-b").metadata("version-b", 1920, 1080).startFile("version-b")
      .firstFrame("version-b").nativeAspect("version-b", 16.0 / 9.0)
    assertEquals(manual.orientation, next.orientation)
    assertEquals(PlayerOrientationState.Owner.MANUAL, next.owner)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, next.rotate().orientation)
    assertFalse(next.stop().nativeReady)
  }

  private val orientationRequests = (-1..14).toList() + listOf(Int.MIN_VALUE, Int.MAX_VALUE)
}
