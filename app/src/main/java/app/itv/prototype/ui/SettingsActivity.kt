package app.itv.prototype.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import app.itv.prototype.ItvApplication
import app.itv.prototype.R
import app.itv.prototype.admin.QrBitmap
import app.itv.prototype.core.persianDigits

class SettingsActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var url: TextView
    private lateinit var port: TextView
    private lateinit var pin: TextView
    private lateinit var serverState: TextView
    private lateinit var qr: ImageView
    private lateinit var back: Button
    private var initialFocusApplied = false
    private val refresh = object : Runnable {
        override fun run() {
            bind()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        url = findViewById(R.id.url)
        port = findViewById(R.id.port)
        pin = findViewById(R.id.pin)
        serverState = findViewById(R.id.serverState)
        qr = findViewById(R.id.qr)
        back = findViewById(R.id.back)
        back.bindFocusTint()
        back.setOnClickListener { finish() }
        findViewById<Button>(R.id.copyUrl).apply {
            bindFocusTint()
            setOnClickListener {
                ItvApplication.instance.dashboard.dashboardUrl()?.let { address ->
                    (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("iTV", address))
                    android.widget.Toast.makeText(this@SettingsActivity, "نشانی کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bind()
        handler.post(refresh)
    }

    override fun onStop() {
        handler.removeCallbacks(refresh)
        super.onStop()
    }

    private fun bind() {
        val dashboard = ItvApplication.instance.dashboard
        val address = dashboard.dashboardUrl()
        val running = dashboard.running && address != null
        serverState.text = getString(if (running) R.string.server_on else R.string.server_off)
        serverState.setTextColor(getColor(if (running) R.color.cyan else R.color.error))
        url.text = address ?: getString(R.string.dashboard_offline)
        port.text = getString(R.string.port_label, if (dashboard.port > 0) persianDigits(dashboard.port) else "—")
        pin.text = persianDigits(dashboard.pairing.pin)
        if (address != null) {
            qr.setImageBitmap(QrBitmap.render(address, 512))
        } else {
            qr.setImageDrawable(null)
        }
        if (!initialFocusApplied) {
            initialFocusApplied = true
            back.post { back.requestFocus() }
        }
    }
}
