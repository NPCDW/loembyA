package app.marlboroadvance.mpvex.ui.player

import app.marlboroadvance.mpvex.R
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStatisticsPageTest {
  private fun source(path: String): String = sequenceOf(
    File("src/main/java/$path"),
    File("app/src/main/java/$path"),
  ).first { it.isFile }.readText()

  @Test
  fun `touch pages contain only file frame cache and track information`() {
    assertEquals(listOf(1, 2, 3, 5), PlayerStatisticsPage.entries.map { it.number })
    assertEquals(3, PlayerStatisticsPage.Cache.number)
    assertEquals(R.string.player_statistics_page_cache, PlayerStatisticsPage.Cache.titleRes)
    assertEquals(R.string.player_statistics_page_tracks, PlayerStatisticsPage.Tracks.titleRes)
    assertEquals(4, PlayerStatisticsPage.entries.map { it.titleRes }.distinct().size)
  }

  @Test
  fun `single statistics entry opens a scrollable submenu with explicit close`() {
    val sheet = source("app/marlboroadvance/mpvex/ui/player/controls/components/sheets/PlayerMoreSheet.kt")
    val menus = sheet.substring(sheet.indexOf("private fun MoreMenu("), sheet.indexOf("private fun MoreRow("))

    assertTrue(sheet.contains("onStatistics = { page = MorePage.Statistics }"))
    assertTrue(sheet.contains("MorePage.Statistics -> StatisticsPageMenu("))
    assertTrue(sheet.contains("activity.selectPlaybackStatisticsPage(it)\n          onDismissRequest()"))
    assertTrue(sheet.contains("activity.closePlaybackStatistics()\n          onDismissRequest()"))
    assertTrue(menus.contains("LazyColumn(Modifier.fillMaxWidth()"))
    assertTrue(menus.contains("items(PlayerStatisticsPage.entries, key = { it.number })"))
    assertTrue(menus.contains("SubmenuHeader(stringResource(R.string.player_more_statistics), onBack)"))
    assertTrue(menus.contains("R.string.player_statistics_close"))
    assertFalse(sheet.contains("onStatisticsPages"))
    assertFalse(sheet.contains("togglePlaybackStatistics"))
    assertFalse(menus.contains("player_statistics_pages_hint"))
    assertFalse(menus.contains("RadioButton"))
  }

  @Test
  fun `script readiness is core scoped while ordinary properties remain file generation checked`() {
    val observer = source("app/marlboroadvance/mpvex/ui/player/PlayerObserver.kt")
    assertTrue(observer.contains("property == PlayerStatisticsScript.READY_PROPERTY || generation =="))
    assertTrue(observer.contains("guard(\"props:\$property\") { activity.onObserverEvent(property, value) }"))
  }

  @Test
  fun `statistics use explicit messages with lifecycle gates and no decoder changes`() {
    val activity = source("app/marlboroadvance/mpvex/ui/player/PlayerActivity.kt")
    val statistics = activity.substring(
      activity.indexOf("  fun selectPlaybackStatisticsPage("),
      activity.indexOf("  fun showPlayerToast("),
    )
    val view = source("app/marlboroadvance/mpvex/ui/player/MPVView.kt")

    assertTrue(statistics.contains("statisticsController.request(PlayerStatisticsAction.Show(page))"))
    assertTrue(statistics.contains("statisticsController.request(PlayerStatisticsAction.Hide)"))
    assertTrue(statistics.contains("\"script-message-to\", \"stats\", PlayerStatisticsScript.SHOW_MESSAGE"))
    assertTrue(statistics.contains("\"script-message-to\", \"stats\", PlayerStatisticsScript.HIDE_MESSAGE"))
    assertTrue(statistics.contains("mpvInitialized && PlayerLib.isCoreReady && !isFinishing && !isDestroyed"))
    assertTrue(statistics.contains("!player.isExiting"))
    assertTrue(statistics.contains("currentVo == \"mediacodec_embed\""))
    assertTrue(statistics.contains("R.string.player_statistics_direct_output"))
    assertTrue(statistics.contains("PlayerLib.command(\"load-script\", script.absolutePath)"))
    assertTrue(view.contains("MPVLib.setOptionString(\"load-stats-overlay\", \"no\")"))
    assertFalse(statistics.contains("display-page"))
    assertFalse(statistics.contains("switchVideoOutput"))
    assertFalse(statistics.contains("keypress"))
  }
}
