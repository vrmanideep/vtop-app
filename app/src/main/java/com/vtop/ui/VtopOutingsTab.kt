package com.vtop.ui

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import android.os.Environment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vtop.models.OutingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NewApi")
@Composable
fun VtopOutingsTab(handler: OutingActionHandler) {
    val context = LocalContext.current
    val outings = VtopAppBridge.outingsState.value
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("General", "Weekend")

    var showGeneralSheet by remember { mutableStateOf(false) }
    var showWeekendSheet by remember { mutableStateOf(false) }
    var isFetchingForm by remember { mutableStateOf(false) }
    var formPrefillData by remember { mutableStateOf<Map<String, String>?>(null) }

    var viewingPdfFile by remember { mutableStateOf<File?>(null) }
    var isFetchingPdf by remember { mutableStateOf(false) }

    val currentType = if (selectedTabIndex == 0) "GENERAL" else "WEEKEND"

    val filteredOutings = outings.filter { it.type == currentType }.sortedByDescending { outing ->
        try {
            val dateStr = outing.fromDate
            if (dateStr.contains("-") && dateStr.split("-")[0].length == 4) {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH))
            } else if (dateStr.isNotEmpty()) {
                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH))
            } else LocalDate.MIN
        } catch (e: Exception) { LocalDate.MIN }
    }

    val activeOutings = filteredOutings.filter { it.status.contains("Accepted", true) || it.status.contains("Approved", true) || it.status.contains("Pending", true) || it.status.contains("Waiting", true) }
    val pastOutings = filteredOutings.filter { !activeOutings.contains(it) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Black,
            contentColor = Color.White,
            indicator = { },
            divider = { },
            modifier = Modifier.padding(top = 0.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if(selectedTabIndex == index) FontWeight.Black else FontWeight.Normal,
                            color = if(selectedTabIndex == index) Color.White else Color.Gray
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            isFetchingForm = true
                            if (selectedTabIndex == 0) {
                                handler.onFetchGeneralFormData { data ->
                                    isFetchingForm = false
                                    if (data != null && data["error"] == null) {
                                        formPrefillData = data
                                        showGeneralSheet = true
                                    } else {
                                        val err = data?.get("error") ?: "Failed to load form from VTOP."
                                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                handler.onFetchWeekendFormData { data ->
                                    isFetchingForm = false
                                    if (data != null && data["error"] == null) {
                                        formPrefillData = data
                                        showWeekendSheet = true
                                    } else {
                                        val err = data?.get("error") ?: "Weekend Outing portal is closed."
                                        android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isFetchingForm,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                    ) {
                        if (isFetchingForm) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("APPLY FOR LEAVE", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (activeOutings.isNotEmpty()) {
                    item { Text("Active Passes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    items(activeOutings) { outing ->
                        ActiveOutingCard(
                            outing = outing,
                            isFetching = isFetchingPdf,
                            onCancel = { handler.onDelete(outing.id, outing.type == "WEEKEND") },
                            onViewPass = {
                                isFetchingPdf = true
                                handler.onViewPass(outing.id, outing.type == "WEEKEND") { file ->
                                    isFetchingPdf = false
                                    viewingPdfFile = file
                                }
                            }
                        )
                    }
                }

                if (pastOutings.isNotEmpty()) {
                    item { Text("History", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    items(pastOutings) { outing -> HistoryOutingCard(outing) }
                }
            }
        }
    }

    if (showGeneralSheet && formPrefillData != null) {
        ModalBottomSheet(onDismissRequest = { showGeneralSheet = false }, containerColor = Color(0xFF0A0A0A), dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }) {
            GeneralOutingForm(
                scrapedData = formPrefillData!!,
                onSubmit = { p, purp, fD, tD, fT, tT -> handler.onGeneralSubmit(p, purp, fD, tD, fT, tT); showGeneralSheet = false },
                onCancel = { showGeneralSheet = false }
            )
        }
    }

    if (showWeekendSheet && formPrefillData != null) {
        ModalBottomSheet(onDismissRequest = { showWeekendSheet = false }, containerColor = Color(0xFF0A0A0A), dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }) {
            WeekendOutingForm(
                scrapedData = formPrefillData!!,
                onSubmit = { place, purp, d, t, c -> handler.onWeekendSubmit(place, purp, d, t, c); showWeekendSheet = false },
                onCancel = { showWeekendSheet = false }
            )
        }
    }

    viewingPdfFile?.let { InAppPdfViewer(it) { viewingPdfFile = null } }
}

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekendOutingForm(
    scrapedData: Map<String, String>,
    onSubmit: (String, String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var place by remember { mutableStateOf("Vijayawada") }
    var purpose by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("7058") } // Prefilled numerical context

    val validDates = remember {
        val list = mutableListOf<String>()
        val now = java.time.LocalDateTime.now()
        val minAllowed = now.plusHours(24)
        var current = minAllowed.toLocalDate()
        for (i in 0..14) {
            if (current.dayOfWeek == java.time.DayOfWeek.SUNDAY || current.dayOfWeek == java.time.DayOfWeek.MONDAY) {
                list.add(current.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)))
            }
            current = current.plusDays(1)
            if (list.size >= 2) break
        }
        list
    }

    var selectedDate by remember { mutableStateOf(if (validDates.isNotEmpty()) validDates[0] else "") }

    val timeOptions = listOf("9:30 AM- 3:30PM", "10:30 AM- 4:30PM", "11:30 AM- 5:30PM", "12:30 PM- 6:30PM")
    var selectedTime by remember { mutableStateOf(timeOptions[0]) }

    val placeOptions = listOf("Vijayawada", "Guntur", "Tenali", "Eluru", "Others")
    var expandedPlace by remember { mutableStateOf(false) }
    var expandedDate by remember { mutableStateOf(false) }
    var expandedTime by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        disabledTextColor = Color.Gray, focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.DarkGray, disabledBorderColor = Color(0xFF222222),
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color(0xFF111111)
    )

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 20.dp)) {
        Text("Weekend Outing Application", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(20.dp))

        // Pre-Filled Details
        OutlinedTextField(value = scrapedData["name"] ?: "", onValueChange = {}, label = {Text("Name")}, enabled = false, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = scrapedData["regNo"] ?: "", onValueChange = {}, label = {Text("Reg No")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
            OutlinedTextField(value = scrapedData["appNo"] ?: "", onValueChange = {}, label = {Text("App No")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = scrapedData["block"] ?: "", onValueChange = {}, label = {Text("Block")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
            OutlinedTextField(value = scrapedData["room"] ?: "", onValueChange = {}, label = {Text("Room")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = scrapedData["parentContact"] ?: "", onValueChange = {}, label = {Text("Parent Contact")}, enabled = false, modifier = Modifier.fillMaxWidth(), colors = fieldColors)

        Spacer(modifier = Modifier.height(16.dp))

        // Dropdowns for Selections
        ExposedDropdownMenuBox(expanded = expandedPlace, onExpandedChange = { expandedPlace = it }) {
            OutlinedTextField(value = place, onValueChange = {}, readOnly = true, label = { Text("Place of Visit") }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors)
            ExposedDropdownMenu(expanded = expandedPlace, onDismissRequest = { expandedPlace = false }, modifier = Modifier.background(Color(0xFF222222))) {
                placeOptions.forEach { option -> DropdownMenuItem(text = { Text(option, color = Color.White) }, onClick = { place = option; expandedPlace = false }) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = purpose, onValueChange = { if(it.length <= 20) purpose = it }, label = { Text("Purpose (Max 20)") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)

        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expandedDate, onExpandedChange = { expandedDate = it }) {
            OutlinedTextField(value = selectedDate, onValueChange = {}, readOnly = true, label = { Text("Leave Date") }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors)
            ExposedDropdownMenu(expanded = expandedDate, onDismissRequest = { expandedDate = false }, modifier = Modifier.background(Color(0xFF222222))) {
                validDates.forEach { option -> DropdownMenuItem(text = { Text(option, color = Color.White) }, onClick = { selectedDate = option; expandedDate = false }) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expandedTime, onExpandedChange = { expandedTime = it }) {
            OutlinedTextField(value = selectedTime, onValueChange = {}, readOnly = true, label = { Text("Time Slot") }, modifier = Modifier.fillMaxWidth().menuAnchor(), colors = fieldColors)
            ExposedDropdownMenu(expanded = expandedTime, onDismissRequest = { expandedTime = false }, modifier = Modifier.background(Color(0xFF222222))) {
                timeOptions.forEach { option -> DropdownMenuItem(text = { Text(option, color = Color.White) }, onClick = { selectedTime = option; expandedTime = false }) }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = contact, onValueChange = { if(it.length <= 10) contact = it }, label = { Text("Contact Number") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)

        Spacer(modifier = Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("CANCEL", color = Color.Gray) }
            Button(
                onClick = { if (purpose.isNotBlank() && contact.length == 10 && selectedDate.isNotBlank()) onSubmit(place, purpose, selectedDate, selectedTime, contact) },
                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)
            ) { Text("SUBMIT", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}

// (GeneralOutingForm, ActiveOutingCard, HistoryOutingCard, InAppPdfViewer remain unchanged from the previous version)
@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralOutingForm(scrapedData: Map<String, String>, onSubmit: (String, String, String, String, String, String) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var place by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }

    val minAllowed = java.time.LocalDateTime.now().plusHours(24).plusMinutes(1)
    var outDate by remember { mutableStateOf(minAllowed.toLocalDate()) }
    var inDate by remember { mutableStateOf(minAllowed.toLocalDate()) }
    var outTime by remember { mutableStateOf(minAllowed.toLocalTime()) }
    var inTime by remember { mutableStateOf(minAllowed.toLocalTime().plusHours(3)) }

    val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    val fieldColors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, disabledTextColor = Color.Gray, focusedBorderColor = Color.White, unfocusedBorderColor = Color.DarkGray, disabledBorderColor = Color(0xFF222222), focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color(0xFF111111))
    val showDatePicker = { isOutDate: Boolean -> val current = if (isOutDate) outDate else inDate; val dialog = DatePickerDialog(context, { _, y, m, d -> val selected = LocalDate.of(y, m + 1, d); if (isOutDate) { outDate = selected; if (inDate.isBefore(outDate)) inDate = outDate } else inDate = selected }, current.year, current.monthValue - 1, current.dayOfMonth); dialog.datePicker.minDate = System.currentTimeMillis() + (24 * 60 * 60 * 1000); dialog.show() }
    val showTimePicker = { isOutTime: Boolean -> val current = if (isOutTime) outTime else inTime; val dialog = TimePickerDialog(context, { _, h, m -> if (isOutTime) outTime = LocalTime.of(h, m) else inTime = LocalTime.of(h, m) }, current.hour, current.minute, true); dialog.show() }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 20.dp)) {
        Text("General Leave Application", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(value = scrapedData["name"] ?: "", onValueChange = {}, label = {Text("Name")}, enabled = false, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = scrapedData["regNo"] ?: "", onValueChange = {}, label = {Text("Reg No")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
            OutlinedTextField(value = scrapedData["appNo"] ?: "", onValueChange = {}, label = {Text("App No")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = scrapedData["gender"] ?: "", onValueChange = {}, label = {Text("Gender")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
            OutlinedTextField(value = scrapedData["block"] ?: "", onValueChange = {}, label = {Text("Block")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
            OutlinedTextField(value = scrapedData["room"] ?: "", onValueChange = {}, label = {Text("Room")}, enabled = false, modifier = Modifier.weight(1f), colors = fieldColors)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = place, onValueChange = { if(it.length <= 20) place = it }, label = { Text("Place of Visit") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = purpose, onValueChange = { if(it.length <= 20) purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors)
        Spacer(modifier = Modifier.height(20.dp))
        Text("Leave Details", color = Color.Gray, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = outDate.format(dateFormatter), onValueChange = {}, enabled = false, modifier = Modifier.weight(1f).clickable { showDatePicker(true) }, colors = fieldColors)
            OutlinedTextField(value = outTime.format(timeFormatter), onValueChange = {}, enabled = false, modifier = Modifier.weight(1f).clickable { showTimePicker(true) }, colors = fieldColors)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Return Details", color = Color.Gray, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = inDate.format(dateFormatter), onValueChange = {}, enabled = false, modifier = Modifier.weight(1f).clickable { showDatePicker(false) }, colors = fieldColors)
            OutlinedTextField(value = inTime.format(timeFormatter), onValueChange = {}, enabled = false, modifier = Modifier.weight(1f).clickable { showTimePicker(false) }, colors = fieldColors)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("CANCEL", color = Color.Gray) }
            Button(onClick = { if (place.isNotBlank() && purpose.isNotBlank()) onSubmit(place, purpose, outDate.format(dateFormatter), inDate.format(dateFormatter), outTime.format(timeFormatter), inTime.format(timeFormatter)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp)) { Text("SUBMIT", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun ActiveOutingCard(outing: OutingModel, isFetching: Boolean, onCancel: () -> Unit, onViewPass: () -> Unit) {
    val isApproved = outing.status.contains("Accepted", true) || outing.status.contains("Approved", true)
    val statusColor = if (isApproved) Color(0xFF4CAF50) else Color(0xFFFFC107)

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(outing.status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(outing.place, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(outing.purpose, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("LEAVE", color = Color.Gray, fontSize = 10.sp)
                    Text(outing.fromDate, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(outing.fromTime, color = Color.White, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("RETURN", color = Color.Gray, fontSize = 10.sp)
                    Text(outing.toDate, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(outing.toTime, color = Color.White, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (outing.canDownload && isApproved) {
                    Button(onClick = onViewPass, enabled = !isFetching, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = statusColor)) {
                        if (isFetching) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black) else Text("View Pass", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
                if (!isApproved) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)), border = BorderStroke(1.dp, Color(0xFFF44336))) { Text("CANCEL REQUEST", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun HistoryOutingCard(outing: OutingModel) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(outing.place, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${outing.fromDate} to ${outing.toDate}", color = Color.Gray, fontSize = 12.sp)
            }
            Text(outing.status, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun InAppPdfViewer(pdfFile: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

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
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                Text("Outpass", color = Color.White, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    try {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val destFile = File(downloadsDir, pdfFile.name)
                        pdfFile.copyTo(destFile, overwrite = true)
                        android.widget.Toast.makeText(context, "Saved to Downloads", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) { e.printStackTrace() }
                }) { Icon(Icons.Default.Download, null, tint = Color.White) }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().padding(16.dp).pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); offset += pan } }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit) } ?: CircularProgressIndicator(color = Color(0xFFFFD700))
            }
        }
    }
}