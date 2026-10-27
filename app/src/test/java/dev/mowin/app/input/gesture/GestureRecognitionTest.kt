package dev.mowin.app.input.gesture

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GestureRecognitionTest {

    private lateinit var state: GestureRecognizerState

    @Before
    fun setUp() {
        state = GestureRecognizerState(
            slopPx = 20f,
            longPressTimeoutMs = 400L,
            multiTouchWindowMs = 200L,
        )
    }

    @Test
    fun testSingleTapRecognition() {
        val t0 = 1000L
        state.onPointerDown(count = 1, position = Offset(100f, 100f), timestamp = t0)

        // Move slightly under slop (e.g. 5px jitter)
        val move = state.onPointerMove(Offset(103f, 102f))
        assertNull("Small jitter should not trigger drag", move)

        // Release quickly (120ms)
        val recognized = state.onPointerUp(remainingCount = 0, currentTime = t0 + 120L)
        assertEquals("Quick tap without slop should be SINGLE_TAP", TouchGestureType.SINGLE_TAP, recognized)
    }

    @Test
    fun testLongPressRecognition() {
        val t0 = 1000L
        state.onPointerDown(count = 1, position = Offset(100f, 100f), timestamp = t0)

        // Check before timeout
        val beforeTimeout = state.checkLongPress(currentTime = t0 + 200L)
        assertNull("Should not fire before 400ms", beforeTimeout)

        // Check after timeout
        val afterTimeout = state.checkLongPress(currentTime = t0 + 450L)
        assertEquals("Should fire LONG_PRESS after timeout", TouchGestureType.LONG_PRESS, afterTimeout)

        // Up should retain LONG_PRESS
        val recognized = state.onPointerUp(remainingCount = 0, currentTime = t0 + 500L)
        assertEquals(TouchGestureType.LONG_PRESS, recognized)
    }

    @Test
    fun testTwoFingerTapRecognition() {
        val t0 = 1000L
        state.onPointerDown(count = 1, position = Offset(100f, 100f), timestamp = t0)
        state.onPointerDown(count = 2, position = Offset(150f, 150f), timestamp = t0 + 30L)

        // Both fingers lift quickly without moving past slop
        state.onPointerUp(remainingCount = 1, currentTime = t0 + 100L)
        val recognized = state.onPointerUp(remainingCount = 0, currentTime = t0 + 110L)

        assertEquals("Multi-touch quick tap should be TWO_FINGER_TAP", TouchGestureType.TWO_FINGER_TAP, recognized)
    }

    @Test
    fun testOneFingerDragRecognition() {
        val t0 = 1000L
        state.onPointerDown(count = 1, position = Offset(100f, 100f), timestamp = t0)

        // Move past slop (deltaX = 35 > 20)
        val drag = state.onPointerMove(Offset(135f, 100f))
        assertEquals("Exceeding slop should trigger ONE_FINGER_DRAG", TouchGestureType.ONE_FINGER_DRAG, drag)

        // Subsequent release ends drag
        val recognized = state.onPointerUp(remainingCount = 0, currentTime = t0 + 600L)
        assertEquals(TouchGestureType.ONE_FINGER_DRAG, recognized)
    }

    @Test
    fun testTwoFingerDragRecognition() {
        val t0 = 1000L
        state.onPointerDown(count = 1, position = Offset(100f, 100f), timestamp = t0)
        state.onPointerDown(count = 2, position = Offset(200f, 100f), timestamp = t0 + 20L)

        // Drag fingers together past slop
        val drag = state.onPointerMove(Offset(100f, 160f))
        assertEquals("Two finger move past slop should trigger TWO_FINGER_DRAG", TouchGestureType.TWO_FINGER_DRAG, drag)

        val recognized = state.onPointerUp(remainingCount = 0, currentTime = t0 + 500L)
        assertEquals(TouchGestureType.TWO_FINGER_DRAG, recognized)
    }

    @Test
    fun testPinchZoomDeltaCalculation() {
        // Multi-touch distance delta
        val p1Start = Offset(100f, 100f)
        val p2Start = Offset(200f, 100f)
        val initialDist = kotlin.math.hypot(p1Start.x - p2Start.x, p1Start.y - p2Start.y)
        assertEquals(100f, initialDist, 0.001f)

        // Spread fingers outward (Zoom in)
        val p1Spread = Offset(80f, 100f)
        val p2Spread = Offset(240f, 100f)
        val spreadDist = kotlin.math.hypot(p1Spread.x - p2Spread.x, p1Spread.y - p2Spread.y)
        val deltaZoomIn = spreadDist - initialDist
        assertTrue("Spread should be positive zoom in delta", deltaZoomIn >= 18f)

        // Pinch fingers inward (Zoom out)
        val p1Pinch = Offset(130f, 100f)
        val p2Pinch = Offset(170f, 100f)
        val pinchDist = kotlin.math.hypot(p1Pinch.x - p2Pinch.x, p1Pinch.y - p2Pinch.y)
        val deltaZoomOut = pinchDist - initialDist
        assertTrue("Pinch should be negative zoom out delta", deltaZoomOut <= -18f)
    }
}
