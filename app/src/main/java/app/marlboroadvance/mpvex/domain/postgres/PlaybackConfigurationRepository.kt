package app.marlboroadvance.mpvex.domain.postgres

import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.domain.emby.ReverseProxyRoute
import java.security.MessageDigest
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The complete and exact set of global_config keys that affect Android at runtime. */
internal val ANDROID_GLOBAL_CONFIG_ALLOWLIST: Set<String> = linkedSetOf(
  "play_version_auto_select_policy",
  "prefetch_playlist",
  "play_param_IsPlayback",
  "mpv_cache_seconds",
  "mpv_cache_min_bytes",
  "mpv_cache_max_bytes",
  "mpv_cache_back_seconds",
  "mpv_cache_back_min_bytes",
  "mpv_cache_back_max_bytes",
  "danger_accept_invalid_certs",
  "subtitleStoredDays",
  "lastCleanSubtitleTime",
  "app_reverse_proxy_id",
  "trakt_reverse_proxy_id",
  "simkl_reverse_proxy_id",
  "yamtrack_reverse_proxy_id",
  "trakt_sync_switch",
  "trakt_username",
  "trakt_access_token",
  "trakt_refresh_token",
  "trakt_expires_in",
  "trakt_redirect_uri",
  "simkl_sync_switch",
  "simkl_username",
  "simkl_access_token",
  "yamtrack_sync_switch",
  "yamtrack_sync_url",
)

class PlaybackCacheWindow internal constructor(
  val seconds: Long,
  val minimumMebibytes: Long,
  val maximumMebibytes: Long,
  private val fallbackMebibytes: Long,
) {
  /** Mirrors loemby's clamp order and its fixed fallback when bitrate is unavailable. */
  fun bytesFor(bitrate: Long?): Long {
    val fallback = fallbackMebibytes * MEBIBYTE
    if (bitrate == null || bitrate <= 0) return fallback
    val bytes = BigDecimal.valueOf(seconds.coerceAtLeast(0)).multiply(BigDecimal.valueOf(bitrate))
      .divide(BigDecimal.valueOf(8L), 0, RoundingMode.DOWN)
      .min(BigDecimal.valueOf(maximumMebibytes.coerceAtLeast(0)).multiply(BigDecimal.valueOf(MEBIBYTE)))
      .max(BigDecimal.valueOf(minimumMebibytes.coerceAtLeast(0)).multiply(BigDecimal.valueOf(MEBIBYTE)))
      .min(BigDecimal.valueOf(Long.MAX_VALUE)).toLong()
    return bytes.takeIf { it > 0 } ?: fallback
  }

  private companion object { const val MEBIBYTE = 1024L * 1024L }
}

class PlaybackCacheConfiguration internal constructor(
  val forward: PlaybackCacheWindow,
  val backward: PlaybackCacheWindow,
)

class SubtitleCacheConfiguration internal constructor(
  val storedDays: Long,
  val sharedLastCleanEpochSeconds: Long?,
) {
  companion object {
    val DEFAULT = SubtitleCacheConfiguration(storedDays = 1L, sharedLastCleanEpochSeconds = null)
  }
}

/** Sensitive tracker values are runtime-only; [toString] never renders them. */
class TraktRuntimeConfiguration internal constructor(
  val enabled: Boolean,
  val username: String,
  val accessToken: String,
  val refreshToken: String,
  val expiresAtSeconds: Long,
  val redirectUri: String,
  val reverseRoute: ReverseProxyRoute = ReverseProxyRoute.DIRECT,
) {
  internal fun fingerprintInput(): List<String> =
    listOf(accessToken, refreshToken, expiresAtSeconds.toString(), redirectUri, username, reverseRoute.fingerprint)

  override fun toString(): String =
    "TraktRuntimeConfiguration(enabled=$enabled, credentials=[redacted], endpoint=[redacted])"
}

class SimklRuntimeConfiguration internal constructor(
  val enabled: Boolean,
  val username: String,
  val accessToken: String,
  val reverseRoute: ReverseProxyRoute = ReverseProxyRoute.DIRECT,
) {
  override fun toString(): String =
    "SimklRuntimeConfiguration(enabled=$enabled, credentials=[redacted], endpoint=[redacted])"
}

class YamTrackRuntimeConfiguration internal constructor(
  val enabled: Boolean,
  val webhookUrl: String,
  val reverseRoute: ReverseProxyRoute = ReverseProxyRoute.DIRECT,
) {
  override fun toString(): String =
    "YamTrackRuntimeConfiguration(enabled=$enabled, webhook=[redacted])"
}

/** Public app data, icons and updater share the app key, independently of every Emby row/session. */
class AppNetworkConfiguration internal constructor(
  val source: PostgresSourceToken,
  val reverseRoute: ReverseProxyRoute,
  val dangerAcceptInvalidCertificates: Boolean,
) {
  val fingerprint: String = runtimeFingerprint(
    source.sourceKey, source.revision.toString(), reverseRoute.fingerprint, dangerAcceptInvalidCertificates.toString(),
  )
  fun resolveUrl(originalUrl: String): String = reverseRoute.resolveUrl(originalUrl)
  fun resolveRedirect(requestUrl: String, location: String): String = reverseRoute.resolveRedirect(requestUrl, location)
  override fun toString(): String = "AppNetworkConfiguration(source=$source, route=[redacted])"
}

internal fun runtimeFingerprint(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
  .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8))
  .joinToString("") { "%02x".format(it) }

/** Fully typed, process-local runtime configuration. No raw global_config map is exposed. */
class PlaybackConfiguration internal constructor(
  val automaticVersionPolicy: String,
  val prefetchPlaylist: Boolean,
  val isPlayback: Boolean,
  val cache: PlaybackCacheConfiguration,
  val dangerAcceptInvalidCertificates: Boolean,
  val subtitleCache: SubtitleCacheConfiguration,
  val trakt: TraktRuntimeConfiguration,
  val simkl: SimklRuntimeConfiguration,
  val yamTrack: YamTrackRuntimeConfiguration,
  val source: PostgresSourceToken,
  val serverUuid: String,
  private val serverBindingFingerprint: String = "",
) {
  internal val networkFingerprint: String = runtimeFingerprint(
    serverBindingFingerprint,
    *trakt.fingerprintInput().toTypedArray(), trakt.enabled.toString(), simkl.enabled.toString(), simkl.username,
    simkl.accessToken, simkl.reverseRoute.fingerprint, yamTrack.enabled.toString(), yamTrack.webhookUrl,
    yamTrack.reverseRoute.fingerprint, dangerAcceptInvalidCertificates.toString(),
  )
  override fun toString(): String =
    "PlaybackConfiguration(source=$source, serverUuid=$serverUuid, runtime=[redacted], credentials=[redacted])"
}

/** Raw values never leave the repository/sanitizer boundary and redact their string representations. */
internal data class SharedServerRaw(
  val id: String,
  val createdAtMillis: Long,
  val baseUrl: String,
  val username: String,
  val password: String,
  val serverName: String,
  val authToken: String,
  val userId: String,
  val client: String,
  val device: String,
  val deviceId: String,
  val clientVersion: String,
  val userAgent: String,
  val orderBy: Int,
  val disabled: Boolean,
  val iconUrl: String,
  val lineId: String,
  val reverseProxyId: String,
) {
  override fun toString(): String = "SharedServerRaw(id=[redacted], configuration=[redacted], credentials=[redacted])"
}

internal data class SharedLineRaw(
  val id: String,
  val name: String,
  val serverId: String,
) {
  override fun toString(): String = "SharedLineRaw(id=[redacted], name=[redacted], serverId=[redacted])"
}

internal data class SharedReverseProxyRaw(
  val id: String,
  val name: String,
  val url: String,
) {
  override fun toString(): String = "SharedReverseProxyRaw(id=[redacted], endpoint=[redacted])"
}

internal data class SharedConfigurationRawSnapshot(
  val values: Map<String, String>,
  val servers: List<SharedServerRaw>,
  val lines: List<SharedLineRaw>,
  val reverseProxies: Map<String, SharedReverseProxyRaw>,
  val source: PostgresSourceToken,
) {
  override fun toString(): String =
    "SharedConfigurationRawSnapshot(source=$source, values=[redacted], servers=${servers.size})"
}

class PlaybackConfigurationRepository(
  private val executor: PostgresExecutor,
  private val nowNanos: () -> Long = System::nanoTime,
) {
  private data class Cached(val snapshot: SharedConfigurationRawSnapshot, val loadedAtNanos: Long)

  private val mutex = Mutex()
  @Volatile private var cache: Cached? = null

  val sourceChanges get() = executor.sourceChanges
  suspend fun currentSource(): PostgresSourceToken = executor.currentSource()
  fun isCurrent(configuration: PlaybackConfiguration): Boolean {
    if (!executor.isCurrent(configuration.source)) return false
    val cached = cache?.snapshot?.takeIf { it.source == configuration.source } ?: return false
    return cached.servers.any { it.id == configuration.serverUuid } &&
      parsePlaybackConfiguration(cached, configuration.serverUuid).networkFingerprint == configuration.networkFingerprint
  }

  fun isCurrent(configuration: AppNetworkConfiguration): Boolean {
    if (!executor.isCurrent(configuration.source)) return false
    val cached = cache?.snapshot?.takeIf { it.source == configuration.source } ?: return false
    return parseAppNetworkConfiguration(cached).fingerprint == configuration.fingerprint
  }

  /** Refresh on the normal bounded TTL and reject an old route/credential snapshot before another request. */
  suspend fun validateCurrent(configuration: PlaybackConfiguration): Boolean {
    val raw = rawSnapshot(configuration.source)
    return executor.isCurrent(configuration.source) && raw.servers.any { it.id == configuration.serverUuid } &&
      parsePlaybackConfiguration(raw, configuration.serverUuid).networkFingerprint == configuration.networkFingerprint
  }

  suspend fun validateCurrent(configuration: AppNetworkConfiguration): Boolean {
    val raw = rawSnapshot(configuration.source)
    return executor.isCurrent(configuration.source) &&
      parseAppNetworkConfiguration(raw).fingerprint == configuration.fingerprint
  }

  suspend fun appNetworkConfiguration(
    expectedSource: PostgresSourceToken? = null,
    forceRefresh: Boolean = false,
  ): AppNetworkConfiguration = parseAppNetworkConfiguration(rawSnapshot(expectedSource, forceRefresh))

  suspend fun snapshot(server: EmbyServer): PlaybackConfiguration {
    val expected = PostgresSourceToken(server.sourceKey, server.sourceRevision)
    if (server.remoteId.isBlank() || expected.sourceKey.isBlank() || !executor.isCurrent(expected)) {
      throw PostgresException(PostgresFailure.SOURCE_CHANGED)
    }
    val raw = rawSnapshot(expected)
    val row = raw.servers.singleOrNull { it.id == server.remoteId }
      ?: throw PostgresException(PostgresFailure.NOT_FOUND)
    verifyServerBinding(server, row, raw)
    val result = parsePlaybackConfiguration(raw, row.id)
    if (!executor.isCurrent(expected)) throw PostgresException(PostgresFailure.SOURCE_CHANGED)
    return result
  }

  /** One repeatable-read transaction and one 60-second cache per exact source/revision token. */
  internal suspend fun rawSnapshot(
    expectedSource: PostgresSourceToken? = null,
    forceRefresh: Boolean = false,
  ): SharedConfigurationRawSnapshot = mutex.withLock {
    val expected = expectedSource ?: executor.currentSource()
    if (!executor.isCurrent(expected)) {
      cache = null
      throw PostgresException(PostgresFailure.SOURCE_CHANGED)
    }
    val now = nowNanos()
    cache?.takeIf {
      !forceRefresh && it.snapshot.source == expected &&
        now - it.loadedAtNanos in 0 until CACHE_TTL_NANOS && executor.isCurrent(expected)
    }?.let { return@withLock it.snapshot }
    // Retain the last identity only while refresh is in flight; rawSnapshot never serves expired data.
    val result = try { executor.read(expected.sourceKey) { session ->
      if (session.source != expected) throw PostgresException(PostgresFailure.SOURCE_CHANGED)
      val connection = session.connection
      fun <T> query(sql: String, bind: (PreparedStatement) -> Unit = {}, read: (ResultSet) -> T): T =
        connection.prepareStatement(sql).use { statement ->
          session.track(statement)
          statement.queryTimeout = JdbcPostgresIo.QUERY_TIMEOUT_SECONDS
          bind(statement)
          statement.executeQuery().use(read)
        }

      val allowedKeys = ANDROID_GLOBAL_CONFIG_ALLOWLIST.toList()
      val placeholders = allowedKeys.joinToString(",") { "?" }
      val values = query(
        "SELECT config_key, config_value FROM global_config WHERE config_key IN ($placeholders) ORDER BY config_key",
        bind = { statement -> allowedKeys.forEachIndexed { index, key -> statement.setString(index + 1, key) } },
      ) { rows ->
        linkedMapOf<String, String>().apply {
          while (rows.next()) {
            val key = rows.requiredString("config_key")
            if (key !in ANDROID_GLOBAL_CONFIG_ALLOWLIST || put(key, rows.requiredString("config_value")) != null) {
              throw PostgresException(PostgresFailure.SCHEMA_INCOMPATIBLE)
            }
          }
        }.toMap()
      }
      val servers = query(
        "SELECT id, create_time, base_url, username, password, server_name, auth_token, user_id, client, device, " +
          "device_id, client_version, user_agent, order_by, disabled, icon_url, line_id, reverse_proxy_id " +
          "FROM emby_server ORDER BY order_by, create_time, id",
      ) { rows ->
        buildList {
          val ids = mutableSetOf<String>()
          while (rows.next()) {
            val id = rows.requiredString("id")
            if (id.isBlank() || !ids.add(id)) throw PostgresException(PostgresFailure.SCHEMA_INCOMPATIBLE)
            add(SharedServerRaw(
              id = id,
              createdAtMillis = rows.getTimestamp("create_time")?.time
                ?: throw PostgresException(PostgresFailure.SCHEMA_INCOMPATIBLE),
              baseUrl = rows.requiredString("base_url"),
              username = rows.string("username"),
              password = rows.string("password"),
              serverName = rows.string("server_name"),
              authToken = rows.string("auth_token"),
              userId = rows.string("user_id"),
              client = rows.requiredString("client"),
              device = rows.requiredString("device"),
              deviceId = rows.requiredString("device_id"),
              clientVersion = rows.requiredString("client_version"),
              userAgent = rows.requiredString("user_agent"),
              orderBy = rows.getInt("order_by"),
              disabled = rows.getInt("disabled") != 0,
              iconUrl = rows.string("icon_url"),
              lineId = rows.string("line_id"),
              reverseProxyId = rows.requiredString("reverse_proxy_id"),
            ))
          }
        }
      }
      val lines = query("SELECT id, name, emby_server_id FROM emby_line ORDER BY create_time, id") { rows ->
        buildList {
          val ids = mutableSetOf<String>()
          while (rows.next()) {
            val row = SharedLineRaw(rows.requiredString("id"), rows.requiredString("name"), rows.requiredString("emby_server_id"))
            if (row.id.isBlank() || !ids.add(row.id)) throw PostgresException(PostgresFailure.SCHEMA_INCOMPATIBLE)
            add(row)
          }
        }
      }
      val selectedReverseIds = (values.entries.filter { it.key in REVERSE_ROUTE_KEYS }.map { it.value } +
        servers.map { it.reverseProxyId }).filter { it.isNotEmpty() && it != "no" }.distinct()
      if (selectedReverseIds.size > MAX_REVERSE_ROWS) throw PostgresException(PostgresFailure.SCHEMA_INCOMPATIBLE)
      val reverseProxies = if (selectedReverseIds.isEmpty()) emptyMap() else query(
        "SELECT id, name, url FROM reverse_proxy_server WHERE id IN (${selectedReverseIds.joinToString(",") { "?" }}) " +
          "ORDER BY create_time, id LIMIT ${MAX_REVERSE_ROWS + 1}",
        bind = { statement -> selectedReverseIds.forEachIndexed { index, id -> statement.setString(index + 1, id) } },
      ) { rows ->
        linkedMapOf<String, SharedReverseProxyRaw>().apply {
          while (rows.next()) {
            val row = SharedReverseProxyRaw(rows.requiredString("id"), rows.string("name"), rows.string("url"))
            if (row.id !in selectedReverseIds || size >= MAX_REVERSE_ROWS || put(row.id, row) != null) {
              throw PostgresException(PostgresFailure.SCHEMA_INCOMPATIBLE)
            }
          }
        }.toMap()
      }
      SharedConfigurationRawSnapshot(values, servers, lines, reverseProxies, session.source)
    } } catch (error: Exception) {
      cache = null
      throw error
    }
    if (!executor.isCurrent(expected) || result.source != expected) {
      cache = null
      throw PostgresException(PostgresFailure.SOURCE_CHANGED)
    }
    result.also { cache = Cached(it, nowNanos()) }
  }

  private fun verifyServerBinding(server: EmbyServer, row: SharedServerRaw, raw: SharedConfigurationRawSnapshot) {
    val matches = server.remoteId == row.id && server.baseUrl == row.baseUrl.trimEnd('/') &&
      server.username == row.username && server.password == row.password &&
      server.client == row.client && server.device == row.device && server.deviceId == row.deviceId &&
      server.clientVersion == row.clientVersion && server.userAgent == row.userAgent &&
      server.reverseProxyId == row.reverseProxyId
    val reverse = raw.reverseProxies[row.reverseProxyId]?.url.orEmpty()
    val reverseMatches = row.reverseProxyId.isEmpty() || row.reverseProxyId == "no" ||
      server.reverseProxyUrl == reverse
    if (!matches || !reverseMatches) throw PostgresException(PostgresFailure.SOURCE_CHANGED)
  }

  private fun ResultSet.string(column: String): String = getString(column).orEmpty()
  private fun ResultSet.requiredString(column: String): String =
    getString(column) ?: throw PostgresException(PostgresFailure.SCHEMA_INCOMPATIBLE)

  private companion object {
    const val CACHE_TTL_NANOS = 60_000_000_000L
    const val MAX_REVERSE_ROWS = 256
    val REVERSE_ROUTE_KEYS = setOf(
      "app_reverse_proxy_id", "trakt_reverse_proxy_id", "simkl_reverse_proxy_id", "yamtrack_reverse_proxy_id",
    )
  }
}

private fun route(raw: SharedConfigurationRawSnapshot, key: String): ReverseProxyRoute {
  val id = raw.values[key]
  return ReverseProxyRoute.selected(id, raw.reverseProxies[id]?.url)
}

private fun parseAppNetworkConfiguration(raw: SharedConfigurationRawSnapshot): AppNetworkConfiguration =
  AppNetworkConfiguration(raw.source, route(raw, "app_reverse_proxy_id"), raw.values["danger_accept_invalid_certs"] == "true")

private fun parsePlaybackConfiguration(
  raw: SharedConfigurationRawSnapshot,
  serverUuid: String,
): PlaybackConfiguration {
  val values = raw.values
  val source = raw.source
  fun long(key: String, invalidFallback: Long): Long =
    values[key].orEmpty().ifEmpty { "0" }.toLongOrNull() ?: invalidFallback
  fun cacheWindow(prefix: String, fallback: Long) = PlaybackCacheWindow(
    seconds = long("${prefix}_seconds", fallback),
    minimumMebibytes = long("${prefix}_min_bytes", fallback),
    maximumMebibytes = long("${prefix}_max_bytes", fallback),
    fallbackMebibytes = fallback,
  )
  val traktUsername = values["trakt_username"].orEmpty()
  val simklUsername = values["simkl_username"].orEmpty()
  val yamTrackWebhook = values["yamtrack_sync_url"].orEmpty()
  return PlaybackConfiguration(
    automaticVersionPolicy = values["play_version_auto_select_policy"] ?: "high-resolution",
    prefetchPlaylist = values["prefetch_playlist"] == "yes",
    isPlayback = values["play_param_IsPlayback"]?.let { it == "true" } ?: true,
    cache = PlaybackCacheConfiguration(cacheWindow("mpv_cache", 300L), cacheWindow("mpv_cache_back", 200L)),
    dangerAcceptInvalidCertificates = values["danger_accept_invalid_certs"] == "true",
    subtitleCache = SubtitleCacheConfiguration(
      storedDays = values["subtitleStoredDays"]?.toLongOrNull() ?: 1L,
      sharedLastCleanEpochSeconds = values["lastCleanSubtitleTime"]?.toLongOrNull(),
    ),
    trakt = TraktRuntimeConfiguration(
      enabled = values["trakt_sync_switch"] != "off" && traktUsername.isNotBlank(),
      username = traktUsername,
      accessToken = values["trakt_access_token"].orEmpty(),
      refreshToken = values["trakt_refresh_token"].orEmpty(),
      expiresAtSeconds = values["trakt_expires_in"]?.toLongOrNull() ?: 0L,
      redirectUri = values["trakt_redirect_uri"].orEmpty(),
      reverseRoute = route(raw, "trakt_reverse_proxy_id"),
    ),
    simkl = SimklRuntimeConfiguration(
      enabled = values["simkl_sync_switch"] != "off" && simklUsername.isNotBlank(),
      username = simklUsername,
      accessToken = values["simkl_access_token"].orEmpty(),
      reverseRoute = route(raw, "simkl_reverse_proxy_id"),
    ),
    yamTrack = YamTrackRuntimeConfiguration(
      enabled = values["yamtrack_sync_switch"] != "off" && yamTrackWebhook.isNotBlank(),
      webhookUrl = yamTrackWebhook,
      reverseRoute = route(raw, "yamtrack_reverse_proxy_id"),
    ),
    source = source,
    serverUuid = serverUuid,
    serverBindingFingerprint = raw.servers.singleOrNull { it.id == serverUuid }?.let { row ->
      runtimeFingerprint(
        row.baseUrl, row.username, row.password, row.client, row.device, row.deviceId, row.clientVersion,
        row.userAgent, row.reverseProxyId, raw.reverseProxies[row.reverseProxyId]?.url.orEmpty(),
      )
    }.orEmpty(),
  )
}

/** Pure typed-parser seam used by JVM tests; no production consumer receives a raw values map. */
internal fun playbackConfigurationForTest(
  values: Map<String, String> = emptyMap(),
  source: PostgresSourceToken = PostgresSourceToken("source", 1),
  serverUuid: String = "server",
  reverseProxyUrls: Map<String, String> = emptyMap(),
): PlaybackConfiguration = parsePlaybackConfiguration(
  raw = SharedConfigurationRawSnapshot(
    values, emptyList(), emptyList(), reverseProxyUrls.mapValues { (id, url) -> SharedReverseProxyRaw(id, "", url) }, source,
  ),
  serverUuid = serverUuid,
)

internal fun appNetworkConfigurationForTest(
  values: Map<String, String> = emptyMap(),
  source: PostgresSourceToken = PostgresSourceToken("source", 1),
  reverseProxyUrls: Map<String, String> = emptyMap(),
): AppNetworkConfiguration = parseAppNetworkConfiguration(SharedConfigurationRawSnapshot(
  values, emptyList(), emptyList(), reverseProxyUrls.mapValues { (id, url) -> SharedReverseProxyRaw(id, "", url) }, source,
))
