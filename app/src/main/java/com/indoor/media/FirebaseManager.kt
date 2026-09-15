package com.indoor.media

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

class FirebaseManager(private val context: Context) {

    data class RemoteItem(
        val name: String,
        val storage: String,
        val type: MediaItem.MediaType,
        val version: Long
    )

    private val prefs: SharedPreferences = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var playlists: PlaylistManager
    private var dbListener: ValueEventListener? = null

    var onPlaylistChanged: (() -> Unit)? = null

    private val enabled: Boolean
        get() = FirebaseConfig.configured

    val deviceId: String by lazy {
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    var deviceName: String
        get() = prefs.getString("device_name", null) ?: ("TV " + deviceId.takeLast(4))
        set(value) {
            prefs.edit().putString("device_name", value).apply()
            if (enabled) pushStatus()
        }

    fun attach(playlistManager: PlaylistManager) {
        playlists = playlistManager
    }

    fun start() {
        if (!enabled) return
        val base = FirebaseDatabase.getInstance().getReference("devices/$deviceId")

        pushStatus()

        dbListener = base.child("playlist/items")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val remote = mutableListOf<RemoteItem>()
                    snapshot.children.forEach { child ->
                        val name = child.key ?: return@forEach
                        val type = when (child.child("type").value?.toString()?.uppercase()) {
                            "IMAGE" -> MediaItem.MediaType.IMAGE
                            "AUDIO" -> MediaItem.MediaType.AUDIO
                            else -> MediaItem.MediaType.VIDEO
                        }
                        remote.add(
                            RemoteItem(
                                name = name,
                                storage = child.child("storage").value?.toString() ?: "",
                                type = type,
                                version = child.child("version").value?.toString()?.toLongOrNull() ?: 0L
                            )
                        )
                    }
                    syncRemote(remote)
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        scope.launch {
            while (isActive) {
                delay(30_000)
                try {
                    base.child("status/lastSeen").setValue(ServerValue.TIMESTAMP)
                } catch (e: Exception) {
                }
            }
        }
    }

    fun stop() {
        if (!enabled) return
        try {
            val base = FirebaseDatabase.getInstance().getReference("devices/$deviceId")
            dbListener?.let { base.child("playlist/items").removeEventListener(it) }
            base.child("status/online").setValue(false)
        } catch (e: Exception) {
        }
    }

    fun reportCurrent(fileName: String?) {
        if (!enabled) return
        try {
            FirebaseDatabase.getInstance().getReference("devices/$deviceId/status")
                .updateChildren(
                    mapOf(
                        "current" to (fileName ?: ""),
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                )
        } catch (e: Exception) {
        }
    }

    private fun pushStatus() {
        try {
            FirebaseDatabase.getInstance().getReference("devices/$deviceId/status")
                .updateChildren(
                    mapOf(
                        "online" to true,
                        "name" to deviceName,
                        "lastSeen" to ServerValue.TIMESTAMP,
                        "current" to "",
                        "itemCount" to playlists.getPlaylist().size
                    )
                )
        } catch (e: Exception) {
        }
    }

    private fun syncRemote(items: List<RemoteItem>) {
        scope.launch {
            val versions = readVersions()
            val synced = readSynced()
            val result = withContext(Dispatchers.IO) {
                for (item in items) {
                    if (item.storage.isEmpty()) continue
                    val localFile = File(playlists.contentDir, item.name)
                    val hasLocal = versions.optLong(item.name, -1L) == item.version && localFile.exists()
                    if (!hasLocal) {
                        val ok = downloadFile(storagePath(item.storage), localFile)
                        if (ok) {
                            versions.put(item.name, item.version)
                        }
                    }
                    if (localFile.exists()) {
                        synced.add(item.name)
                        ensureInPlaylist(item.name, localFile)
                    }
                }
                val names = items.map { it.name }.toSet()
                val toRemove = synced.filter { it !in names }
                toRemove.forEach { name ->
                    playlists.removeItem(name)
                    synced.remove(name)
                    versions.remove(name)
                }
                saveVersions(versions)
                saveSynced(synced)
            }
            try {
                FirebaseDatabase.getInstance().getReference("devices/$deviceId/status/itemCount")
                    .setValue(playlists.getPlaylist().size)
            } catch (e: Exception) {
            }
            withContext(Dispatchers.Main) {
                onPlaylistChanged?.invoke()
            }
        }
    }

    private fun ensureInPlaylist(name: String, file: File) {
        if (playlists.getPlaylist().none { it.fileName == name }) {
            playlists.addItem(file)
        }
    }

    private fun storagePath(path: String): StorageReference {
        return if (path.startsWith("gs://")) {
            FirebaseStorage.getInstance().getReferenceFromUrl(path)
        } else {
            FirebaseStorage.getInstance().reference.child(path)
        }
    }

    private suspend fun downloadFile(ref: StorageReference, dest: File): Boolean =
        suspendCancellableCoroutine { cont ->
            ref.getFile(dest)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }

    private fun readVersions(): JSONObject {
        return try {
            JSONObject(prefs.getString("versions_json", "{}"))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun saveVersions(versions: JSONObject) {
        prefs.edit().putString("versions_json", versions.toString()).apply()
    }

    private fun readSynced(): MutableSet<String> {
        val set = mutableSetOf<String>()
        try {
            val array = JSONArray(prefs.getString("synced_json", "[]"))
            for (i in 0 until array.length()) set.add(array.getString(i))
        } catch (e: Exception) {
        }
        return set
    }

    private fun saveSynced(synced: Set<String>) {
        val array = JSONArray()
        synced.forEach { array.put(it) }
        prefs.edit().putString("synced_json", array.toString()).apply()
    }
}