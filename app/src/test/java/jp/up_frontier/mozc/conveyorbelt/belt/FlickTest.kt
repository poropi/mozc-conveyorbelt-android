package jp.up_frontier.mozc.conveyorbelt.belt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlickTest {
  private val row = 150f

  @Test fun aHardTapThatSlidesAFewPixelsGetsNoFling() {
    // 30 px in 40 ms is ~750 px/s (5 rows/s) if taken at face value.
    assertEquals(0f, Flick.releaseVelocity(travelPx = 30f, touchMs = 40, velocityPxPerSec = 750f, rowPx = row), 0f)
  }

  @Test fun aShortFastSlideBeyondTheTapDistanceStillGetsNoFling() {
    assertEquals(0f, Flick.releaseVelocity(travelPx = 80f, touchMs = 45, velocityPxPerSec = 6000f, rowPx = row), 0f)
  }

  @Test fun aSlowDragThatShortLikeTravelGetsNoFling() {
    assertEquals(0f, Flick.releaseVelocity(travelPx = 60f, touchMs = 400, velocityPxPerSec = 100f, rowPx = row), 0f)
  }

  @Test fun aRealFlickKeepsItsSpeed() {
    assertEquals(6f, Flick.releaseVelocity(travelPx = 300f, touchMs = 120, velocityPxPerSec = 900f, rowPx = row), 0.001f)
    assertEquals(-6f, Flick.releaseVelocity(travelPx = 300f, touchMs = 120, velocityPxPerSec = -900f, rowPx = row), 0.001f)
  }

  @Test fun aWildFlickIsCapped() {
    assertEquals(Flick.MAX_ROWS_PER_SEC, Flick.releaseVelocity(travelPx = 600f, touchMs = 150, velocityPxPerSec = 8000f, rowPx = row), 0f)
    assertEquals(-Flick.MAX_ROWS_PER_SEC, Flick.releaseVelocity(travelPx = 600f, touchMs = 150, velocityPxPerSec = -8000f, rowPx = row), 0f)
  }

  @Test fun theTapDistanceIsWiderThanTheSystemSlop() {
    assertTrue(Flick.scrollStartDistance(21f) > 21f * 2)
  }
}
