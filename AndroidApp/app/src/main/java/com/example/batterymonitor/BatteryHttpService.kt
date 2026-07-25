package com.example.batterymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

/**
 * 前台服务:在手机上跑一个极小的 HTTP 服务器,只暴露 GET /battery。
 * 被服务器查询时才现场读一次电量返回。
 * 同时定时向 Flask 服务器注册(应对手机 IP 变化)。
 */
class BatteryHttpService : Service() {

    private var httpServer: BatteryServer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: android.content.SharedPreferences

    private val reRegister = object : Runnable {
        override fun run() {
            registerToServer()
            handler.postDelayed(this, 5 * 60 * 1000L) // 每 5 分钟重注册
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        startForeground(1, buildNotification("正在提供电量查询服务"))
        httpServer = BatteryServer(LISTEN_PORT).apply {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
        }
        handler.post(reRegister)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(reRegister)
        httpServer?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 现场读取当前电量,拼成 JSON */
    private fun currentBatteryJson(): String {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        return JSONObject().apply {
            put("deviceId", deviceId())
            put("name", prefs.getString("name", Build.MODEL))
            put("battery", level)
            put("charging", charging)
        }.toString()
    }

    /** 向 Flask 服务器登记自己(IP 由服务器自动获取) */
    private fun registerToServer() {
        val serverIp = prefs.getString("serverIp", "").orEmpty()
        if (serverIp.isBlank()) return
        thread {
            try {
                val body = JSONObject().apply {
                    put("deviceId", deviceId())
                    put("name", prefs.getString("name", Build.MODEL))
                    put("port", LISTEN_PORT)
                }.toString()
                (URL("http://$serverIp:5010/register").openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                    inputStream.use { it.readBytes() }
                    disconnect()
                }
            } catch (_: Exception) {
                // 局域网偶发失败无所谓,下次重注册会补上
            }
        }
    }

    private fun deviceId(): String {
        var id = prefs.getString("deviceId", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("deviceId", id).apply()
        }
        return id
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "battery_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "电量服务", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("电量监控")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .build()
    }

    /** 极小 HTTP 服务器,只处理 /battery */
    inner class BatteryServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response =
            if (session.uri == "/battery") {
                newFixedLengthResponse(
                    Response.Status.OK, "application/json", currentBatteryJson())
            } else {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/plain", "not found")
            }
    }

    companion object {
        const val LISTEN_PORT = 8080
    }
}
