package app.marlboroadvance.mpvex.ui.browser.emby.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.domain.emby.EmbyHttp
import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.domain.emby.EmbyEndpoints
import app.marlboroadvance.mpvex.domain.emby.EmbyRequestHeaders
import app.marlboroadvance.mpvex.domain.emby.ReverseProxyRoute
import app.marlboroadvance.mpvex.domain.emby.playback.LoopbackHttpPool
import app.marlboroadvance.mpvex.domain.postgres.AppNetworkConfiguration
import app.marlboroadvance.mpvex.domain.postgres.PlaybackConfigurationRepository
import app.marlboroadvance.mpvex.domain.postgres.PostgresSourceToken
import coil3.ImageLoader
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.koin.compose.koinInject
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** The explicit row owns authentication; public icons and local data never inherit a selected row. */
@Composable
internal fun EmbyServerIcon(
  sourceKey: String,
  iconUrl: String,
  modifier: Modifier = Modifier,
  server: EmbyServer? = null,
) {
  val context = LocalContext.current
  val fallback = painterResource(R.drawable.ic_emby_server_default)
  val ownership = EmbyServerIconLoader.ownership(iconUrl, server)
  val configurations = if (ownership == EmbyServerIconLoader.Ownership.LOCAL) null else {
    koinInject<PlaybackConfigurationRepository>()
  }
  val source = configurations?.sourceChanges?.collectAsState()?.value
  val request by produceState<ImageRequest?>(null, context, sourceKey, iconUrl, server, source) {
    value = null
    if (ownership == EmbyServerIconLoader.Ownership.LOCAL) {
      value = EmbyServerIconLoader.request(context, sourceKey, iconUrl)
      return@produceState
    }
    val configs = configurations ?: return@produceState
    val token = source ?: return@produceState
    if (sourceKey != token.sourceKey || (ownership == EmbyServerIconLoader.Ownership.EMBY &&
        server?.sourceRevision != token.revision)) return@produceState
    while (true) {
      value = try {
        if (ownership == EmbyServerIconLoader.Ownership.EMBY) {
          val row = server ?: return@produceState
          val owned = configs.snapshot(row)
          EmbyServerIconLoader.request(context, sourceKey, iconUrl, row) { configs.validateCurrent(owned) }
        } else {
          val app = configs.appNetworkConfiguration(PostgresSourceToken(token.sourceKey, token.revision))
          EmbyServerIconLoader.request(context, sourceKey, iconUrl, app = app) { configs.validateCurrent(app) }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }
      delay(60_000L)
    }
  }
  val painter = if (request == null) fallback else rememberAsyncImagePainter(
    model = request,
    imageLoader = EmbyServerIconLoader.get(context),
    placeholder = fallback,
    error = fallback,
    fallback = fallback,
  )
  Image(painter = painter, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

/** Typed Coil data preserves this icon's explicit owning row, never a global selected server. */
internal class EmbyServerIconData(
  val value: String,
  val cacheKey: String,
  val server: EmbyServer? = null,
  val app: AppNetworkConfiguration? = null,
  val validate: suspend () -> Boolean = { true },
) {
  override fun toString(): String = "EmbyServerIconData([redacted])"
}

internal object EmbyServerIconLoader {
  internal enum class Ownership { LOCAL, EMBY, PUBLIC }
  internal fun ownership(value: String, server: EmbyServer?): Ownership = when {
    value.startsWith("data:", ignoreCase = true) -> Ownership.LOCAL
    server != null && EmbyEndpoints.belongsToServer(server, value) -> Ownership.EMBY
    else -> Ownership.PUBLIC
  }

  internal const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
  private const val MEMORY_BYTES = 4L * 1024 * 1024
  private const val DISK_BYTES = 16L * 1024 * 1024
  private val gate = Semaphore(4)
  private val client by lazy { EmbyHttp.images.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }
  private val publicPool by lazy { LoopbackHttpPool() }
  @Volatile private var instance: ImageLoader? = null

  fun get(context: Context): ImageLoader = instance ?: synchronized(this) {
    instance ?: ImageLoader.Builder(context.applicationContext)
      .components { add(IconFetcher.Factory(), EmbyServerIconData::class) }
      .memoryCache {
        MemoryCache.Builder().maxSizeBytes(MEMORY_BYTES).weakReferencesEnabled(false).build()
      }
      .diskCache {
        DiskCache.Builder().directory(context.cacheDir.resolve("emby_server_icons").toOkioPath())
          .maxSizeBytes(DISK_BYTES).build()
      }
      .logger(null)
      .build().also { instance = it }
  }

  fun request(
    context: Context,
    sourceKey: String,
    value: String,
    server: EmbyServer? = null,
    app: AppNetworkConfiguration? = null,
    validate: suspend () -> Boolean = { true },
  ): ImageRequest? {
    if (!isSupportedValue(value) || !ownerMatches(sourceKey, server, app)) return null
    val local = value.startsWith("data:", ignoreCase = true)
    val owner = server?.takeIf { EmbyEndpoints.belongsToServer(it, value) }
    if (!local && owner == null && app == null) return null
    if (!local && owner == null && app?.reverseRoute?.state == ReverseProxyRoute.State.BLOCKED) return null
    val routed = owner?.let { EmbyEndpoints.resolve(it, value) } ?: if (local) value else app!!.resolveUrl(value)
    val key = cacheKey(sourceKey, routed, owner, if (owner == null) app else null)
    // Public/data images have no row tag, including redirects back onto an Emby origin.
    return ImageRequest.Builder(context)
      .data(EmbyServerIconData(routed, key, owner, if (owner == null) app else null, validate))
      .memoryCacheKey(key).diskCacheKey(key)
      // The custom fetcher validates generation/route before any cache read, including decoded memory.
      .memoryCachePolicy(CachePolicy.DISABLED)
      .diskCachePolicy(CachePolicy.DISABLED)
      .size(192, 192)
      .build()
  }

  internal fun ownerMatches(sourceKey: String, server: EmbyServer?, app: AppNetworkConfiguration?): Boolean =
    (server == null || server.sourceKey == sourceKey) && (app == null || app.source.sourceKey == sourceKey) &&
      (server == null || app == null || server.sourceRevision == app.source.revision)

  internal fun cacheKey(
    sourceKey: String,
    value: String,
    server: EmbyServer? = null,
    app: AppNetworkConfiguration? = null,
  ): String = EmbyImageLoader.sha256(
    "row-icon-v3", sourceKey, server?.let { EmbyImageLoader.cacheKey(it, value) }.orEmpty(),
    app?.fingerprint.orEmpty(), value,
  )

  internal fun memoryCachePolicy(value: String, key: String, now: Long = System.currentTimeMillis()): CachePolicy =
    if (value.startsWith("data:", ignoreCase = true) || NetworkImageCachePolicy.memoryFresh(key, now)) {
      CachePolicy.ENABLED
    } else CachePolicy.WRITE_ONLY

  internal fun isSupportedValue(value: String): Boolean {
    if (value.isBlank()) return false
    if (value.startsWith("data:", ignoreCase = true)) {
      val comma = value.indexOf(',')
      return comma in 1..80 && value.length - comma - 1 <= ((MAX_IMAGE_BYTES + 2) / 3) * 4 &&
        value.substring(0, comma).lowercase() in DATA_HEADERS
    }
    if (value.length > 8192) return false
    return runCatching {
      val uri = URI(value)
      uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.rawFragment == null && uri.port in -1..65535 && uri.port != 0
    }.getOrDefault(false)
  }

  internal fun decodeDataImage(value: String): ByteArray? {
    if (!value.startsWith("data:", ignoreCase = true) || !isSupportedValue(value)) return null
    return runCatching {
      Base64.getDecoder().decode(value.substringAfter(',')).takeIf {
        it.size in 1..MAX_IMAGE_BYTES && isRaster(it)
      }
    }.getOrNull()
  }

  /** Android's existing raster decoder handles these; remote/data SVG safely uses the default vector. */
  internal fun isRaster(bytes: ByteArray): Boolean {
    fun starts(vararg values: Int) = bytes.size >= values.size && values.indices.all { (bytes[it].toInt() and 0xff) == values[it] }
    return starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) || starts(0xff, 0xd8, 0xff) ||
      starts(0x47, 0x49, 0x46, 0x38) || starts(0x42, 0x4d) ||
      (starts(0x52, 0x49, 0x46, 0x46) && bytes.size >= 12 && bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII)))
  }

  internal fun iconRequest(value: String, server: EmbyServer): Request =
    if (EmbyEndpoints.belongsToServer(server, value)) {
      EmbyRequestHeaders.request(server, EmbyEndpoints.resolve(server, value)).get().build()
    } else {
      publicRequest(value)
    }

  internal fun publicRequest(value: String, app: AppNetworkConfiguration? = null): Request {
    require(isSupportedValue(value) && !value.startsWith("data:", ignoreCase = true)) { "Unsupported server icon" }
    // Untagged request: no selected Emby session, 401 recovery or inherited token.
    return Request.Builder().url(app?.resolveUrl(value) ?: value).get().build()
  }

  private val DATA_HEADERS = setOf("png", "jpeg", "jpg", "webp", "gif", "bmp", "x-ms-bmp")
    .mapTo(mutableSetOf()) { "data:image/$it;base64" }

  private class IconFetcher(private val data: EmbyServerIconData, private val disk: DiskCache?) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
      gate.withPermit {
        currentCoroutineContext().ensureActive()
        ensureCurrent()
        val cached = readCache()
        val local = data.value.startsWith("data:", ignoreCase = true)
        val bytes = cached ?: if (local) decodeDataImage(data.value) else download()
        if (bytes == null || !isRaster(bytes)) {
          if (cached != null) {
            disk?.remove(data.cacheKey)
            NetworkImageCachePolicy.forget(data.cacheKey)
          }
          throw IOException("Unable to load server icon") // Never include a URL, data URI, or cause.
        }
        currentCoroutineContext().ensureActive()
        ensureCurrent()
        if (cached == null && !local) writeCache(bytes)
        SourceFetchResult(
          source = ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM, metadata = null),
          mimeType = null,
          dataSource = if (cached != null) DataSource.DISK else if (local) DataSource.MEMORY else DataSource.NETWORK,
        )
      }
    }

    private fun readCache(): ByteArray? {
      val cache = disk ?: return null
      val cached = runCatching {
        cache.openSnapshot(data.cacheKey)?.use { snapshot ->
          val fs = cache.fileSystem
          val metadata = fs.read(snapshot.metadata) { readUtf8() }
          val timestamp = NetworkImageCachePolicy.metadataTimestamp(metadata) ?: return@use null
          if (fs.metadata(snapshot.data).size?.let { it in 1..MAX_IMAGE_BYTES.toLong() } != true) return@use null
          fs.read(snapshot.data) { readByteArray() }.takeIf(::isRaster)?.also {
            NetworkImageCachePolicy.record(data.cacheKey, timestamp)
          }
        }
      }.getOrNull()
      if (cached == null) {
        cache.remove(data.cacheKey)
        NetworkImageCachePolicy.forget(data.cacheKey)
      }
      return cached
    }

    private fun writeCache(bytes: ByteArray) {
      val cache = disk ?: return
      val writtenAt = System.currentTimeMillis()
      val editor = runCatching { cache.openEditor(data.cacheKey) }.getOrNull() ?: return
      try {
        cache.fileSystem.write(editor.data) { write(bytes) }
        cache.fileSystem.write(editor.metadata) { writeUtf8(writtenAt.toString()) }
        editor.commit()
        NetworkImageCachePolicy.record(data.cacheKey, writtenAt)
      } catch (_: Exception) {
        runCatching { editor.abort() }
        cache.remove(data.cacheKey)
        NetworkImageCachePolicy.forget(data.cacheKey)
      }
    }

    private suspend fun ensureCurrent() {
      if (!data.validate()) throw IOException("Server icon source changed")
    }

    private suspend fun download(): ByteArray? = try {
      data.server?.let { row ->
        ensureCurrent()
        client.newCall(iconRequest(data.value, row)).execute().use { response ->
          ensureCurrent()
          if (response.isSuccessful) readImage(response.body) else null
        }
      } ?: downloadPublic()
    } catch (error: Exception) {
      if (error is CancellationException) throw error
      null
    }

    private suspend fun downloadPublic(): ByteArray? {
      val app = data.app ?: return null
      var url = app.resolveUrl(data.value)
      repeat(6) { hop ->
        ensureCurrent()
        publicPool.execute(
          url = url, kind = LoopbackHttpPool.Kind.IMAGE, retry = false,
          dangerAcceptInvalidCerts = app.dangerAcceptInvalidCertificates,
          redirectPolicy = LoopbackHttpPool.RedirectPolicy.NONE,
        ).use { response ->
          ensureCurrent()
          if (response.code !in setOf(301, 302, 303, 307, 308)) {
            return if (response.isSuccessful) readImage(response.body) else null
          }
          val location = response.header("Location") ?: return null
          if (hop == 5) return null
          // Resolve against the original public URL and rewrite Location through the same app route.
          url = app.resolveRedirect(url, location)
        }
      }
      return null
    }

    private fun readImage(body: okhttp3.ResponseBody): ByteArray? {
      if (body.contentLength() > MAX_IMAGE_BYTES) return null
      return body.source().use { source ->
        val buffer = Buffer()
        while (buffer.size <= MAX_IMAGE_BYTES) {
          val count = source.read(buffer, minOf(8192L, MAX_IMAGE_BYTES + 1L - buffer.size))
          if (count == -1L) return@use buffer.readByteArray().takeIf(::isRaster)
        }
        null
      }
    }

    class Factory : Fetcher.Factory<EmbyServerIconData> {
      override fun create(data: EmbyServerIconData, options: Options, imageLoader: ImageLoader): Fetcher =
        IconFetcher(data, imageLoader.diskCache)
    }
  }
}
