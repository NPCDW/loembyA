package app.marlboroadvance.mpvex.ui.preferences

import app.marlboroadvance.mpvex.R

/**
 * 各设置页可定位行的补充索引，与 [SearchablePreferences] 的英文关键词清单合并。
 * 标题必须与页面上的 settingsAnchor 一致；移除或迁移设置时同步维护入口和分类。
 */
internal val generatedSearchablePreferences: List<SearchablePreference> = buildList {
    add(SearchablePreference(
        titleRes = R.string.postgres_settings_title,
        summaryRes = R.string.postgres_settings_summary,
        screenKeywords = listOf("设置", "preferences", "settings", "database", "数据库"),
        category = "PostgreSQL",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        title = "共享配置（只读）",
        summary = "查看 Android 生效配置、服务器线路和反向代理；请在 loemby 桌面端修改",
        keywords = listOf("global config", "reverse proxy", "server", "trakt", "simkl", "yamtrack", "只读", "共享", "反向代理"),
        category = "PostgreSQL",
        screen = SharedConfigurationPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_appearance_title,
        summaryRes = R.string.pref_appearance_summary,
        screenKeywords = listOf("设置", "preferences", "settings"),
        category = "外观",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player,
        summaryRes = R.string.pref_player_summary,
        category = "播放与控件",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture,
        summaryRes = R.string.pref_gesture_summary,
        category = "播放与控件",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.danmaku_title,
        summaryRes = R.string.danmaku_settings_summary,
        category = "播放与控件",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder,
        summaryRes = R.string.pref_decoder_summary,
        category = "媒体渲染（解码 / 字幕 / 音频）",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_subtitles,
        summaryRes = R.string.pref_subtitles_summary,
        category = "媒体渲染（解码 / 字幕 / 音频）",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_audio,
        summaryRes = R.string.pref_audio_summary,
        category = "媒体渲染（解码 / 字幕 / 音频）",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_advanced,
        summaryRes = R.string.pref_advanced_summary,
        category = "高级与关于",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_about_title,
        summaryRes = R.string.pref_about_summary,
        category = "高级与关于",
        screen = PreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_appearance_amoled_mode_title,
        summaryRes = R.string.pref_appearance_amoled_mode_summary,
        screenKeywords = listOf("外观", "主题", "theme", "appearance", "深色", "浅色", "amoled", "颜色", "卡片"),
        category = "外观",
        screen = AppearancePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_appearance_hide_nav_labels_title,
        summaryRes = R.string.pref_appearance_hide_nav_labels_summary,
        screenKeywords = listOf("导航", "底部导航", "标题", "只显示图标", "navigation", "bottom bar", "labels"),
        category = "外观",
        screen = AppearancePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_appearance_side_scrollbar_title,
        summaryRes = R.string.pref_appearance_side_scrollbar_summary_on,
        category = "外观",
        screen = AppearancePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_appearance_watched_threshold_title,
        summaryRes = R.string.pref_appearance_watched_threshold_summary,
        category = "外观",
        screen = AppearancePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.appearance_deep_title,
        summaryRes = R.string.appearance_icon_title,
        category = "外观",
        screen = AppearancePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_auto_skip_intro_title,
        summaryRes = R.string.pref_auto_skip_intro_summary_on,
        category = "常规",
        screen = PlayerPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_auto_skip_outro_title,
        summaryRes = R.string.pref_auto_skip_outro_summary_on,
        category = "常规",
        screen = PlayerPreferencesScreen,
    ))
    add(SearchablePreference(
        title = "自动画中画",
        summaryRes = R.string.i18n_auto_pip_hint,
        category = "常规",
        screen = PlayerPreferencesScreen,
    ))
    add(SearchablePreference(
        title = "暂停时保持屏幕常亮",
        summary = "Screen stays awake while video is paused",
        category = "常规",
        screen = PlayerPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_display_hide_player_control_time,
        summary = "Configure the controls auto-hide timeout",
        screenKeywords = listOf("控件", "自动隐藏", "timeout", "controls"),
        category = "常规",
        screen = PlayerPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_use_precise_seeking,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_custom_skip_duration_title,
        summaryRes = R.string.pref_player_custom_skip_duration_summary,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_gestures_brightness,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_gestures_volume,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_gestures_pinch_to_zoom,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_gestures_horizontal_swipe_to_seek,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_gestures_horizontal_swipe_sensitivity,
        summary = "Current: \${sensitivityPercent}/100 (\${if (sensitivityPercent < 30) ",
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_haptic_feedback,
        summaryRes = R.string.pref_player_haptic_feedback_summary,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_gestures_hold_for_multiple_speed,
        summaryRes = R.string.generic_disabled,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        title = "动态倍速浮层",
        summaryRes = R.string.i18n_speed_overlay_hint,
        category = "Gestures",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_display_show_status_bar,
        category = "pref_player_display",
        screen = PlayerPreferencesScreen,
    ))
    add(SearchablePreference(
        title = "显示控件时显示导航栏",
        category = "pref_player_display",
        screen = PlayerPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_player_double_tap_seek_duration,
        summary = "Custom (\${doubleTapSeekDuration}s)",
        screenKeywords = listOf("手势", "gesture", "双击", "滑动", "音量", "亮度"),
        category = "pref_gesture_double_tap_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.i18n_double_tap_area_width,
        summary = "Current: \${doubleTapSeekAreaWidth}%",
        category = "pref_gesture_double_tap_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_double_tap_left_title,
        category = "pref_gesture_double_tap_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_single_tap_center_title,
        category = "pref_gesture_double_tap_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_double_tap_center_title,
        category = "pref_gesture_double_tap_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_double_tap_right_title,
        category = "pref_gesture_double_tap_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_use_single_tap_for_center_title,
        summaryRes = R.string.pref_gesture_use_single_tap_for_center_summary,
        category = "pref_gesture_double_tap_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_media_previous,
        category = "pref_gesture_media_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_media_play,
        category = "pref_gesture_media_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_gesture_media_next,
        category = "pref_gesture_media_title",
        screen = GesturePreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.danmaku_enable_title,
        summaryRes = R.string.danmaku_enable_summary,
        screenKeywords = listOf("弹幕", "danmaku", "弹幕源", "弹幕库"),
        category = "danmaku_title",
        screen = DanmakuSettingsScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.danmaku_auto_match,
        summaryRes = R.string.danmaku_auto_match_summary,
        category = "danmaku_title",
        screen = DanmakuSettingsScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_profile_title,
        screenKeywords = listOf("解码", "渲染", "decoder", "硬解", "hw", "滤镜", "shader"),
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_default_title,
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_gpu_next_title,
        summaryRes = R.string.pref_decoder_gpu_next_summary,
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_dv_p5_title,
        summaryRes = R.string.pref_decoder_dv_p5_summary,
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_hdr_title,
        summaryRes = R.string.pref_decoder_hdr_summary,
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_vulkan_title,
        summaryRes = R.string.pref_decoder_vulkan_summary,
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_debanding_title,
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_decoder_yuv420p_title,
        summaryRes = R.string.pref_decoder_yuv420p_summary,
        category = "pref_decoder",
        screen = DecoderPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_preferred_languages,
        summaryRes = R.string.not_set_video_default,
        screenKeywords = listOf("字幕", "subtitle", "ass", "字体", "语言"),
        category = "general",
        screen = SubtitlesPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.player_sheets_sub_override_ass,
        summaryRes = R.string.player_sheets_sub_override_ass_subtitle,
        category = "general",
        screen = SubtitlesPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.player_sheets_sub_follow_picture,
        summaryRes = R.string.player_sheets_sub_follow_picture_summary,
        category = "general",
        screen = SubtitlesPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_preferred_languages,
        summaryRes = R.string.not_set_video_default,
        screenKeywords = listOf("音频", "audio", "声道", "音量", "音轨"),
        category = "pref_audio",
        screen = AudioPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_audio_pitch_correction_title,
        summaryRes = R.string.pref_audio_pitch_correction_summary,
        category = "pref_audio",
        screen = AudioPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_audio_volume_normalization_title,
        summaryRes = R.string.pref_audio_volume_normalization_summary,
        category = "pref_audio",
        screen = AudioPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_audio_normalization_strength,
        category = "pref_audio",
        screen = AudioPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.background_playback_title,
        category = "pref_audio",
        screen = AudioPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_audio_channels,
        category = "pref_audio",
        screen = AudioPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_audio_volume_boost_cap,
        summaryRes = R.string.generic_disabled,
        category = "pref_audio",
        screen = AudioPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_advanced_mpv_conf_storage_location,
        category = "MPV 配置",
        screen = AdvancedPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_advanced_mpv_conf,
        summaryRes = R.string.i18n_tap_to_edit_config,
        category = "MPV 配置",
        screen = AdvancedPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_advanced_input_conf,
        summaryRes = R.string.i18n_tap_to_edit_config,
        category = "MPV 配置",
        screen = AdvancedPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.i18n_clear_config_cache,
        summaryRes = R.string.i18n_clear_config_cache_hint,
        category = "缓存",
        screen = AdvancedPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_advanced_clear_fonts_cache,
        summaryRes = R.string.i18n_clear_font_cache_hint,
        category = "缓存",
        screen = AdvancedPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_advanced_verbose_logging_title,
        summaryRes = R.string.pref_advanced_verbose_logging_summary,
        category = "日志",
        screen = AdvancedPreferencesScreen,
    ))
    add(SearchablePreference(
        titleRes = R.string.pref_advanced_dump_logs_title,
        summaryRes = R.string.pref_advanced_dump_logs_summary,
        category = "日志",
        screen = AdvancedPreferencesScreen,
    ))
}
