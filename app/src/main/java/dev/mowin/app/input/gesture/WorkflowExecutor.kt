package dev.mowin.app.input.gesture

import kotlinx.coroutines.delay
import java.util.Collections

/**
 * Common abstraction for real RFB/VNC dispatch and test-mode simulation.
 */
interface InputBridge {
    suspend fun sendPointer(buttonMask: Int, x: Int, y: Int)
    suspend fun sendKey(keySym: Int)
    suspend fun sendKeyDown(keySym: Int)
    suspend fun sendKeyUp(keySym: Int)
}

/**
 * Handles workflow sequences, shortcut execution, and safe release of held inputs.
 */
class WorkflowExecutor(private val bridge: InputBridge?) {

    private val heldKeys = Collections.synchronizedSet(mutableSetOf<Int>())
    private var activeMouseMask: Int = 0
    private var lastX: Int = 0
    private var lastY: Int = 0

    /**
     * Executes a configured action, returning human-readable steps executed.
     */
    suspend fun executeAction(
        config: GestureActionConfig,
        x: Int,
        y: Int,
        isTestMode: Boolean = false,
        onLog: (String) -> Unit = {},
    ): List<String> {
        lastX = x
        lastY = y
        val logs = mutableListOf<String>()

        fun log(msg: String) {
            logs.add(msg)
            onLog(msg)
        }

        when (config.actionType) {
            ActionType.MOUSE_CLICK -> {
                val mask = config.mouseButton.mask
                log("Mouse move -> ($x, $y)")
                log("${config.mouseButton.displayName} DOWN")
                if (!isTestMode && bridge != null) {
                    bridge.sendPointer(0, x, y)
                    delay(15)
                    bridge.sendPointer(mask, x, y)
                }
                delay(40)
                log("${config.mouseButton.displayName} UP")
                if (!isTestMode && bridge != null) {
                    bridge.sendPointer(0, x, y)
                }
            }

            ActionType.MOUSE_DRAG -> {
                val mask = config.mouseButton.mask
                activeMouseMask = mask
                log("${config.mouseButton.displayName} HOLD at ($x, $y)")
                if (!isTestMode && bridge != null) {
                    bridge.sendPointer(mask, x, y)
                }
            }

            ActionType.KEY_SHORTCUT, ActionType.WORKFLOW -> {
                val shortcut = StandardShortcuts.find(config.shortcutId)
                if (shortcut == null) {
                    log("Unknown shortcut ID: ${config.shortcutId}")
                    return logs
                }

                log("Executing workflow: ${shortcut.displayName}")
                for (step in shortcut.steps) {
                    when (step) {
                        is WorkflowStep.KeyDown -> {
                            log("Key DOWN: 0x${step.keySym.toString(16)} (${step.description})")
                            heldKeys.add(step.keySym)
                            if (!isTestMode && bridge != null) {
                                bridge.sendKeyDown(step.keySym)
                            }
                        }
                        is WorkflowStep.KeyUp -> {
                            log("Key UP: 0x${step.keySym.toString(16)} (${step.description})")
                            heldKeys.remove(step.keySym)
                            if (!isTestMode && bridge != null) {
                                bridge.sendKeyUp(step.keySym)
                            }
                        }
                        is WorkflowStep.MouseDown -> {
                            log("Mouse button DOWN (mask=${step.buttonMask})")
                            activeMouseMask = activeMouseMask or step.buttonMask
                            if (!isTestMode && bridge != null) {
                                bridge.sendPointer(activeMouseMask, x, y)
                            }
                        }
                        is WorkflowStep.MouseUp -> {
                            log("Mouse button UP (mask=${step.buttonMask})")
                            activeMouseMask = activeMouseMask and step.buttonMask.inv()
                            if (!isTestMode && bridge != null) {
                                bridge.sendPointer(activeMouseMask, x, y)
                            }
                        }
                        is WorkflowStep.Delay -> {
                            log("Wait ${step.durationMs}ms")
                            delay(step.durationMs)
                        }
                    }
                }
            }

            ActionType.NONE -> {
                log("Gesture ignored (Unmapped)")
            }
        }

        return logs
    }

    /**
     * Dispatches a mouse scroll wheel click (Wheel Up = Zoom In, Wheel Down = Zoom Out).
     */
    suspend fun executeWheel(
        isZoomIn: Boolean,
        x: Int,
        y: Int,
        isTestMode: Boolean = false,
        onLog: (String) -> Unit = {},
    ): List<String> {
        lastX = x
        lastY = y
        val logs = mutableListOf<String>()
        val button = if (isZoomIn) MouseButton.WHEEL_UP else MouseButton.WHEEL_DOWN
        val mask = button.mask
        val msg = if (isZoomIn) "CAD Zoom In (Wheel Up)" else "CAD Zoom Out (Wheel Down)"
        logs.add(msg)
        onLog(msg)
        if (!isTestMode && bridge != null) {
            bridge.sendPointer(mask, x, y)
            delay(20)
            bridge.sendPointer(0, x, y)
        }
        return logs
    }

    /**
     * Updates pointer position during an active drag.
     */
    suspend fun updateDrag(x: Int, y: Int, isTestMode: Boolean = false) {
        lastX = x
        lastY = y
        if (!isTestMode && bridge != null && activeMouseMask != 0) {
            bridge.sendPointer(activeMouseMask, x, y)
        }
    }

    /**
     * Safely releases all depressed mouse buttons and keys.
     * Prevents stuck inputs when gestures end or cancel.
     */
    suspend fun releaseAllHeld(isTestMode: Boolean = false, onLog: (String) -> Unit = {}) {
        if (activeMouseMask != 0) {
            onLog("Safe Release: Mouse button mask $activeMouseMask -> 0")
            activeMouseMask = 0
            if (!isTestMode && bridge != null) {
                bridge.sendPointer(0, lastX, lastY)
            }
        }

        val keysToRelease = synchronized(heldKeys) {
            val copy = heldKeys.toList()
            heldKeys.clear()
            copy
        }

        for (keySym in keysToRelease) {
            onLog("Safe Release: Key 0x${keySym.toString(16)}")
            if (!isTestMode && bridge != null) {
                bridge.sendKeyUp(keySym)
            }
        }
    }

    fun hasHeldInputs(): Boolean = activeMouseMask != 0 || heldKeys.isNotEmpty()
}
