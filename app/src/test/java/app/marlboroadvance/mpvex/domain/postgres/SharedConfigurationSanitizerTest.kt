package app.marlboroadvance.mpvex.domain.postgres

import android.os.Parcelable
import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import java.io.Serializable
import org.junit.Assert.*
import org.junit.Test

class SharedConfigurationSanitizerTest {
  @Test fun `sanitizer strips all secret canaries controls bidi and desktop only values`() {
    val canaries = listOf(
      "PASSWORD-CANARY", "TOKEN-CANARY", "WEBHOOK-CANARY", "DEVICE-CANARY", "USER-ID-CANARY",
    )
    val raw = rawSnapshot(
      values = mapOf(
        "play_version_auto_select_policy" to "high-resolution\u0000\u202Ehidden",
        "trakt_username" to "account@example",
        "trakt_access_token" to canaries[1],
        "trakt_refresh_token" to "REFRESH-CANARY",
        "trakt_redirect_uri" to "https://private.redirect.example:9443/secret/path?code=${canaries[1]}",
        "simkl_access_token" to "SIMKL-CANARY",
        "yamtrack_sync_url" to "https://hook.private.example/webhooks/${canaries[2]}",
      ),
      server = serverRaw(
        password = canaries[0], authToken = canaries[1], userId = canaries[4],
        deviceId = canaries[3], userAgent = "agent/${canaries[3]}",
      ),
    )
    val snapshot = SharedConfigurationSanitizer.sanitize(raw)
    val rendered = render(snapshot)

    canaries.forEach { assertFalse(it, rendered.contains(it)) }
    listOf("REFRESH-CANARY", "SIMKL-CANARY", "private.redirect", "secret/path", "hook.private", "webhooks").forEach {
      assertFalse(it, rendered.contains(it))
    }
    assertFalse(rendered.contains('\u0000'))
    assertFalse(rendered.contains('\u202E'))
    assertFalse(rendered.contains("desktop_only"))
    assertTrue(rendered.contains("已配置"))
    assertTrue(rendered.contains("含路径前缀"))
    assertTrue(rendered.contains("https://p***e.redirect.example:9443"))
    assertFalse(Serializable::class.java.isAssignableFrom(snapshot.javaClass))
    assertFalse(Parcelable::class.java.isAssignableFrom(snapshot.javaClass))
    assertFalse(snapshot.toString().contains("account"))
  }

  @Test fun `endpoint exposes only sanitized scheme host port and path presence`() {
    assertEquals("https://m***a.example:8443 · 含路径前缀", SharedConfigurationSanitizer.sanitizeEndpoint("https://media.example:8443/emby?q=token#fragment"))
    assertEquals("http://l***t · 无路径前缀", SharedConfigurationSanitizer.sanitizeEndpoint("http://localhost"))
    assertEquals("https://[IPv6]:9443 · 含路径前缀", SharedConfigurationSanitizer.sanitizeEndpoint("https://[2001:db8::7]:9443/prefix"))
    assertEquals("地址无效", SharedConfigurationSanitizer.sanitizeEndpoint("https://user:password@example.com/path"))
    assertEquals("未配置", SharedConfigurationSanitizer.sanitizeEndpoint(""))
  }

  @Test fun `server projection shows approved session line reverse route and credential statuses only`() {
    val row = serverRaw()
    val raw = rawSnapshot(server = row)
    val shared = EmbyServer(
      name = "Media", host = "media.example", username = "u", password = "p", userId = row.userId,
      apiToken = row.authToken, remoteId = row.id, sourceKey = raw.source.sourceKey, sourceRevision = raw.source.revision,
    )
    val relogin = shared.copy(apiToken = "memory-token", userId = "memory-user")
    val sharedText = render(SharedConfigurationSanitizer.sanitize(raw, mapOf(row.id to shared)))
    val reloginText = render(SharedConfigurationSanitizer.sanitize(raw, mapOf(row.id to relogin)))
    val loggedOutText = render(SharedConfigurationSanitizer.sanitize(raw))

    assertTrue(sharedText.contains("共享 Token"))
    assertTrue(reloginText.contains("本进程重登录"))
    assertTrue(loggedOutText.contains("未登录"))
    assertTrue(sharedText.contains("引用有效"))
    assertTrue(sharedText.contains("反向代理"))
    assertFalse(sharedText.contains("浏览代理"))
    assertFalse(sharedText.contains("播放代理"))
    assertTrue(sharedText.contains("桌面端已应用到服务器行"))
    assertFalse(sharedText.contains("server-password"))
    assertFalse(sharedText.contains("shared-token"))
    assertFalse(sharedText.contains("shared-user-id"))
  }

  @Test fun `unknown global key is rejected instead of displayed`() {
    val raw = rawSnapshot().copy(values = mapOf("mpv_volume" to "87"))
    try {
      SharedConfigurationSanitizer.sanitize(raw)
      fail("Expected unknown-key rejection")
    } catch (_: IllegalArgumentException) {
      // exact allowlist boundary
    }
  }

  @Test fun `app and tracker routes display only sanitized selection status without forward proxies`() {
    val raw = rawSnapshot(values = mapOf(
      "app_reverse_proxy_id" to "app-private-id",
      "trakt_reverse_proxy_id" to "trakt-private-id",
      "simkl_reverse_proxy_id" to "missing-private-id",
      "yamtrack_reverse_proxy_id" to "invalid-private-id",
    )).copy(reverseProxies = mapOf(
      "app-private-id" to SharedReverseProxyRaw("app-private-id", "App Gateway", "https://apprelay.test/PRIVATE-PATH?PRIVATE-QUERY"),
      "trakt-private-id" to SharedReverseProxyRaw("trakt-private-id", "Tracker Gateway", "https://tracker.test/private-path"),
      "invalid-private-id" to SharedReverseProxyRaw("invalid-private-id", "Invalid", "https://user:PRIVATE-PASSWORD@invalid.test/"),
      "unused" to SharedReverseProxyRaw("unused", "UNUSED-CANARY", "https://unused.test/"),
    ))
    val snapshot = SharedConfigurationSanitizer.sanitize(raw)
    val app = snapshot.sections.single { it.title == "网络与字幕" }.items.first()
    assertEquals("应用数据、外部图标与更新反向代理", app.label)
    assertEquals("引用无效；请求已阻止", app.value)
    val trakt = snapshot.sections.single { it.title == "Trakt" }.items.first()
    assertTrue(trakt.value.contains("Tracker Gateway"))
    assertTrue(trakt.value.contains("https://t***r.test"))
    assertEquals("引用有效", trakt.info)
    assertEquals("引用无效；请求已阻止", snapshot.sections.single { it.title == "Simkl" }.items.first().value)
    assertEquals("引用无效；请求已阻止", snapshot.sections.single { it.title == "YamTrack" }.items.first().value)
    val rendered = render(snapshot)
    listOf("app-private-id", "trakt-private-id", "missing-private-id", "PRIVATE-PATH", "PRIVATE-QUERY",
      "PRIVATE-PASSWORD", "UNUSED-CANARY", "浏览代理", "播放代理").forEach {
      assertFalse(it, rendered.contains(it))
    }
  }

  @Test fun `missing route selection displays direct connection not a borrowed server route`() {
    val snapshot = SharedConfigurationSanitizer.sanitize(rawSnapshot())
    listOf("网络与字幕", "Trakt", "Simkl", "YamTrack").forEach { title ->
      val route = snapshot.sections.single { it.title == title }.items.first()
      assertEquals("未使用", route.value)
      assertEquals("直接连接", route.info)
    }
  }

  private fun render(snapshot: SharedConfigurationSnapshot): String = buildString {
    snapshot.sections.forEach { section ->
      append(section.title); append(section.summary)
      section.items.forEach { append(it.label); append(it.value); append(it.info) }
    }
    snapshot.servers.forEach { server ->
      append(server.title); append(server.summary)
      server.sections.forEach { section ->
        append(section.title)
        section.items.forEach { append(it.label); append(it.value); append(it.info) }
      }
    }
  }

  private fun rawSnapshot(
    values: Map<String, String> = emptyMap(),
    server: SharedServerRaw = serverRaw(),
  ) = SharedConfigurationRawSnapshot(
    values = values,
    servers = listOf(server),
    lines = listOf(SharedLineRaw("line-id", "Primary", server.id)),
    reverseProxies = mapOf("reverse-id" to SharedReverseProxyRaw("reverse-id", "Gateway", "https://gateway.example/reverse/path")),
    source = PostgresSourceToken("source", 3),
  )

  private fun serverRaw(
    password: String = "server-password",
    authToken: String = "shared-token",
    userId: String = "shared-user-id",
    deviceId: String = "device-id",
    userAgent: String = "user-agent",
  ) = SharedServerRaw(
    id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    createdAtMillis = 1_700_000_000_000L,
    baseUrl = "https://media.private.example:9443/emby",
    username = "viewer@example",
    password = password,
    serverName = "Media\u0007Server",
    authToken = authToken,
    userId = userId,
    client = "loemby",
    device = "Android",
    deviceId = deviceId,
    clientVersion = "1.0",
    userAgent = userAgent,
    orderBy = 1,
    disabled = false,
    iconUrl = "https://icons.private.example/art/server.png",
    lineId = "line-id",
    reverseProxyId = "reverse-id",
  )
}
