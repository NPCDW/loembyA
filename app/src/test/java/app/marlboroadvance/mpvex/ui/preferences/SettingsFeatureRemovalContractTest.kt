package app.marlboroadvance.mpvex.ui.preferences

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFeatureRemovalContractTest {
  private fun file(path: String): File = sequenceOf(File(path), File("app/$path")).first(File::isFile)

  private fun source(name: String): String =
    file("src/main/java/app/marlboroadvance/mpvex/$name").readText()

  private fun blockAfter(text: String, marker: String): String {
    val start = text.indexOf(marker).also { assertTrue(marker, it >= 0) }
    val open = text.indexOf('{', start).also { assertTrue(it > start) }
    var depth = 0
    for (index in open until text.length) {
      when (text[index]) {
        '{' -> depth++
        '}' -> if (--depth == 0) return text.substring(open + 1, index)
      }
    }
    error("Unclosed block: $marker")
  }

  @Test
  fun `app lock is absent and PostgreSQL still exclusively gates navigation and foreground refresh`() {
    val activity = source("MainActivity.kt")
    val passed = blockAfter(activity, "if (postgresState.passed)")
    assertTrue(passed.contains("Navigator()"))
    assertTrue(passed.contains("DisposableEffect(Unit)"))
    assertTrue(passed.contains("ProcessLifecycleOwner.get().lifecycle"))
    assertTrue(passed.contains("embyServerRepository.setForeground("))
    assertFalse(activity.replace(passed, "").contains("embyServerRepository.setForeground("))
    assertTrue(activity.contains("viewModelStoreOwner = this@MainActivity"))
    assertTrue(activity.contains("key = \"postgres-startup-gate\""))
    assertTrue(activity.contains("PostgresSetupForm(postgresViewModel"))
    assertTrue(activity.contains("AppSplashScreen(onFinished"))
    assertTrue(activity.contains("App.markBootOk()"))
    assertFalse(activity.contains("AppLock"))
    assertFalse(source("di/PreferencesModule.kt").contains("AppLock"))
  }

  @Test
  fun `removed settings and their search results do not survive while general navigation stays`() {
    val settings = source("ui/preferences/PreferencesScreen.kt")
    val player = source("ui/preferences/PlayerPreferencesScreen.kt")
    val search = source("ui/preferences/SearchablePreference.kt") +
      source("ui/preferences/SearchablePreferenceIndex.kt")
    val removed = listOf(
      "预测性懒加载", "Emby 推荐与首页", "Emby 照片浏览", "Emby 卡片外观", "Emby 封面合成",
      "Emby 随机播放", "Emby 随机抽卡", "预解析直链", "app_lock_", "pref_media_source_pref_title",
      "savePositionOnQuit", "closeAfterReachingEndOfVideo", "autoplayNextVideo", "autoplayNextCountdownSeconds",
      "nextUpShowSeconds", "showNextUpCard", "rememberSpeedPerSeries", "rememberAudioTrackPerSeries",
      "rememberSubtitleTrackPerSeries", "rememberGlobalSpeed", "mpvDiskCacheEnabled", "MpvDiskCache",
      "rememberBrightness", "rememberSeriesBrightness", "allowGesturesInPanels", "showLoadingCircle",
      "autoCropBlackBars", "pref_player_save_position_on_quit", "pref_player_close_after_eof",
      "pref_player_controls_allow_gestures_in_panels", "pref_player_controls_show_loading_circle",
      "pref_player_disk_cache", "pref_player_clear_disk_cache", "pref_auto_crop_black_bars_title",
      "pref_remember_subtitle_track_title", "pref_remember_global_speed_title",
    )
    removed.forEach { value -> assertFalse(value, (settings + player + search).contains(value)) }
    assertTrue(settings.contains("AppearancePreferencesScreen"))
    assertTrue(settings.contains("PlayerPreferencesScreen"))
    assertTrue(settings.contains("GesturePreferencesScreen"))
    assertTrue(settings.contains("SharedConfigurationPreferencesScreen"))
    assertFalse(source("ui/preferences/SubtitlesPreferencesScreen.kt").contains("autoloadMatchingSubtitles"))
    assertFalse(source("ui/preferences/DecoderPreferencesScreen.kt").contains("pref_anime4k_title"))
    assertTrue(source("ui/player/controls/components/sheets/PlayerMoreSheet.kt").contains("onAnime4K"))
  }

  @Test
  fun `player gestures and seeking settings live and search in the gestures screen`() {
    val player = source("ui/preferences/PlayerPreferencesScreen.kt")
    val gestures = source("ui/preferences/GesturePreferencesScreen.kt")
    val indexes = listOf(
      source("ui/preferences/SearchablePreference.kt"),
      source("ui/preferences/SearchablePreferenceIndex.kt"),
    )
    val gestureTitles = listOf(
      "pref_player_gestures_brightness", "pref_player_gestures_volume", "pref_player_gestures_pinch_to_zoom",
      "pref_player_gestures_horizontal_swipe_to_seek", "pref_player_gestures_horizontal_swipe_sensitivity",
      "pref_player_haptic_feedback", "pref_player_gestures_hold_for_multiple_speed", "动态倍速浮层",
      "pref_player_use_precise_seeking", "pref_player_custom_skip_duration_title",
    )
    val entries = indexes.map { index ->
      Regex("add\\(SearchablePreference\\([\\s\\S]*?\\)\\)").findAll(index).map { it.value }.toList()
    }
    gestureTitles.forEach { title ->
      assertFalse(title, player.contains(title))
      assertTrue(title, gestures.contains(title))
      val indexedTitle = if (title == "动态倍速浮层") "pref_dynamic_speed_overlay_title" else title
      entries.forEach { index ->
        val matches = index.filter { it.contains(title) || it.contains(indexedTitle) }
        assertTrue(title, matches.isNotEmpty())
        matches.forEach { match -> assertTrue(title, match.contains("screen = GesturePreferencesScreen")) }
      }
    }
    assertTrue(gestures.contains("PlayerPreferences"))
    assertTrue(gestures.contains("doubleTapToSeekDuration"))
  }

  @Test
  fun `hide labels are seconds or never and reuse the runtime policy`() {
    val player = source("ui/preferences/PlayerPreferencesScreen.kt")
    assertTrue(player.contains("PlayerControlAutoHidePolicy.VALUES"))
    assertTrue(player.contains("PlayerControlAutoHidePolicy.normalize"))
    assertTrue(player.contains("pluralStringResource(R.plurals.seconds"))
    assertTrue(player.contains("pref_player_control_hide_never"))
    assertFalse(player.contains(" milliseconds"))
    val chinese = file("src/main/res/values-zh-rCN/strings.xml").readText()
    assertTrue(chinese.contains("<item quantity=\"other\">%1\$d 秒</item>"))
    assertTrue(chinese.contains("name=\"pref_player_control_hide_never\">永不"))
  }

  @Test
  fun `feed registration and application-lock resources have been deleted`() {
    val manifest = file("src/main/AndroidManifest.xml").readText()
    assertFalse(manifest.contains("VerticalFeedActivity"))
    assertFalse(manifest.contains("Theme.mpvex.Feed"))
    assertTrue(manifest.contains(".ui.player.PlayerActivity"))
    listOf("values", "values-zh-rCN").forEach { qualifier ->
      assertFalse(file("src/main/res/$qualifier/strings.xml").readText().contains("name=\"app_lock_"))
    }
  }
}
