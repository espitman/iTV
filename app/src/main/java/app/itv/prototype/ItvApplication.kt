package app.itv.prototype

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.itv.prototype.admin.LanDashboard
import app.itv.prototype.data.ImportCoordinator
import app.itv.prototype.data.ItvDatabase
import app.itv.prototype.data.LibraryRepository
import app.itv.prototype.data.TelewebionClient

class ItvApplication : Application() {
    lateinit var database: ItvDatabase
        private set
    lateinit var repository: LibraryRepository
        private set
    lateinit var importer: ImportCoordinator
        private set
    lateinit var dashboard: LanDashboard
        private set
    val telewebion = TelewebionClient()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Pictures.install(this)
        database = ItvDatabase.create(this)
        repository = LibraryRepository(database)
        importer = ImportCoordinator(repository, telewebion)
        dashboard = LanDashboard(this, repository, importer)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                dashboard.start()
                importer.onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                importer.onBackground()
                dashboard.stop()
                repository.flushProgress()
            }
        })
    }

    companion object {
        lateinit var instance: ItvApplication
            private set
    }
}
