package dev.mowin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.mowin.app.input.gesture.*
import kotlinx.coroutines.launch

@Composable
fun GestureEditorDialog(
    profileManager: GestureProfileManager,
    onDismiss: () -> Unit,
    onProfileChanged: (GestureProfile) -> Unit,
) {
    var profiles by remember { mutableStateOf(profileManager.loadProfiles()) }
    var activeProfile by remember { mutableStateOf(profileManager.getActiveProfile()) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Editor, 1 = Test Sandbox
    var editingGesture by remember { mutableStateOf<TouchGestureType?>(null) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val testExecutor = remember { WorkflowExecutor(bridge = null) }
    var testLog by remember { mutableStateOf(listOf("Ready. Perform touch gestures in the box below.")) }
    var lastRecognizedGesture by remember { mutableStateOf<TouchGestureType?>(null) }

    fun refreshProfiles(newActiveId: String? = null) {
        profiles = profileManager.loadProfiles()
        activeProfile = if (newActiveId != null) {
            profiles.firstOrNull { it.id == newActiveId } ?: profileManager.getActiveProfile()
        } else {
            profileManager.getActiveProfile()
        }
        onProfileChanged(activeProfile)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF131A22),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "TOUCH GESTURE WORKFLOWS",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "Custom gestures & CAD macros",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF90A4AE),
                        )
                    }

                    FilledTonalButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF263238)),
                    ) {
                        Text("✕ Done", fontSize = 12.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Profile Selector Row
                Text(
                    "ACTIVE PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF78909C),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    profiles.forEach { p ->
                        FilterChip(
                            selected = p.id == activeProfile.id,
                            onClick = {
                                profileManager.setActiveProfileId(p.id)
                                refreshProfiles(p.id)
                            },
                            label = { Text(p.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        )
                    }

                    // Add Custom Profile Button
                    OutlinedButton(
                        onClick = { showNewProfileDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text("+ New", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tabs: Editor vs Test Sandbox
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E2836),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("⚙ Gesture Mappings", fontSize = 13.sp) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("🎯 Test Sandbox", fontSize = 13.sp) },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                if (selectedTab == 0) {
                    // MAPPINGS LIST
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(TouchGestureType.values()) { gesture ->
                            val currentConfig = activeProfile.mappings[gesture] ?: GestureActionConfig(ActionType.NONE)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingGesture = gesture },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E242E)),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            gesture.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                        )
                                        Text(
                                            gesture.description,
                                            fontSize = 11.sp,
                                            color = Color(0xFF8190A0),
                                        )
                                    }

                                    Surface(
                                        color = when (currentConfig.actionType) {
                                            ActionType.MOUSE_CLICK -> Color(0xFF1A382A)
                                            ActionType.MOUSE_DRAG -> Color(0xFF1B2E3D)
                                            ActionType.KEY_SHORTCUT -> Color(0xFF382E1E)
                                            ActionType.WORKFLOW -> Color(0xFF2E1B38)
                                            ActionType.NONE -> Color(0xFF262A30)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = ButtonDefaults.outlinedButtonBorder,
                                    ) {
                                        Text(
                                            currentConfig.displayLabel,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = when (currentConfig.actionType) {
                                                ActionType.MOUSE_CLICK -> Color(0xFF4CAF50)
                                                ActionType.MOUSE_DRAG -> Color(0xFF42A5F5)
                                                ActionType.KEY_SHORTCUT -> Color(0xFFFFB74D)
                                                ActionType.WORKFLOW -> Color(0xFFBA68C8)
                                                ActionType.NONE -> Color(0xFF90A4AE)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Profile Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                activeProfile = profileManager.resetProfileToDefault(activeProfile.id)
                                refreshProfiles(activeProfile.id)
                            },
                        ) {
                            Text("Reset Profile to Default", fontSize = 12.sp, color = Color(0xFFFFB74D))
                        }

                        if (activeProfile.id !in listOf(
                                GestureProfileManager.DEFAULT_PROFILE_ID,
                                GestureProfileManager.CAD_PROFILE_ID,
                                GestureProfileManager.PRODUCTIVITY_PROFILE_ID
                            )
                        ) {
                            TextButton(
                                onClick = {
                                    profileManager.deleteProfile(activeProfile.id)
                                    refreshProfiles()
                                },
                            ) {
                                Text("Delete Profile", fontSize = 12.sp, color = Color(0xFFFF5252))
                            }
                        }
                    }
                } else {
                    // TEST SANDBOX TAB
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF00E676), RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "SAFE TEST MODE (Apps Protected)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                )
                            }
                            TextButton(
                                onClick = {
                                    testLog = listOf("Log cleared. Ready for input.")
                                    lastRecognizedGesture = null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Interactive Touch Area
                        val gestureListener = remember(activeProfile) {
                            object : GestureListener {
                                override fun onGestureRecognized(gesture: TouchGestureType, position: Offset) {
                                    lastRecognizedGesture = gesture
                                    val config = activeProfile.mappings[gesture] ?: GestureActionConfig(ActionType.NONE)
                                    scope.launch {
                                        testExecutor.executeAction(
                                            config = config,
                                            x = position.x.toInt(),
                                            y = position.y.toInt(),
                                            isTestMode = true,
                                            onLog = { msg ->
                                                testLog = (listOf("▶ [${gesture.displayName}] $msg") + testLog).take(30)
                                            }
                                        )
                                    }
                                }

                                override fun onDragUpdate(gesture: TouchGestureType, position: Offset, delta: Offset) {
                                    scope.launch {
                                        testExecutor.updateDrag(position.x.toInt(), position.y.toInt(), isTestMode = true)
                                    }
                                }

                                override fun onPinchZoom(isZoomIn: Boolean, center: Offset) {
                                    lastRecognizedGesture = TouchGestureType.PINCH_ZOOM
                                    scope.launch {
                                        testExecutor.executeWheel(
                                            isZoomIn = isZoomIn,
                                            x = center.x.toInt(),
                                            y = center.y.toInt(),
                                            isTestMode = true,
                                            onLog = { msg ->
                                                testLog = (listOf("▶ [Pinch to Zoom] $msg") + testLog).take(30)
                                            }
                                        )
                                    }
                                }

                                override fun onGestureEnd(gesture: TouchGestureType?) {
                                    scope.launch {
                                        testExecutor.releaseAllHeld(isTestMode = true) { msg ->
                                            testLog = (listOf("✓ $msg") + testLog).take(30)
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0A0E14))
                                .border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(10.dp))
                                .detectMoWinGestures(gestureListener),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    lastRecognizedGesture?.let { "Recognized: ${it.displayName}" }
                                        ?: "Touch, Drag, Long-Press, or 2-Finger Tap Here",
                                    fontWeight = FontWeight.Bold,
                                    color = if (lastRecognizedGesture != null) Color(0xFF64B5F6) else Color(0xFF78909C),
                                    fontSize = 13.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Profile: ${activeProfile.name}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF546E7A),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dispatched Actions Log Preview
                        Text(
                            "DISPATCHED ACTION STREAM",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF78909C),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF1B222D), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                        ) {
                            items(testLog) { line ->
                                Text(
                                    line,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        line.startsWith("▶") -> Color(0xFF81D4FA)
                                        line.startsWith("✓") -> Color(0xFF81C784)
                                        else -> Color(0xFFB0BEC5)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal to Edit Mapping for a specific gesture
    editingGesture?.let { gesture ->
        ActionSelectorDialog(
            gesture = gesture,
            initialConfig = activeProfile.mappings[gesture] ?: GestureActionConfig(ActionType.NONE),
            onDismiss = { editingGesture = null },
            onSave = { newConfig ->
                val updated = activeProfile.copyWithMapping(gesture, newConfig)
                activeProfile = updated
                profileManager.saveProfile(updated)
                refreshProfiles(updated.id)
                editingGesture = null
            }
        )
    }

    // Modal to Create New Custom Profile
    if (showNewProfileDialog) {
        AlertDialog(
            onDismissRequest = { showNewProfileDialog = false },
            title = { Text("New Custom Profile") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newProfileName.trim()
                        if (name.isNotBlank()) {
                            val created = profileManager.createCustomProfile(name, activeProfile.id)
                            refreshProfiles(created.id)
                            newProfileName = ""
                            showNewProfileDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dialog to configure the action assigned to a gesture.
 */
@Composable
private fun ActionSelectorDialog(
    gesture: TouchGestureType,
    initialConfig: GestureActionConfig,
    onDismiss: () -> Unit,
    onSave: (GestureActionConfig) -> Unit,
) {
    var selectedActionType by remember { mutableStateOf(initialConfig.actionType) }
    var selectedMouseButton by remember { mutableStateOf(initialConfig.mouseButton) }
    var selectedShortcutId by remember { mutableStateOf(initialConfig.shortcutId ?: StandardShortcuts.ALL.first().id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map '${gesture.displayName}'", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Action Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ActionType.values().forEach { at ->
                        FilterChip(
                            selected = selectedActionType == at,
                            onClick = { selectedActionType = at },
                            label = { Text(at.displayName, fontSize = 11.sp) },
                        )
                    }
                }

                when (selectedActionType) {
                    ActionType.MOUSE_CLICK, ActionType.MOUSE_DRAG -> {
                        Text("Mouse Button", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MouseButton.values().forEach { btn ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedMouseButton = btn }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedMouseButton == btn,
                                        onClick = { selectedMouseButton = btn },
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(btn.displayName, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    ActionType.KEY_SHORTCUT, ActionType.WORKFLOW -> {
                        Text("Keyboard Workflow Preset", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StandardShortcuts.ALL.forEach { sc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedShortcutId = sc.id }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selectedShortcutId == sc.id,
                                        onClick = { selectedShortcutId = sc.id },
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(sc.displayName, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    ActionType.NONE -> {
                        Text(
                            "This gesture will be ignored.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = when (selectedActionType) {
                        ActionType.MOUSE_CLICK, ActionType.MOUSE_DRAG -> GestureActionConfig(
                            actionType = selectedActionType,
                            mouseButton = selectedMouseButton,
                        )
                        ActionType.KEY_SHORTCUT, ActionType.WORKFLOW -> GestureActionConfig(
                            actionType = selectedActionType,
                            shortcutId = selectedShortcutId,
                        )
                        ActionType.NONE -> GestureActionConfig(ActionType.NONE)
                    }
                    onSave(config)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
