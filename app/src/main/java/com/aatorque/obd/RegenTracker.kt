package com.aatorque.obd

import android.content.SharedPreferences

/** Состояния записи о прожиге. */
object RegenState {
    const val RUNNING = 0      // прожиг начался (по температуре), ещё не завершён
    const val COMPLETE = 1     // после него вырос счётчик регенераций
    const val INTERRUPTED = 2  // счётчик не вырос: прожиг прерван или это был ложный нагрев
    const val BY_COUNTER = 3   // температура недоступна, запись сделана по росту счётчика
}

/** Запись о прожиге: когда включился, сажа перед ним (г/л), пробег после прошлого прожига (км), состояние, принудительный ли. */
data class RegenEntry(val time: Long, val soot: Double, val km: Double?, val state: Int, val forced: Boolean = false)

/**
 * Журнал прожигов. Одна копия на процесс (служба и экраны читают одно и то же).
 * Интервал между прожигами: максимальный «пробег после прожига» к моменту роста счётчика.
 * Экспериментально: запись сажи делается в момент ВКЛЮЧЕНИЯ прожига (температура перед сажевым
 * держится выше порога), по максимуму сажи, накопленному до этого момента.
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
    private var skipping = false           // подключились посреди прожига: запись не ведём, ждём падения сажи
    private var skipMax = 0.0
    private var awaitDrop = false          // после записи ждём, пока сажа упадёт, и только тогда сбрасываем максимум
    private var recordedSoot = 0.0
    private val recent = ArrayList<Double>()
    private val log = ArrayList<RegenEntry>().apply { addAll(parseLog(prefs.getString("rg_soot_log", "") ?: "")) }

    private var lastStatus: Int? = null          // статус прожига от блока (DID 0380): 0 нет, 1 обычный, 2 принудительный
    private var statusSeenAt = 0L
    private var lastTemp: Double? = null
    private var tempSeenAt = 0L
    private var active = false             // сейчас идёт прожиг (по температуре)
    private var hotSince = 0L
    private var coldSince = 0L
    private var endedAt = 0L

    private fun sootLogEnabled() = prefs.getBoolean("exp_soot_log", true)
    private fun useStatus() = prefs.getString("exp_regen_source", "auto") != "temp"
    private fun statusAvailable(now: Long) = useStatus() && statusSeenAt > 0 && now - statusSeenAt < 60_000

    private fun startTemp() = prefs.getString("exp_regen_temp", "450")?.toDoubleOrNull() ?: 450.0

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
        if (skipping) {
            if (smooth > skipMax) skipMax = smooth
            if (smooth < 0.5 * skipMax) { skipping = false; skipMax = 0.0; sootPeak = smooth }
            save()
            return
        }
        if (awaitDrop && smooth < 0.5 * recordedSoot) {
            awaitDrop = false
            sootPeak = smooth
        } else if (smooth > sootPeak) sootPeak = smooth
        checkTimeout(System.currentTimeMillis())
        save()
    }

    /** Температура перед сажевым фильтром (°C). Включение прожига = температура выше порога HOLD_MS подряд. */
    @Synchronized fun onTemp(t: Double) {
        if (!sootLogEnabled() || t.isNaN() || t < -40 || t > 1200) return
        val now = System.currentTimeMillis()
        val newSession = tempSeenAt == 0L || now - tempSeenAt > 60_000
        lastTemp = t
        tempSeenAt = now
        if (statusAvailable(now)) {            // триггер — статус блока, температура только для показа
            checkTimeout(now)
            save()
            return
        }
        val th = startTemp()
        if (!active) {
            if (t >= th) {
                if (newSession) {
                    // приложение подключилось посреди прожига: максимум сажи неизвестен, эту запись не ведём,
                    // а накопленный максимум сбросим, когда сажа упадёт
                    active = true; hotSince = 0L; coldSince = 0L
                    skipping = true; skipMax = 0.0; sootPeak = 0.0
                } else {
                    if (hotSince == 0L) hotSince = now
                    else if (now - hotSince >= HOLD_MS) startRegen(now)
                }
            } else hotSince = 0L
        } else {
            if (t < th - 50) {
                if (coldSince == 0L) coldSince = now
                else if (now - coldSince >= HOLD_MS) endRegen(now)
            } else coldSince = 0L
        }
        checkTimeout(now)
        save()
    }

    /** Статус прожига от блока (0 нет, 1 обычный, 2 принудительный). Включение = статус не 0 STATUS_HOLD_MS подряд. */
    @Synchronized fun onStatus(s: Int) {
        if (!sootLogEnabled() || s < 0 || s > 2) return
        val now = System.currentTimeMillis()
        val newSession = statusSeenAt == 0L || now - statusSeenAt > 60_000
        statusSeenAt = now
        lastStatus = s
        if (!useStatus()) return
        if (!active) {
            if (s >= 1) {
                if (newSession) {
                    // подключились посреди прожига: максимум сажи неизвестен, запись не ведём
                    active = true; hotSince = 0L; coldSince = 0L
                    skipping = true; skipMax = 0.0; sootPeak = 0.0
                } else {
                    if (hotSince == 0L) hotSince = now
                    else if (now - hotSince >= STATUS_HOLD_MS) startRegen(now, s == 2)
                }
            } else hotSince = 0L
        } else {
            if (s == 2) markForced()
            if (s == 0) {
                if (coldSince == 0L) coldSince = now
                else if (now - coldSince >= STATUS_HOLD_MS) endRegen(now)
            } else coldSince = 0L
        }
        checkTimeout(now)
        save()
    }

    private fun endRegen(now: Long) {
        active = false; coldSince = 0L; endedAt = now
        if (skipping) { skipping = false; sootPeak = lastSoot ?: 0.0 }
    }

    private fun markForced() {
        val last = log.lastOrNull() ?: return
        if (last.state == RegenState.RUNNING && !last.forced) log[log.lastIndex] = last.copy(forced = true)
    }

    private fun startRegen(now: Long, forced: Boolean = false) {
        active = true; hotSince = 0L; coldSince = 0L; endedAt = 0L
        if (sootPeak > 0.3) {
            log.add(RegenEntry(now, sootPeak, lastSeen.takeIf { it > 0 }, RegenState.RUNNING, forced))
            while (log.size > 30) log.removeAt(0)
            recordedSoot = sootPeak
            awaitDrop = true
        }
    }

    private fun checkTimeout(now: Long) {
        val last = log.lastOrNull() ?: return
        if (last.state != RegenState.RUNNING) return
        val noCounter = endedAt > 0 && now - endedAt > 180_000          // прожиг закончился, счётчик не вырос
        val stale = !active && now - last.time > 30 * 60_000L            // давно, и прожига уже нет
        if (noCounter || stale) {
            log[log.lastIndex] = last.copy(state = RegenState.INTERRUPTED)
            endedAt = 0L
        }
    }

    @Synchronized fun onCount(c: Int) {
        val prev = lastCount
        lastCount = c
        if (prev != null && c > prev) {
            val now = System.currentTimeMillis()
            var km: Double? = null
            if (peak > 1.0) {
                intervals.add(peak)
                while (intervals.size > 50) intervals.removeAt(0)
                km = peak
                awaitReset = lastSeen > 0.5 * peak
                peak = if (awaitReset) 0.0 else lastSeen
            }
            val last = log.lastOrNull()
            if (last != null && last.state == RegenState.RUNNING) {
                log[log.lastIndex] = last.copy(state = RegenState.COMPLETE)
                endedAt = 0L
            } else if (sootLogEnabled() && sootPeak > 0.3 && (tempSeenAt == 0L || now - tempSeenAt > 120_000) &&
                (statusSeenAt == 0L || now - statusSeenAt > 120_000)) {
                // температура недоступна — записываем по росту счётчика
                log.add(RegenEntry(now, sootPeak, km, RegenState.BY_COUNTER))
                while (log.size > 30) log.removeAt(0)
                recordedSoot = sootPeak
                awaitDrop = true
            }
        }
        save()
    }

    @Synchronized fun lastInterval(): Double? = intervals.lastOrNull()
    @Synchronized fun avgInterval(n: Int): Double? = intervals.takeLast(n).takeIf { it.isNotEmpty() }?.average()

    private fun valid() = log.filter { (it.state == RegenState.COMPLETE || it.state == RegenState.BY_COUNTER) && !it.forced }

    @Synchronized fun lastSootPeak(): Double? = valid().lastOrNull()?.soot
    @Synchronized fun avgSootPeak(n: Int): Double? = valid().takeLast(n).map { it.soot }.takeIf { it.isNotEmpty() }?.average()
    @Synchronized fun currentSootPeak(): Double? = sootPeak.takeIf { it > 0 }
    @Synchronized fun currentSoot(): Double? = lastSoot
    @Synchronized fun currentTemp(): Double? = lastTemp
    @Synchronized fun regenActive(): Boolean = active
    /** Статус от блока (0/1/2), если он сейчас приходит, иначе null. */
    @Synchronized fun liveStatus(): Int? = if (statusAvailable(System.currentTimeMillis())) lastStatus else null
    @Synchronized fun history(): List<RegenEntry> = ArrayList(log)
    @Synchronized fun clearSootLog() {
        log.clear(); sootPeak = 0.0; awaitDrop = false; recent.clear()
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
            val st = p.getOrNull(3)?.toIntOrNull() ?: RegenState.BY_COUNTER   // старые записи были по счётчику
            val forced = p.getOrNull(4) == "1"
            if (t == null || s == null) null else RegenEntry(t, s, p.getOrNull(2)?.toDoubleOrNull(), st, forced)
        }

    private fun save() {
        prefs.edit()
            .putInt("rg_count", lastCount ?: -1)
            .putFloat("rg_peak", peak.toFloat())
            .putFloat("rg_seen", lastSeen.toFloat())
            .putString("rg_hist", intervals.joinToString(","))
            .putFloat("rg_soot_peak", sootPeak.toFloat())
            .putString("rg_soot_log", log.joinToString(";") { "${it.time}|${it.soot}|${it.km ?: ""}|${it.state}|${if (it.forced) 1 else 0}" })
            .apply()
    }

    companion object {
        private const val HOLD_MS = 20_000L          // температура выше порога
        private const val STATUS_HOLD_MS = 10_000L   // статус блока не 0
        @Volatile private var inst: RegenTracker? = null
        fun get(prefs: SharedPreferences): RegenTracker =
            inst ?: synchronized(this) { inst ?: RegenTracker(prefs).also { inst = it } }
    }
}
