package com.example.notifbypass

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    // pkg -> EditText holding that app's comma-separated aliases
    private val nameFields = mutableMapOf<String, EditText>()

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

        // --- "Vibration patterns" section: pick + test the text/call buzz ---
        val vibeHeader = TextView(this).apply {
            text = getString(R.string.vibe_header)
            textSize = 20f
            setPadding(0, 48, 0, 8)
        }
        root.addView(vibeHeader)

        addPatternSelector(
            root,
            getString(R.string.label_text_pattern),
            VibrationPatterns.TEXT,
            MatchConfig.getTextPatternId(this)
        ) { MatchConfig.setTextPatternId(this, it) }

        addPatternSelector(
            root,
            getString(R.string.label_call_pattern),
            VibrationPatterns.CALL,
            MatchConfig.getCallPatternId(this)
        ) { MatchConfig.setCallPatternId(this, it) }

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
     * [savedId]) plus a "Test" button that plays the highlighted pattern so the
     * user can learn how it feels. Selecting an item persists it via [onSelect].
     */
    private fun addPatternSelector(
        parent: LinearLayout,
        label: String,
        patterns: List<VibePattern>,
        savedId: String,
        onSelect: (String) -> Unit
    ) {
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
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    onSelect(patterns[pos].id)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
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
    }

    /** Persist every app's alias field. Listener picks them up on the next notification. */
    private fun saveNames() {
        for ((pkg, field) in nameFields) {
            MatchConfig.setRaw(this, pkg, field.text.toString())
        }
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
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
