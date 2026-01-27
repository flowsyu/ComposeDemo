package com.flowsyu.composedemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    onNavigateToLibrary = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Screen.MediaLibrary) {
                                popUpTo(Screen.MediaLibrary) { inclusive = true }
                            }
                        }
                    },
                    onNavigateToSettings = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Screen.Settings)
                        }
                    }
                )
            }
        }
    ) {
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
        }
    }
}

@Composable
fun DrawerContent(
    onNavigateToLibrary: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding()
    ) {
        Text(
            text = "媒体播放器",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("媒体库") },
            selected = false,
            onClick = onNavigateToLibrary
        )
        NavigationDrawerItem(
            label = { Text("设置") },
            selected = false,
            onClick = onNavigateToSettings
        )
    }
}
