package com.arenalive.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF151923)
private val Muted = Color(0xFF727988)
private val Accent = Color(0xFF2563EB)
private val Canvas = Color(0xFFF7F8FA)
private val Line = Color(0xFFE8EAF0)

private data class SportsEvent(
    val id: String,
    val name: String,
    val category: String,
    val home: String,
    val away: String,
    val status: String,
    val startTime: String,
    val hot: Boolean,
) {
    val isLive: Boolean get() = status.contains("live", ignoreCase = true) || status.contains("in progress", ignoreCase = true)
}

private data class Feed(
    val events: List<SportsEvent>,
    val updatedAt: String,
    val liveCount: String,
    val upcomingCount: String,
    val finishedCount: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Canvas, surface = Color.White)) {
                SportsHome(onOpenOfficial = { event -> openOfficialBroadcast(event) })
            }
        }
    }

    private fun openOfficialBroadcast(event: SportsEvent) {
        val safeUrl = OfficialBroadcasts.urlFor(this, event.id)
        if (safeUrl == null) {
            android.widget.Toast.makeText(this, "No official broadcaster link is configured for this event yet.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
    }
}

@Composable
private fun SportsHome(onOpenOfficial: (SportsEvent) -> Unit) {
    var feed by remember { mutableStateOf<Feed?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("All sports") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = null
        try {
            feed = withContext(Dispatchers.IO) { SportsApi.fetch() }
        } catch (_: Exception) {
            error = "Couldn’t load the schedule. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    val currentFeed = feed
    val categories = listOf("All sports") + (currentFeed?.events.orEmpty().map { it.category }.filter { it.isNotBlank() }.distinct().sorted())
    val visibleEvents = currentFeed?.events.orEmpty().filter {
        selectedCategory == "All sports" || it.category.equals(selectedCategory, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().background(Canvas).systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ARENA", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                Text("Live sports, at a glance.", color = Muted, fontSize = 13.sp)
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh events", tint = Ink)
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryPill("${currentFeed?.liveCount ?: "—"} live", isLive = true)
                SummaryPill("${currentFeed?.upcomingCount ?: "—"} upcoming")
                SummaryPill("${currentFeed?.finishedCount ?: "—"} finished")
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val active = selectedCategory == category
                    Surface(
                        modifier = Modifier.clickable { selectedCategory = category },
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) Ink else Color.White,
                    ) {
                        Text(
                            category,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            color = if (active) Color.White else Muted,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            when {
                loading && currentFeed == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                error != null && currentFeed == null -> EmptyState(error!!, "Tap refresh to try again.")
                visibleEvents.isEmpty() -> EmptyState("No events found.", "Try another sport or refresh the schedule.")
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("EVENTS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                                Text("Updated ${currentFeed?.updatedAt ?: "—"}", color = Muted, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                        items(visibleEvents, key = { it.id }) { event ->
                            EventCard(event = event, onWatch = { onOpenOfficial(event) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(text: String, isLive: Boolean = false) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isLive) Box(Modifier.size(7.dp).background(Color(0xFFE34242), CircleShape))
            Text(text, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EventCard(event: SportsEvent, onWatch: () -> Unit) {
    Surface(shape = RoundedCornerShape(17.dp), color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(event.category.uppercase(Locale.getDefault()), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (event.isLive) Box(Modifier.size(7.dp).background(Color(0xFFE34242), CircleShape))
                    Text(event.status.ifBlank { "Scheduled" }, color = if (event.isLive) Color(0xFFC83232) else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(event.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(event.home.ifBlank { "—" }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("vs", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp))
                Text(event.away.ifBlank { "—" }, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Divider(color = Line)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatStart(event.startTime), color = Muted, fontSize = 11.sp)
                TextButton(onClick = onWatch, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                    Text(if (event.isLive) "WATCH" else "BROADCAST INFO", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

private object SportsApi {
    private const val ENDPOINT = "https://events.ivan-flux.online/api/v1/user?username=ATV"

    fun fetch(): Feed {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("API returned ${connection.responseCode}")
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
                        ),
                    )
                }
            }.sortedWith(compareByDescending<SportsEvent> { it.isLive }.thenBy { it.startTime })
            return Feed(
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
}

/** Parses only a broadcaster URL explicitly curated by the app maintainer. */
private object OfficialBroadcasts {
    fun urlFor(context: android.content.Context, eventId: String): String? = try {
        val json = context.assets.open("official_broadcasts.json").bufferedReader().use { it.readText() }
        val raw = JSONObject(json).optString(eventId).takeIf { it.isNotBlank() } ?: return null
        val uri = Uri.parse(raw)
        raw.takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null }
    } catch (_: Exception) {
        null
    }
}

private fun formatStart(raw: String): String {
    if (raw.isBlank()) return "Time to be announced"
    return try {
        val input = SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", Locale.US).parse(raw) ?: return raw
        SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(input.time))
    } catch (_: Exception) {
        raw
    }
}
