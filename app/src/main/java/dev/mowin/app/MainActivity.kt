package dev.mowin.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import dev.mowin.app.runtime.DeviceInfo
import dev.mowin.app.runtime.RuntimeCleanupService
import dev.mowin.app.runtime.RuntimePaths
import dev.mowin.app.runtime.RuntimeProcessManager
import dev.mowin.app.runtime.RuntimeState
import dev.mowin.app.ui.MoWinTheme
import dev.mowin.app.ui.RuntimeScreen

class MainActivity : ComponentActivity() {

    private val cleanupConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind and start background process monitoring and cleanup service
        try {
            val serviceIntent = Intent(this, RuntimeCleanupService::class.java)
            startService(serviceIntent)
            bindService(serviceIntent, cleanupConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            // Android 8+ background start limit if any
        }

        // On Back press at root activity, finish and clean up immediately
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }

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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(cleanupConnection)
        } catch (ignored: Exception) {}
        RuntimeProcessManager.killAllRuntimeProcesses(applicationContext)
    }
}
