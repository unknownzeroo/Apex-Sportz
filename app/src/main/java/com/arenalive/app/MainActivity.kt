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
import androidx.compose.material.icons.filled.PlayArrow
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
    
    val exoPlayer = remember(stream) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers)

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

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoVideoPlayer", "Playback failed for ${stream.url}", error)
                playbackError = error.errorCodeName ?: "Unable to play this stream"
                onPlaybackError(stream.url)
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFrame = true
                playbackError = null
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackError = null
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        delay(10_000)
        if (!hasRenderedFrame && exoPlayer.playbackState == Player.STATE_READY) {
            Log.e("ExoVideoPlayer", "Stream ready without a rendered video frame: ${stream.url}")
            playbackError = "No video frame rendered"
            onPlaybackError(stream.url)
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

        if (playbackError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "This stream could not be played",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Try another stream or retry this one.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    playbackError = null
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }) {
                    Text("Retry")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// UTILS & UI COMPONENTS
// ---------------------------------------------------------------------------
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
