package app.marlboroadvance.mpvex.domain.emby.playback

import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.domain.postgres.PlaybackConfiguration
import app.marlboroadvance.mpvex.domain.postgres.playbackConfigurationForTest
import com.github.luben.zstd.Zstd
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream

/** Real JVM CIO socket and real HTTP upstream, not a mocked rewriteHls result. */
class LoopbackPlaybackServerTest {
  private val browser = OkHttpClient.Builder().followRedirects(false).build()
  private fun exchange(code: Int, body: ByteArray = ByteArray(0), headers: Map<String, String> = emptyMap()): (HttpExchange) -> Unit = { call ->
    headers.forEach { (name, value) -> call.responseHeaders.add(name, value) }
    call.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
    call.responseBody.use { it.write(body) }
  }

  private class Fixture : AutoCloseable {
    val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val executor = Executors.newCachedThreadPool()
    val directory = Files.createTempDirectory("loemby-loopback-test").toFile()
    val calls = AtomicInteger()
    var playbackBody = ""; var payload = ""
    var playbackEncoding: String? = null
    val server: EmbyServer
    var config: PlaybackConfiguration
    val local: LoopbackPlaybackServer
    val origin: String get() = "http://127.0.0.1:${upstream.address.port}"
    init {
      upstream.executor = executor
      server = EmbyServer(name = "test", host = "127.0.0.1", port = upstream.address.port,
        username = "account", password = "password", apiToken = "secret", userId = "user",
        remoteId = "uuid", sourceKey = "source", sourceRevision = 1,
        client = "client", device = "device", deviceId = "deviceid", clientVersion = "version", userAgent = "agent")
      config = playbackConfigurationForTest(
        source = app.marlboroadvance.mpvex.domain.postgres.PostgresSourceToken("source", 1),
        serverUuid = "uuid",
      )
      local = LoopbackPlaybackServer(directory, { it }, { it }, { config })
      payload = """{"PlaySessionId":"session","MediaSources":[{"Id":"media","Name":"1080p","Container":"mkv","DirectStreamUrl":"/video","MediaStreams":[]}]}"""
      upstream.createContext("/emby/Items/item/PlaybackInfo") {
        calls.incrementAndGet(); playbackBody = it.requestBody.bufferedReader().readText()
        Thread.sleep(25)
        val bytes = payload.toByteArray().let { if (playbackEncoding == "zstd") Zstd.compress(it) else it }
        playbackEncoding?.let { encoding -> it.responseHeaders.add("Content-Encoding", encoding) }
        it.sendResponseHeaders(200, bytes.size.toLong()); it.responseBody.use { out -> out.write(bytes) }
      }
      upstream.start()
    }
    override fun close() {
      runBlocking { local.stop() }; upstream.stop(0); executor.shutdownNow(); directory.deleteRecursively()
    }
  }

  private fun get(url: String, range: String? = null) = browser.newCall(Request.Builder().url(url)
    .apply { range?.let { header("Range", it) } }.build()).execute()

  private fun mediaUrls(text: String): List<String> =
    Regex("http://127\\.0\\.0\\.1:[0-9]+/stream/[^/]+/[^\\s\"<>']+")
      .findAll(text).map { it.value }.toList()

  private fun assertOwned(f: Fixture, play: String, urls: List<String>) {
    assertTrue("Expected localized HLS links", urls.isNotEmpty())
    urls.forEach { url ->
      assertTrue(url, f.local.ownsMediaUrl(play, url))
      assertEquals(play, f.local.playUrlForNativePath(url))
      assertFalse(f.local.ownsMediaUrl(play, "$url?extra=1"))
      get(f.local.playUrlForNativePath(url)!!).use { redirect ->
        assertEquals(308, redirect.code)
        assertTrue(f.local.ownsMediaUrl(play, redirect.header("Location")))
      }
    }
  }

  @Test fun singleFlightPostRedirectResumeOwnershipAndRelease() = runBlocking {
    Fixture().use { f ->
      val play = f.local.register(PlaybackSpec(f.server, "item", positionTicks = 123))
      val results = List(12) { async { f.local.resolvedForUrl(play) } }.awaitAll()
      assertEquals(1, f.calls.get()); assertEquals(1, results.map { it.videoUrl }.distinct().size)
      assertTrue(f.playbackBody.contains("\"MaxStreamingBitrate\":1400000000"))
      assertTrue(f.playbackBody.contains("\"EnableTranscoding\":false"))
      get(play).use { assertEquals(308, it.code); assertEquals(results.first().videoUrl, it.header("Location")) }
      assertTrue(f.local.ownsMediaUrl(play, results.first().videoUrl))
      assertFalse(f.local.ownsMediaUrl(play, results.first().videoUrl + "?wrong"))
      assertFalse(f.local.isLocalPlayUrl(play.replace("127.0.0.1", "localhost")))
      assertFalse(f.local.isLocalPlayUrl(play.substringBeforeLast('/') + "/-1"))
      f.local.setResume(play, 456); assertEquals(456L, f.local.resolvedForUrl(play).spec.positionTicks)
      f.local.clearResume(play); assertEquals(0L, f.local.resolvedForUrl(play).spec.positionTicks)
      f.local.release(play); assertTrue(f.local.isLocalPlayUrl(play))
      f.local.resolvedForUrl(play); assertEquals(2, f.calls.get())
    }
  }

  @Test fun rangeResponseStreamsLengthAndSixHeadersWithoutHopHeaders() = runBlocking {
    Fixture().use { f ->
      f.upstream.createContext("/binary") { call ->
        assertEquals("bytes=2-4", call.requestHeaders.getFirst("Range"))
        assertEquals("1", call.requestHeaders.getFirst("icy-metadata"))
        assertEquals("*/*", call.requestHeaders.getFirst("Accept"))
        f.local.http.identityHeaders(f.server).forEach { (name, value) -> assertEquals(name, value, call.requestHeaders.getFirst(name)) }
        assertNull(call.requestHeaders.getFirst("X-Emby-Authorization"))
        exchange(206, "cde".toByteArray(), mapOf("Content-Range" to "bytes 2-4/8", "Keep-Alive" to "timeout=30", "Connection" to "close"))(call)
      }
      val stream = f.local.registerStream(f.server, f.origin + "/binary", "video")
      get(stream, "bytes=2-4").use { response ->
        assertEquals(206, response.code); assertEquals("3", response.header("Content-Length"))
        assertEquals("bytes 2-4/8", response.header("Content-Range"))
        assertNull(response.header("Keep-Alive")); assertEquals("cde", response.body.string())
      }
      get(stream.substringBeforeLast('/') + "/missing").use { assertEquals(404, it.code); assertEquals("没有找到对应的流媒体", it.body.string()) }
    }
  }

  @Test fun streamRedirectKeepsIdentityResolvesRelativeLocationAndTenAllowed() = runBlocking {
    Fixture().use { f ->
      f.upstream.createContext("/absolute", exchange(302, headers = mapOf("Location" to f.origin.replace("127.0.0.1", "localhost") + "/final")))
      f.upstream.createContext("/relative", exchange(302, headers = mapOf("Location" to "/final")))
      f.upstream.createContext("/missing", exchange(302))
      f.upstream.createContext("/final") {
        assertEquals("secret", it.requestHeaders.getFirst("X-Emby-Token")); exchange(200, "done".toByteArray())(it)
      }
      for (n in 0..11) f.upstream.createContext("/r$n", if (n == 0) exchange(200, "end".toByteArray())
        else exchange(302, headers = mapOf("Location" to "${f.origin}/r${n - 1}")))
      suspend fun stream(path: String) = f.local.registerStream(f.server, f.origin + path, "video")
      get(stream("/absolute")).use { assertEquals("done", it.body.string()) }
      get(stream("/relative")).use { assertEquals(200, it.code); assertEquals("done", it.body.string()) }
      get(stream("/missing")).use { assertEquals("手动重定向失败，媒体流响应没有 Location 头", it.body.string()) }
      get(stream("/r10")).use { assertEquals("end", it.body.string()) }
      get(stream("/r11")).use { assertEquals(503, it.code); assertEquals("重定向次数过多", it.body.string()) }
    }
  }

  @Test fun allFiveHlsMimeRewriteRelativeLinesAndUriAttributesWithOwnership() = runBlocking {
    Fixture().use { f ->
      val text = listOf(
        "#EXTM3U",
        "#EXT-X-KEY:METHOD=AES-128,URI=\"keys/key.bin\"",
        "#EXT-X-MAP:URI=\"/init.mp4\"",
        "child/index.m3u8",
        "segments/one.ts",
        "/root.ts",
        "${f.origin}/absolute.ts",
        "",
      ).joinToString("\r\n")
      val types = listOf(
        "application/vnd.apple.mpegurl", "APPLICATION/X-MPEGURL", "Application/MpegUrl",
        "AuDiO/MpEgUrL", "AUDIO/X-MPEGURL",
      )
      types.forEachIndexed { i, mime ->
        f.upstream.createContext("/manifests/hls$i", exchange(
          200, text.toByteArray(), mapOf("Content-Type" to "$mime; charset=utf-8"),
        ))
      }
      val payloads = linkedMapOf(
        "/manifests/keys/key.bin" to "key",
        "/init.mp4" to "init",
        "/manifests/child/index.m3u8" to "child",
        "/manifests/segments/one.ts" to "one",
        "/root.ts" to "root",
        "/absolute.ts" to "absolute",
      )
      payloads.forEach { (path, payload) -> f.upstream.createContext(path, exchange(200, payload.toByteArray())) }
      for (i in types.indices) {
        val play = f.local.register(PlaybackSpec(f.server, "item", overrideUrl = "${f.origin}/manifests/hls$i"))
        val result = f.local.resolvedForUrl(play)
        get(result.videoUrl).use {
          assertEquals(200, it.code)
          assertEquals("${types[i]}; charset=utf-8", it.header("Content-Type"))
          val body = it.body.string()
          assertFalse(body.contains("keys/key.bin")); assertFalse(body.contains("/init.mp4\""))
          assertFalse(body.contains("child/index.m3u8")); assertFalse(body.contains("segments/one.ts"))
          assertFalse(body.contains(f.origin + "/absolute.ts"))
          val urls = Regex("http://127\\.0\\.0\\.1:[0-9]+/(?:stream/[^/]+/[^\\s\"]+)")
            .findAll(body).map { match -> match.value }.toList()
          assertEquals(payloads.size, urls.size)
          urls.forEach { assertTrue(f.local.ownsMediaUrl(play, it)) }
          assertEquals(payloads.values.toSet(), urls.map { local -> get(local).use { child -> child.body.string() } }.toSet())
        }
      }
    }
  }

  @Test fun redirectedMasterAndSuffixlessChildrenLocalizeEveryOwnedLayerWithoutChangingRawQueries() = runBlocking {
    Fixture().use { f ->
      val query = "signature=a%2Fb%2Bc%25&literal=a+b&repeat=1&repeat=2&empty="
      val seen = CopyOnWriteArrayList<String>()
      fun context(path: String, code: Int, text: String = "", headers: Map<String, String> = emptyMap()) {
        f.upstream.createContext(path) { call ->
          seen += call.requestURI.toASCIIString()
          exchange(code, text.toByteArray(), headers)(call)
        }
      }
      context("/entry", 302, headers = mapOf("Location" to "/hls/root/master?$query"))
      context("/hls/root/master", 200, listOf(
        "#EXTM3U",
        "#EXT-X-STREAM-INF:BANDWIDTH=1000000",
        "renditions/child-0?$query",
        "#EXT-X-STREAM-INF:BANDWIDTH=2000000",
        "renditions/child-1?$query",
        "",
      ).joinToString("\r\n"), mapOf("Content-Type" to "application/vnd.apple.mpegurl"))
      for (i in 0..1) {
        context("/hls/root/renditions/child-$i", 302, headers = mapOf(
          "Location" to "../../final/playlist-$i?$query",
        ))
        val headers = if (i == 0) mapOf("Content-Type" to "text/plain") else emptyMap()
        context("/hls/final/playlist-$i", 200, listOf(
          "#EXTM3U",
          "#EXT-X-KEY:METHOD=AES-128,URI=\"keys/key-$i?$query\"",
          "#EXT-X-MAP:URI=\"/init/map-$i?$query\"",
          "#EXTINF:10,",
          "../segments/segment-$i?$query",
          "#EXT-X-ENDLIST",
        ).joinToString("\n"), headers)
        context("/hls/final/keys/key-$i", 200, "key-$i")
        context("/init/map-$i", 200, "map-$i")
        context("/hls/segments/segment-$i", 200, "segment-$i")
      }
      val play = f.local.register(PlaybackSpec(f.server, "item", overrideUrl = "${f.origin}/entry"))
      val master = get(play).use { redirect ->
        assertEquals(308, redirect.code)
        redirect.header("Location")!!
      }
      assertOwned(f, play, listOf(master))
      val children = get(master).use { response ->
        assertEquals(200, response.code)
        mediaUrls(response.body.string())
      }
      assertEquals(2, children.size)
      assertOwned(f, play, children)
      children.forEachIndexed { i, child ->
        val resources = get(child).use { response ->
          assertEquals(200, response.code)
          val text = response.body.string()
          assertFalse(text.contains("keys/key-$i"))
          assertFalse(text.contains("../segments"))
          mediaUrls(text)
        }
        assertEquals(3, resources.size)
        assertOwned(f, play, resources)
        resources.forEachIndexed { index, resource ->
          get(resource).use { response ->
            assertEquals(200, response.code)
            assertEquals(listOf("key-$i", "map-$i", "segment-$i")[index], response.body.string())
          }
        }
      }
      val expected = listOf("/entry", "/hls/root/master?$query") + (0..1).flatMap { i ->
        listOf(
          "/hls/root/renditions/child-$i?$query", "/hls/final/playlist-$i?$query",
          "/hls/final/keys/key-$i?$query", "/init/map-$i?$query", "/hls/segments/segment-$i?$query",
        )
      }
      assertEquals(expected, seen.toList())
      assertEquals(1, f.calls.get())
    }
  }

  @Test fun selectedGatewayRoutesCrossHostLinksOnceAndResolvesRootRelativeAgainstLogicalUpstream() = runBlocking {
    Fixture().use { f ->
      // Both hosts are local: even a broken implementation cannot contact an external service.
      val cross = "http://localhost:${f.upstream.address.port}"
      val prefix = "http://localhost:${f.upstream.address.port}/gateway/"
      val query = "signature=x%2Fy%2Bz%25&literal=one+two&empty="
      val routed = CopyOnWriteArrayList<String>()
      val direct = CopyOnWriteArrayList<String>()
      val server = f.server.copy(
        reverseProxyId = "fixture-proxy",
        reverseProxyUrl = prefix.replace("http://localhost", "HTTP://LOCALHOST").removeSuffix("/"),
      )
      val master = listOf(
        "#EXTM3U",
        "#EXT-X-KEY:METHOD=AES-128,URI=\"../keys/master?$query\"",
        "#EXT-X-MAP:URI=\"$prefix$cross/root-map?$query\"",
        "child?$query",
      ).joinToString("\n")
      val child = listOf(
        "#EXTM3U",
        "#EXT-X-KEY:METHOD=AES-128,URI=\"/keys/root?$query\"",
        "#EXT-X-MAP:URI=\"$prefix$cross/init/child?$query\"",
        "/segments/root?$query",
      ).joinToString("\n")
      val responses = linkedMapOf(
        "${f.origin}/emby/Items/item/PlaybackInfo" to f.payload,
        "$cross/hls/master?$query" to master,
        "$cross/keys/master?$query" to "master-key",
        "$cross/root-map?$query" to "master-map",
        "$cross/hls/child?$query" to child,
        "$cross/keys/root?$query" to "child-key",
        "$cross/init/child?$query" to "child-map",
        "$cross/segments/root?$query" to "segment",
      )
      f.upstream.createContext("/gateway/") { call ->
        val logical = call.requestURI.toASCIIString().removePrefix("/gateway/")
        routed += logical
        val text = responses[logical]
        val headers = if (logical == "$cross/hls/master?$query") {
          mapOf("Content-Type" to "application/vnd.apple.mpegurl")
        } else emptyMap()
        exchange(if (text == null) 404 else 200, text.orEmpty().toByteArray(), headers)(call)
      }
      f.upstream.createContext("/entry") { call ->
        direct += call.requestURI.toASCIIString()
        exchange(302, headers = mapOf("Location" to "$cross/hls/master?$query"))(call)
      }
      f.upstream.createContext("/hls/") { call ->
        direct += call.requestURI.toASCIIString()
        exchange(404)(call)
      }
      val play = f.local.register(PlaybackSpec(server, "item", overrideUrl = "${f.origin}/entry"))
      val localMaster = get(play).use { redirect ->
        assertEquals(308, redirect.code)
        redirect.header("Location")!!
      }
      assertOwned(f, play, listOf(localMaster))
      val masterLinks = get(localMaster).use { response ->
        assertEquals(200, response.code)
        mediaUrls(response.body.string())
      }
      assertEquals(3, masterLinks.size)
      assertOwned(f, play, masterLinks)
      masterLinks.take(2).forEachIndexed { index, url ->
        get(url).use { response ->
          assertEquals(200, response.code)
          assertEquals(listOf("master-key", "master-map")[index], response.body.string())
        }
      }
      val childLinks = get(masterLinks.last()).use { response ->
        assertEquals(200, response.code)
        mediaUrls(response.body.string())
      }
      assertEquals(3, childLinks.size)
      assertOwned(f, play, childLinks)
      childLinks.forEachIndexed { index, url ->
        get(url).use { response ->
          assertEquals(200, response.code)
          assertEquals(listOf("child-key", "child-map", "segment")[index], response.body.string())
        }
      }
      assertEquals(listOf("/entry"), direct.toList())
      assertEquals(responses.keys.toList(), routed.toList())
      assertFalse(routed.any { it.startsWith(prefix) || "/gateway/" in it })
    }
  }

  @Test fun hlsMimeNormalizationMatchesOnlyExactTypesIgnoringCaseAndParameters() = runBlocking {
    Fixture().use { f ->
      // No marker: successful rewriting here can only be MIME-based, not the sniff fallback.
      val text = "segment-no-suffix\r\n"
      val supported = listOf(
        "Application/Vnd.Apple.MpegUrl; charset=utf-8", "APPLICATION/X-MPEGURL; ignored=yes",
        "application/mpegurl", "AuDiO/MpEgUrL", "audio/x-mpegurl; charset=UTF-8",
      )
      val unsupported = listOf(
        "application/vnd.apple.mpegurl-extra", "application/x-mpegurl-json", "application/mpegurl2",
        "audio/mpegurl-extra", "audio/x-mpegurl-invalid", "application/octet-stream",
      )
      (supported + unsupported).forEachIndexed { index, type ->
        f.upstream.createContext("/mime/$index", exchange(200, text.toByteArray(), mapOf("Content-Type" to type)))
        val stream = f.local.registerStream(f.server, "${f.origin}/mime/$index", "video")
        get(stream).use { response ->
          assertEquals(200, response.code)
          val body = response.body.string()
          if (index < supported.size) {
            assertEquals(1, mediaUrls(body).size)
            assertFalse(body.contains("segment-no-suffix"))
          } else {
            assertEquals(text, body)
          }
        }
      }
    }
  }

  @Test fun decodedCompressedBomAndWhitespaceManifestsAreSniffedWithoutLosingThePrefix() = runBlocking {
    Fixture().use { f ->
      val text = "\uFEFF \t\r\n#EXTM3U\r\n#EXTINF:1,\r\nsegment\r\n#EXT-X-ENDLIST\r\n"
      val bytes = text.toByteArray()
      val variants = linkedMapOf(
        "identity" to bytes,
        "gzip" to ByteArrayOutputStream().also { GZIPOutputStream(it).use { out -> out.write(bytes) } }.toByteArray(),
        "deflate" to ByteArrayOutputStream().also {
          DeflaterOutputStream(it).use { out -> out.write(bytes) }
        }.toByteArray(),
        "zstd" to Zstd.compress(bytes),
      )
      f.upstream.createContext("/sniff/segment", exchange(200, "segment-payload".toByteArray()))
      for ((encoding, payload) in variants) {
        val headers = buildMap {
          if (encoding != "identity") put("Content-Encoding", encoding)
          if (encoding != "zstd") put("Content-Type", "application/octet-stream")
        }
        f.upstream.createContext("/sniff/$encoding", exchange(200, payload, headers))
        val stream = f.local.registerStream(f.server, "${f.origin}/sniff/$encoding", "video")
        get(stream).use { response ->
          assertEquals(200, response.code)
          assertNull(response.header("Content-Encoding"))
          val rewritten = response.body.string()
          assertTrue(rewritten.contains("#EXTM3U\n#EXTINF:1,"))
          assertTrue(rewritten.endsWith("#EXT-X-ENDLIST"))
          val urls = mediaUrls(rewritten)
          assertEquals(1, urls.size)
          get(urls.single()).use { segment ->
            assertEquals(200, segment.code)
            assertEquals("segment-payload", segment.body.string())
          }
        }
      }
    }
  }

  @Test fun sniffLeavesBinaryRangeAndMarkerLookalikesUnchangedWithBoundedLeadingWhitespace() = runBlocking {
    Fixture().use { f ->
      val cases = listOf(
        byteArrayOf(0, 0xff.toByte(), 2) + "#EXTM3U\nsegment\n".toByteArray(),
        "#EXTM3UX\nsegment\n".toByteArray(),
        " \t#EXTM3U-invalid\nsegment\n".toByteArray(),
        (" ".repeat(600) + "#EXTM3U\nsegment\n").toByteArray(),
        ByteArray(100000) { (it % 251).toByte() },
      )
      cases.forEachIndexed { index, payload ->
        val ranges = CopyOnWriteArrayList<String>()
        f.upstream.createContext("/not-hls/$index") { call ->
          ranges += call.requestHeaders.getFirst("Range").orEmpty()
          exchange(206, payload, mapOf(
            "Content-Type" to "application/octet-stream",
            "Content-Range" to "bytes 0-${payload.lastIndex}/${payload.size + 10}",
          ))(call)
        }
        val stream = f.local.registerStream(f.server, "${f.origin}/not-hls/$index", "video")
        get(stream, "bytes=0-${payload.lastIndex}").use { response ->
          assertEquals(206, response.code)
          assertEquals(payload.size.toString(), response.header("Content-Length"))
          assertEquals("bytes 0-${payload.lastIndex}/${payload.size + 10}", response.header("Content-Range"))
          assertArrayEquals(payload, response.body.bytes())
        }
        assertEquals(listOf("bytes=0-${payload.lastIndex}"), ranges.toList())
      }
    }
  }

  @Test(timeout = 12000) fun invalidGzipReturns503ButEncodedEmptyRedirectStillReachesItsTarget() = runBlocking {
    Fixture().use { f ->
      f.upstream.createContext("/invalid-gzip", exchange(200, "not-a-gzip-header".toByteArray(), mapOf(
        "Content-Type" to "application/octet-stream", "Content-Encoding" to "gzip",
      )))
      f.upstream.createContext("/encoded-redirect", exchange(302, headers = mapOf(
        "Content-Encoding" to "gzip", "Location" to "/gzip-target",
      )))
      f.upstream.createContext("/gzip-target", exchange(200, "target".toByteArray()))
      val boundedBrowser = browser.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
      val failed = f.local.registerStream(f.server, "${f.origin}/invalid-gzip", "video")
      boundedBrowser.newCall(Request.Builder().url(failed).build()).execute().use { response ->
        assertEquals(503, response.code)
        assertFalse(response.body.string().contains("not-a-gzip-header"))
      }
      val redirect = f.local.registerStream(f.server, "${f.origin}/encoded-redirect", "video")
      boundedBrowser.newCall(Request.Builder().url(redirect).build()).execute().use { response ->
        assertEquals(200, response.code)
        assertEquals("target", response.body.string())
      }
    }
  }

  @Test(timeout = 12000) fun truncatedTypedAndSniffedManifestsReturn503InsteadOfPartialText() = runBlocking {
    Fixture().use { f ->
      val partial = "#EXTM3U\n#EXTINF:1,\npartial-segment\n".toByteArray()
      val compressed = ByteArrayOutputStream().also {
        GZIPOutputStream(it).use { out -> out.write(partial) }
      }.toByteArray().dropLast(8).toByteArray()
      val cases = listOf(
        Triple("application/vnd.apple.mpegurl", partial, ""),
        Triple("application/octet-stream", partial, ""),
        Triple("application/octet-stream", compressed, "Content-Encoding: gzip\r\n"),
      )
      // HttpServer's fixed-length writer may leave an underwritten response open. Close a raw socket explicitly.
      ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { upstream ->
        upstream.soTimeout = 3000
        val executor = Executors.newSingleThreadExecutor()
        try {
          val served = executor.submit {
            for ((type, payload, encoding) in cases) upstream.accept().use { socket ->
              socket.soTimeout = 3000
              val request = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
              while (!request.readLine().isNullOrEmpty()) Unit
              val length = payload.size + if (encoding.isEmpty()) 100 else 0
              val headers = "HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: $length\r\n" +
                encoding + "Connection: close\r\n\r\n"
              socket.getOutputStream().write(headers.toByteArray(Charsets.US_ASCII) + payload)
              socket.getOutputStream().flush()
              socket.shutdownOutput()
            }
          }
          val boundedBrowser = browser.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()
          for (index in cases.indices) {
            val stream = f.local.registerStream(f.server, "http://127.0.0.1:${upstream.localPort}/$index", "video")
            boundedBrowser.newCall(Request.Builder().url(stream).build()).execute().use { response ->
              assertEquals(503, response.code)
              val text = response.body.string()
              assertFalse(text.contains("#EXTM3U"))
              assertFalse(text.contains("partial-segment"))
              assertTrue(mediaUrls(text).isEmpty())
            }
          }
          served.get(3, TimeUnit.SECONDS)
          Unit
        } finally {
          executor.shutdownNow()
        }
      }
    }
  }

  @Test fun subtitleHitMissAndPrecacheOnlyComplete200Files() = runBlocking {
    Fixture().use { f ->
      val subtitles = AtomicInteger()
      f.upstream.createContext("/subtitle") { subtitles.incrementAndGet(); exchange(200, "subtitle".toByteArray())(it) }
      val upstream = f.origin + "/subtitle"
      val local = f.local.registerSubtitle(f.server, upstream)
      get(local).use { assertEquals("subtitle", it.body.string()) }
      assertFalse(f.local.subtitleCache.fileForUrl(upstream).exists())
      f.local.subtitleCache.precache(f.local.scope, f.server, upstream)!!.join()
      assertTrue(f.local.subtitleCache.fileForUrl(upstream).isFile)
      get(local).use { assertEquals(200, it.code); assertEquals("subtitle", it.body.string()); assertNull(it.header("Content-Type")) }
      assertEquals(2, subtitles.get())
      f.upstream.createContext("/failed", exchange(404, "missing".toByteArray()))
      f.local.subtitleCache.precache(f.local.scope, f.server, f.origin + "/failed")!!.join()
      assertFalse(f.local.subtitleCache.fileForUrl(f.origin + "/failed").exists())
      assertFalse(f.directory.walk().any { it.name.endsWith(".tmp") })
      f.local.subtitleCache.clear(); assertFalse(f.local.subtitleCache.fileForUrl(upstream).exists())
    }
  }

  @Test fun failedOptionalSubtitlePrefetchDoesNotNotifyButMediaFailureStillDoes() = runBlocking {
    Fixture().use { f ->
      val errors = java.util.concurrent.CopyOnWriteArrayList<LoopbackPlaybackServer.PlaybackNetworkError>()
      val collector = launch(start = CoroutineStart.UNDISPATCHED) { f.local.errors.collect { errors += it } }
      try {
        f.payload = """{"PlaySessionId":"s","MediaSources":[{"Id":"media","Container":"mkv",
          "DirectStreamUrl":"/video","MediaStreams":[{"Index":4,"Type":"Subtitle",
          "IsExternal":true,"Codec":"ass"}]}]}"""
        f.upstream.createContext("/emby/Videos/item/media/Subtitles/4/Stream.ass", exchange(503))
        f.upstream.createContext("/emby/video", exchange(200, "playing".toByteArray()))
        f.upstream.createContext("/media-failure", exchange(503))
        val play = f.local.register(PlaybackSpec(f.server, "item"))
        val result = f.local.resolvedForUrl(play)
        val subtitle = f.origin + "/emby/Videos/item/media/Subtitles/4/Stream.ass"
        // Join the identical single-flight cache job launched during preparation.
        f.local.subtitleCache.precache(f.local.scope, f.server, subtitle)?.join()
        get(result.videoUrl).use { assertEquals(200, it.code); assertEquals("playing", it.body.string()) }
        delay(50)
        assertTrue(errors.isEmpty())
        assertFalse(f.local.subtitleCache.fileForUrl(subtitle).exists())
        val failedMedia = f.local.registerStream(f.server, f.origin + "/media-failure", "video")
        get(failedMedia).use { assertEquals(503, it.code) }
        withTimeout(3000) { while (errors.isEmpty()) delay(10) }
        assertEquals(503, errors.single().status)
        assertTrue(errors.single().message.startsWith("媒体流响应错误"))
      } finally {
        collector.cancel()
        collector.join()
      }
    }
  }

  @Test fun freshPlaybackInfoAutoSelectionValidatesSelectedVersionNotStaleFirstSource() = runBlocking {
    Fixture().use { f ->
      f.config = playbackConfigurationForTest(
        values = mapOf("play_version_auto_select_policy" to "high-resolution"),
        source = app.marlboroadvance.mpvex.domain.postgres.PostgresSourceToken("source", 1),
        serverUuid = "uuid",
      )
      f.payload = """{"PlaySessionId":"s","MediaSources":[
        {"Id":"iso","Name":"ISO","Container":"iso","MediaStreams":[{"Type":"Video","Width":640,"Height":360}]},
        {"Id":"video","Name":"4K","Container":"mkv","DirectStreamUrl":"/selected","MediaStreams":[{"Type":"Video","Width":3840,"Height":2160}]}
      ]}"""
      val play = f.local.register(PlaybackSpec(f.server, "item"))
      val resolved = f.local.resolvedForUrl(play)
      assertEquals("video", resolved.source.Id)
      assertEquals(1, resolved.sourceIndex)
      assertTrue(resolved.videoUrl.startsWith("http://127.0.0.1:"))

      val explicit = f.local.register(PlaybackSpec(f.server, "item", explicitMediaSourceId = "iso"))
      try { f.local.resolvedForUrl(explicit); fail() } catch (_: IllegalArgumentException) { }
    }
  }

  @Test fun preplayStartsExternalSubtitleCacheAndIsoSelectedDoesNotFallback() = runBlocking {
    Fixture().use { f ->
      f.payload = """{"PlaySessionId":"s","MediaSources":[{"Id":"media","Container":"mkv","MediaStreams":[{"Index":4,"Type":"Subtitle","IsExternal":true,"Codec":"ass"}]}]}"""
      f.upstream.createContext("/emby/Videos/item/media/Subtitles/4/Stream.ass", exchange(200, "cached".toByteArray()))
      val play = f.local.register(PlaybackSpec(f.server, "item"))
      f.local.resolvedForUrl(play)
      val target = f.local.subtitleCache.fileForUrl(f.origin + "/emby/Videos/item/media/Subtitles/4/Stream.ass")
      withTimeout(3000) { while (!target.exists()) delay(10) }
      assertEquals("cached", target.readText())
      f.payload = """{"MediaSources":[{"Id":"iso","Container":"iso"},{"Id":"ordinary","Container":"mkv"}]}"""
      val iso = f.local.register(PlaybackSpec(f.server, "item", explicitMediaSourceId = "iso"))
      try { f.local.resolvedForUrl(iso); fail() } catch (_: IllegalArgumentException) { }
    }
  }

  @Test fun directHttpApiImageFollowAndThirdPartyPostNoIdentityAndRetryFalse() = runBlocking {
    Fixture().use { f ->
      f.upstream.createContext("/redirect", exchange(302, headers = mapOf("Location" to "target")))
      f.upstream.createContext("/target") { assertEquals("secret", it.requestHeaders.getFirst("X-Emby-Token")); exchange(200, "ok".toByteArray())(it) }
      f.local.http.execute(f.server, f.origin + "/redirect", kind = LoopbackHttpPool.Kind.IMAGE).use { assertEquals("ok", it.body.string()) }
      val calls = AtomicInteger()
      f.upstream.createContext("/third") {
        calls.incrementAndGet(); assertEquals("POST", it.requestMethod)
        assertNull(it.requestHeaders.getFirst("X-Emby-Token"))
        assertEquals("{}", it.requestBody.bufferedReader().readText()); exchange(429)(it)
      }
      f.local.http.execute(url = f.origin + "/third", method = "POST", retry = false).use { assertEquals(429, it.code) }
      assertEquals(1, calls.get())
      assertEquals(0, f.local.http.client(kind = LoopbackHttpPool.Kind.STREAM).readTimeoutMillis)
      assertEquals(30000, f.local.http.client(kind = LoopbackHttpPool.Kind.IMAGE).readTimeoutMillis)
      assertNotSame(f.local.http.client(kind = LoopbackHttpPool.Kind.IMAGE), f.local.http.client(kind = LoopbackHttpPool.Kind.API))
      listOf(LoopbackHttpPool.Kind.STREAM, LoopbackHttpPool.Kind.IMAGE, LoopbackHttpPool.Kind.API).forEach {
        assertEquals(java.net.Proxy.NO_PROXY, f.local.http.client(kind = it).proxy)
      }
    }
  }

  @Test fun zstdPlaybackInfoAndHlsManifestResolveAndStreamAcrossRepeatedReads() = runBlocking {
    Fixture().use { f ->
      f.playbackEncoding = "zstd"
      f.payload = """{"PlaySessionId":"session","MediaSources":[{"Id":"media","Container":"m3u8","DirectStreamUrl":"/manifest","MediaStreams":[]}]}"""
      val manifest = "#EXTM3U\n#EXTINF:10,\nsegment.ts\n#EXT-X-ENDLIST\n"
      val segment = ByteArray(100000) { (it % 251).toByte() }
      f.upstream.createContext("/emby/manifest", exchange(200, Zstd.compress(manifest.toByteArray()), mapOf(
        "Content-Type" to "application/vnd.apple.mpegurl", "Content-Encoding" to "zstd",
      )))
      f.upstream.createContext("/emby/segment.ts", exchange(200, Zstd.compress(segment), mapOf(
        "Content-Encoding" to "zstd",
      )))
      val play = f.local.register(PlaybackSpec(f.server, "item"))
      repeat(3) {
        get(play).use { response ->
          assertEquals(308, response.code)
          val video = response.header("Location")!!
          get(video).use { playlist ->
            assertEquals(200, playlist.code)
            assertNull(playlist.header("Content-Encoding"))
            val text = playlist.body.string()
            assertTrue(text.startsWith("#EXTM3U\n"))
            val localSegment = text.lineSequence().single { it.startsWith("http://") }
            assertTrue(f.local.ownsMediaUrl(play, localSegment))
            get(localSegment).use { media ->
              assertEquals(200, media.code)
              assertNull(media.header("Content-Encoding"))
              assertNull(media.header("Content-Length"))
              assertArrayEquals(segment, media.body.bytes())
            }
          }
        }
        f.local.release(play)
      }
      assertEquals(3, f.calls.get())
    }
  }

  @Test fun compressedBodiesDecodedAndUpstreamLengthNotForwarded() = runBlocking {
    Fixture().use { f ->
      val bytes = "decompressed".toByteArray()
      fun encoded(write: (ByteArrayOutputStream) -> Unit): ByteArray = ByteArrayOutputStream().also(write).toByteArray()
      val variants = mapOf(
        "gzip" to encoded { GZIPOutputStream(it).use { out -> out.write(bytes) } },
        "deflate" to encoded { DeflaterOutputStream(it).use { out -> out.write(bytes) } },
        "zstd" to Zstd.compress(bytes),
        // Brotli uncompressed meta-block encoding of "hello".
        "br" to byteArrayOf(0x0b, 0x02, 0x80.toByte(), 0x68, 0x65, 0x6c, 0x6c, 0x6f, 0x03),
      )
      for ((encoding, data) in variants) {
        f.upstream.createContext("/encoded/$encoding", exchange(200, data, mapOf("Content-Encoding" to encoding)))
        val stream = f.local.registerStream(f.server, "${f.origin}/encoded/$encoding", "video")
        get(stream).use {
          assertNull(it.header("Content-Encoding")); assertNull(it.header("Content-Length"))
          assertEquals(if (encoding == "br") "hello" else "decompressed", it.body.string())
        }
      }
    }
  }
}
