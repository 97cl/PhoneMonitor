package com.phonemonitor

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etServerUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerUrl = findViewById(R.id.et_server_url)
        btnSave = findViewById(R.id.btn_save)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)
        tvStatus = findViewById(R.id.tv_status)

        // 加载已保存的服务器地址
        etServerUrl.setText(Prefs.getServerUrl(this))

        btnSave.setOnClickListener {
            val url = etServerUrl.text.toString().trim()
            if (url.isBlank()) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setServerUrl(this, url)
            Toast.makeText(this, "已保存: $url", Toast.LENGTH_SHORT).show()
        }

        btnStart.setOnClickListener {
            if (!checkPermissions()) return@setOnClickListener
            startMonitoring()
        }

        btnStop.setOnClickListener {
            stopMonitoring()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun checkPermissions(): Boolean {
        // 1. 使用情况访问权限
        if (!UsageStatsHelper.hasPermission(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要使用情况访问权限")
                .setMessage("请授予 PhoneMonitor 查看应用使用情况的权限，这是获取使用数据的基础。")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("取消", null)
                .show()
            return false
        }

        // 2. 无障碍服务（用于实时检测当前应用）
        if (!ForegroundAppTracker.isEnabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("需要无障碍服务")
                .setMessage("请开启 PhoneMonitor 的无障碍服务，用于实时检测当前正在使用的应用。")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("取消", null)
                .show()
            return false
        }

        // 3. 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationHelper.hasPermission(this)) {
                NotificationHelper.requestPermission(this)
                return false
            }
        }

        return true
    }

    private fun startMonitoring() {
        val url = etServerUrl.text.toString().trim()
        if (url.isBlank()) {
            Toast.makeText(this, "请先填写服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setServerUrl(this, url)

        val intent = Intent(this, UsageMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(this, "监控已启动", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, UsageMonitorService::class.java)
        stopService(intent)
        Toast.makeText(this, "监控已停止", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun updateStatus() {
        val running = UsageMonitorService.isRunning
        val url = Prefs.getServerUrl(this)

        tvStatus.text = if (running) {
            "状态：运行中\n服务器：$url"
        } else {
            "状态：未启动\n服务器：$url"
        }
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
    }
}
