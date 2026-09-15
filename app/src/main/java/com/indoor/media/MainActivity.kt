package com.indoor.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ui.PlayerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var imageView: ImageView
    private lateinit var manifestPlayer: ManifestPlayer
    private lateinit var playlistManager: PlaylistManager
    private lateinit var firebaseManager: FirebaseManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private val handler = Handler(Looper.getMainLooper())
    private var cloudSocketManager: CloudSocketManager? = null
    private var cloudActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AppPreferences.isPaired(this)) {
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        imageView = findViewById(R.id.imageView)

        initPlayer()

        playlistManager = PlaylistManager(this)
        playlistManager.init()

        FirebaseConfig.init(this)
        firebaseManager = FirebaseManager(this)
        firebaseManager.attach(playlistManager)
        firebaseManager.onPlaylistChanged = {
            runOnUiThread {
                if (!cloudActive) {
                    handler.postDelayed({ scheduleLocalPlayback() }, 600)
                }
            }
        }
        firebaseManager.start()

        acquireWakeLock()
        overridePendingTransition(0, 0)

        cloudSocketManager = CloudSocketManager(this).apply {
            onManifestReceived = { manifest ->
                runOnUiThread {
                    cloudActive = true
                    manifestPlayer.onManifestReceived(manifest)
                }
            }
            onCommandReceived = { action, value -> handleCloudCommand(action, value) }
            connect()
        }

        handler.postDelayed({
            if (!cloudActive && playlistManager.getPlaylist().isNotEmpty()) {
                scheduleLocalPlayback()
            }
        }, 8000)
    }

    private fun initPlayer() {
        manifestPlayer = ManifestPlayer(this, playerView, imageView) { content, count, vol ->
            firebaseManager.reportCurrent(content)
            cloudSocketManager?.sendStatus(content, count, vol)
        }
        manifestPlayer.onProofEvent = { id, name, type, started, ended, dur ->
            cloudSocketManager?.sendProof(id, name, type, started, ended, dur)
        }
        playerView.setOnClickListener {
            manifestPlayer.onCommand("STOP", null)
            startActivity(Intent(this@MainActivity, ContentManagerActivity::class.java))
        }
    }

    private fun handleCloudCommand(action: String, value: Any?) {
        when (action) {
            "REBOOT" -> runOnUiThread { restartApp() }
            "UNPAIR" -> runOnUiThread { unpairDevice() }
            "REFRESH" -> runOnUiThread {
                manifestPlayer.onCommand("REFRESH", null)
                cloudSocketManager?.disconnect()
                cloudSocketManager?.connect()
            }
            "SCREEN_OFF" -> runOnUiThread {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                manifestPlayer.onCommand(action, value)
            }
            "SCREEN_ON" -> runOnUiThread {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                manifestPlayer.onCommand(action, value)
            }
            else -> runOnUiThread { manifestPlayer.onCommand(action, value) }
        }
    }

    private fun scheduleLocalPlayback() {
        if (cloudActive || isFinishing || isDestroyed) return
        val items = playlistManager.getPlaylist().map { item ->
            ManifestPlayer.ManifestItem(
                id = item.id,
                name = item.fileName,
                type = item.type.name,
                storageUrl = Uri.fromFile(File(item.path)).toString(),
                durationSeconds = item.durationSeconds,
                version = 0
            )
        }
        if (items.isEmpty()) {
            handler.postDelayed({ scheduleLocalPlayback() }, 3000)
            return
        }
        manifestPlayer.setLocalItems(items)
    }

    private fun restartApp() {
        manifestPlayer.release()
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            )
        )
        finish()
    }

    private fun unpairDevice() {
        AppPreferences.setPaired(this, false)
        manifestPlayer.release()
        firebaseManager.stop()
        startActivity(Intent(this, PairingActivity::class.java))
        finish()
    }

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "indoor:player")
        }
        wakeLock.acquire()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUi()
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun onStart() {
        super.onStart()
        manifestPlayer.onCommand("PLAY", null)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onPause() {
        super.onPause()
        manifestPlayer.onCommand("PAUSE", null)
    }

    override fun onDestroy() {
        super.onDestroy()
        manifestPlayer.release()
        firebaseManager.stop()
        cloudSocketManager?.disconnect()
        handler.removeCallbacksAndMessages(null)
        wakeLock?.release()
    }
}
