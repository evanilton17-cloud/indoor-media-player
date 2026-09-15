package com.indoor.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem as ExoMediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ManifestPlayer(
    private val context: Context,
    private val playerView: PlayerView,
    private val imageView: ImageView,
    private val onStatusUpdate: (String, Int, Int) -> Unit
) {

    companion object {
        private const val TAG = "ManifestPlayer"
    }

    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var manifestItems = mutableListOf<ManifestItem>()
    private var currentIndex = 0
    private var isShowingImage = false
    private var imageStartTime = 0L
    private var currentPlayingId: String? = null
    private var imageRunnable: Runnable? = null

    var onProofEvent: ((String, String, String, String, String, Int) -> Unit)? = null
    var onContentChanged: ((String) -> Unit)? = null

    data class ManifestItem(
        val id: String,
        val name: String,
        val type: String,
        val storageUrl: String,
        val durationSeconds: Int,
        val version: Int
    )

    init {
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && !isShowingImage) {
                        handler.post { playNext() }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Playback error: ${error.message}")
                    handler.post { playNext() }
                }
            })
            repeatMode = Player.REPEAT_MODE_OFF
            setAudioAttributes(
                com.google.android.exoplayer2.audio.AudioAttributes.Builder()
                    .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                    .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
        }
        playerView.player = exoPlayer
        playerView.useController = false
        playerView.setKeepContentOnPlayerReset(true)
    }

    fun onManifestReceived(json: JSONObject) {
        val items = mutableListOf<ManifestItem>()
        val arr = json.optJSONArray("items")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                items.add(
                    ManifestItem(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        type = obj.optString("type", "VIDEO").uppercase(),
                        storageUrl = obj.optString("storageUrl", ""),
                        durationSeconds = obj.optInt("durationSeconds", 10),
                        version = obj.optInt("version", 0)
                    )
                )
            }
        }
if (items.isEmpty()) {
            Log.d(TAG, "Empty manifest received")
            return
        }

        val previousId = if (currentIndex < manifestItems.size) manifestItems[currentIndex].id else null
        manifestItems.clear()
        manifestItems.addAll(items)

        currentIndex = 0
        if (previousId != null) {
            val newIdx = manifestItems.indexOfFirst { it.id == previousId }
            if (newIdx >= 0) {
                currentIndex = newIdx
            }
        }

        downloadAndPlay()
    }

    fun setLocalItems(items: List<ManifestItem>) {
        manifestItems.clear()
        manifestItems.addAll(items)
        currentIndex = 0
        currentPlayingId = null
        downloadAndPlay()
    }

    private fun downloadAndPlay() {
        if (manifestItems.isEmpty()) {
            handler.postDelayed({ downloadAndPlay() }, 5000)
            return
        }
        if (currentIndex >= manifestItems.size) currentIndex = 0
        val item = manifestItems[currentIndex]

        Thread {
            val file = downloadFile(item)
            if (file != null && file.exists()) {
                handler.post { playItem(item, file) }
            } else {
                Log.e(TAG, "Failed to download: ${item.name}")
                handler.post { playNext() }
            }
        }.start()
    }

    private fun playItem(item: ManifestItem, file: File) {
        if (currentPlayingId != item.id) {
            emitProofStarted(item)
            currentPlayingId = item.id
            onContentChanged?.invoke(item.name)
        }

        val volume = ((exoPlayer?.volume ?: 1f) * 100).toInt()
        onStatusUpdate(item.name, manifestItems.size, volume)

        when {
            item.type == "VIDEO" || item.type == "AUDIO" -> playMedia(item, file)
            item.type == "IMAGE" -> showImage(item, file)
        }
    }

    private fun playMedia(item: ManifestItem, file: File) {
        clearImage()
        isShowingImage = false
        imageView.visibility = View.GONE
        playerView.visibility = View.VISIBLE

        val mediaItem = ExoMediaItem.fromUri(Uri.fromFile(file))
        exoPlayer?.setMediaItem(mediaItem)
        exoPlayer?.prepare()
        exoPlayer?.play()
    }

    private fun showImage(item: ManifestItem, file: File) {
        isShowingImage = true
        imageStartTime = System.currentTimeMillis()
        playerView.visibility = View.GONE
        imageView.visibility = View.VISIBLE

        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            imageView.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Image decode error: ${e.message}")
            playNext()
            return
        }

        val durationMs = (item.durationSeconds * 1000).toLong()
        imageRunnable = Runnable { playNext() }
        handler.postDelayed(imageRunnable!!, durationMs)
    }

    private fun clearImage() {
        imageRunnable?.let { handler.removeCallbacks(it) }
        imageRunnable = null
        val drawable = imageView.drawable
        if (drawable is android.graphics.drawable.BitmapDrawable) {
            val bitmap = drawable.bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        imageView.setImageDrawable(null)
    }

    private fun emitProofStarted(item: ManifestItem) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        onProofEvent?.invoke(item.id, item.name, item.type, now, "", 0)
    }

    private fun emitProofEnded(item: ManifestItem) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val duration = if (item.type == "IMAGE") {
            ((System.currentTimeMillis() - imageStartTime) / 1000).toInt()
        } else {
            item.durationSeconds
        }
        onProofEvent?.invoke(item.id, item.name, item.type, "", now, duration)
    }

    private fun playNext() {
        if (manifestItems.isEmpty()) return

        if (currentPlayingId != null && currentIndex < manifestItems.size) {
            emitProofEnded(manifestItems[currentIndex])
        }

        currentIndex++
        if (currentIndex >= manifestItems.size) currentIndex = 0
        downloadAndPlay()
    }

    fun onCommand(action: String, value: Any?) {
        when (action) {
            "PLAY" -> exoPlayer?.play()
            "PAUSE" -> exoPlayer?.pause()
            "STOP" -> {
                exoPlayer?.stop()
                clearImage()
            }
            "VOLUME" -> {
                val vol = (value as? Number)?.toFloat()?.div(100f) ?: return
                exoPlayer?.volume = vol.coerceIn(0f, 1f)
            }
            "SCREEN_OFF" -> {
                exoPlayer?.pause()
                isShowingImage = false
                playerView.visibility = View.GONE
                imageView.visibility = View.GONE
            }
            "SCREEN_ON" -> {
                imageView.visibility = View.VISIBLE
                playerView.visibility = View.VISIBLE
            }
            "REFRESH" -> {
                exoPlayer?.stop()
                clearImage()
                manifestItems.clear()
                currentIndex = 0
                currentPlayingId = null
            }
        }
    }

    private fun downloadFile(item: ManifestItem): File? {
        if (item.storageUrl.startsWith("file://")) {
            return try {
                val localFile = File(Uri.parse(item.storageUrl).path ?: return null)
                if (localFile.exists()) localFile else null
            } catch (e: Exception) {
                null
            }
        }

        val ext = getExtension(item.storageUrl)
        val filename = "${item.id}.$ext"
        val contentDir = context.getExternalFilesDir(null) ?: context.filesDir ?: return null
        val file = File(contentDir, filename)
        val versionFile = File(contentDir, "${filename}.version")

        val cachedVersion = try {
            if (versionFile.exists()) versionFile.readText().trim().toIntOrNull() else null
        } catch (e: Exception) {
            null
        }

        if (file.exists() && cachedVersion == item.version) return file

        if (file.exists()) file.delete()

        val tmpFile = File(contentDir, "${filename}.tmp")
        try {
            val request = Request.Builder().url(item.storageUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                tmpFile.delete()
                return null
            }
            val body = response.body ?: return null
            FileOutputStream(tmpFile).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
            tmpFile.renameTo(file)
            try {
                versionFile.writeText(item.version.toString())
            } catch (e: Exception) {
            }
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            tmpFile.delete()
            return null
        }
    }

    private fun getExtension(url: String): String {
        val path = url.split("?").first()
        val lastSegment = path.split("/").lastOrNull() ?: "bin"
        return if (lastSegment.contains(".")) {
            lastSegment.substringAfterLast(".")
        } else {
            "bin"
        }
    }

    fun release() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        handler.removeCallbacksAndMessages(null)
        clearImage()
    }
}
