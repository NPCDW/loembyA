package app.marlboroadvance.mpvex.domain.emby

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ReverseProxyRouteTest {
  @Test fun rawTargetsArePreservedAndSamePrefixIsNeverDuplicated() {
    val target = "https://example.test/path%2Fitem?q=a%2Bb&empty=&unicode=%E4%B8%AD#part"
    val route = ReverseProxyRoute.selected("row", " https://relay.test/gateway/ ")
    val expected = "https://relay.test/gateway/$target"
    assertEquals(expected, route.resolveUrl(target))
    assertEquals(expected, route.resolveUrl(expected))
    assertEquals("https://relay.test/gateway/https://relay.test.evil/path",
      route.resolveUrl("https://relay.test.evil/path"))
    assertEquals(target, ReverseProxyRoute.DIRECT.resolveUrl(target))
    assertEquals(target, applyReverseProxyUrl(target, "  "))
    assertEquals("file:///not-network", applyReverseProxyUrl("file:///not-network", "https://relay.test/"))
  }

  @Test fun redirectHeadersStayOnTheSelectedRouteIncludingAbsoluteRelativeAndAlreadyRoutedLocations() {
    val route = ReverseProxyRoute.selected("row", "https://relay.test/gateway")
    val request = route.resolveUrl("https://origin.test/path/item?q=old")
    assertEquals("https://relay.test/gateway/https://cdn.test/raw%2F.png?q=a%2Bb",
      route.resolveRedirect(request, "https://cdn.test/raw%2F.png?q=a%2Bb"))
    assertEquals("https://relay.test/gateway/https://origin.test/new%2F.png?q=a%2Bb",
      route.resolveRedirect(request, "/new%2F.png?q=a%2Bb"))
    assertEquals("https://relay.test/gateway/https://origin.test/path/next.png",
      route.resolveRedirect(request, "next.png"))
    assertEquals("https://relay.test/gateway/https://cdn.test/asset",
      route.resolveRedirect(request, "//cdn.test/asset"))
    assertEquals("https://relay.test/gateway/https://origin.test/path/item?q=a%2Bb",
      route.resolveRedirect(request, "?q=a%2Bb"))
    assertEquals("https://relay.test/gateway/https://origin.test/path/item?q=old#part",
      route.resolveRedirect(request, "#part"))
    val routed = route.resolveUrl("https://cdn.test/icon")
    assertEquals(routed, route.resolveRedirect(request, routed))
  }

  @Test fun canonicalRequestAuthoritiesKeepDefaultPortsAndCaseFromDuplicatingThePrefix() {
    val route = ReverseProxyRoute.selected("row", "https://RELAY.test:443/gateway/")
    val raw = route.resolveUrl("https://cdn.test/icon%2F.png?q=a%2Bb")
    val canonical = raw.toHttpUrl().toString()
    assertEquals("https://relay.test/gateway/https://cdn.test/icon%2F.png?q=a%2Bb", canonical)
    assertEquals(canonical, route.resolveUrl(canonical))
    assertEquals(canonical, route.resolveRedirect(canonical, canonical))
    assertEquals("https://RELAY.test:443/gateway/https://cdn.test/icon%2F.png?q=new%2Bvalue",
      route.resolveRedirect(canonical, "?q=new%2Bvalue"))
  }

  @Test fun selectedMissingMalformedOrCredentialBearingPrefixesFailClosed() {
    listOf(null, "", " ", "invalid", "file:///proxy", "https://user:secret@relay.test/",
      "https://relay.test:0/", "https://relay.test:65536/", "https://relay.test/r?secret=1", "https://relay.test/#x",
    ).forEach { prefix ->
      val route = ReverseProxyRoute.selected("selected", prefix)
      assertEquals(ReverseProxyRoute.State.BLOCKED, route.state)
      assertThrows(IllegalStateException::class.java) { route.resolveUrl("https://target.test/") }
      assertFalse(route.toString().contains("secret"))
    }
    listOf(null, "", "no").forEach { selection ->
      assertEquals(ReverseProxyRoute.State.DIRECT, ReverseProxyRoute.selected(selection, null).state)
    }
    listOf("file:///private", "https://user:secret@target.test/", "http://target.test:0/", "not-http").forEach {
      assertThrows(IllegalArgumentException::class.java) { ReverseProxyRoute.DIRECT.resolveUrl(it) }
    }
  }

  @Test fun opaqueRouteFingerprintChangesForSelectionOrEndpointAndNeverRendersEither() {
    val a = ReverseProxyRoute.selected("private-row", "https://private-relay.test/r/")
    assertEquals(64, a.fingerprint.length)
    assertEquals(a.fingerprint, ReverseProxyRoute.selected("private-row", " https://private-relay.test/r ").fingerprint)
    assertNotEquals(a.fingerprint, ReverseProxyRoute.selected("other-row", "https://private-relay.test/r/").fingerprint)
    assertNotEquals(a.fingerprint, ReverseProxyRoute.selected("private-row", "https://other-relay.test/r/").fingerprint)
    assertFalse(a.toString().contains("private-row"))
    assertFalse(a.toString().contains("private-relay"))
  }
}
