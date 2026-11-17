package app.marlboroadvance.mpvex.ui.player

import app.marlboroadvance.mpvex.database.entities.PlaybackStateEntity
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalPlaybackStateWriterTest {
  private fun snapshot(
    identifier: String = "item-a",
    position: Int = 40,
    duration: Int = 100,
    version: String? = null,
  ) = PlaybackStateSnapshot(
    state = PlaybackStateEntity(
      mediaTitle = identifier,
      lastPosition = position,
      playbackSpeed = 1.25,
      sid = 2,
      secondarySid = -1,
      subDelay = 250,
      subSpeed = 1.1,
      aid = 1,
      audioDelay = 100,
    ),
    durationSeconds = duration,
    watchedThreshold = 90,
    versionProgressKey = version,
  )

  @Test
  fun `switch snapshots keep immutable item and version identities while Room is blocked`() = runTest {
    val repository = Repository()
    val blocked = CompletableDeferred<Unit>()
    repository.beforeWrite = { if (repository.writes.isEmpty()) blocked.await() }
    val writer = LocalPlaybackStateWriter(repository, backgroundScope)
    writer.enqueue(snapshot(version = "version-a"))
    runCurrent()
    writer.enqueue(snapshot(identifier = "item-b", position = 30, version = "version-b"))
    val read = async { writer.read("item-b") }
    runCurrent()
    assertTrue(repository.writes.isEmpty())
    assertFalse(read.isCompleted)

    blocked.complete(Unit)
    runCurrent()
    assertEquals(listOf("item-a", "version-a", "item-b", "version-b"), repository.writes.map { it.mediaTitle })
    assertEquals(listOf(40, 40, 30, 30), repository.writes.map { it.lastPosition })
    assertEquals(30, read.await()?.lastPosition)
  }

  @Test
  fun `same item saves are FIFO and restoration sees the last queued snapshot`() = runTest {
    val repository = Repository()
    val writer = LocalPlaybackStateWriter(repository, backgroundScope)
    writer.enqueue(snapshot(position = 10))
    writer.enqueue(snapshot(position = 20))
    writer.enqueue(snapshot(position = 35))
    assertEquals(35, writer.read("item-a")?.lastPosition)
    assertEquals(listOf(10, 20, 35), repository.writes.map { it.lastPosition })
  }

  @Test
  fun `completion clears resume point but retains duration and sticky watched state`() = runTest {
    val repository = Repository()
    val writer = LocalPlaybackStateWriter(repository, backgroundScope)
    writer.enqueue(snapshot(position = 99))
    val completed = writer.read("item-a")!!
    assertEquals(0, completed.lastPosition)
    assertEquals(100, completed.timeRemaining)
    assertTrue(completed.hasBeenWatched)
    writer.enqueue(snapshot(position = 10))
    assertTrue(writer.read("item-a")!!.hasBeenWatched)
  }

  @Test
  fun `unknown duration falls back only to previous duration and never invents completion`() = runTest {
    val repository = Repository()
    val writer = LocalPlaybackStateWriter(repository, backgroundScope)
    writer.enqueue(snapshot(position = 37, duration = 0))
    val unknown = writer.read("item-a")!!
    assertEquals(37, unknown.lastPosition)
    assertEquals(0, unknown.timeRemaining)
    assertFalse(unknown.hasBeenWatched)

    writer.enqueue(snapshot(position = 40, duration = 100))
    writer.read("item-a")
    writer.enqueue(snapshot(position = 70, duration = 0))
    val fallback = writer.read("item-a")!!
    assertEquals(70, fallback.lastPosition)
    assertEquals(30, fallback.timeRemaining)
  }

  @Test
  fun `a failed write does not drop the next immutable snapshot`() = runTest {
    val repository = Repository()
    repository.beforeWrite = { if (it.mediaTitle == "item-a") error("local write failed") }
    val failures = mutableListOf<Throwable>()
    val writer = LocalPlaybackStateWriter(repository, backgroundScope, { failures.add(it) })
    writer.enqueue(snapshot())
    writer.enqueue(snapshot(identifier = "item-b"))
    assertEquals(40, writer.read("item-b")?.lastPosition)
    assertEquals(1, failures.size)
  }

  private class Repository : PlaybackStateRepository {
    val states = linkedMapOf<String, PlaybackStateEntity>()
    val writes = mutableListOf<PlaybackStateEntity>()
    var beforeWrite: suspend (PlaybackStateEntity) -> Unit = {}
    override suspend fun upsert(playbackState: PlaybackStateEntity) {
      beforeWrite(playbackState)
      writes.add(playbackState)
      states[playbackState.mediaTitle] = playbackState
    }
    override suspend fun getVideoDataByTitle(mediaTitle: String) = states[mediaTitle]
    override suspend fun getVideoDataByTitlePrefix(prefix: String) = states.values.firstOrNull {
      it.mediaTitle.startsWith(prefix)
    }
  }
}
