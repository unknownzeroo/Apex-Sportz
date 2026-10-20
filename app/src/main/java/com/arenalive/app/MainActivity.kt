package com.arenalive.app

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF0F0F0F)
private val Muted = Color(0xFF606060)
private val Accent = Color(0xFF2563EB)
private val Canvas = Color(0xFFFFFFFF)
private val BgGray = Color(0xFFF2F2F2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Canvas)) {
                YouTubeStyleScreen()
            }
        }
    }
}

@Composable
fun YouTubeStyleScreen() {
    var feed by remember { mutableStateOf<Feed?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedEvent by remember { mutableStateOf<SportsEvent?>(null) }
    var selectedStream by remember { mutableStateOf<StreamOption?>(null) }
    var failedStreamUrls by remember { mutableStateOf(emptySet<String>()) }
    val scope = rememberCoroutineScope()

    fun handlePlaybackError(failedUrl: String) {
        val updatedFailures = failedStreamUrls + failedUrl
        failedStreamUrls = updatedFailures
        val nextStream = selectedEvent?.streams?.firstOrNull { stream ->
            stream.url != failedUrl && stream.url !in updatedFailures
        }
        if (nextStream != null) selectedStream = nextStream
    }

    suspend fun refresh() {
        loading = true
        errorMsg = null
        try {
            feed = SportsApi.fetch()
            // Auto-play first live event
            if (selectedEvent == null) {
                val firstLive = feed?.events?.firstOrNull { it.isLive } ?: feed?.events?.firstOrNull()
                if (firstLive != null && firstLive.streams.isNotEmpty()) {
                    selectedEvent = firstLive
                    failedStreamUrls = emptySet()
                    selectedStream = preferredStream(firstLive.streams)
                }
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Failed to connect to the server."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Canvas).systemBarsPadding()
    ) {
        // 1. VIDEO PLAYER
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (selectedStream != null) {
                    ExoVideoPlayer(
                        stream = selectedStream!!,
                        onPlaybackError = ::handlePlaybackError
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Text("Select an event to watch", color = Color.Gray)
                    }
                }
            }
        }

        // 2. STREAM SELECTOR
        if (selectedEvent != null) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedEvent!!.streams) { stream ->
                        val isSelected = stream.url == selectedStream?.url
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Ink else BgGray,
                            modifier = Modifier.clickable {
                                failedStreamUrls = emptySet()
                                selectedStream = stream
                            }
                        ) {
                            Text(
                                stream.title,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (isSelected) Color.White else Ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 3. EVENT DETAILS
        if (selectedEvent != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        selectedEvent!!.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectedEvent!!.isLive) {
                            Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LIVE", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("UPCOMING", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(formatStart(selectedEvent!!.startTime), color = Muted, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedEvent!!.teamA, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(" vs ", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        Text(selectedEvent!!.teamB, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
                Divider(color = BgGray, thickness = 8.dp)
            }
        }

        // 4. SCHEDULE HEADER
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Upcoming & Live", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Ink)
                }
            }
        }

        // 5. SCHEDULE LIST & ERROR STATE
        if (loading && feed == null) {
            item { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) } }
        } else if (errorMsg != null && feed == null) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Failed to load streams", fontWeight = FontWeight.Bold, color = Color.Red)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!, color = Muted, textAlign = TextAlign.Center, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { scope.launch { refresh() } }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            items(feed?.events ?: emptyList(), key = { it.id }) { event ->
                EventListItem(event = event, isSelected = event.id == selectedEvent?.id) {
                    selectedEvent = event
                    failedStreamUrls = emptySet()
                    selectedStream = preferredStream(event.streams)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// FIXED EXOPLAYER WITH CLEARKEY DRM
// ---------------------------------------------------------------------------
@Composable
fun ExoVideoPlayer(stream: StreamOption, onPlaybackError: (String) -> Unit) {
    val context = LocalContext.current
    var playbackError by remember(stream) { mutableStateOf<String?>(null) }
    var hasRenderedFrame by remember(stream) { mutableStateOf(false) }
    var playbackState by remember(stream) { mutableIntStateOf(Player.STATE_IDLE) }
    var isPlaying by remember(stream) { mutableStateOf(false) }
    var positionMs by remember(stream) { mutableLongStateOf(0L) }
    var durationMs by remember(stream) { mutableLongStateOf(C.TIME_UNSET) }
    var showDebug by remember(stream) { mutableStateOf(false) }
    
    val playbackSetup = remember(stream) {
        // Some stream hosts reject ExoPlayer's default user agent. Keep a browser-like
        // default, while letting source-specific headers supplied by the API override it.
        val requestHeaders = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        requestHeaders.putAll(stream.headers)

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(requestHeaders)

        val mediaItemBuilder = MediaItem.Builder().setUri(stream.url)

        var drmSessionManager: DefaultDrmSessionManager? = null
        if (!stream.drmKey.isNullOrBlank()) {
            try {
                // API gives Hex, ExoPlayer needs Base64URL
                val (kidHex, keyHex) = stream.drmKey.split(":")
                val kidBase64 = hexToBase64Url(kidHex)
                val keyBase64 = hexToBase64Url(keyHex)
                
                // Build the JSON format ExoPlayer expects for ClearKey
                val jsonKey = """{"keys":[{"kty":"oct","k":"$keyBase64","kid":"$kidBase64"}],"type":"temporary"}"""
                
                val drmCallback = LocalMediaDrmCallback(jsonKey.toByteArray())

                drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)

                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        if (drmSessionManager != null) {
            mediaSourceFactory.setDrmSessionManagerProvider(
                DrmSessionManagerProvider { drmSessionManager }
            )
        }

        // Do not start network playback here. The listener must be attached first;
        // otherwise fast failures can happen before onPlayerError is registered.
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player to mediaItemBuilder.build()
    }
    val exoPlayer = playbackSetup.first
    val mediaItem = playbackSetup.second

    DisposableEffect(exoPlayer, mediaItem) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoVideoPlayer", "Playback failed for ${stream.url}", error)
                playbackError = error.errorCodeName ?: "Unable to play this stream"
                playbackState = Player.STATE_IDLE
                onPlaybackError(stream.url)
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFrame = true
                playbackError = null
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) playbackError = null
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        // Register callbacks before prepare/play so startup errors cannot be missed.
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, playbackState, hasRenderedFrame) {
        if (
            hasRenderedFrame ||
            (playbackState != Player.STATE_BUFFERING && playbackState != Player.STATE_READY)
        ) {
            return@LaunchedEffect
        }

        val stateAtStart = playbackState
        // A live stream that never leaves BUFFERING is just as broken as a READY
        // stream that never renders a frame. Time out both and try the next source.
        delay(if (stateAtStart == Player.STATE_BUFFERING) 20_000L else 10_000L)

        if (!hasRenderedFrame && exoPlayer.playbackState == stateAtStart) {
            val reason = if (stateAtStart == Player.STATE_BUFFERING) {
                "Stream timed out while buffering"
            } else {
                "No video frame rendered"
            }
            Log.e("ExoVideoPlayer", "$reason: ${stream.url}")
            playbackError = reason
            onPlaybackError(stream.url)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    player = exoPlayer
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC101010))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("APEX LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = playerStatusColor(playbackState, hasRenderedFrame, playbackError)) {
                    Text(
                        playerStatusLabel(playbackState, hasRenderedFrame, playbackError),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showDebug = !showDebug }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        if (showDebug) Icons.Default.Close else Icons.Default.BugReport,
                        contentDescription = "Toggle playback diagnostics",
                        tint = Color.White
                    )
                }
            }

            if (showDebug) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    color = Color(0xEE111111),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("PLAYBACK DIAGNOSTICS", color = Color(0xFF93C5FD), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Stream: ${stream.title}", color = Color.White, fontSize = 11.sp)
                        Text("State: ${playerStateName(playbackState)}  |  Playing: $isPlaying", color = Color.LightGray, fontSize = 10.sp)
                        Text("First frame: $hasRenderedFrame  |  Position: ${positionMs / 1000}s", color = Color.LightGray, fontSize = 10.sp)
                        Text("DRM: ${if (stream.drmKey.isNullOrBlank()) "none" else "ClearKey metadata"}", color = Color.LightGray, fontSize = 10.sp)
                        Text("URL: ${stream.url}", color = Color.LightGray, fontSize = 9.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (stream.headers.isNotEmpty()) Text("Headers: ${stream.headers.keys.joinToString()}", color = Color.LightGray, fontSize = 9.sp)
                        if (playbackError != null) Text("Error: $playbackError", color = Color(0xFFFCA5A5), fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (playbackError != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Playback failed — trying the next stream", color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Text(playbackError!!, color = Color(0xFFFCA5A5), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(onClick = {
                        playbackError = null
                        hasRenderedFrame = false
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Retry stream")
                    }
                }
            } else {
                if (durationMs != C.TIME_UNSET && durationMs > 0) {
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = { exoPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..durationMs.toFloat(),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xCC101010)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play or pause", tint = Color.White)
                    }
                    Text(stream.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(playerStateName(playbackState), color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// UTILS & UI COMPONENTS
// ---------------------------------------------------------------------------
private fun playerStateName(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN"
}

private fun playerStatusLabel(state: Int, hasFrame: Boolean, error: String?): String = when {
    error != null -> "ERROR"
    hasFrame && state == Player.STATE_READY -> "PLAYING"
    state == Player.STATE_BUFFERING -> "BUFFERING"
    state == Player.STATE_READY -> "READY / NO FRAME"
    else -> playerStateName(state)
}

private fun playerStatusColor(state: Int, hasFrame: Boolean, error: String?): Color = when {
    error != null -> Color(0xFFB91C1C)
    hasFrame && state == Player.STATE_READY -> Color(0xFF15803D)
    state == Player.STATE_BUFFERING -> Color(0xFFB45309)
    else -> Color(0xFF4B5563)
}

private fun hexToBase64Url(hex: String): String {
    require(hex.length == 32 && hex.all { it in "0123456789abcdefABCDEF" }) {
        "ClearKey values must be 16-byte hexadecimal strings"
    }
    val bytes = ByteArray(hex.length / 2)
    for (i in bytes.indices) {
        bytes[i] = ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
    }
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private fun preferredStream(streams: List<StreamOption>): StreamOption? {
    // The feed commonly puts encrypted DASH first. Start with HLS so the app
    // has a playable default on devices without the required DRM scheme.
    return streams.firstOrNull { it.drmKey.isNullOrBlank() && isHls(it.url) }
        ?: streams.firstOrNull { it.drmKey.isNullOrBlank() }
        ?: streams.firstOrNull()
}

private fun isHls(url: String): Boolean =
    url.substringBefore('|').substringBefore('?').endsWith(".m3u8", ignoreCase = true)

@Composable
private fun EventListItem(event: SportsEvent, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isSelected) BgGray else Canvas)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(120.dp, 68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Text(event.category.take(3).uppercase(), fontWeight = FontWeight.Bold, color = Muted)
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("${event.teamA} vs ${event.teamB}", fontSize = 12.sp, color = Muted, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.isLive) {
                    Box(modifier = Modifier.size(6.dp).background(Color.Red, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("LIVE", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("UPCOMING", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(formatStart(event.startTime), fontSize = 10.sp, color = Muted)
            }
        }
    }
}

private fun formatStart(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val input = SimpleDateFormat("yyyy/MM/dd HH:mm:ss Z", Locale.US).parse(raw) ?: return raw
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(input.time))
    } catch (_: Exception) {
        raw
    }
}
