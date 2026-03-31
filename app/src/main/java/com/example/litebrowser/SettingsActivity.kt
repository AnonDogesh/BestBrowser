package com.example.litebrowser

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import com.example.litebrowser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        initBehaviorControls()
        initSearchEngineControl()
        binding.btnAdblockSettings.setOnClickListener {
            startActivity(Intent(this, AdBlockSettingsActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initBehaviorControls() {
        val saveTabs = AppSettings.shouldSaveTabsOnExit(this)
        binding.rbSaveTabs.isChecked = saveTabs
        binding.rbDeleteTabs.isChecked = !saveTabs

        val openLast = AppSettings.shouldOpenLastTab(this)
        binding.rbOpenLastTab.isChecked = openLast
        binding.rbOpenNewTab.isChecked = !openLast

        binding.rgCloseBehavior.setOnCheckedChangeListener { _, checkedId ->
            val shouldSave = checkedId == R.id.rbSaveTabs
            AppSettings.setSaveTabsOnExit(this, shouldSave)
        }

        binding.rgOpenBehavior.setOnCheckedChangeListener { _, checkedId ->
            val shouldOpenLast = checkedId == R.id.rbOpenLastTab
            AppSettings.setOpenLastTab(this, shouldOpenLast)
        }
    }

    private fun initSearchEngineControl() {
        renderSelectedEngine()

        binding.btnSearchEngine.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            AppSettings.SearchEngine.entries.forEachIndexed { index, engine ->
                popup.menu.add(Menu.NONE, index, index, engine.displayName)
            }

            popup.setOnMenuItemClickListener { item ->
                val selected = AppSettings.SearchEngine.entries[item.itemId]
                AppSettings.setSearchEngine(this, selected)
                renderSelectedEngine()
                true
            }
            popup.show()
        }
    }

    private fun renderSelectedEngine() {
        val engine = AppSettings.getSearchEngine(this)
        binding.tvSearchEngineValue.text = engine.displayName
    }
}
