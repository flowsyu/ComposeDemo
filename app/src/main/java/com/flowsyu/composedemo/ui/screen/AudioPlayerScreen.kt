package com.flowsyu.composedemo.ui.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowsyu.composedemo.model.MediaFile
import com.flowsyu.composedemo.model.MediaManager
import com.flowsyu.composedemo.service.AudioPlaybackService
import com.flowsyu.composedemo.service.PlaybackMode
import com.flowsyu.composedemo.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    mediaFile: MediaFile,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var audioService by remember { mutableStateOf<AudioPlaybackService?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(mediaFile.duration) }
    var currentMediaFile by remember { mutableStateOf(mediaFile) }
    var isBound by remember { mutableStateOf(false) }
    var playbackMode by remember { mutableStateOf(PlaybackMode.LoopAll) }
    
    // Playlist Modal
    var showPlaylist by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

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
                    // Only play if it's a new request or not playing? 
                    // For now keeping original logic to play passed file.
                    // But if service is already playing this file, it might restart.
                    // Ideally we check if it's already playing.
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

    // 绑定服务
    DisposableEffect(Unit) {
        val intent = Intent(context, AudioPlaybackService::class.java)
        context.startService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(serviceConnection)
        }
    }

    // 更新播放进度
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            audioService?.let {
                currentPosition = it.getCurrentPosition().toLong()
                duration = it.getDuration().toLong()
            }
            delay(100)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音频播放") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 专辑封面区域
            Card(
                modifier = Modifier
                    .size(280.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 歌曲信息
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text(
                    text = currentMediaFile.name,
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

            // 进度条
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { value ->
                        currentPosition = value.toLong()
                    },
                    onValueChangeFinished = {
                        audioService?.seekTo(currentPosition.toInt())
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
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

            // 播放控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 快退
                IconButton(
                    onClick = {
                        audioService?.let {
                            val newPos = (currentPosition - 10000).coerceAtLeast(0)
                            it.seekTo(newPos.toInt())
                            currentPosition = newPos
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "快退10秒",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 播放/暂停
                FilledIconButton(
                    onClick = {
                        if (isPlaying) {
                            audioService?.pausePlayback()
                        } else {
                            audioService?.resumePlayback()
                        }
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(40.dp)
                    )
                }

                // 快进
                IconButton(
                    onClick = {
                        audioService?.let {
                            val newPos = (currentPosition + 10000).coerceAtMost(duration)
                            it.seekTo(newPos.toInt())
                            currentPosition = newPos
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = "快进10秒",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 附加信息
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

                // 列表按钮
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

                // 播放模式切换按钮 (根据需求合并)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { 
                        audioService?.togglePlayMode()
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
                    text = "播放列表 (${MediaManager.mediaList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn {
                    itemsIndexed(MediaManager.mediaList) { index, file ->
                        val isCurrent = file.uri == currentMediaFile.uri
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
                                audioService?.playAudio(file)
                            }
                        )
                    }
                }
            }
        }
    }
}
