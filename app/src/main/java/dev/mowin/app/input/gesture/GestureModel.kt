package dev.mowin.app.input.gesture

/**
 * Recognizable touch gestures on the screen surface.
 */
enum class TouchGestureType(val displayName: String, val description: String) {
    SINGLE_TAP("Single Tap", "Tap with one finger"),
    LONG_PRESS("Long Press", "Press and hold with one finger"),
    TWO_FINGER_TAP("Two-Finger Tap", "Simultaneous tap with two fingers"),
    ONE_FINGER_DRAG("One-Finger Drag", "Press and slide with one finger"),
    TWO_FINGER_DRAG("Two-Finger Drag", "Slide two fingers together (CAD Pan/Scroll)"),
    PINCH_ZOOM("Pinch to Zoom", "Pinch or spread two fingers (CAD Zoom In/Out)"),
}

/**
 * Categories of actions a gesture can trigger.
 */
enum class ActionType(val displayName: String) {
    MOUSE_CLICK("Mouse Click"),
    MOUSE_DRAG("Mouse Drag"),
    KEY_SHORTCUT("Keyboard Shortcut"),
    WORKFLOW("Workflow Sequence"),
    NONE("No Action"),
}

/**
 * Mouse buttons mapped to RFB bitmasks.
 */
enum class MouseButton(val mask: Int, val displayName: String) {
    LEFT(1, "Left Click"),
    MIDDLE(2, "Middle Click (CAD Pan)"),
    RIGHT(4, "Right Click (Context)"),
    WHEEL_UP(8, "Scroll Wheel Up (CAD Zoom In)"),
    WHEEL_DOWN(16, "Scroll Wheel Down (CAD Zoom Out)"),
}

/**
 * Discrete step in a workflow sequence.
 */
sealed class WorkflowStep {
    data class KeyDown(val keySym: Int, val description: String = "Key Down") : WorkflowStep()
    data class KeyUp(val keySym: Int, val description: String = "Key Up") : WorkflowStep()
    data class MouseDown(val buttonMask: Int, val description: String = "Mouse Down") : WorkflowStep()
    data class MouseUp(val buttonMask: Int, val description: String = "Mouse Up") : WorkflowStep()
    data class Delay(val durationMs: Long) : WorkflowStep()
}

/**
 * Standard X11 KeySyms for Windows workflows.
 */
object KeySyms {
    const val BACKSPACE = 0xFF08
    const val TAB = 0xFF09
    const val RETURN = 0xFF0D
    const val ESCAPE = 0xFF1B
    const val DELETE = 0xFFFF
    const val CTRL_L = 0xFFE3
    const val SHIFT_L = 0xFFE1
    const val ALT_L = 0xFFE9
    const val SPACE = 0x0020

    // Standard ASCII keys
    const val KEY_A = 0x0061
    const val KEY_C = 0x0063
    const val KEY_E = 0x0065
    const val KEY_S = 0x0073
    const val KEY_V = 0x0076
    const val KEY_Y = 0x0079
    const val KEY_Z = 0x007A
}

/**
 * Named shortcut definition.
 */
data class ShortcutDefinition(
    val id: String,
    val displayName: String,
    val steps: List<WorkflowStep>,
)

object StandardShortcuts {
    val CTRL_S = ShortcutDefinition(
        id = "CTRL_S",
        displayName = "Ctrl+S (Save)",
        steps = listOf(
            WorkflowStep.KeyDown(KeySyms.CTRL_L, "Ctrl Down"),
            WorkflowStep.KeyDown(KeySyms.KEY_S, "'S' Down"),
            WorkflowStep.Delay(30),
            WorkflowStep.KeyUp(KeySyms.KEY_S, "'S' Up"),
            WorkflowStep.KeyUp(KeySyms.CTRL_L, "Ctrl Up"),
        )
    )

    val CTRL_Z = ShortcutDefinition(
        id = "CTRL_Z",
        displayName = "Ctrl+Z (Undo)",
        steps = listOf(
            WorkflowStep.KeyDown(KeySyms.CTRL_L, "Ctrl Down"),
            WorkflowStep.KeyDown(KeySyms.KEY_Z, "'Z' Down"),
            WorkflowStep.Delay(30),
            WorkflowStep.KeyUp(KeySyms.KEY_Z, "'Z' Up"),
            WorkflowStep.KeyUp(KeySyms.CTRL_L, "Ctrl Up"),
        )
    )

    val CTRL_Y = ShortcutDefinition(
        id = "CTRL_Y",
        displayName = "Ctrl+Y (Redo)",
        steps = listOf(
            WorkflowStep.KeyDown(KeySyms.CTRL_L, "Ctrl Down"),
            WorkflowStep.KeyDown(KeySyms.KEY_Y, "'Y' Down"),
            WorkflowStep.Delay(30),
            WorkflowStep.KeyUp(KeySyms.KEY_Y, "'Y' Up"),
            WorkflowStep.KeyUp(KeySyms.CTRL_L, "Ctrl Up"),
        )
    )

    val CTRL_A = ShortcutDefinition(
        id = "CTRL_A",
        displayName = "Ctrl+A (Select All)",
        steps = listOf(
            WorkflowStep.KeyDown(KeySyms.CTRL_L, "Ctrl Down"),
            WorkflowStep.KeyDown(KeySyms.KEY_A, "'A' Down"),
            WorkflowStep.Delay(30),
            WorkflowStep.KeyUp(KeySyms.KEY_A, "'A' Up"),
            WorkflowStep.KeyUp(KeySyms.CTRL_L, "Ctrl Up"),
        )
    )

    val ESCAPE = ShortcutDefinition(
        id = "ESCAPE",
        displayName = "Esc (Cancel / Clear Selection)",
        steps = listOf(
            WorkflowStep.KeyDown(KeySyms.ESCAPE, "Esc Down"),
            WorkflowStep.Delay(30),
            WorkflowStep.KeyUp(KeySyms.ESCAPE, "Esc Up"),
        )
    )

    val CAD_ZOOM_EXTENTS = ShortcutDefinition(
        id = "CAD_ZOOM_EXTENTS",
        displayName = "CAD: Zoom Extents (Z ⏎ E ⏎)",
        steps = listOf(
            WorkflowStep.KeyDown(KeySyms.KEY_Z, "'Z' Down"),
            WorkflowStep.KeyUp(KeySyms.KEY_Z, "'Z' Up"),
            WorkflowStep.Delay(20),
            WorkflowStep.KeyDown(KeySyms.RETURN, "Enter Down"),
            WorkflowStep.KeyUp(KeySyms.RETURN, "Enter Up"),
            WorkflowStep.Delay(30),
            WorkflowStep.KeyDown(KeySyms.KEY_E, "'E' Down"),
            WorkflowStep.KeyUp(KeySyms.KEY_E, "'E' Up"),
            WorkflowStep.Delay(20),
            WorkflowStep.KeyDown(KeySyms.RETURN, "Enter Down"),
            WorkflowStep.KeyUp(KeySyms.RETURN, "Enter Up"),
        )
    )

    val ALL = listOf(CTRL_S, CTRL_Z, CTRL_Y, CTRL_A, ESCAPE, CAD_ZOOM_EXTENTS)

    fun find(id: String?): ShortcutDefinition? = ALL.firstOrNull { it.id == id }
}

/**
 * User-configurable action mapped to a gesture.
 */
data class GestureActionConfig(
    val actionType: ActionType,
    val mouseButton: MouseButton = MouseButton.LEFT,
    val shortcutId: String? = null,
    val customLabel: String? = null,
) {
    val displayLabel: String
        get() = when (actionType) {
            ActionType.MOUSE_CLICK -> mouseButton.displayName
            ActionType.MOUSE_DRAG -> "${mouseButton.displayName} Drag"
            ActionType.KEY_SHORTCUT, ActionType.WORKFLOW -> {
                val sc = StandardShortcuts.find(shortcutId)
                sc?.displayName ?: (customLabel ?: "Shortcut ($shortcutId)")
            }
            ActionType.NONE -> "Unmapped"
        }
}

/**
 * Complete gesture profile.
 */
data class GestureProfile(
    val id: String,
    val name: String,
    val isReadOnly: Boolean = false,
    val mappings: Map<TouchGestureType, GestureActionConfig>,
) {
    fun copyWithMapping(gesture: TouchGestureType, config: GestureActionConfig): GestureProfile {
        val updated = mappings.toMutableMap()
        updated[gesture] = config
        return copy(mappings = updated)
    }
}
