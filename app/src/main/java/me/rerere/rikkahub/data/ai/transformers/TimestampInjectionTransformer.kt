package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 当前時刻を system prompt に注入する。
 * ユーザーメッセージには一切触れない。
 * AI は system 経由で時刻を認知するため、対話内に [t:xx:xx] は表示されない。
 */
object TimestampInjectionTransformer : InputMessageTransformer {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (EEEE)")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val now = LocalDateTime.now().format(formatter)
        val timeContext = "\n\n[Current Time: $now]"

        return messages.map { msg ->
            if (msg.role == MessageRole.SYSTEM) {
                val currentText = msg.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }
                msg.copy(
                    parts = listOf(
                        UIMessagePart.Text(currentText + timeContext)
                    )
                )
            } else {
                msg
            }
        }
    }
}
