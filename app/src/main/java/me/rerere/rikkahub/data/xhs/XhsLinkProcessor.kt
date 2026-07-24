package me.rerere.rikkahub.data.xhs

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
        """(https?://)?(www\.)?(xiaohongshu\.com/discovery/item/|xiaohongshu\.com/explore/|xhslink\.com/)[^\s]+""",
        RegexOption.IGNORE_CASE
    )

    private val INITIAL_STATE_PATTERN = Regex(
        """window\.__INITIAL_STATE__\s*=\s*(\{.+?\})\s*</script>""",
        RegexOption.DOT_MATCHES_ALL
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
        val match = INITIAL_STATE_PATTERN.find(html) ?: return null
        val jsonStr = match.groupValues[1]
            .replace("\\u002F", "/")
            .replace("undefined", "null")

        return try {
            val jsonObj = json.parseToJsonElement(jsonStr).jsonObject

            // 尝试两种路径
            val noteData = tryGetNoteData(jsonObj) ?: return null
            noteData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse __INITIAL_STATE__", e)
            null
        }
    }

    private fun tryGetNoteData(state: JsonObject): XhsNoteData? {
        try {
            // 路径1: note.noteDetailMap.{noteId}.note
            val noteSection = state["note"]?.jsonObject ?: return null
            val noteDetailMap = noteSection["noteDetailMap"]?.jsonObject ?: return null
            val firstEntry = noteDetailMap.entries.firstOrNull() ?: return null
            val noteObj = firstEntry.value.jsonObject["note"]?.jsonObject ?: return null

            val title = noteObj["title"]?.jsonPrimitive?.content ?: ""
            val desc = noteObj["desc"]?.jsonPrimitive?.content ?: ""
            val user = noteObj["user"]?.jsonObject
            val nickname = user?.get("nickname")?.jsonPrimitive?.content ?: ""

            // 图片列表
            val imageList = noteObj["imageList"]?.jsonArray
            val images = imageList?.mapNotNull { imgElement ->
                val imgObj = imgElement.jsonObject
                // 尝试 urlDefault > url 
                val imgUrl = imgObj["urlDefault"]?.jsonPrimitive?.content
                    ?: imgObj["url"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                normalizeImageUrl(imgUrl)
            } ?: emptyList()

            // 互动数据
            val interactInfo = noteObj["interactInfo"]?.jsonObject
            val likedCount = interactInfo?.get("likedCount")?.jsonPrimitive?.content ?: "0"
            val commentCount = interactInfo?.get("commentCount")?.jsonPrimitive?.content ?: "0"
            val collectedCount = interactInfo?.get("collectedCount")?.jsonPrimitive?.content ?: "0"

            // 评论（首屏）
            val comments = mutableListOf<String>()
            try {
                val commentSection = state["comment"]?.jsonObject
                val commentsData = commentSection?.get("comments")?.jsonArray
                commentsData?.take(5)?.forEach { commentElement ->
                    val commentObj = commentElement.jsonObject
                    val commentUser = commentObj["userInfo"]?.jsonObject?.get("nickname")?.jsonPrimitive?.content ?: ""
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
