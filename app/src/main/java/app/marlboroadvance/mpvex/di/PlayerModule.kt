package app.marlboroadvance.mpvex.di

import android.util.Log
import app.marlboroadvance.mpvex.ui.player.LocalPlaybackStateWriter
import org.koin.dsl.module

val PlayerModule = module {
  single {
    LocalPlaybackStateWriter(
      repository = get(),
      onFailure = { failure -> Log.w("PlaybackState", "Local playback save failed: ${failure.javaClass.simpleName}") },
    )
  }
}
