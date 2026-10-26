package dev.mowin.app.runtime

/**
 * The H4 PRoot launcher argv — written once, reused everywhere.
 *
 * Must include --kill-on-exit and the bind mounts (dev/proc/sys, shmem,
 * host Downloads, workdir /root). Env sets HOME/PATH/TERM/LANG and
 * PROOT_NO_SECCOMP=1.
 */
object ProotLauncher {

    fun argv(
        prootPath: String,
        rootfsPath: String,
        nativeLibDir: String,
        hostDownloadPath: String,
    ): List<String> = listOf(
        prootPath,
        "--kill-on-exit", "--link2symlink", "-0",
        "-r", rootfsPath,
        "-b", "/dev", "-b", "/proc", "-b", "/sys",
        "-b", "$rootfsPath/tmp:/dev/shm",
        "-b", "$hostDownloadPath:/mnt/host",
        "-w", "/root",
        "/usr/bin/env", "-i",
        "HOME=/root",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin",
        "TERM=xterm-256color",
        "LANG=C.UTF-8",
        "PROOT_NO_SECCOMP=1",
        "LD_LIBRARY_PATH=$nativeLibDir",
        "/bin/bash", "-l",
    )
}
