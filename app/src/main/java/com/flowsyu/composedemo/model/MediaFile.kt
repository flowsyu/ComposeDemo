package com.flowsyu.composedemo.model

import android.net.Uri
import java.io.Serializable

data class MediaFile(
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long = 0, // 毫秒
    val type: MediaType,
    val thumbnail: String? = null
) : Serializable

enum class MediaType {
    AUDIO, VIDEO, IMAGE
}

enum class ViewType {
    LIST, GRID
}
