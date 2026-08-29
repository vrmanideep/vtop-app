@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UseKtx", "RedundantSamConstructor")

package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vtop.models.OutingModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import com.composables.icons.lucide.*
import com.vtop.utils.NotificationHelper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.vtop.utils.AnalyticsManager
import androidx.core.content.FileProvider

private val OutingColorSuccess = Color(0xFF4ADE80)
private val OutingColorWarning = Color(0xFFFBBF24)
private val OutingColorDanger = Color(0xFFE53935)
private const val PENDING_BLOCK_DAYS = 5

private fun String.toTitleCase(): String {
    return this.lowercase(Locale.getDefault()).split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

private fun formatDate(dateStr: String?, yearFormat: Boolean = false): String {
    if (dateStr.isNullOrBlank()) return "--"
    return try {
        val isDashed = dateStr.contains("-") && dateStr.split("-")[0].length == 4
        val inFmt = if (isDashed) "yyyy-MM-dd" else "dd-MMM-yyyy"
        val outFmt = if (yearFormat) "dd MMM yyyy" else "dd MMM"
        val date = SimpleDateFormat(inFmt, Locale.ENGLISH).parse(dateStr)
        if (date != null) SimpleDateFormat(outFmt, Locale.ENGLISH).format(date) else dateStr
    } catch (_: Exception) {
        dateStr
    }
}

private fun isStrictlyApproved(statusUpper: String): Boolean {
    return (statusUpper.contains("ISSUED") || statusUpper.contains("AVAILED") || statusUpper.contains("APPROVE") || statusUpper.contains("ACCEPT")) &&
            !statusUpper.contains("WAITING") && !statusUpper.contains("PENDING") && !statusUpper.contains("FORWARD")
}

private fun calculateLiveProgress(outD: String, outT: String, inD: String, inT: String, currentMillis: Long, isWeekend: Boolean): Triple<String, Float, Boolean> {
    try {
        val dateFmtIn = if (outD.contains("-") && outD.split("-")[0].length == 4) "yyyy-MM-dd" else "dd-MMM-yyyy"
        val sdf = SimpleDateFormat("$dateFmtIn hh:mm a", Locale.ENGLISH)

        val leaveStr: String
        val returnStr: String

        if (isWeekend) {
            val parts = outT.split("-")
            val t1 = parts.getOrNull(0)?.trim() ?: return Triple("", 0f, false)
            val t2 = parts.getOrNull(1)?.trim() ?: return Triple("", 0f, false)
            leaveStr = "$outD $t1"
            returnStr = "$outD $t2"
        } else {
            leaveStr = "$outD ${outT.replace("-", "").trim()}"
            returnStr = "$inD ${inT.replace("-", "").trim()}"
        }

        val leaveDate = sdf.parse(leaveStr)
        val returnDate = sdf.parse(returnStr)

        if (leaveDate != null && returnDate != null) {
            val totalMs = returnDate.time - leaveDate.time
            val elapsedMs = currentMillis - leaveDate.time

            if (totalMs <= 0) return Triple("0h duration", 0f, false)

            if (elapsedMs <= 0) {
                val startsInMs = -elapsedMs
                val startsInHours = startsInMs / (1000 * 60 * 60)
                val startsInMins = (startsInMs / (1000 * 60)) % 60
                val timeString = if (startsInHours > 0) "${startsInHours}h ${startsInMins}m" else "${startsInMins}m"
                return Triple("Starts in $timeString", 0f, false)
            }

            val pct = (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            val pctInt = (pct * 100).toInt()

            if (pct >= 1f) {
                return Triple("Completed", 1f, false)
            }

            val remMs = totalMs - elapsedMs
            val remH = remMs / (1000 * 60 * 60)
            val remM = (remMs / (1000 * 60)) % 60
            val text = if (remH > 24) "${remH / 24}d ${remH % 24}h remaining · $pctInt%" else "${remH}h ${remM}m remaining · $pctInt%"

            return Triple(text, pct, true)
        }
    } catch (_: Exception) {}
    return Triple("", 0f, false)
}

private fun sharePdf(context: Context, sourceFile: File, leaveId: String) {
    try {
        // Securely expose the cached file to other apps using FileProvider
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", sourceFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Outpass"))
    } catch (e: Exception) {
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun savePdfToDownloads(context: Context, sourceFile: File, fileName: String) {
    try {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outStream ->
                    sourceFile.inputStream().use { it.copyTo(outStream) }
                }

                // FIX: Use the public MediaStore URI directly instead of the restricted FileProvider
                NotificationHelper.showDownloadNotificationFromUri(
                    context = context,
                    uri = uri,
                    fileName = fileName,
                    mimeType = "application/pdf",
                    title = "Download Complete",
                    description = "Tap to open $fileName"
                )
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)

            NotificationHelper.showDownloadNotification(
                context = context,
                file = destFile,
                title = "Download Complete",
                description = "Tap to open $fileName"
            )
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NewApi")
@Composable
fun VtopOutingsTab(outingsData: List<OutingModel>, handler: OutingActionHandler) {
    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Outings_Screen") }
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var showWizardType by remember { mutableStateOf<String?>(null) }
    var isFetchingForm by remember { mutableStateOf(false) }

    var viewingPdfFile by remember { mutableStateOf<File?>(null) }
    var fetchingPdfIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    isFetchingForm = true
                    val isGeneral = pagerState.currentPage == 0
                    if (isGeneral) {
                        handler.onFetchGeneralFormData { data ->
                            isFetchingForm = false
                            if (data != null && data["error"] == null) showWizardType = "GENERAL"
                            else Toast.makeText(context, data?.get("error") ?: "Failed to load form from VTOP.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        handler.onFetchWeekendFormData { data ->
                            isFetchingForm = false
                            if (data != null && data["error"] == null) showWizardType = "WEEKEND"
                            else Toast.makeText(context, data?.get("error") ?: "Weekend Outing portal is closed.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 70.dp)
            ) {
                if (isFetchingForm) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("+ New request", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val currentType = if (page == 0) "GENERAL" else "WEEKEND"

                val filteredOutings = outingsData.filter {
                    it.type.trim().uppercase(Locale.getDefault()) == currentType
                }.sortedByDescending { outing ->
                    try {
                        val dateStr = outing.fromDate
                        if (dateStr.contains("-") && dateStr.split("-")[0].length == 4) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dateStr)?.time ?: 0L
                        } else if (dateStr.isNotEmpty()) {
                            SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).parse(dateStr)?.time ?: 0L
                        } else 0L
                    } catch (_: Exception) { 0L }
                }

                val activeOutings = filteredOutings.filter {
                    val s = it.status.uppercase(Locale.getDefault())
                    s.contains("ACCEPT") || s.contains("APPROVE") || s.contains("PENDING") || s.contains("WAITING") || s.contains("FORWARD") || s.contains("ISSUED") || s.contains("AVAIL")
                }
                val pastOutings = filteredOutings.filter { !activeOutings.contains(it) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 140.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (activeOutings.isNotEmpty()) {
                        item { Text("ACTIVE PASSES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp)) }

                        items(
                            items = activeOutings,
                            key = { outing ->
                                "${outing.type}_${outing.id}_${outing.fromDate}_${outing.fromTime}"
                            }
                        ) { outing ->
                            ActiveOutingCard(
                                outing = outing,
                                isFetching = fetchingPdfIds.contains(outing.id),
                                isSharing = fetchingPdfIds.contains(outing.id + "_share"),
                                isDownloading = fetchingPdfIds.contains(outing.id + "_dl"),
                                onViewPass = {
                                    fetchingPdfIds = fetchingPdfIds + outing.id
                                    handler.onViewPass(outing.id, outing.type == "WEEKEND") { file: File? ->
                                        fetchingPdfIds = fetchingPdfIds - outing.id
                                        if (file != null) viewingPdfFile = file
                                        else Toast.makeText(context, "Failed to download outpass.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSharePass = {
                                    fetchingPdfIds = fetchingPdfIds + (outing.id + "_share")
                                    handler.onViewPass(outing.id, outing.type == "WEEKEND") { file: File? ->
                                        fetchingPdfIds = fetchingPdfIds - (outing.id + "_share")
                                        if (file != null) sharePdf(context, file, outing.id)
                                        else Toast.makeText(context, "Failed to download pass for sharing.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDownloadPass = {
                                    fetchingPdfIds = fetchingPdfIds + (outing.id + "_dl")
                                    handler.onViewPass(outing.id, outing.type == "WEEKEND") { file: File? ->
                                        fetchingPdfIds = fetchingPdfIds - (outing.id + "_dl")
                                        if (file != null) savePdfToDownloads(context, file, "${outing.id}.pdf")
                                        else Toast.makeText(context, "Failed to download outpass.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDelete = { handler.onDelete(outing.id, outing.type == "WEEKEND") }
                            )
                        }
                    }

                    if (pastOutings.isNotEmpty()) {
                        item { Text("HISTORY", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(top = 16.dp)) }

                        items(
                            items = pastOutings,
                            key = { outing ->
                                "${outing.type}_${outing.id}_${outing.fromDate}_${outing.fromTime}"
                            }
                        ) { outing ->
                            HistoryOutingCard(outing)
                        }
                    }

                    if (activeOutings.isEmpty() && pastOutings.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillParentMaxHeight(0.7f), // Forces it to take up most of the screen so dragging works
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("No outings yet this semester", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Tap the button below to submit your first request", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 80.dp),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)) },
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("General", fontSize = 15.sp, fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Weekend", fontSize = 15.sp, fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showWizardType != null) {
        Dialog(
            onDismissRequest = { showWizardType = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                OutingWizardDialog(
                    type = showWizardType!!,
                    onDismiss = { showWizardType = null },
                    onSubmitGeneral = { p, purp, fD, tD, fT, tT -> handler.onGeneralSubmit(p, purp, fD, tD, fT, tT); showWizardType = null },
                    onSubmitWeekend = { place, purp, d, t, c -> handler.onWeekendSubmit(place, purp, d, t, c); showWizardType = null }
                )
            }
        }
    }

    viewingPdfFile?.let { InAppPdfViewer(it) { viewingPdfFile = null } }
}

@Composable
private fun ApprovalJourney(statusStr: String, type: String) {
    val s = statusStr.uppercase(Locale.getDefault())
    val themePrimary = MaterialTheme.colorScheme.primary

    val isSubmitted = true // Always true if the card exists
    val isMentorApproved = !s.contains("MENTOR") && (s.contains("WARDEN") || s.contains("ACCEPT") || s.contains("APPROVE"))
    val isWardenApproved = s.contains("ACCEPT") || s.contains("APPROVE") || s.contains("ISSUED")
    val isRejected = s.contains("REJECT") || s.contains("CANCEL") || s.contains("DECLINE")

    val steps = listOf("Submitted", "Mentor Approval", "Warden Approval", "Pass Available")

    val currentStep = when {
        isRejected -> -1 // Journey stopped
        isWardenApproved -> 3
        isMentorApproved -> 2
        else -> 1 // Still at step 1 (Waiting for Mentor)
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("Approval journey", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

        steps.forEachIndexed { index, title ->
            val isCompleted = index < currentStep
            val isCurrent = index == currentStep

            val dotColor = when {
                currentStep == -1 && index <= 1 -> OutingColorDanger // Show path to rejection
                isCompleted -> OutingColorSuccess
                isCurrent -> themePrimary
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }

            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(20.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
                    if (index != steps.lastIndex) {
                        Box(modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .weight(1f)
                            .background(if (index < currentStep && currentStep != -1) OutingColorSuccess else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = 12.dp, bottom = if (index == steps.lastIndex) 0.dp else 24.dp).offset(y = (-2).dp)) {
                    Text(title, color = if (index <= currentStep || currentStep == -1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    if (isCurrent) {
                        Text(statusStr.toTitleCase(), color = dotColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveOutingCard(
    outing: OutingModel,
    isFetching: Boolean,
    isSharing: Boolean,
    isDownloading: Boolean,
    onViewPass: () -> Unit,
    onSharePass: () -> Unit,
    onDownloadPass: () -> Unit,
    onDelete: () -> Unit
) {
    val statusUpper = outing.status.uppercase(Locale.getDefault())
    val isApproved = isStrictlyApproved(statusUpper)
    val isPending = !isApproved

    if (isPending) {
        val context = LocalContext.current
        val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
        val offsetX = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        var showConfirmDialog by remember { mutableStateOf(false) }
        var thresholdCrossed by remember { mutableStateOf(false) }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    showConfirmDialog = false
                    coroutineScope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                },
                title = { Text("Cancel Request", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                text = { Text("Are you sure you want to cancel this outing request?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    TextButton(onClick = { showConfirmDialog = false; onDelete() }) {
                        Text("Yes, Cancel", color = OutingColorDanger, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showConfirmDialog = false
                        coroutineScope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                    }) { Text("Keep", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
            Box(
                modifier = Modifier.matchParentSize().background(OutingColorDanger).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("Cancel", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                val target = offsetX.value + dragAmount
                                if (target <= 0f) {
                                    coroutineScope.launch { offsetX.snapTo(target) }

                                    if (target < -200f && !thresholdCrossed) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(100)
                                        }
                                        thresholdCrossed = true
                                    } else if (target >= -200f) {
                                        thresholdCrossed = false
                                    }
                                }
                            },
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (offsetX.value < -200f) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator.vibrate(150)
                                        }

                                        offsetX.animateTo(-300f, animationSpec = spring())
                                        showConfirmDialog = true
                                    } else {
                                        offsetX.animateTo(0f, animationSpec = spring())
                                    }
                                    thresholdCrossed = false
                                }
                            }
                        )
                    }
            ) {
                ActiveCardContent(outing, isFetching, isSharing, isDownloading, onViewPass, onSharePass, onDownloadPass)
            }
        }
    } else {
        ActiveCardContent(outing, isFetching, isSharing, isDownloading, onViewPass, onSharePass, onDownloadPass)
    }
}

@Composable
private fun ActiveCardContent(
    outing: OutingModel,
    isFetching: Boolean,
    isSharing: Boolean,
    isDownloading: Boolean,
    onViewPass: () -> Unit,
    onSharePass: () -> Unit,
    onDownloadPass: () -> Unit
) {
    val statusUpper = outing.status.uppercase(Locale.getDefault())
    val isApproved = isStrictlyApproved(statusUpper)

    val shortStatus = when {
        isApproved -> "Approved"
        statusUpper.contains("FORWARD") -> "Forwarded"
        else -> "Pending"
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val statusColor = if (isApproved) OutingColorSuccess else OutingColorWarning
    val statusBg = statusColor.copy(alpha = 0.15f)

    var currentMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (isApproved) {
        LaunchedEffect(Unit) {
            while(true) {
                delay(60_000)
                currentMillis = System.currentTimeMillis()
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${outing.type.uppercase(Locale.getDefault())} · $shortStatus", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (isApproved) {
                    Box(modifier = Modifier
                        .background(statusBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(shortStatus, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(modifier = Modifier
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(shortStatus, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(outing.place, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(outing.purpose, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(12.dp))

            val outT = if (outing.type.uppercase(Locale.getDefault()) == "WEEKEND") outing.fromTime.substringBefore("-").trim() else outing.fromTime.replace("-", "").trim()
            val inT = if (outing.type.uppercase(Locale.getDefault()) == "WEEKEND") outing.fromTime.substringAfter("-").trim() else outing.toTime.replace("-", "").trim()
            val inD = if (outing.type.uppercase(Locale.getDefault()) == "WEEKEND") outing.fromDate else outing.toDate

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatDate(outing.fromDate, true), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(outT, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Text(
                    text = "to",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(formatDate(inD, true), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(inT, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (isApproved) {
                val (durationText, progressPct, isOngoing) = calculateLiveProgress(outing.fromDate, outing.fromTime, outing.toDate, outing.toTime, currentMillis, outing.type.uppercase(Locale.getDefault()) == "WEEKEND")

                if (isOngoing) {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        if (progressPct > 0f) {
                            Box(Modifier
                                .weight(progressPct)
                                .height(3.dp)
                                .background(
                                    themePrimary,
                                    RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)
                                ))
                        }
                        if (progressPct < 1f) {
                            Box(Modifier
                                .weight(1f - progressPct)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                .align(Alignment.CenterVertically))
                        }
                    }
                }

                val showDuration = durationText.isNotBlank() && (durationText != "Completed" || isOngoing)
                if (showDuration) {
                    val topPadding = if (isOngoing) 8.dp else 16.dp
                    Text(durationText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = topPadding))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .background(statusBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Lucide.FileCheck, contentDescription = null, modifier = Modifier.size(12.dp), tint = statusColor)
                        Spacer(Modifier.width(6.dp))
                        Text("Pass available", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isWorking = isFetching || isSharing || isDownloading

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clip(CircleShape)
                                .clickable(enabled = !isWorking) { onViewPass() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFetching) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                            else Icon(Lucide.SquareArrowUpRight, contentDescription = "View", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clip(CircleShape)
                                .clickable(enabled = !isWorking) { onDownloadPass() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                            else Icon(Lucide.FileDown, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                .clip(CircleShape)
                                .clickable(enabled = !isWorking) { onSharePass() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSharing) CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                strokeWidth = 2.dp
                            )
                            else Icon(
                                Lucide.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                ApprovalJourney(outing.status, outing.type)
                Spacer(modifier = Modifier.height(12.dp))
                Text("← swipe left to cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontStyle = FontStyle.Italic)
            }
        }
    }
}

@Composable
private fun HistoryOutingCard(outing: OutingModel) {
    val statusUpper = outing.status.uppercase(Locale.getDefault())
    val isApproved = isStrictlyApproved(statusUpper)
    val isRejected = statusUpper.contains("REJECT") || statusUpper.contains("CANCEL") || statusUpper.contains("DECLINE")

    val statusColor = when {
        isApproved -> OutingColorSuccess
        isRejected -> OutingColorDanger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)) {
                Text(outing.place, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val outT = if (outing.type.uppercase(Locale.getDefault()) == "WEEKEND") outing.fromTime.substringBefore("-").trim() else outing.fromTime.replace("-", "").trim()
                Text("${formatDate(outing.fromDate)} · $outT", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Text(outing.status.toTitleCase(), color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        }
    }
}

// --------------------------------------------------------
// UNIFIED WIZARD FORMS
// --------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormDatePickerDialog(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    val themePrimary = MaterialTheme.colorScheme.primary

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                onDismiss()
            }) { Text("OK", color = themePrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                titleContentColor = themePrimary,
                headlineContentColor = MaterialTheme.colorScheme.onSurface,
                weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                dayContentColor = MaterialTheme.colorScheme.onSurface,
                selectedDayContainerColor = themePrimary,
                selectedDayContentColor = Color.White,
                todayDateBorderColor = themePrimary,
                todayContentColor = themePrimary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    val themePrimary = MaterialTheme.colorScheme.primary

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(timePickerState.hour, timePickerState.minute)
                onDismiss()
            }) { Text("OK", color = themePrimary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Select Time", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    clockDialSelectedContentColor = Color.White,
                    clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    selectorColor = themePrimary,
                    timeSelectorSelectedContainerColor = themePrimary.copy(alpha = 0.2f),
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    timeSelectorSelectedContentColor = themePrimary,
                    timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutingWizardDialog(
    type: String,
    onDismiss: () -> Unit,
    onSubmitGeneral: (String, String, String, String, String, String) -> Unit,
    onSubmitWeekend: (String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val themePrimary = MaterialTheme.colorScheme.primary
    var currentStep by remember { mutableIntStateOf(1) }

    // Shared State
    var generalPlace by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }

    // Weekend State
    val weekendPlaceOptions = listOf("Vijayawada", "Guntur", "Tenali", "Eluru")
    var weekendSelectedPlace by remember { mutableStateOf(weekendPlaceOptions[0]) }

    var contact by remember { mutableStateOf("") }
    val weekendDates = remember {
        val list = mutableListOf<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.HOUR_OF_DAY, 24)
        val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        repeat(15) {
            val d = cal.get(Calendar.DAY_OF_WEEK)
            if (d == Calendar.SUNDAY || d == Calendar.MONDAY) list.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
            if (list.size >= 2) return@repeat
        }
        list
    }
    var weekendSelectedDate by remember { mutableStateOf(if (weekendDates.isNotEmpty()) weekendDates[0] else "") }
    val weekendTimeOptions = listOf("9:30 AM- 3:30PM", "10:30 AM- 4:30PM", "11:30 AM- 5:30PM", "12:30 PM- 6:30PM")
    var weekendSelectedTime by remember { mutableStateOf(weekendTimeOptions[0]) }

    // General State
    val initialCal = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 24) } }
    var outDateMillis by remember { mutableLongStateOf(initialCal.timeInMillis) }
    var inDateMillis by remember { mutableLongStateOf(initialCal.timeInMillis) }
    var outHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY).coerceIn(6, 23)) }
    var outMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE).coerceIn(0, 59)) }

    val initialInCal = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 27) } }
    var inHour by remember { mutableIntStateOf(initialInCal.get(Calendar.HOUR_OF_DAY).coerceIn(6, 23)) }
    var inMinute by remember { mutableIntStateOf(initialInCal.get(Calendar.MINUTE).coerceIn(0, 59)) }

    val displayDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    val submitDateFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

    fun formatTimeDisplay(hour: Int, min: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min) }
        return timeFmt.format(cal.time)
    }

    var showOutDatePicker by remember { mutableStateOf(false) }
    var showInDatePicker by remember { mutableStateOf(false) }
    var showOutTimePicker by remember { mutableStateOf(false) }
    var showInTimePicker by remember { mutableStateOf(false) }

    if (showOutDatePicker) {
        FormDatePickerDialog(initialDateMillis = outDateMillis, onDateSelected = {
            outDateMillis = it
            if (inDateMillis < outDateMillis) inDateMillis = outDateMillis
            showOutDatePicker = false
        }, onDismiss = { showOutDatePicker = false })
    }
    if (showInDatePicker) {
        FormDatePickerDialog(initialDateMillis = inDateMillis, onDateSelected = {
            inDateMillis = it
            showInDatePicker = false
        }, onDismiss = { showInDatePicker = false })
    }

    if (showOutTimePicker) {
        FormTimePickerDialog(initialHour = outHour, initialMinute = outMinute, onTimeSelected = { h, m ->
            if (h in 6..23) { outHour = h; outMinute = m; showOutTimePicker = false }
            else Toast.makeText(context, "Invalid time! Outings allowed 6 AM - 11 PM", Toast.LENGTH_LONG).show()
        }, onDismiss = { showOutTimePicker = false })
    }
    if (showInTimePicker) {
        FormTimePickerDialog(initialHour = inHour, initialMinute = inMinute, onTimeSelected = { h, m ->
            if (h in 6..23) { inHour = h; inMinute = m; showInTimePicker = false }
            else Toast.makeText(context, "Invalid time! Outings allowed 6 AM - 11 PM", Toast.LENGTH_LONG).show()
        }, onDismiss = { showInTimePicker = false })
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = themePrimary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha=0.3f),
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface) }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text("New request", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(type.toTitleCase(), color = themePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(32.dp).background(if (currentStep >= 1) themePrimary else MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) { Text("1", color = Color.White, fontWeight = FontWeight.Bold) }
                Text("Details", color = if (currentStep >= 1) themePrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.width(60.dp).height(2.dp).background(if (currentStep == 2) themePrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)).offset(y = (-8).dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(32.dp).background(if (currentStep == 2) themePrimary else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) { Text("2", color = if(currentStep == 2) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
                Text("Review", color = if (currentStep == 2) themePrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (currentStep == 1) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).weight(1f)) {
                Text("Where are you going?", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

                if (type == "WEEKEND") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(weekendPlaceOptions) { p ->
                            val isSelected = weekendSelectedPlace == p
                            Box(modifier = Modifier
                                .border(1.dp, if (isSelected) themePrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(50))
                                .background(if (isSelected) themePrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .clickable { weekendSelectedPlace = p }
                                .padding(horizontal = 16.dp, vertical = 10.dp)) {
                                Text(p, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                } else {
                    OutlinedTextField(value = generalPlace, onValueChange = { if(it.length <= 25) generalPlace = it }, label = { Text("Place of Visit") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors, singleLine = true, shape = RoundedCornerShape(12.dp))
                }

                Spacer(Modifier.height(24.dp))
                Text("Purpose", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(value = purpose, onValueChange = { if(it.length <= 25) purpose = it }, placeholder = { Text("Brief reason for leave") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors, singleLine = true, shape = RoundedCornerShape(12.dp))

                if (type == "WEEKEND") {
                    Spacer(Modifier.height(24.dp))
                    Text("Contact Number", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = contact, onValueChange = { if (it.length <= 10 && it.all { char -> char.isDigit() }) contact = it }, placeholder = { Text("+91") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(12.dp))

                    Spacer(Modifier.height(24.dp))
                    Text("When are you leaving?", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    var expandedWDate by remember { mutableStateOf(false) }
                    @Suppress("DEPRECATION")
                    ExposedDropdownMenuBox(expanded = expandedWDate, onExpandedChange = { expandedWDate = it }) {
                        OutlinedTextField(value = weekendSelectedDate, onValueChange = {}, readOnly = true, trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors, shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = expandedWDate, onDismissRequest = { expandedWDate = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            weekendDates.forEach { option -> DropdownMenuItem(text = { Text(option, color = MaterialTheme.colorScheme.onSurface) }, onClick = { weekendSelectedDate = option; expandedWDate = false }) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    var expandedWTime by remember { mutableStateOf(false) }
                    @Suppress("DEPRECATION")
                    ExposedDropdownMenuBox(expanded = expandedWTime, onExpandedChange = { expandedWTime = it }) {
                        OutlinedTextField(value = weekendSelectedTime, onValueChange = {}, readOnly = true, trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors, shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = expandedWTime, onDismissRequest = { expandedWTime = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            weekendTimeOptions.forEach { option -> DropdownMenuItem(text = { Text(option, color = MaterialTheme.colorScheme.onSurface) }, onClick = { weekendSelectedTime = option; expandedWTime = false }) }
                        }
                    }
                } else {
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Leave", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            OutlinedCard(onClick = { showOutDatePicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.3f))) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Lucide.CalendarDays, null, tint = themePrimary, modifier = Modifier.size(16.dp))
                                    Text(displayDateFmt.format(Date(outDateMillis)), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedCard(onClick = { showOutTimePicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.3f))) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Lucide.Clock, null, tint = themePrimary, modifier = Modifier.size(16.dp))
                                    Text(formatTimeDisplay(outHour, outMinute), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Return", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            OutlinedCard(onClick = { showInDatePicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.3f))) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Lucide.CalendarDays, null, tint = themePrimary, modifier = Modifier.size(16.dp))
                                    Text(displayDateFmt.format(Date(inDateMillis)), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedCard(onClick = { showInTimePicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.3f))) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Lucide.Clock, null, tint = themePrimary, modifier = Modifier.size(16.dp))
                                    Text(formatTimeDisplay(inHour, inMinute), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            val canProceed = if (type == "WEEKEND") purpose.isNotBlank() && contact.length == 10 else generalPlace.isNotBlank() && purpose.isNotBlank()
            Button(
                onClick = { if (canProceed) {
                    if (type == "GENERAL" && inDateMillis < outDateMillis) Toast.makeText(context, "Return date must be after Leave date", Toast.LENGTH_SHORT).show()
                    else currentStep = 2
                }},
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (canProceed) themePrimary else MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Next — review >", color = if (canProceed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 16.sp) }

        } else if (currentStep == 2) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).weight(1f)) {
                Text("Review your request", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

                val finalPlace = if (type == "WEEKEND") weekendSelectedPlace else generalPlace

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Destination", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text(finalPlace, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Purpose", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text(purpose, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.2f))
                        Spacer(Modifier.height(16.dp))

                        if (type == "WEEKEND") {
                            val outT = weekendSelectedTime.substringBefore("-").trim()
                            val inT = weekendSelectedTime.substringAfter("-").trim()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Leave", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text("${formatDate(weekendSelectedDate, true)} · $outT", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Return", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text("${formatDate(weekendSelectedDate, true)} · $inT", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Leave", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text("${displayDateFmt.format(Date(outDateMillis))} · ${formatTimeDisplay(outHour, outMinute)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Return", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); Text("${displayDateFmt.format(Date(inDateMillis))} · ${formatTimeDisplay(inHour, inMinute)}", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                val warningText = if (type == "WEEKEND") "Warden approval is required before your pass is issued." else "Mentor and Warden approval is required before your pass is issued."
                Row(modifier = Modifier.fillMaxWidth().background(OutingColorWarning.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).border(1.dp, OutingColorWarning.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = OutingColorWarning, modifier = Modifier.size(20.dp))
                    Text(warningText, color = OutingColorWarning, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
                }
            }

            Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { currentStep = 1 }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) { Text("Edit", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Button(
                    onClick = {
                        if (type == "WEEKEND") {
                            onSubmitWeekend(weekendSelectedPlace, purpose, weekendSelectedDate, weekendSelectedTime, contact)
                        } else {
                            val subOutDate = submitDateFmt.format(Date(outDateMillis))
                            val subInDate = submitDateFmt.format(Date(inDateMillis))
                            val subOutTime = String.format(Locale.ENGLISH, "%02d:%02d", outHour, outMinute)
                            val subInTime = String.format(Locale.ENGLISH, "%02d:%02d", inHour, inMinute)
                            onSubmitGeneral(generalPlace, purpose, subOutDate, subInDate, subOutTime, subInTime)
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = themePrimary), shape = RoundedCornerShape(16.dp)
                ) { Text("Submit", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            }
        }
    }
}

// --------------------------------------------------------
// IN-APP PDF VIEWER
// --------------------------------------------------------
@Composable
fun InAppPdfViewer(pdfFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val themePrimary = MaterialTheme.colorScheme.primary
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            try {
                val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(fileDescriptor)
                val page = pdfRenderer.openPage(0)

                val bmp = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pdfRenderer.close()
                fileDescriptor.close()
                bitmap = bmp
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface) }
                Text("Outpass Document", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(onClick = { savePdfToDownloads(context, pdfFile, pdfFile.name) }) {
                    Icon(Lucide.FileDown, null, tint = themePrimary)
                }
            }
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f); offset += pan
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                } ?: CircularProgressIndicator(color = themePrimary)
            }
        }
    }
}