package com.zyplayer.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.zyplayer.app.databinding.ActivityMainBinding
import com.zyplayer.app.ui.favorites.FavoritesFragment
import com.zyplayer.app.ui.home.HomeFragment
import com.zyplayer.app.ui.search.SearchFragment
import com.zyplayer.app.ui.settings.SettingsFragment
import com.zyplayer.app.util.CacheManager
import com.zyplayer.app.util.DisclaimerDialog

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val homeFragment by lazy { HomeFragment() }
    private val searchFragment by lazy { SearchFragment() }
    private val favoritesFragment by lazy { FavoritesFragment() }
    private val settingsFragment by lazy { SettingsFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 首次启动初始化默认源
        CacheManager.ensureDefaultSources(this)

        // 检查免责声明
        if (!DisclaimerDialog.isAccepted(this)) {
            DisclaimerDialog.show(this,
                onAgree = {
                    // 同意后正常进入应用
                    setupBottomNav()
                    if (savedInstanceState == null) {
                        switchFragment(homeFragment)
                    }
                },
                onExit = {
                    // 不同意则退出应用
                    finishAffinity()
                }
            )
        } else {
            // 已同意过，直接进入
            setupBottomNav()
            if (savedInstanceState == null) {
                switchFragment(homeFragment)
            }
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchFragment(homeFragment)
                R.id.nav_search -> switchFragment(searchFragment)
                R.id.nav_favorites -> switchFragment(favoritesFragment)
                R.id.nav_settings -> switchFragment(settingsFragment)
            }
            true
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}