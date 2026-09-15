package com.indoor.media

import android.content.Context
import android.util.Log
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

class CloudSocketManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSocket"
    }

    private var socket: Socket? = null
    private var connected = false

    var onManifestReceived: ((JSONObject) -> Unit)? = null
    var onCommandReceived: ((String, Any?) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onPairingCodeReceived: ((String) -> Unit)? = null
    var onPairedConfirmed: (() -> Unit)? = null

    fun connect() {
        if (socket?.connected() == true) return

        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 10000
                timeout = 20000
            }
            socket = IO.socket(URI.create(AppPreferences.SERVER_URL), opts)
        } catch (e: Exception) {
            Log.e(TAG, "Socket creation failed: ${e.message}")
            return
        }

        socket?.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "Connected")
            connected = true
            sendHello()
            onConnected?.invoke()
        }

        socket?.on(Socket.EVENT_DISCONNECT) { args ->
            Log.d(TAG, "Disconnected: ${args.firstOrNull()}")
            connected = false
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "Connect error: ${args.firstOrNull()}")
            connected = false
        }

        socket?.on("stream:manifest") { args ->
            val data = args.firstOrNull() as? JSONObject
            if (data != null) {
                Log.d(TAG, "Manifest received")
                onManifestReceived?.invoke(data)
            }
        }

        socket?.on("device:command") { args ->
            val data = args.firstOrNull() as? JSONObject
            if (data != null) {
                val action = data.optString("action", "")
                val value = data.opt("value")
                Log.d(TAG, "Command: $action")
                onCommandReceived?.invoke(action, value)
            }
        }

        socket?.connect()
    }

    private fun sendHello() {
        val data = JSONObject().apply {
            put("uniqueId", AppPreferences.getDeviceId(context))
            put("appVersion", "1.0.0")
            put("model", AppPreferences.getModel())
            put("androidVersion", AppPreferences.getAndroidVersion())
        }
        socket?.emit("device:hello", data, Ack { args ->
            val response = args.firstOrNull() as? JSONObject
            if (response != null && response.has("needPairing")) {
                val needPairing = response.optBoolean("needPairing", false)
                val pairingCode = response.optString("pairingCode", null)
                if (needPairing) {
                    AppPreferences.setPaired(context, false)
                    if (pairingCode != null && pairingCode.isNotEmpty()) {
                        AppPreferences.setPairingCode(context, pairingCode)
                        onPairingCodeReceived?.invoke(pairingCode)
                    }
                } else {
                    AppPreferences.setPaired(context, true)
                    onPairedConfirmed?.invoke()
                }
            }
        })
    }

    fun reHello() {
        sendHello()
    }

    fun sendStatus(currentContent: String, itemCount: Int, volume: Int) {
        val data = JSONObject().apply {
            put("currentContent", currentContent)
            put("itemCount", itemCount)
            put("volume", volume)
        }
        socket?.emit("device:status", data)
    }

    fun sendProof(contentId: String, contentName: String, type: String, startedAt: String, endedAt: String, durationSeconds: Int) {
        val data = JSONObject().apply {
            put("contentId", contentId)
            put("contentName", contentName)
            put("type", type)
            put("startedAt", startedAt)
            put("endedAt", endedAt)
            put("durationSeconds", durationSeconds)
        }
        socket?.emit("device:proof", data)
    }

    fun isConnected(): Boolean = connected && socket?.connected() == true

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        connected = false
    }
}