package dev.mowin.app.ui

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mowin.app.runtime.DeviceSnapshot
import dev.mowin.app.runtime.ProcessRunner
import dev.mowin.app.runtime.ProotLauncher
import dev.mowin.app.runtime.RuntimePaths
import dev.mowin.app.runtime.RuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private suspend fun executeCommand(
    context: Context,
    paths: RuntimePaths?,
    runner: ProcessRunner,
    command: String,
    onLine: (String) -> Unit,
    onFinish: (Int) -> Unit,
) {
    val nativeLibDir = context.applicationInfo.nativeLibraryDir
    val prootBin = File(nativeLibDir, "libproot.so")
    val hasRootfs = paths != null && paths.isInitialized()

    val (argv, env) = if (hasRootfs && prootBin.exists()) {
        val downloadPath = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS,
        ).absolutePath
        val prArgv = ProotLauncher.buildCommand(
            prootPath = prootBin.absolutePath,
            rootfsPath = paths!!.rootfs.absolutePath,
            nativeLibDir = nativeLibDir,
            hostDownloadPath = downloadPath,
            command = listOf("/bin/bash", "-c", command),
        )
        val prEnv = ProotLauncher.hostEnv(nativeLibDir, paths.tmp.absolutePath)
        prArgv to prEnv
    } else {
        listOf("/bin/sh", "-c", command) to emptyMap()
    }

    val exit = runner.run(
        argv = argv,
        env = env,
        onLine = onLine,
    )
    onFinish(exit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeScreen(
    snapshot: DeviceSnapshot,
    state: RuntimeState,
    paths: RuntimePaths? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runner = remember { ProcessRunner() }
    var cmd by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf("$ mowin — interactive (type + tap Run)\n") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("MoWin", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(snapshot = snapshot, state = state)

            // Embedded Live Windows Display Surface
            if (paths != null && paths.isInitialized()) {
                X11DisplayCard(
                    rootfsDir = paths.rootfs,
                    onStartDisplay = {
                        scope.launch(Dispatchers.IO) {
                            scope.launch(Dispatchers.Main) { out += "> mowin-display\n" }
                            executeCommand(
                                context = context,
                                paths = paths,
                                runner = runner,
                                command = "/usr/local/bin/mowin-display",
                                onLine = { line -> scope.launch(Dispatchers.Main) { out += "$line\n" } },
                                onFinish = { exit -> scope.launch(Dispatchers.Main) { out += "display exit=$exit\n" } },
                            )
                        }
                    },
                    onLaunchNotepad = {
                        scope.launch(Dispatchers.IO) {
                            scope.launch(Dispatchers.Main) { out += "> wine notepad.exe (Winlator Desktop)\n" }
                            executeCommand(
                                context = context,
                                paths = paths,
                                runner = runner,
                                command = "/usr/local/bin/mowin-display; killall -9 notepad.exe winemine.exe 2>/dev/null || true; wine explorer.exe /desktop=shell,1280x720 notepad.exe",
                                onLine = { line -> scope.launch(Dispatchers.Main) { out += "$line\n" } },
                                onFinish = { exit -> scope.launch(Dispatchers.Main) { out += "notepad exit=$exit\n" } },
                            )
                        }
                    },
                    onLaunchWinemine = {
                        scope.launch(Dispatchers.IO) {
                            scope.launch(Dispatchers.Main) { out += "> wine winemine.exe (Winlator Desktop)\n" }
                            executeCommand(
                                context = context,
                                paths = paths,
                                runner = runner,
                                command = "/usr/local/bin/mowin-display; killall -9 notepad.exe winemine.exe 2>/dev/null || true; wine explorer.exe /desktop=shell,1280x720 winemine.exe",
                                onLine = { line -> scope.launch(Dispatchers.Main) { out += "$line\n" } },
                                onFinish = { exit -> scope.launch(Dispatchers.Main) { out += "winemine exit=$exit\n" } },
                            )
                        }
                    },
                    onLaunchCad = {
                        scope.launch(Dispatchers.IO) {
                            scope.launch(Dispatchers.Main) { out += "> wine SolveSpace CAD (2D/3D Parametric)\n" }
                            executeCommand(
                                context = context,
                                paths = paths,
                                runner = runner,
                                command = "/usr/local/bin/mowin-cad",
                                onLine = { line -> scope.launch(Dispatchers.Main) { out += "$line\n" } },
                                onFinish = { exit -> scope.launch(Dispatchers.Main) { out += "cad exit=$exit\n" } },
                            )
                        }
                    },
                    onLaunchSteam = {
                        scope.launch(Dispatchers.IO) {
                            scope.launch(Dispatchers.Main) { out += "> wine Steam (Installer / Client)\n" }
                            executeCommand(
                                context = context,
                                paths = paths,
                                runner = runner,
                                command = "/usr/local/bin/mowin-steam",
                                onLine = { line -> scope.launch(Dispatchers.Main) { out += "$line\n" } },
                                onFinish = { exit -> scope.launch(Dispatchers.Main) { out += "steam exit=$exit\n" } },
                            )
                        }
                    },
                    onKillWine = {
                        scope.launch(Dispatchers.IO) {
                            scope.launch(Dispatchers.Main) { out += "> Terminating Wine, CAD, Steam, and X11 processes...\n" }
                            dev.mowin.app.runtime.RuntimeProcessManager.killAllRuntimeProcesses(context)
                            executeCommand(
                                context = context,
                                paths = paths,
                                runner = runner,
                                command = "killall -9 wineserver notepad.exe winemine.exe explorer.exe solvespace.exe solvespace_sc.exe steam.exe steamwebhelper.exe SteamSetup.exe Xvfb x11vnc 2>/dev/null || true",
                                onLine = { line -> scope.launch(Dispatchers.Main) { out += "$line\n" } },
                                onFinish = { exit -> scope.launch(Dispatchers.Main) { out += "kill exit=$exit\n" } },
                            )
                        }
                    },
                )
            }

            // Terminal Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "=== TERMINAL OUTPUT ===",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        out,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(Color(0xFF0A0E14))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = cmd,
                            onValueChange = { cmd = it },
                            label = { Text("cmd") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !isRunning,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = !isRunning && cmd.isNotBlank(),
                            onClick = {
                                val currentCmd = cmd.trim()
                                if (currentCmd.isNotBlank()) {
                                    cmd = ""
                                    isRunning = true
                                    out += "> $currentCmd\n"
                                    scope.launch(Dispatchers.IO) {
                                        executeCommand(
                                            context = context,
                                            paths = paths,
                                            runner = runner,
                                            command = currentCmd,
                                            onLine = { line ->
                                                scope.launch(Dispatchers.Main) {
                                                    out += "$line\n"
                                                }
                                            },
                                            onFinish = { exit ->
                                                scope.launch(Dispatchers.Main) {
                                                    out += "exit=$exit\n"
                                                    isRunning = false
                                                }
                                            },
                                        )
                                    }
                                }
                            },
                        ) {
                            Text(if (isRunning) "..." else "Run")
                        }
                    }
                }
            }
        }
    }
}
