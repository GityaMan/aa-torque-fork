package com.aatorque.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

/** Экран «Подключение к OBD»: выбор адаптера, запуск/остановка, статус и проверка значений. */
@Suppress("DEPRECATION")
class ObdSettingsActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("obd_bridge", MODE_PRIVATE) }
    private lateinit var statusView: TextView
    private lateinit var valuesView: TextView
    private lateinit var group: RadioGroup
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Подключение к OBD"
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, pad, 0, pad / 4) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply { text = t; setOnClickListener { onClick() } }

        statusView = TextView(this).apply { textSize = 18f; text = ObdBridgeService.status }
        root.addView(label("Состояние"))
        root.addView(statusView)

        root.addView(button("Запустить / подключиться") { askPermissionsThenStart(); ObdBridgeService.reconnect(this) })
        root.addView(button("Остановить") { ObdBridgeService.stop(this) })
        root.addView(button("Настройки Bluetooth (сопряжение)") {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        })

        root.addView(label("Адаптер (из сопряжённых устройств)"))
        group = RadioGroup(this)
        root.addView(group)

        root.addView(label("Порог сажи для «DPF %», г/л (пусто — выключено)"))
        val dpf = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "например 1.5"
            val saved = prefs.getFloat("dpf_full_gl", 0f)
            if (saved > 0) setText(saved.toString())
        }
        root.addView(dpf)
        root.addView(button("Сохранить порог") {
            val v = dpf.text.toString().replace(',', '.').toFloatOrNull() ?: 0f
            prefs.edit().putFloat("dpf_full_gl", v).apply()
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        })

        root.addView(label("Проверка значений"))
        valuesView = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 14f; text = "—" }
        root.addView(valuesView)

        setContentView(ScrollView(this).apply {
            addView(root)
            setOnApplyWindowInsetsListener { v, insets ->
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
        })
        askPermissionsThenStart()
    }

    override fun onResume() {
        super.onResume()
        fillDevices()
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

    // ---------------- разрешения ----------------

    private fun askPermissionsThenStart() {
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
            fillDevices()
            ObdBridgeService.start(this)
        }
    }

    // ---------------- список адаптеров ----------------

    @SuppressLint("MissingPermission")
    private fun fillDevices() {
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()
        val saved = prefs.getString("obd_mac", null)
        val auto = RadioButton(this).apply {
            text = "Автоматически (имя содержит OBD, ELM, V-Link…)"
            id = View.generateViewId(); tag = ""
        }
        group.addView(auto)
        var checked = auto
        val allowed = Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (allowed) {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices
                ?.sortedBy { it.name ?: "" }
                ?.forEach { d ->
                    val rb = RadioButton(this).apply {
                        text = "${d.name ?: "?"}  (${d.address})"
                        id = View.generateViewId(); tag = d.address
                    }
                    group.addView(rb)
                    if (d.address == saved) checked = rb
                }
        } else {
            group.addView(TextView(this).apply { text = "Нет разрешения Bluetooth: нажмите «Запустить»" })
        }
        checked.isChecked = true
        group.setOnCheckedChangeListener { g, id ->
            val mac = g.findViewById<RadioButton>(id)?.tag as? String ?: ""
            prefs.edit().apply { if (mac.isEmpty()) remove("obd_mac") else putString("obd_mac", mac) }.apply()
            ObdBridgeService.reconnect(this)
        }
    }

    // ---------------- статус и значения ----------------

    private val tick = object : Runnable {
        override fun run() {
            statusView.text = ObdBridgeService.status
            if (!busy) {
                busy = true
                io.execute {
                    val text = KEY.joinToString("\n") { (id, name) ->
                        val v = ObdBridgeService.peek(id)
                        String.format(Locale.US, "%-24s %s", name, v?.let { String.format(Locale.US, "%.2f", it) } ?: "—")
                    }
                    ui.post { valuesView.text = text; busy = false }
                }
            }
            ui.postDelayed(this, 2000)
        }
    }

    private companion object {
        val KEY = listOf(
            "05" to "Температура ОЖ",
            "0c" to "Обороты",
            "0d" to "Скорость",
            "ff9005" to "Температура АКПП",
            "ff9003" to "Сажа, г/л",
            "ff9001" to "Счётчик регенераций",
            "ff9002" to "Пробег после прожига",
        )
    }
}
