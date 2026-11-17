package app.marlboroadvance.mpvex.ui.player

import app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Everything needed for a local write, captured before native playback or item identity can change. */
data class PlaybackStateSnapshot(
  val state: PlaybackStateEntity,
  val durationSeconds: Int,
  val watchedThreshold: Int,
  val versionProgressKey: String? = null,
) {
  internal fun merge(previous: PlaybackStateEntity?): PlaybackStateEntity {
    val position = state.lastPosition.coerceAtLeast(0)
    val duration = if (durationSeconds > 0) {
      durationSeconds
    } else {
      ((previous?.lastPosition ?: 0).toLong() + (previous?.timeRemaining ?: 0))
        .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
    val completed = duration > 0 && position >= duration - 1
    val resumePosition = if (completed) 0 else position
    val watched = duration > 0 && position.toFloat() / duration >= watchedThreshold / 100f
    return state.copy(
      lastPosition = resumePosition,
      timeRemaining = (duration - resumePosition).coerceAtLeast(0),
      hasBeenWatched = completed || watched || previous?.hasBeenWatched == true,
    )
  }
}

/**
 * App-scoped FIFO writer for the existing Room playback-state repository. It never reads native state
 * or captures an Activity, and writes are neither conflated nor cancelled by a later snapshot/teardown.
 */
class LocalPlaybackStateWriter(
  private val repository: PlaybackStateRepository,
  scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
  private val onFailure: (Throwable) -> Unit = {},
) {
  private sealed interface Operation {
    data class Save(val snapshot: PlaybackStateSnapshot) : Operation
    data class Read(val identifier: String, val result: CompletableDeferred<PlaybackStateEntity?>) : Operation
  }

  private val pending = Channel<Operation>(Channel.UNLIMITED)

  init {
    scope.launch {
      for (operation in pending) {
        try {
          when (operation) {
            is Operation.Save -> {
              val snapshot = operation.snapshot
              val entity = snapshot.merge(repository.getVideoDataByTitle(snapshot.state.mediaTitle))
              repository.upsert(entity)
              snapshot.versionProgressKey?.let { repository.upsert(entity.copy(mediaTitle = it)) }
            }
            is Operation.Read -> operation.result.complete(repository.getVideoDataByTitle(operation.identifier))
          }
        } catch (cancelled: CancellationException) {
          if (operation is Operation.Read) operation.result.cancel(cancelled)
          throw cancelled
        } catch (failure: Exception) {
          if (operation is Operation.Read) operation.result.completeExceptionally(failure)
          onFailure(failure)
        }
      }
    }
  }

  fun enqueue(snapshot: PlaybackStateSnapshot) {
    check(pending.trySend(Operation.Save(snapshot)).isSuccess)
  }

  /** Restoration sees earlier snapshots even when their Room writes are still pending. */
  suspend fun read(identifier: String): PlaybackStateEntity? {
    val result = CompletableDeferred<PlaybackStateEntity?>()
    pending.send(Operation.Read(identifier, result))
    return result.await()
  }
}
