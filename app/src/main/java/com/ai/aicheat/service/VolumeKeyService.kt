package com.ai.aicheat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ai.aicheat.MainActivity
import com.ai.aicheat.R
import com.ai.aicheat.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * 音量键监听服务
 * 使用Root权限监听全局按键事件
 */
class VolumeKeyService : LifecycleService() {
    
    companion object {
        private const val TAG = "VolumeKeyService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "volume_key_service"
        
        // 按键事件码
        private const val KEY_VOLUME_DOWN = "KEY_VOLUMEDOWN"
        private const val KEY_VOLUME_UP = "KEY_VOLUMEUP"
        
        private var instance: VolumeKeyService? = null
        
        fun isRunning(): Boolean = instance != null
        
        fun start(context: Context) {
            val intent = Intent(context, VolumeKeyService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, VolumeKeyService::class.java))
        }
    }
    
    private var keyMonitorJob: Job? = null
    private var monitorProcess: Process? = null
    private var isProcessing = false  // 防止重复触发
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startKeyMonitor()
        
        // 同时启动悬浮窗服务
        startService(Intent(this, OverlayService::class.java))
        
        Log.d(TAG, "VolumeKeyService started")
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY  // 被杀后重启
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持应用后台运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("服务运行中")
            .setContentText("正在监听...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 启动按键监听
     * 使用getevent命令监听底层输入事件
     */
    private fun startKeyMonitor() {
        keyMonitorJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting key monitor...")
                
                // 使用su执行getevent监听所有输入设备
                val process = Runtime.getRuntime().exec("su")
                monitorProcess = process
                
                val os = DataOutputStream(process.outputStream)
                // getevent -l 会输出可读的事件名称
                os.writeBytes("getevent -l\n")
                os.flush()
                
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                
                var lastVolumeDownTime = 0L
                var lastVolumeUpTime = 0L
                val debounceTime = 500L  // 防抖时间
                
                while (isActive) {
                    val line = reader.readLine() ?: break
                    
                    // 检测音量下键按下事件
                    if (line.contains(KEY_VOLUME_DOWN) && line.contains("DOWN")) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastVolumeDownTime > debounceTime) {
                            lastVolumeDownTime = currentTime
                            Log.d(TAG, "Volume DOWN pressed")
                            onVolumeDownPressed()
                        }
                    }
                    
                    // 检测音量上键按下事件
                    if (line.contains(KEY_VOLUME_UP) && line.contains("DOWN")) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastVolumeUpTime > debounceTime) {
                            lastVolumeUpTime = currentTime
                            Log.d(TAG, "Volume UP pressed")
                            onVolumeUpPressed()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Key monitor error", e)
                // 尝试重启监听
                delay(3000)
                if (isActive) {
                    startKeyMonitor()
                }
            }
        }
    }
    
    /**
     * 音量下键按下 - 截图并发送AI
     */
    private fun onVolumeDownPressed() {
        if (isProcessing) {
            Log.d(TAG, "Already processing, skip")
            return
        }
        
        lifecycleScope.launch {
            isProcessing = true
            try {
                // 显示处理提示
                OverlayService.showText(this@VolumeKeyService, "正在处理...")
                
                // 短暂延迟，避免截到按键动画
                delay(100)
                
                // 截图
                Log.d(TAG, "Taking screenshot...")
                val bitmap = RootUtils.takeScreenshotAsBitmap()
                
                if (bitmap != null) {
                    Log.d(TAG, "Screenshot taken, sending to AI...")
                    OverlayService.showText(this@VolumeKeyService, "正在分析...")
                    
                    // 发送到AI
                    val result = AIService.analyzeScreenshot(bitmap)
                    
                    result.onSuccess { response ->
                        Log.d(TAG, "AI response: ${response.take(100)}")
                        OverlayService.showText(this@VolumeKeyService, response)
                        
                        // 使用Android原生API复制到剪贴板（必须在主线程执行）
                        copyToClipboard(response)
                    }
                    
                    result.onFailure { error ->
                        Log.e(TAG, "AI error", error)
                        OverlayService.showText(this@VolumeKeyService, "错误: ${error.message}")
                    }
                    
                    // 回收bitmap
                    bitmap.recycle()
                } else {
                    Log.e(TAG, "Screenshot failed")
                    OverlayService.showText(this@VolumeKeyService, "截图失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Process error", e)
                OverlayService.showText(this@VolumeKeyService, "处理失败: ${e.message}")
            } finally {
                isProcessing = false
            }
        }
    }
    
    /**
     * 音量上键按下 - 切换悬浮窗可见性
     */
    private fun onVolumeUpPressed() {
        OverlayService.hideText(this)
    }
    
    /**
     * 复制文本到剪贴板（使用Android原生API）
     */
    private fun copyToClipboard(text: String) {
        mainHandler.post {
            try {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = ClipData.newPlainText("AI Response", text)
                clipboardManager.setPrimaryClip(clipData)
                Log.d(TAG, "AI response copied to clipboard successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy to clipboard", e)
                // 如果原生API失败，尝试使用Root方式
                lifecycleScope.launch {
                    RootUtils.copyToClipboard(text)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        keyMonitorJob?.cancel()
        try {
            monitorProcess?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying process", e)
        }
        instance = null
        Log.d(TAG, "VolumeKeyService stopped")
    }
}
