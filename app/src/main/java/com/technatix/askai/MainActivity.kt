package com.technatix.askai

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val textEntry get() = ComponentName(this, ProcessTextActivity::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cbEntry = findViewById<CheckBox>(R.id.cbTextEntry)
        cbEntry.isChecked = isEnabled(textEntry)
        cbEntry.setOnCheckedChangeListener { _, on -> setEnabled(textEntry, on) }

        findViewById<MaterialButton>(R.id.btnAddTile).setOnClickListener { addTile() }
        findViewById<MaterialButton>(R.id.btnGetClaude).setOnClickListener { ClaudeApp.openStore(this) }

        val settings = Settings(this)
        val prompts = findViewById<EditText>(R.id.etPrompts)
        prompts.setText(settings.promptsText)
        val cbBurnIn = findViewById<CheckBox>(R.id.cbBurnIn)
        cbBurnIn.isChecked = settings.burnIn
        cbBurnIn.setOnCheckedChangeListener { _, on -> settings.burnIn = on }
        findViewById<MaterialButton>(R.id.btnSavePrompts).setOnClickListener {
            settings.promptsText = prompts.text.toString()
            val n = settings.prompts().size
            Toast.makeText(
                this,
                when (n) {
                    0 -> "Saved. Screenshots will be sent without a message."
                    1 -> "Saved. This message goes with every screenshot."
                    else -> "Saved. You'll pick one of $n messages each time."
                },
                Toast.LENGTH_SHORT
            ).show()
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        val installed = ClaudeApp.isInstalled(this)
        findViewById<TextView>(R.id.claudeStatus).text =
            if (installed) "Claude app: installed" else "Claude app: not installed"
        findViewById<MaterialButton>(R.id.btnGetClaude).visibility =
            if (installed) View.GONE else View.VISIBLE
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val sbm = getSystemService(StatusBarManager::class.java)
            sbm.requestAddTileService(
                ComponentName(this, CaptureTileService::class.java),
                "AskAI capture",
                Icon.createWithResource(this, R.drawable.ic_capture),
                mainExecutor
            ) { result ->
                val msg = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added to Quick Settings"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Tile is already in Quick Settings"
                    else -> "Tile not added"
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                this,
                "Pull down Quick Settings, tap the edit (pencil) button and drag \"AskAI capture\" in.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun isEnabled(c: ComponentName) =
        packageManager.getComponentEnabledSetting(c) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setEnabled(c: ComponentName, on: Boolean) {
        packageManager.setComponentEnabledSetting(
            c,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
