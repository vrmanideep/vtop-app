package com.vtop.sync

import android.content.Context
import com.vtop.logic.GlobalSyncer
import com.vtop.ui.core.GlobalSyncer

object SyncManager {

    suspend fun syncEverything(
        context: Context,
        force: Boolean = false
    ) {
        GlobalSyncer.sync(
            context = context,
            force = force
        )
    }

    fun isSyncing() = GlobalSyncer.isSyncing
}