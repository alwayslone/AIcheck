package com.ai.aicheat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ai.aicheat.service.AIService
import com.ai.aicheat.service.VolumeKeyService
import com.ai.aicheat.ui.theme.AicheatTheme
import com.ai.aicheat.util.ConfigManager
import com.ai.aicheat.util.RootUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 检查并请求悬浮窗权限
        checkOverlayPermission()
        
        setContent {
            AicheatTheme {
                MainScreen(
                    onStartService = { startMonitorService() },
                    onStopService = { stopMonitorService() },
                    onCheckRoot = { checkRoot() }
                )
            }
        }
    }
    
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }
    
    private fun startMonitorService() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            checkOverlayPermission()
            return
        }
        
        lifecycleScope.launch {
            val hasRoot = RootUtils.checkRootAccess()
            if (hasRoot) {
                VolumeKeyService.start(this@MainActivity)
                Toast.makeText(this@MainActivity, "服务已启动", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "需要Root权限", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun stopMonitorService() {
        VolumeKeyService.stop(this)
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
    }
    
    private fun checkRoot() {
        lifecycleScope.launch {
            val hasRoot = RootUtils.checkRootAccess()
            val message = if (hasRoot) "Root权限: ✅ 已获取" else "Root权限: ❌ 未获取"
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能正常工作", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onCheckRoot: () -> Unit
) {
    val context = LocalContext.current
    // 使用ConfigManager持久化配置
    var apiUrl by remember { mutableStateOf(ConfigManager.apiUrl) }
    var apiKey by remember { mutableStateOf(ConfigManager.apiKey) }
    var model by remember { mutableStateOf(ConfigManager.model) }
    var prompt by remember { mutableStateOf(ConfigManager.prompt) }
    var isServiceRunning by remember { mutableStateOf(VolumeKeyService.isRunning()) }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI助手") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "服务状态",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServiceRunning) "✅ 运行中" else "⚫ 已停止",
                        fontSize = 16.sp
                    )
                }
            }
            
            // 操作指南
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "使用说明",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• 音量下键: 截图并发送AI分析")
                    Text("• 音量上键: 显示/隐藏结果")
                    Text("• 悬浮窗无法被截图拍到")
                    Text("• 服务保持后台运行")
                }
            }
            
            // API配置
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "API配置",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        label = { Text("API URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("自定义Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        placeholder = { Text("请输入AI分析图片时使用的提示词...") }
                    )
                    
                    Button(
                        onClick = {
                            // 保存到ConfigManager
                            ConfigManager.apiUrl = apiUrl
                            ConfigManager.apiKey = apiKey
                            ConfigManager.model = model
                            ConfigManager.prompt = prompt
                            // 同时更新AIService的内存配置
                            AIService.updateConfig(
                                AIService.AIConfig(apiUrl, apiKey, model)
                            )
                            Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存配置")
                    }
                }
            }
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCheckRoot,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("检查Root")
                }
                
                Button(
                    onClick = {
                        onStartService()
                        isServiceRunning = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isServiceRunning
                ) {
                    Text("启动服务")
                }
                
                Button(
                    onClick = {
                        onStopService()
                        isServiceRunning = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isServiceRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("停止服务")
                }
            }
            
            // 警告信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚠️ 注意事项",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 需要Root权限\n• 需要悬浮窗权限\n• 请合理合法使用\n• 不要用于考试作弊！！！！\n• 造成任何不良后果自己承担",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}