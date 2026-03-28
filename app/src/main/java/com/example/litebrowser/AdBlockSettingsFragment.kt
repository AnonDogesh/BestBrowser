package com.example.litebrowser

import android.net.Uri
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class AdBlockSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.adblock_preferences, rootKey)

        val enableSwitch = findPreference<SwitchPreferenceCompat>("adblock_enabled")
        val allowlistPref = findPreference<Preference>("toggle_allowlist")
        val updateNowPref = findPreference<Preference>("update_filters_now")
        val customRulesPref = findPreference<EditTextPreference>("custom_rules")
        val blockedCountPref = findPreference<Preference>("blocked_count")

        enableSwitch?.isChecked = AdBlocker.enabled
        enableSwitch?.setOnPreferenceChangeListener { _, newValue ->
            AdBlocker.setEnabled(requireContext(), newValue as Boolean)
            true
        }

        allowlistPref?.setOnPreferenceClickListener {
            val currentUrl = requireContext().getSharedPreferences("adblock_prefs", android.content.Context.MODE_PRIVATE).getString("last_page_url", null)
            val host = currentUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() }
            if (host != null) {
                AdBlocker.toggleAllowlist(requireContext(), host)
                allowlistPref.summary = if (AdBlocker.isDomainAllowlisted(host)) {
                    "Allowlisted: $host"
                } else {
                    "Not allowlisted: $host"
                }
            }
            true
        }

        updateNowPref?.setOnPreferenceClickListener {
            AdBlocker.requestOneTimeUpdate(requireContext())
            true
        }

        customRulesPref?.text = AdBlocker.customRules(requireContext())
        customRulesPref?.setOnPreferenceChangeListener { _, newValue ->
            AdBlocker.updateCustomRules(requireContext(), newValue.toString())
            true
        }

        blockedCountPref?.summary = AdBlocker.blockedCount(requireContext()).toString()
    }
}
