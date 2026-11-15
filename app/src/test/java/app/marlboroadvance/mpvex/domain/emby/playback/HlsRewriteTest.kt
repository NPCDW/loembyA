package app.marlboroadvance.mpvex.domain.emby.playback

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsRewriteTest {
  private val manifest = "https://origin.fixture.test/hls/rendition/master?request=a%2Fb"

  @Test fun allResourceLinesResolveAgainstManifestRegardlessOfSuffixAndKeepRawQueryEncoding() = runBlocking {
    val registered = mutableListOf<String>()
    val text = listOf(
      "#EXTM3U",
      "jpeg/image.jpeg?signature=a%2Fb%2Bc%25&literal=a+b&empty=",
      "../no-extension?repeat=1&repeat=2",
      "//cdn.fixture.test/assets/image.jpg?value=x%2Fy",
      "/root/init",
      "https://other.fixture.test/absolute?value=x%2Fy",
      "data:text/plain,inline-segment",
      "",
    ).joinToString("\r\n")
    val rewritten = LoopbackPlaybackServer.rewriteHls(text, manifest) { url ->
      registered += url
      "http://127.0.0.1:12345/stream/m3u8/${registered.size}"
    }
    assertEquals(listOf(
      "https://origin.fixture.test/hls/rendition/jpeg/image.jpeg?signature=a%2Fb%2Bc%25&literal=a+b&empty=",
      "https://origin.fixture.test/hls/no-extension?repeat=1&repeat=2",
      "https://cdn.fixture.test/assets/image.jpg?value=x%2Fy",
      "https://origin.fixture.test/root/init",
      "https://other.fixture.test/absolute?value=x%2Fy",
    ), registered)
    assertEquals(listOf("#EXTM3U") + (1..5).map { "http://127.0.0.1:12345/stream/m3u8/$it" } +
      "data:text/plain,inline-segment", rewritten.lines())
    assertFalse(rewritten.contains('\r'))
    assertFalse(rewritten.endsWith('\n'))
  }

  @Test fun relativeUriAttributesAndAllOtherAbsoluteTagLinksAreLocalizedExactlyOnce() = runBlocking {
    val registered = mutableListOf<String>()
    val text = listOf(
      "#EXTM3U",
      "#EXT-X-KEY:METHOD=AES-128,URI=\"../keys/key?value=a%2Fb\",KEYFORMAT=\"https://keys.fixture.test/schema\"",
      "#EXT-X-MAP:uri = \"/maps/init\"",
      "#EXT-X-SESSION-DATA:VALUE=\"https://metadata.fixture.test/value\",EXTRA=\"http://other.fixture.test/info\"",
      "# comment https://comment.fixture.test/info <https://comment.fixture.test/other> 'http://last.fixture.test/x'",
      "#EXT-X-MEDIA:TYPE=AUDIO,URI=\"//audio.fixture.test/child\"",
    ).joinToString("\n")
    val rewritten = LoopbackPlaybackServer.rewriteHls(text, manifest) { url ->
      registered += url
      "http://127.0.0.1:12345/stream/m3u8/${registered.size}"
    }
    assertEquals(listOf(
      "https://origin.fixture.test/hls/keys/key?value=a%2Fb",
      "https://keys.fixture.test/schema",
      "https://origin.fixture.test/maps/init",
      "https://metadata.fixture.test/value",
      "http://other.fixture.test/info",
      "https://comment.fixture.test/info",
      "https://comment.fixture.test/other",
      "http://last.fixture.test/x",
      "https://audio.fixture.test/child",
    ), registered)
    assertTrue(rewritten.contains("URI=\"http://127.0.0.1:12345/stream/m3u8/1\""))
    assertTrue(rewritten.contains("KEYFORMAT=\"http://127.0.0.1:12345/stream/m3u8/2\""))
    assertTrue(rewritten.contains("uri = \"http://127.0.0.1:12345/stream/m3u8/3\""))
    assertEquals(9, Regex("http://127\\.0\\.0\\.1:12345/stream/m3u8/[0-9]+").findAll(rewritten).count())
    assertFalse(registered.any { it.startsWith("http://127.0.0.1") })
  }

  @Test fun dataUriAttributesAreOpaqueAndInsertedAbsoluteCapabilityUrlsAreNeverScannedAgain() = runBlocking {
    val registered = mutableListOf<String>()
    val inline = "DaTa:text/plain,https://inline.fixture.test/do-not-fetch"
    val text = listOf(
      "#EXTM3U",
      "#EXT-X-KEY:URI=\"$inline\",KEYFORMAT=\"https://format.fixture.test/schema\"",
      "#EXT-X-MAP:URI=\"../init\",NOTE=\"http://note.fixture.test/value\"",
      "data:text/plain,https://segment.fixture.test/do-not-fetch",
    ).joinToString("\n")
    val rewritten = LoopbackPlaybackServer.rewriteHls(text, manifest) { url ->
      registered += url
      "http://127.0.0.1:12345/stream/m3u8/${registered.size}"
    }
    assertEquals(listOf(
      "https://format.fixture.test/schema",
      "https://origin.fixture.test/hls/init",
      "http://note.fixture.test/value",
    ), registered)
    assertTrue(rewritten.contains("URI=\"$inline\""))
    assertTrue(rewritten.endsWith("data:text/plain,https://segment.fixture.test/do-not-fetch"))
    assertEquals(3, Regex("http://127\\.0\\.0\\.1:12345/stream/m3u8/[0-9]+").findAll(rewritten).count())
    assertFalse(registered.any { "do-not-fetch" in it || "127.0.0.1" in it })
  }

  @Test fun bomLeadingWhitespaceAndIndentedTagsKeepHeaderAndResourceOrder() = runBlocking {
    val registered = mutableListOf<String>()
    val text = "\uFEFF \t\r\n#EXTM3U\r\n  #EXT-X-MAP:URI=\"../init\"\r\n\tsegment.jpeg\r\n"
    val rewritten = LoopbackPlaybackServer.rewriteHls(text, manifest) { url ->
      registered += url
      "http://127.0.0.1:12345/stream/m3u8/${registered.size}"
    }
    assertEquals(listOf(
      "https://origin.fixture.test/hls/init",
      "https://origin.fixture.test/hls/rendition/segment.jpeg",
    ), registered)
    assertTrue(rewritten.startsWith("#EXTM3U\n"))
    assertEquals("  #EXT-X-MAP:URI=\"http://127.0.0.1:12345/stream/m3u8/1\"", rewritten.lines()[1])
    assertEquals("http://127.0.0.1:12345/stream/m3u8/2", rewritten.lines()[2])
  }
}
