package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.ui.player.PlayerControlAutoHidePolicy
import kotlinx.coroutines.flow.map

class PlayerPreferences(
  preferenceStore: PreferenceStore,
) {
  val invertDuration = preferenceStore.getBoolean("invert_duration")
  val usePreciseSeeking = preferenceStore.getBoolean("use_precise_seeking", false)

  // Gesture settings keep their original eight keys while the settings UI lives under Gestures.
  val holdForMultipleSpeed = preferenceStore.getFloat("hold_for_multiple_speed", 2f)
  val showDynamicSpeedOverlay = preferenceStore.getBoolean("show_dynamic_speed_overlay", true)
  val brightnessGesture = preferenceStore.getBoolean("gestures_brightness", true)
  val volumeGesture = preferenceStore.getBoolean("volume_brightness", true)
  val pinchToZoomGesture = preferenceStore.getBoolean("pinch_to_zoom_gesture", true)
  val horizontalSwipeToSeek = preferenceStore.getBoolean("horizontal_swipe_to_seek", true)
  val horizontalSwipeSensitivity = preferenceStore.getFloat("horizontal_swipe_sensitivity", 0.05f)
  val hapticFeedbackEnabled = preferenceStore.getBoolean("haptic_feedback_enabled", true)

  // Only explicit Make default/preset actions persist speed; ordinary session changes do not.
  val defaultSpeed = preferenceStore.getFloat("default_speed", 1f)
  val speedPresets = preferenceStore.getStringSet(
    "default_speed_presets",
    setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
  )

  val showSystemStatusBar = preferenceStore.getBoolean("show_system_status_bar")
  val showSystemNavigationBar = preferenceStore.getBoolean("show_system_navigation_bar")
  private val storedControlsHideDelay = preferenceStore.getInt(
    "player_time_to_disappear",
    PlayerControlAutoHidePolicy.DEFAULT_MILLIS,
  )
  val playerTimeToDisappear: Preference<Int> = object : Preference<Int> {
    override fun get() = PlayerControlAutoHidePolicy.normalize(storedControlsHideDelay.get())
    override fun set(value: Int) = storedControlsHideDelay.set(PlayerControlAutoHidePolicy.normalize(value))
    override fun delete() = storedControlsHideDelay.delete()
    override fun changes() = storedControlsHideDelay.changes().map(PlayerControlAutoHidePolicy::normalize)
  }

  val customSkipDuration = preferenceStore.getInt("custom_skip_duration", 90)

  /** Emby intro markers take priority; enabled automatic skipping can fall back to the fixed duration. */
  val autoSkipIntro = preferenceStore.getBoolean("auto_skip_intro", false)

  /** Outro skipping seeks to the end; normal completion advances the queue without a countdown. */
  val autoSkipOutro = preferenceStore.getBoolean("auto_skip_outro", false)

  val autoPiPOnNavigation = preferenceStore.getBoolean("auto_pip_on_navigation", false)
  val keepScreenOnWhenPaused = preferenceStore.getBoolean("keep_screen_on_when_paused", false)
}
