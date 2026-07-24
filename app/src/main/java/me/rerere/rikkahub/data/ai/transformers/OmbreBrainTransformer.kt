package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import kotlin.uuid.Uuid

private const val TAG = "OmbreBrainTransformer"
private const val MAX_INJECTION_LENGTH = 3000

/**
 * OmbreBrain 自動記憶注入 Transformer
 *
 * ユーザーがメッセージを送るたびに、自動でOBのbreathを呼び、
 * 関連記憶をsystem promptに注入する。
 * AIが自分でbreathを呼ぶ必要がなくなる。
 */
class OmbreBrainTransformer(
    private val mcpManager: McpManager,
) : InputMessageTransformer {

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        // 取用户最后一条消息的文本
        val lastUserMessage = messages.lastOrNull { it.role == MessageRole.USER }
        val userText = lastUserMessage?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString(" ") { it.text }
            ?.take(200)
            ?: return messages

        if (userText.isBlank()) return messages

        // 找到OmbreBrain的serverId
        val obServerInfo = findOmbreBrainServer() ?: return messages

        // 调用breath
        val memories = try {
            callBreath(obServerInfo.first, userText)
        } catch (e: Exception) {
            Log.w(TAG, "OB breath call failed: ${e.message}")
            return messages
        }

        if (memories.isBlank()) return messages

        Log.d(TAG, "Injecting OB memories (${memories.length} chars)")

        // 注入到system message末尾
        val injection = "[OmbreBrain 相关记忆]\n$memories\n[记忆结束]"

        return messages.map { msg ->
            if (msg.role == MessageRole.SYSTEM) {
                val currentText = msg.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }
                msg.copy(
                    parts = listOf(
                        UIMessagePart.Text(currentText + "\n\n" + injection)
                    )
                )
            } else {
                msg
            }
        }
    }

    private fun findOmbreBrainServer(): Pair<Uuid, String>? {
        // 在已注册的MCP tools中找OmbreBrain的breath
        val allTools = mcpManager.getAllAvailableTools()
        for ((serverId, serverName, tool) in allTools) {
            if (tool.name == "breath") {
                return Pair(serverId, tool.name)
            }
        }
        // 也匹配包含 "breath" 的工具名（防止前缀）
        for ((serverId, serverName, tool) in allTools) {
            if (tool.name.contains("breath", ignoreCase = true)) {
                return Pair(serverId, tool.name)
            }
        }
        return null
    }

    private suspend fun callBreath(serverId: Uuid, query: String): String {
        val args = buildJsonObject {
            put("query", query)
            put("max_tokens", MAX_INJECTION_LENGTH)
            put("max_results", 5)
        }

        return try {
            val result = kotlinx.coroutines.withTimeout(5000L) {
                mcpManager.callTool(serverId, "breath", args)
            }
            result
                .filterIsInstance<UIMessagePart.Text>()
                .joinToString("\n") { it.text }
                .take(MAX_INJECTION_LENGTH)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "OB breath timed out")
            ""
        }
    }
}
