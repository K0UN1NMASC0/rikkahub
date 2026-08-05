package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant

/**
 * 表情包白名单自动注入转换器
 *
 * 从助手绑定的快捷消息中提取所有含"表情包:"格式的条目，
 * 自动生成白名单并注入到 system prompt 末尾，
 * 使 AI 知道当前可用的表情包 ID 列表。
 *
 * 这样用户添加新表情包时，只需在快捷消息中添加，
 * 不需要再手动更新提示词中的表情包列表。
 */
object StickerListTransformer : InputMessageTransformer {

    // 匹配快捷消息中的 (表情包:xxx) 格式，提取 xxx 部分
    private val stickerPattern = Regex("""\(表情包:(.+?)\)""")

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val quickMessages = ctx.settings.getQuickMessagesOfAssistant(ctx.assistant)
        if (quickMessages.isEmpty()) return messages

        // 从快捷消息的 content 中提取表情包 ID
        val stickerIds = quickMessages.mapNotNull { qm ->
            stickerPattern.find(qm.content)?.groupValues?.getOrNull(1)
        }.distinct()

        if (stickerIds.isEmpty()) return messages

        // 构建注入文本
        val stickerList = stickerIds.joinToString("/")
        val injection = "\n\n<sticker_list>可用表情包(格式:(表情包:ID)，只能从此列表选，不能编造): $stickerList</sticker_list>"

        // 注入到第一条 SYSTEM 消息末尾
        var injected = false
        return messages.map { msg ->
            if (!injected && msg.role == MessageRole.SYSTEM) {
                injected = true
                val currentText = msg.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }
                msg.copy(
                    parts = listOf(
                        UIMessagePart.Text(currentText + injection)
                    )
                )
            } else {
                msg
            }
        }
    }
}
