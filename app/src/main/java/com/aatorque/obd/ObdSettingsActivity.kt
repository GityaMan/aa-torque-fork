package com.aatorque.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.aatorque.stats.R
import java.util.Locale
import java.util.concurrent.Executors

/** Экран «Подключение к OBD» в стиле остальных настроек приложения (тема AppTheme, список настроек). */
class ObdSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)              // та же тема, что у главного экрана
        super.onCreate(savedInstanceState)
        title = "Подключение к OBD"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, ObdPrefsFragment())
                .commit()
        }
        askPermissionsThenStart()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    fun askPermissionsThenStart() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isEmpty()) ObdBridgeService.start(this)
        else requestPermissions(need.toTypedArray(), 77)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) {
            ObdBridgeService.start(this)
            (supportFragmentManager.findFragmentById(android.R.id.content) as? ObdPrefsFragment)?.refreshAdapters()
        }
    }
}

class ObdPrefsFragment : PreferenceFragmentCompat() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var statusPref: Preference? = null
    private var valuesPref: Preference? = null
    private var adapterPref: ListPreference? = null
    @Volatile private var busy = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "obd_bridge"
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("obd_bridge", Context.MODE_PRIVATE)
        val screen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen = screen

        fun category(title: String): PreferenceCategory {
            val c = PreferenceCategory(ctx)
            c.title = title
            screen.addPreference(c)
            return c
        }
        fun action(cat: PreferenceCategory, title: String, summary: String?, onClick: () -> Unit) {
            val p = Preference(ctx)
            p.title = title
            if (summary != null) p.summary = summary
            p.setOnPreferenceClickListener { onClick(); true }
            cat.addPreference(p)
        }

        // --- состояние ---
        val st = category("Состояние")
        val status = Preference(ctx)
        status.title = "Связь с адаптером"
        status.summary = ObdBridgeService.status
        status.isSelectable = false
        st.addPreference(status)
        statusPref = status
        val values = Preference(ctx)
        values.title = "Текущие значения"
        values.summary = "—"
        values.isSelectable = false
        st.addPreference(values)
        valuesPref = values

        // --- подключение ---
        val con = category("Подключение")
        action(con, "Запустить / подключиться", "Запускает фоновую службу и переподключается к адаптеру") {
            (activity as? ObdSettingsActivity)?.askPermissionsThenStart()
            ObdBridgeService.reconnect(ctx)
        }
        action(con, "Остановить", "Закрывает соединение и убирает уведомление") {
            ObdBridgeService.stop(ctx)
        }
        action(con, "Настройки Bluetooth", "Сопряжение адаптера (имя обычно OBDII, PIN 1234 или 0000)") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        val ad = ListPreference(ctx)
        ad.key = "obd_mac"
        ad.title = "Адаптер"
        ad.dialogTitle = "Адаптер (из сопряжённых устройств)"
        ad.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        ad.setOnPreferenceChangeListener { _, _ -> ObdBridgeService.reconnect(ctx); true }
        con.addPreference(ad)
        adapterPref = ad
        refreshAdapters()

        // --- опрос датчиков ---
        val poll = category("Опрос датчиков")
        val fast = ListPreference(ctx)
        fast.key = "poll_fast_ms"
        fast.title = "Частота опроса датчиков"
        fast.dialogTitle = "Как часто обновлять основные датчики"
        fast.entries = arrayOf("Максимальная (без паузы)", "Высокая (~10 раз/с)", "Обычная (~5 раз/с)", "Экономная (~2 раза/с)")
        fast.entryValues = arrayOf("0", "100", "200", "500")
        fast.setDefaultValue("100")
        fast.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        poll.addPreference(fast)
        val slow = ListPreference(ctx)
        slow.key = "poll_slow_ms"
        slow.title = "Частота опроса медленных датчиков"
        slow.dialogTitle = "Сажа, температура АКПП, счётчик и пробег прожига"
        slow.entries = arrayOf("Часто (раз в 1 с)", "Обычно (раз в 3 с)", "Редко (раз в 10 с)", "Очень редко (раз в 30 с)")
        slow.entryValues = arrayOf("1000", "3000", "10000", "30000")
        slow.setDefaultValue("3000")
        slow.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        poll.addPreference(slow)
        val turbo = SwitchPreferenceCompat(ctx)
        turbo.key = "fast_adapter"
        turbo.title = "Ускорение ответов адаптера"
        turbo.summary = "Режим ATAT2 и ответ без ожидания лишних кадров. Если значения пропали, выключите."
        turbo.setDefaultValue(true)
        turbo.setOnPreferenceChangeListener { _, _ -> ObdBridgeService.reconnect(ctx); true }
        poll.addPreference(turbo)

        // --- сажевый фильтр ---
        val dpfCat = category("Сажевый фильтр")
        val dpf = EditTextPreference(ctx)
        dpf.key = "dpf_full_gl_ui"
        dpf.isPersistent = false                 // число храним сами как float (его читает ObdEngine)
        dpf.title = "Порог сажи для «DPF %», г/л"
        dpf.dialogTitle = "Порог сажи (г/л), пусто — выключено"
        dpf.text = prefs.getFloat("dpf_full_gl", 0f).takeIf { it > 0 }?.toString() ?: ""
        dpf.setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        dpf.summaryProvider = Preference.SummaryProvider<EditTextPreference> { p ->
            if (p.text.isNullOrBlank()) "Выключено" else "${p.text} г/л"
        }
        dpf.setOnPreferenceChangeListener { _, v ->
            val f = v.toString().replace(',', '.').toFloatOrNull() ?: 0f
            prefs.edit().putFloat("dpf_full_gl", f).apply()
            true
        }
        dpfCat.addPreference(dpf)

        // --- приложение ---
        val app = category("Приложение")
        action(app, "Экспериментальные функции", "Запись сажи перед прожигом и подбор порога для DPF %") {
            startActivity(Intent(ctx, ObdExperimentalActivity::class.java))
        }
        action(app, "Выход из приложения", "Закрыть службу, соединение и приложение целиком") {
            activity?.let { AppExit.confirmAndExit(it) }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshAdapters() {
        val ad = adapterPref ?: return
        val ctx = context ?: return
        val names = mutableListOf("Автоматически (OBD, ELM, V-Link…)")
        val vals = mutableListOf("")
        val allowed = Build.VERSION.SDK_INT < 31 ||
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices
                ?.sortedBy { it.name ?: "" }
                ?.forEach {
                    names += "${it.name ?: "?"}  (${it.address})"
                    vals += it.address
                }
        }
        ad.entries = names.toTypedArray()
        ad.entryValues = vals.toTypedArray()
        if (ad.value == null) ad.value = ""
    }

    override fun onResume() {
        super.onResume()
        refreshAdapters()
        ui.post(tick)
    }

    override fun onPause() {
        ui.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private val tick = object : Runnable {
        override fun run() {
            statusPref?.summary = ObdBridgeService.status
            if (!busy) {
                busy = true
                io.execute {
                    val text = KEY.joinToString("\n") { (id, name) ->
                        val v = ObdBridgeService.peek(id)
                        name + ": " + (v?.let { String.format(Locale.US, "%.2f", it) } ?: "—")
                    }
                    ui.post { valuesPref?.summary = text; busy = false }
                }
            }
            ui.postDelayed(this, 2000)
        }
    }

    private companion object {
        val KEY = listOf(
            "05" to "Температура ОЖ, °C",
            "0c" to "Обороты, об/мин",
            "0d" to "Скорость, км/ч",
            "ff9005" to "Температура АКПП, °C",
            "ff9006" to "Статус прожига (0/1/2)",
            "ff9003" to "Сажа, г/л",
            "ff9001" to "Счётчик регенераций",
            "ff9002" to "Пробег после прожига, км",
        )
    }
}
