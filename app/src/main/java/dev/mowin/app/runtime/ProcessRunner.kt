package dev.mowin.app.runtime

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * The only ProcessBuilder call site in MoWin.
 *
 * Launch PRoot with --kill-on-exit so children die with it; read stdout/stderr
 * through one thread; propagate exit code; support cancellation.
 */
class ProcessRunner {

    private var process: Process? = null

    fun run(
        argv: List<String>,
        cwd: File? = null,
        env: Map<String, String> = emptyMap(),
        onLine: (String) -> Unit = {},
    ): Int {
        process?.destroy()
        val pb = ProcessBuilder(argv)
        pb.redirectErrorStream(true)
        pb.directory(cwd)
        pb.environment().putAll(env)
        process = pb.start()

        val reader = BufferedReader(InputStreamReader(process!!.inputStream))
        Thread {
            try {
                reader.lineSequence().forEach(onLine)
            } catch (e: Throwable) {
                // Stream closed on process exit or destroy
            }
        }.start()

        val exit = process!!.waitFor()
        process = null
        return exit
    }

    fun cancel() {
        val p = process ?: return
        p.destroy()
        Thread {
            try {
                Thread.sleep(2000)
                if (p.isAlive) p.destroyForcibly()
            } catch (e: Throwable) {
            }
        }.start()
    }
}
