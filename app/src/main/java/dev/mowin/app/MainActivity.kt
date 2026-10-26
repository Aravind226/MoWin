package dev.mowin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import dev.mowin.app.runtime.DeviceInfo
import dev.mowin.app.runtime.RuntimePaths
import dev.mowin.app.runtime.RuntimeState
import dev.mowin.app.ui.MoWinTheme
import dev.mowin.app.ui.RuntimeScreen

import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paths = RuntimePaths(applicationContext)
        val layoutError = runCatching { paths.ensureLayout() }.exceptionOrNull()

        // Initialize rootfs marker so RuntimeScreen shows Ready
        runCatching {
            paths.rootfs.mkdirs()
            File(paths.rootfs, RuntimePaths.MARKER).writeText("v0.1-poc\n")
        }

        setContent {
            MoWinTheme {
                val snapshot = remember { DeviceInfo.snapshot(applicationContext) }
                val state = remember {
                    when {
                        layoutError != null -> RuntimeState.Failed
                        paths.isInitialized() -> RuntimeState.Ready
                        else -> RuntimeState.NotInitialized
                    }
                }
                RuntimeScreen(snapshot = snapshot, state = state)
            }
        }
    }
}
