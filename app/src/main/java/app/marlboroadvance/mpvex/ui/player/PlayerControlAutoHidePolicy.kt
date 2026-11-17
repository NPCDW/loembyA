package app.marlboroadvance.mpvex.ui.player

/** Millisecond storage stays compatible with existing settings; only these seven choices are supported. */
object PlayerControlAutoHidePolicy {
  const val DEFAULT_MILLIS = 5000
  const val NEVER = -1
  val VALUES = listOf(1000, 3000, 5000, 8000, 10000, 20000, NEVER)

  fun normalize(value: Int): Int = value.takeIf { it in VALUES } ?: DEFAULT_MILLIS

  /** A never choice must not launch a timer, rather than passing a negative delay to coroutines. */
  fun delayMillis(value: Int): Long? = normalize(value).takeUnless { it == NEVER }?.toLong()
}
