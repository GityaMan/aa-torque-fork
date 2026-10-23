package com.aatorque.obd

import android.content.SharedPreferences
import java.io.IOException

/** Опрос блоков через ELM327: инициализация, смена заголовков, кэш, разбор ответов. */
class ObdEngine(private val link: Elm327Link, private val prefs: SharedPreferences) {
    val tracker = RegenTracker(prefs)

    private class Sample(val t: Long, val v: Double?)
    private val cache = HashMap<String, Sample>()
    private val supported = HashSet<Int>()      // PID режима 01, о которых заявил блок двигателя
    private var curHdr = ""
    private var bg: Thread? = null
    @Volatile private var closed = false

    /** Подряд идущие сбои связи; сервис переподключается, когда их набирается 5. */
    @Volatile var failures = 0
        private set

    /** Порог сажи (г/л) для расчёта процента заполнения; 0 — не задан. */
    fun dpfFullGl(): Double {
        val p = prefs.getFloat("dpf_full_gl", 0f).toDouble()
        return if (p > 0) p else ObdConfig.DPF_FULL_GL
    }

    @Synchronized
    fun init() {
        curHdr = ""
        runCatching { link.command("ATZ", 3000) }
        for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP6")) link.command(c, 1500)
        var r = parse(send("7E0", "0100", 4000), "4100")
        if (r == null) {                                   // не CAN 11/500 — автопоиск протокола
            link.command("ATSP0", 1500); curHdr = ""
            r = parse(send("7E0", "0100", 8000), "4100")
        }
        if (r == null) throw IOException("Блок двигателя не отвечает. Включите зажигание.")
        readSupported(r)
        failures = 0
        readId("33")                                       // барометр нужен для расчёта наддува
        bg = Thread(::backgroundLoop, "obd-regen").also { it.isDaemon = true; it.start() }
    }

    fun close() {
        closed = true
        bg?.interrupt()
        link.close()
    }

    /** Счётчик и пробег прожига читаем сами, чтобы журнал не зависел от того, что выведено на экран. */
    private fun backgroundLoop() {
        try {
            while (!closed) {
                readId("ff9001"); readId("ff9002")
                Thread.sleep(20_000)
            }
        } catch (_: InterruptedException) { }
    }

    fun listIds(): Array<String> = MazdaPids.all
        .filter { !it.isStandard || supported.isEmpty() || supported.contains(it.stdPid) }
        .map { it.id }.toTypedArray()

    fun value(id: String): Double? = MazdaPids.byId(id)?.let { read(it) }
    fun readId(id: String): Double? = value(id)

    @Synchronized
    fun read(p: PidDef): Double? {
        val now = System.currentTimeMillis()
        val ttl = if (p.slow) 3000L else 100L
        cache[p.id]?.let { if (now - it.t < ttl) return it.v }
        val v = try { fetch(p) } catch (e: IOException) { failures++; null }
        cache[p.id] = Sample(System.currentTimeMillis(), v)
        if (v != null) when (p.id) {
            "ff9001" -> tracker.onCount(v.toInt())
            "ff9002" -> tracker.onDist(v)
        }
        return v
    }

    private fun fetch(p: PidDef): Double? {
        p.virtual?.let { return it(this) }
        val req = p.request ?: return null
        val prefix = (if (req.startsWith("01")) "41" else "62") + req.substring(2)
        val text = send(p.header, req, 1500)
        failures = 0
        val b = parse(text, prefix) ?: return null
        return p.decode?.invoke(b)
    }

    private fun send(hdr: String, cmd: String, timeoutMs: Long): String {
        if (hdr != curHdr) { link.command("ATSH$hdr", 1000); curHdr = hdr }
        return link.command(cmd, timeoutMs)
    }

    private fun readSupported(first: IntArray) {
        supported.clear()
        var bits: IntArray? = first
        var base = 0
        while (bits != null && bits.size >= 4) {
            for (i in 0 until 32) if ((bits[i shr 3] and (0x80 shr (i and 7))) != 0) supported.add(base + i + 1)
            if (!supported.contains(base + 32) || base >= 0xA0) break
            base += 32
            val h = "%02X".format(base)
            bits = parse(send("7E0", "01$h", 2000), "41$h")
        }
    }

    /** Байты ответа после префикса (например «410C» или «620434»); null, если ответа нет. */
    private fun parse(text: String?, prefix: String): IntArray? {
        if (text.isNullOrBlank()) return null
        val lines = text.uppercase().split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val multi = lines.any { Regex("^[0-9A-F]\\s*:").containsMatchIn(it) }
        val sb = StringBuilder()
        for (l in lines) {
            if (multi) {
                Regex("^[0-9A-F]\\s*:([0-9A-F ]*)$").find(l)?.let { sb.append(it.groupValues[1]) }
            } else if (Regex("^[0-9A-F ]+$").matches(l)) sb.append(l)
        }
        val hex = sb.toString().replace(" ", "")
        val i = hex.indexOf(prefix.uppercase())
        if (i < 0) return null
        val rest = hex.substring(i + prefix.length)
        return IntArray(rest.length / 2) { rest.substring(it * 2, it * 2 + 2).toInt(16) }
    }
}
