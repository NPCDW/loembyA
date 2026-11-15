package app.marlboroadvance.mpvex.ui.player

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStatisticsControllerTest {
  @Test
  fun `latest action waits for script readiness then repeated page requests remain explicit`() {
    val sent = mutableListOf<PlayerStatisticsAction>()
    var unavailable = 0
    val controller = PlayerStatisticsController(sent::add) { unavailable++ }
    controller.beginLoading()
    controller.request(PlayerStatisticsAction.Show(PlayerStatisticsPage.Default))
    controller.request(PlayerStatisticsAction.Show(PlayerStatisticsPage.Cache))
    assertTrue(sent.isEmpty())
    controller.onReady()
    assertEquals(listOf(PlayerStatisticsAction.Show(PlayerStatisticsPage.Cache)), sent)
    controller.request(PlayerStatisticsAction.Show(PlayerStatisticsPage.Cache))
    controller.request(PlayerStatisticsAction.Hide)
    assertEquals(3, sent.size)
    assertEquals(PlayerStatisticsAction.Hide, sent.last())
    assertEquals(0, unavailable)
  }

  @Test
  fun `close before readiness cancels queued show`() {
    val sent = mutableListOf<PlayerStatisticsAction>()
    val controller = PlayerStatisticsController(sent::add) { error("unexpected unavailable") }
    controller.beginLoading()
    controller.request(PlayerStatisticsAction.Show(PlayerStatisticsPage.Tracks))
    controller.request(PlayerStatisticsAction.Hide)
    controller.onReady()
    assertEquals(listOf(PlayerStatisticsAction.Hide), sent)
  }

  @Test
  fun `load failure and stopped player do not execute pending native commands`() {
    val sent = mutableListOf<PlayerStatisticsAction>()
    var unavailable = 0
    val controller = PlayerStatisticsController(sent::add) { unavailable++ }
    controller.beginLoading()
    controller.request(PlayerStatisticsAction.Show(PlayerStatisticsPage.FrameTimings))
    controller.onLoadTimeout()
    assertTrue(sent.isEmpty())
    assertEquals(1, unavailable)
    controller.onReady()
    assertTrue(sent.isEmpty())
    controller.beginLoading()
    controller.request(PlayerStatisticsAction.Hide)
    controller.stop()
    controller.onReady()
    controller.request(PlayerStatisticsAction.Show(PlayerStatisticsPage.Default))
    assertEquals(2, unavailable)
    assertTrue(sent.isEmpty())
  }

  @Test
  fun `asset deployment replaces same-length changed script without using auto scan directory`() {
    val dir = Files.createTempDirectory("statistics-script-test").toFile()
    try {
      val target = PlayerStatisticsScript.deploy(dir, "first".toByteArray())
      val same = PlayerStatisticsScript.deploy(dir, "other".toByteArray())
      assertEquals(target, same)
      assertEquals("other", same.readText())
      assertEquals("stats.lua", same.name)
      assertFalse(same.relativeTo(dir).path.startsWith("scripts/"))
      PlayerStatisticsScript.deploy(dir, "other".toByteArray())
      assertEquals("other", same.readText())
    } finally {
      dir.deleteRecursively()
    }
  }
}
