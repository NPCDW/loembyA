package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.PlayerPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.player.PlayerControlAutoHidePolicy
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

@Serializable
object PlayerPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<PlayerPreferences>()
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = stringResource(R.string.pref_player),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
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
        SettingsSpotlightReveal(listState, PlayerPreferencesScreen)
        LazyColumn(
          state = listState,
          modifier =
            Modifier
              .fillMaxSize()
              .padding(padding),
        ) {
          item {
            PreferenceSectionHeader(title = "常规")
          }

          item {
            PreferenceCard {
              val hideDelay by preferences.playerTimeToDisappear.collectAsState()
              val normalizedHideDelay = PlayerControlAutoHidePolicy.normalize(hideDelay)
              val hideDelayLabels = mutableMapOf<Int, String>()
              for (millis in PlayerControlAutoHidePolicy.VALUES) {
                hideDelayLabels[millis] = playerControlHideDelayLabel(millis)
              }
              ListPreference(
                icon = {
                  Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                value = normalizedHideDelay,
                onValueChange = preferences.playerTimeToDisappear::set,
                values = PlayerControlAutoHidePolicy.VALUES,
                valueToText = { AnnotatedString(hideDelayLabels.getValue(it)) },
                modifier = Modifier.settingsAnchor(R.string.pref_player_display_hide_player_control_time),
                title = {
                  Text(
                    text = stringResource(R.string.pref_player_display_hide_player_control_time),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                summary = { Text(hideDelayLabels.getValue(normalizedHideDelay)) },
              )

              PreferenceDivider()

              val autoSkipIntro by preferences.autoSkipIntro.collectAsState()
              SwitchPreference(
                icon = {
                  Icon(
                    Icons.Outlined.SkipNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                value = autoSkipIntro,
                onValueChange = preferences.autoSkipIntro::set,
                modifier = Modifier.settingsAnchor(R.string.pref_auto_skip_intro_title),
                title = {
                  Text(
                    stringResource(R.string.pref_auto_skip_intro_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                summary = {
                  Text(
                    text = stringResource(
                      if (autoSkipIntro) R.string.pref_auto_skip_intro_summary_on
                      else R.string.pref_auto_skip_intro_summary_off,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val autoSkipOutro by preferences.autoSkipOutro.collectAsState()
              SwitchPreference(
                icon = {
                  Icon(
                    Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                value = autoSkipOutro,
                onValueChange = preferences.autoSkipOutro::set,
                modifier = Modifier.settingsAnchor(R.string.pref_auto_skip_outro_title),
                title = {
                  Text(
                    stringResource(R.string.pref_auto_skip_outro_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                summary = {
                  Text(
                    text = stringResource(
                      if (autoSkipOutro) R.string.pref_auto_skip_outro_summary_on
                      else R.string.pref_auto_skip_outro_summary_off,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val autoPiPOnNavigation by preferences.autoPiPOnNavigation.collectAsState()
              SwitchPreference(
                icon = {
                  Icon(
                    Icons.Outlined.PictureInPictureAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                value = autoPiPOnNavigation,
                onValueChange = preferences.autoPiPOnNavigation::set,
                modifier = Modifier.settingsAnchor("自动画中画"),
                title = { Text("自动画中画", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                summary = {
                  Text(
                    text = stringResource(R.string.i18n_auto_pip_hint),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              val keepScreenOnWhenPaused by preferences.keepScreenOnWhenPaused.collectAsState()
              SwitchPreference(
                icon = {
                  Icon(
                    Icons.Outlined.LightMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                value = keepScreenOnWhenPaused,
                onValueChange = preferences.keepScreenOnWhenPaused::set,
                modifier = Modifier.settingsAnchor("暂停时保持屏幕常亮"),
                title = { Text("暂停时保持屏幕常亮", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                summary = {
                  Text(
                    text = if (keepScreenOnWhenPaused) {
                      "Screen stays awake while video is paused"
                    } else {
                      "Screen can turn off while video is paused"
                    },
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )
            }
          }

          item {
            PreferenceSectionHeader(title = stringResource(R.string.pref_player_display))
          }

          item {
            PreferenceCard {
              val showSystemStatusBar by preferences.showSystemStatusBar.collectAsState()
              SwitchPreference(
                icon = {
                  Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                value = showSystemStatusBar,
                onValueChange = preferences.showSystemStatusBar::set,
                modifier = Modifier.settingsAnchor(R.string.pref_player_display_show_status_bar),
                title = {
                  Text(
                    stringResource(R.string.pref_player_display_show_status_bar),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
              )

              PreferenceDivider()

              val showSystemNavigationBar by preferences.showSystemNavigationBar.collectAsState()
              SwitchPreference(
                icon = {
                  Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                },
                value = showSystemNavigationBar,
                onValueChange = preferences.showSystemNavigationBar::set,
                modifier = Modifier.settingsAnchor("显示控件时显示导航栏"),
                title = { Text("显示控件时显示导航栏", maxLines = 1, overflow = TextOverflow.Ellipsis) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun playerControlHideDelayLabel(millis: Int): String =
  if (millis == PlayerControlAutoHidePolicy.NEVER) {
    stringResource(R.string.pref_player_control_hide_never)
  } else {
    val seconds = millis / 1_000
    pluralStringResource(R.plurals.seconds, seconds, seconds)
  }
