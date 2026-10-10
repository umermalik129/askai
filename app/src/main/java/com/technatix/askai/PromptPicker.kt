package com.technatix.askai

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lets the user pick one of the saved prompts by its label. With zero or one prompt there is
 * nothing to choose, so [onPick] is called straight away.
 */
fun AppCompatActivity.pickPrompt(prompts: List<Prompt>, onCancel: () -> Unit, onPick: (Prompt?) -> Unit) {
    if (prompts.size <= 1) {
        onPick(prompts.firstOrNull())
        return
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Choose a prompt")
        .setItems(prompts.map { it.label }.toTypedArray()) { _, i -> onPick(prompts[i]) }
        .setNegativeButton("Cancel") { _, _ -> onCancel() }
        .setOnCancelListener { onCancel() }
        .show()
}
