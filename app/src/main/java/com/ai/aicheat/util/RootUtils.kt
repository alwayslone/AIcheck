package com.ai.aicheat.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

/**
 * Root权限工具类
 * 用于执行需要Root权限的操作
 */
object RootUtils {
    private const val TAG = "RootUtils"
    
    /**
     * 检查是否有Root权限
     */
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }
    
    /**
     * 执行Root命令
     */
    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            val exitValue = process.waitFor()
            
            CommandResult(exitValue == 0, output, error)
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            CommandResult(false, "", e.message ?: "Unknown error")
        }
    }
    
    /**
     * 使用Root权限截图
     * @param outputPath 截图保存路径
     * @return 截图文件，失败返回null
     */
    suspend fun takeScreenshot(outputPath: String): File? = withContext(Dispatchers.IO) {
        try {
            // 使用screencap命令截图
            val result = executeCommand("screencap -p $outputPath")
            if (result.success) {
                val file = File(outputPath)
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Screenshot saved to $outputPath")
                    file
                } else {
                    Log.e(TAG, "Screenshot file not created or empty")
                    null
                }
            } else {
                Log.e(TAG, "Screenshot command failed: ${result.error}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Take screenshot failed", e)
            null
        }
    }
    
    /**
     * 使用Root权限截图并返回Bitmap
     */
    suspend fun takeScreenshotAsBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val tempPath = "/data/local/tmp/screenshot_${System.currentTimeMillis()}.png"
        try {
            val file = takeScreenshot(tempPath)
            if (file != null) {
                val bitmap = BitmapFactory.decodeFile(tempPath)
                // 删除临时文件
                executeCommand("rm $tempPath")
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Take screenshot as bitmap failed", e)
            executeCommand("rm $tempPath")
            null
        }
    }
    
    /**
     * 使用Root权限监听按键事件
     * 通过getevent命令监听输入事件
     */
    fun getInputEventCommand(): String {
        return "getevent -l"
    }
    
    /**
     * 模拟按键事件（如需要恢复音量等）
     */
    suspend fun injectKeyEvent(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val result = executeCommand("input keyevent $keyCode")
        result.success
    }
    
    /**
     * 使用Root权限将文本复制到剪贴板
     * 通过写入临时文件并使用 app_process 执行 Java 代码来实现
     * @param text 要复制的文本
     * @return 是否成功
     */
    suspend fun copyToClipboard(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = "/data/local/tmp/clip_${System.currentTimeMillis()}.txt"
            
            // 将文本写入临时文件（避免shell转义问题）
            val writeResult = writeTextToFile(text, tempFile)
            if (!writeResult) {
                Log.e(TAG, "Failed to write text to temp file")
                return@withContext false
            }
            
            // 方法1: 使用 cmd clipboard (Android 10+)
            var result = executeCommand("cat $tempFile | cmd clipboard set")
            if (result.success && !result.error.contains("Error") && !result.error.contains("Exception")) {
                Log.d(TAG, "Copied to clipboard using cmd clipboard")
                executeCommand("rm $tempFile")
                return@withContext true
            }
            
            // 方法2: 使用 content provider 方式
            val escapedForShell = text.replace("'", "'\"'\"'")
            result = executeCommand("am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$escapedForShell' --activity-no-history --activity-exclude-from-recents com.android.shell 2>/dev/null || true")
            
            // 方法3: 通过 settings 写入（部分设备支持）
            result = executeCommand("settings put system clipboard_text \"$(cat $tempFile)\"")
            
            // 清理临时文件
            executeCommand("rm $tempFile")
            
            Log.d(TAG, "Clipboard copy attempted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Copy to clipboard failed", e)
            false
        }
    }
    
    /**
     * 将文本写入文件（使用Root权限）
     */
    private suspend fun writeTextToFile(text: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            // 使用 cat 和 EOF 来写入多行文本，避免转义问题
            os.writeBytes("cat > $filePath << 'CLIPBOARD_EOF'\n")
            os.writeBytes(text)
            os.writeBytes("\nCLIPBOARD_EOF\n")
            os.writeBytes("chmod 644 $filePath\n")
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            
            val exitValue = process.waitFor()
            exitValue == 0
        } catch (e: Exception) {
            Log.e(TAG, "Write text to file failed", e)
            false
        }
    }
    
    /**
     * 使用Root权限将文本复制到剪贴板（简化版，直接使用input命令模拟粘贴）
     * 这种方式更可靠，但会直接粘贴而非仅复制
     * @param text 要粘贴的文本
     * @return 是否成功
     */
    suspend fun pasteText(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 转义特殊字符用于input text命令
            val escapedText = text
                .replace("\\", "\\\\")
                .replace(" ", "%s")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
            
            val result = executeCommand("input text \"$escapedText\"")
            result.success
        } catch (e: Exception) {
            Log.e(TAG, "Paste text failed", e)
            false
        }
    }
    
    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String
    )
}
