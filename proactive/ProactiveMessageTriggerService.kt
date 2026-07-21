package me.rerere.rikkahub.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.sendNotification
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

class ProactiveMessageTriggerService : Service() {

    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "ProactiveTrigger"
        private const val CONFIG_FILE = "tulpa_proactive.json"
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Tulpa")
            .setSmallIcon(me.rerere.rikkahub.R.drawable.small_icon)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(20001, notification)

        scope.launch {
            try {
                trigger()
            } catch (e: Exception) {
                Log.e(TAG, "Proactive message failed", e)
            } finally {
                val intervalPrefs = getSharedPreferences("proactive_settings", Context.MODE_PRIVATE)
                val interval = intervalPrefs.getInt("proactive_interval", 180)
                ProactiveMessageReceiver.schedule(this@ProactiveMessageTriggerService, interval, interval)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun readConfig(): JSONObject {
        return try {
            val file = File(Environment.getExternalStorageDirectory(), CONFIG_FILE)
            if (file.exists()) {
                JSONObject(file.readText())
            } else {
                // 也尝试 Documents 文件夹
                val file2 = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), CONFIG_FILE)
                if (file2.exists()) {
                    JSONObject(file2.readText())
                } else {
                    Log.w(TAG, "Config file not found at ${file.absolutePath} or ${file2.absolutePath}")
                    JSONObject()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read config", e)
            JSONObject()
        }
    }

    private suspend fun trigger() {
        val prefs = getSharedPreferences("proactive_settings", Context.MODE_PRIVATE)
        val config = readConfig()
        val baseUrl = prefs.getString("proactive_base_url", "") ?: ""
        val apiKey = prefs.getString("proactive_api_key", "") ?: ""
        val modelId = prefs.getString("proactive_model_id", "") ?: ""

        if (baseUrl.isBlank() || apiKey.isBlank() || modelId.isBlank()) {
            Log.w(TAG, "Config incomplete: baseUrl=$baseUrl, apiKey=${apiKey.take(5)}..., modelId=$modelId")
            return
        }

        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getCurrentAssistant()

        // 读最近对话
        val recentConvs = conversationRepository.getRecentConversations(assistant.id, limit = 1)
        val conversation = recentConvs.firstOrNull()

        // 距上次聊天时间
        val lastUpdateAt = conversation?.updateAt
        val idleMinutes = if (lastUpdateAt != null) {
            ((System.currentTimeMillis() - lastUpdateAt.toEpochMilli()) / 60000L).toInt()
        } else 9999

        val currentTimeStr = SimpleDateFormat("yyyy年MM月dd日 HH:mm EEEE", Locale.CHINESE).format(Date())

        // 历史消息（最近10条）
        val historyMessages: List<UIMessage> = conversation?.messageNodes
            ?.flatMap { it.messages }
            ?.takeLast(10)
            ?: emptyList()

        // 构建 system prompt
        val systemPrompt = buildString {
            if (assistant.systemPrompt.isNotBlank()) {
                appendLine(assistant.systemPrompt)
                appendLine()
            }
            appendLine("<time_reminder>现在时间：$currentTimeStr</time_reminder>")
            appendLine()
            appendLine("## 主动消息")
            appendLine("距上次聊天约${idleMinutes}分钟。")
            appendLine("你现在可以主动给用户发一条消息。")
            appendLine("规则：")
            appendLine("- 没什么好说的，或用户刚说了去睡觉且不到5小时 → 只回复 [PASS]")
            appendLine("- 不要复述上一轮内容，发新话题或自然的关心")
            appendLine("- 不要提及你是定时触发的，像突然想到什么一样开口")
            appendLine("- 直接输出想说的话，不要加任何解释")
        }

        // 构建请求
        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            historyMessages.forEach { msg ->
                val role = when (msg.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    else -> return@forEach
                }
                val text = msg.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }.trim()
                if (text.isNotBlank()) {
                    put(JSONObject().put("role", role).put("content", text))
                }
            }
            put(JSONObject().put("role", "user").put("content", "请决定是否发消息。没话说就回复 [PASS]。"))
        }

        val body = JSONObject().put("model", modelId)
            .put("messages", messages)
            .put("max_tokens", 300)
            .put("temperature", 0.9)
            .toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        Log.d(TAG, "Calling API: model=$modelId, history=${historyMessages.size}, idle=${idleMinutes}min")

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "API error: ${response.code} ${response.body?.string()}")
            return
        }

        val responseText = response.body?.string() ?: return
        val replyText = JSONObject(responseText).getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        Log.d(TAG, "AI reply: ${replyText.take(80)}")

        if (replyText.isBlank() || replyText.contains("[PASS]", ignoreCase = true)) {
            Log.d(TAG, "AI chose to skip")
            return
        }

        // 写入对话历史
        val aiMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text(replyText))
        )
        val aiNode = aiMessage.toMessageNode()

        if (conversation != null) {
            val updatedConv = conversation.copy(
                messageNodes = conversation.messageNodes + aiNode,
                updateAt = Instant.now()
            )
            conversationRepository.updateConversation(updatedConv)
        } else {
            val newConv = Conversation(
                id = Uuid.random(),
                assistantId = assistant.id,
                title = "",
                messageNodes = listOf(aiNode),
                createAt = Instant.now(),
                updateAt = Instant.now()
            )
            conversationRepository.insertConversation(newConv)
        }

        // 发通知
        val convId = conversation?.id ?: Uuid.random()
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("conversationId", convId.toString())
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 20002
        ) {
            title = assistant.name.ifBlank { "Tulpa" }
            content = replyText.take(100)
            autoCancel = true
            useDefaults = true
            contentIntent = pendingIntent
        }

        Log.d(TAG, "Proactive message saved and notified")
    }
}
