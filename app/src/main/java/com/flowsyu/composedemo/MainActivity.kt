package com.flowsyu.composedemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.flowsyu.composedemo.model.MediaType
import com.flowsyu.composedemo.navigation.Screen
import com.flowsyu.composedemo.navigation.toMediaFile
import com.flowsyu.composedemo.navigation.toJson
import com.flowsyu.composedemo.ui.screen.*
import com.flowsyu.composedemo.ui.theme.ComposeDemoTheme
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.Coil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()
        setContent {
            ComposeDemoTheme {
                MediaPlayerApp()
            }
        }
    }
}

@Composable
fun MediaPlayerApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.MediaLibrary
    ) {
        composable<Screen.MediaLibrary>(
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() }
        ) {
            MediaLibraryScreen(
                onOpenSettings = {
                    navController.navigate(Screen.Settings)
                },
                onMediaFileClick = { mediaFile ->
                    when (mediaFile.type) {
                        MediaType.VIDEO -> {
                            navController.navigate(
                                Screen.VideoPlayer(mediaFile.toJson())
                            )
                        }

                        MediaType.AUDIO -> {
                            navController.navigate(
                                Screen.AudioPlayer(mediaFile.toJson())
                            )
                        }
                        
                        MediaType.IMAGE -> {
                            navController.navigate(
                                Screen.ImageViewer(mediaFile.toJson())
                            )
                        }
                    }
                }
            )
        }

        composable<Screen.Settings>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            SettingsScreen(
                onBack = { navController.navigateUp() }
            )
        }

        composable<Screen.VideoPlayer>(
            enterTransition = {
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.VideoPlayer>()
            val mediaFile = args.mediaFileJson.toMediaFile()
            VideoPlayerScreen(
                mediaFile = mediaFile,
                onBack = { navController.navigateUp() }
            )
        }

        composable<Screen.AudioPlayer>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.AudioPlayer>()
            val mediaFile = args.mediaFileJson.toMediaFile()
            AudioPlayerScreen(
                mediaFile = mediaFile,
                onBack = { navController.navigateUp() }
            )
        }

        composable<Screen.ImageViewer>(
            enterTransition = { 
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300)) 
            },
            popExitTransition = { 
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ImageViewer>()
            val mediaFile = args.mediaFileJson.toMediaFile()
            ImageViewerScreen(
                mediaFile = mediaFile,
                onBack = { navController.navigateUp() }
            )
        }
    }
}