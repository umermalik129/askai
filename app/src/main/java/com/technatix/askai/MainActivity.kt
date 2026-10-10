package com.technatix.askai

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private val textEntry get() = ComponentName(this, ProcessTextActivity::class.java)
    private lateinit var settings: Settings

    private lateinit var appChips: ChipGroup
    private lateinit var groupOpenApp: View
    private lateinit var groupApi: View
    private lateinit var tilKey: TextInputLayout
    private lateinit var tilModel: TextInputLayout
    private lateinit var tilBaseUrl: TextInputLayout
    private lateinit var etKey: EditText
    private lateinit var etModel: EditText
    private lateinit var etBaseUrl: EditText
    private lateinit var promptAdapter: PromptAdapter
    private var loadingFields = false

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

        header(R.id.hdrSend, R.drawable.ic_phone, "Send to")
        header(R.id.hdrPrompts, R.drawable.ic_message, "Prompts")
        header(R.id.hdrText, R.drawable.ic_text_select, "Selectable text")
        header(R.id.hdrCapture, R.drawable.ic_capture, "Screen capture")

        setupMode()
        setupOpenApp()
        setupApi()
        setupPrompts()

        findViewById<MaterialSwitch>(R.id.swTextEntry).apply {
            isChecked = isEnabled(textEntry)
            setOnCheckedChangeListener { _, on -> setEnabled(textEntry, on) }
        }
        findViewById<MaterialButton>(R.id.btnAddTile).setOnClickListener { addTile() }
        findViewById<MaterialButton>(R.id.btnAssistant).setOnClickListener { openAssistantSettings() }

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

    // ---- Send mode ----

    private fun setupMode() {
        groupOpenApp = findViewById(R.id.groupOpenApp)
        groupApi = findViewById(R.id.groupApi)
        val group = findViewById<MaterialButtonToggleGroup>(R.id.tgMode)
        group.check(if (settings.mode == SendMode.API) R.id.btnModeApi else R.id.btnModeApp)
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            settings.mode = if (id == R.id.btnModeApi) SendMode.API else SendMode.OPEN_APP
            showMode()
        }
        showMode()
    }

    private fun showMode() {
        val api = settings.mode == SendMode.API
        groupOpenApp.visibility = if (api) View.GONE else View.VISIBLE
        groupApi.visibility = if (api) View.VISIBLE else View.GONE
    }

    // ---- Open-app mode ----

    private fun setupOpenApp() {
        appChips = findViewById(R.id.cgApp)
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
            refreshAppStatus()
        }
        findViewById<MaterialButton>(R.id.btnGetApp).setOnClickListener { settings.app.openStore(this) }
        findViewById<MaterialSwitch>(R.id.swBurnIn).apply {
            isChecked = settings.burnIn
            setOnCheckedChangeListener { _, on -> settings.burnIn = on }
        }
    }

    private fun refreshAppChips() {
        for (i in 0 until appChips.childCount) {
            val chip = appChips.getChildAt(i) as Chip
            val app = chip.tag as AiApp
            chip.alpha = if (app.isInstalled(this)) 1f else 0.6f
            if (app == settings.app && !chip.isChecked) chip.isChecked = true
        }
        refreshAppStatus()
    }

    private fun refreshAppStatus() {
        val app = settings.app
        val installed = app.isInstalled(this)
        findViewById<TextView>(R.id.appStatus).text =
            if (installed) "Text and screenshots open in ${app.label}." else "${app.label} is not installed yet."
        findViewById<MaterialButton>(R.id.btnGetApp).apply {
            text = "Get ${app.label}"
            visibility = if (installed) View.GONE else View.VISIBLE
        }
    }

    // ---- API mode ----

    private fun setupApi() {
        tilKey = findViewById(R.id.tilKey)
        tilModel = findViewById(R.id.tilModel)
        tilBaseUrl = findViewById(R.id.tilBaseUrl)
        etKey = findViewById(R.id.etKey)
        etModel = findViewById(R.id.etModel)
        etBaseUrl = findViewById(R.id.etBaseUrl)

        val group = findViewById<MaterialButtonToggleGroup>(R.id.tgProvider)
        group.check(if (settings.apiProvider == ApiProvider.OPENAI) R.id.btnProvOpenai else R.id.btnProvClaude)
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            settings.apiProvider = if (id == R.id.btnProvOpenai) ApiProvider.OPENAI else ApiProvider.CLAUDE
            loadApiFields()
        }

        etKey.doAfterTextChanged {
            if (loadingFields) return@doAfterTextChanged
            val v = it?.toString().orEmpty()
            if (settings.apiProvider == ApiProvider.CLAUDE) settings.claudeKey = v else settings.openaiKey = v
        }
        etModel.doAfterTextChanged {
            if (loadingFields) return@doAfterTextChanged
            val v = it?.toString().orEmpty()
            if (settings.apiProvider == ApiProvider.CLAUDE) settings.claudeModel = v else settings.openaiModel = v
        }
        etBaseUrl.doAfterTextChanged {
            if (loadingFields) return@doAfterTextChanged
            settings.openaiBaseUrl = it?.toString().orEmpty()
        }
        loadApiFields()
    }

    private fun loadApiFields() {
        loadingFields = true
        val claude = settings.apiProvider == ApiProvider.CLAUDE
        etKey.setText(if (claude) settings.claudeKey else settings.openaiKey)
        etModel.setText(if (claude) settings.claudeModel else settings.openaiModel)
        etBaseUrl.setText(settings.openaiBaseUrl)
        tilKey.helperText = if (claude) "From console.anthropic.com" else "From your provider's dashboard"
        tilModel.helperText = "Default: ${settings.apiProvider.defaultModel}"
        tilBaseUrl.visibility = if (claude) View.GONE else View.VISIBLE
        loadingFields = false
    }

    // ---- Prompts ----

    private fun setupPrompts() {
        promptAdapter = PromptAdapter(
            onEdit = { editPrompt(it) },
            onDelete = { deletePrompt(it) },
            onOrderChanged = { settings.prompts = it }
        )
        promptAdapter.attachTo(findViewById(R.id.promptList))
        findViewById<MaterialButton>(R.id.btnAddPrompt).setOnClickListener { editPrompt(null) }
        renderPrompts()
    }

    private fun renderPrompts() {
        val prompts = settings.prompts
        promptAdapter.submit(prompts)
        findViewById<View>(R.id.promptsEmpty).visibility = if (prompts.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.promptsHint).text = when (prompts.size) {
            0 -> "Text and screenshots are sent without an instruction."
            1 -> "With one prompt you are never asked; it is used every time."
            else -> "You pick one of these each time."
        }
    }

    private fun editPrompt(index: Int?) {
        val view = layoutInflater.inflate(R.layout.dialog_prompt, null)
        val etLabel = view.findViewById<EditText>(R.id.etLabel)
        val etText = view.findViewById<EditText>(R.id.etText)
        val existing = index?.let { settings.prompts.getOrNull(it) }
        existing?.let {
            etLabel.setText(it.label)
            etText.setText(it.text)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "New prompt" else "Edit prompt")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val text = etText.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                val label = etLabel.text.toString().trim().ifEmpty { text.take(24) }
                val list = settings.prompts.toMutableList()
                if (index != null && index < list.size) list[index] = Prompt(label, text) else list.add(Prompt(label, text))
                settings.prompts = list
                renderPrompts()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePrompt(index: Int) {
        val list = settings.prompts.toMutableList()
        val removed = list.removeAt(index)
        settings.prompts = list
        renderPrompts()
        Snackbar.make(findViewById(R.id.scroll), "Deleted \"${removed.label}\"", Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                settings.prompts = settings.prompts.toMutableList().also { it.add(index.coerceAtMost(it.size), removed) }
                renderPrompts()
            }
            .show()
    }

    // ---- Misc ----

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

    /** Opens the system screen where the default assistant app is chosen. */
    private fun openAssistantSettings() {
        val candidates = listOf(
            Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (i in candidates) {
            if (i.resolveActivity(packageManager) != null) {
                startActivity(i)
                Toast.makeText(this, "Pick AskAI as the digital assistant app", Toast.LENGTH_LONG).show()
                return
            }
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
