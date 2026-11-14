package app.marlboroadvance.mpvex.ui.cine

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import app.marlboroadvance.mpvex.ui.cine.components.LocalCineHaze
import app.marlboroadvance.mpvex.ui.cine.components.LocalCineHazeEnabled
import app.marlboroadvance.mpvex.ui.cine.tokens.CineColors
import app.marlboroadvance.mpvex.ui.cine.tokens.CineSpacing
import app.marlboroadvance.mpvex.ui.cine.tokens.CineTypographySet
import app.marlboroadvance.mpvex.ui.cine.tokens.LocalCineColors
import app.marlboroadvance.mpvex.ui.cine.tokens.LocalCineSpacing
import app.marlboroadvance.mpvex.ui.cine.tokens.LocalCineTypography
import app.marlboroadvance.mpvex.ui.cine.tokens.cineColorScheme
import app.marlboroadvance.mpvex.ui.cine.tokens.cineMaterialTypography
import app.marlboroadvance.mpvex.ui.cine.tokens.rememberCineColors
import dev.chrisbanes.haze.rememberHazeState

/**
 * Cine 子树的主题壳。
 *
 * ## 只做三件事
 * 1. 从 `colorScheme` 派生语义色（[CineColors]）；
 * 2. 下发 Cine 字阶 / 间距；
 * 3. 用 [cineMaterialTypography] 覆盖一层 M3 Typography —— **只作用于 Cine 子树**，
 *    因为 M3 组件（BottomSheet / AlertDialog）内部读的是 `MaterialTheme.typography`，
 *    不覆盖它们会按 M3 默认字阶渲染，和周围 Cine 文字差一号。
 *
 * ## 中性底色与用户强调色
 * 动态取色会给 `surface` 染色并压平容器明度差，因此 surface / onSurface / outline
 * 使用设计稿的中性阶梯；primary / tertiary / error 仍跟随用户主题。
 * 详见 `cineColorScheme`。
 *
 * ## Classic 回落隔离
 * `CineRoute` 只在 Cine 分支提供此主题；`fallback()` 不包 `CineTheme`，
 * Classic 页面不会继承这里的 typography / colorScheme 覆盖。
 */
@Composable
fun CineTheme(content: @Composable () -> Unit) {
  // 顺序很重要：先由「用户主题」算出一套中性影院基底，再让 CineColors 从**这套基底**派生。
  // 反过来（先派生颜色再换 scheme）会让 onSurface / glassBorder 仍然带着壁纸的染色。
  val base = MaterialTheme.colorScheme
  val scheme = remember(base) { cineColorScheme(base) }
  val colors = rememberCineColors(scheme)
  val typography = remember { CineTypographySet() }
  val spacing = remember { CineSpacing() }
  val materialTypography = remember { cineMaterialTypography() }
  // 模糊源：每个 Cine 路由一份。页面把滚动容器标成 cineHazeSource，玻璃层取它做底
  val hazeState = rememberHazeState()

  CompositionLocalProvider(
    LocalCineColors provides colors,
    LocalCineTypography provides typography,
    LocalCineSpacing provides spacing,
    LocalCineHaze provides hazeState,
    LocalCineHazeEnabled provides true,
  ) {
    MaterialTheme(
      colorScheme = scheme,
      typography = materialTypography,
      content = content,
    )
  }
}

/**
 * Cine 主题读值入口，用法与 `MaterialTheme` 对齐：
 * `CineTheme.colors.posterScrim`、`CineTheme.type.heroTitle`、`CineTheme.space.sectionGap`。
 */
object CineTheme {
  val colors: CineColors
    @Composable @ReadOnlyComposable get() = LocalCineColors.current

  val type: CineTypographySet
    @Composable @ReadOnlyComposable get() = LocalCineTypography.current

  val space: CineSpacing
    @Composable @ReadOnlyComposable get() = LocalCineSpacing.current
}

/**
 * 是否宽屏（≥600dp）。
 *
 * 设计决策 4：本期只做 Dock → Rail 的二分，不做横屏双栏。
 * 用 `LocalConfiguration.screenWidthDp` 而不是 Material3 的 WindowSizeClass：
 * 后者要额外依赖，而这里只需要一个阈值。
 */
@Composable
fun rememberCineWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** 当前屏幕高度（dp），用于把 Hero 夹到「不超过 62% 屏高」 */
@Composable
fun rememberCineScreenHeightDp(): Int = LocalConfiguration.current.screenHeightDp
