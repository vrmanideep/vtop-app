package com.vtop.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.vtop.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Locale

class NextClassWidget : GlanceAppWidget() {

    private fun getGlanceReminderColor(type: String): Color {
        return when (type.uppercase(Locale.ROOT)) {
            "QUIZ" -> Color(0xDC3F15A2)
            "ASSIGNMENT" -> Color(0xFF03A9F4)
            "VIVA" -> Color(0xFFB3FF00)
            "RECORD" -> Color(0xFF4CAFA3)
            "OTHERS" -> Color(0xFF24CDE8)
            else -> Color.White
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Safely fetch data
        val classData = try {
            WidgetLogic.getNextClassData(context)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        // Capture any error message to display on the widget
        val errorMessage = try {
            WidgetLogic.getNextClassData(context)
            null
        } catch (e: Exception) {
            e.message ?: "Unknown Error"
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(Color(0xFF141414))
                    .cornerRadius(24.dp)
                    .padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.Start
            ) {
                if (errorMessage != null) {
                    // Display crash data directly on the widget
                    Text(
                        text = "Widget Error: $errorMessage",
                        style = TextStyle(
                            color = ColorProvider(day = Color.Red, night = Color.Red),
                            fontSize = 12.sp
                        )
                    )
                } else if (classData == null) {
                    Text(
                        text = "No upcoming classes today 🎉",
                        style = TextStyle(
                            color = ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 16.sp
                        )
                    )
                } else {
                    val nextClass = classData.first
                    val upcomingReminder = classData.second

                    Text(
                        text = "NEXT CLASS",
                        style = TextStyle(
                            color = ColorProvider(day = Color.Gray, night = Color.Gray),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))

                    Text(
                        text = "${nextClass.courseCode} - ${nextClass.courseName}",
                        style = TextStyle(
                            color = ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))

                    val cyanColor = Color(0xFF29B6F6)
                    Text(
                        text = "${nextClass.venue} - ${nextClass.timeSlot}",
                        style = TextStyle(
                            color = ColorProvider(day = cyanColor, night = cyanColor),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))

                    Text(
                        text = "${nextClass.faculty} - ${nextClass.slot}",
                        style = TextStyle(
                            color = ColorProvider(day = Color.LightGray, night = Color.LightGray),
                            fontSize = 14.sp
                        ),
                        maxLines = 1
                    )

                    if (upcomingReminder != null) {
                        Spacer(modifier = GlanceModifier.height(16.dp))

                        val formattedDate = try {
                            val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                            val targetFormat = SimpleDateFormat("d-M", Locale.ENGLISH)
                            val parsedDate = originalFormat.parse(upcomingReminder.date)
                            parsedDate?.let { targetFormat.format(it) } ?: upcomingReminder.date
                        } catch (_: Exception) {
                            upcomingReminder.date
                        }

                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E))
                                .cornerRadius(12.dp)
                                .padding(12.dp)
                        ) {
                            val reminderColor = getGlanceReminderColor(upcomingReminder.type)
                            Text(
                                text = "${upcomingReminder.type} • $formattedDate",
                                style = TextStyle(
                                    color = ColorProvider(day = reminderColor, night = reminderColor),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )

                            Spacer(modifier = GlanceModifier.height(4.dp))

                            Text(
                                text = upcomingReminder.syllabus,
                                style = TextStyle(
                                    color = ColorProvider(day = Color.LightGray, night = Color.LightGray),
                                    fontSize = 12.sp
                                ),
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}