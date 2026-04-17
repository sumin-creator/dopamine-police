package dev.shortblocker.app

import android.content.Context
import dev.shortblocker.app.data.AppStateStore
import dev.shortblocker.app.domain.ShortVideoDetector
import dev.shortblocker.app.domain.ShortblockerNotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store = AppStateStore(appContext, applicationScope)
    val detector = ShortVideoDetector()
    val notificationController = ShortblockerNotificationController(appContext)
}
