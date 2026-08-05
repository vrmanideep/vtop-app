package com.vtop.sync

import android.content.Context
import com.vtop.ui.core.GlobalSyncer

object SyncManager {

    suspend fun syncEverything(
        context: Context,
        force: Boolean = false
    ) {
        GlobalSyncer.performSync(
            context = context,
            forceNewSession = force
        )
    }

    fun isSyncing() = GlobalSyncer.isSyncing.value
}