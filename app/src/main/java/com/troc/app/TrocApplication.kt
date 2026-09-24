package com.troc.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TrocApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Init Chaquopy via reflection if available (optional)
        try {
            val pythonClass = Class.forName("com.chaquo.python.Python")
            val isStartedMethod = pythonClass.getMethod("isStarted")
            val isStarted = isStartedMethod.invoke(null) as Boolean
            if (!isStarted) {
                val androidPlatformClass = Class.forName("com.chaquo.python.android.AndroidPlatform")
                val platformConstructor = androidPlatformClass.getConstructor(android.content.Context::class.java)
                val platform = platformConstructor.newInstance(this)
                val startMethod = pythonClass.getMethod("start", Class.forName("com.chaquo.python.Python\$Platform"))
                startMethod.invoke(null, platform)
            }
        } catch (e: ClassNotFoundException) {
            // Chaquopy not installed, ignore - Python sandbox will use mock
        } catch (e: Exception) {
            // Ignore init errors
        }
    }
}
