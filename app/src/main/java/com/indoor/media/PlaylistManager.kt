package com.indoor.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PlaylistManager(private val context: Context) {

    val contentDir: File
        get() = context.getExternalFilesDir(null) ?: context.filesDir

    private val playlistFile: File
        get() = File(contentDir, "playlist.json")

    private val playlist = mutableListOf<MediaItem>()

    fun init() {
        contentDir.mkdirs()
        loadPlaylist()
        scanFiles()
        savePlaylist()
    }

    fun scanFiles() {
        val existingIds = playlist.associateBy { it.path }
        contentDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val type = when (file.extension.lowercase()) {
                    "mp4", "mkv", "webm", "avi", "mov" -> MediaItem.MediaType.VIDEO
                    "jpg", "jpeg", "png", "gif", "webp", "bmp" -> MediaItem.MediaType.IMAGE
                    "mp3", "wav", "aac", "ogg", "m4a", "flac" -> MediaItem.MediaType.AUDIO
                    else -> return@forEach
                }
                if (existingIds[file.absolutePath] == null) {
                    playlist.add(
                        MediaItem(
                            id = System.currentTimeMillis().toString() + file.name.hashCode(),
                            fileName = file.name,
                            path = file.absolutePath,
                            type = type,
                            durationSeconds = getMediaDuration(file, type)
                        )
                    )
                }
            }
        }
    }

    fun getPlaylist(): List<MediaItem> = playlist.sortedBy { it.fileName }

    fun addItem(file: File): MediaItem {
        val type = when (file.extension.lowercase()) {
            "mp4", "mkv", "webm", "avi", "mov" -> MediaItem.MediaType.VIDEO
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> MediaItem.MediaType.IMAGE
            "mp3", "wav", "aac", "ogg", "m4a", "flac" -> MediaItem.MediaType.AUDIO
            else -> return error("Unsupported file type: ${file.extension}")
        }
        val item = MediaItem(
            id = System.currentTimeMillis().toString() + file.name.hashCode(),
            fileName = file.name,
            path = file.absolutePath,
            type = type,
            durationSeconds = getMediaDuration(file, type)
        )
        playlist.add(item)
        savePlaylist()
        return item
    }

    fun removeItem(fileName: String) {
        val item = playlist.find { it.fileName == fileName }
        if (item != null) {
            File(item.path).delete()
            playlist.remove(item)
            savePlaylist()
        }
    }

    fun clearAll() {
        contentDir.listFiles()?.forEach { it.delete() }
        playlist.clear()
        savePlaylist()
    }

    private fun getMediaDuration(file: File, type: MediaItem.MediaType): Int {
        if (type == MediaItem.MediaType.IMAGE) return 5
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this.context, Uri.fromFile(file))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt() ?: 0
            retriever.release()
            (duration / 1000).coerceAtLeast(1)
        } catch (e: Exception) {
            30
        }
    }

    private fun savePlaylist() {
        val json = JSONObject().apply {
            val array = JSONArray()
            playlist.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("fileName", item.fileName)
                        put("path", item.path)
                        put("type", item.type.name)
                        put("duration", item.durationSeconds)
                    }
                )
            }
            put("playlist", array)
        }
        playlistFile.writeText(json.toString())
    }

    private fun loadPlaylist() {
        if (!playlistFile.exists()) return
        try {
            val json = JSONObject(playlistFile.readText())
            val array = json.getJSONArray("playlist")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                playlist.add(
                    MediaItem(
                        id = obj.getString("id"),
                        fileName = obj.getString("fileName"),
                        path = obj.getString("path"),
                        type = MediaItem.MediaType.valueOf(obj.getString("type")),
                        durationSeconds = obj.getInt("duration")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}