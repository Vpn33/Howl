package com.example.howl

import android.annotation.SuppressLint
import android.util.Log
import android.app.Application
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
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

        Prefs.initialise(db = database)

        val androidVersion = Build.VERSION.RELEASE
        val androidSDK = Build.VERSION.SDK_INT
        HLog.i("Howl", "Howl $howlVersion running on Android $androidVersion (SDK $androidSDK)")

        OutputManager.initialise()

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
}