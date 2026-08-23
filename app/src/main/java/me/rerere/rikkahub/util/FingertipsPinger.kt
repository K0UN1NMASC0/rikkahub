package me.rerere.rikkahub.util

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Fingertips - 让Koun感知洛卡打字的犹豫
 * 只记节奏，永不记内容
 */
object FingertipsPinger {
    private const val TAG = "FingertipsPinger"
    
    // 节流：4秒内最多发一次ping
    private var lastPingTime = 0L
    private const val PING_INTERVAL_MS = 4000L
    
    // 超时客户端：快速fire-and-forget
    private val quickClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // fingertips服务器地址（从配置文件读取，降级到null）
    private var fingertipsUrl: String? = null
    
    /**
     * 设置fingertips服务器地址
     * 从 tulpa_proactive.json 的 fingertipsUrl 字段读取
     */
    fun setUrl(url: String?) {
        fingertipsUrl = url?.trimEnd('/')
        if (fingertipsUrl != null) {
            Log.d(TAG, "fingertips URL set: $fingertipsUrl")
        }
    }
    
    /**
     * 在输入框内容变化时调用
     * @param text - 当前输入框内容
     */
    fun ping(text: String) {
        // 未配置URL，静默跳过
        if (fingertipsUrl == null) return
        
        // 空输入框不算在打字
        if (text.isBlank()) return
        
        val now = System.currentTimeMillis()
        // 节流：4秒内最多ping一次
        if (now - lastPingTime < PING_INTERVAL_MS) return
        
        lastPingTime = now
        
        // 异步fire-and-forget
        scope.launch {
            try {
                val url = "$fingertipsUrl/api/typing/ping"
                val request = Request.Builder()
                    .url(url)
                    .post("".toRequestBody())
                    .build()
                
                quickClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "ping ok")
                    } else {
                        Log.w(TAG, "ping failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                // 静默失败，不影响正常聊天
                Log.v(TAG, "ping error (silent): ${e.message}")
            }
        }
    }
}
