package me.rerere.rikkahub.data.ai.transformers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TAG = "WeatherContextTx"
private const val CACHE_TTL_MS = 30 * 60 * 1000L  // 30分

/**
 * 天気コンテキスト注入 Transformer。
 * 洛卡が居る場所の天気を open-meteo で取得し、system prompt に注入する。
 * バックグラウンドで動かない — メッセージ送信時のみ、位置と天気をキャッシュから引くか API 叩く。
 * 位置取得の権限が無い場合は静かにスキップ。
 */
class WeatherContextTransformer(
    private val context: Context,
) : InputMessageTransformer {

    @Volatile
    private var cachedText: String? = null

    @Volatile
    private var cachedAt: Long = 0

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val info = fetchOrCached() ?: return messages
        return messages.map { msg ->
            if (msg.role == MessageRole.SYSTEM) {
                val currentText = msg.parts
                    .filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n") { it.text }
                msg.copy(
                    parts = listOf(
                        UIMessagePart.Text(currentText + "\n\n" + info)
                    )
                )
            } else msg
        }
    }

    private suspend fun fetchOrCached(): String? {
        val now = System.currentTimeMillis()
        cachedText?.let {
            if (now - cachedAt < CACHE_TTL_MS) return it
        }

        val loc = try {
            withTimeoutOrNull(2000L) { getLastKnownLocation() }
        } catch (e: Exception) {
            Log.w(TAG, "location fetch failed: ${e.message}")
            null
        } ?: return cachedText  // 失败时保留旧缓存

        val text = try {
            withTimeoutOrNull(3000L) {
                fetchWeather(loc.latitude, loc.longitude)
            }
        } catch (e: Exception) {
            Log.w(TAG, "weather fetch failed: ${e.message}")
            null
        } ?: return cachedText

        cachedText = text
        cachedAt = now
        return text
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return@withContext null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val providers = try {
            lm.getProviders(true)
        } catch (e: Exception) {
            emptyList()
        }

        var best: Location? = null
        for (p in providers) {
            val l = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } ?: continue
            if (best == null || l.time > best.time) best = l
        }
        best
    }

    @Serializable
    private data class OMResp(val current: Current? = null)
    @Serializable
    private data class Current(
        val temperature_2m: Double? = null,
        val apparent_temperature: Double? = null,
        val relative_humidity_2m: Int? = null,
        val weather_code: Int? = null,
        val wind_speed_10m: Double? = null,
        val time: String? = null
    )

    private suspend fun fetchWeather(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&timezone=auto"
        val req = Request.Builder().url(url).build()
        val resp = http.newCall(req).execute()
        resp.use {
            if (!it.isSuccessful) return@withContext null
            val body = it.body?.string() ?: return@withContext null
            val parsed = try {
                json.decodeFromString(OMResp.serializer(), body)
            } catch (e: Exception) {
                Log.w(TAG, "parse weather failed: ${e.message}")
                return@withContext null
            }
            val c = parsed.current ?: return@withContext null
            buildString {
                append("[洛卡的当前环境 - 自动获取，仅供参考]\n")
                append("位置: (${"%.3f".format(lat)}, ${"%.3f".format(lon)})\n")
                c.temperature_2m?.let { t -> append("气温: ${"%.1f".format(t)}°C") }
                c.apparent_temperature?.let { t -> append(" (体感 ${"%.1f".format(t)}°C)") }
                append("\n")
                c.relative_humidity_2m?.let { h -> append("湿度: $h%\n") }
                c.weather_code?.let { code -> append("天气代码: $code (${wmoDesc(code)})\n") }
                c.wind_speed_10m?.let { w -> append("风速: ${"%.1f".format(w)} km/h\n") }
                append("[环境信息结束]")
            }
        }
    }

    private fun wmoDesc(code: Int): String = when (code) {
        0 -> "晴"
        1, 2, 3 -> "多云"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        61, 63, 65 -> "雨"
        66, 67 -> "冻雨"
        71, 73, 75 -> "雪"
        77 -> "冰粒"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷暴"
        96, 99 -> "雷暴伴冰雹"
        else -> "未知"
    }
}
