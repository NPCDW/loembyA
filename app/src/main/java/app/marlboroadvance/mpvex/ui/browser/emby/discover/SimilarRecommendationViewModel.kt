package app.marlboroadvance.mpvex.ui.browser.emby.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.marlboroadvance.mpvex.domain.emby.EmbyApiException
import app.marlboroadvance.mpvex.domain.emby.EmbyItem
import app.marlboroadvance.mpvex.domain.emby.EmbyRepository
import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/** Server recommendations and home/discover sampling, always excluding played items. */
class SimilarRecommendationViewModel(
  application: Application,
) : AndroidViewModel(application) {
  private val repository by inject<EmbyRepository>(EmbyRepository::class.java)

  private val _similar = MutableStateFlow<List<EmbyItem>>(emptyList())
  val similar: StateFlow<List<EmbyItem>> = _similar.asStateFlow()

  private val _mix = MutableStateFlow<List<EmbyItem>>(emptyList())
  val mix: StateFlow<List<EmbyItem>> = _mix.asStateFlow()

  private val _nextUp = MutableStateFlow<List<EmbyItem>>(emptyList())
  val nextUp: StateFlow<List<EmbyItem>> = _nextUp.asStateFlow()

  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  fun clearError() {
    _error.value = null
  }

  fun loadSimilar(server: EmbyServer, seed: EmbyItem) {
    val seedId = seed.Id?.takeIf { it.isNotBlank() }
    if (seedId == null) {
      _error.value = "这个条目没有可用的 ID"
      return
    }
    _loading.value = true
    viewModelScope.launch {
      try {
        val page = repository.getSimilarItems(
          server = server,
          itemId = seedId,
          limit = SIMILAR_LIMIT,
          includeItemTypes = listOf("Movie", "Series"),
          excludeIdList = listOf(seedId),
        )
        // Compatible servers may ignore played filters. Preserve server ordering without inventing local scores.
        val items = page.Items.filter { it.UserData?.Played != true }
        _similar.value = items
        if (items.isEmpty()) {
          _error.value = "服务端没返回相似内容（可能没刮削，或该服务器不支持推荐接口）"
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        _error.value = if (error is EmbyApiException && error.code in listOf(404, 405)) {
          "这个服务器不支持推荐接口"
        } else {
          error.message ?: "加载相似推荐失败"
        }
      } finally {
        _loading.value = false
      }
    }
  }

  /** Seeded mixes use InstantMix; seedless home/discover keeps the existing server random sample. */
  fun loadMix(server: EmbyServer, seed: EmbyItem?, limit: Int = MIX_LIMIT) {
    val take = limit.coerceIn(1, MIX_LIMIT)
    _loading.value = true
    viewModelScope.launch {
      try {
        val items = if (seed?.Id != null) {
          repository.getInstantMixItems(
            server = server,
            itemId = seed.Id,
            limit = take,
            includeItemTypes = listOf("Movie", "Series"),
            mediaTypes = listOf("Video"),
            filters = listOf("IsUnplayed"),
          ).Items
        } else {
          repository.getRandomItems(
            server = server,
            includeItemTypes = listOf("Movie", "Series"),
            limit = take,
          ).Items
        }.filter { it.UserData?.Played != true }
        _mix.value = items
        if (items.isEmpty()) {
          _error.value = if (seed == null) "库里没有可播放的内容" else "没有更多可续播的内容了"
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        // Unsupported endpoints report their failure; they do not silently change recommendation policy.
        _error.value = if (error is EmbyApiException && error.code in listOf(404, 405)) {
          "这个服务器不支持自动连播接口"
        } else {
          error.message ?: "加载连播列表失败"
        }
      } finally {
        _loading.value = false
      }
    }
  }

  /** NextUp being unavailable is normal; hide the empty section. */
  fun loadNextUp(server: EmbyServer) {
    viewModelScope.launch {
      try {
        _nextUp.value = repository.getNextUpItems(server, NEXT_UP_LIMIT).Items
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        _nextUp.value = emptyList()
      }
    }
  }

  fun loadAll(server: EmbyServer, seed: EmbyItem?) {
    _error.value = null
    loadNextUp(server)
    seed?.let { loadSimilar(server, it) }
    loadMix(server, seed)
  }

  /** Refresh server recommendations without changing algorithms. */
  fun shuffle(server: EmbyServer, seed: EmbyItem) {
    loadSimilar(server, seed)
  }

  companion object {
    const val SIMILAR_LIMIT = 20
    const val MIX_LIMIT = 20
    const val HOME_MIX_COUNT = 12
    const val NEXT_UP_LIMIT = 12

    fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
      initializer { SimilarRecommendationViewModel(application) }
    }
  }
}
