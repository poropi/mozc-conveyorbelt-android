package jp.up_frontier.mozc.conveyorbelt.belt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import jp.up_frontier.mozc.conveyorbelt.keyboard.KEYMAP
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_MS = 180L
private const val TAP_MS = 100L
private const val FLING_FRICTION = 2.5f
private const val SETTLE_BELOW = 0.5f
private const val SETTLE_RATE = 12f
private val KEY_TOP = Color(0xFFFFFFFF)
private val KEY_BOTTOM = Color(0xFFF7F7F7)
private val KEY_SIDE = Color(0xFFCDD1D6)
private val KEY_PRESSED_TOP = Color(0xFFBBDEFB)
private val KEY_PRESSED_BOTTOM = Color(0xFF7FB8EE)
private val KEY_LEGEND = Color(0xFF30343A)
private val KEY_EMPTY_COLOR = Color(0xFF26282C)
private val BELT_COLOR = Color(0xFF16181B)

private enum class Mode { PENDING, HELD, SCROLL, IGNORED }

/** What the fingers are doing to the belt, shared with the motor loop. */
private class BeltGesture {
  /** Fingers currently dragging the belt (taps and holds do not count). */
  var scrolling = 0

  /** Rows/s left over from a fling, added on top of the motor and decaying back to it. */
  var velocity = 0f
}

private class PointerState(val start: Offset, val key: Int?, val downMs: Long) {
  var mode = Mode.PENDING
  var holdJob: Job? = null
  val velocity = VelocityTracker()
}

/**
 * Conveyor-belt keyboard: 4 lanes of keys on one looping belt. Drag/fling vertically to roll
 * the belt; a quick tap types the key, a touch held still keeps it down (for Ctrl/Shift/Fn chords).
 */
@Composable
fun BeltKeyboard(
  pressed: Set<Int>,
  onPress: (Int) -> Unit,
  onRelease: (Int) -> Unit,
  modifier: Modifier = Modifier,
  visibleRows: Float = 11f,
  // Rows per second the motor drives the belt; positive moves keys downward, 0 stops it.
  flowRowsPerSec: Float = 0f,
) {
  // Position of the belt in rows. Row r is drawn r - offset rows above the bottom edge.
  var offset by rememberSaveable { mutableFloatStateOf(0f) }
  val currentPressed by rememberUpdatedState(pressed)
  val onPressState by rememberUpdatedState(onPress)
  val onReleaseState by rememberUpdatedState(onRelease)
  val gesture = remember { BeltGesture() }
  val flow by rememberUpdatedState(flowRowsPerSec)
  val textMeasurer = rememberTextMeasurer()
  val slop = LocalViewConfiguration.current.touchSlop
  // Demo/test tooling: a debuggable build reports the belt's phase and geometry to logcat (tag
  // "beltphase") so an outside script can time taps to the keys. Release builds log nothing.
  val debuggable = (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

  // The motor: the belt never stops turning because of a tap or a held key. Only a finger
  // dragging the belt takes it over; a fling then fades back into the motor's speed.
  LaunchedEffect(Unit) {
    var last = 0L
    var phaseLoggedAt = 0L
    val lap = BeltModel.ROWS.toFloat()
    while (true) {
      withFrameNanos { now ->
        if (last != 0L && gesture.scrolling == 0) {
          val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
          val next = (offset + (flow + gesture.velocity) * dt) % lap
          offset = if (next < 0f) next + lap else next
          gesture.velocity *= exp(-FLING_FRICTION * dt)
          if (abs(gesture.velocity) < 0.05f) gesture.velocity = 0f
          // With the motor off, settle on a whole row so keys line up with the touch grid.
          if (flow == 0f && abs(gesture.velocity) < SETTLE_BELOW) {
            offset += (offset.roundToInt() - offset) * minOf(1f, SETTLE_RATE * dt)
          }
        }
        last = now
        if (debuggable && now - phaseLoggedAt > 100_000_000L) {
          phaseLoggedAt = now
          Log.d("beltphase", "offset=%.4f flow=%.3f nanos=%d".format(offset, flow, SystemClock.elapsedRealtimeNanos()))
        }
      }
    }
  }

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .onGloballyPositioned { c ->
        if (debuggable) {
          val p = c.positionInRoot()
          Log.d("beltphase", "canvas x=%.1f y=%.1f w=%d h=%d rows=%.1f".format(p.x, p.y, c.size.width, c.size.height, visibleRows))
        }
      }
      .clipToBounds()
      .pointerInput(visibleRows) {
        coroutineScope {
          val scope = this
          val states = HashMap<PointerId, PointerState>()

          fun hit(p: Offset): Int? {
            val w = this@pointerInput.size.width.toFloat()
            val h = this@pointerInput.size.height.toFloat()
            if (p.x < 0 || p.x >= w || p.y < 0 || p.y >= h) return null
            val rowH = h / visibleRows
            val row = floor((h - p.y) / rowH + offset).toInt()
            val lane = (p.x / (w / BeltModel.LANES)).toInt().coerceIn(0, BeltModel.LANES - 1)
            return BeltModel.keyIndex(row, lane).takeUnless { KEYMAP[it].isNone }
          }

          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              for (c: PointerInputChange in event.changes) {
                val st = states[c.id]
                when {
                  c.changedToDown() -> {
                    val key = hit(c.position)
                    val s = PointerState(c.position, key, c.uptimeMillis)
                    states[c.id] = s
                    s.velocity.addPosition(c.uptimeMillis, c.position)
                    if (key != null) {
                      s.holdJob = scope.launch {
                        delay(HOLD_MS)
                        if (s.mode == Mode.PENDING) {
                          s.mode = Mode.HELD
                          onPressState(key)
                        }
                      }
                    }
                    c.consume()
                  }
                  c.changedToUp() && st != null -> {
                    states.remove(c.id)
                    st.holdJob?.cancel()
                    when (st.mode) {
                      Mode.IGNORED -> Unit
                      Mode.HELD -> st.key?.let(onReleaseState)
                      Mode.PENDING -> st.key?.let { key ->
                        scope.launch {
                          onPressState(key)
                          delay(TAP_MS)
                          onReleaseState(key)
                        }
                      }
                      Mode.SCROLL -> {
                        gesture.scrolling--
                        st.velocity.addPosition(c.uptimeMillis, c.position)
                        val rowPx = this@pointerInput.size.height / visibleRows
                        val fling = Flick.releaseVelocity(
                          travelPx = (c.position - st.start).getDistance(),
                          touchMs = c.uptimeMillis - st.downMs,
                          velocityPxPerSec = st.velocity.calculateVelocity().y,
                          rowPx = rowPx,
                        )
                        // Hand the finger's speed to the belt; the motor's own speed is already in the total.
                        gesture.velocity = if (fling == 0f) 0f else fling - flow
                      }
                    }
                    c.consume()
                  }
                  c.pressed && st != null -> {
                    st.velocity.addPosition(c.uptimeMillis, c.position)
                    // The motor owns the belt: a finger only drags it while the motor is stopped.
                    if (st.mode == Mode.PENDING && flow == 0f && (c.position - st.start).getDistance() > Flick.scrollStartDistance(slop)) {
                      st.mode = Mode.SCROLL
                      gesture.scrolling++
                      gesture.velocity = 0f
                      st.holdJob?.cancel()
                    }
                    // While the motor runs, a finger that wanders off is swiping, not typing: it does nothing.
                    if (st.mode == Mode.PENDING && (c.position - st.start).getDistance() > Flick.scrollStartDistance(slop)) {
                      st.mode = Mode.IGNORED
                      st.holdJob?.cancel()
                    }
                    if (st.mode == Mode.SCROLL) {
                      val rowH = this@pointerInput.size.height / visibleRows
                      offset += (c.position.y - c.previousPosition.y) / rowH
                      c.consume()
                    }
                  }
                }
              }
            }
          }
        }
      },
  ) {
    drawRect(BELT_COLOR)
    val w = this.size.width
    val h = this.size.height
    val rowH = h / visibleRows
    val laneW = w / BeltModel.LANES
    val gap = rowH * 0.04f
    val corner = CornerRadius(rowH * 0.15f)
    val firstRow = floor(offset).toInt() - 1
    val lastRow = firstRow + visibleRows.toInt() + 3

    for (row in firstRow..lastRow) {
      val top = h - (row - offset + 1f) * rowH
      if (top > h || top + rowH < 0f) continue
      // Keys fold away over the rollers at both ends of the belt: squash and darken them.
      val cy = top + rowH / 2f
      val edge = minOf(cy, h - cy) / rowH
      val t = ((edge - 0.1f) / 0.9f).coerceIn(0f, 1f)
      val s = t * t * (3f - 2f * t)
      val squash = 0.5f + 0.5f * s
      val shade = (1f - s) * 0.6f

      withTransform({ scale(1f, squash, Offset(w / 2f, cy)) }) {
        for (lane in 0 until BeltModel.LANES) {
          val index = BeltModel.keyIndex(row, lane)
          val key = KEYMAP[index]
          val x = lane * laneW + gap
          val keyW = laneW - gap * 2
          if (key.isNone) {
            drawRoundRect(KEY_EMPTY_COLOR, Offset(x, top + gap), Size(keyW, rowH - gap * 2), corner)
            continue
          }
          val isDown = index in currentPressed
          val depth = if (isDown) rowH * 0.02f else rowH * 0.055f
          val faceH = rowH - gap * 2 - depth
          val faceTop = top + gap + if (isDown) rowH * 0.04f else 0f
          drawRoundRect(KEY_SIDE, Offset(x, top + gap + depth), Size(keyW, faceH), corner)
          drawRoundRect(
            brush = Brush.verticalGradient(
              listOf(if (isDown) KEY_PRESSED_TOP else KEY_TOP, if (isDown) KEY_PRESSED_BOTTOM else KEY_BOTTOM),
              startY = faceTop,
              endY = faceTop + faceH,
            ),
            topLeft = Offset(x, faceTop),
            size = Size(keyW, faceH),
            cornerRadius = corner,
          )
          val caption = key.caption
          if (caption.isNotEmpty()) {
            val base = minOf(rowH * 0.46f, laneW * 0.38f)
            val px = when {
              caption.length == 1 -> base
              caption.length <= 4 -> base * 0.78f
              else -> base * 0.62f
            }
            val layout = textMeasurer.measure(
              caption,
              TextStyle(color = KEY_LEGEND, fontWeight = FontWeight.Medium, fontSize = px.toSp()),
              constraints = Constraints(maxWidth = keyW.toInt()),
            )
            drawText(layout, topLeft = Offset(x + (keyW - layout.size.width) / 2f, faceTop + (faceH - layout.size.height) / 2f))
          }
          if (shade > 0f) drawRoundRect(Color.Black.copy(alpha = shade), Offset(x, top + gap), Size(keyW, rowH - gap * 2), corner)
        }
      }
    }

    // Roller shading at both ends sells the curve of the belt.
    val band = rowH * 0.9f
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), startY = 0f, endY = band), size = Size(w, band))
    drawRect(
      Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startY = h - band, endY = h),
      topLeft = Offset(0f, h - band),
      size = Size(w, band),
    )
  }
}
