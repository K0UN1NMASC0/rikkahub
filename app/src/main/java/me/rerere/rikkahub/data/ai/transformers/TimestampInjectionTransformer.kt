package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 每条用户消息末尾自动追加24小时制时间戳 [t:HH:mm]。
 * 只在发给AI的payload中注入，不保存到本地对话历史，UI上不可见。
 * 这样AI可以感知用户发消息的精确时间。
 */
object TimestampInjectionTransformer : InputMessageTransformer {

    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val now = LocalDateTime.now().format(formatter)
        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex == -1) return messages

        return messages.mapIndexed { index, msg ->
            if (index == lastUserIndex && msg.role == MessageRole.USER) {
                val parts = msg.parts.toMutableList()
                // 找到最后一个Text part，在末尾追加时间戳
                val lastTextIndex = parts.indexOfLast { it is UIMessagePart.Text }
                if (lastTextIndex >= 0) {
                    val textPart = parts[lastTextIndex] as UIMessagePart.Text
                    parts[lastTextIndex] = UIMessagePart.Text(
                        textPart.text + "\n[t:$now]"
                    )
                } else {
                    // 没有text part就新加一个
                    parts.add(UIMessagePart.Text("[t:$now]"))
                }
                msg.copy(parts = parts)
            } else {
                msg
            }
        }
    }
}
