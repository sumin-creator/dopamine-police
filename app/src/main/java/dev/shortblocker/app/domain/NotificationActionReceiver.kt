package dev.shortblocker.app.domain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.shortblocker.app.ShortblockerApplication
import dev.shortblocker.app.data.UserAction
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as ShortblockerApplication
        val pendingResult = goAsync()

        application.container.applicationScope.launch {
            val action = when (intent.action) {
                ACTION_STOP -> UserAction.STOP
                ACTION_EXTEND -> UserAction.EXTEND
                ACTION_IGNORE -> UserAction.IGNORE
                else -> null
            }
            if (action != null) {
                application.container.store.applyUserAction(action, source = "notification")
                application.container.notificationController.dismissIntervention()
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_STOP = "dev.shortblocker.app.action.STOP"
        const val ACTION_EXTEND = "dev.shortblocker.app.action.EXTEND"
        const val ACTION_IGNORE = "dev.shortblocker.app.action.IGNORE"
    }
}
