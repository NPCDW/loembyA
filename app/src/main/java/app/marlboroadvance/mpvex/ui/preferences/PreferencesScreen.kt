package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.DanmakuPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight
import app.marlboroadvance.mpvex.ui.postgres.PostgresPreferencesScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Composable
internal fun PreferencesContent(modifier: Modifier = Modifier, showBack: Boolean = true) {
  PreferencesScreen.RenderContent(modifier, showBack)
}

@Serializable
object PreferencesScreen : Screen {
  @Composable
  override fun Content() { PreferencesContent() }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  internal fun RenderContent(modifier: Modifier, showBack: Boolean) {
    val backstack = LocalBackStack.current
    val bottomInset = if (showBack) 0.dp else LocalNavigationBarHeight.current
    Scaffold(
      modifier = modifier,
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_preferences),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            if (showBack) IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val listState = rememberLazyListState()
        SettingsSpotlightReveal(listState, PreferencesScreen)
        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(bottom = bottomInset),
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          item {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable { backstack.add(SettingsSearchScreen) },
              shape = RoundedCornerShape(28.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              tonalElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }

          item {
            PreferenceCard {
              Preference(
                modifier = Modifier.settingsAnchor(R.string.postgres_settings_title),
                title = { Text(stringResource(R.string.postgres_settings_title)) },
                summary = {
                  Text(
                    stringResource(R.string.postgres_settings_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(PostgresPreferencesScreen) },
              )

              PreferenceDivider()

              Preference(
                modifier = Modifier.settingsAnchor("共享配置（只读）"),
                title = { Text("共享配置（只读）") },
                summary = {
                  Text(
                    "查看 Android 生效配置、服务器线路和反向代理；请在 loemby 桌面端修改",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(SharedConfigurationPreferencesScreen) },
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "外观")
          }

          item {
            PreferenceCard {
              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_appearance_title),
                title = {
                  Text(stringResource(R.string.pref_appearance_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_appearance_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(AppearancePreferencesScreen) },
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "播放与控件")
          }

          item {
            PreferenceCard {
              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_player),
                title = {
                  Text(stringResource(R.string.pref_player), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_player_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(PlayerPreferencesScreen) },
              )

              PreferenceDivider()

              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_gesture),
                title = {
                  Text(stringResource(R.string.pref_gesture), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_gesture_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Gesture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(GesturePreferencesScreen) },
              )

              PreferenceDivider()

              val danmakuPrefs = koinInject<DanmakuPreferences>()
              val danmakuEnabled by danmakuPrefs.enabled.collectAsState()
              Preference(
                modifier = Modifier.settingsAnchor(R.string.danmaku_title),
                title = {
                  Text(stringResource(R.string.danmaku_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.danmaku_settings_summary) +
                      if (danmakuEnabled) "（已开启）" else "（未开启）",
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(DanmakuSettingsScreen) },
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "媒体渲染（解码 / 字幕 / 音频）")
          }

          item {
            PreferenceCard {
              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_decoder),
                title = {
                  Text(stringResource(R.string.pref_decoder), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_decoder_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(DecoderPreferencesScreen) },
              )

              PreferenceDivider()

              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_subtitles),
                title = {
                  Text(stringResource(R.string.pref_subtitles), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_subtitles_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Subtitles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(SubtitlesPreferencesScreen) },
              )

              PreferenceDivider()

              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_audio),
                title = {
                  Text(stringResource(R.string.pref_audio), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_audio_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(AudioPreferencesScreen) },
              )
            }
          }

          item {
            PreferenceSectionHeader(title = "高级与关于")
          }

          item {
            PreferenceCard {
              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_advanced),
                title = {
                  Text(stringResource(R.string.pref_advanced), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_advanced_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(AdvancedPreferencesScreen) },
              )

              PreferenceDivider()

              Preference(
                modifier = Modifier.settingsAnchor(R.string.pref_about_title),
                title = {
                  Text(stringResource(R.string.pref_about_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                summary = {
                  Text(
                    text = stringResource(R.string.pref_about_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                icon = {
                  Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                onClick = { backstack.add(AboutScreen) },
              )
            }
          }
        }
      }
    }
  }
}
