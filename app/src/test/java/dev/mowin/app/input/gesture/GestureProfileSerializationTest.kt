package dev.mowin.app.input.gesture

import org.junit.Assert.*
import org.junit.Test

class GestureProfileSerializationTest {

    @Test
    fun testSerializationAndDeserializationRoundtrip() {
        val builtIns = GestureProfileManager.createBuiltInProfiles()
        assertEquals(4, builtIns.size)

        // Modify one mapping in default profile
        val modified = builtIns[0].copyWithMapping(
            TouchGestureType.TWO_FINGER_TAP,
            GestureActionConfig(
                actionType = ActionType.WORKFLOW,
                shortcutId = "CAD_ZOOM_EXTENTS",
            )
        )

        val profilesToTest = listOf(modified, builtIns[1], builtIns[2], builtIns[3])

        // Mock manager serializer
        val dummyManager = GestureProfileManagerFake()
        val json = dummyManager.serializeProfiles(profilesToTest)
        assertNotNull(json)
        assertTrue(json.contains("CAD_ZOOM_EXTENTS"))
        assertTrue(json.contains("Default Desktop"))
        assertTrue(json.contains("CAD Workstation"))
        assertTrue(json.contains("Steam & Gaming"))

        val deserialized = dummyManager.deserializeProfiles(json)
        assertEquals(4, deserialized.size)

        val roundtripDefault = deserialized.first { it.id == GestureProfileManager.DEFAULT_PROFILE_ID }
        val roundtripMapping = roundtripDefault.mappings[TouchGestureType.TWO_FINGER_TAP]
        assertNotNull(roundtripMapping)
        assertEquals(ActionType.WORKFLOW, roundtripMapping?.actionType)
        assertEquals("CAD_ZOOM_EXTENTS", roundtripMapping?.shortcutId)
    }

    @Test
    fun testStandardShortcutsDefinitionIntegrity() {
        val save = StandardShortcuts.find("CTRL_S")
        assertNotNull(save)
        assertEquals("Ctrl+S (Save)", save?.displayName)
        assertTrue(save?.steps?.isNotEmpty() == true)

        val zoom = StandardShortcuts.find("CAD_ZOOM_EXTENTS")
        assertNotNull(zoom)
        assertEquals("CAD: Zoom Extents (Z ⏎ E ⏎)", zoom?.displayName)
    }
}

/**
 * Fake that uses pure serialization without requiring Android Context/SharedPreferences.
 */
class GestureProfileManagerFake {
    fun serializeProfiles(profiles: List<GestureProfile>): String {
        val root = org.json.JSONArray()
        for (profile in profiles) {
            val pObj = org.json.JSONObject()
            pObj.put("id", profile.id)
            pObj.put("name", profile.name)
            pObj.put("isReadOnly", profile.isReadOnly)

            val mapObj = org.json.JSONObject()
            for ((gesture, config) in profile.mappings) {
                val cObj = org.json.JSONObject()
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
        val root = org.json.JSONArray(jsonStr)
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

        return list
    }
}
