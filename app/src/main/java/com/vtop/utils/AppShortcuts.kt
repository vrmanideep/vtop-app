package com.vtop.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vtop.ui.MainActivity

object AppShortcuts {
    fun setupDynamicShortcuts(context: Context) {
        // 1. Bunk Simulator Shortcut
        val simulatorIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.vtop.SHORTCUT_SIMULATOR"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val simulatorShortcut = ShortcutInfoCompat.Builder(context, "id_simulator")
            .setShortLabel("Simulator")
            .setLongLabel("Open Bunk Simulator")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_compass))
            .setIntent(simulatorIntent)
            .build()

        // 2. Outings Shortcut
        val outingsIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.vtop.SHORTCUT_OUTINGS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val outingsShortcut = ShortcutInfoCompat.Builder(context, "id_outings")
            .setShortLabel("Outings")
            .setLongLabel("Apply or View Outings")
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_send))
            .setIntent(outingsIntent)
            .build()

        // Push shortcuts to the OS
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(simulatorShortcut, outingsShortcut))
    }
}