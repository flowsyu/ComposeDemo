package com.flowsyu.composedemo.ui.screen

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.flowsyu.composedemo.model.MediaManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flowsyu.composedemo.model.MediaFile
import com.flowsyu.composedemo.data.SettingsRepository
import com.flowsyu.composedemo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.tween
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    mediaFile: MediaFile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(context) }
    
    // Focus requester for the main container to capture key events from remote
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var currentMediaFile by remember { mutableStateOf(mediaFile) }
    var duration by remember { mutableStateOf(mediaFile.duration) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableStateOf(16f / 9f) }
    var showPoster by remember(currentMediaFile) { mutableStateOf(true) }

    // Playlist
    var showPlaylist by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    // 更新播放进度
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            // If controls are hidden, show them.
                            // If focused on the main container (not buttons), toggle controls/play.
                            showControls = !showControls
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                            if (!showControls) {
                                showControls = true
                                true // consume event to prevent focus jump before controls appear? 
                                     // Actually better to let it pass if we want focus system to work immediately
                                     // but here we just want to wake up the UI
                            } else {
                                false
                            }
                        }
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            mediaPlayer?.let {
                                if (isPlaying) it.pause() else it.start()
                                isPlaying = !isPlaying
                                showControls = true
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // SurfaceView for video
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            surfaceHolder = holder
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            surfaceHolder = null
                            mediaPlayer?.release()
                            mediaPlayer = null
                        }
                    })
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(videoAspectRatio)
        )
        
        // Poster Frame (Video Thumbnail Placeholder)
        // Shows initially to prevent black flash, fades out when video starts rendering
        AnimatedVisibility(
            visible = showPoster,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(500)),
            modifier = Modifier.align(Alignment.Center).aspectRatio(videoAspectRatio)
        ) {
             AsyncImage(
                model = currentMediaFile.uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
             )
        }

        // 控制栏
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
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
                        text = currentMediaFile.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 底部控制栏
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp)
                ) {
                    // 进度条
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { value ->
                                currentPosition = value.toLong()
                            },
                            onValueChangeFinished = {
                                mediaPlayer?.seekTo(currentPosition.toInt())
                            },
                            valueRange = 0f..duration.toFloat(),
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

                    // 播放控制按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 快退
                        IconButton(onClick = {
                            mediaPlayer?.let {
                                val newPos = (currentPosition - 10000).coerceAtLeast(0)
                                it.seekTo(newPos.toInt())
                                currentPosition = newPos
                            }
                        }) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "快退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 播放/暂停
                        IconButton(onClick = {
                            mediaPlayer?.let {
                                if (isPlaying) {
                                    it.pause()
                                    isPlaying = false
                                } else {
                                    it.start()
                                    isPlaying = true
                                }
                            }
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        // 快进
                        IconButton(onClick = {
                            mediaPlayer?.let {
                                val newPos = (currentPosition + 10000).coerceAtMost(duration)
                                it.seekTo(newPos.toInt())
                                currentPosition = newPos
                            }
                        }) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "快进10秒",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 列表
                        IconButton(onClick = { showPlaylist = !showPlaylist }) {
                            Icon(
                                Icons.Default.QueueMusic,
                                contentDescription = "播放列表",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // 倍速
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
                                            // playbackSpeed is updated via the flow collection
                                            scope.launch {
                                                settingsRepository.setVideoPlaybackSpeed(speed)
                                            }
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
        
        // Playlist UI
        if (showPlaylist) {
            if (isLandscape) {
                // Landscape: Side Panel
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Dim background for outside click
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
                                currentFile = currentMediaFile,
                                onFileSelected = { file ->
                                    // Switch video
                                    mediaPlayer?.release()
                                    mediaPlayer = null // This triggers update in AndroidView
                                    currentMediaFile = file
                                    // Keep playlist open or close? Often nice close on selection in mobile
                                    // But maybe keep open for quick switching? Let's keep open for now or toggle?
                                    // Let's close it to let user see video.
                                    showPlaylist = false 
                                }
                            )
                        }
                    }
                }
            } else {
                // Portrait: Bottom Sheet
                ModalBottomSheet(
                    onDismissRequest = { showPlaylist = false },
                    sheetState = sheetState
                ) {
                      VideoPlaylistContent(
                        currentFile = currentMediaFile,
                        onFileSelected = { file ->
                            mediaPlayer?.release()
                            mediaPlayer = null
                            currentMediaFile = file
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
    onFileSelected: (MediaFile) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "播放列表 (${MediaManager.mediaList.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            itemsIndexed(MediaManager.mediaList) { index, file ->
                // Basic filter for video type logic could go here if Mixed list
                // But assuming MediaManager has mixed list, maybe we should filter or just show all?
                // Assuming currently user wants to see what's in the current context playlist.
                
                val isCurrent = file.uri == currentFile.uri
                ListItem(
                    headlineContent = { 
                        Text(
                            file.name, 
                            maxLines = 1, 
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
