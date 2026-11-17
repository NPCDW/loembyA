package app.marlboroadvance.mpvex.ui.browser.emby.discover

import androidx.compose.runtime.CompositionLocalProvider

import app.marlboroadvance.mpvex.ui.browser.emby.components.LocalEmbyImageServer

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.emby.EmbyItem
import app.marlboroadvance.mpvex.domain.emby.EmbyServer
import app.marlboroadvance.mpvex.domain.emby.EmbyTicks
import app.marlboroadvance.mpvex.domain.emby.displayTitle
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.emby.EmbyEmptyState
import app.marlboroadvance.mpvex.ui.browser.emby.EmbyViewModel
import app.marlboroadvance.mpvex.ui.browser.emby.EmbyDetailScreen
import app.marlboroadvance.mpvex.ui.browser.emby.openEmbyMediaCard
import app.marlboroadvance.mpvex.ui.browser.emby.components.EmbyCardStyle
import app.marlboroadvance.mpvex.ui.browser.emby.components.EmbyMediaCard
import app.marlboroadvance.mpvex.ui.browser.emby.components.EmbySectionHeader
import app.marlboroadvance.mpvex.ui.browser.emby.components.EmbySkeletonSection
import app.marlboroadvance.mpvex.ui.browser.emby.components.EmbyWideCard
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 「猜你喜欢」独立页（清单 #14）。
 *
 * 三个区块，自上而下：
 * 1. **续播**（`/Shows/NextUp`）—— 服务端没开启时这一段**不显示**（不是错误）
 * 2. **相似推荐** —— 以用户选的「种子」为起点，排序由服务端提供
 * 3. **即混** —— 攒一批可连播的内容，一键开始
 *
 * ## 为什么种子要用户自己选
 *
 * 首页进这一页时没有「正在看的东西」，服务端 `Similar` / `InstantMix` 都要求一个
 * `itemId`。三个选项：
 * - 随机挑一部当种子 —— 用户看到的是陌生片子的推荐，很难判断推荐准不准
 * - 用「继续观看」的第一部当种子 —— 合理，但用户可能没在追剧
 * - **让用户从最近看过的里挑一个** —— 语义最清楚，「我选了《沙丘》，给我类似的」
 *
 * 所以做成：进页面先展示候选（继续观看 + 最新加入），用户点一个才出推荐。
 * 顶栏也能切换种子，随时换。
 */
@Serializable
object EmbyDiscoverScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val application = androidx.compose.ui.platform.LocalContext.current
      .applicationContext as Application
    val backStack = LocalBackStack.current
    // `embyVm.play` 是 suspend（准备播放会话），所以每个播放入口都得有 scope
    val scope = rememberCoroutineScope()

    val vm: SimilarRecommendationViewModel = viewModel(
      factory = SimilarRecommendationViewModel.factory(application),
    )
    val embyVm: EmbyViewModel = viewModel(factory = EmbyViewModel.factory(application))

    val server by embyVm.currentServer.collectAsState()
    CompositionLocalProvider(LocalEmbyImageServer provides server) {
    val resumeItems by embyVm.resumeItems.collectAsState()
    val latestItems by embyVm.latestItems.collectAsState()

    val similar by vm.similar.collectAsState()
    val mix by vm.mix.collectAsState()
    val nextUp by vm.nextUp.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    val gridColumns = 3

    var seed by remember { mutableStateOf<EmbyItem?>(null) }
    var seedPickerShown by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Seedless discovery retains NextUp and the existing random sample; Similar needs a seed.
    LaunchedEffect(server?.id, seed?.Id) {
      val current = server ?: return@LaunchedEffect
      vm.loadAll(current, seed)
    }

    LaunchedEffect(error) {
      val message = error ?: return@LaunchedEffect
      snackbarHostState.showSnackbar(message)
      vm.clearError()
    }

    if (seedPickerShown) {
      SeedPickerDialog(
        candidates = (resumeItems + latestItems).distinctBy { it.Id },
        onPick = {
          seed = it
          seedPickerShown = false
        },
        onDismiss = { seedPickerShown = false },
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("猜你喜欢") },
          navigationIcon = {
            IconButton(onClick = { backStack.removeLastOrNull() }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
          },
          actions = {
            IconButton(onClick = { seedPickerShown = true }) {
              Icon(Icons.Default.AutoAwesome, contentDescription = "换个种子")
            }
            IconButton(
              onClick = {
                val current = server ?: return@IconButton
                val currentSeed = seed ?: return@IconButton
                vm.shuffle(current, currentSeed)
              },
              enabled = seed != null && !loading,
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "换一批")
            }
          },
        )
      },
      snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        // ── 种子选择条 ──
        item {
          SeedRow(
            seed = seed,
            onClick = { seedPickerShown = true },
          )
        }

        // 首屏加载：一块内容都还没有 ⇒ 摆骨架预告版式，别在种子条底下孤零零转个圈。
        // 已经有内容了还在转 ⇒ 那是换种子后的刷新，用小转圈当页脚即可
        //（同 PagingFooter 的约定：整屏 loading 和页脚 loading 不同时出现）。
        if (loading && nextUp.isEmpty() && similar.isEmpty() && mix.isEmpty()) {
          item { EmbySkeletonSection() }
          item { EmbySkeletonSection() }
        } else if (loading) {
          item {
            Box(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              app.marlboroadvance.mpvex.ui.theme.AppLoadingIndicator()
            }
          }
        }

        // ── 续播 ──
        // 空列表直接不渲染：服务端没开这个功能是**常态**，不是错误。
        if (nextUp.isNotEmpty()) {
          item {
            EmbySectionHeader(title = "接着看")
          }
          item {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(nextUp, key = { it.Id ?: it.hashCode().toString() }) { item ->
                EmbyWideCard(
                  title = embyVm.displayTitle(item),
                  contentDescription = item.Name,
                  subtitle = item.SeriesName ?: item.ProductionYear?.toString(),
                  imageUrl = server?.let { embyVm.imageUrl(it, item, "Backdrop", 640) },
                  fallbackImageUrl = server?.let { embyVm.imageUrl(it, item, "Primary", 640) },
                  // 续播列表的进度来自服务端 PlaybackPositionTicks
                  progress = nextUpProgress(item),
                  isFavorite = item.UserData?.IsFavorite == true,
                  onClick = {
                    val current = server ?: return@EmbyWideCard
                    openEmbyMediaCard(item,
                      onOpenDetail = { episode -> episode.Id?.let { backStack.add(EmbyDetailScreen(it, episode.displayTitle())) } },
                      onPlay = { media -> scope.launch { embyVm.play(current, media, EmbyTicks.ticksToSeconds(media.UserData?.PlaybackPositionTicks ?: 0L)) } },
                    )
                  },
                )
              }
            }
          }
        }

        // ── 相似推荐 ──
        if (similar.isNotEmpty()) {
          item {
            EmbySectionHeader(
              title = "看了「${seed?.Name ?: "这部"}」可能也喜欢",
              action = {
                val current = server
                val currentSeed = seed
                if (current != null && currentSeed != null) {
                  TextButton(onClick = { vm.shuffle(current, currentSeed) }) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("换一批")
                  }
                }
              },
            )
          }
          item {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(similar, key = { it.Id ?: it.hashCode().toString() }) { item ->
                EmbyMediaCard(
                  title = item.displayTitle(),
                  contentDescription = item.Name,
                  subtitle = item.ProductionYear?.toString() ?: item.Type,
                  imageUrl = server?.let { embyVm.imageUrl(it, item) },
                  progress = item.UserData?.PlayedPercentage?.takeIf { !item.UserData.Played }
                    ?.let { it / 100.0 }?.toFloat(),
                  isFavorite = item.UserData?.IsFavorite == true,
                  onClick = {
                    val current = server ?: return@EmbyMediaCard
                    openEmbyMediaCard(item,
                      onOpenDetail = { episode -> episode.Id?.let { backStack.add(EmbyDetailScreen(it, episode.displayTitle())) } },
                      onPlay = { media -> scope.launch { embyVm.play(current, media) } },
                    )
                  },
                )
              }
            }
          }
        }

        // ── 即混 ──
        item {
          EmbySectionHeader(
            title = "连播列表",
            action = {
              if (mix.isNotEmpty()) {
                TextButton(
                  onClick = {
                    val current = server ?: return@TextButton
                    // Preserve the sampled order and start every queue entry from the beginning.
                    embyVm.launchFromStartPlayback(current, mix)
                  },
                ) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                  Spacer(Modifier.size(4.dp))
                  Text("开始播放")
                }
              }
            },
          )
        }
        if (mix.isNotEmpty()) {
          item {
            // 即混用**网格**而不是横滑行：这一块是「一攒可连播的内容」，
            // 用户要的是一眼扫过去有多少、横着滑反而看不到全貌。
            // 版式与媒体库网格一致（同样的海报比例 / 三列 / 卡片组件）。
            EmbyChunkedPosterGrid(
              items = mix,
              columns = gridColumns,
              server = server,
              embyVm = embyVm,
              onClick = { item ->
                val current = server ?: return@EmbyChunkedPosterGrid
                openEmbyMediaCard(item,
                  onOpenDetail = { episode -> episode.Id?.let { backStack.add(EmbyDetailScreen(it, episode.displayTitle())) } },
                  onPlay = { media -> scope.launch { embyVm.play(current, media) } },
                )
              },
            )
          }
        } else if (!loading) {
          item {
            EmbyEmptyState(
              message = "还没有可连播的内容",
              buttonText = "换个种子",
              onAction = { seedPickerShown = true },
            )
          }
        }

        // 全空时给一个正经的空态，而不是三个空区块让人以为页面坏了
        if (!loading && similar.isEmpty() && mix.isEmpty() && nextUp.isEmpty()) {
          item {
            Column(
              modifier = Modifier.fillMaxWidth().padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              Text(
                text = "还没有内容。先选一个种子，再看推荐。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
              Button(onClick = { seedPickerShown = true }) { Text("选择种子") }
            }
          }
        }
      }
    }
    }
  }
}

/**
 * 「即混连播」的条目网格。
 *
 * ## 为什么要手写网格而不是直接用 `LazyVerticalGrid`
 *
 * 本函数是作为一个 `item {}` 放在外层 `LazyColumn` 里的。
 * `LazyVerticalGrid` 嵌在 `LazyColumn` 里会拿到**无界的 maxHeight**，
 * 运行时抛「vertically scrollable component was measured with an infinity maximum height」。
 * 而固定高度的 `Grid` 又要求开发者自己算行高（海报比例 + 两行文字），一旦卡片规格改了就得重算。
 *
 * 所以走「按列数切片 → 每片一个 `Row` → 卡片用 `weight(1f)` 平分」这条路：
 * - 高度由内容自然决定，不用手算；
 * - 列数变了不用改任何布局代码；
 * - 复用媒体库同一个 [EmbyMediaCard]，所以卡片长相与媒体库完全一致
 *   （这也是用户要求的「参考媒体库的布局」）；固定采用三列。
 *
 * 末行不足 `columns` 个时用 `Spacer(weight)` 补位，避免最后一行卡片被拉宽。
 */
@Composable
private fun EmbyChunkedPosterGrid(
  items: List<EmbyItem>,
  columns: Int,
  server: EmbyServer?,
  embyVm: EmbyViewModel,
  onClick: (EmbyItem) -> Unit,
) {
  val cols = columns.coerceIn(2, 6)
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items.chunked(cols).forEach { row ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        row.forEach { item ->
          EmbyMediaCard(
            server = server,
            title = embyVm.displayTitle(item),
            contentDescription = item.Name,
            subtitle = item.ProductionYear?.toString() ?: item.Type,
            imageUrl = server?.let { embyVm.imageUrl(it, item, "Primary", 480) },
            fallbackImageUrl = server?.let { embyVm.imageUrl(it, item, "Thumb", 480) },
            progress = nextUpProgress(item),
            isFavorite = item.UserData?.IsFavorite == true,
            style = EmbyCardStyle.POSTER,
            // 网格里必须 fillWidth：否则卡片会用 style.width 的固定宽度，
            // 一行三个时总宽度对不上屏幕，卡片会被挤出去。
            fillWidth = true,
            onClick = { onClick(item) },
            modifier = Modifier.weight(1f),
          )
        }
        // 末行补位：没有它，最后一行几张卡片会把整行宽度吃满，卡片比例就变了
        repeat(cols - row.size) {
          Spacer(Modifier.weight(1f))
        }
      }
    }
  }
}

/**
 * 续播卡片的进度条（0f~1f）。
 *
 * 优先用服务端给的 `PlayedPercentage`（更准，服务端知道总时长）；
 * 没有就退回「已播位置 / 总时长」。
 *
 * ⚠️ 两处不能省：
 * - 除数可能为 0（没刮削时长的条目）⇒ 必须挡掉，否则 NaN 传进进度条会画不出来
 * - 已看完整的（`Played == true`）不画进度条 —— 画满格一条 100% 的横线
 *   只会让人以为「没看完」
 */
private fun nextUpProgress(item: EmbyItem): Float? {
  val data = item.UserData ?: return null
  if (data.Played) return null
  data.PlayedPercentage?.takeIf { it > 0.0 && it < 100.0 }?.let { return (it / 100.0).toFloat() }
  val position = data.PlaybackPositionTicks
  val total = item.MediaSources?.firstOrNull()?.RunTimeTicks
    ?: item.RunTimeTicks
    ?: return null
  if (position <= 0L || total <= 0L) return null
  return ((position.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)).toFloat()
}

/** Current recommendation seed; ranking is always supplied by the server. */
@Composable
private fun SeedRow(
  seed: EmbyItem?,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    TextButton(onClick = onClick) {
      Icon(Icons.Default.AutoAwesome, contentDescription = null, Modifier.size(18.dp))
      Spacer(Modifier.size(6.dp))
      Text(seed?.Name ?: "选择种子")
    }
    Text(
      text = "推荐来源：服务端",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * 种子选择对话框。
 *
 * 候选项用 [EmbyMediaCard] 的紧凑形态而不是大卡片：候选可能有十几个，
 * 网格三列才一屏放得下。
 *
 * ⚠️ 用 [FlowRow] + 手动排版而不是 `LazyVerticalGrid`：
 * 对话框高度受限，`LazyVerticalGrid` 在有限高度里会与外层滚动打架。
 */
@Composable
private fun SeedPickerDialog(
  candidates: List<EmbyItem>,
  onPick: (EmbyItem) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("选一个种子") },
    text = {
      if (candidates.isEmpty()) {
        Text("最近没有看过的内容。先去首页看点什么，再回来选种子。")
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2,
          ) {
            candidates.forEach { item ->
              FilterChip(
                selected = false,
                onClick = { onPick(item) },
                label = {
                  Text(
                    text = item.Name ?: "",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                  )
                },
              )
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}
