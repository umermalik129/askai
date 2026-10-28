package com.technatix.askai

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.widget.Toast
import java.util.Locale

/**
 * "WhatsApp" entry in the text-selection menu. Android shows it for every selection (menu
 * entries cannot depend on the text), so this checks for a phone number and opens wa.me/<number>.
 */
class WhatsAppActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        val number = waNumber(text)
        if (number == null) {
            Toast.makeText(this, "Not a phone number", Toast.LENGTH_SHORT).show()
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No app can open wa.me links", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    /**
     * wa.me wants the full international number as bare digits. Local numbers ("0300 1234567")
     * take the country of the SIM, or of the phone's locale when there is no SIM.
     */
    private fun waNumber(text: String): String? {
        if (!PHONE.matches(text)) return null
        val raw = text.filter { it.isDigit() || it == '+' }
        val e164 = PhoneNumberUtils.formatNumberToE164(raw, countryIso())
            ?: raw.takeIf { it.startsWith("+") }
            ?: return null
        return e164.removePrefix("+").takeIf { it.length in 7..15 && it.all(Char::isDigit) }
    }

    private fun countryIso(): String {
        val tm = getSystemService(TelephonyManager::class.java)
        val iso = tm?.simCountryIso?.takeIf { it.isNotEmpty() }
            ?: tm?.networkCountryIso?.takeIf { it.isNotEmpty() }
            ?: Locale.getDefault().country
        return iso.uppercase(Locale.ROOT)
    }

    private companion object {
        /** Digits with the usual separators, optionally led by "+". Anything else is not a number. */
        val PHONE = Regex("""\+?[\d\s().\p{Pd}]+""")
    }
}
