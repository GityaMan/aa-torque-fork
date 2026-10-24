package com.aatorque.obd

import android.content.SharedPreferences

/** Запись о прожиге: когда, какая была сажа перед ним (г/л) и пробег между прожигами (км). */
data class RegenEntry(val time: Long, val soot: Double, val km: Double?)

/**
 * Журнал прожигов. Одна копия на процесс (служба и экраны читают одно и то же).
 * 1) Интервал между прожигами = максимальный «пробег после прожига» к моменту, когда счётчик вырос.
 * 2) Экспериментально: максимум накопления сажи перед прожигом (со сглаживанием по трём замерам).
 */
class RegenTracker private constructor(private val prefs: SharedPreferences) {
    private var lastCount: Int? = prefs.getInt("rg_count", -1).takeIf { it >= 0 }
    private var peak = prefs.getFloat("rg_peak", 0f).toDouble()
    private var lastSeen = prefs.getFloat("rg_seen", 0f).toDouble()
    private var awaitReset = false
    private val intervals = ArrayList<Double>().apply {
        prefs.getString("rg_hist", "")!!.split(",").filter { it.isNotBlank() }.mapTo(this) { it.toDouble() }
    }

    // --- экспериментальный сбор данных по саже ---
    private var sootPeak = prefs.getFloat("rg_soot_peak", 0f).toDouble()
    private var lastSoot: Double? = null
    private var awaitSootDrop = false
    private var recordedSoot = 0.0
    private val recent = ArrayList<Double>()
    private val log = ArrayList<RegenEntry>().apply { addAll(parseLog(prefs.getString("rg_soot_log", "") ?: "")) }

    private fun sootLogEnabled() = prefs.getBoolean("exp_soot_log", true)

    @Synchronized fun onDist(d: Double) {
        lastSeen = d
        if (awaitReset) {
            if (d < 0.5 * (intervals.lastOrNull() ?: 0.0)) { awaitReset = false; peak = d }
        } else if (d > peak) peak = d
        save()
    }

    @Synchronized fun onSoot(v: Double) {
        if (!sootLogEnabled()) return
        if (v.isNaN() || v < 0 || v > 30) return
        lastSoot = v
        recent.add(v)
        if (recent.size > 3) recent.removeAt(0)
        val smooth = median(recent)
        if (awaitSootDrop) {
            if (smooth < 0.5 * recordedSoot) { awaitSootDrop = false; sootPeak = smooth }
        } else if (smooth > sootPeak) sootPeak = smooth
        save()
    }

    @Synchronized fun onCount(c: Int) {
        val prev = lastCount
        lastCount = c
        if (prev != null && c > prev) {
            var km: Double? = null
            if (peak > 1.0) {
                intervals.add(peak)
                while (intervals.size > 50) intervals.removeAt(0)
                km = peak
                awaitReset = lastSeen > 0.5 * peak
                peak = if (awaitReset) 0.0 else lastSeen
            }
            if (sootLogEnabled() && sootPeak > 0.3) {
                log.add(RegenEntry(System.currentTimeMillis(), sootPeak, km))
                while (log.size > 30) log.removeAt(0)
                recordedSoot = sootPeak
                val now = lastSoot ?: 0.0
                awaitSootDrop = now > 0.5 * recordedSoot
                sootPeak = if (awaitSootDrop) 0.0 else now
            }
        }
        save()
    }

    @Synchronized fun lastInterval(): Double? = intervals.lastOrNull()
    @Synchronized fun avgInterval(n: Int): Double? = intervals.takeLast(n).takeIf { it.isNotEmpty() }?.average()

    @Synchronized fun lastSootPeak(): Double? = log.lastOrNull()?.soot
    @Synchronized fun currentSootPeak(): Double? = sootPeak.takeIf { it > 0 }
    @Synchronized fun currentSoot(): Double? = lastSoot
    @Synchronized fun avgSootPeak(n: Int): Double? = log.takeLast(n).map { it.soot }.takeIf { it.isNotEmpty() }?.average()
    @Synchronized fun history(): List<RegenEntry> = ArrayList(log)
    @Synchronized fun clearSootLog() {
        log.clear(); sootPeak = 0.0; awaitSootDrop = false; recent.clear()
        save()
    }

    private fun median(l: List<Double>): Double {
        val s = l.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun parseLog(text: String): List<RegenEntry> =
        text.split(";").filter { it.isNotBlank() }.mapNotNull { rec ->
            val p = rec.split("|")
            val t = p.getOrNull(0)?.toLongOrNull()
            val s = p.getOrNull(1)?.toDoubleOrNull()
            if (t == null || s == null) null else RegenEntry(t, s, p.getOrNull(2)?.toDoubleOrNull())
        }

    private fun save() {
        prefs.edit()
            .putInt("rg_count", lastCount ?: -1)
            .putFloat("rg_peak", peak.toFloat())
            .putFloat("rg_seen", lastSeen.toFloat())
            .putString("rg_hist", intervals.joinToString(","))
            .putFloat("rg_soot_peak", sootPeak.toFloat())
            .putString("rg_soot_log", log.joinToString(";") { "${it.time}|${it.soot}|${it.km ?: ""}" })
            .apply()
    }

    companion object {
        @Volatile private var inst: RegenTracker? = null
        fun get(prefs: SharedPreferences): RegenTracker =
            inst ?: synchronized(this) { inst ?: RegenTracker(prefs).also { inst = it } }
    }
}
