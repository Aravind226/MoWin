package dev.mowin.app.runtime

/**
 * The H4 PRoot launcher argv — written once, reused everywhere.
 *
 * Must include --kill-on-exit and the bind mounts (dev/proc/sys, shmem,
 * host Downloads, workdir /root). Env sets HOME/PATH/TERM/LANG and
 * PROOT_NO_SECCOMP=1.
 */
object ProotLauncher {

    fun hostEnv(nativeLibDir: String, tmpDir: String): Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to nativeLibDir,
        "PROOT_LOADER" to "$nativeLibDir/libproot-loader.so",
        "PROOT_TMP_DIR" to tmpDir,
        "PROOT_NO_SECCOMP" to "1",
    )

    fun argv(
        prootPath: String,
        rootfsPath: String,
        nativeLibDir: String,
        hostDownloadPath: String,
    ): List<String> = buildCommand(
        prootPath = prootPath,
        rootfsPath = rootfsPath,
        nativeLibDir = nativeLibDir,
        hostDownloadPath = hostDownloadPath,
        command = listOf("/bin/bash", "-l"),
    )

    fun buildCommand(
        prootPath: String,
        rootfsPath: String,
        nativeLibDir: String,
        hostDownloadPath: String,
        command: List<String>,
    ): List<String> = listOf(
        prootPath,
        "--link2symlink", "-0",
        "-r", rootfsPath,
        "-b", "/dev", "-b", "/proc", "-b", "/sys",
        "-b", "$rootfsPath/tmp:/dev/shm",
        "-b", "$hostDownloadPath:/mnt/host",
        "-w", "/root",
        "/usr/bin/env", "-i",
        "HOME=/root",
        "PATH=/opt/hangover/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin",
        "TERM=xterm-256color",
        "LANG=C.UTF-8",
        "DISPLAY=:0",
        "WINEDLLPATH=/opt/hangover/lib/wine",
        "WINESERVER=/opt/hangover/bin/wineserver",
        "LD_LIBRARY_PATH=/opt/hangover/lib/wine/aarch64-unix:/usr/local/lib:/usr/lib",
        "VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json",
        "WINEDEBUG=-all",
    ) + command
}
