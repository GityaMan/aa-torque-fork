package com.aatorque.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.UUID

/** Bluetooth Classic (SPP) канал к ELM327. Команды строго по очереди. */
class Elm327Link(private val device: BluetoothDevice) {
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission")
    fun open() {
        close()
        runCatching { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() }   // нужен BLUETOOTH_SCAN; поиск мы не запускаем, отказ не страшен
        var s: BluetoothSocket? = null
        try {
            s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
        } catch (e: IOException) {
            runCatching { s?.close() }
            // Запасной путь для «китайских» клонов, не отвечающих на SPP UUID
            s = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                .invoke(device, 1) as BluetoothSocket
            try { s.connect() } catch (e2: IOException) { runCatching { s.close() }; throw e2 }
        }
        socket = s
        input = s!!.inputStream
        output = s.outputStream
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    /** Отправляет команду и читает ответ до приглашения '>' . Возвращает строки ответа без эха. */
    @Synchronized
    fun command(cmd: String, timeoutMs: Long): String {
        val out = output ?: throw IOException("Нет соединения")
        val inp = input ?: throw IOException("Нет соединения")
        while (inp.available() > 0) inp.read()          // выбрасываем хвост прошлого ответа
        out.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (inp.available() > 0) {
                val c = inp.read()
                if (c < 0) throw IOException("Соединение закрыто")
                if (c.toChar() == '>') break
                sb.append(c.toChar())
            } else {
                if (System.currentTimeMillis() > deadline) throw SocketTimeoutException("Таймаут: $cmd")
                Thread.sleep(4)
            }
        }
        return sb.toString().replace('\r', '\n').lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) }
            .joinToString("\n")
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
