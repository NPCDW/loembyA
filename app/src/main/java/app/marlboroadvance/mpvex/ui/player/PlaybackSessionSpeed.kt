package app.marlboroadvance.mpvex.ui.player

/** Session speed belongs to one logical item, not a persisted Room row or a series/global memory map. */
class PlaybackSessionSpeed {
  private var itemIdentifier: String? = null
  var generation = 0L
    private set
  var speed = 1f
    private set

  fun beginItem(identifier: String, defaultSpeed: Float): Float {
    if (itemIdentifier != identifier) {
      itemIdentifier = identifier
      generation++
      speed = normalize(defaultSpeed)
    }
    return speed
  }

  fun changeSpeed(value: Float): Float {
    generation++
    speed = normalize(value)
    return speed
  }

  /** Native reloads retain explicit session speed, but retire a hold/ramp still attached to the old file. */
  fun invalidateTemporarySpeed() {
    generation++
  }

  /** A hold or its animated release from the old item can never write the new item's speed. */
  fun acceptsTemporarySpeed(itemGeneration: Long): Boolean = itemGeneration == generation

  private fun normalize(value: Float): Float = value.takeIf { it.isFinite() && it > 0f } ?: 1f
}
