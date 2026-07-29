package com.example.watermarkcamera

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class WeatherResponse(
    val code: String,
    val now: WeatherNow?
)

data class WeatherNow(
    val text: String?,
    val temp: String?,
    @SerializedName("wind_dir") val windDir: String?,
    @SerializedName("wind_scale") val windScale: String?,
    val humidity: String?
)

class WeatherHelper {

    companion object {
        private const val TAG = "WeatherHelper"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getWeatherByOpenMeteo(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m,wind_direction_10m"

                Log.d(TAG, "请求天气: $url")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "天气响应码: ${response.code}")

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Log.d(TAG, "天气响应: $body")
                    if (body != null) {
                        parseOpenMeteoResponse(body)
                    } else {
                        Log.e(TAG, "天气响应体为空")
                        null
                    }
                } else {
                    Log.e(TAG, "天气请求失败: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "天气获取异常", e)
                null
            }
        }

    private fun parseOpenMeteoResponse(json: String): String {
        return try {
            val gson = com.google.gson.Gson()
            val response = gson.fromJson(json, OpenMeteoResponse::class.java)

            val current = response.current
            if (current != null) {
                val temp = current.temperature_2m?.let { String.format("%.0f", it) } ?: "--"
                val weatherCode = current.weather_code ?: 0
                val weatherText = getWeatherText(weatherCode)
                val humidity = current.relative_humidity_2m?.let { String.format("%.0f", it) } ?: "--"
                val windSpeed = current.wind_speed_10m?.let { String.format("%.0f", it) } ?: "--"
                val windDir = current.wind_direction_10m?.let { getWindDirection(it) } ?: ""

                buildString {
                    append(weatherText)
                    append(" ")
                    append(temp)
                    append("°C")
                    append(" ")
                    append(windDir)
                    append(windSpeed)
                    append("km/h")
                    append(" 湿度")
                    append(humidity)
                    append("%")
                }
            } else {
                Log.e(TAG, "天气数据 current 字段为空")
                "未知天气"
            }
        } catch (e: Exception) {
            Log.e(TAG, "天气解析异常", e)
            "未知天气"
        }
    }

    private fun getWeatherText(code: Int): String {
        return when (code) {
            0 -> "晴"
            1, 2, 3 -> "多云"
            45, 48 -> "雾"
            51, 53, 55 -> "小雨"
            61, 63, 65 -> "雨"
            71, 73, 75 -> "雪"
            80, 81, 82 -> "阵雨"
            95 -> "雷暴"
            96, 99 -> "冰雹雷暴"
            else -> "未知"
        }
    }

    private fun getWindDirection(degrees: Double): String {
        val directions = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        val index = ((degrees + 22.5) % 360 / 45).toInt()
        return directions[index]
    }
}

data class OpenMeteoResponse(
    val current: OpenMeteoCurrent?
)

data class OpenMeteoCurrent(
    val temperature_2m: Double?,
    val weather_code: Int?,
    val wind_speed_10m: Double?,
    val relative_humidity_2m: Double?,
    val wind_direction_10m: Double?
)