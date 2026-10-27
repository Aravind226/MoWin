package dev.mowin.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.mowin.app.input.gesture.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Arrays

/**
 * Robust RFB (VNC) client implementing InputBridge for pointer and keyboard dispatch.
 * Synchronized with a Mutex to guarantee ordered, atomic TCP packet writes,
 * and a conflated channel for ultra-smooth 60-120Hz drag and pointer streaming without lag.
 */
class VncInputClient : InputBridge {
    private var socket: Socket? = null
    private val socketMutex = Mutex()
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workerJob: Job? = null
    private var connected = false

    private class PointerMove(val mask: Int, val x: Int, val y: Int)
    private val pointerChannel = Channel<PointerMove>(capacity = Channel.CONFLATED)

    private suspend fun internalConnect(): Boolean {
        try {
            if (socket != null && !socket!!.isClosed) return true
            val s = Socket("127.0.0.1", 5900)
            s.tcpNoDelay = true
            s.sendBufferSize = 64 * 1024
            val input = s.getInputStream()
            val output = s.getOutputStream()
            val buf = ByteArray(1024)

            // RFB Handshake (RFB 003.008)
            input.read(buf, 0, 12)
            output.write("RFB 003.008\n".toByteArray())
            input.read(buf)
            output.write(byteArrayOf(1)) // Security type 1 = None
            input.read(buf, 0, 4)       // Auth result
            output.write(byteArrayOf(1)) // ClientInit (shared)
            input.read(buf, 0, 24)      // ServerInit

            socket = s
            connected = true

            // Start conflated pointer streaming worker
            workerJob?.cancel()
            workerJob = clientScope.launch {
                for (move in pointerChannel) {
                    if (!connected) break
                    sendPointerInternal(move.mask, move.x, move.y)
                }
            }

            return true
        } catch (e: Exception) {
            socket = null
            connected = false
            return false
        }
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        socketMutex.withLock { internalConnect() }
    }

    private suspend fun sendPointerInternal(buttonMask: Int, x: Int, y: Int) {
        socketMutex.withLock {
            try {
                if (socket == null || socket!!.isClosed) {
                    if (!internalConnect()) return@withLock
                }
                val clampedX = x.coerceIn(0, 1279)
                val clampedY = y.coerceIn(0, 719)
                val packet = byteArrayOf(
                    5, // PointerEvent
                    buttonMask.toByte(),
                    (clampedX ushr 8).toByte(),
                    (clampedX and 0xFF).toByte(),
                    (clampedY ushr 8).toByte(),
                    (clampedY and 0xFF).toByte(),
                )
                socket?.getOutputStream()?.apply {
                    write(packet)
                    flush()
                }
            } catch (e: Exception) {
                try { socket?.close() } catch (e: Exception) {}
                socket = null
                connected = false
            }
        }
    }

    override suspend fun sendPointerMove(buttonMask: Int, x: Int, y: Int) {
        if (connected) {
            pointerChannel.trySend(PointerMove(buttonMask, x, y))
        } else {
            sendPointerInternal(buttonMask, x, y)
        }
    }

    override suspend fun sendPointer(buttonMask: Int, x: Int, y: Int) = withContext(Dispatchers.IO) {
        sendPointerInternal(buttonMask, x, y)
    }

    override suspend fun sendKeyDown(keySym: Int) = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            try {
                if (socket == null || socket!!.isClosed) {
                    if (!internalConnect()) return@withLock
                }
                val out = socket?.getOutputStream() ?: return@withLock
                val downPkt = byteArrayOf(
                    4, // KeyEvent
                    1, // Down
                    0, 0,
                    (keySym ushr 24).toByte(),
                    (keySym ushr 16).toByte(),
                    (keySym ushr 8).toByte(),
                    (keySym and 0xFF).toByte(),
                )
                out.write(downPkt)
                out.flush()
            } catch (e: Exception) {
                try { socket?.close() } catch (e: Exception) {}
                socket = null
                connected = false
            }
        }
    }

    override suspend fun sendKeyUp(keySym: Int) = withContext(Dispatchers.IO) {
        socketMutex.withLock {
            try {
                if (socket == null || socket!!.isClosed) {
                    if (!internalConnect()) return@withLock
                }
                val out = socket?.getOutputStream() ?: return@withLock
                val upPkt = byteArrayOf(
                    4, // KeyEvent
                    0, // Up
                    0, 0,
                    (keySym ushr 24).toByte(),
                    (keySym ushr 16).toByte(),
                    (keySym ushr 8).toByte(),
                    (keySym and 0xFF).toByte(),
                )
                out.write(upPkt)
                out.flush()
            } catch (e: Exception) {
                try { socket?.close() } catch (e: Exception) {}
                socket = null
                connected = false
            }
        }
    }

    fun disconnect() {
        connected = false
        workerJob?.cancel()
        workerJob = null
        try {
            socket?.close()
        } catch (ignored: Exception) {}
        socket = null
    }

    override suspend fun sendKey(keySym: Int) = withContext(Dispatchers.IO) {
        sendKeyDown(keySym)
        delay(15)
        sendKeyUp(keySym)
    }

    suspend fun sendText(text: String) = withContext(Dispatchers.IO) {
        for (ch in text) {
            when (ch) {
                '\n' -> sendKey(KeySyms.RETURN)
                '\t' -> sendKey(KeySyms.TAB)
                '\b' -> sendKey(KeySyms.BACKSPACE)
                else -> {
                    val code = ch.code
                    if (code in 0x20..0x7E) {
                        sendKey(code)
                    }
                }
            }
            delay(15)
        }
    }
}

/**
 * Translates touch coordinates within a letterboxed Compose View into 1280x720 framebuffer pixels.
 */
private fun mapTouchToFb(touchX: Float, touchY: Float, viewWidth: Int, viewHeight: Int): Pair<Int, Int>? {
    val fbW = 1280f
    val fbH = 720f
    val scale = minOf(viewWidth / fbW, viewHeight / fbH)
    val renderedW = fbW * scale
    val renderedH = fbH * scale
    val offsetX = (viewWidth - renderedW) / 2f
    val offsetY = (viewHeight - renderedH) / 2f

    val relX = touchX - offsetX
    val relY = touchY - offsetY

    if (relX < 0 || relX > renderedW || relY < 0 || relY > renderedH) {
        return null // Touched letterbox border
    }

    val fbX = ((relX / renderedW) * fbW).toInt().coerceIn(0, 1279)
    val fbY = ((relY / renderedH) * fbH).toInt().coerceIn(0, 719)
    return Pair(fbX, fbY)
}

@Composable
fun X11DisplayCard(
    rootfsDir: File,
    onLaunchNotepad: () -> Unit,
    onLaunchWinemine: () -> Unit,
    onLaunchCad: () -> Unit = {},
    onStartDisplay: () -> Unit,
    onKillWine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vncClient = remember { VncInputClient() }
    val profileManager = remember(context) { GestureProfileManager(context) }
    var activeProfile by remember { mutableStateOf(profileManager.getActiveProfile()) }
    val workflowExecutor = remember(vncClient) { WorkflowExecutor(vncClient) }

    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDisplayActive by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isRightClickMode by remember { mutableStateOf(false) }
    var isKeyboardOpen by remember { mutableStateOf(false) }
    var keyboardInputText by remember { mutableStateOf("") }
    var showGestureEditor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fbFile = remember(rootfsDir) { File(rootfsDir, "tmp/Xvfb_screen0") }

    DisposableEffect(Unit) {
        onDispose {
            vncClient.disconnect()
        }
    }

    // Live Framebuffer Reader Loop: double-buffered zero-allocation 60 FPS streamer
    LaunchedEffect(rootfsDir) {
        withContext(Dispatchers.IO) {
            val width = 1280
            val height = 720
            val pixelCount = width * height
            val headerSize = 3232L
            val byteCount = pixelCount * 4

            // Pre-allocated reusable buffers (zero allocations in loop)
            val rawBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.LITTLE_ENDIAN)
            val rawInts = IntArray(pixelCount)
            val prevInts = IntArray(pixelCount)
            val pixelInts = IntArray(pixelCount)

            // Double buffering Bitmaps to completely eliminate GC pressure
            val bmpA = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val bmpB = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            var useA = true

            var raf: RandomAccessFile? = null
            var channel: FileChannel? = null
            var mappedBuf: MappedByteBuffer? = null
            var lastLength = 0L

            try {
                while (isActive) {
                    if (fbFile.exists() && fbFile.length() >= headerSize + byteCount) {
                        isDisplayActive = true
                        val curLen = fbFile.length()
                        try {
                            if (raf == null || channel == null || !channel.isOpen || curLen != lastLength) {
                                try { channel?.close() } catch (ignored: Exception) {}
                                try { raf?.close() } catch (ignored: Exception) {}
                                raf = RandomAccessFile(fbFile, "r")
                                channel = raf.channel
                                lastLength = curLen
                                mappedBuf = try {
                                    channel.map(FileChannel.MapMode.READ_ONLY, headerSize, byteCount.toLong())
                                        .order(ByteOrder.LITTLE_ENDIAN) as? MappedByteBuffer
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (mappedBuf != null) {
                                mappedBuf.position(0)
                                val intBuf = mappedBuf.asIntBuffer()
                                intBuf.get(rawInts)
                            } else {
                                channel!!.position(headerSize)
                                rawBuffer.clear()
                                var bytesRead = 0
                                while (bytesRead < byteCount && channel!!.isOpen) {
                                    val r = channel!!.read(rawBuffer)
                                    if (r < 0) break
                                    bytesRead += r
                                }
                                if (bytesRead >= byteCount) {
                                    rawBuffer.flip()
                                    val intBuf = rawBuffer.asIntBuffer()
                                    intBuf.get(rawInts)
                                }
                            }

                            // Compare with previous frame - only convert & redraw if pixels changed
                            val changed = !Arrays.equals(rawInts, prevInts)
                            if (changed) {
                                // Fast native conversion from Xvfb 32-bit BGRX to Android ARGB
                                for (i in 0 until pixelCount) {
                                    val p = rawInts[i]
                                    pixelInts[i] = 0xFF000000.toInt() or
                                            ((p and 0xFF) shl 16) or
                                            (p and 0xFF00) or
                                            ((p ushr 16) and 0xFF)
                                }
                                System.arraycopy(rawInts, 0, prevInts, 0, pixelCount)

                                val targetBmp = if (useA) bmpA else bmpB
                                targetBmp.setPixels(pixelInts, 0, width, 0, 0, width, height)
                                displayBitmap = targetBmp
                                useA = !useA
                            }
                        } catch (e: Exception) {
                            try { channel?.close() } catch (ignored: Exception) {}
                            try { raf?.close() } catch (ignored: Exception) {}
                            channel = null
                            raf = null
                            mappedBuf = null
                        }
                        delay(16) // ~60 FPS smooth rendering
                    } else {
                        isDisplayActive = false
                        try { channel?.close() } catch (ignored: Exception) {}
                        try { raf?.close() } catch (ignored: Exception) {}
                        channel = null
                        raf = null
                        mappedBuf = null
                        delay(200) // Idle sleep waiting for display
                    }
                }
            } finally {
                try { channel?.close() } catch (ignored: Exception) {}
                try { raf?.close() } catch (ignored: Exception) {}
            }
        }
    }

    // Interactive Display View Composable routing recognized gestures
    @Composable
    fun InteractiveDisplaySurface(
        isFull: Boolean,
        modifier: Modifier = Modifier,
    ) {
        var surfaceSize by remember { mutableStateOf(IntSize(1280, 720)) }

        val gestureListener = remember(activeProfile, surfaceSize, isRightClickMode) {
            object : GestureListener {
                override fun onGestureRecognized(gesture: TouchGestureType, position: Offset) {
                    val coords = mapTouchToFb(position.x, position.y, surfaceSize.width, surfaceSize.height) ?: return
                    val config = if (isRightClickMode && gesture == TouchGestureType.SINGLE_TAP) {
                        GestureActionConfig(ActionType.MOUSE_CLICK, MouseButton.RIGHT)
                    } else {
                        activeProfile.mappings[gesture] ?: GestureActionConfig(ActionType.NONE)
                    }

                    scope.launch(Dispatchers.IO) {
                        workflowExecutor.executeAction(
                            config = config,
                            x = coords.first,
                            y = coords.second,
                            isTestMode = false,
                        )
                    }
                }

                override fun onDragUpdate(gesture: TouchGestureType, position: Offset, delta: Offset) {
                    val coords = mapTouchToFb(position.x, position.y, surfaceSize.width, surfaceSize.height) ?: return
                    scope.launch(Dispatchers.IO) {
                        workflowExecutor.updateDrag(coords.first, coords.second, isTestMode = false)
                    }
                }

                override fun onPinchZoom(isZoomIn: Boolean, center: Offset) {
                    val coords = mapTouchToFb(center.x, center.y, surfaceSize.width, surfaceSize.height) ?: return
                    scope.launch(Dispatchers.IO) {
                        workflowExecutor.executeWheel(
                            isZoomIn = isZoomIn,
                            x = coords.first,
                            y = coords.second,
                            isTestMode = false,
                        )
                    }
                }

                override fun onGestureEnd(gesture: TouchGestureType?) {
                    scope.launch(Dispatchers.IO) {
                        workflowExecutor.releaseAllHeld(isTestMode = false)
                    }
                }
            }
        }

        val surfaceModifier = if (isDisplayActive) {
            modifier
                .onSizeChanged { surfaceSize = it }
                .detectMoWinGestures(gestureListener)
        } else {
            modifier.onSizeChanged { surfaceSize = it }
        }

        Box(
            modifier = surfaceModifier,
            contentAlignment = Alignment.Center,
        ) {
            val bmp = displayBitmap
            if (bmp != null && isDisplayActive) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Windows Desktop",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Display server not started or waiting for window...",
                        color = Color(0xFF78909C),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onStartDisplay,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("Start Display Server", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Keyboard and Quick Keys Control Bar Composable
    @Composable
    fun KeyboardControlBar() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E222B), RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = keyboardInputText,
                    onValueChange = { keyboardInputText = it },
                    placeholder = { Text("Type text here...", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val textToSend = keyboardInputText
                            keyboardInputText = ""
                            scope.launch(Dispatchers.IO) {
                                vncClient.sendText(textToSend)
                            }
                        },
                    ),
                )
                Button(
                    onClick = {
                        val textToSend = keyboardInputText
                        keyboardInputText = ""
                        scope.launch(Dispatchers.IO) {
                            vncClient.sendText(textToSend)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Send", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(
                    onClick = { scope.launch(Dispatchers.IO) { vncClient.sendKey(KeySyms.RETURN) } },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("Enter ⏎", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = { scope.launch(Dispatchers.IO) { vncClient.sendKey(KeySyms.BACKSPACE) } },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("Bksp ⌫", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = { scope.launch(Dispatchers.IO) { vncClient.sendKey(KeySyms.SPACE) } },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("Space ␣", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = { scope.launch(Dispatchers.IO) { vncClient.sendKey(KeySyms.TAB) } },
                    modifier = Modifier.weight(0.8f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("Tab", fontSize = 11.sp)
                }
                FilledTonalButton(
                    onClick = { scope.launch(Dispatchers.IO) { vncClient.sendKey(KeySyms.ESCAPE) } },
                    modifier = Modifier.weight(0.8f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("Esc", fontSize = 11.sp)
                }
            }
        }
    }

    // Fullscreen Overlay Dialog
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                // Interactive Display Surface
                InteractiveDisplaySurface(
                    isFull = true,
                    modifier = Modifier.fillMaxSize(),
                )

                // Floating Top Control Bar with pinned Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 8.dp, end = 8.dp)
                        .background(Color(0xCC1A1F26), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Mode Toggle (Left vs Right Click)
                        FilterChip(
                            selected = isRightClickMode,
                            onClick = { isRightClickMode = !isRightClickMode },
                            label = {
                                Text(
                                    if (isRightClickMode) "🚩 Right" else "🖱 Left",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                        )

                        // Gestures Editor Button
                        FilledTonalButton(
                            onClick = { showGestureEditor = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("⚙ Gestures", fontSize = 11.sp)
                        }

                        // Keyboard Toggle
                        FilterChip(
                            selected = isKeyboardOpen,
                            onClick = { isKeyboardOpen = !isKeyboardOpen },
                            label = { Text("⌨ Keys", fontSize = 11.sp) },
                        )

                        // Quick App Switchers
                        FilledTonalButton(
                            onClick = onLaunchNotepad,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Notepad", fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = onLaunchWinemine,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Mines", fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = onLaunchCad,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        ) {
                            Text("📐 CAD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Quick CAD actions in fullscreen
                        FilledTonalButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    workflowExecutor.executeAction(
                                        GestureActionConfig(ActionType.KEY_SHORTCUT, shortcutId = "ESCAPE"),
                                        0,
                                        0,
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF37474F)),
                        ) {
                            Text("Esc", fontSize = 11.sp, color = Color.White)
                        }

                        FilledTonalButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    workflowExecutor.executeAction(
                                        GestureActionConfig(ActionType.KEY_SHORTCUT, shortcutId = "CTRL_Z"),
                                        0,
                                        0,
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF37474F)),
                        ) {
                            Text("↶ Undo", fontSize = 11.sp, color = Color.White)
                        }

                        FilledTonalButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    workflowExecutor.executeWheel(isZoomIn = true, x = 640, y = 360)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF263238)),
                        ) {
                            Text("🔍+", fontSize = 11.sp, color = Color.White)
                        }

                        FilledTonalButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    workflowExecutor.executeWheel(isZoomIn = false, x = 640, y = 360)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF263238)),
                        ) {
                            Text("🔍-", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Exit Fullscreen Button - Always pinned on the right
                    FilledTonalButton(
                        onClick = { isFullscreen = false },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE53935)),
                    ) {
                        Text("✕", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Floating Bottom Keyboard
                if (isKeyboardOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .align(Alignment.BottomCenter),
                    ) {
                        KeyboardControlBar()
                    }
                }
            }
        }
    }

    // Normal Card Mode
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "WINDOWS DISPLAY (1280x720)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Profile: ${activeProfile.name}",
                        fontSize = 11.sp,
                        color = Color(0xFF90A4AE),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isDisplayActive) Color(0xFF00E676) else Color(0xFFFF5252),
                                shape = RoundedCornerShape(4.dp),
                            ),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDisplayActive) "ACTIVE" else "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDisplayActive) Color(0xFF00E676) else Color(0xFFFF5252),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // Fullscreen Toggle Button
                    OutlinedButton(
                        onClick = { isFullscreen = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("⛶", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Desktop Surface Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0E14))
                    .border(1.dp, Color(0xFF2E3440), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                InteractiveDisplaySurface(
                    isFull = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input Control Bar (Mouse Mode + Keyboard Toggle + Gesture Profile Config)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isRightClickMode,
                        onClick = { isRightClickMode = !isRightClickMode },
                        label = {
                            Text(
                                if (isRightClickMode) "🚩 Right" else "🖱 Left",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                    )
                    FilterChip(
                        selected = isKeyboardOpen,
                        onClick = { isKeyboardOpen = !isKeyboardOpen },
                        label = { Text("⌨ Keys", fontSize = 11.sp) },
                    )
                    FilledTonalButton(
                        onClick = { showGestureEditor = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("⚙ Gestures", fontSize = 11.sp)
                    }
                }
                Text(
                    "2-Finger: ${activeProfile.mappings[TouchGestureType.TWO_FINGER_TAP]?.displayLabel ?: "Custom"}",
                    fontSize = 11.sp,
                    color = Color(0xFF90A4AE),
                )
            }

            // Keyboard input field if open
            if (isKeyboardOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                KeyboardControlBar()
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Text(
                "QUICK LAUNCH",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF78909C),
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    onClick = onLaunchNotepad,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text("Notepad", fontSize = 11.sp, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onLaunchWinemine,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Text("Mines", fontSize = 11.sp, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onLaunchCad,
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text("📐 CAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onKillWine,
                    modifier = Modifier.weight(0.8f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                ) {
                    Text("Kill", fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }

    // Gesture Editor Dialog
    if (showGestureEditor) {
        GestureEditorDialog(
            profileManager = profileManager,
            onDismiss = { showGestureEditor = false },
            onProfileChanged = { updated ->
                activeProfile = updated
            }
        )
    }
}
