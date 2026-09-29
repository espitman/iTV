package app.itv.prototype.ui

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import app.itv.prototype.ItvApplication
import app.itv.prototype.R
import app.itv.prototype.admin.QrBitmap
import app.itv.prototype.core.persianDigits

class SettingsActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var url: TextView
    private lateinit var pinBoxes: List<TextView>
    private lateinit var serverState: TextView
    private lateinit var statusDot: View
    private lateinit var qr: ImageView
    private lateinit var qrEmpty: TextView
    private lateinit var back: View
    private var initialFocusApplied = false
    private var shownAddress: String? = null
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
        pinBoxes = listOf(
            findViewById(R.id.pin0),
            findViewById(R.id.pin1),
            findViewById(R.id.pin2),
            findViewById(R.id.pin3),
        )
        serverState = findViewById(R.id.serverState)
        statusDot = findViewById(R.id.statusDot)
        qr = findViewById(R.id.qr)
        qrEmpty = findViewById(R.id.qrEmpty)
        back = findViewById(R.id.back)
        findViewById<TextView>(R.id.clientVersion).text = getString(
            R.string.settings_client_version,
            packageManager.getPackageInfo(packageName, 0).versionName ?: "—",
        )
        prepareFocus(back)
        back.setOnClickListener { finish() }
        back.nextFocusDownId = back.id
        back.nextFocusUpId = back.id
        back.nextFocusLeftId = back.id
        back.nextFocusRightId = back.id
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
        serverState.setText(if (running) R.string.server_status_on else R.string.server_status_off)
        serverState.setTextColor(getColor(if (running) R.color.text else R.color.error))
        statusDot.setBackgroundResource(
            if (running) R.drawable.bg_settings_dot_on else R.drawable.bg_settings_dot_off,
        )
        if (address != null) {
            url.text = address
            url.setTextColor(0xFF38BDF8.toInt())
        } else {
            url.text = getString(R.string.dashboard_offline)
            url.setTextColor(getColor(R.color.muted))
        }
        bindPin(persianDigits(dashboard.pairing.pin))
        if (address != null) {
            if (address != shownAddress) {
                qr.setImageBitmap(QrBitmap.render(address, 1024))
                shownAddress = address
            }
            qrEmpty.visibility = View.GONE
        } else {
            qr.setImageDrawable(null)
            shownAddress = null
            qrEmpty.visibility = View.VISIBLE
        }
        if (!initialFocusApplied) {
            initialFocusApplied = true
            back.post { back.requestFocus() }
        }
    }

    private fun bindPin(digits: String) {
        val cells = if (digits.length <= pinBoxes.size) {
            List(pinBoxes.size) { index -> digits.getOrNull(index)?.toString().orEmpty() }
        } else {
            val head = pinBoxes.size - 1
            List(head) { index -> digits[index].toString() } + digits.substring(head)
        }
        pinBoxes.forEachIndexed { index, box ->
            box.text = cells[index]
            box.textSize = if (cells[index].length > 1) 16f else 22f
        }
    }

    private fun prepareFocus(view: View) {
        if (Build.VERSION.SDK_INT >= 26) view.defaultFocusHighlightEnabled = false
        view.isFocusable = true
        view.isFocusableInTouchMode = true
    }
}
