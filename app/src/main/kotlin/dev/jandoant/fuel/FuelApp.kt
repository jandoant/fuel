package dev.jandoant.fuel

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.jandoant.fuel.work.Jobs

class FuelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Jobs.schedule(this)
        // Sync on app open (PRD §4.1) and re-read "today" after midnight.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.model.refreshDay()
                Jobs.syncNow(this@FuelApp)
            }
        })
    }
}
