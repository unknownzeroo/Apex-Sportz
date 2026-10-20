package com.arenalive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Theme Colors
private val Ink = Color(0xFF151923)
private val Muted = Color(0xFF727988)
private val Accent = Color(0xFF2563EB)
private val Canvas = Color(0xFFF7F8FA)
private val Line = Color(0xFFE8EAF0)

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
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") { HomeScreen(navController) }
                    composable("detail") { DetailScreen(navController) }
                    composable("player") { PlayerScreen(navController) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PAGE 1: HOME SCREEN
// ---------------------------------------------------------------------------
@Composable
fun HomeScreen(navController: NavController) {
    var feed by remember { mutableStateOf<Feed?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("All sports") }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = null
        try {
            feed = SportsApi.fetch()
        } catch (e: Exception) {
            error = "Couldn’t load the schedule. Check your connection."
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
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("APEX SPORTZ", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                Text("Live sports, at a glance.", color = Muted, fontSize = 13.sp)
            }
            IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Ink)
            }
        }

        // Filters
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.take(4).forEach { category ->
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
        
        // List of Events
        when {
            loading && currentFeed == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            error != null && currentFeed == null -> EmptyState(error!!, "Tap refresh to try again.")
            visibleEvents.isEmpty() -> EmptyState("No events found.", "Try another sport or refresh.")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    items(visibleEvents, key = { it.id }) { event ->
                        EventCard(event = event, onWatch = {
                            EventHolder.currentEvent = event
                            navController.navigate("detail")
                        })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PAGE 2: EVENT DETAILS SCREEN
// ---------------------------------------------------------------------------
@Composable
fun DetailScreen(navController: NavController) {
    val event = EventHolder.currentEvent
    if (event == null) {
        navController.popBackStack()
        return
    }

    Column(Modifier.fillMaxSize().background(Canvas).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Event Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        }

        Column(Modifier.padding(20.dp)) {
            Text(event.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("${event.home} vs ${event.away}", fontSize = 18.sp, color = Muted)
            Spacer(Modifier.height(24.dp))
            
            Text("Available Streams", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(12.dp))

            if (event.streams.isEmpty()) {
                Text("No streams available for this event.", color = Muted)
            } else {
                event.streams.forEach { stream ->
                    Button(
                        onClick = {
                            EventHolder.currentEvent = event.copy(streams = listOf(stream)) // Pass selected stream
                            navController.navigate("player")
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text(stream.title, color = Color.White)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PAGE 3: PLAYER SCREEN (WITH HEADERS & DRM)
// ---------------------------------------------------------------------------
@Composable
fun PlayerScreen(navController: NavController) {
    val stream = EventHolder.currentEvent?.streams?.firstOrNull()
    if (stream == null) {
        navController.popBackStack()
        return
    }

    val context = LocalContext.current
    val exoPlayer = remember(stream.url) {
        // 1. Setup Custom Headers for media segments
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(stream.headers)

        // 2. Setup Media Item
        val mediaItemBuilder = MediaItem.Builder().setUri(stream.url)

        // 3. Setup DRM (If keys or license URL exist in the API)
        if (stream.licenseUrl != null) {
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                .setLicenseUri(stream.licenseUrl)
                // FIX: Use setLicenseRequestHeaders instead of setKeyRequestProperties
                .setLicenseRequestHeaders(stream.headers) 
                .build()
            mediaItemBuilder.setDrmConfiguration(drmConfig)
        } else if (stream.drmKeys.isNotEmpty()) {
            // If you use ClearKey (custom DRM keys)
            val keySetId = stream.drmKeys.entries.joinToString("&") { "${it.key}=${it.value}" }
            val drmConfig = MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                .setKeySetId(keySetId.toByteArray())
                .build()
            mediaItemBuilder.setDrmConfiguration(drmConfig)
        }

        // 4. Build ExoPlayer
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------
// SHARED COMPONENTS
// ---------------------------------------------------------------------------
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
                TextButton(onClick = onWatch) {
                    Text("WATCH · ${event.streams.size}", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

private fun formatStart(raw: String): String {
    if (raw.isBlank()) return "TBA"
    return try {
        val input = SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", Locale.US).parse(raw) ?: return raw
        SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).format(Date(input.time))
    } catch (_: Exception) {
        raw
    }
}
