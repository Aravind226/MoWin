package dev.mowin.app.runtime
import java.io.OutputStream
import java.io.OutputStreamWriter

class ProcessWriter(private val stream: OutputStream) {
    private val writer = OutputStreamWriter(stream)
    fun write(cmd: String) {
        writer.write(cmd + "\n")
        writer.flush()
    }
}
