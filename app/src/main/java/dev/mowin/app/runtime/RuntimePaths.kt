package dev.mowin.app.runtime

import android.content.Context
import java.io.File

/**
 * Single source of truth for every path the runtime touches.
 *
 * Nothing else in the app may invent paths. The rootfs is only ever created by
 * the importer (extract to [staging], then atomically rename to [rootfs]).
 */
class RuntimePaths(context: Context) {

    val root: File = File(context.filesDir, "runtime")
    val rootfs: File = File(root, "rootfs")
    val staging: File = File(root, "rootfs.staging")
    val home: File = File(root, "root")
    val tmp: File = File(root, "tmp")
    val logs: File = File(root, "logs")
    val bin: File = File(root, "bin")

    private val directories = listOf(root, home, tmp, logs, bin)

    fun ensureLayout() {
        directories.forEach { dir ->
            if (!dir.isDirectory && !dir.mkdirs()) {
                error("Cannot create ${dir.absolutePath}")
            }
        }
    }

    fun isInitialized(): Boolean = File(rootfs, MARKER).isFile || File(rootfs, "usr/bin/pacman").isFile

    companion object {
        const val MARKER = ".mowin-init-complete"
    }
}
