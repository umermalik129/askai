package com.technatix.askai

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private val textEntry get() = ComponentName(this, ProcessTextActivity::class.java)
    private lateinit var settings: Settings
    private lateinit var appChips: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        val scroll = findViewById<View>(R.id.scroll)
        val basePadding = scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = basePadding + bars.bottom)
            insets
        }

        header(R.id.hdrApp, R.drawable.ic_sparkle, "Send to")
        header(R.id.hdrText, R.drawable.ic_text_select, "Selectable text")
        header(R.id.hdrCapture, R.drawable.ic_capture, "Screen capture")
        header(R.id.hdrMessages, R.drawable.ic_message, "Messages")

        appChips = findViewById(R.id.cgApp)
        buildAppChips()
        findViewById<MaterialButton>(R.id.btnGetApp).setOnClickListener { settings.app.openStore(this) }

        findViewById<MaterialSwitch>(R.id.swTextEntry).apply {
            isChecked = isEnabled(textEntry)
            setOnCheckedChangeListener { _, on -> setEnabled(textEntry, on) }
        }

        findViewById<MaterialButton>(R.id.btnAddTile).setOnClickListener { addTile() }

        findViewById<EditText>(R.id.etPrompts).apply {
            setText(settings.promptsText)
            doAfterTextChanged { settings.promptsText = it?.toString().orEmpty() }
        }
        findViewById<MaterialSwitch>(R.id.swBurnIn).apply {
            isChecked = settings.burnIn
            setOnCheckedChangeListener { _, on -> settings.burnIn = on }
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshAppChips()
    }

    private fun header(id: Int, icon: Int, title: String) {
        val v = findViewById<View>(id)
        v.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.title).text = title
    }

    /** One filter chip per supported AI app. */
    private fun buildAppChips() {
        AiApp.entries.forEach { app ->
            val chip = (layoutInflater.inflate(R.layout.chip_app, appChips, false) as Chip).apply {
                id = View.generateViewId()
                tag = app
                text = app.label
            }
            appChips.addView(chip)
        }
        appChips.setOnCheckedStateChangeListener { group, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val app = group.findViewById<Chip>(id)?.tag as? AiApp ?: return@setOnCheckedStateChangeListener
            if (settings.app != app) settings.app = app
            refreshStatus()
        }
    }

    private fun refreshAppChips() {
        for (i in 0 until appChips.childCount) {
            val chip = appChips.getChildAt(i) as Chip
            val app = chip.tag as AiApp
            chip.alpha = if (app.isInstalled(this)) 1f else 0.6f
            if (app == settings.app && !chip.isChecked) chip.isChecked = true
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        val app = settings.app
        val installed = app.isInstalled(this)
        findViewById<TextView>(R.id.appStatus).text =
            if (installed) "Text and screenshots open in ${app.label}."
            else "${app.label} is not installed yet."
        findViewById<MaterialButton>(R.id.btnGetApp).apply {
            text = "Get ${app.label}"
            visibility = if (installed) View.GONE else View.VISIBLE
        }
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
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "The tile is already in Quick Settings"
                    else -> "Tile not added"
                }
                Snackbar.make(findViewById(R.id.scroll), msg, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            Snackbar.make(
                findViewById(R.id.scroll),
                "Open Quick Settings, tap the pencil and drag AskAI capture into the panel.",
                Snackbar.LENGTH_LONG
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
        Toast.makeText(this, if (on) "AskAI shown in selection menu" else "AskAI hidden from selection menu", Toast.LENGTH_SHORT).show()
    }
}
