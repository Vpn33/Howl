package com.example.howl

import android.util.Log
import android.app.Application
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

const val howlVersion = BuildConfig.VERSION_NAME

class HowlApp : Application() {
    val database: HowlDatabase by lazy { HowlDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()

        // 初始化数据库
        val db = HowlDatabase.getDatabase(this)
        Prefs.initialise(db = db)

        val androidVersion = Build.VERSION.RELEASE
        val androidSDK = Build.VERSION.SDK_INT
        HLog.d("Howl", "Howl $howlVersion running on Android $androidVersion (SDK $androidSDK)")

        // 同步加载语言设置并应用
        val language = loadLanguageSync(db)
        HLog.d("HowlApp", "Loaded language setting: $language")
        applyLanguage(language)

        // 初始化ActivityType的displayNames映射，使用初始语言设置
        ActivityType.initDisplayNames(applicationContext)

        // 初始化其他组件
        BluetoothHandler.initialise(
            context = applicationContext,
            onConnectionStatusUpdate = { ConnectionManager.setConnectionStatus(it) }
        )
        Player.initialise(context = applicationContext)
        Generator.initialise()

        // Load preferences asynchronously and initialise dependent components in a callback
        Prefs.loadAll {
            withContext(Dispatchers.Main) {
                Player.switchOutput(Prefs.outputType.value)
            }
            RemoteControlServer.initialise()
            Log.d("Howl", "Async initialisation complete.")
        }
    }

    // 应用语言设置
    private fun applyLanguage(language: String) {
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)

        // 使用新的方法来更新语言设置，适用于Android 7.0及以上版本
        val resources = resources
        val configuration = resources.configuration
        configuration.setLocale(locale)

        // 对于旧版本，使用updateConfiguration
        resources.updateConfiguration(configuration, resources.displayMetrics)


        HLog.d("HowlApp", "Applied language setting: $language")
    }

    // 同步加载语言设置
    private fun loadLanguageSync(db: HowlDatabase): String {
        return runBlocking {
            try {
                val entities = db.preferencesDao().getAll()
                val languageEntity = entities.find { it.name == "language" }
                HLog.d("HowlApp", "Loaded language setting: ${languageEntity?.value ?: "zh"}")
                languageEntity?.value ?: "zh"
            } catch (e: Exception) {
                HLog.d("HowlApp", "Error loading language setting", e)
                "zh"
            }
        }
    }
}