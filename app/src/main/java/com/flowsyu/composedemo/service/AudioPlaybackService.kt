package com.flowsyu.composedemo.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.flowsyu.composedemo.MainActivity
import com.flowsyu.composedemo.R
import com.flowsyu.composedemo.model.MediaFile

import com.flowsyu.composedemo.model.MediaManager
import kotlin.random.Random

import com.flowsyu.composedemo.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PlaybackMode {
    LoopAll, LoopOne, Shuffle
}

class AudioPlaybackService : Service() {
    private val binder = AudioBinder()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var settingsRepository: SettingsRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentMediaFile: MediaFile? = null
    private var playbackStateCallback: ((PlaybackState) -> Unit)? = null

    private var playlist: List<MediaFile> = emptyList()
    private var currentPlaylistIndex: Int = -1
    private var playbackMode: PlaybackMode = PlaybackMode.LoopAll
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_playback_channel"
        const val ACTION_PLAY = "com.flowsyu.composedemo.PLAY"
        const val ACTION_PAUSE = "com.flowsyu.composedemo.PAUSE"
        const val ACTION_STOP = "com.flowsyu.composedemo.STOP"
        const val ACTION_NEXT = "com.flowsyu.composedemo.NEXT" 
        const val ACTION_PREVIOUS = "com.flowsyu.composedemo.PREVIOUS"
    }

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0,
        val duration: Long = 0,
        val mediaFile: MediaFile? = null,
        val playbackMode: PlaybackMode = PlaybackMode.LoopAll
    )

    inner class AudioBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }
    
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        
        // Initialize playlist from MediaManager if available
        if (MediaManager.mediaList.isNotEmpty()) {
            playlist = MediaManager.mediaList
            currentPlaylistIndex = MediaManager.currentMediaIndex
        }
        
        // Load persist playback mode
        serviceScope.launch {
            val modeIndex = settingsRepository.playbackMode.first()
            playbackMode = PlaybackMode.values().getOrElse(modeIndex) { PlaybackMode.LoopAll }
        }
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_STOP -> stopPlayback()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
        }
        return START_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "AudioPlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumePlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onStop() {
                    stopPlayback()
                }
                
                override fun onSkipToNext() {
                    playNext()
                }
                
                override fun onSkipToPrevious() {
                    playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                }
            })
            isActive = true
        }
    }

    fun playAudio(mediaFile: MediaFile) {
        // Sync playlist from MediaManager
        if (MediaManager.mediaList.isNotEmpty()) {
            playlist = MediaManager.mediaList
            currentPlaylistIndex = MediaManager.mediaList.indexOfFirst { it.uri == mediaFile.uri }
        } else {
             // Fallback for direct calls without MediaManager set
             playlist = listOf(mediaFile)
             currentPlaylistIndex = 0
        }

        if (currentMediaFile?.uri == mediaFile.uri && mediaPlayer?.isPlaying == true) {
            // 如果已在播放同一首歌，则忽略
            updatePlaybackState(true)
            return
        }
        
        currentMediaFile = mediaFile
        
        if (!requestAudioFocus()) {
            return
        }

        startPlayer(mediaFile)
    }

    private fun startPlayer(mediaFile: MediaFile) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, mediaFile.uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnPreparedListener {
                    start()
                    updatePlaybackState(true)
                    updateNotification()
                    updateMediaSessionMetadata()
                }
                setOnCompletionListener {
                    onPlaybackCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopPlayback()
        }
    }

    private fun onPlaybackCompleted() {
        if (playbackMode == PlaybackMode.LoopOne) {
            currentMediaFile?.let { startPlayer(it) }
        } else {
            playNext(autoPlay = true)
        }
    }
    
    fun playNext(autoPlay: Boolean = false) {
        if (playlist.isEmpty()) return
        
        val nextIndex = if (playbackMode == PlaybackMode.Shuffle) {
            Random.nextInt(playlist.size)
        } else {
            (currentPlaylistIndex + 1)
        }
        
        if (nextIndex >= playlist.size) {
            if (playbackMode == PlaybackMode.LoopAll || autoPlay) {
                currentPlaylistIndex = 0
            } else {
                // End of playlist
                updatePlaybackState(false)
                return
            }
        } else {
            currentPlaylistIndex = nextIndex
        }
        
        if (currentPlaylistIndex in playlist.indices) {
            val nextFile = playlist[currentPlaylistIndex]
            currentMediaFile = nextFile
            startPlayer(nextFile)
        }
    }
    
    fun playPrevious() {
        if (playlist.isEmpty()) return
        
        val prevIndex = if (playbackMode == PlaybackMode.Shuffle) {
            Random.nextInt(playlist.size)
        } else {
            if (currentPlaylistIndex > 0) currentPlaylistIndex - 1 else playlist.size - 1
        }
        
        currentPlaylistIndex = prevIndex
        if (currentPlaylistIndex in playlist.indices) {
            val prevFile = playlist[currentPlaylistIndex]
            currentMediaFile = prevFile
            startPlayer(prevFile)
        }
    }
    
    fun togglePlayMode() {
        playbackMode = when (playbackMode) {
            PlaybackMode.LoopAll -> PlaybackMode.Shuffle
            PlaybackMode.Shuffle -> PlaybackMode.LoopOne
            PlaybackMode.LoopOne -> PlaybackMode.LoopAll
        }
        serviceScope.launch {
            settingsRepository.setPlaybackMode(playbackMode.ordinal)
        }
        updatePlaybackState(isPlaying())
    }
    
    fun getPlaybackMode(): PlaybackMode = playbackMode

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                updatePlaybackState(false)
                updateNotification()
            }
        }
    }

    fun resumePlayback() {
        if (!requestAudioFocus()) {
            return
        }
        
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                updatePlaybackState(true)
                updateNotification()
            }
        }
    }

    fun stopPlayback() {
        mediaPlayer?.let {
            it.stop()
            it.release()
        }
        mediaPlayer = null
        abandonAudioFocus()
        updatePlaybackState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun setPlaybackStateCallback(callback: (PlaybackState) -> Unit) {
        playbackStateCallback = callback
    }

    private fun updatePlaybackState(isPlaying: Boolean) {
        val state = PlaybackState(
            isPlaying = isPlaying,
            currentPosition = getCurrentPosition().toLong(),
            duration = getDuration().toLong(),
            mediaFile = currentMediaFile,
            playbackMode = playbackMode
        )
        playbackStateCallback?.invoke(state)

        val playbackState = PlaybackStateCompat.Builder()
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                state.currentPosition,
                1.0f
            )
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMediaSessionMetadata() {
        currentMediaFile?.let { mediaFile ->
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaFile.name)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Unknown Artist")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaFile.duration)
                .build()
            mediaSession.setMetadata(metadata)
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                setAudioAttributes(
                    AudioAttributes.Builder().run {
                        setUsage(AudioAttributes.USAGE_MEDIA)
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        build()
                    }
                )
                build()
            }
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示正在播放的音频"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = if (isPlaying()) {
            Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_PAUSE
            }
        } else {
            Intent(this, AudioPlaybackService::class.java).apply {
                action = ACTION_PLAY
            }
        }

        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, AudioPlaybackService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentMediaFile?.name ?: "音频播放")
            .setContentText(if (isPlaying()) "正在播放" else "已暂停")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(
                if (isPlaying()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying()) "暂停" else "播放",
                playPausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                "停止",
                stopPendingIntent
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying())
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaSession.release()
        abandonAudioFocus()
    }
}
