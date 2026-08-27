package com.example.howl

import android.annotation.SuppressLint
import android.util.Log
import android.app.Application
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

const val howlVersion = BuildConfig.VERSION_NAME

class HowlApp : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
            private set
    }
    val database: HowlDatabase by lazy { HowlDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext

        // 初始化数据库
        val db = HowlDatabase.getDatabase(this)
        Prefs.initialise(db = db)

        val androidVersion = Build.VERSION.RELEASE
        val androidSDK = Build.VERSION.SDK_INT
        HLog.i("Howl", "Howl $howlVersion running on Android $androidVersion (SDK $androidSDK)")

        OutputManager.initialise()

        // 同步加载语言设置并应用
        val language = loadLanguageSync(db)
        HLog.d("HowlApp", "Loaded language setting: $language")
        applyLanguage(language)

        // Load preferences asynchronously and initialise dependent components in a callback
        Prefs.loadAll {
            withContext(Dispatchers.Main) {
                val states = Prefs.outputStates.value
                if (states.isNotEmpty()) {
                    OutputManager.restoreOutputs(states)
                }
            }
            RemoteControlServer.initialise()
            Log.d("Howl", "Async initialisation complete.")
        }

        Player.initialise(context = applicationContext)
        Generator.initialise()
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