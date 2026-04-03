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

        // 同步加载语言设置并应用
        val language = loadLanguageSync(db)
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)
        val resources = resources
        val configuration = resources.configuration
        configuration.setLocale(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)

        val androidVersion = Build.VERSION.RELEASE
        val androidSDK = Build.VERSION.SDK_INT
        HLog.d("Howl", "Howl $howlVersion running on Android $androidVersion (SDK $androidSDK)")

        // Load preferences asynchronously and initialise dependent components in a callback
        Prefs.loadAll {
            withContext(Dispatchers.Main) {
                Player.switchOutput(Prefs.outputType.value)
            }
            RemoteControlServer.initialise()
            Log.d("Howl", "Async initialisation complete.")
        }

        // Context-based initialisations that don't depend on Prefs
        BluetoothHandler.initialise(
            context = applicationContext,
            onConnectionStatusUpdate = { ConnectionManager.setConnectionStatus(it) }
        )
        Player.initialise(context = applicationContext)
        Generator.initialise()
    }

    // 同步加载语言设置
    private fun loadLanguageSync(db: HowlDatabase): String {
        return runBlocking {
            try {
                val entities = db.preferencesDao().getAll()
                val languageEntity = entities.find { it.name == "language" }
                languageEntity?.value ?: "zh"
            } catch (e: Exception) {
                HLog.d("HowlApp", "Error loading language setting", e)
                "zh"
            }
        }
    }
}