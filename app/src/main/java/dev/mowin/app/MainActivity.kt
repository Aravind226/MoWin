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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paths = RuntimePaths(applicationContext)
        val layoutError = runCatching { paths.ensureLayout() }.exceptionOrNull()

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
                RuntimeScreen(snapshot = snapshot, state = state, paths = paths)
            }
        }
    }
}
