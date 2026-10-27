package dev.mowin.app.runtime

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File

/**
 * Manages runtime process lifecycle, ensuring clean shutdown of all background Wine,
 * X11, PRoot, and CAD processes when the application is closed or reset.
 */
object RuntimeProcessManager {

    private const val TAG = "RuntimeProcessManager"

    private val PROCESS_TARGETS = listOf(
        "solvespace.exe",
        "solvespace_sc.exe",
        "solvespace_x86.exe",
        "notepad.exe",
        "winemine.exe",
        "steam.exe",
        "steamwebhelper.exe",
        "SteamSetup.exe",
        "explorer.exe",
        "wineserver",
        "wine",
        "wine64",
        "wineboot.exe",
        "services.exe",
        "winedevice.exe",
        "plugplay.exe",
        "start.exe",
        "svchost.exe",
        "rpcss.exe",
        "conhost.exe",
        "Xvfb",
        "x11vnc",
        "proot",
        "box64",
    )

    /**
     * Terminate all runtime and desktop processes spawned under this app UID.
     */
    fun killAllRuntimeProcesses(context: Context? = null) {
        val myPid = Process.myPid()
        val myUid = Process.myUid()

        // 1. Direct killall & pkill targeting Wine, X11, CAD, and emulation daemons
        try {
            val targets = PROCESS_TARGETS.joinToString(" ")
            val cmd = "killall -9 $targets 2>/dev/null; pkill -9 -f \"solvespace|notepad|winemine|steam|SteamSetup|wineserver|wine64|wineboot|services\\.exe|winedevice\\.exe|explorer\\.exe|Xvfb|x11vnc|proot|box64\" 2>/dev/null || true"
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
            proc.waitFor()
            Log.i(TAG, "killall and pkill executed for runtime processes.")
        } catch (e: Exception) {
            Log.w(TAG, "Error executing killall/pkill", e)
        }

        // 2. Iterate /proc to kill any remaining child processes under our UID
        try {
            val procDir = File("/proc")
            procDir.listFiles()?.forEach { file ->
                val pid = file.name.toIntOrNull()
                if (pid != null && pid != myPid && pid > 1) {
                    try {
                        val statusFile = File(file, "status")
                        if (statusFile.exists()) {
                            val uidLine = statusFile.bufferedReader().useLines { lines ->
                                lines.firstOrNull { it.startsWith("Uid:") }
                            }
                            if (uidLine != null) {
                                val uids = uidLine.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                if (uids.size > 1 && uids[1].toIntOrNull() == myUid) {
                                    Log.d(TAG, "Killing residual UID process PID=$pid")
                                    Process.sendSignal(pid, Process.SIGNAL_KILL)
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error iterating /proc for UID cleanup", e)
        }

        // 3. Clean up stale lock and socket files
        try {
            context?.let { ctx ->
                val tmpDir = File(ctx.filesDir, "runtime/rootfs/tmp")
                if (tmpDir.exists()) {
                    tmpDir.listFiles { _, name ->
                        name.startsWith(".X") ||
                                name.startsWith(".l2s") ||
                                name.startsWith("Xvfb_screen")
                    }?.forEach { file ->
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning stale lock files", e)
        }
    }
}
