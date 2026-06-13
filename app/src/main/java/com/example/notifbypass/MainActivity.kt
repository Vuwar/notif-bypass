package com.example.notifbypass

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tiny code-built UI so we don't need an XML layout file.
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
        }

        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 32, 0, 32)
        }

        val grantButton = Button(this).apply {
            text = getString(R.string.btn_grant_access)
            setOnClickListener { openNotificationAccessSettings() }
        }

        val batteryButton = Button(this).apply {
            text = getString(R.string.btn_battery)
            setOnClickListener { openBatterySettings() }
        }

        val keepAliveButton = Button(this).apply {
            text = getString(R.string.btn_keep_alive)
            setOnClickListener {
                KeepAliveService.start(this@MainActivity)
                Toast.makeText(
                    this@MainActivity,
                    "Keep-alive service started",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(grantButton)
        root.addView(batteryButton)
        root.addView(keepAliveButton)
        setContentView(root)

        // Start the keep-alive service on first launch too.
        KeepAliveService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        statusText.text = if (isNotificationServiceEnabled()) {
            "✅ Notification Access: ENABLED\nListener is active."
        } else {
            "❌ Notification Access: DISABLED\nTap below to enable."
        }
    }

    /** Checks whether our listener is in the system's enabled-listeners list. */
    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false

        val myComponent = ComponentName(this, MyNotificationListener::class.java)
        return flat.split(":").any {
            ComponentName.unflattenFromString(it) == myComponent
        }
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Open Settings > Notification Access manually", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun openBatterySettings() {
        try {
            // Opens the battery-optimization list so you can set NotifBypass to "Don't optimize".
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Open Settings > Battery manually", Toast.LENGTH_LONG).show()
        }
    }
}
