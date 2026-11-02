package com.pocodebloat

import kotlin.system.exitProcess

/**
 * Berjalan di proses terpisah dengan hak akses shell (uid 2000) yang disediakan Shizuku.
 * Class ini harus punya constructor tanpa argumen.
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(cmd: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            "$code\n$output"
        } catch (e: Exception) {
            "-1\n${e.message}"
        }
    }
}
