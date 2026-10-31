package com.livingroom.launcher

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class DayForecast(
    val weekday: String,
    val max: Int,
    val min: Int,
    val code: Int,
)

data class CityWeather(
    val city: String,
    val temp: Int,
    val code: Int,
    val days: List<DayForecast>,
)

private data class City(
    val name: String,
    val lat: Double,
    val lon: Double,
)

private val CITIES = listOf(City("London", 51.5072, -0.1276), City("Lagos", 6.5244, 3.3792), City("Dubai", 25.2048, 55.2708))

/** Current weather + 5 days for London, Lagos and Dubai from Open-Meteo (free, no key). Blocking. */
fun loadWeather(): List<CityWeather> =
    try {
        val url =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${CITIES.joinToString(",") { it.lat.toString() }}" +
                "&longitude=${CITIES.joinToString(",") { it.lon.toString() }}" +
                "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=5"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 8000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        // One location returns an object; several return an array in the same order
        val arr = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
        CITIES.indices.map { i ->
            val o = arr.getJSONObject(i)
            val cur = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            CityWeather(
                city = CITIES[i].name,
                temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
                code = cur.getInt("weather_code"),
                days =
                    (0 until dates.length()).map { d ->
                        DayForecast(
                            weekday = LocalDate.parse(dates.getString(d)).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(d)).toInt(),
                            min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(d)).toInt(),
                            code = daily.getJSONArray("weather_code").getInt(d),
                        )
                    },
            )
        }
    } catch (ex: Exception) {
        Log.w("Weather", "Weather failed: ${ex.message}")
        emptyList()
    }

/** WMO weather code to icon. */
fun weatherIcon(code: Int): Int =
    when (code) {
        0 -> R.drawable.ic_sun
        1, 2 -> R.drawable.ic_cloud_sun
        3 -> R.drawable.ic_cloud
        45, 48 -> R.drawable.ic_cloud_fog
        in 51..67, in 80..82 -> R.drawable.ic_cloud_rain
        in 71..77, 85, 86 -> R.drawable.ic_snowflake
        in 95..99 -> R.drawable.ic_cloud_lightning
        else -> R.drawable.ic_cloud
    }

/** WMO weather code to a short description. */
fun weatherText(code: Int): String =
    when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "Cloudy"
    }
