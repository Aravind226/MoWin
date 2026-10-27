package dev.mowin.app.input.gesture

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorkflowExecutorTest {

    private lateinit var mockBridge: TestInputBridge
    private lateinit var executor: WorkflowExecutor

    class TestInputBridge : InputBridge {
        val pointerEvents = mutableListOf<Triple<Int, Int, Int>>() // mask, x, y
        val keyEvents = mutableListOf<Pair<String, Int>>() // "DOWN" / "UP", keysym

        override suspend fun sendPointer(buttonMask: Int, x: Int, y: Int) {
            pointerEvents.add(Triple(buttonMask, x, y))
        }

        override suspend fun sendKey(keySym: Int) {
            keyEvents.add("DOWN" to keySym)
            keyEvents.add("UP" to keySym)
        }

        override suspend fun sendKeyDown(keySym: Int) {
            keyEvents.add("DOWN" to keySym)
        }

        override suspend fun sendKeyUp(keySym: Int) {
            keyEvents.add("UP" to keySym)
        }
    }

    @Before
    fun setUp() {
        mockBridge = TestInputBridge()
        executor = WorkflowExecutor(mockBridge)
    }

    @Test
    fun testMouseClickExecution() = runBlocking {
        val config = GestureActionConfig(
            actionType = ActionType.MOUSE_CLICK,
            mouseButton = MouseButton.RIGHT,
        )

        val logs = executor.executeAction(config, x = 300, y = 400, isTestMode = false)

        assertTrue(logs.any { it.contains("Right Click") && it.contains("DOWN") })
        assertTrue(logs.any { it.contains("Right Click") && it.contains("UP") })

        // Check bridge pointer events
        assertEquals(3, mockBridge.pointerEvents.size)
        assertEquals(0, mockBridge.pointerEvents[0].first) // Move to (300, 400)
        assertEquals(4, mockBridge.pointerEvents[1].first) // Down (mask=4)
        assertEquals(0, mockBridge.pointerEvents[2].first) // Up (mask=0)
    }

    @Test
    fun testWorkflowShortcutExecution() = runBlocking {
        val config = GestureActionConfig(
            actionType = ActionType.KEY_SHORTCUT,
            shortcutId = "CTRL_S",
        )

        val logs = executor.executeAction(config, x = 100, y = 100, isTestMode = false)

        assertTrue(logs.any { it.contains("Executing workflow: Ctrl+S (Save)") })
        assertEquals(4, mockBridge.keyEvents.size)
        assertEquals("DOWN" to KeySyms.CTRL_L, mockBridge.keyEvents[0])
        assertEquals("DOWN" to KeySyms.KEY_S, mockBridge.keyEvents[1])
        assertEquals("UP" to KeySyms.KEY_S, mockBridge.keyEvents[2])
        assertEquals("UP" to KeySyms.CTRL_L, mockBridge.keyEvents[3])
    }

    @Test
    fun testSafeReleaseOnDragCancellation() = runBlocking {
        // Start mouse drag
        val config = GestureActionConfig(
            actionType = ActionType.MOUSE_DRAG,
            mouseButton = MouseButton.MIDDLE,
        )

        executor.executeAction(config, x = 100, y = 200, isTestMode = false)
        assertTrue(executor.hasHeldInputs())

        // Update drag position
        executor.updateDrag(x = 150, y = 250, isTestMode = false)

        // Cancellation / End triggers safe release
        executor.releaseAllHeld(isTestMode = false)
        assertFalse("Inputs must be completely released", executor.hasHeldInputs())

        // Verify last pointer event released the mask
        val lastEvent = mockBridge.pointerEvents.last()
        assertEquals(0, lastEvent.first) // mask = 0
    }

    @Test
    fun testTestModeDoesNotDispatchToHardware() = runBlocking {
        val config = GestureActionConfig(
            actionType = ActionType.MOUSE_CLICK,
            mouseButton = MouseButton.LEFT,
        )

        val logs = executor.executeAction(config, x = 50, y = 50, isTestMode = true)

        assertTrue(logs.isNotEmpty())
        assertTrue("Bridge should have received ZERO events in test mode", mockBridge.pointerEvents.isEmpty())
        assertTrue("Bridge should have received ZERO keys in test mode", mockBridge.keyEvents.isEmpty())
    }

    @Test
    fun testWheelZoomExecution() = runBlocking {
        // Zoom In (Wheel Up: mask = 8)
        val logsIn = executor.executeWheel(isZoomIn = true, x = 640, y = 360, isTestMode = false)
        assertTrue(logsIn.any { it.contains("Zoom In") })
        assertEquals(2, mockBridge.pointerEvents.size)
        assertEquals(8, mockBridge.pointerEvents[0].first) // Wheel Up mask 8
        assertEquals(0, mockBridge.pointerEvents[1].first) // Released mask 0

        // Zoom Out (Wheel Down: mask = 16)
        val logsOut = executor.executeWheel(isZoomIn = false, x = 640, y = 360, isTestMode = false)
        assertTrue(logsOut.any { it.contains("Zoom Out") })
        assertEquals(4, mockBridge.pointerEvents.size)
        assertEquals(16, mockBridge.pointerEvents[2].first) // Wheel Down mask 16
        assertEquals(0, mockBridge.pointerEvents[3].first) // Released mask 0
    }
}
