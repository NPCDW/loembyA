package app.marlboroadvance.mpvex.preferences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPlayerPreferenceCleanupTest {
  @Test
  fun `direct output wins while seeding decoder mode then both legacy keys are removed`() {
    val plan = requireNotNull(
      LegacyPlayerPreferenceCleanup.plan(
        mapOf("direct_output" to true, "try_hw_dec" to false),
      ),
    )

    assertEquals("HardwarePlus", plan.decoderModeToSeed)
    assertTrue("direct_output" in plan.keysToRemove)
    assertTrue("try_hw_dec" in plan.keysToRemove)
  }

  @Test
  fun `disabled legacy hardware decode seeds software`() {
    val plan = requireNotNull(LegacyPlayerPreferenceCleanup.plan(mapOf("try_hw_dec" to false)))

    assertEquals("Software", plan.decoderModeToSeed)
  }

  @Test
  fun `existing decoder mode is preserved and unrelated values are untouched`() {
    val plan = requireNotNull(
      LegacyPlayerPreferenceCleanup.plan(
        mapOf(
          "mpv_decoder_mode" to "Hardware",
          "direct_output" to true,
          "unrelated_setting" to "keep",
        ),
      ),
    )

    assertNull(plan.decoderModeToSeed)
    assertFalse("mpv_decoder_mode" in plan.keysToRemove)
    assertFalse("unrelated_setting" in plan.keysToRemove)
    assertTrue("direct_output" in plan.keysToRemove)
  }

  @Test
  fun `cleanup is idempotent after current marker`() {
    assertNull(
      LegacyPlayerPreferenceCleanup.plan(
        mapOf(
          LegacyPlayerPreferenceCleanup.MARKER_KEY to LegacyPlayerPreferenceCleanup.VERSION,
          "direct_output" to true,
        ),
      ),
    )
  }

  @Test
  fun `approved keys include removed player and hidden danmaku persistence but retain visible controls`() {
    val removed = LegacyPlayerPreferenceCleanup.obsoleteKeys
    listOf(
      "custom_aspect_ratios",
      "default_video_zoom",
      "default_custom_aspect_ratio",
      "sub_picker_path",
      "emby_card_spacing",
      "repeat_mode",
      "shuffle_enabled",
      "filter_brightness",
      "danmaku_with_related",
      "danmaku_time_offset_ms",
    ).forEach { assertTrue(it, it in removed) }

    listOf(
      "pinch_to_zoom_gesture",
      "debanding",
      "danmaku_enabled",
      "danmaku_host",
      "danmaku_app_id",
      "danmaku_app_secret",
      "danmaku_auto_match",
    ).forEach { assertFalse(it, it in removed) }
  }

  @Test
  fun `upgrades version two and clears removed feature values without resetting live settings`() {
    val values = LegacyPlayerPreferenceCleanup.obsoleteKeys.associateWith { "old-value" }.toMutableMap<String, Any>()
    values[LegacyPlayerPreferenceCleanup.MARKER_KEY] = 2
    values["mpv_decoder_mode"] = "Hardware"
    values["player_time_to_disappear"] = 4000
    val retained = mapOf(
      "gestures_brightness" to false,
      "volume_brightness" to false,
      "pinch_to_zoom_gesture" to true,
      "horizontal_swipe_to_seek" to true,
      "horizontal_swipe_sensitivity" to 0.05f,
      "haptic_feedback_enabled" to true,
      "hold_for_multiple_speed" to 2f,
      "show_dynamic_speed_overlay" to true,
      "use_precise_seeking" to true,
      "custom_skip_duration" to 137,
      "enable_anime4k" to true,
      "anime4k_mode" to "A",
      "default_speed" to 1.25f,
      "photo_last_index_folder" to 7,
      "photo_grid_columns" to 3,
    )
    values.putAll(retained)

    val plan = requireNotNull(LegacyPlayerPreferenceCleanup.plan(values))

    assertEquals(LegacyPlayerPreferenceCleanup.VERSION, plan.markerVersion)
    assertEquals(LegacyPlayerPreferenceCleanup.obsoleteKeys, plan.keysToRemove)
    assertNull(plan.decoderModeToSeed)
    assertEquals(5000, plan.controlsHideMillisToSeed)
    retained.keys.forEach { assertFalse(it, it in plan.keysToRemove) }
  }

  @Test
  fun `supported hide times including never are retained without rewriting`() {
    listOf(1000, 3000, 5000, 8000, 10000, 20000, -1).forEach { value ->
      val plan = requireNotNull(LegacyPlayerPreferenceCleanup.plan(mapOf("player_time_to_disappear" to value)))
      assertNull("$value", plan.controlsHideMillisToSeed)
      assertFalse("player_time_to_disappear" in plan.keysToRemove)
    }
  }

  @Test
  fun `unsupported and malformed legacy hide times seed five seconds`() {
    listOf(500, 1500, 4000, 0, -2, 5000L, "3000").forEach { value ->
      val plan = requireNotNull(LegacyPlayerPreferenceCleanup.plan(mapOf("player_time_to_disappear" to value)))
      assertEquals("$value", 5000, plan.controlsHideMillisToSeed)
    }
    assertNull(requireNotNull(LegacyPlayerPreferenceCleanup.plan(emptyMap<String, Any>())).controlsHideMillisToSeed)
  }

  @Test
  fun `application runs synchronous cleanup before starting Koin`() {
    val source = sequenceOf(
      File("src/main/java/app/marlboroadvance/mpvex/App.kt"),
      File("app/src/main/java/app/marlboroadvance/mpvex/App.kt"),
    ).first(File::isFile).readText()

    val onCreate = source.substringAfter("override fun onCreate()")
    val cleanup = onCreate.indexOf("cleanupLegacyPlayerPreferences()")
    val koin = onCreate.indexOf("startKoin {")
    assertTrue(cleanup >= 0 && koin > cleanup)
    assertTrue(source.contains("editor.commit()"))
  }
}
