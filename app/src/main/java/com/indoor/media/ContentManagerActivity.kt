package com.indoor.media

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration

class ContentManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ContentAdapter
    private lateinit var playlistManager: PlaylistManager
    private lateinit var statusText: TextView
    private var currentPort: Int = -1

    private val portReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UploadServerService.ACTION_START) {
                currentPort = intent.getIntExtra(UploadServerService.LISTEN_PORT, -1)
                updateStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content_manager)

        playlistManager = PlaylistManager(this)
        playlistManager.init()

        recyclerView = findViewById(R.id.contentList)
        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(48, 24, 48, 24)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ContentAdapter { fileName ->
            playlistManager.removeItem(fileName)
            refreshList()
        }
        recyclerView.adapter = adapter

        startUploadServer()

        registerReceiver(portReceiver, IntentFilter(UploadServerService.ACTION_START))

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = getLocalIpAddress() ?: wifiManager.connectionInfo.ipAddress
            .let { (it and 0xff).toString() + "." + ((it shr 8) and 0xff) + "." + ((it shr 16) and 0xff) + "." + ((it shr 24) and 0xff) }

        refreshList()

        val finalIp = ip
        handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            updateStatus()
        }, 800)
        localIp = finalIp
    }

    private lateinit var handler: android.os.Handler
    private var localIp: String = ""

    override fun onStart() {
        super.onStart()
        updateStatus()
    }

    private fun startUploadServer() {
        val intent = Intent(this, UploadServerService::class.java)
        intent.action = UploadServerService.ACTION_START
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun refreshList() {
        adapter.submitList(playlistManager.getPlaylist())
    }

    private fun updateStatus() {
        val ip = localIp
        if (currentPort > 0) {
            statusText.text = "Servidor de upload ativo\nAbra no navegador:  http://$ip:$currentPort"
        } else {
            statusText.text = "Iniciando servidor de upload..."
        }
        statusText.visibility = android.view.View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(portReceiver)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addresses: Enumeration<InetAddress> = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
        }
        return null
    }
}