package com.arenalive.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

// Holds the selected event so the Player screen knows what to play
object EventHolder {
    var currentEvent: SportsEvent? = null
}

object SportsApi {
    // YOUR DEFAULT API (Hardcoded exactly as you wanted)
    private const val ENDPOINT = "https://events.ivan-flux.online/api/v1/user?username=ATV"

    suspend fun fetch(): Feed = withContext(Dispatchers.IO) {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
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

    // Parses headers and DRM from the link (e.g., "https://url.com|Referer=xyz|drm_key=123")
    private fun parseStreamOptions(event: JSONObject): List<StreamOption> {
        val channels = event.optJSONArray("channels_data") ?: return emptyList()
        return buildList {
            for (index in 0 until channels.length()) {
                val channel = channels.optJSONObject(index) ?: continue
                val rawLink = channel.optString("link").trim()
                if (rawLink.isBlank()) continue

                val parts = rawLink.split("|")
                val url = parts.first().trim()
                if (url.isBlank()) continue

                val headers = mutableMapOf<String, String>()
                val drmKeys = mutableMapOf<String, String>()
                var licenseUrl: String? = null

                parts.drop(1).forEach { part ->
                    val trimmed = part.trim()
                    if (trimmed.contains("=")) {
                        val (key, value) = trimmed.split("=", limit = 2)
                        when {
                            key.equals("license", true) || key.equals("licenseUrl", true) -> licenseUrl = value
                            key.startsWith("drm", true) || key.startsWith("key", true) -> drmKeys[key] = value
                            else -> headers[key] = value 
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
