package com.arenalive.app

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
    val teamAFlag: String,
    val teamBFlag: String,
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
    val drmKey: String? = null, // Format: KID:KEY
)

data class Feed(
    val events: List<SportsEvent>,
    val updatedAt: String,
    val liveCount: String,
    val upcomingCount: String,
)

object SportsApi {
    // Default API Hardcoded
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
                            title = info.optString("eventName").ifBlank { item.optString("title", "Sports event") },
                            category = item.optString("cat", "Sport"),
                            teamA = info.optString("teamA", "—"),
                            teamB = info.optString("teamB", "—"),
                            teamAFlag = info.optString("teamAFlag", ""),
                            teamBFlag = info.optString("teamBFlag", ""),
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
                val drmKey = channel.optString("api").trim().takeIf { it.contains(":") } // Catch ClearKey

                // Extract headers (e.g., |user-agent=...&referer=...)
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
