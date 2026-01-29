package com.flowsyu.composedemo.ui.screen

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowsyu.composedemo.model.MediaFile
import com.flowsyu.composedemo.model.MediaType
import com.flowsyu.composedemo.model.ViewType
import com.flowsyu.composedemo.util.formatDuration
import com.flowsyu.composedemo.util.formatFileSize
import com.flowsyu.composedemo.viewmodel.MediaLibraryViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

import com.flowsyu.composedemo.model.MediaManager
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import com.flowsyu.composedemo.ui.preview.DevicePreviews
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import android.content.res.Configuration
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MediaLibraryScreen(
    onOpenSettings: () -> Unit,
    onMediaFileClick: (MediaFile) -> Unit,
    viewModel: MediaLibraryViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    MediaLibraryScreenContent(
        mediaFiles = state.mediaFiles,
        isLoading = state.isLoading,
        error = state.error,
        viewType = state.viewType,
        permissionsGranted = permissionsState.allPermissionsGranted,
        onOpenSettings = onOpenSettings,
        onMediaFileClick = onMediaFileClick,
        onToggleViewType = viewModel::toggleViewType,
        onRefresh = { viewModel.loadMediaFiles() },
        onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreenContent(
    mediaFiles: List<MediaFile>,
    isLoading: Boolean,
    error: String?,
    viewType: ViewType,
    permissionsGranted: Boolean,
    onOpenSettings: () -> Unit,
    onMediaFileClick: (MediaFile) -> Unit,
    onToggleViewType: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val itemFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var lastSelectedUri by rememberSaveable { mutableStateOf<String?>(null) }
    
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val gridMinSize = if (isTv) 150.dp else 120.dp

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(isSearching) {
        if (isSearching) {
            searchFieldFocusRequester.requestFocus()
        }
    }
    
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }
    
    val filteredMediaFiles = remember(mediaFiles, searchQuery) {
        if (searchQuery.isBlank()) {
            mediaFiles
        } else {
            mediaFiles.filter { 
                it.name.contains(searchQuery, ignoreCase = true) 
            }
        }
    }
    
    val handleMediaClick: (MediaFile) -> Unit = { file ->
        lastSelectedUri = file.uri.toString()
        val index = filteredMediaFiles.indexOfFirst { it.uri == file.uri }
        if (index != -1) {
            MediaManager.setPlaylist(filteredMediaFiles, index)
        }
        onMediaFileClick(file)
    }

    LaunchedEffect(lastSelectedUri, filteredMediaFiles, viewType) {
        val key = lastSelectedUri ?: return@LaunchedEffect
        val index = filteredMediaFiles.indexOfFirst { it.uri.toString() == key }
        if (index >= 0) {
            if (viewType == ViewType.LIST) {
                listState.scrollToItem(index)
            } else {
                gridState.scrollToItem(index)
            }
            withFrameNanos { }
            itemFocusRequesters[key]?.requestFocus()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFieldFocusRequester),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, "清除")
                                    }
                                }
                            }
                        )
                    } else {
                        Text("媒体库 (${filteredMediaFiles.size})") 
                    }
                },
                navigationIcon = {
                    if (isSearching) {
                        IconButton(onClick = { 
                            isSearching = false 
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, "关闭搜索")
                        }
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(
                            onClick = { isSearching = true },
                            modifier = Modifier.focusRequester(focusRequester)
                        ) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                        IconButton(onClick = onToggleViewType) {
                            Icon(
                                if (viewType == ViewType.LIST) Icons.Default.GridView 
                                else Icons.Default.List,
                                contentDescription = "切换视图"
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !permissionsGranted -> {
                    PermissionRequestContent(
                        onRequestPermission = onRequestPermission
                    )
                }
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    ErrorContent(
                        error = error,
                        onRetry = onRefresh
                    )
                }
                filteredMediaFiles.isEmpty() -> {
                    if (searchQuery.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("未找到相关文件")
                        }
                    } else {
                        EmptyContent()
                    }
                }
                else -> {
                    if (viewType == ViewType.LIST) {
                        MediaListView(
                            mediaFiles = filteredMediaFiles,
                            listState = listState,
                            focusRequesterForItem = { file ->
                                itemFocusRequesters.getOrPut(file.uri.toString()) { FocusRequester() }
                            },
                            onMediaFileClick = handleMediaClick
                        )
                    } else {
                        MediaGridView(
                            mediaFiles = filteredMediaFiles,
                            gridState = gridState,
                            minSize = gridMinSize,
                            focusRequesterForItem = { file ->
                                itemFocusRequesters.getOrPut(file.uri.toString()) { FocusRequester() }
                            },
                            onMediaFileClick = handleMediaClick
                        )
                    }
                }
            }
        }
    }
}

@DevicePreviews
@Composable
fun MediaLibraryScreenPreview() {
    val sampleFiles = (1..10).map {
        MediaFile(
            uri = android.net.Uri.parse("content://media/external/images/media/$it"),
            name = "Sample Media $it",
            path = "/path/to/media/$it",
            size = 1024 * 1024L * it,
            duration = 60000L * it,
            type = if (it % 2 == 0) MediaType.VIDEO else MediaType.AUDIO
        )
    }

    MaterialTheme {
        MediaLibraryScreenContent(
            mediaFiles = sampleFiles,
            isLoading = false,
            error = null,
            viewType = ViewType.GRID,
            permissionsGranted = true,
            onOpenSettings = {},
            onMediaFileClick = {},
            onToggleViewType = {},
            onRefresh = {},
            onRequestPermission = {}
        )
    }
}

@Composable
fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "需要存储权限",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "需要访问存储权限来扫描音视频文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("授予权限")
        }
    }
}

@Composable
fun ErrorContent(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "加载失败",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "没有找到媒体文件",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "尝试在设置中修改扫描目录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
fun MediaListView(
    mediaFiles: List<MediaFile>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusRequesterForItem: (MediaFile) -> FocusRequester,
    onMediaFileClick: (MediaFile) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(8.dp)
    ) {
        items(mediaFiles, key = { it.uri.toString() }) { mediaFile ->
            MediaListItem(
                mediaFile = mediaFile,
                focusRequester = focusRequesterForItem(mediaFile),
                onClick = { onMediaFileClick(mediaFile) }
            )
        }
    }
}

@Composable
fun MediaListItem(
    mediaFile: MediaFile,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)
    val isPreview = LocalInspectionMode.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp) // Match Card shape + padding
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (mediaFile.type) {
                MediaType.VIDEO -> {
                    if (isPreview) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoLibrary, null, tint = Color.White)
                        }
                    } else {
                        AsyncImage(
                            model = mediaFile.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.VideoLibrary),
                            placeholder = rememberVectorPainter(Icons.Default.VideoLibrary)
                        )
                    }
                }
                MediaType.IMAGE -> {
                    if (isPreview) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, null, tint = Color.White)
                        }
                    } else {
                        AsyncImage(
                            model = mediaFile.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.Image),
                            placeholder = rememberVectorPainter(Icons.Default.Image)
                        )
                    }
                }
                MediaType.AUDIO -> {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaFile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    if (mediaFile.type != MediaType.IMAGE) {
                        Text(
                            text = formatDuration(mediaFile.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = formatFileSize(mediaFile.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Show explicit focus indicator or just rely on the border
            if (isFocused) {
               Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.primary
               )
            } else {
                // Show nothing or gray arrow? default was PlayArrow 
               Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
               )
            }
        }
    }
}

@Composable
fun MediaGridView(
    mediaFiles: List<MediaFile>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    minSize: androidx.compose.ui.unit.Dp,
    focusRequesterForItem: (MediaFile) -> FocusRequester,
    onMediaFileClick: (MediaFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(8.dp)
    ) {
        items(mediaFiles, key = { it.uri.toString() }) { mediaFile ->
            MediaGridItem(
                mediaFile = mediaFile,
                focusRequester = focusRequesterForItem(mediaFile),
                onClick = { onMediaFileClick(mediaFile) }
            )
        }
    }
}

@Composable
fun MediaGridItem(
    mediaFile: MediaFile,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f)
    val isPreview = LocalInspectionMode.current

    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (mediaFile.type) {
                MediaType.VIDEO -> {
                    if (isPreview) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoLibrary, null, tint = Color.White)
                        }
                    } else {
                         AsyncImage(
                            model = mediaFile.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.VideoLibrary),
                            placeholder = rememberVectorPainter(Icons.Default.VideoLibrary)
                        )
                    }
                }
                MediaType.IMAGE -> {
                    if (isPreview) {
                         Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, null, tint = Color.White)
                        }
                    } else {
                        AsyncImage(
                            model = mediaFile.uri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.Image),
                            placeholder = rememberVectorPainter(Icons.Default.Image)
                        )
                    }
                }
                MediaType.AUDIO -> {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = mediaFile.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(mediaFile.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
