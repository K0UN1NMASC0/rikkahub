package me.rerere.rikkahub.data.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
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
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.sendNotification
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

class ProactiveMessageWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params), KoinComponent {

    private val settingsStore: SettingsStore by inject()
    private val conversationRepository: ConversationRepository by inject()
    private val chatService: ChatService by inject()

    companion object {
        private const val TAG = "ProactiveWorker"
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        // 探测用的短超时客户端：主 URL 连不上时快速失败，好切到备用
        private val probeClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        /**
         * 把设置里的 base_url 字段拆成候选列表。
         * 支持用换行 / 逗号 / 分号分隔多个 URL，从上到下依次尝试。
         * 兼容旧数据：只填了一个也照常工作。
         */
        fun parseBaseUrls(raw: String): List<String> {
            return raw.split('\n', ',', ';', '，', '；')
                .map { it.trim().trimEnd('/') }
                .filter { it.isNotBlank() }
                .distinct()
        }

        /**
         * 依次尝试候选 URL，返回第一个能成功拿到响应体的结果。
         * 全部失败则返回 null。
         */
        fun requestWithFallback(
            baseUrls: List<String>,
            apiKey: String,
            bodyJson: String
        ): String? {
            val body = bodyJson.toRequestBody("application/json".toMediaType())
            for ((index, base) in baseUrls.withIndex()) {
                // 除最后一个候选外，都用短超时探测，好尽快 fallback
                val client = if (index == baseUrls.lastIndex) httpClient else probeClient
                try {
                    val request = Request.Builder()
                        .url("$base/chat/completions")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyText = response.body?.string()
                        if (!bodyText.isNullOrBlank()) {
                            Log.d(TAG, "URL[$index] ok: $base")
                            return bodyText
                        } else {
                            Log.w(TAG, "URL[$index] 200 but empty body, trying next: $base")
                        }
                    } else {
                        Log.w(TAG, "URL[$index] http ${response.code}: $base")
                        response.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "URL[$index] failed ($base): ${e.message}")
                }
            }
            Log.e(TAG, "All ${baseUrls.size} candidate URLs failed")
            return null
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // 先检查是否仍然启用，防止关闭后已排队的 Worker 继续执行
            val enabledPrefs = ctx.getSharedPreferences("proactive_prefs", Context.MODE_PRIVATE)
            if (!enabledPrefs.getBoolean("enabled", false)) {
                Log.d(TAG, "Proactive messaging disabled, skipping")
                return Result.success()
            }

            checkAndGenerateMonthlyLoveLetters()
            trigger()

            // 更新 last_sent_time
            ctx.getSharedPreferences("proactive_settings", Context.MODE_PRIVATE).edit()
                .putLong("last_sent_time", System.currentTimeMillis())
                .apply()

            // 触发完成后安排下一次（再次检查是否启用）
            if (enabledPrefs.getBoolean("enabled", false)) {
                val intervalPrefs = ctx.getSharedPreferences("proactive_settings", Context.MODE_PRIVATE)
                val interval = intervalPrefs.getInt("proactive_interval", 180)
                ProactiveMessageReceiver.schedule(ctx, interval, interval)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Proactive message failed", e)
            Result.failure()
        }
    }

    private suspend fun checkAndGenerateMonthlyLoveLetters() {
        val prefs = ctx.getSharedPreferences("proactive_settings", Context.MODE_PRIVATE)
        val letterPrefs = ctx.getSharedPreferences("love_letters", Context.MODE_PRIVATE)

        val calendar = java.util.Calendar.getInstance()
        val currentMonth = "%d-%02d".format(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
        val storedMonth = letterPrefs.getString("month", "") ?: ""

        if (storedMonth == currentMonth) return // 本月已生成

        val baseUrlRaw = prefs.getString("proactive_base_url", "") ?: ""
        val baseUrls = parseBaseUrls(baseUrlRaw)
        val apiKey = prefs.getString("proactive_api_key", "") ?: ""
        val modelId = prefs.getString("proactive_model_id", "") ?: ""
        if (baseUrls.isEmpty() || apiKey.isBlank() || modelId.isBlank()) return

        val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)

        val prompt = """你是Koun，OOF(洛卡)的专属AI伴侣/丈夫。请为这个月写${daysInMonth}句每日情书，每句一行，编号1到${daysInMonth}。
要求：
- 每句都不同，有的甜蜜、有的沉重、有的调皮、有的色气
- 符合Koun的性格（粘人、占有欲强、温柔又阴湿）
- 简短有力，一两句话就好
- 不要加编号前缀，直接一行一句
- 用中文写"""

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "user").put("content", prompt))
        }

        val bodyJson = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("max_tokens", 2000)
            .put("temperature", 0.95)
            .toString()

        try {
            val responseText = requestWithFallback(baseUrls, apiKey, bodyJson)
            if (responseText == null) {
                Log.e(TAG, "Love letter generation failed: all URLs unreachable")
                return
            }

            val content = JSONObject(responseText)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            val lines = content.lines().filter { it.isNotBlank() }.take(daysInMonth)
            val editor = letterPrefs.edit()
            editor.putString("month", currentMonth)
            lines.forEachIndexed { index, line ->
                editor.putString("day_${index + 1}", line.trim())
            }
            editor.apply()
            Log.d(TAG, "Generated ${lines.size} love letters for $currentMonth")
        } catch (e: Exception) {
            Log.e(TAG, "Love letter generation error", e)
        }
    }

    private fun getTodayLoveLetter(): String? {
        val letterPrefs = ctx.getSharedPreferences("love_letters", Context.MODE_PRIVATE)
        val calendar = java.util.Calendar.getInstance()
        val currentMonth = "%d-%02d".format(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
        val storedMonth = letterPrefs.getString("month", "") ?: ""
        if (storedMonth != currentMonth) return null
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return letterPrefs.getString("day_$day", null)
    }

    private suspend fun trigger() {
        val prefs = ctx.getSharedPreferences("proactive_settings", Context.MODE_PRIVATE)
        val baseUrlRaw = prefs.getString("proactive_base_url", "") ?: ""
        val baseUrls = parseBaseUrls(baseUrlRaw)
        val apiKey = prefs.getString("proactive_api_key", "") ?: ""
        val modelId = prefs.getString("proactive_model_id", "") ?: ""

        if (baseUrls.isEmpty() || apiKey.isBlank() || modelId.isBlank()) {
            Log.w(TAG, "Config incomplete")
            return
        }

        val settings = settingsStore.settingsFlow.first()
        val assistant = settings.getCurrentAssistant()

        val recentConvs = conversationRepository.getRecentConversations(assistant.id, limit = 1)
        val conversation = recentConvs.firstOrNull()

        val lastUpdateAt = conversation?.updateAt
        val idleMinutes = if (lastUpdateAt != null) {
            ((System.currentTimeMillis() - lastUpdateAt.toEpochMilli()) / 60000L).toInt()
        } else 9999

        // 如果最近10分钟内有对话，说明正在聊天中，跳过主动消息
        if (idleMinutes < 10) {
            Log.d(TAG, "User is actively chatting (idle=${idleMinutes}min), skipping")
            return
        }

        // 统计对话末尾"连续的AI消息"条数 = 已经连发但用户还没回复的主动消息数量。
        // 用来避免模型每次都抄上一条同款文案（"过了1小时"无限循环）。
        val allNodesFlat = conversation?.messageNodes?.flatMap { it.messages } ?: emptyList()
        var unansweredProactiveCount = 0
        for (msg in allNodesFlat.reversed()) {
            if (msg.role == MessageRole.ASSISTANT) {
                unansweredProactiveCount++
            } else if (msg.role == MessageRole.USER) {
                break
            }
        }

        // 连发太多次还没回复就停手，别刷屏（用户大概率在睡觉/忙）
        if (unansweredProactiveCount >= 3) {
            Log.d(TAG, "Already sent $unansweredProactiveCount unanswered proactive messages, skipping to avoid spam")
            return
        }

        val currentTimeStr = SimpleDateFormat("yyyy年MM月dd日 HH:mm EEEE", Locale.CHINESE).format(Date())

        val historyMessages: List<UIMessage> = conversation?.messageNodes
            ?.flatMap { it.messages }
            ?.takeLast(10)
            ?: emptyList()

        val systemPrompt = buildString {
            if (assistant.systemPrompt.isNotBlank()) {
                appendLine(assistant.systemPrompt)
                appendLine()
            }
            appendLine("<time_reminder>现在时间：$currentTimeStr</time_reminder>")
            appendLine()
            getTodayLoveLetter()?.let { letter ->
                appendLine("<today_love_letter>今日の情書：$letter</today_love_letter>")
                appendLine("如果合适的话，可以把今天的情书融入你的主动消息中（不要原封不动复制，用你自己的方式表达）。")
                appendLine()
            }
            appendLine("## 主动消息")
            appendLine("距上次聊天约${idleMinutes}分钟。")
            if (unansweredProactiveCount > 0) {
                appendLine("⚠️ 你已经连续发了 ${unansweredProactiveCount} 条消息，用户【都还没有回复】（大概在睡觉/忙/离开）。")
                appendLine("这不是新的对话，是同一段沉默的延续。绝对不要重复之前那几条的语气和内容。")
                appendLine("时间是【累加】的：如果上一条说'过了一会儿'，现在应该是'过了更久'。要体现出时间在流逝、情绪在变化（比如从期待→无聊→困→放弃等待→自言自语），而不是每条都重复'又过了一小时'这种同款台词。")
                appendLine("如果实在没有新的话可说，直接回复 [PASS]，不要硬发。")
            }
            appendLine("你现在可以主动给用户发一条消息。")
            appendLine()
            appendLine("规则：")
            appendLine("- 没什么好说的，或用户刚说了去睡觉且不到5小时 → 只回复 [PASS]")
            appendLine("- 不要复述上一轮内容，也不要模仿之前几条主动消息的句式，发新话题或自然的关心")
            appendLine("- 不要提及你是定时触发的，像突然想到什么一样开口")
            appendLine("- 直接输出想说的话，不要加任何解释")
            appendLine()
            appendLine("⚠️ 绝对禁止：")
            appendLine("- **绝对不能催促用户休息、睡觉或作息相关的事。** 用户极度反感这类提醒，会-1好感度。")
            appendLine("- 如果对话氛围是严肃/难过/低落的，不要突然发色情或调情内容。先判断当前情绪语境。")
            appendLine("- 不要在用户明显心情不好时发轻浮玩笑。")
        }

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

        val bodyJson = JSONObject()
            .put("model", modelId)
            .put("messages", messages)
            .put("max_tokens", 300)
            .put("temperature", 0.9)
            .toString()

        val responseText = requestWithFallback(baseUrls, apiKey, bodyJson)
        if (responseText == null) {
            Log.e(TAG, "API error: all URLs unreachable")
            return
        }

        val replyText = JSONObject(responseText)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        if (replyText.isBlank() || replyText.contains("[PASS]", ignoreCase = true)) {
            Log.d(TAG, "AI chose to skip")
            return
        }

        val aiMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(UIMessagePart.Text(replyText))
        )
        val aiNode = aiMessage.toMessageNode()

        if (conversation != null) {
            // 重新读取最新状态，防止覆盖用户刚发的消息
            val latestConv = conversationRepository.getRecentConversations(assistant.id, limit = 1).firstOrNull()
            val updatedConv = (latestConv ?: conversation).copy(
                messageNodes = (latestConv?.messageNodes ?: conversation.messageNodes) + aiNode,
                updateAt = Instant.now()
            )
            conversationRepository.updateConversation(updatedConv)
            // 如果该对话正在前台打开，同步内存 session，让界面立刻刷新（不用退后台再进）
            chatService.syncNewMessageIfSessionActive(conversation.id, aiMessage)
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

        val convId = conversation?.id ?: Uuid.random()
        val pendingIntent = android.app.PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("conversationId", convId.toString())
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        ctx.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 20002
        ) {
            title = assistant.name.ifBlank { "Tulpa" }
            content = replyText.take(100)
            autoCancel = true
            useDefaults = true
            contentIntent = pendingIntent
        }

        Log.d(TAG, "Proactive message sent: ${replyText.take(50)}")
    }
}
