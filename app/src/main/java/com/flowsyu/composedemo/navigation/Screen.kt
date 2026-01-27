package com.flowsyu.composedemo.navigation

import android.net.Uri
import com.flowsyu.composedemo.model.MediaFile
import com.flowsyu.composedemo.model.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder

@Serializable
sealed class Screen {
    @Serializable
    data object MediaLibrary : Screen()
    
    @Serializable
    data object Settings : Screen()
    
    @Serializable
    data class VideoPlayer(val mediaFileJson: String) : Screen()
    
    @Serializable
    data class AudioPlayer(val mediaFileJson: String) : Screen()
}

fun MediaFile.toJson(): String {
    val json = Json.encodeToString(
        mapOf(
            "uri" to uri.toString(),
            "name" to name,
            "path" to path,
            "size" to size.toString(),
            "duration" to duration.toString(),
            "type" to type.name
        )
    )
    return URLEncoder.encode(json, "UTF-8")
}

fun String.toMediaFile(): MediaFile {
    val decoded = URLDecoder.decode(this, "UTF-8")
    val map = Json.decodeFromString<Map<String, String>>(decoded)
    return MediaFile(
        uri = Uri.parse(map["uri"]),
        name = map["name"] ?: "",
        path = map["path"] ?: "",
        size = map["size"]?.toLongOrNull() ?: 0L,
        duration = map["duration"]?.toLongOrNull() ?: 0L,
        type = MediaType.valueOf(map["type"] ?: "AUDIO")
    )
}
