package dev.mowin.app.runtime

import java.io.File
import java.time.Instant

class LogStore(private val logDir: File) {
    init { if (!logDir.exists()) logDir.mkdirs() }

    fun sessionLog(name: String = "session-${Instant.now().epochSecond}"): File {
        val f = File(logDir, "$name.log")
        f.createNewFile()
        return f
    }

    fun append(file: File, line: String) {
        file.appendText(line + "\n")
    }
}
