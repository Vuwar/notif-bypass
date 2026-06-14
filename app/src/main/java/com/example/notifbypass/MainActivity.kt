package com.example.notifbypass

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var lastAlertText: TextView
    private lateinit var grantButton: Button

    // pkg -> EditText holding that app's comma-separated aliases
    private val nameFields = mutableMapOf<String, EditText>()

    // Pattern dropdowns; persisted only when "Save vibration patterns" is tapped.
    private lateinit var textPatternSpinner: Spinner
    private lateinit var callPatternSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPostNotificationsIfNeeded()

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
            setPadding(0, 32, 0, 8)
        }

        lastAlertText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 24)
        }

        grantButton = Button(this).apply {
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
        root.addView(lastAlertText)
        root.addView(grantButton)
        root.addView(batteryButton)
        root.addView(keepAliveButton)

        // --- "Phone settings to enable" reference checklist (set in system Settings) ---
        val setupHeader = TextView(this).apply {
            text = getString(R.string.setup_header)
            textSize = 20f
            setPadding(0, 48, 0, 8)
        }
        val setupText = TextView(this).apply {
            text = getString(R.string.setup_checklist)
            textSize = 14f
            setLineSpacing(0f, 1.15f)
        }
        root.addView(setupHeader)
        root.addView(setupText)

        // --- "Who to alert" section: one editable field per app ---
        val namesHeader = TextView(this).apply {
            text = getString(R.string.names_header)
            textSize = 20f
            setPadding(0, 48, 0, 8)
        }
        val namesHint = TextView(this).apply {
            text = getString(R.string.names_hint)
            textSize = 13f
            setPadding(0, 0, 0, 16)
        }
        root.addView(namesHeader)
        root.addView(namesHint)

        for (app in MatchConfig.APPS) {
            val label = TextView(this).apply {
                text = app.label
                textSize = 15f
                setPadding(0, 16, 0, 4)
            }
            val field = EditText(this).apply {
                setText(MatchConfig.getRaw(this@MainActivity, app.pkg))
                setSingleLine(true)
            }
            nameFields[app.pkg] = field
            root.addView(label)
            root.addView(field)
        }

        val saveNamesButton = Button(this).apply {
            text = getString(R.string.btn_save_names)
            setOnClickListener { saveNames() }
        }
        root.addView(saveNamesButton)

        val matchBodyCheck = CheckBox(this).apply {
            text = getString(R.string.label_match_body)
            isChecked = MatchConfig.getMatchBody(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                MatchConfig.setMatchBody(this@MainActivity, checked)
            }
        }
        root.addView(matchBodyCheck)

        // --- "Vibration patterns" section: pick + test the text/call buzz ---
        val vibeHeader = TextView(this).apply {
            text = getString(R.string.vibe_header)
            textSize = 20f
            setPadding(0, 48, 0, 8)
        }
        root.addView(vibeHeader)

        textPatternSpinner = addPatternSelector(
            root,
            getString(R.string.label_text_pattern),
            VibrationPatterns.TEXT,
            MatchConfig.getTextPatternId(this)
        )

        callPatternSpinner = addPatternSelector(
            root,
            getString(R.string.label_call_pattern),
            VibrationPatterns.CALL,
            MatchConfig.getCallPatternId(this)
        )

        val savePatternsButton = Button(this).apply {
            text = getString(R.string.btn_save_patterns)
            setOnClickListener { saveVibrationPatterns() }
        }
        root.addView(savePatternsButton)

        val playSoundCheck = CheckBox(this).apply {
            text = getString(R.string.label_play_sound)
            isChecked = MatchConfig.getPlaySound(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                MatchConfig.setPlaySound(this@MainActivity, checked)
            }
        }
        root.addView(playSoundCheck)

        // Scrollable so the name fields are reachable on small screens.
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        // Start the keep-alive service on first launch too.
        KeepAliveService.start(this)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    /**
     * Builds a labelled pattern picker: a dropdown of [patterns] (pre-selected to
     * [savedId]) plus a "Test" button that plays the highlighted pattern so the user
     * can learn how it feels. The selection is NOT persisted here — that only happens
     * when the user taps "Save vibration patterns". Returns the Spinner so the caller
     * can read its choice on save.
     */
    private fun addPatternSelector(
        parent: LinearLayout,
        label: String,
        patterns: List<VibePattern>,
        savedId: String
    ): Spinner {
        val title = TextView(this).apply {
            text = label
            textSize = 15f
            setPadding(0, 16, 0, 4)
        }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                patterns.map { it.label }
            )
            setSelection(patterns.indexOfFirst { it.id == savedId }.coerceAtLeast(0))
        }

        val testButton = Button(this).apply {
            text = getString(R.string.btn_test)
            setOnClickListener {
                // Play once at full strength so the rhythm is clearly felt.
                val selected = patterns[spinner.selectedItemPosition]
                Haptics.play(this@MainActivity, selected.timings, 255, -1)
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                spinner,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(testButton)
        }

        parent.addView(title)
        parent.addView(row)
        return spinner
    }

    /** Persist the currently-selected text/call patterns (only on explicit save). */
    private fun saveVibrationPatterns() {
        MatchConfig.setTextPatternId(this, VibrationPatterns.TEXT[textPatternSpinner.selectedItemPosition].id)
        MatchConfig.setCallPatternId(this, VibrationPatterns.CALL[callPatternSpinner.selectedItemPosition].id)
        Toast.makeText(this, "Patterns saved", Toast.LENGTH_SHORT).show()
    }

    /** Persist every app's alias field. Listener picks them up on the next notification. */
    private fun saveNames() {
        for ((pkg, field) in nameFields) {
            MatchConfig.setRaw(this, pkg, field.text.toString())
        }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
    }

    /** Asks for POST_NOTIFICATIONS (Android 13+) so the keep-alive notification can show. */
    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
    }

    /**
     * Re-checks notification access (called on every [onResume], so it updates if
     * the user revokes/grants access in Settings and returns). When active, the
     * grant prompt disappears and only the green tick remains; if access is lost,
     * the prompt comes back.
     */
    private fun refreshStatus() {
        val enabled = isNotificationServiceEnabled()
        statusText.text = if (enabled) {
            "✅ Notification Access: ENABLED\nListener is active."
        } else {
            "❌ Notification Access: DISABLED\nTap below to enable."
        }
        grantButton.visibility = if (enabled) View.GONE else View.VISIBLE
        refreshLastAlert()
    }

    /** Shows when the last real alert fired (proof the pipeline is working). */
    private fun refreshLastAlert() {
        val at = MatchConfig.getLastAlertAt(this)
        lastAlertText.text = if (at == 0L) {
            "Last alert: none yet"
        } else {
            val rel = DateUtils.getRelativeTimeSpanString(
                at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            )
            val desc = MatchConfig.getLastAlertDesc(this)?.let { " ($it)" }.orEmpty()
            "Last alert: $rel$desc"
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
