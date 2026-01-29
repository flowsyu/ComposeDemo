package com.flowsyu.composedemo.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.flowsyu.composedemo.data.SettingsRepository
import com.flowsyu.composedemo.model.MediaFile
import com.flowsyu.composedemo.model.MediaManager
import com.flowsyu.composedemo.model.MediaType
import com.flowsyu.composedemo.ui.preview.DevicePreviews
import com.flowsyu.composedemo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun VideoPlayerScreen(
    mediaFile: MediaFile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var currentMediaFile by remember { mutableStateOf(mediaFile) }
    var duration by remember { mutableStateOf(mediaFile.duration) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }
    var showPoster by remember(currentMediaFile) { mutableStateOf(true) }
    
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    // Init speed from data store
    LaunchedEffect(Unit) {
        settingsRepository.videoPlaybackSpeed.collectLatest { speed ->
            playbackSpeed = speed
            mediaPlayer?.let { setPlaybackSpeed(it, speed) }
        }
    }
    
    // Manage MediaPlayer lifecycle based on Surface and File
    LaunchedEffect(currentMediaFile, surfaceHolder) {
        val holder = surfaceHolder
        if (holder != null) {
            mediaPlayer?.release()
            mediaPlayer = null
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, currentMediaFile.uri)
                    setDisplay(holder)
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            showPoster = false
                            true
                        } else {
                            false
                        }
                    }
                    setOnPreparedListener { mp ->
                        duration = mp.duration.toLong()
                        mp.start()
                        isPlaying = true
                        setPlaybackSpeed(mp, playbackSpeed)
                        
                        // Update aspect ratio
                        if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                            videoAspectRatio = mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                        }
                        
                        if (mp.videoWidth > mp.videoHeight) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                        
                        // Fallback: if render info not received quickly, hide poster
                        scope.launch {
                            delay(500)
                            showPoster = false
                        }
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosition = 0
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    // Update progress
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    currentPosition = it.currentPosition.toLong()
                }
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        
        onDispose {
            mediaPlayer?.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    VideoPlayerScreenContent(
        mediaFile = currentMediaFile,
        isPlaying = isPlaying,
        currentPosition = currentPosition,
        duration = duration,
        showControls = showControls,
        showPoster = showPoster,
        videoAspectRatio = videoAspectRatio,
        playbackSpeed = playbackSpeed,
        playlist = MediaManager.mediaList,
        onBack = onBack,
        onSurfaceCreated = { surfaceHolder = it },
        onSurfaceDestroyed = { 
            surfaceHolder = null
            mediaPlayer?.release()
            mediaPlayer = null
        },
        onToggleControls = { showControls = !showControls },
        onShowControls = { showControls = true },
        onPlayPause = {
             mediaPlayer?.let {
                if (isPlaying) it.pause() else it.start()
                isPlaying = !isPlaying
                showControls = true
            }
        },
        onSeek = { pos ->
            mediaPlayer?.let {
                 it.seekTo(pos.toInt())
                 currentPosition = pos
            }
        },
        onPlaybackSpeedChange = { speed ->
            scope.launch {
                settingsRepository.setVideoPlaybackSpeed(speed)
            }
        },
        onFileSelected = { file ->
             mediaPlayer?.release()
             mediaPlayer = null
             currentMediaFile = file
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreenContent(
    mediaFile: MediaFile,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    showControls: Boolean,
    showPoster: Boolean,
    videoAspectRatio: Float,
    playbackSpeed: Float,
    playlist: List<MediaFile>,
    onBack: () -> Unit,
    onSurfaceCreated: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onToggleControls: () -> Unit,
    onShowControls: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onFileSelected: (MediaFile) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    var showPlaylist by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    // Local slider state for smooth dragging
    var sliderPosition by remember(currentPosition) { mutableStateOf(currentPosition.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onToggleControls()
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            onToggleControls()
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                            if (!showControls) {
                                onShowControls()
                                true
                            } else {
                                false
                            }
                        }
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            onPlayPause()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSurfaceCreated(holder)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSurfaceDestroyed()
                        }
                    })
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(videoAspectRatio)
        )
        
        AnimatedVisibility(
            visible = showPoster,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.align(Alignment.Center).aspectRatio(videoAspectRatio)
        ) {
             AsyncImage(
                model = mediaFile.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
             )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = mediaFile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Text(
                            text = formatDuration(if (isDragging) sliderPosition.toLong() else currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = if (isDragging) sliderPosition else currentPosition.toFloat(),
                            onValueChange = { 
                                isDragging = true
                                sliderPosition = it
                            },
                            onValueChangeFinished = {
                                onSeek(sliderPosition.toLong())
                                isDragging = false
                            },
                            valueRange = 0f..if (duration > 0) duration.toFloat() else 1f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = formatDuration(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val newPos = (currentPosition - 10000).coerceAtLeast(0)
                            onSeek(newPos)
                        }) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "快退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = onPlayPause) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        IconButton(onClick = {
                            val newPos = (currentPosition + 10000).coerceAtMost(duration)
                            onSeek(newPos)
                        }) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "快进10秒",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = { showPlaylist = !showPlaylist }) {
                            Icon(
                                Icons.Default.QueueMusic,
                                contentDescription = "播放列表",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Box {
                            IconButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = "倍速",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            onPlaybackSpeedChange(speed)
                                            showSpeedMenu = false
                                        },
                                        trailingIcon = if (speed == playbackSpeed) {
                                            {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (showPlaylist) {
            if (isLandscape) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                     Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showPlaylist = false }
                    )
                    
                    AnimatedVisibility(
                        visible = showPlaylist,
                        enter = slideInHorizontally(initialOffsetX = { it }),
                        exit = slideOutHorizontally(targetOffsetX = { it })
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(300.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                             VideoPlaylistContent(
                                currentFile = mediaFile,
                                playlist = playlist,
                                onFileSelected = { file ->
                                    onFileSelected(file)
                                    showPlaylist = false 
                                }
                            )
                        }
                    }
                }
            } else {
                ModalBottomSheet(
                    onDismissRequest = { showPlaylist = false },
                    sheetState = sheetState
                ) {
                      VideoPlaylistContent(
                        currentFile = mediaFile,
                        playlist = playlist,
                        onFileSelected = { file ->
                            onFileSelected(file)
                            showPlaylist = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlaylistContent(
    currentFile: MediaFile,
    playlist: List<MediaFile>,
    onFileSelected: (MediaFile) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "播放列表 (${playlist.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            itemsIndexed(playlist) { index, file ->
                val isCurrent = file.uri == currentFile.uri
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
                                Icons.Default.PlayArrow,
                                contentDescription = "Playing",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
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

private fun setPlaybackSpeed(mediaPlayer: MediaPlayer, speed: Float) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val params = mediaPlayer.playbackParams
            params.speed = speed
            mediaPlayer.playbackParams = params
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@DevicePreviews
@Composable
fun VideoPlayerScreenPreview() {
    val sampleFile = MediaFile(
        uri = android.net.Uri.EMPTY,
        name = "Big Buck Bunny.mp4",
        path = "",
        size = 1000L,
        duration = 600000L,
        type = MediaType.VIDEO
    )
    val playlist = listOf(sampleFile, sampleFile.copy(name = "Another Video.mp4"))
    
    MaterialTheme {
        VideoPlayerScreenContent(
            mediaFile = sampleFile,
            isPlaying = true,
            currentPosition = 120000L,
            duration = 600000L,
            showControls = true,
            showPoster = false,
            videoAspectRatio = 16f/9f,
            playbackSpeed = 1.0f,
            playlist = playlist,
            onBack = {},
            onSurfaceCreated = {},
            onSurfaceDestroyed = {},
            onToggleControls = {},
            onShowControls = {},
            onPlayPause = {},
            onSeek = {},
            onPlaybackSpeedChange = {},
            onFileSelected = {}
        )
    }
}
