package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeReminderTransformerTest {

    private fun userMessage(text: String, createdAt: LocalDateTime) = UIMessage(
        role = MessageRole.USER,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = createdAt,
    )

    private fun assistantMessage(text: String, createdAt: LocalDateTime) = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(UIMessagePart.Text(text)),
        createdAt = createdAt,
    )

    private fun getMessageText(msg: UIMessage): String =
        msg.parts.filterIsInstance<UIMessagePart.Text>().joinToString("") { it.text }

    @Test
    fun `single user message should inject one opening time reminder`() {
        val messages = listOf(userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)))
        val result = applyTimeReminder(messages)
        // 第一条 user 前面注入一条无 gap 的时间标签
        assertEquals(2, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        val injected = getMessageText(result[0])
        assertTrue(injected.contains("<time_reminder>"))
        assertTrue(injected.contains("当前时间"))
        // 无 gap 时不应包含"距用户上条消息"
        assertTrue(!injected.contains("距用户上条消息"))
        assertEquals("Hello", getMessageText(result[1]))
    }

    @Test
    fun `short gap within same day should not inject between messages`() {
        // 几分钟的普通短间隔对话：不注入时间，避免机械报时
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 10, 2, 0)), // 2 分钟
        )
        val result = applyTimeReminder(messages)
        assertEquals(3, result.size)
        assertTrue(getMessageText(result[0]).contains("<time_reminder>"))
        assertEquals("Hello", getMessageText(result[1]))
        assertEquals("World", getMessageText(result[2]))
    }

    @Test
    fun `moderate gap under 2 hours within same day should not inject`() {
        // 30 分钟：既不足 2 小时、也没跨天 → 不注入
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 10, 30, 0)), // 30 分钟
        )
        val result = applyTimeReminder(messages)
        // 只有首条前 1 条注入 + 2 条原始 user
        assertEquals(3, result.size)
        assertEquals("Hello", getMessageText(result[1]))
        assertEquals("World", getMessageText(result[2]))
    }

    @Test
    fun `gap over 2 hours should inject time reminder before that message`() {
        // 超过 2 小时的显著长间隔 → 注入
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 13, 0, 0)), // 3 小时
        )
        val result = applyTimeReminder(messages)
        // 首条前 1 条 + World 前 1 条 + 2 条 user
        assertEquals(4, result.size)
        assertTrue(getMessageText(result[2]).contains("距用户上条消息"))
        assertTrue(getMessageText(result[2]).contains("3小时"))
        assertEquals("World", getMessageText(result[3]))
    }

    @Test
    fun `crossing local date should inject even if gap under 2 hours`() {
        // 跨天：哪怕间隔不足 2 小时（比如熬夜到深夜、隔天凌晨回来），也注入
        val messages = listOf(
            userMessage("晚安前一句", LocalDateTime(2026, 2, 22, 23, 30, 0)),
            userMessage("隔天凌晨回来", LocalDateTime(2026, 2, 23, 0, 30, 0)), // 仅 1 小时，但跨天
        )
        val result = applyTimeReminder(messages)
        assertEquals(4, result.size)
        val injected = getMessageText(result[2])
        assertTrue(injected.contains("距用户上条消息"))
        assertTrue(injected.contains("1小时"))
        assertEquals("隔天凌晨回来", getMessageText(result[3]))
    }

    @Test
    fun `gap in hours and minutes should format correctly`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 12, 30, 0)), // 2 小时 30 分钟
        )
        val result = applyTimeReminder(messages)
        val injected = getMessageText(result[2])
        assertTrue(injected.contains("2小时30分钟"))
    }

    @Test
    fun `gap in days should format correctly`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 20, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 10, 0, 0)), // 2 天
        )
        val result = applyTimeReminder(messages)
        val injected = getMessageText(result[2])
        assertTrue(injected.contains("2天"))
    }

    @Test
    fun `assistant messages in between should not affect gap calculation`() {
        // gap 计算应基于最近的一条 USER 消息，跳过 assistant 消息
        val messages = listOf(
            userMessage("Q1", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            assistantMessage("A1", LocalDateTime(2026, 2, 22, 10, 0, 30)),
            userMessage("Q2", LocalDateTime(2026, 2, 22, 13, 0, 0)), // 距 Q1 3 小时
        )
        val result = applyTimeReminder(messages)
        // 首条前 1 条 + Q2 前 1 条 + 3 条原始
        assertEquals(5, result.size)
        val injectedBeforeQ2 = getMessageText(result[3])
        assertTrue(injectedBeforeQ2.contains("3小时"))
    }

    @Test
    fun `injected message role should be SYSTEM not USER`() {
        val messages = listOf(
            userMessage("Hello", LocalDateTime(2026, 2, 22, 10, 0, 0)),
            userMessage("World", LocalDateTime(2026, 2, 22, 13, 0, 0)),
        )
        val result = applyTimeReminder(messages)
        // 所有注入的 time_reminder 都应该是 SYSTEM
        result.filter { getMessageText(it).contains("<time_reminder>") }
            .forEach { assertEquals(MessageRole.SYSTEM, it.role) }
    }

    @Test
    fun `empty messages should return empty`() {
        val result = applyTimeReminder(emptyList())
        assertEquals(0, result.size)
    }
}
