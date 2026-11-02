package com.pocodebloat

import android.content.Context
import android.content.pm.PackageManager

class PackageRepo(private val ctx: Context) {

    private fun IShellService.listPackages(args: String): Set<String> =
        run("pm list packages $args --user 0").output
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { Cmd.isValid(it) }
            .toSet()

    @Suppress("DEPRECATION")
    private fun labelOf(pkg: String): String = try {
        val pm = ctx.packageManager
        val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        pm.getApplicationLabel(info).toString()
    } catch (e: Exception) {
        pkg
    }

    /** Memuat semua paket sistem beserta statusnya (terpasang / nonaktif / dihapus untuk user 0). */
    fun load(
        shell: IShellService,
        presets: Map<String, Preset>,
        history: Set<String>
    ): List<AppItem> {
        val installed = shell.listPackages("")
        val disabled = shell.listPackages("-d")
        val systemAll = shell.listPackages("-s -u")

        return systemAll.map { pkg ->
            val state = when {
                pkg !in installed -> AppState.REMOVED
                pkg in disabled -> AppState.DISABLED
                else -> AppState.INSTALLED
            }
            val preset = Presets.classify(pkg, presets)
            AppItem(
                pkg = pkg,
                label = labelOf(pkg),
                level = preset.level,
                note = preset.note,
                state = state,
                removedByApp = pkg in history
            )
        }.sortedBy { it.label.lowercase() }
    }
}
