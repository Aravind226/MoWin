package dev.mowin.app.runtime

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.util.Locale

data class DeviceSnapshot(
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val abis: List<String>,
    val freeBytes: Long,
    val totalBytes: Long,
    val memoryClassMb: Int,
) {
    val abiLabel: String get() = abis.joinToString(", ")
    val freeLabel: String get() = formatBytes(freeBytes)
    val totalLabel: String get() = formatBytes(totalBytes)
}

object DeviceInfo {

    fun snapshot(context: Context): DeviceSnapshot {
        val statFs = StatFs(context.filesDir.absolutePath)
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        return DeviceSnapshot(
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            freeBytes = statFs.availableBytes,
            totalBytes = statFs.totalBytes,
            memoryClassMb = activityManager.memoryClass,
        )
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
