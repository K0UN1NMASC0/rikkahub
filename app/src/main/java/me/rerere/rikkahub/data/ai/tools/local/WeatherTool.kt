package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal fun buildWeatherTool(context: Context): Tool = Tool(
    name = "get_weather",
    description = """
        Get current weather for the device's GPS location using Open-Meteo API. 
        Returns temperature, apparent temperature, humidity, weather description, wind speed, and location name.
        Requires location permission.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject { },
            required = listOf()
        )
    },
    execute = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            listOf(UIMessagePart.Text("""{"error":"PERMISSION_DENIED","message":"Location permission is not granted. Cannot fetch weather."}"""))
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val location: Location? = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                null
            }

            if (location == null) {
                listOf(UIMessagePart.Text("""{"error":"NO_LOCATION","message":"Could not determine current location for weather lookup."}"""))
            } else {
                val lat = location.latitude
                val lon = location.longitude

                // Get address name
                val address = try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    addresses?.firstOrNull()?.let { addr ->
                        buildString {
                            addr.locality?.let { append(it) }
                            addr.subLocality?.let { append(" $it") }
                        }.trim().ifEmpty { 
                            addr.adminArea ?: "Unknown"
                        }
                    } ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }

                // Fetch weather from Open-Meteo (free, no API key needed)
                val weatherResult = try {
                    val apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=auto"
                    val connection = URL(apiUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()
                        response
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }

                if (weatherResult == null) {
                    listOf(UIMessagePart.Text("""{"error":"API_FAILED","message":"Weather API request failed.","location":"$address","latitude":$lat,"longitude":$lon}"""))
                } else {
                    // Parse the JSON response manually (minimal parsing)
                    val temp = extractJsonDouble(weatherResult, "temperature_2m")
                    val apparent = extractJsonDouble(weatherResult, "apparent_temperature")
                    val humidity = extractJsonDouble(weatherResult, "relative_humidity_2m")
                    val windSpeed = extractJsonDouble(weatherResult, "wind_speed_10m")
                    val weatherCode = extractJsonInt(weatherResult, "weather_code")
                    val weatherDesc = weatherCodeToDescription(weatherCode)

                    val result = buildJsonObject {
                        put("location", address)
                        put("latitude", lat)
                        put("longitude", lon)
                        put("temperature_c", temp)
                        put("apparent_temperature_c", apparent)
                        put("relative_humidity_percent", humidity)
                        put("wind_speed_kmh", windSpeed)
                        put("weather_code", weatherCode)
                        put("weather_description", weatherDesc)
                    }
                    listOf(UIMessagePart.Text(result.toString()))
                }
            }
        }
    }
)

private fun extractJsonDouble(json: String, key: String): Double {
    val pattern = "\"$key\"\\s*:\\s*([\\d.-]+)".toRegex()
    return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
}

private fun extractJsonInt(json: String, key: String): Int {
    val pattern = "\"$key\"\\s*:\\s*([\\d-]+)".toRegex()
    return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}

private fun weatherCodeToDescription(code: Int): String {
    return when (code) {
        0 -> "晴天"
        1 -> "大部分晴"
        2 -> "多云"
        3 -> "阴天"
        45, 48 -> "雾"
        51, 53, 55 -> "毛毛雨"
        56, 57 -> "冻毛毛雨"
        61, 63, 65 -> "雨"
        66, 67 -> "冻雨"
        71, 73, 75 -> "雪"
        77 -> "雪粒"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95 -> "雷暴"
        96, 99 -> "雷暴伴冰雹"
        else -> "未知"
    }
}
