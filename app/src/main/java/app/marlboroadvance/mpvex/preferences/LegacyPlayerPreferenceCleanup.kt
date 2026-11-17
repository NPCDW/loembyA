package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.ui.player.PlayerControlAutoHidePolicy

/** Pure startup preference migration plan, kept Android-free for JVM tests. */
object LegacyPlayerPreferenceCleanup {
  const val MARKER_KEY = "player_hidden_feature_cleanup_version"
  const val VERSION = 3
  const val DECODER_MODE_KEY = "mpv_decoder_mode"
  const val CONTROLS_HIDE_KEY = "player_time_to_disappear"

  private const val LEGACY_DIRECT_OUTPUT_KEY = "direct_output"
  private const val LEGACY_TRY_HW_DEC_KEY = "try_hw_dec"

  /** Only exact keys belonging to approved removed app, browsing, player and danmaku features. */
  val obsoleteKeys: Set<String> = setOf(
    LEGACY_DIRECT_OUTPUT_KEY,
    LEGACY_TRY_HW_DEC_KEY,
    "enable_recently_played",
    "sub_picker_path",
    "emby_card_spacing",
    "custom_aspect_ratios",
    "default_video_zoom",
    "pan_and_zoom_enabled",
    "include_subtitles_in_snapshot",
    "gif_width",
    "repeat_mode",
    "shuffle_enabled",
    "default_video_aspect",
    "default_custom_aspect_ratio",
    "deband_iterations",
    "deband_threshold",
    "deband_range",
    "deband_grain",
    "filter_brightness",
    "filter_saturation",
    "filter_gamma",
    "filter_contrast",
    "filter_hue",
    "filter_sharpness",
    "danmaku_engine",
    "danmaku_with_related",
    "danmaku_area_ratio",
    "danmaku_alpha",
    "danmaku_font_scale",
    "danmaku_speed_scale",
    "danmaku_density",
    "danmaku_prevent_overlapping",
    "danmaku_block_words",
    "danmaku_remember_selection",
    "danmaku_time_offset_ms",
    "danmaku_series_offsets",
    "app_lock_enabled",
    "app_lock_method",
    "app_lock_pin_hash",
    "app_lock_pattern_hash",
    "app_lock_auto_unlock",
    "random_play_count",
    "emby_random_drill_down_on_tap",
    "emby_random_gesture_toast",
    "emby_gacha_draw_count",
    "emby_gacha_style",
    "emby_folder_random_cover",
    "emby_folder_cover_refresh_policy",
    "emby_folder_cover_refresh_hours",
    "emby_folder_cover_refresh_days",
    "emby_folder_cover_last_synth_at",
    "emby_card_corner",
    "emby_show_card_badges",
    "emby_folder_icon_blue",
    "emby_predictive_preload",
    "emby_show_recommendations",
    "emby_random_genre_recommend",
    "emby_similar_algorithm",
    "emby_similar_exclude_played",
    "emby_home_show_mix",
    "emby_home_mix_count",
    "emby_mix_grid_columns",
    "photo_preload_count",
    "photo_slideshow_interval",
    "photo_auto_slideshow",
    "photo_filmstrip",
    "emby_resolve_direct_link",
    "emby_media_source_preference",
    "save_position",
    "close_after_eof",
    "autoplay_next_video",
    "autoplay_next_countdown_seconds",
    "next_up_show_seconds",
    "show_next_up_card",
    "remember_speed_per_series",
    "remember_audio_track_per_series",
    "remember_subtitle_track_per_series",
    "remembered_speeds",
    "remembered_audio_tracks",
    "remembered_subtitle_tracks",
    "remember_global_speed",
    "last_playback_speed",
    "mpv_disk_cache_enabled",
    "mpv_disk_cache_size_mb",
    "remember_brightness",
    "default_brightness",
    "remember_series_brightness",
    "series_brightness",
    "allow_gestures_in_panels",
    "show_loading_circle",
    "auto_crop_black_bars",
    "sub_autoload_enabled",
  )

  data class Plan(
    val decoderModeToSeed: String?,
    val controlsHideMillisToSeed: Int?,
    val keysToRemove: Set<String>,
    val markerVersion: Int,
  )

  fun plan(values: Map<String, *>): Plan? {
    val currentVersion = (values[MARKER_KEY] as? Number)?.toInt() ?: 0
    if (currentVersion >= VERSION) return null

    val decoderMode = if (values.containsKey(DECODER_MODE_KEY)) {
      null
    } else {
      when {
        values[LEGACY_DIRECT_OUTPUT_KEY] == true -> "HardwarePlus"
        values[LEGACY_TRY_HW_DEC_KEY] == false -> "Software"
        else -> "Auto"
      }
    }

    val controlsHideMillis = if (values.containsKey(CONTROLS_HIDE_KEY)) {
      val stored = values[CONTROLS_HIDE_KEY]
      val normalized = PlayerControlAutoHidePolicy.normalize(
        (stored as? Int) ?: PlayerControlAutoHidePolicy.DEFAULT_MILLIS,
      )
      normalized.takeUnless { stored is Int && stored == normalized }
    } else {
      null
    }

    return Plan(
      decoderModeToSeed = decoderMode,
      controlsHideMillisToSeed = controlsHideMillis,
      keysToRemove = obsoleteKeys.intersect(values.keys),
      markerVersion = VERSION,
    )
  }
}
