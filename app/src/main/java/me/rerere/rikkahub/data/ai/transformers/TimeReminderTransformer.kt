package me.rerere.rikkahub.data.ai.transformers

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.toLocalDateTime
import kotlin.time.toJavaInstant

// 3 分钟阈值：短于此间隔视为同一次对话，不注入（避免连发消息刷屏时噪音）
private const val TIME_GAP_THRESHOLD_SECONDS = 180L

/**
 * 时间提醒注入转换器
 *
 * 在每条用户消息之前注入一条 SYSTEM 消息，告知 AI：
 *  - 当前时间（yyyy-MM-dd HH:mm，不带星期）
 *  - 距上一条用户消息经过的时间（human readable）
 *
 * 注入为 SYSTEM role 而不是 USER role，避免 AI 误认为是用户手打内容。
 * 前后连发（间隔 < 3 分钟）不注入，避免同一次对话被噪音刷屏。
 */
object TimeReminderTransformer : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.enableTimeReminder) return messages
        return applyTimeReminder(messages)
    }
}

internal fun applyTimeReminder(messages: List<UIMessage>): List<UIMessage> {
    val result = mutableListOf<UIMessage>()
    val tz = TimeZone.currentSystemDefault()

    var firstUserFound = false
    for (i in messages.indices) {
        val current = messages[i]
        if (current.role == MessageRole.USER) {
            val currInstant = current.createdAt.toInstant(tz)
            if (!firstUserFound) {
                firstUserFound = true
                result.add(buildTimeReminderMessage(null, currInstant))
            } else {
                // 找到最近的一条 USER 消息作为“上一条”，避免连续 assistant 消息干扰计算
                val prevUserInstant = findPreviousUserInstant(messages, i, tz)
                if (prevUserInstant != null) {
                    val gapSeconds = (currInstant - prevUserInstant).inWholeSeconds
                    if (gapSeconds > TIME_GAP_THRESHOLD_SECONDS) {
                        result.add(buildTimeReminderMessage(gapSeconds, currInstant))
                    }
                }
            }
        }
        result.add(current)
    }

    return result
}

private fun findPreviousUserInstant(
    messages: List<UIMessage>,
    currentIndex: Int,
    tz: TimeZone,
): Instant? {
    for (j in (currentIndex - 1) downTo 0) {
        if (messages[j].role == MessageRole.USER) {
            return messages[j].createdAt.toInstant(tz)
        }
    }
    return null
}

private fun buildTimeReminderMessage(gapSeconds: Long?, instant: Instant): UIMessage {
    val javaInstant = instant.toJavaInstant()
    val timeStr = javaInstant.toLocalDateTime()
    val content = if (gapSeconds != null) {
        val gapText = formatGap(gapSeconds)
        "<time_reminder>系统注入·仅AI可见 | 当前时间: $timeStr | 距用户上条消息: $gapText</time_reminder>"
    } else {
        "<time_reminder>系统注入·仅AI可见 | 当前时间: $timeStr</time_reminder>"
    }
    return UIMessage(
        role = MessageRole.SYSTEM,
        parts = listOf(UIMessagePart.Text(content)),
    )
}

private fun formatGap(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}秒"
        seconds < 3600 -> "${seconds / 60}分钟"
        seconds < 86400 -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m == 0L) "${h}小时" else "${h}小时${m}分钟"
        }
        else -> {
            val d = seconds / 86400
            val h = (seconds % 86400) / 3600
            if (h == 0L) "${d}天" else "${d}天${h}小时"
        }
    }
}
