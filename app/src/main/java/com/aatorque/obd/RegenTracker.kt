package com.aatorque.obd

import android.content.SharedPreferences

/**
 * Журнал интервалов между прожигами. Интервал = максимальный «пробег после прожига»,
 * накопленный к моменту, когда счётчик регенераций вырос.
 */
class RegenTracker(private val prefs: SharedPreferences) {
    private var lastCount: Int? = prefs.getInt("rg_count", -1).takeIf { it >= 0 }
    private var peak = prefs.getFloat("rg_peak", 0f).toDouble()
    private var lastSeen = prefs.getFloat("rg_seen", 0f).toDouble()
    private var awaitReset = false
    private val intervals = ArrayList<Double>().apply {
        prefs.getString("rg_hist", "")!!.split(",").filter { it.isNotBlank() }.mapTo(this) { it.toDouble() }
    }

    @Synchronized fun onDist(d: Double) {
        lastSeen = d
        if (awaitReset) {
            // ждём, пока пробег после прожига обнулится
            if (d < 0.5 * (intervals.lastOrNull() ?: 0.0)) { awaitReset = false; peak = d }
        } else if (d > peak) peak = d
        save()
    }

    @Synchronized fun onCount(c: Int) {
        val prev = lastCount
        lastCount = c
        if (prev != null && c > prev && peak > 1.0) {
            intervals.add(peak)
            while (intervals.size > 50) intervals.removeAt(0)
            awaitReset = lastSeen > 0.5 * peak
            peak = if (awaitReset) 0.0 else lastSeen
        }
        save()
    }

    @Synchronized fun lastInterval(): Double? = intervals.lastOrNull()
    @Synchronized fun avgInterval(n: Int): Double? = intervals.takeLast(n).takeIf { it.isNotEmpty() }?.average()

    private fun save() {
        prefs.edit()
            .putInt("rg_count", lastCount ?: -1)
            .putFloat("rg_peak", peak.toFloat())
            .putFloat("rg_seen", lastSeen.toFloat())
            .putString("rg_hist", intervals.joinToString(","))
            .apply()
    }
}
