package jp.up_frontier.mozc.conveyorbelt.belt

/** Decides when a touch on the belt is a drag or a flick rather than a (possibly sloppy) key tap. */
internal object Flick {
  const val MAX_ROWS_PER_SEC = 15f
  private const val SLOP_FACTOR = 3f
  private const val MIN_FLICK_MS = 60L
  private const val MIN_FLICK_ROWS = 0.6f

  /** How far a finger must slide before the touch stops being a tap. Hard taps slide a little. */
  fun scrollStartDistance(touchSlopPx: Float): Float = touchSlopPx * SLOP_FACTOR

  /**
   * Rows/s the belt keeps after the finger lifts, or 0. A short, quick slide (what a hard tap
   * looks like) must not turn into a big fling: it needs real travel and time, and is capped.
   */
  fun releaseVelocity(travelPx: Float, touchMs: Long, velocityPxPerSec: Float, rowPx: Float): Float {
    if (touchMs < MIN_FLICK_MS || travelPx < rowPx * MIN_FLICK_ROWS) return 0f
    return (velocityPxPerSec / rowPx).coerceIn(-MAX_ROWS_PER_SEC, MAX_ROWS_PER_SEC)
  }
}
