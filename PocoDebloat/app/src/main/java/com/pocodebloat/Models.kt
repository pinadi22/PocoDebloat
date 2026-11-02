package com.pocodebloat

enum class Level(val label: String) {
    SAFE("Aman"),
    CAUTION("Hati-hati"),
    DANGER("Jangan dihapus"),
    UNKNOWN("Tidak dikenal")
}

enum class AppState { INSTALLED, DISABLED, REMOVED }

enum class Method(val label: String) {
    UNINSTALL("Hapus"),
    DISABLE("Nonaktifkan")
}

enum class ShizukuStatus { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, CONNECTING, READY }

data class AppItem(
    val pkg: String,
    val label: String,
    val level: Level,
    val note: String,
    val state: AppState,
    val removedByApp: Boolean
)

data class ExecResult(val code: Int, val output: String) {
    val ok: Boolean get() = code == 0 && !output.contains("Failure", ignoreCase = true)
}

/** Menjalankan perintah lewat layanan shell Shizuku dan mem-parse hasilnya. */
fun IShellService.run(cmd: String): ExecResult {
    val raw = exec(cmd)
    val idx = raw.indexOf('\n')
    if (idx < 0) return ExecResult(-1, raw)
    val code = raw.substring(0, idx).trim().toIntOrNull() ?: -1
    return ExecResult(code, raw.substring(idx + 1).trim())
}

/** Semua perintah dibuat di sini, dan nama paket divalidasi agar tidak ada injeksi shell. */
object Cmd {
    private val valid = Regex("^[A-Za-z0-9_.]+$")

    fun isValid(pkg: String) = valid.matches(pkg)

    fun uninstall(pkg: String) = "pm uninstall -k --user 0 $pkg"
    fun disable(pkg: String) = "pm disable-user --user 0 $pkg"
    fun reinstall(pkg: String) = "cmd package install-existing --user 0 $pkg"
    fun enable(pkg: String) = "pm enable --user 0 $pkg"
}
