package dev.mowin.app.runtime

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.utils.IOUtils

object RootfsImporter {
    fun importFromUri(context: Context, uri: Uri, destDir: File, stagingDir: File): Boolean {
        if (!destDir.exists()) destDir.mkdirs()
        if (!stagingDir.exists()) stagingDir.mkdirs()
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return false
            val tar = TarArchiveInputStream(GZIPInputStream(stream))
            var entry = tar.nextEntry
            while (entry != null) {
                val safeName = entry.name.replace("..", "").trim('/')
                if (safeName.isEmpty() || safeName.contains("/../") || safeName == "/") {
                    entry = tar.nextEntry
                    continue
                }
                val target = File(stagingDir, safeName)
                val parent = target.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                if (entry.isDirectory) {
                    target.mkdirs()
                } else if (entry.isSymbolicLink || entry.linkName != null) {
                    // Skip symlinks for safety; rootfs has them handled by extraction logic
                    entry = tar.nextEntry
                    continue
                } else {
                    FileOutputStream(target).use { out ->
                        IOUtils.copy(tar, out)
                    }
                }
                entry = tar.nextEntry
            }
            tar.close()
            // Validate staging
            val marker = File(stagingDir, ".mowin-init-complete")
            val pacman = File(stagingDir, "usr/bin/pacman")
            return marker.exists() || pacman.exists()
        } catch (e: Exception) {
            return false
        }
    }
}
