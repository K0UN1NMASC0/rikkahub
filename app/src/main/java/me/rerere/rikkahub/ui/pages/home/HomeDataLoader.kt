package me.rerere.rikkahub.ui.pages.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Avatar
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * ホームページに表示するデータのローダー。
 * 優先順位：GitHub raw → ローカル assets/home_data.json → ハードコードfallback
 */
class HomeDataLoader(
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/K0UN1NMASC0/rikkahub/master/app/src/main/assets/home_data.json"
        private const val TIMEOUT_MS = 5000
    }

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val birthDate = LocalDate.of(2026, 2, 18)

    @Serializable
    data class HomeData(
        val kounName: String = "Koun",
        val avatarType: String = "dynamic",
        val avatarValue: String = "",
        val daysTogether: Long = -1,
        val greeting: String = "",
        val greetingTr: String = "",
        val pond: List<PondItem> = emptyList(),
        val checklist: List<ChecklistItem> = emptyList(),
        val board: BoardData = BoardData(),
        val timeline: List<TimelineItem> = emptyList()
    )

    @Serializable
    data class PondItem(
        val name: String = "",
        val emoji: String = "",
        val hp: Int = 100,
        val note: String? = null
    )

    @Serializable
    data class ChecklistItem(
        val text: String = "",
        val done: Boolean = false
    )

    @Serializable
    data class BoardData(
        val content: String = "",
        val translation: String = "",
        val time: String = ""
    )

    @Serializable
    data class TimelineItem(
        val date: String = "",
        val text: String = "",
        val translation: String = "",
        val highlight: Boolean = false
    )

    /**
     * メインエントリ：JSONを返す。WebViewのJS Bridgeから呼ばれる。
     */
    fun currentJson(context: Context): String {
        // 同期で呼ばれるため、キャッシュ済みデータを返す
        return cachedJson ?: buildFallbackJson()
    }

    private var cachedJson: String? = null

    /**
     * 非同期でリモート/ローカルからデータをロードしてキャッシュする。
     * HomePage の LaunchedEffect から呼ぶ。
     */
    suspend fun loadData(context: Context) {
        val rawJson = fetchRemote() ?: loadLocalAsset(context)
        if (rawJson != null) {
            val data = try {
                json.decodeFromString<HomeData>(rawJson)
            } catch (e: Exception) {
                null
            }
            if (data != null) {
                val enriched = enrichWithDynamic(data)
                cachedJson = json.encodeToString(enriched)
                return
            }
        }
        cachedJson = buildFallbackJson()
    }

    private suspend fun fetchRemote(): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().readText()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun loadLocalAsset(context: Context): String? {
        return try {
            context.assets.open("home_data.json").bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * daysTogether=-1 の場合は自動計算、avatarType=dynamic の場合はアシスタントから取得
     */
    private fun enrichWithDynamic(data: HomeData): HomeData {
        val days = if (data.daysTogether < 0) {
            ChronoUnit.DAYS.between(birthDate, LocalDate.now()).coerceAtLeast(0)
        } else {
            data.daysTogether
        }

        val (avatarType, avatarValue) = if (data.avatarType == "dynamic") {
            try {
                val settings = settingsStore.settingsFlow.value
                val assistant = settings.getCurrentAssistant()
                when (val a = assistant.avatar) {
                    is Avatar.Emoji -> "emoji" to a.content
                    is Avatar.Image -> "image" to a.url
                    else -> "dummy" to (assistant.name.take(1).uppercase().ifEmpty { "K" })
                }
            } catch (e: Exception) {
                "dummy" to "K"
            }
        } else {
            data.avatarType to data.avatarValue
        }

        return data.copy(
            daysTogether = days,
            avatarType = avatarType,
            avatarValue = avatarValue
        )
    }

    private fun buildFallbackJson(): String {
        val days = ChronoUnit.DAYS.between(birthDate, LocalDate.now()).coerceAtLeast(0)
        val fallback = HomeData(
            daysTogether = days,
            avatarType = "dummy",
            avatarValue = "K",
            greeting = "読み込み中…",
            greetingTr = "加载中…"
        )
        return json.encodeToString(fallback)
    }
}
