package de.stefanmedack.ccctv

import android.util.Log
import com.crashlytics.android.Crashlytics
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import de.stefanmedack.ccctv.di.DaggerAppComponent
import io.fabric.sdk.android.Fabric
import timber.log.Timber
import timber.log.Timber.DebugTree

class C3TVApp : DaggerApplication() {
    override fun applicationInjector(): AndroidInjector<out DaggerApplication> = DaggerAppComponent.builder()
            .application(this)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }

        Fabric.with(this, Crashlytics())
        Timber.plant(CrashlyticsTree())

    }

    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
            if (priority < Log.WARN) {
                return
            }

            Crashlytics.log(message)
            if (t != null) {
                Crashlytics.logException(t)
            }
        }
    }
}
