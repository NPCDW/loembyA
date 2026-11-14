package app.marlboroadvance.mpvex.ui.player

import android.content.pm.ActivityInfo

/** App windows are portrait; video players lock to an explicit orientation. */
object AppOrientationPolicy {
  const val APP = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
  const val VIDEO = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  const val MPV = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

  /** Canonicalize requests at the Activity boundary, including requests from engines/host clients. */
  fun constrainRequest(videoPlayback: Boolean, @Suppress("UNUSED_PARAMETER") requested: Int): Int =
    if (videoPlayback) VIDEO else APP

  /** MPV accepts only fixed orientations. Unknown and legacy requests fall back to landscape. */
  fun constrainMpvRequest(requested: Int): Int = when (requested) {
    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    else -> MPV
  }

  /** Select a fixed orientation from a valid rotation-corrected aspect. */
  fun orientationForVideo(aspect: Double?): Int? = aspect
    ?.takeIf { it.isFinite() && it > 0.001 }
    ?.let {
      if (it > 1.0) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

  fun rotatedOrientation(current: Int): Int = when (current) {
    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
  }

  /** Fullscreen entry cannot rely on an orientation change (launch may already be landscape). */
  fun shouldEnterVideoFullscreen(
    landscape: Boolean,
    inPictureInPicture: Boolean,
    finishing: Boolean,
    destroyed: Boolean,
    alreadyFullscreen: Boolean,
  ): Boolean = landscape && !inPictureInPicture && !finishing && !destroyed && !alreadyFullscreen
}

/** Presentation ownership for one native file; the URL also distinguishes same-item versions. */
data class PlayerOrientationState(
  val fileIdentity: String? = null,
  val orientation: Int = AppOrientationPolicy.MPV,
  val owner: Owner = Owner.DEFAULT,
  val nativeStarted: Boolean = false,
  val nativeReady: Boolean = false,
) {
  enum class Owner { DEFAULT, METADATA, NATIVE, MANUAL }

  /** Preparation must close the native gate before querying/resolving another file. */
  fun prepareFile(identity: String?): PlayerOrientationState = copy(
    fileIdentity = identity,
    orientation = if (owner == Owner.MANUAL) orientation else AppOrientationPolicy.MPV,
    owner = if (owner == Owner.MANUAL) Owner.MANUAL else Owner.DEFAULT,
    nativeStarted = false,
    nativeReady = false,
  )

  fun startFile(identity: String): PlayerOrientationState =
    (if (identity == fileIdentity && !nativeStarted && owner != Owner.NATIVE) this else prepareFile(identity))
      .copy(nativeStarted = true, nativeReady = false)

  fun metadata(identity: String, width: Int?, height: Int?): PlayerOrientationState {
    if (identity != fileIdentity || owner == Owner.NATIVE || owner == Owner.MANUAL) return this
    if (width == null || height == null || width <= 0 || height <= 0) return this
    val selected = AppOrientationPolicy.orientationForVideo(width.toDouble() / height) ?: return this
    return copy(orientation = selected, owner = Owner.METADATA)
  }

  /** PLAYBACK_RESTART follows the first frame, so old dimensions during prepare cannot win. */
  fun firstFrame(identity: String): PlayerOrientationState =
    if (identity == fileIdentity && nativeStarted) copy(nativeReady = true) else this

  fun nativeAspect(identity: String, aspect: Double?): PlayerOrientationState {
    if (identity != fileIdentity || !nativeReady || owner == Owner.MANUAL) return this
    val selected = AppOrientationPolicy.orientationForVideo(aspect) ?: return this
    return copy(orientation = selected, owner = Owner.NATIVE)
  }

  fun rotate(): PlayerOrientationState = copy(
    orientation = AppOrientationPolicy.rotatedOrientation(orientation),
    owner = Owner.MANUAL,
  )

  fun stop(): PlayerOrientationState = copy(nativeStarted = false, nativeReady = false)
}
