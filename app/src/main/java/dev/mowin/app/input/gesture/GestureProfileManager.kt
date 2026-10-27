package dev.mowin.app.input.gesture

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages built-in and user-customized gesture profiles with JSON persistence.
 */
class GestureProfileManager(private val context: Context) {

    private val profilesFile = File(context.filesDir, "gesture_profiles.json")
    private val prefs = context.getSharedPreferences("gesture_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_ACTIVE_PROFILE_ID = "active_profile_id"
        const val DEFAULT_PROFILE_ID = "profile_default_desktop"
        const val CAD_PROFILE_ID = "profile_cad"
        const val PRODUCTIVITY_PROFILE_ID = "profile_productivity"
        const val STEAM_GAMING_PROFILE_ID = "profile_steam_gaming"

        fun createBuiltInProfiles(): List<GestureProfile> = listOf(
            GestureProfile(
                id = DEFAULT_PROFILE_ID,
                name = "Default Desktop",
                isReadOnly = false,
                mappings = mapOf(
                    TouchGestureType.SINGLE_TAP to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.LONG_PRESS to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.RIGHT,
                    ),
                    TouchGestureType.TWO_FINGER_TAP to GestureActionConfig(
                        actionType = ActionType.KEY_SHORTCUT,
                        shortcutId = "ESCAPE",
                    ),
                    TouchGestureType.ONE_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.MOUSE_DRAG,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.TWO_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.MOUSE_DRAG,
                        mouseButton = MouseButton.MIDDLE,
                    ),
                    TouchGestureType.PINCH_ZOOM to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.WHEEL_UP,
                        customLabel = "CAD Viewport Zoom (Wheel In/Out)",
                    ),
                )
            ),
            GestureProfile(
                id = CAD_PROFILE_ID,
                name = "CAD Workstation",
                isReadOnly = false,
                mappings = mapOf(
                    TouchGestureType.SINGLE_TAP to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.LONG_PRESS to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.RIGHT,
                    ),
                    TouchGestureType.TWO_FINGER_TAP to GestureActionConfig(
                        actionType = ActionType.WORKFLOW,
                        shortcutId = "CAD_ZOOM_EXTENTS",
                    ),
                    TouchGestureType.ONE_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.MOUSE_DRAG,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.TWO_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.MOUSE_DRAG,
                        mouseButton = MouseButton.MIDDLE,
                    ),
                    TouchGestureType.PINCH_ZOOM to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.WHEEL_UP,
                        customLabel = "CAD Viewport Zoom (Wheel In/Out)",
                    ),
                )
            ),
            GestureProfile(
                id = PRODUCTIVITY_PROFILE_ID,
                name = "Productivity & Coding",
                isReadOnly = false,
                mappings = mapOf(
                    TouchGestureType.SINGLE_TAP to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.LONG_PRESS to GestureActionConfig(
                        actionType = ActionType.KEY_SHORTCUT,
                        shortcutId = "CTRL_A",
                    ),
                    TouchGestureType.TWO_FINGER_TAP to GestureActionConfig(
                        actionType = ActionType.KEY_SHORTCUT,
                        shortcutId = "CTRL_Z",
                    ),
                    TouchGestureType.ONE_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.MOUSE_DRAG,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.TWO_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.KEY_SHORTCUT,
                        shortcutId = "CTRL_S",
                    ),
                )
            ),
            GestureProfile(
                id = STEAM_GAMING_PROFILE_ID,
                name = "Steam & Gaming",
                isReadOnly = false,
                mappings = mapOf(
                    TouchGestureType.SINGLE_TAP to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.LONG_PRESS to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.RIGHT,
                    ),
                    TouchGestureType.TWO_FINGER_TAP to GestureActionConfig(
                        actionType = ActionType.KEY_SHORTCUT,
                        shortcutId = "ESCAPE",
                    ),
                    TouchGestureType.ONE_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.MOUSE_DRAG,
                        mouseButton = MouseButton.LEFT,
                    ),
                    TouchGestureType.TWO_FINGER_DRAG to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.WHEEL_DOWN,
                        customLabel = "Scroll Down",
                    ),
                    TouchGestureType.PINCH_ZOOM to GestureActionConfig(
                        actionType = ActionType.MOUSE_CLICK,
                        mouseButton = MouseButton.WHEEL_UP,
                        customLabel = "Scroll Up",
                    ),
                )
            )
        )
    }

    /**
     * Loads all saved profiles, falling back to defaults.
     */
    fun loadProfiles(): List<GestureProfile> {
        if (!profilesFile.exists()) {
            val defaults = createBuiltInProfiles()
            saveProfiles(defaults)
            return defaults
        }

        return try {
            val jsonStr = profilesFile.readText()
            deserializeProfiles(jsonStr)
        } catch (_: Exception) {
            val defaults = createBuiltInProfiles()
            saveProfiles(defaults)
            defaults
        }
    }

    /**
     * Returns the currently active profile.
     */
    fun getActiveProfile(): GestureProfile {
        val profiles = loadProfiles()
        val activeId = prefs.getString(PREF_ACTIVE_PROFILE_ID, DEFAULT_PROFILE_ID)
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    /**
     * Sets the active profile ID.
     */
    fun setActiveProfileId(id: String) {
        prefs.edit().putString(PREF_ACTIVE_PROFILE_ID, id).apply()
    }

    /**
     * Saves or updates a specific profile.
     */
    fun saveProfile(profile: GestureProfile) {
        val current = loadProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        saveProfiles(current)
    }

    /**
     * Resets a built-in profile to its factory mapping.
     */
    fun resetProfileToDefault(profileId: String): GestureProfile {
        val builtIns = createBuiltInProfiles()
        val defaultProfile = builtIns.firstOrNull { it.id == profileId } ?: builtIns.first()
        saveProfile(defaultProfile)
        return defaultProfile
    }

    /**
     * Creates a new custom profile cloned from an existing one.
     */
    fun createCustomProfile(name: String, baseProfileId: String = DEFAULT_PROFILE_ID): GestureProfile {
        val current = loadProfiles()
        val base = current.firstOrNull { it.id == baseProfileId } ?: current.first()
        val newId = "custom_${System.currentTimeMillis()}"
        val newProfile = base.copy(
            id = newId,
            name = name,
            isReadOnly = false,
        )
        saveProfile(newProfile)
        setActiveProfileId(newId)
        return newProfile
    }

    /**
     * Deletes a profile (built-in profiles cannot be deleted).
     */
    fun deleteProfile(profileId: String): Boolean {
        if (profileId in listOf(DEFAULT_PROFILE_ID, CAD_PROFILE_ID, PRODUCTIVITY_PROFILE_ID)) {
            return false // Cannot delete standard profiles
        }
        val current = loadProfiles().toMutableList()
        val removed = current.removeAll { it.id == profileId }
        if (removed) {
            saveProfiles(current)
            if (prefs.getString(PREF_ACTIVE_PROFILE_ID, null) == profileId) {
                setActiveProfileId(DEFAULT_PROFILE_ID)
            }
        }
        return removed
    }

    private fun saveProfiles(profiles: List<GestureProfile>) {
        try {
            val jsonStr = serializeProfiles(profiles)
            profilesFile.writeText(jsonStr)
        } catch (_: Exception) {
        }
    }

    fun serializeProfiles(profiles: List<GestureProfile>): String {
        val root = JSONArray()
        for (profile in profiles) {
            val pObj = JSONObject()
            pObj.put("id", profile.id)
            pObj.put("name", profile.name)
            pObj.put("isReadOnly", profile.isReadOnly)

            val mapObj = JSONObject()
            for ((gesture, config) in profile.mappings) {
                val cObj = JSONObject()
                cObj.put("actionType", config.actionType.name)
                cObj.put("mouseButton", config.mouseButton.name)
                cObj.put("shortcutId", config.shortcutId ?: "")
                cObj.put("customLabel", config.customLabel ?: "")
                mapObj.put(gesture.name, cObj)
            }
            pObj.put("mappings", mapObj)
            root.put(pObj)
        }
        return root.toString(2)
    }

    fun deserializeProfiles(jsonStr: String): List<GestureProfile> {
        val list = mutableListOf<GestureProfile>()
        val root = JSONArray(jsonStr)
        for (i in 0 until root.length()) {
            val pObj = root.getJSONObject(i)
            val id = pObj.getString("id")
            val name = pObj.getString("name")
            val isReadOnly = pObj.optBoolean("isReadOnly", false)

            val mapObj = pObj.getJSONObject("mappings")
            val mappings = mutableMapOf<TouchGestureType, GestureActionConfig>()

            for (key in mapObj.keys()) {
                val gesture = try {
                    TouchGestureType.valueOf(key)
                } catch (_: Exception) {
                    null
                } ?: continue

                val cObj = mapObj.getJSONObject(key)
                val actionType = try {
                    ActionType.valueOf(cObj.getString("actionType"))
                } catch (_: Exception) {
                    ActionType.NONE
                }
                val mouseButton = try {
                    MouseButton.valueOf(cObj.optString("mouseButton", MouseButton.LEFT.name))
                } catch (_: Exception) {
                    MouseButton.LEFT
                }
                val shortcutId = cObj.optString("shortcutId").takeIf { it.isNotBlank() }
                val customLabel = cObj.optString("customLabel").takeIf { it.isNotBlank() }

                mappings[gesture] = GestureActionConfig(
                    actionType = actionType,
                    mouseButton = mouseButton,
                    shortcutId = shortcutId,
                    customLabel = customLabel,
                )
            }

            list.add(GestureProfile(id, name, isReadOnly, mappings))
        }

        return if (list.isEmpty()) createBuiltInProfiles() else list
    }
}
