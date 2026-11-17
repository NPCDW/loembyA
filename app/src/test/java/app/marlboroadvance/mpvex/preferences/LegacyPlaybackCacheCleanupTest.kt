package app.marlboroadvance.mpvex.preferences

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyPlaybackCacheCleanupTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun `removes only dedicated stream caches from every supplied root`() {
    val internal = temporaryFolder.newFolder("internal")
    val external = temporaryFolder.newFolder("external")
    val roots = listOf(internal, external)
    roots.forEach { root ->
      File(root, "mpv_stream_cache/nested").mkdirs()
      File(root, "mpv_stream_cache/nested/old-buffer").writeText("retired")
      File(root, "images").mkdirs()
      File(root, "images/cover").writeText("keep")
      File(root, "loemby/subtitle").mkdirs()
      File(root, "loemby/subtitle/subtitle").writeText("keep")
    }

    LegacyPlaybackCacheCleanup.removeFrom(roots + internal)
    LegacyPlaybackCacheCleanup.removeFrom(roots)

    roots.forEach { root ->
      assertFalse(File(root, "mpv_stream_cache").exists())
      assertTrue(File(root, "images/cover").isFile)
      assertTrue(File(root, "loemby/subtitle/subtitle").isFile)
    }
  }

  @Test
  fun `links inside retired cache do not remove their targets`() {
    val root = temporaryFolder.newFolder("cache")
    val outside = temporaryFolder.newFolder("outside")
    val retained = File(outside, "retained").apply { writeText("keep") }
    val legacy = File(root, "mpv_stream_cache").apply { mkdirs() }
    Files.createSymbolicLink(File(legacy, "link").toPath(), outside.toPath())

    LegacyPlaybackCacheCleanup.removeFrom(listOf(root))

    assertFalse(legacy.exists())
    assertTrue(retained.isFile)
  }

  @Test
  fun `linked dedicated directory is unlinked without traversing it`() {
    val root = temporaryFolder.newFolder("cache")
    val outside = temporaryFolder.newFolder("outside")
    val retained = File(outside, "retained").apply { writeText("keep") }
    val link = File(root, "mpv_stream_cache")
    Files.createSymbolicLink(link.toPath(), outside.toPath())

    LegacyPlaybackCacheCleanup.removeFrom(listOf(root))

    assertFalse(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
    assertTrue(retained.isFile)
  }
}
