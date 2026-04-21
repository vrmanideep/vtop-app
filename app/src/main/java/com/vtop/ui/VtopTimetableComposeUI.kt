package com.vtop.ui

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import android.util.Log
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import com.vtop.models.AttendanceModel
import com.vtop.models.CGPASummary
import com.vtop.models.CourseGrade
import com.vtop.models.CourseMark
import com.vtop.models.CourseSession
import com.vtop.models.ExamScheduleModel
import com.vtop.models.GradeHistoryItem
import com.vtop.models.OutingModel
import com.vtop.models.SemesterOption
import com.vtop.models.TimetableModel
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import android.widget.FrameLayout
import com.vtop.network.VtopClient

// --- THEME ---
private val ThemeAccent   = Color.White
private val CardGrey      = Color(0xFF141414)
private val GlassBg       = Color.White.copy(alpha = 0.08f)
private val GlassBorder   = Color.White.copy(alpha = 0.15f)

// --- ENUMS & SHARED CALLBACKS ---
interface AuthActionCallback {
    fun onLoginSubmit(regNo: String, pass: String)
    fun onSemesterSelect(semId: String, semName: String)
}

enum class AuthState { FORM, LOADING_SEMESTERS, SELECT_SEMESTER, DOWNLOADING_DATA, OTP }
enum class DockPosition { TOP, BOTTOM, LEFT, RIGHT }

// --- REMINDER MANAGER ---
data class CourseReminder(
    val id: String = UUID.randomUUID().toString(),
    val courseCode: String,
    val classId: String,
    val type: String,
    val date: String, // yyyy-MM-dd
    val syllabus: String = ""
)

object ReminderManager {
    fun loadReminders(context: Context): List<CourseReminder> {
        val prefs = context.getSharedPreferences("VTOP_Reminders", Context.MODE_PRIVATE) // Keeping identical key to not lose old data
        val jsonStr = prefs.getString("data", "[]") ?: "[]"
        val list = mutableListOf<CourseReminder>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dDateStr = obj.getString("date")
                val dDate = sdf.parse(dDateStr) ?: Date()
                val targetCal = Calendar.getInstance().apply { time = dDate }

                if (!targetCal.before(todayCal)) { // Auto-discard passed reminders
                    list.add(CourseReminder(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        courseCode = obj.getString("courseCode"),
                        classId = obj.getString("classId"),
                        type = obj.getString("type"),
                        date = dDateStr,
                        syllabus = obj.optString("syllabus", "")
                    ))
                }
            }
            saveReminders(context, list)
        } catch (_: Exception) { }
        return list
    }

    fun saveReminders(context: Context, reminders: List<CourseReminder>) {
        val array = JSONArray()
        reminders.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id); obj.put("courseCode", r.courseCode); obj.put("classId", r.classId);
            obj.put("type", r.type); obj.put("date", r.date); obj.put("syllabus", r.syllabus)
            array.put(obj)
        }
        context.getSharedPreferences("VTOP_Reminders", Context.MODE_PRIVATE).edit {
            putString("data", array.toString())
        }
    }

    fun getExportJsonString(context: Context): String {
        val reminders = loadReminders(context)
        val array = JSONArray()
        reminders.forEach { r ->
            array.put(JSONObject().apply { put("id", r.id); put("courseCode", r.courseCode); put("classId", r.classId); put("type", r.type); put("date", r.date); put("syllabus", r.syllabus) })
        }
        return array.toString()
    }

    fun importFromJsonString(context: Context, jsonString: String) {
        JSONArray(jsonString) // Validate format before saving
        context.getSharedPreferences("VTOP_Reminders", Context.MODE_PRIVATE).edit {
            putString("data", jsonString)
        }
    }
}

// ---------------------------------------------------------------------------
// APP MAIN BRIDGE
// ---------------------------------------------------------------------------
object VtopAppBridge {
    var timetableState = mutableStateOf<TimetableModel?>(null)
    var attendanceState = mutableStateOf<List<AttendanceModel>>(emptyList())
    var examsState = mutableStateOf<List<ExamScheduleModel>>(emptyList())
    var outingsState = mutableStateOf<List<OutingModel>>(emptyList())

    var semestersState = mutableStateOf<List<SemesterOption>>(emptyList())
    var marksState = mutableStateOf<List<CourseMark>>(emptyList())
    var gradesState = mutableStateOf<List<CourseGrade>>(emptyList())
    var historySummaryState = mutableStateOf<CGPASummary?>(null)
    var historyItemsState = mutableStateOf<List<GradeHistoryItem>>(emptyList())

    var appError = mutableStateOf<String?>(null)
    var onMarksDataRequest: ((String, Boolean) -> Unit)? = null
    var syncStatus = mutableStateOf("IDLE") // States: "IDLE", "LOGGING_IN", "SYNCING"
    var currentOtpResolver = mutableStateOf<com.vtop.network.VtopClient.OtpResolver?>(null)

    @JvmStatic fun showError(message: String) { appError.value = message }

    @SuppressLint("NewApi")
    @JvmStatic
    fun launch(
        container: android.view.ViewGroup,
        timetable: TimetableModel?, attendanceList: List<AttendanceModel>?, examList: List<ExamScheduleModel>?, outingList: List<OutingModel>?,
        semesters: List<SemesterOption>?, marks: List<CourseMark>?, grades: List<CourseGrade>?, cgpa: CGPASummary?, historyItems: List<GradeHistoryItem>?,
        onSyncClick: Runnable, onLogoutClick: Runnable, outingHandler: OutingActionHandler, onMarksRequest: (String, Boolean) -> Unit
    ) {
        timetableState.value = timetable; attendanceState.value = attendanceList ?: emptyList(); examsState.value = examList ?: emptyList()
        outingsState.value = outingList ?: emptyList(); semestersState.value = semesters ?: emptyList(); marksState.value = marks ?: emptyList()
        gradesState.value = grades ?: emptyList(); historySummaryState.value = cgpa; historyItemsState.value = historyItems ?: emptyList()
        onMarksDataRequest = onMarksRequest

        container.removeAllViews()
        val composeView = ComposeView(container.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        if (timetableState.value != null) {
                            VtopMainScreen(timetableState.value!!, attendanceState.value, examsState.value, onSyncClick, onLogoutClick, outingHandler)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No Data Found.\nPlease Sync to Continue.", color = Color.Gray, textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
        }
        container.addView(composeView)
    }

    @JvmStatic fun updateMarksOnly(newMarks: List<CourseMark>) { marksState.value = newMarks }
    @JvmStatic fun updateGradesOnly(newGrades: List<CourseGrade>) { gradesState.value = newGrades }
}

// ---------------------------------------------------------------------------
// LOGIN BRIDGE & UI
// ---------------------------------------------------------------------------
object VtopLoginBridge {
    var currentState = mutableStateOf(AuthState.FORM)
    var fetchedSemesters = mutableStateOf<List<Map<String, String>>>(emptyList())
    var loginError = mutableStateOf<String?>(null)
    var cachedRegNo = ""; var cachedPassword = ""

    @JvmStatic
    fun launchAuth(container: android.view.ViewGroup, savedReg: String?, savedPass: String?, callback: AuthActionCallback) {
        container.removeAllViews()
        val composeView = ComposeView(container.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        when (currentState.value) {
                            AuthState.FORM -> VtopLoginForm(savedReg, savedPass, callback)
                            AuthState.LOADING_SEMESTERS -> VtopLoadingState("Connecting...")
                            AuthState.SELECT_SEMESTER -> VtopSemesterMenu(fetchedSemesters.value, callback)
                            AuthState.DOWNLOADING_DATA -> VtopLoadingState("Syncing Data...")
                            AuthState.OTP -> VtopOtpForm(
                                onVerify = { code ->
                                    // We will hook this up to the client later!
                                    android.widget.Toast.makeText(container.context, "OTP Entered: $code", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onCancel = { currentState.value = AuthState.FORM }
                            )
                        }
                    }
                }
            }
        }
        container.addView(composeView)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VtopLoginForm(savedReg: String?, savedPass: String?, callback: AuthActionCallback) {
    var regNo by remember { mutableStateOf(savedReg ?: "") }
    var password by remember { mutableStateOf(savedPass ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    val errorMsg = VtopLoginBridge.loginError.value
    LaunchedEffect(errorMsg) { if (errorMsg != null) { delay(5000); VtopLoginBridge.loginError.value = null } }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxWidth(0.85f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "VTOP", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text(text = "STUDENT PORTAL", color = ThemeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(40.dp))
            val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = ThemeAccent, unfocusedBorderColor = Color.Gray, cursorColor = ThemeAccent)
            OutlinedTextField(value = regNo, onValueChange = { regNo = it }, label = { Text("Registration Number", color = Color.Gray) }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = colors)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password", color = Color.Gray) }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { Text(if (passwordVisible) "HIDE" else "SHOW", color = ThemeAccent, modifier = Modifier.clickable { passwordVisible = !passwordVisible }.padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold) }, modifier = Modifier.fillMaxWidth(), colors = colors)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { VtopLoginBridge.cachedRegNo = regNo; VtopLoginBridge.cachedPassword = password; callback.onLoginSubmit(regNo, password) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = ThemeAccent)) { Text("LOGIN", color = Color.Black, fontWeight = FontWeight.Bold) }
        }
        AnimatedVisibility(visible = errorMsg != null, enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp)) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.padding(16.dp).fillMaxWidth(0.9f).clickable { VtopLoginBridge.loginError.value = null }) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.White); Spacer(modifier = Modifier.width(12.dp)); Text(text = errorMsg ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
        }
    }
}

@Composable
fun VtopOtpForm(onVerify: (String) -> Unit, onCancel: () -> Unit) {
    var otpCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)), // Dim the background slightly
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)), // Matches CardGrey
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = ThemeAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Verification",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "Enter the 6-digit code sent to your VIT email",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(32.dp))

                // Custom 6-Box OTP Input
                androidx.compose.foundation.text.BasicTextField(
                    value = otpCode,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) otpCode = it },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    decorationBox = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(6) { index ->
                                val char = when {
                                    index >= otpCode.length -> ""
                                    else -> otpCode[index].toString()
                                }
                                val isFocused = otpCode.length == index || (otpCode.length == 6 && index == 5)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.8f)
                                        .background(
                                            if (isFocused) Color.White.copy(alpha = 0.1f) else Color(0xFF1E1E1E),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isFocused) ThemeAccent else Color.White.copy(alpha = 0.05f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { onVerify(otpCode) },
                    enabled = otpCode.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThemeAccent,
                        disabledContainerColor = Color(0xFF2C2C2C)
                    )
                ) {
                    Text(
                        "VERIFY SESSION",
                        color = if (otpCode.length == 6) Color.Black else Color.Gray,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onCancel) {
                    Text("Cancel", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}


fun showOtpOverlay(root: FrameLayout, activity: AppCompatActivity, resolver: VtopClient.OtpResolver) {
    Log.d("VTOP_OTP", "showOtpOverlay executing, adding ComposeView")
    val composeView = ComposeView(activity).apply { tag = "otp_overlay" }
    composeView.setContent {
        VtopOtpForm(
            onVerify = { otp ->
                Log.d("VTOP_OTP", "OTP submitted: $otp")
                resolver.submit(otp)
                root.removeView(composeView)
            },
            onCancel = {
                Log.d("VTOP_OTP", "OTP cancelled")
                resolver.cancel()
                root.removeView(composeView)
            }
        )
    }
    root.addView(composeView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    ))
}

@Composable
fun VtopLoadingState(message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = ThemeAccent); Spacer(Modifier.height(16.dp)); Text(text = message, color = Color.White) }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun VtopSemesterMenu(semesters: List<Map<String, String>>, callback: AuthActionCallback) {
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = { isRefreshing = true; VtopLoginBridge.currentState.value = AuthState.LOADING_SEMESTERS; callback.onLoginSubmit(VtopLoginBridge.cachedRegNo, VtopLoginBridge.cachedPassword) })
    LaunchedEffect(semesters) { isRefreshing = false }
    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { VtopLoginBridge.currentState.value = AuthState.FORM }) { Text("← Back to Login", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                IconButton(onClick = { isRefreshing = true; VtopLoginBridge.currentState.value = AuthState.LOADING_SEMESTERS; callback.onLoginSubmit(VtopLoginBridge.cachedRegNo, VtopLoginBridge.cachedPassword) }, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) { Icon(Icons.Default.Refresh, contentDescription = "Refresh Semesters", tint = Color.White) }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.fillMaxWidth(0.9f), colors = CardDefaults.cardColors(containerColor = CardGrey)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Select Semester", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Pull down or tap sync to refresh if your current semester isn't listed.", color = Color.Gray, fontSize = 12.sp); Spacer(Modifier.height(16.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            if (semesters.isEmpty()) item { Text("No semesters found. Please refresh.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                            items(semesters) { sem -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { callback.onSemesterSelect(sem["id"] ?: "", sem["name"] ?: "") }.background(GlassBg).padding(16.dp)) { Text(text = sem["name"] ?: "", color = Color.White) } }
                        }
                    }
                }
            }
        }
        PullRefreshIndicator(refreshing = isRefreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter), backgroundColor = CardGrey, contentColor = ThemeAccent)
    }
}

// ---------------------------------------------------------------------------
// MAIN SCREEN ROUTING

@Composable
fun VtopBottomNavigation(
    currentScreen: String,
    onSelect: (String) -> Unit
) {
    val items = listOf(
        Pair("HOME", Icons.Default.Home),
        Pair("ATTENDANCE", Icons.Default.CheckCircle),
        Pair("EXAMS", Icons.Default.Event),
        Pair("MARKS", Icons.Default.Assessment),
        Pair("OUTINGS", Icons.Default.DirectionsRun),
        Pair("PROFILE", Icons.Default.Person)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        color = Color(0xFF141414).copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentScreen.equals(item.first, true)
                val tint by animateColorAsState(if (isSelected) Color.White else Color.Gray)
                val scale by animateFloatAsState(if (isSelected) 1.2f else 1f)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(item.first) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.second,
                        contentDescription = item.first,
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isSelected) item.first else "",
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("NewApi")
@Composable
fun VtopMainScreen(timetable: TimetableModel, attendanceData: List<AttendanceModel>, examsData: List<ExamScheduleModel>, onSyncClick: Runnable, onLogoutClick: Runnable, outingHandler: OutingActionHandler) {
    val context = LocalContext.current

    // Fetch the user's preferred navigation style from Vault
    var navStyle by remember { mutableStateOf(com.vtop.utils.Vault.getNavStyle(context)) }

    var currentScreen by remember { mutableStateOf("HOME") }
    val navItems = listOf("HOME", "ATTENDANCE", "EXAMS", "MARKS", "OUTINGS", "PROFILE")

    BackHandler(enabled = currentScreen != "HOME") {
        if (currentScreen == "ANALYTICS") currentScreen = "PROFILE"
        else currentScreen = "HOME"
    }

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = { isRefreshing = true; coroutineScope.launch { onSyncClick.run(); delay(1500); isRefreshing = false } })

    val errorMsg = VtopAppBridge.appError.value
    LaunchedEffect(errorMsg) { if (errorMsg != null) { Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show(); delay(5000); VtopAppBridge.appError.value = null } }

    // Scaffold manages the space for the static bottom bar so it doesn't cover your lists
    Scaffold(
        bottomBar = {
            if (navStyle == "STATIC") {
                VtopBottomNavigation(currentScreen) { currentScreen = it }
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        // paddingValues ensures content is pushed up above the static bar
        Box(modifier = Modifier.fillMaxSize().padding(bottom = paddingValues.calculateBottomPadding()).background(Color.Black).pullRefresh(pullRefreshState))  {
            Column(modifier = Modifier.fillMaxSize()) {
                if (currentScreen != "SIMULATOR") VtopGlobalTopBar(currentScreen = currentScreen, onSyncClick = { onSyncClick.run() })
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        currentScreen.equals("HOME", true) -> { if (timetable.scheduleMap.isNotEmpty()) VtopTimetableContent(timetable, attendanceData) else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No Timetable Data Found", color = Color.White) } }
                        currentScreen.equals("ATTENDANCE", true) -> { if (attendanceData.isNotEmpty()) VtopAttendanceDashboard(attendanceData) { currentScreen = "SIMULATOR" } else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No Attendance Data Found", color = Color.White) } }
                        currentScreen.equals("SIMULATOR", true) -> { BunkSimulatorTab(timetable, attendanceData) { currentScreen = "ATTENDANCE" } }
                        currentScreen.equals("EXAMS", true) -> { if (examsData.isNotEmpty()) VtopExamsTab(examsData) else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No Exam Schedule Found", color = Color.White) } }
                        currentScreen.equals("MARKS", true) -> { VtopMarksTab(VtopAppBridge.semestersState.value, VtopAppBridge.marksState.value, VtopAppBridge.gradesState.value, VtopAppBridge.historySummaryState.value, VtopAppBridge.historyItemsState.value, { semId -> VtopAppBridge.onMarksDataRequest?.invoke(semId, false) }, { semId -> VtopAppBridge.onMarksDataRequest?.invoke(semId, true) }) }
                        currentScreen.equals("OUTINGS", true) -> { VtopOutingsTab(outingHandler) }
                        currentScreen.equals("PROFILE", true) -> {
                            VtopProfileTab(
                                onTriggerSync = { onSyncClick.run() },
                                onLogout = { onLogoutClick.run() },
                                onViewAnalytics = { currentScreen = "ANALYTICS" }
                            )
                        }
                        currentScreen.equals("ANALYTICS", true) -> {
                            VtopAnalyticsTab(
                                attendanceData = attendanceData,
                                historySummary = VtopAppBridge.historySummaryState.value,
                                historyData = VtopAppBridge.historyItemsState.value
                            )
                        }
                        else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("$currentScreen Coming Soon", color = Color.Gray) }
                    }
                }
            }

            AnimatedVisibility(visible = errorMsg != null, enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.padding(16.dp).fillMaxWidth(0.9f).clickable { VtopAppBridge.appError.value = null }) { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.White); Spacer(modifier = Modifier.width(12.dp)); Text(text = errorMsg ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
            }

            PullRefreshIndicator(refreshing = isRefreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter), backgroundColor = CardGrey, contentColor = ThemeAccent)

            // Conditionally render the floating dock if STATIC is not selected
            if (navStyle != "STATIC") {
                Box(modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }.align(Alignment.BottomCenter).padding(bottom = 60.dp).pointerInput(Unit) { detectDragGestures { change, dragAmount -> change.consume(); offsetX += dragAmount.x; offsetY += dragAmount.y; val hLimit = (screenWidthPx / 2f) - with(density) { 30.dp.toPx() }; val vTopLimit = -(screenHeightPx - with(density) { 140.dp.toPx() }); val vBottomLimit = with(density) { 20.dp.toPx() }; offsetX = offsetX.coerceIn(-hLimit, hLimit); offsetY = offsetY.coerceIn(vTopLimit, vBottomLimit) } }) {
                    FloatingDockContainer(currentScreen, navItems, offsetX, offsetY, screenWidthPx, screenHeightPx, onSyncClick) { currentScreen = it }
                }
            }
        }
    }
    // --- OTP OVERLAY LOGIC ---
    val otpResolver = VtopAppBridge.currentOtpResolver.value
    if (otpResolver != null) {
        // This Box blocks all touches to the background UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            contentAlignment = Alignment.Center
        ) {
            VtopOtpForm(
                onVerify = { otp ->
                    otpResolver.submit(otp)
                    VtopAppBridge.currentOtpResolver.value = null // Hide UI
                },
                onCancel = {
                    otpResolver.cancel()
                    VtopAppBridge.currentOtpResolver.value = null // Hide UI
                }
            )
        }
    }
} // <-- This is the closing brace of the outermost Box in VtopMainScreen



// TIMETABLE CONTENT (API 24 SAFE + SCROLLING + REMINDERS)


@Composable
fun getReminderColor(type: String): Color {
    return when (type.uppercase(Locale.ROOT)) {
        "QUIZ" -> Color(0xDC3F15A2)       // Purple
        "ASSIGNMENT" -> Color(0xFF03A9F4) // Blue
        "VIVA" -> Color(0xFFB3FF00)       // Orange
        "RECORD" -> Color(0xFF4CAFA3)     // Green
        "OTHERS" -> Color(0xFF24CDE8)     // Red
        else -> Color.White
    }
}

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VtopTimetableContent(timetable: TimetableModel, attendanceData: List<AttendanceModel>) {
    val context = LocalContext.current
    var selectedCourse by remember { mutableStateOf<CourseSession?>(null) }
    var selectedCourseDateStr by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val sdfDateKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val sdfDayFull = remember { SimpleDateFormat("EEEE", Locale.ENGLISH) }
    val sdfDayShort = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }

    val todayCal = remember { Calendar.getInstance() }
    val todayDateStr = remember { sdfDateKey.format(todayCal.time) }

    val timelineDates = remember {
        (-30..30).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offset)
            cal
        }
    }

    var remindersTrigger by remember { mutableIntStateOf(0) }
    val allReminders = remember(remindersTrigger) { ReminderManager.loadReminders(context) }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 30) // 30 is Today
    var expandedDateStr by remember { mutableStateOf<String?>(todayDateStr) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(timelineDates) { dateCal ->
            val currentDateKey = sdfDateKey.format(dateCal.time)
            val dayStr = sdfDayFull.format(dateCal.time)
            val dayCourses = timetable.scheduleMap[dayStr] ?: emptyList()
            val isExpanded = currentDateKey == expandedDateStr

            if (dayCourses.isEmpty()) {
                Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp)).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(text = toLabel(dateCal, todayCal), color = Color.White.copy(alpha = 0.25f), fontSize = 14.sp)
                        Text(text = "DAY OFF", color = Color.White.copy(alpha = 0.1f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expandedDateStr = null }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(text = "${dateCal.get(Calendar.DAY_OF_MONTH)}/${dateCal.get(Calendar.MONTH) + 1}\n${sdfDayShort.format(dateCal.time)}", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(text = "${dayCourses.size} Classes", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
                        val merged = VtopUiUtils.mergeLabSessions(dayCourses)
                        merged.forEach { c ->
                            val courseReminder = allReminders.find { it.classId == c.classId && it.date >= currentDateKey }
                            VtopCourseTile(modifier = Modifier.weight(1f), course = c, courseDate = dateCal, reminder = courseReminder) {
                                selectedCourse = it
                                selectedCourseDateStr = currentDateKey
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).clickable { expandedDateStr = currentDateKey }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = toLabel(dateCal, todayCal), color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            val merged = VtopUiUtils.mergeLabSessions(dayCourses)
                            merged.forEach { c ->
                                val courseReminder = allReminders.find { it.classId == c.classId && it.date >= currentDateKey }
                                val indicatorColor = if (courseReminder != null) getReminderColor(courseReminder.type) else Color.White.copy(alpha = 0.2f)
                                Box(modifier = Modifier.height(4.dp).width(16.dp).background(indicatorColor, RoundedCornerShape(50)))
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedCourse != null && selectedCourseDateStr != null) {
        val matchedAttendance = attendanceData.find { it.courseCode == selectedCourse?.courseCode }
        val activeCourseReminders = allReminders.filter { it.classId == selectedCourse?.classId && it.date >= todayDateStr }.sortedBy { it.date }

        var showReminderForm by remember { mutableStateOf(false) }
        var editingReminderId by remember { mutableStateOf<String?>(null) }
        var showDetailedAttendance by remember { mutableStateOf(false) }

        ModalBottomSheet(onDismissRequest = { selectedCourse = null; selectedCourseDateStr = null; editingReminderId = null }, containerColor = Color(0xFF141414), sheetState = sheetState) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.padding(horizontal = 24.dp).navigationBarsPadding().verticalScroll(scrollState).padding(bottom = 32.dp)) {

                if (activeCourseReminders.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Upcoming Reminders", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        activeCourseReminders.forEach { r ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${r.type} - ${r.date}", color = getReminderColor(r.type), fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    if (r.syllabus.isNotEmpty()) {
                                        Text(r.syllabus, color = Color.Gray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                TextButton(onClick = { editingReminderId = r.id; showReminderForm = true }) { Text("Edit", color = Color.White, fontSize = 12.sp) }
                            }
                        }
                    }
                }

                Text(text = selectedCourse?.courseCode ?: "N/A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = selectedCourse?.courseName ?: "Course", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.15f)); Spacer(Modifier.height(16.dp))
                VtopDetailItem("Slot", selectedCourse?.slot); VtopDetailItem("Timing", selectedCourse?.timeSlot); VtopDetailItem("Professor", selectedCourse?.faculty); VtopDetailItem("Venue", selectedCourse?.venue); VtopDetailItem("Class ID", selectedCourse?.classId)
                Spacer(Modifier.height(20.dp))

                AnimatedVisibility(visible = !showReminderForm) {
                    Button(onClick = { editingReminderId = null; showReminderForm = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C)), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add Reminder", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                AnimatedVisibility(visible = showReminderForm) {
                    val existing = allReminders.find { it.id == editingReminderId }
                    ReminderForm(
                        course = selectedCourse!!,
                        timetable = timetable,
                        existingReminder = existing,
                        onSave = { type, dateString, syllabusText ->
                            val newList = allReminders.toMutableList()
                            if (editingReminderId != null) { newList.removeAll { it.id == editingReminderId } }
                            newList.add(CourseReminder(courseCode = selectedCourse!!.courseCode ?: "", classId = selectedCourse!!.classId ?: "", type = type, date = dateString, syllabus = syllabusText))
                            ReminderManager.saveReminders(context, newList)
                            remindersTrigger++
                            showReminderForm = false
                            Toast.makeText(context, "Reminder Saved", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = {
                            val newList = allReminders.toMutableList()
                            newList.removeAll { it.id == editingReminderId }
                            ReminderManager.saveReminders(context, newList)
                            remindersTrigger++
                            showReminderForm = false
                            Toast.makeText(context, "Reminder Deleted", Toast.LENGTH_SHORT).show()
                        },
                        onCancel = { showReminderForm = false }
                    )
                }
                Spacer(Modifier.height(20.dp))

                Surface(color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth().animateContentSize().clickable { showDetailedAttendance = !showDetailedAttendance }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("Attendance", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(text = if (showDetailedAttendance) "Tap to hide details" else "Tap to view details", color = Color.Gray, fontSize = 11.sp) }; val pString = matchedAttendance?.attendancePercentage ?: "0"; val p = pString.filter { it.isDigit() }.toIntOrNull() ?: 0; Text("${p}%", color = if (p >= 75) Color.Green else Color.Red, fontSize = 20.sp, fontWeight = FontWeight.Black) }
                        if (showDetailedAttendance) { Column { Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.08f)); Spacer(Modifier.height(16.dp)); if (matchedAttendance != null) VtopDetailedAttendanceTable(matchedAttendance) else Text("No records found.", color = Color.Gray, fontSize = 12.sp) } }
                    }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
fun ReminderForm(
    course: CourseSession,
    timetable: TimetableModel,
    existingReminder: CourseReminder?,
    onSave: (String, String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf(existingReminder?.type ?: "Quiz") }

    val cal = remember { Calendar.getInstance() }
    val sdfOutput = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    var selectedDateStr by remember { mutableStateOf(existingReminder?.date ?: sdfOutput.format(cal.time)) }
    var syllabusText by remember { mutableStateOf(existingReminder?.syllabus ?: "") }
    var errorText by remember { mutableStateOf("") }

    LaunchedEffect(existingReminder) {
        if (existingReminder != null) {
            try {
                val d = sdfOutput.parse(existingReminder.date)
                if (d != null) cal.time = d
            } catch (e: Exception) {}
        }
    }

    val types = mutableListOf("Quiz", "Assignment", "Others")
    if (course.courseType?.contains("LO", true) == true || course.courseType?.contains("Lab", true) == true || course.slot?.contains("L") == true) { types.add("Viva"); types.add("Record") }

    val showDatePicker = {
        val dialog = DatePickerDialog(context, { _, y, m, d ->
            cal.set(y, m, d)
            selectedDateStr = sdfOutput.format(cal.time)
            errorText = ""
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000
        dialog.show()
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), border = BorderStroke(1.dp, GlassBorder), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(if (existingReminder != null) "Edit Reminder" else "Set Reminder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { type -> val isSelected = type == selectedType; Box(modifier = Modifier.background(if (isSelected) ThemeAccent else Color.Transparent, RoundedCornerShape(16.dp)).border(1.dp, if (isSelected) ThemeAccent else Color.Gray, RoundedCornerShape(16.dp)).clickable { selectedType = type }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text(type, color = if (isSelected) Color.Black else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
            }
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker() }) {
                OutlinedTextField(value = selectedDateStr, onValueChange = {}, readOnly = true, label = { Text("Reminder Date", color = Color.Gray) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = ThemeAccent, unfocusedBorderColor = Color.DarkGray, disabledTextColor = Color.White, disabledBorderColor = Color.DarkGray, disabledLabelColor = Color.Gray), modifier = Modifier.fillMaxWidth(), enabled = false)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = syllabusText,
                onValueChange = { syllabusText = it },
                label = { Text("Syllabus / Notes", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = ThemeAccent, unfocusedBorderColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (errorText.isNotEmpty()) { Text(errorText, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existingReminder != null && onDelete != null) {
                    Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.weight(1f)) { Text("Delete", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", color = Color.Gray) }
                }

                Button(onClick = {
                    val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
                    val classesThatDay = timetable.scheduleMap[dayName] ?: emptyList()
                    val isValid = classesThatDay.any { it.classId == course.classId || it.venue == course.venue }
                    if (isValid) onSave(selectedType, selectedDateStr, syllabusText) else errorText = "You don't have this class on $dayName!"
                }, colors = ButtonDefaults.buttonColors(containerColor = ThemeAccent), modifier = Modifier.weight(1f)) { Text("Save", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun VtopCourseTile(modifier: Modifier = Modifier, course: CourseSession, courseDate: Calendar, reminder: CourseReminder?, onClick: (CourseSession) -> Unit) {
    val status = VtopUiUtils.getCourseStatus(course.timeSlot, courseDate)
    val backgroundColor = when (status) { "NOW" -> Color.White.copy(alpha = 0.9f); "PAST" -> Color.White.copy(alpha = 0.03f); else -> Color.White.copy(alpha = 0.08f) }
    val textColor = if (status == "NOW") Color.Black else Color.White

    Card(modifier = modifier.aspectRatio(0.6f).clickable { onClick(course) }, colors = CardDefaults.cardColors(containerColor = backgroundColor), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (status == "NOW") Color.White else Color.White.copy(alpha = 0.15f))) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 10.dp, horizontal = 4.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = course.courseCode ?: "N/A", color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Text(text = course.slot ?: "N/A", color = textColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                Text(text = course.timeSlot?.split("-")?.firstOrNull() ?: "--", color = textColor.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }

            if (reminder != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .height(3.dp)
                        .width(20.dp)
                        .background(getReminderColor(reminder.type), RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
fun VtopDetailItem(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween) { Text(text = label, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp); Text(text = value ?: "N/A", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f)) }
}

private fun toLabel(date: Calendar, today: Calendar): String {
    val dYear = date.get(Calendar.YEAR)
    val dDay = date.get(Calendar.DAY_OF_YEAR)
    val tYear = today.get(Calendar.YEAR)
    val tDay = today.get(Calendar.DAY_OF_YEAR)
    return when (dYear) {
        tYear -> when (dDay) {
            tDay -> "Today"
            tDay - 1 -> "Yesterday"
            tDay + 1 -> "Tomorrow"
            else -> "${SimpleDateFormat("EEEE", Locale.ENGLISH).format(date.time)} (${date.get(Calendar.DAY_OF_MONTH)}/${date.get(Calendar.MONTH) + 1})"
        }
        else -> "${SimpleDateFormat("EEEE", Locale.ENGLISH).format(date.time)} (${date.get(Calendar.DAY_OF_MONTH)}/${date.get(Calendar.MONTH) + 1})"
    }
}

object VtopUiUtils {
    fun mergeLabSessions(courses: List<CourseSession>): List<CourseSession> {
        if (courses.size < 2) return courses
        val merged = mutableListOf<CourseSession>()
        var cur = courses[0]
        for (i in 1 until courses.size) { val nxt = courses[i]; if (cur.courseCode == nxt.courseCode && (cur.slot?.contains("L") == true)) { cur = CourseSession(cur.courseName, cur.courseCode, cur.courseType, "${cur.slot}+${nxt.slot?.filter { it.isDigit() }}", cur.venue, cur.faculty, cur.timeSlot, cur.gridDisplay, cur.classId) } else { merged.add(cur); cur = nxt } }
        merged.add(cur)
        return merged
    }

    fun getCourseStatus(timeSlot: String?, dateCal: Calendar): String {
        return try {
            val now = Calendar.getInstance()
            val start = timeSlot?.split("-")?.firstOrNull()?.trim() ?: "08:00"
            var hour = start.filter { it.isDigit() }.toIntOrNull() ?: 8
            if (start.contains("PM", true) && hour < 12) hour += 12

            val classTime = dateCal.clone() as Calendar
            classTime.set(Calendar.HOUR_OF_DAY, hour)
            classTime.set(Calendar.MINUTE, 0)
            classTime.set(Calendar.SECOND, 0)

            val classEndTime = classTime.clone() as Calendar
            classEndTime.add(Calendar.MINUTE, 50)

            if (now.before(classTime)) "FUTURE" else if (now.after(classEndTime)) "PAST" else "NOW"
        } catch (_: Exception) { "FUTURE" }
    }
}

// ---------------------------------------------------------------------------
// PROFILE UI (SAF EXPORT/IMPORT & DEV TOOLS)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VtopProfileTab(currentVersion: String = "0.5.0", githubApiUrl: String = "https://api.github.com/repos/YOUR_ORG/YOUR_REPO/releases/latest", availableSemesters: List<Map<String, String>> = emptyList(), onTriggerSync: () -> Unit = {}, onViewAnalytics: () -> Unit = {}, onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val savedCredentials = remember(refreshTrigger) { Vault.getCredentials(context) }
    var regNo by remember(savedCredentials[0]) { mutableStateOf(savedCredentials[0] ?: "") }
    var password by remember(savedCredentials[1]) { mutableStateOf(savedCredentials[1] ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    val savedSem = remember(refreshTrigger) { Vault.getSelectedSemester(context) }
    var semesterId by remember(savedSem[0]) { mutableStateOf(savedSem[0] ?: "") }
    val activeSemName = savedSem[1] ?: "Not Set"

    val cachedSemesters = remember { val list = mutableListOf<Map<String, String>>(); try { val jsonStr = prefs.getString("SEMESTERS_CACHE", "[]") ?: "[]"; val array = JSONArray(jsonStr); for (i in 0 until array.length()) { val obj = array.getJSONObject(i); list.add(mapOf("id" to obj.getString("id"), "name" to obj.getString("name"))) } } catch (_: Exception) { }; list }
    val finalSemesters = availableSemesters.ifEmpty { cachedSemesters }

    var showCredentialsDialog by remember { mutableStateOf(false) }
    var showSemDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var expandedSemDropdown by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf("") }

    var adminTaps by remember { mutableIntStateOf(0) }
    var showDeveloperTools by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(ReminderManager.getExportJsonString(context).toByteArray())
                    Toast.makeText(context, "Reminders Exported Successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val jsonString = stream.bufferedReader().use { reader -> reader.readText() }
                    ReminderManager.importFromJsonString(context, jsonString)
                    Toast.makeText(context, "Reminders Imported Successfully", Toast.LENGTH_SHORT).show()
                    onTriggerSync()
                }
            } catch (e: Exception) { Toast.makeText(context, "Import Failed or Invalid JSON", Toast.LENGTH_LONG).show() }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(modifier = Modifier.fillMaxWidth().height(24.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { adminTaps++; if (adminTaps >= 7) { showDeveloperTools = true; adminTaps = 0; Toast.makeText(context, "Developer Mode Unlocked", Toast.LENGTH_SHORT).show() } })

        ProfileOptionCard(title = "Manage Credentials", icon = Icons.Default.AccountCircle, onClick = { showCredentialsDialog = true })
        ProfileOptionCard(title = "Analytics", icon = Icons.Default.Assessment, onClick = { onViewAnalytics() })
        ProfileOptionCard(title = "Change Semester", subtitle = "Active: $activeSemName", icon = Icons.Default.DateRange, onClick = { showSemDialog = true })
        ProfileOptionCard(title = "Check for Updates", subtitle = "Version $currentVersion", icon = Icons.Default.Refresh, onClick = {
            showUpdateDialog = true; isCheckingUpdate = true; updateMessage = "Checking server for updates..."; updateUrl = ""
            coroutineScope.launch(Dispatchers.IO) {
                try { val connection = URL(githubApiUrl).openConnection() as HttpURLConnection; connection.requestMethod = "GET"; connection.setRequestProperty("Accept", "application/vnd.github.v3+json"); connection.connectTimeout = 5000; if (connection.responseCode == HttpURLConnection.HTTP_OK) { val response = connection.inputStream.bufferedReader().use { it.readText() }; val latestVersion = JSONObject(response).getString("tag_name").removePrefix("v"); val downloadUrl = JSONObject(response).getString("html_url"); withContext(Dispatchers.Main) { if (latestVersion != currentVersion) { updateMessage = "A new version ($latestVersion) is available!"; updateUrl = downloadUrl } else { updateMessage = "You are currently up to date." }; isCheckingUpdate = false } } else { withContext(Dispatchers.Main) { updateMessage = "Server returned code: ${connection.responseCode}"; isCheckingUpdate = false } } } catch (e: Exception) { withContext(Dispatchers.Main) { updateMessage = "Network error: ${e.localizedMessage}"; isCheckingUpdate = false } }
            }
        })

        ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth().clickable { showMoreOptions = !showMoreOptions }) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    //Icon(Icons.Default.MoreVert, "More", tint = Color.White, modifier = Modifier.size(28.dp))
                    //Spacer(Modifier.width(16.dp))
                    Text("More", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Icon(if (showMoreOptions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Expand", tint = Color.Gray)
                }
                AnimatedVisibility(visible = showMoreOptions) {
                    Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        TextButton(onClick = { exportLauncher.launch("vtop_reminders.json") }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Download, "Export", tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Export Reminders (JSON)", color = Color.White) }
                        }
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Upload, "Import", tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Import Reminders (JSON)", color = Color.White) }
                        }
                    }
                }
            }
        }

        if (showDeveloperTools) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Developer Tools", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            ProfileOptionCard(title = "Force Crash (Test Logs)", icon = Icons.Default.Warning, onClick = { throw RuntimeException("Test Crash from Developer Tools") })
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { showLogoutDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp)) { Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("LOGOUT", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
    }

    if (showLogoutDialog) { AlertDialog(onDismissRequest = { showLogoutDialog = false }, containerColor = Color(0xFF1E1E1E), titleContentColor = Color.White, textContentColor = Color.White, title = { Text("Confirm Logout") }, text = { Text("Are you sure you want to logout? This will erase all offline data from your device.", color = Color.LightGray) }, confirmButton = { TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Logout", color = Color(0xFFF44336), fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = Color.Gray) } }) }
    if (showCredentialsDialog) { AlertDialog(onDismissRequest = { showCredentialsDialog = false }, containerColor = Color(0xFF1E1E1E), titleContentColor = Color.White, textContentColor = Color.White, title = { Text("Update Credentials") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = regNo, onValueChange = { regNo = it }, label = { Text("Registration Number") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.Gray, focusedLabelColor = Color.White, unfocusedLabelColor = Color.Gray)); OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { Text(text = if (passwordVisible) "HIDE" else "SHOW", color = Color.Gray, modifier = Modifier.clickable { passwordVisible = !passwordVisible }.padding(8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.Gray, focusedLabelColor = Color.White, unfocusedLabelColor = Color.Gray)) } }, confirmButton = { TextButton(onClick = { Vault.saveCredentials(context, regNo, password); refreshTrigger++; Toast.makeText(context, "Credentials Saved. Syncing...", Toast.LENGTH_SHORT).show(); showCredentialsDialog = false; onTriggerSync() }) { Text("Save & Sync", color = Color.White, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showCredentialsDialog = false }) { Text("Cancel", color = Color.Gray) } }) }
    if (showSemDialog) { AlertDialog(onDismissRequest = { showSemDialog = false }, containerColor = Color(0xFF1E1E1E), titleContentColor = Color.White, textContentColor = Color.White, title = { Text("Change Semester") }, text = { Column { Text("Select your active semester:", color = Color.Gray, fontSize = 14.sp); Spacer(modifier = Modifier.height(8.dp)); Box(modifier = Modifier.fillMaxWidth().clickable { expandedSemDropdown = true }) { OutlinedTextField(value = finalSemesters.find { it["id"] == semesterId }?.get("name") ?: semesterId.ifEmpty { "Select Semester" }, onValueChange = {}, readOnly = true, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = Color.White, disabledBorderColor = Color.Gray), modifier = Modifier.fillMaxWidth()); DropdownMenu(expanded = expandedSemDropdown, onDismissRequest = { expandedSemDropdown = false }, modifier = Modifier.background(Color(0xFF2C2C2C))) { if (finalSemesters.isEmpty()) DropdownMenuItem(text = { Text("No semesters cached", color = Color.Gray) }, onClick = { expandedSemDropdown = false }) else finalSemesters.forEach { sem -> DropdownMenuItem(text = { Text(sem["name"] ?: "Unknown", color = Color.White) }, onClick = { semesterId = sem["id"] ?: ""; expandedSemDropdown = false }) } } } } }, confirmButton = { TextButton(onClick = { val semName = finalSemesters.find { it["id"] == semesterId }?.get("name") ?: "Unknown"; Vault.saveSelectedSemester(context, semesterId, semName); refreshTrigger++; Toast.makeText(context, "Semester Updated. Syncing...", Toast.LENGTH_SHORT).show(); showSemDialog = false; onTriggerSync() }) { Text("Save & Sync", color = Color.White, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showSemDialog = false }) { Text("Cancel", color = Color.Gray) } }) }
    if (showUpdateDialog) { AlertDialog(onDismissRequest = { showUpdateDialog = false }, containerColor = Color(0xFF1E1E1E), titleContentColor = Color.White, textContentColor = Color.White, title = { Text("System Update") }, text = { Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { if (isCheckingUpdate) CircularProgressIndicator(color = Color.White); Text(text = updateMessage, textAlign = TextAlign.Center) } }, confirmButton = { if (updateUrl.isNotEmpty() && !isCheckingUpdate) Button(onClick = { val intent = Intent(Intent.ACTION_VIEW, updateUrl.toUri()); context.startActivity(intent); showUpdateDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.White)) { Text("Download", color = Color.Black) } else if (!isCheckingUpdate) TextButton(onClick = { showUpdateDialog = false }) { Text("OK", color = Color.White) } }, dismissButton = { TextButton(onClick = { showUpdateDialog = false; isCheckingUpdate = false }) { Text(if (isCheckingUpdate) "Cancel" else "Close", color = Color.Gray) } }) }
}

@Composable
fun ProfileOptionCard(title: String, subtitle: String? = null, icon: ImageVector, onClick: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth().clickable { onClick() }) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(28.dp)); Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(text = title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); if (subtitle != null) { Text(text = subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) } } } }
}

// ---------------------------------------------------------------------------
// OTHER TABS (ATTENDANCE, MARKS, GRADES, EXAMS)
// ---------------------------------------------------------------------------

@Composable
fun VtopAttendanceDashboard(attendanceData: List<AttendanceModel>, onLaunchSimulator: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onLaunchSimulator() },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bunk Simulator", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Predict your attendance before skipping classes.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = "Simulate", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            item { Text(text = "Attendance Overview", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(items = attendanceData) { course: AttendanceModel -> VtopAttendanceCard(course) }
        }
    }
}

@Composable
fun VtopAttendanceCard(item: AttendanceModel) {
    var exp by remember { mutableStateOf(false) }
    val pString = item.attendancePercentage ?: "0"
    val p = pString.filter { it.isDigit() }.toIntOrNull() ?: 0
    val statusColor = if (p >= 75) Color.Green else Color.Red
    val cType = item.courseType ?: ""
    val categoryLabel = when {
        cType.contains("TH", ignoreCase = true) || cType.contains("ETH", ignoreCase = true) -> "Theory"
        cType.contains("LO", ignoreCase = true) || cType.contains("ELA", ignoreCase = true) -> "Lab"
        cType.contains("PJT", ignoreCase = true) || cType.contains("EPT", ignoreCase = true) -> "Project"
        else -> cType.ifEmpty { "Theory" }
    }

    ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White.copy(alpha = 0.08f)), modifier = Modifier.fillMaxWidth().clickable { exp = !exp }) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize(animationSpec = tween(300))) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.courseCode ?: "N/A", color = ThemeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = categoryLabel, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(text = "$p%", color = statusColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(text = item.courseName ?: "N/A", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "${item.attendedClasses ?: 0}/${item.totalClasses ?: 0}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            if (exp) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                    Spacer(Modifier.height(8.dp))
                    VtopDetailedAttendanceTable(item)
                }
            }
        }
    }
}

@Composable
fun VtopDetailedAttendanceTable(item: AttendanceModel) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(text = "#", modifier = Modifier.weight(0.1f), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text(text = "DATE", modifier = Modifier.weight(0.35f), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text(text = "TIME/SLOT", modifier = Modifier.weight(0.25f), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
            Text(text = "STATUS", modifier = Modifier.weight(0.3f), color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, textAlign = TextAlign.End)
        }
        item.history?.forEachIndexed { i, h ->
            val statusUpper = h.status?.uppercase(Locale.getDefault()) ?: ""
            val displayColor = when {
                statusUpper.contains("PRESENT") -> Color.Green
                statusUpper.contains("ON DUTY") || statusUpper.contains("DUTY") -> Color(0xFF0A84FF)
                else -> Color.Red
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${i+1}", modifier = Modifier.weight(0.1f), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Text(text = h.date ?: "", modifier = Modifier.weight(0.35f), color = Color.White, fontSize = 11.sp)
                Text(text = "${h.time ?: "--"} / ${h.slot ?: "--"}", modifier = Modifier.weight(0.25f), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                Text(text = h.status ?: "", modifier = Modifier.weight(0.3f), color = displayColor, fontSize = 11.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VtopMarksTab(semesters: List<SemesterOption>, marksData: List<CourseMark>, gradesData: List<CourseGrade>, historySummary: CGPASummary?, historyData: List<GradeHistoryItem>, onMarksSemesterChanged: (String) -> Unit, onGradesSemesterChanged: (String) -> Unit) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Marks", "Grades", "History")

    val context = LocalContext.current
    val globalSemId = remember { Vault.getSelectedSemester(context)[0] }

    var marksExpanded by remember { mutableStateOf(false) }
    var selectedMarksSem by remember(semesters) { mutableStateOf(semesters.find { it.id == globalSemId } ?: semesters.firstOrNull()) }

    var gradesExpanded by remember { mutableStateOf(false) }
    var selectedGradesSem by remember(semesters) { mutableStateOf(semesters.let { semesterList -> val index = semesterList.indexOfFirst { it.id == globalSemId }; if (index != -1 && index + 1 < semesterList.size) semesterList[index + 1] else semesterList.getOrNull(1) ?: semesterList.firstOrNull() }) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp).background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp)).padding(4.dp)) {
            tabs.forEachIndexed { index, title -> val isSelected = selectedTabIndex == index; Box(modifier = Modifier.weight(1f).background(if (isSelected) Color(0xFF333333) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { selectedTabIndex = index }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text(text = title, color = if (isSelected) Color.White else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp) } }
        }

        when (selectedTabIndex) {
            0 -> {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(value = selectedMarksSem?.name ?: "Select Semester", onValueChange = {}, readOnly = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.DarkGray, disabledTextColor = Color.White, disabledBorderColor = Color.DarkGray), modifier = Modifier.fillMaxWidth(), enabled = false)
                    Box(modifier = Modifier.matchParentSize().clickable { marksExpanded = true })
                    DropdownMenu(expanded = marksExpanded, onDismissRequest = { marksExpanded = false }, modifier = Modifier.background(Color(0xFF222222))) {
                        semesters.map { it.name }.forEach { option -> DropdownMenuItem(text = { Text(option, color = Color.White) }, onClick = { val sem = semesters.find { it.name == option }; if(sem != null) { selectedMarksSem = sem; onMarksSemesterChanged(sem.id) }; marksExpanded = false }) }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { if (marksData.isEmpty()) item { Text("No Marks published for this semester yet.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp)) } else items(marksData) { mark -> MarksCard(mark) } }
            }
            1 -> {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    OutlinedTextField(value = selectedGradesSem?.name ?: "Select Semester", onValueChange = {}, readOnly = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color.White, unfocusedBorderColor = Color.DarkGray, disabledTextColor = Color.White, disabledBorderColor = Color.DarkGray), modifier = Modifier.fillMaxWidth(), enabled = false)
                    Box(modifier = Modifier.matchParentSize().clickable { gradesExpanded = true })
                    DropdownMenu(expanded = gradesExpanded, onDismissRequest = { gradesExpanded = false }, modifier = Modifier.background(Color(0xFF222222))) {
                        semesters.map { it.name }.forEach { option -> DropdownMenuItem(text = { Text(option, color = Color.White) }, onClick = { val sem = semesters.find { it.name == option }; if(sem != null) { selectedGradesSem = sem; onGradesSemesterChanged(sem.id) }; gradesExpanded = false }) }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { if (gradesData.isEmpty()) item { Text("No Grades published for this semester yet.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp)) } else items(gradesData) { grade -> GradesCard(grade) } }
            }
            2 -> { LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { historySummary?.let { HistoryHeroCard(it) } }; items(historyData) { history -> HistoryItemCard(history) } } }
        }
    }
}

@Composable
fun MarksCard(courseMark: CourseMark) {
    var expanded by remember { mutableStateOf(false) }
    val scoreFmt = String.format(Locale.US, "%.1f", courseMark.totalWeightageMark)
    val totalFmt = String.format(Locale.US, "%.1f", courseMark.totalWeightagePercent)
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)), modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) { Text(courseMark.courseCode, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(courseMark.courseTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(courseMark.courseType, color = Color(0xFF2196F3).copy(alpha = 0.8f), fontSize = 12.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("Total", color = Color.Gray, fontSize = 12.sp); Text("$scoreFmt / $totalFmt", color = Color(0xFF4CAF50), fontSize = 16.sp, fontWeight = FontWeight.Black) }
            }
            AnimatedVisibility(visible = expanded) { Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(bottom = 12.dp)); courseMark.details.forEach { detail -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(detail.title, color = Color.LightGray, fontSize = 14.sp); Text("${detail.scoredMark} / ${detail.maxMark}", color = ThemeAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium) } } } }
        }
    }
}

@Composable
fun HistoryItemCard(item: GradeHistoryItem) {
    val gradeColor = when (item.grade) { "S" -> Color(0xFF4CAF50); "A" -> Color(0xFF2196F3); "B" -> Color(0xFF9C27B0); "C", "D", "E" -> Color(0xFFFFC107); "F", "N" -> Color(0xFFF44336); else -> Color.Gray }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { Text(item.courseCode, color = Color.Gray, fontSize = 12.sp); Text(item.courseTitle, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) { Text(item.courseType, color = Color(0xFF2196F3).copy(alpha=0.8f), fontSize = 12.sp); Text("•", color = Color.DarkGray, fontSize = 12.sp); Text("${item.credits} Credits", color = Color.LightGray, fontSize = 12.sp); Text("•", color = Color.DarkGray, fontSize = 12.sp); Text(item.examMonth, color = Color(0xFFFFC107).copy(alpha=0.8f), fontSize = 12.sp) } }
            Box(modifier = Modifier.background(gradeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { Text(item.grade, color = gradeColor, fontSize = 18.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun GradesCard(grade: CourseGrade) {
    val gradeColor = when (grade.grade) { "S" -> Color(0xFF4CAF50); "A" -> Color(0xFF2196F3); "B" -> Color(0xFF9C27B0); "C", "D", "E" -> Color(0xFFFFC107); "F", "N" -> Color(0xFFF44336); else -> Color.Gray }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { Text(grade.courseCode, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(grade.courseTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(grade.courseType, color = Color.DarkGray, fontSize = 12.sp) }
            Box(modifier = Modifier.size(40.dp).background(gradeColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(grade.grade, color = gradeColor, fontSize = 20.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
fun HistoryHeroCard(summary: CGPASummary) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)), border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("CGPA", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(summary.cgpa, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Earned", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(summary.creditsEarned, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VtopExamsTab(exams: List<ExamScheduleModel>) {
    var selectedExam by remember { mutableStateOf<ExamScheduleModel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ExamListScreen(exams = exams, onExamClick = { selectedExam = it })
    if (selectedExam != null) { ModalBottomSheet(onDismissRequest = { selectedExam = null }, sheetState = sheetState, containerColor = Color(0xFF141414), dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }) { ExamBottomSheetContent(exam = selectedExam!!) } }
}

@Composable
fun ExamListScreen(exams: List<ExamScheduleModel>, onExamClick: (ExamScheduleModel) -> Unit) {
    val sdf = remember { SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH) }
    val now = remember { Date() }
    val nextExam = remember(exams) { exams.mapNotNull { exam -> try { val timeParts = exam.examTime.split("-"); val endTimeStr = if (timeParts.size > 1) timeParts[1].trim() else exam.examTime.trim(); val dateTimeStr = "${exam.examDate} $endTimeStr"; val date = sdf.parse(dateTimeStr); if (date != null && date.after(now)) Pair(exam, date) else null } catch (_: Exception) { null } }.minByOrNull { it.second }?.first }
    val groupedExams = exams.groupBy { it.examType }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            //item { Text("Exam Schedule", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 8.dp)) }
            if (nextExam != null) { item { Text("NEXT UP", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)); DetailedExamCard(exam = nextExam, containerColor = Color(0xFF1E1E1E), borderColor = Color.White.copy(alpha = 0.3f), onClick = { onExamClick(nextExam) }); Spacer(modifier = Modifier.height(24.dp)); HorizontalDivider(color = Color.White.copy(alpha = 0.05f)); Spacer(modifier = Modifier.height(8.dp)) } }
            groupedExams.forEach { (examType, examList) -> item { Text(examType, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }; items(items = examList) { exam: ExamScheduleModel -> DetailedExamCard(exam = exam, containerColor = Color(0xFF141414), borderColor = Color.White.copy(alpha = 0.05f), onClick = { onExamClick(exam) }) } }
        }
    }
}

@Composable
fun DetailedExamCard(exam: ExamScheduleModel, containerColor: Color, borderColor: Color, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = containerColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(exam.courseCode, color = Color(0xFFFFC107), fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(exam.examDate, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(modifier = Modifier.height(4.dp))
            Text(exam.courseTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Time", color = Color.Gray, fontSize = 11.sp); Text(exam.examTime, color = Color.White, fontSize = 13.sp) }; Column(horizontalAlignment = Alignment.End) { Text("Venue", color = Color.Gray, fontSize = 11.sp); Text(exam.venue, color = Color.White, fontSize = 13.sp) } }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Seat Location", color = Color.Gray, fontSize = 11.sp); Text(exam.seatLocation, color = Color.White, fontSize = 13.sp) }; Column(horizontalAlignment = Alignment.End) { Text("Seat No", color = Color.Gray, fontSize = 11.sp); Text(exam.seatNumber, color = Color.White, fontSize = 13.sp) } }
        }
    }
}

@Composable
fun ExamBottomSheetContent(exam: ExamScheduleModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("General (Semester)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(exam.courseTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp)); val details = listOf("Course Code" to exam.courseCode, "Course Type" to exam.courseType, "Class ID" to exam.classId, "Slot" to exam.slot, "Session" to exam.examSession, "Reporting Time" to exam.reportingTime, "Exam Date" to exam.examDate, "Exam Time" to exam.examTime, "Venue" to exam.venue); details.forEach { (label, value) -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color.Gray, fontSize = 14.sp); Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End) } }; Spacer(modifier = Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Seat Location", color = Color.White, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(4.dp)); Text("Seat No: ${exam.seatNumber}", color = Color.Gray, fontSize = 12.sp) }; Text(exam.seatLocation, color = Color(0xFF4CAF50), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) } }
    }
}

@Composable
fun FloatingDockContainer(currentScreen: String, items: List<String>, offsetX: Float, offsetY: Float, screenWidthPx: Float, screenHeightPx: Float, onSyncClick: Runnable, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val position = when { offsetX < -(screenWidthPx * 0.35f) -> DockPosition.LEFT; offsetX > (screenWidthPx * 0.35f) -> DockPosition.RIGHT; offsetY < -(screenHeightPx * 0.7f) -> DockPosition.TOP; else -> DockPosition.BOTTOM }
    val transformOrigin = when (position) { DockPosition.LEFT -> TransformOrigin(0f, 0.5f); DockPosition.RIGHT -> TransformOrigin(1f, 0.5f); DockPosition.TOP -> TransformOrigin(0.5f, 0f); else -> TransformOrigin(0.5f, 1f) }

    Layout(
        content = {
            val rotation = when (position) { DockPosition.LEFT -> -90f; DockPosition.RIGHT -> 90f; else -> 0f }
            val isVertical = position == DockPosition.LEFT || position == DockPosition.RIGHT
            Box(modifier = Modifier.size(width = if (isVertical) 44.dp else 140.dp, height = if (isVertical) 140.dp else 44.dp), contentAlignment = Alignment.Center) { Card(modifier = Modifier.requiredSize(width = 140.dp, height = 44.dp).graphicsLayer { rotationZ = rotation }.clickable { expanded = !expanded }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CardGrey), border = BorderStroke(1.dp, GlassBorder)) { Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Text(text = currentScreen.uppercase(Locale.getDefault()), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp); Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp).padding(start = 4.dp)) } } }
            Box { AnimatedVisibility(visible = expanded, enter = fadeIn() + scaleIn(transformOrigin = transformOrigin, animationSpec = tween(200)), exit = fadeOut() + scaleOut(transformOrigin = transformOrigin, animationSpec = tween(150))) { Card(modifier = Modifier.width(200.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CardGrey.copy(0.98f)), border = BorderStroke(1.dp, GlassBorder)) { Column(modifier = Modifier.padding(8.dp)) { items.forEach { item -> val isSelected = currentScreen.equals(item, ignoreCase = true); Text(item, modifier = Modifier.fillMaxWidth().clickable { onSelect(item); expanded = false }.padding(14.dp), color = if (isSelected) ThemeAccent else Color.White, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium); if (items.last() != item) HorizontalDivider(color = GlassBorder.copy(alpha = 0.2f)) }; Spacer(Modifier.height(8.dp)); Button(onClick = { onSyncClick.run(); expanded = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = GlassBg)) { Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = ThemeAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Sync", color = ThemeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } } } }
        }
    ) { measurables, constraints ->
        val handlePlaceable = measurables[0].measure(constraints)
        val menuPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(handlePlaceable.width, handlePlaceable.height) {
            handlePlaceable.place(0, 0)
            val spacing = 12.dp.roundToPx()
            when (position) {
                DockPosition.TOP -> menuPlaceable.place(x = (handlePlaceable.width - menuPlaceable.width) / 2, y = handlePlaceable.height + spacing)
                DockPosition.BOTTOM -> menuPlaceable.place(x = (handlePlaceable.width - menuPlaceable.width) / 2, y = -menuPlaceable.height - spacing)
                DockPosition.LEFT -> menuPlaceable.place(x = handlePlaceable.width + spacing, y = (handlePlaceable.height - menuPlaceable.height) / 2)
                DockPosition.RIGHT -> menuPlaceable.place(x = -menuPlaceable.width - spacing, y = (handlePlaceable.height - menuPlaceable.height) / 2)
            }
        }
    }
}

@Composable
fun VtopGlobalTopBar(currentScreen: String, onSyncClick: () -> Unit) {
    val context = LocalContext.current
    var timeAgoText by remember { mutableStateOf("Calculating...") }
    val syncStatus by VtopAppBridge.syncStatus

    LaunchedEffect(Unit) {
        while (true) {
            val lastSyncMillis = Vault.getLastSyncTimestamp(context)
            if (lastSyncMillis == 0L) {
                timeAgoText = "Never synced"
            } else {
                val diffSeconds = (System.currentTimeMillis() - lastSyncMillis) / 1000
                timeAgoText = when {
                    diffSeconds < 60 -> "Just now"
                    diffSeconds < 3600 -> "${diffSeconds / 60} mins ago"
                    diffSeconds < 86400 -> "${diffSeconds / 3600} hours ago"
                    else -> "${diffSeconds / 86400} days ago"
                }
            }
            delay(1000)
        }
    }

    val displayTitle = when (currentScreen.uppercase(Locale.ROOT)) {
        "HOME" -> "Timetable"
        "ATTENDANCE" -> "Attendance"
        "EXAMS" -> "Exam Schedule"
        "MARKS" -> "Marks & Grades"
        "OUTINGS" -> "Outings"
        "PROFILE" -> "Settings"
        else -> currentScreen
    }

    // Determine what the subtitle should say based on the global state
    val subtitleText = when (syncStatus) {
        "LOGGING_IN" -> "Logging in..."
        "SYNCING" -> "Syncing data..."
        else -> "Last synced: $timeAgoText"
    }

    // Optional: Make text pulse if syncing
    val alpha by animateFloatAsState(
        targetValue = if (syncStatus != "IDLE") 0.5f else 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    Row(modifier = Modifier.fillMaxWidth().background(Color.Transparent).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = displayTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(text = subtitleText, color = Color.Gray.copy(alpha = alpha), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onSyncClick, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape).size(42.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Color.White)
        }
    }
}



