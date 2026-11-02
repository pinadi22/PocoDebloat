package com.pocodebloat

import android.content.Context
import org.json.JSONObject

data class Preset(val level: Level, val note: String)

object Presets {

    /** Paket dengan awalan ini selalu dianggap berbahaya untuk dihapus. */
    private val dangerPrefixes = listOf(
        "com.android.",
        "com.google.android.overlay",
        "com.google.android.trichromelibrary",
        "com.google.android.networkstack",
        "com.google.android.modulemetadata",
        "com.qualcomm.",
        "com.qti.",
        "org.codeaurora.",
        "vendor.",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.lbe.security"
    )

    fun load(context: Context): Map<String, Preset> {
        val text = context.assets.open("presets.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("packages")
        val map = HashMap<String, Preset>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val level = runCatching { Level.valueOf(o.getString("level")) }.getOrDefault(Level.UNKNOWN)
            map[o.getString("id")] = Preset(level, o.optString("note", ""))
        }
        return map
    }

    fun classify(pkg: String, presets: Map<String, Preset>): Preset {
        presets[pkg]?.let { return it }
        if (pkg == "android" || dangerPrefixes.any { pkg.startsWith(it) }) {
            return Preset(Level.DANGER, "Komponen inti sistem")
        }
        return Preset(Level.UNKNOWN, "Belum ada di daftar preset, cari tahu dulu sebelum menghapus")
    }
}
