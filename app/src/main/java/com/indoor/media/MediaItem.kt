package com.indoor.media

data class MediaItem(
    val id: String,
    val fileName: String,
    val path: String,
    val type: MediaType,
    val durationSeconds: Int = 0
) {
    enum class MediaType { VIDEO, IMAGE, AUDIO }
}