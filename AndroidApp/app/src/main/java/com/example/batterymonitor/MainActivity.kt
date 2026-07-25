package com.example.batterymonitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)

        val serverIp = findViewById<EditText>(R.id.serverIp)
        val deviceName = findViewById<EditText>(R.id.deviceName)
        val status = findViewById<TextView>(R.id.status)

        serverIp.setText(prefs.getString("serverIp", ""))
        deviceName.setText(prefs.getString("name", Build.MODEL))

        // Android 13+ 需要通知权限,否则前台服务通知不显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        findViewById<Button>(R.id.startBtn).setOnClickListener {
            prefs.edit()
                .putString("serverIp", serverIp.text.toString().trim())
                .putString("name", deviceName.text.toString().trim())
                .apply()
            ContextCompat.startForegroundService(
                this, Intent(this, BatteryHttpService::class.java))
            status.text = "服务已启动,监听端口 ${BatteryHttpService.LISTEN_PORT}"
            Toast.makeText(this, "已启动并注册", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.stopBtn).setOnClickListener {
            stopService(Intent(this, BatteryHttpService::class.java))
            status.text = "服务已停止"
        }
    }
}
