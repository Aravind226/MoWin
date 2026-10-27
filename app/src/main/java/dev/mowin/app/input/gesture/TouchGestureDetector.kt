package dev.mowin.app.input.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAny
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Event callbacks emitted by the gesture detector.
 */
interface GestureListener {
    fun onGestureRecognized(gesture: TouchGestureType, position: Offset)
    fun onDragUpdate(gesture: TouchGestureType, position: Offset, delta: Offset)
    fun onPinchZoom(isZoomIn: Boolean, center: Offset) {}
    fun onGestureEnd(gesture: TouchGestureType?)
}

/**
 * Pure Kotlin recognizer logic (easy to unit-test and deterministic).
 */
class GestureRecognizerState(
    val slopPx: Float = 24f,
    val longPressTimeoutMs: Long = 400L,
    val multiTouchWindowMs: Long = 180L,
) {
    var activeGesture: TouchGestureType? = null
        private set

    private var initialDownTime: Long = 0L
    private var initialPos: Offset = Offset.Zero
    private var lastPos: Offset = Offset.Zero
    private var pointerCount: Int = 0
    private var maxPointersInGesture: Int = 0
    private var hasMovedPastSlop: Boolean = false

    fun onPointerDown(count: Int, position: Offset, timestamp: Long) {
        pointerCount = count
        if (count > maxPointersInGesture) {
            maxPointersInGesture = count
        }
        if (count == 1 && initialDownTime == 0L) {
            initialDownTime = timestamp
            initialPos = position
            lastPos = position
            hasMovedPastSlop = false
            activeGesture = null
        }
    }

    fun onPointerMove(position: Offset): TouchGestureType? {
        lastPos = position
        val dx = abs(position.x - initialPos.x)
        val dy = abs(position.y - initialPos.y)

        if (dx > slopPx || dy > slopPx) {
            hasMovedPastSlop = true
            if (activeGesture == null) {
                activeGesture = if (maxPointersInGesture >= 2) {
                    TouchGestureType.TWO_FINGER_DRAG
                } else {
                    TouchGestureType.ONE_FINGER_DRAG
                }
                return activeGesture
            }
        }
        return null
    }

    fun checkLongPress(currentTime: Long): TouchGestureType? {
        if (!hasMovedPastSlop && maxPointersInGesture == 1 && activeGesture == null) {
            if (currentTime - initialDownTime >= longPressTimeoutMs) {
                activeGesture = TouchGestureType.LONG_PRESS
                return activeGesture
            }
        }
        return null
    }

    fun onPointerUp(remainingCount: Int, currentTime: Long): TouchGestureType? {
        pointerCount = remainingCount
        if (remainingCount == 0) {
            val duration = currentTime - initialDownTime
            val recognized = when {
                activeGesture != null -> activeGesture
                hasMovedPastSlop -> {
                    if (maxPointersInGesture >= 2) TouchGestureType.TWO_FINGER_DRAG
                    else TouchGestureType.ONE_FINGER_DRAG
                }
                maxPointersInGesture >= 2 && duration <= multiTouchWindowMs + 300L -> {
                    TouchGestureType.TWO_FINGER_TAP
                }
                duration >= longPressTimeoutMs -> {
                    TouchGestureType.LONG_PRESS
                }
                else -> {
                    TouchGestureType.SINGLE_TAP
                }
            }

            // Reset
            initialDownTime = 0L
            maxPointersInGesture = 0
            hasMovedPastSlop = false
            activeGesture = null
            return recognized
        }
        return null
    }

    fun reset() {
        initialDownTime = 0L
        maxPointersInGesture = 0
        hasMovedPastSlop = false
        activeGesture = null
        pointerCount = 0
    }
}

/**
 * Compose Modifier extension for gesture recognition.
 */
fun Modifier.detectMoWinGestures(
    listener: GestureListener,
): Modifier = pointerInput(Unit) {
    val state = GestureRecognizerState(
        slopPx = 20.coerceAtLeast(viewConfiguration.touchSlop.toInt()).toFloat(),
        longPressTimeoutMs = 380L,
    )

    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            val startTime = System.currentTimeMillis()
            var currentPos = down.position
            var prevPos = currentPos
            state.onPointerDown(1, currentPos, startTime)

            var isGestureActive = true
            var longPressJob = launch {
                delay(380L)
                if (isGestureActive) {
                    val lp = state.checkLongPress(System.currentTimeMillis())
                    if (lp != null) {
                        listener.onGestureRecognized(lp, currentPos)
                    }
                }
            }

            var recognizedDragGesture: TouchGestureType? = null
            var prevMultiTouchDist = 0f

            while (isGestureActive) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val activePointers = event.changes.filter { it.pressed }
                val now = System.currentTimeMillis()

                if (activePointers.isEmpty()) {
                    longPressJob.cancel()
                    isGestureActive = false
                    val finalGesture = state.onPointerUp(0, now)
                    if (recognizedDragGesture == null && finalGesture != null) {
                        listener.onGestureRecognized(finalGesture, currentPos)
                    }
                    listener.onGestureEnd(recognizedDragGesture ?: finalGesture)
                    event.changes.forEach { it.consume() }
                    break
                }

                state.onPointerDown(activePointers.size, activePointers.first().position, now)
                val primary = activePointers.first()
                currentPos = primary.position
                val delta = currentPos - prevPos
                prevPos = currentPos

                // Multi-touch Pinch to Zoom detection (for CAD viewports)
                if (activePointers.size >= 2) {
                    val p1 = activePointers[0].position
                    val p2 = activePointers[1].position
                    val dist = kotlin.math.hypot((p1.x - p2.x), (p1.y - p2.y))
                    val center = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)

                    if (prevMultiTouchDist > 0f) {
                        val distDelta = dist - prevMultiTouchDist
                        if (kotlin.math.abs(distDelta) >= 18f) {
                            val isZoomIn = distDelta > 0f
                            listener.onPinchZoom(isZoomIn, center)
                            prevMultiTouchDist = dist
                        }
                    } else {
                        prevMultiTouchDist = dist
                    }
                } else {
                    prevMultiTouchDist = 0f
                }

                val dragStart = state.onPointerMove(currentPos)
                if (dragStart != null && recognizedDragGesture == null) {
                    longPressJob.cancel()
                    recognizedDragGesture = dragStart
                    listener.onGestureRecognized(dragStart, currentPos)
                }

                if (recognizedDragGesture != null && delta != Offset.Zero) {
                    listener.onDragUpdate(recognizedDragGesture, currentPos, delta)
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }
}
