package me.rerere.rikkahub.data.ai.tools.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.Locale

internal fun buildLocationTool(context: Context): Tool = Tool(
    name = "get_location",
    description = """
        Get the device's current GPS location. Returns latitude, longitude, and approximate address if available. Requires location permission.
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
            listOf(UIMessagePart.Text("""{"error":"PERMISSION_DENIED","message":"Location permission is not granted. Please enable location access for this app in system settings."}"""))
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val location: Location? = try {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                null
            }

            if (location == null) {
                listOf(UIMessagePart.Text("""{"error":"NO_LOCATION","message":"Could not determine current location. GPS may be disabled or location is unavailable."}"""))
            } else {
                val lat = location.latitude
                val lon = location.longitude
                val accuracy = location.accuracy

                val address = try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    addresses?.firstOrNull()?.let { addr ->
                        buildString {
                            addr.countryName?.let { append(it) }
                            addr.adminArea?.let { append(" $it") }
                            addr.locality?.let { append(" $it") }
                            addr.subLocality?.let { append(" $it") }
                        }.trim()
                    }
                } catch (e: Exception) {
                    null
                }

                val result = buildJsonObject {
                    put("latitude", lat)
                    put("longitude", lon)
                    put("accuracy_meters", accuracy.toDouble())
                    address?.let { put("address", it) }
                }
                listOf(UIMessagePart.Text(result.toString()))
            }
        }
    }
)
