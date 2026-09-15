package com.indoor.media

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PairingActivity : AppCompatActivity() {

    private lateinit var tvPin: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnRefresh: Button
    private var socketManager: CloudSocketManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pairingCheck: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AppPreferences.isPaired(this)) {
            goToMain()
            return
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_pairing)

        tvPin = findViewById(R.id.tvPin)
        tvStatus = findViewById(R.id.tvStatus)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        btnRefresh = findViewById(R.id.btnRefresh)

        hideSystemUi()

        tvPin.text = AppPreferences.getPairingCode(this)

        btnRefresh.setOnClickListener {
            tvStatus.text = "Verificando pareamento..."
            tvConnectionStatus.visibility = View.GONE
            connectAndHello()
        }

        connectAndHello()
    }

    private fun connectAndHello() {
        socketManager?.disconnect()
        socketManager = CloudSocketManager(this).apply {
            onConnected = {
                runOnUiThread {
                    tvConnectionStatus.text = "Conectado ao servidor"
                    tvConnectionStatus.visibility = View.VISIBLE
                    tvStatus.text = "Aguardando pareamento..."
                }
            }
            onPairingCodeReceived = { code ->
                runOnUiThread {
                    tvPin.text = code
                    tvStatus.text = "Aguardando pareamento..."
                }
            }
            onPairedConfirmed = {
                runOnUiThread { goToMain() }
            }
            onCommandReceived = { action, _ ->
                if (action == "REFRESH") {
                    runOnUiThread { checkPairingStatus() }
                }
            }
            connect()
        }
        startPairingPoll()
    }

    private fun startPairingPoll() {
        pairingCheck?.let { handler.removeCallbacks(it) }
        pairingCheck = Runnable {
            socketManager?.reHello()
            handler.postDelayed(pairingCheck!!, 8000)
        }
        handler.postDelayed(pairingCheck!!, 5000)
    }

    private fun checkPairingStatus() {
        if (AppPreferences.isPaired(this)) {
            goToMain()
        } else {
            runOnUiThread {
                val code = AppPreferences.getPairingCode(this)
                tvPin.text = code
                tvStatus.text = "Aguardando pareamento..."
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        pairingCheck?.let { handler.removeCallbacks(it) }
        socketManager?.disconnect()
    }
}
