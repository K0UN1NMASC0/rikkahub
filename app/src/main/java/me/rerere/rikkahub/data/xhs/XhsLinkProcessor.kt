package me.rerere.rikkahub.data.xhs

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "XhsLinkProcessor"

/**
 * 小红书链接解析器
 * 检测用户消息中的小红书链接，自动抓取笔记数据并将图片转为base64
 */
object XhsLinkProcessor {

    private val XHS_URL_PATTERN = Regex(
        """(https?://)?(www\.)?(xiaohongshu\.com/discovery/item/|xiaohongshu\.com/explore/|xhslink\.com/|xhslink\.cn/)[^\s]+""",
        RegexOption.IGNORE_CASE
    )

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 检测文本中是否包含小红书链接
     */
    fun containsXhsLink(text: String): Boolean {
        return XHS_URL_PATTERN.containsMatchIn(text)
    }

    /**
     * 处理用户消息中的小红书链接
     * 返回增强后的 parts 列表（原文本 + XHS结构化信息 + 图片base64）
     */
    suspend fun processMessageParts(parts: List<UIMessagePart>): List<UIMessagePart> = coroutineScope {
        val textPart = parts.filterIsInstance<UIMessagePart.Text>().firstOrNull() ?: return@coroutineScope parts
        val text = textPart.text

        if (!containsXhsLink(text)) return@coroutineScope parts

        val url = XHS_URL_PATTERN.find(text)?.value ?: return@coroutineScope parts
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url

        try {
            val noteData = fetchNoteData(fullUrl) ?: return@coroutineScope parts
            val enrichedParts = mutableListOf<UIMessagePart>()

            // 保留原始文本
            enrichedParts.addAll(parts)

            // 添加结构化笔记信息
            val noteText = buildNoteText(noteData)
            enrichedParts.add(UIMessagePart.Text(noteText))

            // 下载图片并转base64（最多5张）
            val imageUrls = noteData.images.take(5)
            if (imageUrls.isNotEmpty()) {
                val imageResults = imageUrls.map { imageUrl ->
                    async(Dispatchers.IO) {
                        downloadImageAsBase64(imageUrl)
                    }
                }.awaitAll()

                imageResults.filterNotNull().forEach { base64Data ->
                    enrichedParts.add(UIMessagePart.Image(url = base64Data))
                }
            }

            enrichedParts
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process XHS link: $fullUrl", e)
            parts // 失败时返回原始parts，不影响正常发送
        }
    }

    /**
     * 抓取小红书笔记数据
     */
    private suspend fun fetchNoteData(url: String): XhsNoteData? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null

            parseInitialState(html)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch note data", e)
            null
        }
    }

    /**
     * 从HTML中解析 __INITIAL_STATE__
     */
    private fun parseInitialState(html: String): XhsNoteData? {
        // 直接定位 window.__INITIAL_STATE__= 后的JSON
        val marker = "window.__INITIAL_STATE__="
        val startIdx = html.indexOf(marker)
        if (startIdx == -1) return null

        val jsonStartIdx = startIdx + marker.length
        val scriptEndIdx = html.indexOf("</script>", jsonStartIdx)
        if (scriptEndIdx == -1) return null

        val jsonStr = html.substring(jsonStartIdx, scriptEndIdx)
            .trim()
            .trimEnd(';')
            .replace("\\u002F", "/")
            .replace("undefined", "null")

        return try {
            val jsonObj = json.parseToJsonElement(jsonStr).jsonObject
            tryGetNoteData(jsonObj)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse __INITIAL_STATE__", e)
            null
        }
    }

    private fun tryGetNoteData(state: JsonObject): XhsNoteData? {
        try {
            // 路径1: noteData.data.noteData (新版移动端页面)
            val noteDataSection = state["noteData"]?.jsonObject
            val noteObj = noteDataSection
                ?.get("data")?.jsonObject
                ?.get("noteData")?.jsonObject

            // 路径2: note.noteDetailMap.{noteId}.note (旧版)
            val fallbackNoteObj = if (noteObj == null) {
                val noteSection = state["note"]?.jsonObject
                val noteDetailMap = noteSection?.get("noteDetailMap")?.jsonObject
                val firstEntry = noteDetailMap?.entries?.firstOrNull()
                firstEntry?.value?.jsonObject?.get("note")?.jsonObject
            } else null

            val finalNoteObj = noteObj ?: fallbackNoteObj ?: return null

            val title = finalNoteObj["title"]?.jsonPrimitive?.content ?: ""
            val desc = finalNoteObj["desc"]?.jsonPrimitive?.content ?: ""
            val user = finalNoteObj["user"]?.jsonObject
            val nickname = user?.get("nickname")?.jsonPrimitive?.content
                ?: user?.get("nick_name")?.jsonPrimitive?.content
                ?: ""

            // 图片列表
            val imageList = finalNoteObj["imageList"]?.jsonArray
            val images = imageList?.mapNotNull { imgElement ->
                val imgObj = imgElement.jsonObject
                val imgUrl = imgObj["urlDefault"]?.jsonPrimitive?.content
                    ?: imgObj["url"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                if (imgUrl.isBlank()) return@mapNotNull null
                normalizeImageUrl(imgUrl)
            } ?: emptyList()

            // 互动数据
            val interactInfo = finalNoteObj["interactInfo"]?.jsonObject
            val likedCount = interactInfo?.get("likedCount")?.jsonPrimitive?.content ?: "0"
            val commentCount = interactInfo?.get("commentCount")?.jsonPrimitive?.content ?: "0"
            val collectedCount = interactInfo?.get("collectedCount")?.jsonPrimitive?.content ?: "0"

            // 评论（首屏）- 尝试多种路径
            val comments = mutableListOf<String>()
            try {
                val commentData = noteDataSection
                    ?.get("data")?.jsonObject
                    ?.get("commentData")?.jsonObject
                    ?.get("comments")?.jsonArray
                    ?: state["comment"]?.jsonObject?.get("comments")?.jsonArray

                commentData?.take(5)?.forEach { commentElement ->
                    val commentObj = commentElement.jsonObject
                    val commentUser = commentObj["userInfo"]?.jsonObject
                        ?.get("nickname")?.jsonPrimitive?.content ?: ""
                    val commentContent = commentObj["content"]?.jsonPrimitive?.content ?: ""
                    if (commentContent.isNotBlank()) {
                        comments.add("$commentUser: $commentContent")
                    }
                }
            } catch (_: Exception) {}

            return XhsNoteData(
                title = title,
                desc = desc,
                author = nickname,
                images = images,
                likedCount = likedCount,
                commentCount = commentCount,
                collectedCount = collectedCount,
                comments = comments
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract note data from state", e)
            return null
        }
    }

    private fun normalizeImageUrl(url: String): String {
        var normalized = url
        if (normalized.startsWith("//")) {
            normalized = "https:$normalized"
        }
        if (!normalized.startsWith("http")) {
            normalized = "https://$normalized"
        }
        return normalized
    }

    /**
     * 构建结构化笔记文本
     */
    private fun buildNoteText(note: XhsNoteData): String {
        return buildString {
            appendLine("\n--- 小红书笔记内容 ---")
            if (note.title.isNotBlank()) appendLine("标题: ${note.title}")
            appendLine("作者: ${note.author}")
            if (note.desc.isNotBlank()) appendLine("正文: ${note.desc}")
            appendLine("互动: ❤️${note.likedCount} 💬${note.commentCount} ⭐${note.collectedCount}")
            appendLine("图片数量: ${note.images.size}张")
            if (note.comments.isNotEmpty()) {
                appendLine("评论区:")
                note.comments.forEach { appendLine("  · $it") }
            }
            appendLine("--- 笔记内容结束 ---")
        }
    }

    /**
     * 下载图片并转为base64 data URL
     */
    private fun downloadImageAsBase64(imageUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1")
                .header("Referer", "https://www.xiaohongshu.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val bytes = response.body?.bytes() ?: return null
            val mime = response.header("Content-Type") ?: "image/jpeg"
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            "data:$mime;base64,$base64"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download image: $imageUrl", e)
            null
        }
    }
}

/**
 * 小红书笔记数据
 */
data class XhsNoteData(
    val title: String,
    val desc: String,
    val author: String,
    val images: List<String>,
    val likedCount: String,
    val commentCount: String,
    val collectedCount: String,
    val comments: List<String> = emptyList()
)
