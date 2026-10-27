package dev.mowin.app.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Service that monitors task lifecycle and terminates background processes
 * (Wine, CAD, X11, PRoot) when MoWin is closed or swiped away from Recents.
 */
class RuntimeCleanupService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i("RuntimeCleanupService", "MoWin task removed from recents - terminating all background processes")
        RuntimeProcessManager.killAllRuntimeProcesses(applicationContext)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        RuntimeProcessManager.killAllRuntimeProcesses(applicationContext)
    }
}
