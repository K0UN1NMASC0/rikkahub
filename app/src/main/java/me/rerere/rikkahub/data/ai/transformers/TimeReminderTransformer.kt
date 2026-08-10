package me.rerere.rikkahub.data.ai.transformers

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.utils.toLocalDateTime
import kotlin.time.toJavaInstant

// 显著时间事件阈值：距上条用户消息超过 2 小时才注入“经过时间”。
// 灵感来自 See-Sol-Lab / Time Anchor：普通短间隔对话不该反复把时间塞进上下文，
// 否则 AI 会一直“看得见表”，变成机械报时机。只有真正长的间隔（离开、睡一觉、
// 忙别的事回来）才是值得 AI 感知的显著时间事件。
private const val TIME_GAP_THRESHOLD_SECONDS = 7200L

/**
 * 时间提醒注入转换器（v2 · Time Anchor 思路）
 *
 * 设计原则：**注意力是关系性的**——只在时间真正可能改变“此刻这句话的含义”时，
 * 才让现实时间进入上下文。而不是每一轮都强制注入。
 *
 * 注入时机（满足任一即注入，SYSTEM role）：
 *  1. 每个对话的第一条用户消息 —— 建立时间基准（只给当前时间，不给经过时间）。
 *  2. 距上一条用户消息 **超过 2 小时** —— 显著的长间隔（离开/睡觉/忙别的事回来）。
 *  3. 与上一条用户消息 **跨越了本地日期**（隔天回来）—— 哪怕间隔不足 2 小时，
 *     跨天本身就是有意义的时间事件。
 *
 * 普通短间隔的连续对话（几分钟一条）**完全不注入时间**，
 * 让 AI 平时想不起时间，避免无意义地报时、催作息。
 *
 * 注入为 SYSTEM role 而不是 USER role，避免 AI 误认为是用户手打内容。
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
                // 对话的第一条用户消息：建立时间基准
                result.add(buildTimeReminderMessage(null, currInstant))
            } else {
                // 找到最近的一条 USER 消息作为“上一条”，避免连续 assistant 消息干扰计算
                val prevUserInstant = findPreviousUserInstant(messages, i, tz)
                if (prevUserInstant != null) {
                    val gapSeconds = (currInstant - prevUserInstant).inWholeSeconds
                    val crossedDate = isDifferentLocalDate(prevUserInstant, currInstant, tz)
                    // 只在“显著时间事件”时注入：长间隔（>2h）或跨天
                    if (gapSeconds > TIME_GAP_THRESHOLD_SECONDS || crossedDate) {
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

/** 判断两个时刻是否落在不同的本地日期（跨天）。 */
private fun isDifferentLocalDate(a: Instant, b: Instant, tz: TimeZone): Boolean {
    return a.toLocalDateTime(tz).date != b.toLocalDateTime(tz).date
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
