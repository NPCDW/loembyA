package app.marlboroadvance.mpvex.domain.emby.playback

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LoopbackHttpPoolRoutingTest {
  @Test fun noRedirectPolicyNeverReplaysPostOrLeaksAuthorizationToOtherOrigin() = runBlocking {
    val first = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val second = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val firstCalls = AtomicInteger()
    val secondCalls = AtomicInteger()
    val pool = LoopbackHttpPool()
    second.createContext("/target") { call ->
      secondCalls.incrementAndGet()
      call.sendResponseHeaders(200, -1)
      call.close()
    }
    first.createContext("/redirect") { call ->
      firstCalls.incrementAndGet()
      assertEquals("POST", call.requestMethod)
      assertEquals("Bearer test-marker", call.requestHeaders.getFirst("Authorization"))
      assertNull(call.requestHeaders.getFirst("X-Emby-Token"))
      call.responseHeaders.add("Location", "http://127.0.0.1:${second.address.port}/target")
      call.sendResponseHeaders(307, -1)
      call.close()
    }
    first.start(); second.start()
    try {
      pool.execute(
        url = "http://127.0.0.1:${first.address.port}/redirect", method = "POST", body = "{}",
        headers = mapOf("Authorization" to "Bearer test-marker"), retry = false,
        redirectPolicy = LoopbackHttpPool.RedirectPolicy.NONE,
      ).use { response ->
        assertEquals(307, response.code)
        assertNotNull(response.header("Location"))
      }
      assertEquals(1, firstCalls.get())
      assertEquals(0, secondCalls.get())
    } finally {
      pool.close(); first.stop(0); second.stop(0)
    }
  }

  @Test fun retryFalseKeepsTransientPostFailureToOneAttempt() = runBlocking {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val calls = AtomicInteger()
    val pool = LoopbackHttpPool()
    server.createContext("/write") { call ->
      calls.incrementAndGet()
      call.sendResponseHeaders(503, -1)
      call.close()
    }
    server.start()
    try {
      pool.execute(
        url = "http://127.0.0.1:${server.address.port}/write", method = "POST", body = "{}", retry = false,
        redirectPolicy = LoopbackHttpPool.RedirectPolicy.NONE,
      ).use { assertEquals(503, it.code) }
      assertEquals(1, calls.get())
    } finally {
      pool.close(); server.stop(0)
    }
  }
}
