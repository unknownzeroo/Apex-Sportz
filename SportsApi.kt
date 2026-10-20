package com.arenalive.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Data Models
data class SportsEvent(
    val id: String,
    val name: String,
    val category: String,
    val home: String,
    val away: String,
    val status: String,
    val startTime: String,
    val hot: Boolean,
    val streams: List<StreamOption>,
) {
    val isLive: Boolean get() = status.contains("live", ignoreCase = true) || status.contains("in progress", ignoreCase = true)
}

data class StreamOption(
    val title: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val drmKeys: Map<String, String> = emptyMap(),
    val licenseUrl: String? = null,
)

data class Feed(
    val events: List<SportsEvent>,
    val updatedAt: String,
    val liveCount: String,
    val upcomingCount: String,
    val finishedCount: String,
    val isCached: Boolean = false,
)

// Singleton to hold selected event for the Player Screen
object EventHolder {
    var currentEvent: SportsEvent? = null
}

object SportsApi {
    private const val DEFAULT_ENDPOINT = "https://events.ivan-flux.online/api/v1/user?username=ATV"
    private const val SETTINGS_PREFS = "apex_settings"

    fun getEndpoint(context: Context): String {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return prefs.getString("api_url", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
    }

    suspend fun fetch(context: Context): Feed = withContext(Dispatchers.IO) {
        val endpoint = getEndpoint(context)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
        }

        try {
            if (connection.responseCode !in 200..299) error("API Error: ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val items = root.optJSONArray("events")
            val events = buildList {
                if (items != null) for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val info = item.optJSONObject("eventInfo") ?: JSONObject()
                    add(
                        SportsEvent(
                            id = item.optString("id", index.toString()),
                            name = info.optString("eventName").ifBlank { item.optString("title", "Sports event") },
                            category = item.optString("cat", "Sport"),
                            home = info.optString("teamA", "—"),
                            away = info.optString("teamB", "—"),
                            status = info.optString("Status", "Upcoming"),
                            startTime = info.optString("startTime", ""),
                            hot = info.optString("isHot", "0") == "1",
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
                finishedCount = root.optString("Finish", "0"),
            )
        } finally {
            connection.disconnect()
        }
    }

    // Parses the link string to extract URL, Headers, and DRM
    private fun parseStreamOptions(event: JSONObject): List<StreamOption> {
        val channels = event.optJSONArray("channels_data") ?: return emptyList()
        return buildList {
            for (index in 0 until channels.length()) {
                val channel = channels.optJSONObject(index) ?: continue
                val rawLink = channel.optString("link").trim()
                if (rawLink.isBlank()) continue

                // Split by pipe "|"
                val parts = rawLink.split("|")
                val url = parts.first().trim()
                if (url.isBlank()) continue

                val headers = mutableMapOf<String, String>()
                val drmKeys = mutableMapOf<String, String>()
                var licenseUrl: String? = null

                // Process the rest of the parts (Headers / DRM)
                parts.drop(1).forEach { part ->
                    val trimmed = part.trim()
                    if (trimmed.contains("=")) {
                        val (key, value) = trimmed.split("=", limit = 2)
                        when {
                            key.equals("license", true) || key.equals("licenseUrl", true) -> licenseUrl = value
                            key.startsWith("drm", true) || key.startsWith("key", true) -> drmKeys[key] = value
                            else -> headers[key] = value // Treat as HTTP header
                        }
                    } else if (trimmed.contains(":")) {
                        val (key, value) = trimmed.split(":", limit = 2)
                        headers[key.trim()] = value.trim()
                    }
                }

                add(
                    StreamOption(
                        title = channel.optString("title").ifBlank { "Stream ${index + 1}" },
                        url = url,
                        headers = headers,
                        drmKeys = drmKeys,
                        licenseUrl = licenseUrl
                    )
                )
            }
        }
    }
}
