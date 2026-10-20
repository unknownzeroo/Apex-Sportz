package com.arenalive.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SportsEvent(
    val id: String,
    val title: String,
    val category: String,
    val teamA: String,
    val teamB: String,
    val status: String,
    val startTime: String,
    val streams: List<StreamOption>,
) {
    val isLive: Boolean get() = status.contains("live", ignoreCase = true) || status.contains("in progress", ignoreCase = true)
}

data class StreamOption(
    val title: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val drmKey: String? = null, // Format: KID:KEY (Hex)
)

data class Feed(
    val events: List<SportsEvent>,
    val updatedAt: String,
    val liveCount: String,
    val upcomingCount: String,
)

object SportsApi {
    // Hardcoded default API
    private const val ENDPOINT = "https://events.ivan-flux.online/api/v1/user?username=ATV"

    suspend fun fetch(): Feed = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            // User-Agent prevents the server from blocking the request
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw Exception("Server error: ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val items = root.optJSONArray("events") ?: throw Exception("Invalid JSON or no events found.")
            
            val events = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val info = item.optJSONObject("eventInfo") ?: JSONObject()
                    add(
                        SportsEvent(
                            id = item.optString("id", index.toString()),
                            title = info.optString("eventName").ifBlank { item.optString("title", "Sports event") },
                            category = item.optString("cat", "Sport"),
                            teamA = info.optString("teamA", "—"),
                            teamB = info.optString("teamB", "—"),
                            status = info.optString("Status", "Upcoming"),
                            startTime = info.optString("startTime", ""),
                            streams = parseStreamOptions(item),
                        )
                    )
                }
            }.sortedWith(compareByDescending<SportsEvent> { it.isLive }.thenBy { it.startTime })

            return@withContext Feed(
                events = events,
                updatedAt = root.optString("Last update time", "—"),
                liveCount = root.optString("Live", "0"),
                upcomingCount = root.optString("Upcoming", events.size.toString()),
            )
        } catch (e: Exception) {
            Log.e("SportsApi", "Network Error: ${e.message}")
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun parseStreamOptions(event: JSONObject): List<StreamOption> {
        val channels = event.optJSONArray("channels_data") ?: return emptyList()
        return buildList {
            for (index in 0 until channels.length()) {
                val channel = channels.optJSONObject(index) ?: continue
                var rawLink = channel.optString("link").trim()
                if (rawLink.isBlank() || rawLink == "https://no.link") continue

                val headers = mutableMapOf<String, String>()
                
                // Catch ClearKey DRM from the "api" field (Format: KID:KEY in Hex)
                val drmKey = channel.optString("api").trim().takeIf { it.contains(":") } 

                // Extract headers (e.g. |user-agent=...&referer=...)
                if (rawLink.contains("|")) {
                    val parts = rawLink.split("|")
                    rawLink = parts[0].trim().removeSuffix("?")
                    
                    parts.drop(1).joinToString("&").split("&").forEach { header ->
                        if (header.contains("=")) {
                            val (key, value) = header.split("=", limit = 2)
                            headers[key.trim()] = value.trim()
                        }
                    }
                }

                add(
                    StreamOption(
                        title = channel.optString("title").ifBlank { "Stream ${index + 1}" },
                        url = rawLink,
                        headers = headers,
                        drmKey = drmKey
                    )
                )
            }
        }
    }
}
