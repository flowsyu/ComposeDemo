# Android 音视频播放器

使用 Jetpack Compose 和 MediaPlayer 实现的功能完整的音视频播放器应用。

## 功能特性

### 1. 侧滑设置页面
- 使用 ModalNavigationDrawer 实现侧滑菜单
- 支持设置媒体文件扫描目录
- 使用 DataStore 持久化保存设置

### 2. 主界面 (MediaLibraryScreen)
- **搜索功能**：支持按文件名实时搜索媒体文件
- 自动扫描系统中的音视频文件
- **视频缩略图**：支持显示视频封面
- 支持列表视图和网格视图切换
- 显示文件名、时长、大小等信息
- 权限管理（存储权限、通知权限）
- 下拉刷新功能

### 3. 视频播放器 (VideoPlayerScreen)
- 全屏播放视频，支持**多种画面比例**适配
- **播放列表**：支持侧边栏（横屏）/ 底部弹窗（竖屏）查看和切换视频
- 播放控制：播放/暂停、快进/快退10秒
- 拖动进度条跳转
- 倍速播放（0.5x - 2.0x），**带选中状态指示**
- 点击屏幕显示/隐藏控制栏
- 自动隐藏控制栏
- **无缝转场**：视频加载前显示封面图，防止闪屏

### 4. 音频播放器 (AudioPlayerScreen)
- 美观的音频播放界面
- **播放列表**：底部弹窗查看当前播放队列
- 后台播放支持（通过 Service 实现）
- MediaSession 集成，支持系统控制中心
- 前台服务通知，显示播放状态
- 音频焦点管理
- 播放控制：播放/暂停、快进/快退10秒、进度拖动

### 5. 多端适配与体验优化
- **Android TV 适配**：
  - 支持遥控器方向键导航（焦点高亮、缩放动画）
  - 支持遥控器媒体按键控制（确认键、播放/暂停键）
  - TV Launcher 入口支持
- **转场动画**：页面切换添加平滑过渡动画（进入/退出、缩放、淡入淡出）
- **性能优化**：SurfaceView 生命周期管理，防止内存泄漏

## 技术栈

- **UI 框架**: Jetpack Compose + Material 3
- **导航**: Navigation Compose + Type-safe navigation
- **状态管理**: ViewModel + StateFlow
- **图片加载**: Coil (支持视频帧解码)
- **数据持久化**: DataStore Preferences
- **媒体播放**: MediaPlayer
- **后台服务**: Foreground Service + MediaSession
- **权限管理**: Accompanist Permissions
- **序列化**: Kotlinx Serialization

## 项目结构

```
app/src/main/java/com/flowsyu/composedemo/
├── MainActivity.kt                          # 主 Activity
├── model/
│   └── MediaFile.kt                        # 媒体文件数据模型
├── data/
│   └── SettingsRepository.kt               # 设置数据仓库
├── viewmodel/
│   ├── MediaLibraryViewModel.kt            # 媒体库 ViewModel
│   └── SettingsViewModel.kt                # 设置 ViewModel
├── ui/
│   ├── screen/
│   │   ├── MediaLibraryScreen.kt          # 主界面
│   │   ├── VideoPlayerScreen.kt           # 视频播放器
│   │   ├── AudioPlayerScreen.kt           # 音频播放器
│   │   └── SettingsScreen.kt              # 设置页面
│   └── theme/                              # 主题相关
├── service/
│   └── AudioPlaybackService.kt             # 音频播放后台服务
├── util/
│   └── MediaScanner.kt                     # 媒体扫描工具
└── navigation/
    └── Screen.kt                           # 导航路由定义
```

## 权限说明

应用需要以下权限：

- `READ_EXTERNAL_STORAGE` (Android 12 及以下)
- `READ_MEDIA_AUDIO` (Android 13+)
- `READ_MEDIA_VIDEO` (Android 13+)
- `POST_NOTIFICATIONS` (通知权限)
- `FOREGROUND_SERVICE` (前台服务)
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (媒体播放前台服务)
- `WAKE_LOCK` (保持唤醒)

## 使用说明

1. **首次启动**：应用会请求存储和通知权限
2. **设置扫描目录**：点击设置按钮，输入要扫描的目录路径（留空则扫描全部）
3. **浏览媒体文件**：在主界面可以切换列表/网格视图
4. **播放视频**：点击视频文件进入全屏播放
5. **播放音频**：点击音频文件进入音频播放页面，支持后台播放
6. **控制中心**：音频播放时可在系统通知栏和锁屏界面控制播放

## 主要依赖

```toml
[versions]
compose = "2024.09.00"
navigation = "2.8.0"
lifecycle = "2.8.4"
datastore = "1.1.1"
media = "1.7.0"
accompanist = "0.34.0"

[libraries]
androidx-navigation-compose
androidx-lifecycle-viewmodel-compose
androidx-datastore-preferences
androidx-media
accompanist-permissions
kotlinx-serialization-json
```

## 开发说明

- 最低 SDK：24 (Android 7.0)
- 目标 SDK：36 (Android 14)
- Kotlin 版本：2.0.21
- AGP 版本：9.0.0

## 特色功能

✅ 完全使用 Compose 构建 UI
✅ Material 3 设计风格
✅ Type-safe Navigation
✅ 后台播放音频
✅ MediaSession 集成
✅ 前台服务通知
✅ 音频焦点管理
✅ 视频倍速播放
✅ 自适应权限请求
✅ 列表/网格视图切换
✅ 优雅的加载和错误处理

## 未来改进

- [ ] 支持播放列表
- [ ] 支持字幕显示
- [ ] 添加音频均衡器
- [ ] 支持更多格式
- [ ] 添加收藏功能
- [ ] 播放历史记录
- [ ] 网络流媒体支持
