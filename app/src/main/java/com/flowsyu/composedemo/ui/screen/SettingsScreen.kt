package com.flowsyu.composedemo.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowsyu.composedemo.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scanDirectory by viewModel.scanDirectory.collectAsState()
    var directoryInput by remember { mutableStateOf("") }
    
    // 打开目录选择器
    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // 获取永久读写权限 (虽然对于 MediaScanner 扫描路径来说可能不是必须的，但对于访问 Tree Uri 是必须的)
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 尝试解析路径
            val path = getPathFromUri(it)
            if (path != null) {
                directoryInput = path
                viewModel.setScanDirectory(path)
            } else {
                 // 如果无法解析，则使用 URI 字符串，但这对于 MediaStore 扫描可能无效
                 // 这里我们提示用户选择正确的位置
            }
        }
    }
    
    LaunchedEffect(scanDirectory) {
        directoryInput = scanDirectory
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                .padding(16.dp)
        ) {
            Text(
                text = "媒体扫描目录",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "留空则扫描全部媒体文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = directoryInput,
                onValueChange = {}, // 只读
                readOnly = true,
                label = { Text("目录路径") },
                placeholder = { Text("点击右侧图标选择目录 ->") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { directoryLauncher.launch(null) },
                enabled = false, // 禁用直接输入，但外层 clickable 可用（需调整颜色）
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = {
                    IconButton(onClick = { directoryLauncher.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择目录")
                    }
                },
                singleLine = false,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    // 如果用户想清除设置，可以点击这个按钮清空
                    if (directoryInput.isEmpty()) {
                         directoryLauncher.launch(null)
                    } else {
                         directoryInput = ""
                         viewModel.setScanDirectory("")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (directoryInput.isEmpty()) "选择目录" else "清除并扫描所有")
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "提示",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• 点击文件夹图标选择目录\n" +
                        "• 若选择 /storage/emulated/0/Music，则仅扫描该目录下的文件\n" +
                        "• 部分Android版本由于权限限制，可能无法选择根目录，建议选择具体的媒体文件夹(如Music, Video, Download)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 辅助函数：尝试从 Tree Uri 解析出文件系统路径
// 注意：这是一个简化的实现，主要针对 ExternalStorageProvider
private fun getPathFromUri(uri: Uri): String? {
    try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":")
        if (parts.size >= 2) {
            val type = parts[0]
            val path = parts[1]
            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().toString() + "/" + path
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
