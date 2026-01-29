package com.flowsyu.composedemo.ui.screen

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flowsyu.composedemo.model.MediaFile
import com.flowsyu.composedemo.model.MediaManager
import com.flowsyu.composedemo.model.MediaType
import com.flowsyu.composedemo.service.AudioPlaybackService
import com.flowsyu.composedemo.service.PlaybackMode
import com.flowsyu.composedemo.ui.preview.DevicePreviews
import com.flowsyu.composedemo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AudioPlayerScreen(
    mediaFile: MediaFile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    var audioService by remember { mutableStateOf<AudioPlaybackService?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(mediaFile.duration) }
    var currentMediaFile by remember { mutableStateOf(mediaFile) }
    var isBound by remember { mutableStateOf(false) }
    var playbackMode by remember { mutableStateOf(PlaybackMode.LoopAll) }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as AudioPlaybackService.AudioBinder
                audioService = binder.getService()
                audioService?.let {
                     playbackMode = it.getPlaybackMode()
                     it.setPlaybackStateCallback { state ->
                        isPlaying = state.isPlaying
                        currentPosition = state.currentPosition
                        duration = state.duration
                        playbackMode = state.playbackMode
                        state.mediaFile?.let { file -> currentMediaFile = file }
                    }
                    it.playAudio(mediaFile)
                }
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                audioService = null
                isBound = false
            }
        }
    }

    DisposableEffect(isLandscape) {
        val window = activity?.window
        if (window != null && isLandscape) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (window != null && isLandscape) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Bind Service
    DisposableEffect(Unit) {
        val intent = Intent(context, AudioPlaybackService::class.java)
        if (!isTv) {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            if (isTv) {
                audioService?.stopPlayback()
                context.stopService(intent)
            }
            context.unbindService(serviceConnection)
        }
    }

    // Update progress
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            audioService?.let {
                currentPosition = it.getCurrentPosition().toLong()
                duration = it.getDuration().toLong()
            }
            delay(100)
        }
    }

    AudioPlayerScreenContent(
        mediaFile = currentMediaFile,
        isPlaying = isPlaying,
        currentPosition = currentPosition,
        duration = duration,
        playbackMode = playbackMode,
        playlist = MediaManager.mediaList,
        onBack = onBack,
        onPlayPause = {
            if (isPlaying) {
                audioService?.pausePlayback()
            } else {
                audioService?.resumePlayback()
            }
        },
        onSeek = { pos ->
            audioService?.seekTo(pos.toInt())
            currentPosition = pos
        },
        onSkipPrevious = {
            audioService?.let {
                val newPos = (currentPosition - 10000).coerceAtLeast(0)
                it.seekTo(newPos.toInt())
                currentPosition = newPos
            }
        },
        onSkipNext = {
            audioService?.let {
                val newPos = (currentPosition + 10000).coerceAtMost(duration)
                it.seekTo(newPos.toInt())
                currentPosition = newPos
            }
        },
        onTogglePlayMode = {
            audioService?.togglePlayMode()
        },
        onFileSelected = { file ->
            audioService?.playAudio(file)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreenContent(
    mediaFile: MediaFile,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackMode: PlaybackMode,
    playlist: List<MediaFile>,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onFileSelected: (MediaFile) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val focusRequester = remember { FocusRequester() }
    
    var showPlaylist by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    // Smooth dragging state
    var sliderPosition by remember(currentPosition) { mutableStateOf(currentPosition.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音频播放") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(vertical = 24.dp, horizontal = 32.dp)
        ) {
            val coverSize = if (isLandscape) 220.dp else 280.dp
            val iconSize = if (isLandscape) 96.dp else 120.dp

            val coverContent: @Composable () -> Unit = {
                Card(
                    modifier = Modifier.size(coverSize),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val infoContent: @Composable () -> Unit = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mediaFile.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unknown Artist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val progressContent: @Composable () -> Unit = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Slider(
                        value = if (isDragging) sliderPosition else currentPosition.toFloat(),
                        onValueChange = { value ->
                            isDragging = true
                            sliderPosition = value
                        },
                        onValueChangeFinished = {
                            onSeek(sliderPosition.toLong())
                            isDragging = false
                        },
                        valueRange = 0f..if(duration > 0) duration.toFloat() else 1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(if (isDragging) sliderPosition.toLong() else currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val controlsContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "快退10秒",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "快进10秒",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            val extraContent: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    var showToast by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(showToast) {
                        showToast?.let {
                            delay(2000)
                            showToast = null
                        }
                    }

                    if (showToast != null) {
                        AlertDialog(
                            onDismissRequest = { showToast = null },
                            confirmButton = {},
                            text = { Text(showToast!!) }
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showPlaylist = true
                        }
                    ) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = "播放列表",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "列表",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            onTogglePlayMode()
                        }
                    ) {
                        val (icon, text) = when (playbackMode) {
                            PlaybackMode.LoopAll -> Icons.Default.Repeat to "列表循环"
                            PlaybackMode.LoopOne -> Icons.Default.RepeatOne to "单曲循环"
                            PlaybackMode.Shuffle -> Icons.Default.Shuffle to "随机播放"
                        }

                        Icon(
                            icon,
                            contentDescription = text,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    coverContent()
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        infoContent()
                        progressContent()
                        controlsContent()
                        extraContent()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    coverContent()
                    infoContent()
                    progressContent()
                    controlsContent()
                    extraContent()
                }
            }
        }
    }

    if (showPlaylist) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylist = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
            ) {
                Text(
                    text = "播放列表 (${playlist.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn {
                    itemsIndexed(playlist) { index, file ->
                        val isCurrent = file.uri == mediaFile.uri
                        ListItem(
                            headlineContent = { 
                                Text(
                                    file.name, 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            leadingContent = {
                                if (isCurrent) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = "Playing",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                onFileSelected(file)
                            }
                        )
                    }
                }
            }
        }
    }
}

@DevicePreviews
@Composable
fun AudioPlayerScreenPreview() {
    val sampleFile = MediaFile(
        uri = android.net.Uri.EMPTY,
        name = "Sample Audio.mp3",
        path = "",
        size = 1000L,
        duration = 180000L,
        type = MediaType.AUDIO
    )
    val playlist = listOf(sampleFile, sampleFile.copy(name = "Another Song.mp3"))

    MaterialTheme {
        AudioPlayerScreenContent(
            mediaFile = sampleFile,
            isPlaying = true,
            currentPosition = 45000L,
            duration = 180000L,
            playbackMode = PlaybackMode.LoopAll,
            playlist = playlist,
            onBack = {},
            onPlayPause = {},
            onSeek = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onTogglePlayMode = {},
            onFileSelected = {}
        )
    }
}
