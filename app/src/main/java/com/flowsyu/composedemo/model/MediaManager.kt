package com.flowsyu.composedemo.model

object MediaManager {
    var mediaList: List<MediaFile> = emptyList()
    var currentMediaIndex: Int = -1
    
    fun setPlaylist(list: List<MediaFile>, currentIndex: Int) {
        mediaList = list
        currentMediaIndex = currentIndex
    }
}
