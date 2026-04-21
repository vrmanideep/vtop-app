package com.vtop.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    // This connects the Android OS to your actual Glance UI
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}