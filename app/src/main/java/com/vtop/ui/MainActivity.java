package com.vtop.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.vtop.network.VtopClient;
import com.vtop.utils.Vault;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VTOP_MAIN";
    private FrameLayout timetableContainer;
    private boolean isSyncing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        timetableContainer = new FrameLayout(this);
        timetableContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(timetableContainer);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        getWindow().setStatusBarColor(Color.parseColor("#000000"));

        String lastSync = Vault.getLastSyncTime(this);
        if (lastSync.equals("Never")) {
            runSilentSync();
        } else {
            refreshUI();
        }
    }

    private void refreshUI() {
        if (timetableContainer == null) return;

        final com.vtop.models.TimetableModel finalTt = Vault.getTimetable(this);
        List<com.vtop.models.AttendanceModel> att = Vault.getAttendance(this);
        List<com.vtop.models.ExamScheduleModel> savedExams = Vault.getExamSchedule(this);
        List<com.vtop.models.OutingModel> savedOutings = Vault.getOutings(this);

        final List<com.vtop.models.SemesterOption> finalSems = new java.util.ArrayList<>();
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("VTOP_PREFS", android.content.Context.MODE_PRIVATE);
            String semJson = prefs.getString("SEMESTERS_CACHE", "[]");
            org.json.JSONArray array = new org.json.JSONArray(semJson);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject obj = array.getJSONObject(i);
                finalSems.add(new com.vtop.models.SemesterOption(obj.getString("id"), obj.getString("name")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse master semesters", e);
        }

        List<com.vtop.models.CourseMark> marks = Vault.getMarks(this);
        List<com.vtop.models.CourseGrade> grades = Vault.getGrades(this);
        final com.vtop.models.CGPASummary finalCgpa = Vault.getCGPASummary(this);
        List<com.vtop.models.GradeHistoryItem> history = Vault.getHistory(this);

        if (att == null) att = new java.util.ArrayList<>();
        if (savedExams == null) savedExams = new java.util.ArrayList<>();
        if (savedOutings == null) savedOutings = new java.util.ArrayList<>();
        if (marks == null) marks = new java.util.ArrayList<>();
        if (grades == null) grades = new java.util.ArrayList<>();
        if (history == null) history = new java.util.ArrayList<>();

        final List<com.vtop.models.AttendanceModel> finalAtt = att;
        final List<com.vtop.models.ExamScheduleModel> finalExams = savedExams;
        final List<com.vtop.models.OutingModel> finalOutings = savedOutings;
        final List<com.vtop.models.CourseMark> finalMarks = marks;
        final List<com.vtop.models.CourseGrade> finalGrades = grades;
        final List<com.vtop.models.GradeHistoryItem> finalHistory = history;

        final com.vtop.ui.OutingActionHandler finalOutingHandler = new com.vtop.ui.OutingActionHandler() {
            @Override
            public void onViewPass(String id, boolean isWeekend, kotlin.jvm.functions.Function1<? super java.io.File, kotlin.Unit> onReady) {
                executeViewPass(id, isWeekend, onReady);
            }

            @Override
            public void onWeekendSubmit(String place, String purpose, String date, String time, String contact) {
                executeWeekendSubmission(place, purpose, date, time, contact);
            }

            @Override
            public void onGeneralSubmit(String place, String purpose, String fromDate, String toDate, String fromTime, String toTime) {
                executeGeneralSubmission(place, purpose, fromDate, toDate, fromTime, toTime);
            }

            @Override
            public void onDelete(String id, boolean isWeekend) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Deletion is disabled.", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFetchGeneralFormData(com.vtop.ui.FetchCallback callback) {
                executeFetchGeneralFormData(callback);
            }

            @Override
            public void onFetchWeekendFormData(com.vtop.ui.FetchCallback callback) {
                executeFetchWeekendFormData(callback);
            }
        };

        timetableContainer.post(() -> {
            timetableContainer.removeAllViews();
            com.vtop.ui.VtopAppBridge.launch(
                    timetableContainer,
                    finalTt, finalAtt, finalExams, finalOutings,
                    finalSems, finalMarks, finalGrades, finalCgpa, finalHistory,
                    this::runSilentSync,
                    this::executeLogout,
                    finalOutingHandler,
                    (semId, isForGrades) -> {
                        executeDynamicMarksFetch(semId, isForGrades);
                        return kotlin.Unit.INSTANCE;
                    }
            );
        });
    }

    private void runSilentSync() {
        Log.d("VTOP_SYNC", "runSilentSync() triggered! Current lock state: " + isSyncing);
        if (isSyncing) return;
        isSyncing = true;

        // 1. UPDATE STATE: LOGGING IN
        runOnUiThread(() -> com.vtop.ui.VtopAppBridge.INSTANCE.getSyncStatus().setValue("LOGGING_IN"));

        String[] creds = Vault.getCredentials(this);
        if (creds[0] == null || creds[1] == null || creds[0].isEmpty()) {
            com.vtop.ui.VtopAppBridge.showError("Please re-login to set credentials.");
            isSyncing = false;
            runOnUiThread(() -> com.vtop.ui.VtopAppBridge.INSTANCE.getSyncStatus().setValue("IDLE"));
            return;
        }

        final String userReg = creds[0];
        final String userPass = creds[1];

        String[] selectedSemData = Vault.getSelectedSemester(this);
        final String targetSemId = selectedSemData[0];

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            boolean success = false;
            String errorMessage = "Unknown Sync Error";
            VtopClient.StatusListener logListener = msg -> Log.d("VTOP_SYNC", msg);

            try {
                logListener.onStatusUpdate("=== STARTING GLOBAL SYNC: " + targetSemId + " ===");
                VtopClient client = new VtopClient(MainActivity.this, userReg, userPass);

                boolean loggedIn = false;
                for (int i = 1; i <= 3; i++) {
                    logListener.onStatusUpdate("Login Attempt " + i);
                    try {
                        if (client.autoLogin(MainActivity.this, createLoginListener("VTOP_SYNC"))) {
                            loggedIn = true;
                            break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Login Attempt Failed: " + e.getMessage());
                        if (e.getMessage() != null && e.getMessage().contains("Login/Password"))
                            throw e;
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (Exception ignored) {
                    }
                }

                if (!loggedIn)
                    throw new Exception("Login failed. Captcha error or Portal is down.");

                // 2. UPDATE STATE: SYNCING
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Sync started...", Toast.LENGTH_SHORT).show();
                    com.vtop.ui.VtopAppBridge.INSTANCE.getSyncStatus().setValue("SYNCING");
                });

                logListener.onStatusUpdate("Fetching Attendance...");
                String attHtml = client.fetchAttendanceRawHtml(targetSemId, logListener);
                if (attHtml == null || attHtml.length() < 1000)
                    throw new Exception("Attendance Session Rejected");
                List<com.vtop.models.AttendanceModel> attList = com.vtop.logic.AttendanceParser.parseSummary(attHtml);

                for (com.vtop.models.AttendanceModel course : attList) {
                    try {
                        String detailHtml = client.fetchAttendanceDetailRawHtml(
                                targetSemId, course.courseId, course.courseType, userReg, msg -> {
                                }
                        );
                        if (detailHtml != null)
                            com.vtop.logic.AttendanceParser.parseDetailAndUpdate(detailHtml, course);
                    } catch (Exception detailEx) {
                        Log.e(TAG, "Failed to fetch detailed history for: " + course.courseCode);
                    }
                }

                logListener.onStatusUpdate("Fetching Timetable...");
                String ttHtml = client.fetchTimetableRawHtml(targetSemId, logListener);
                com.vtop.models.TimetableModel timetable = null;
                if (ttHtml != null && ttHtml.length() > 1000)
                    timetable = com.vtop.logic.TimetableParser.parse(ttHtml);

                logListener.onStatusUpdate("Fetching Exams...");
                String examsHtml = client.fetchExamScheduleRawHtml(targetSemId, logListener);
                java.util.List<com.vtop.models.ExamScheduleModel> examList = new java.util.ArrayList<>();
                if (examsHtml != null && examsHtml.length() > 1000)
                    examList = com.vtop.logic.ExamScheduleParser.INSTANCE.parse(examsHtml);

                logListener.onStatusUpdate("Fetching Outing History...");
                java.util.List<com.vtop.models.OutingModel> finalOutingsList = new java.util.ArrayList<>();
                String weekendHtml = client.fetchWeekendOutingRawHtml(userReg, logListener);
                if (weekendHtml != null && weekendHtml.length() > 1000)
                    finalOutingsList.addAll(com.vtop.logic.OutingParser.INSTANCE.parseWeekend(weekendHtml));
                String generalHtml = client.fetchGeneralOutingRawHtml(userReg, logListener);
                if (generalHtml != null && generalHtml.length() > 1000)
                    finalOutingsList.addAll(com.vtop.logic.OutingParser.INSTANCE.parseGeneral(generalHtml));

                logListener.onStatusUpdate("Fetching Marks...");
                String marksHtml = client.fetchMarksRawHtml(targetSemId, logListener);
                List<com.vtop.models.CourseMark> parsedMarks = new java.util.ArrayList<>();
                if (marksHtml != null && marksHtml.length() > 1000)
                    parsedMarks = com.vtop.logic.MarksParser.INSTANCE.parseMarks(marksHtml);

                logListener.onStatusUpdate("Fetching Grades...");
                String gradesHtml = client.fetchGradesRawHtml(targetSemId, logListener);
                List<com.vtop.models.CourseGrade> parsedGrades = new java.util.ArrayList<>();
                if (gradesHtml != null && gradesHtml.length() > 1000)
                    parsedGrades = com.vtop.logic.MarksParser.INSTANCE.parseGrades(gradesHtml);

                logListener.onStatusUpdate("Fetching Grade History...");
                String historyHtml = client.fetchHistoryRawHtml(logListener);
                com.vtop.models.CGPASummary parsedCgpa = null;
                List<com.vtop.models.GradeHistoryItem> parsedHistory = new java.util.ArrayList<>();
                if (historyHtml != null && historyHtml.length() > 1000) {
                    kotlin.Pair<com.vtop.models.CGPASummary, List<com.vtop.models.GradeHistoryItem>> historyData = com.vtop.logic.MarksParser.INSTANCE.parseHistory(historyHtml);
                    parsedCgpa = historyData.getFirst();
                    parsedHistory = historyData.getSecond();
                }

                logListener.onStatusUpdate("Saving all data to Vault...");
                Vault.saveAttendance(MainActivity.this, attList);
                if (timetable != null) Vault.saveTimetable(MainActivity.this, timetable);
                Vault.saveExamSchedule(MainActivity.this, examList);
                Vault.saveOutings(MainActivity.this, finalOutingsList);
                Vault.saveMarks(MainActivity.this, parsedMarks);
                Vault.saveGrades(MainActivity.this, parsedGrades);
                if (parsedCgpa != null) Vault.saveCGPASummary(MainActivity.this, parsedCgpa);
                Vault.saveHistory(MainActivity.this, parsedHistory);

                success = true;

            } catch (java.net.UnknownHostException e) {
                Log.e(TAG, "No Internet", e);
                errorMessage = "No internet connection. Please turn on WiFi or Mobile Data.";
            } catch (java.net.SocketTimeoutException e) {
                Log.e(TAG, "Timeout", e);
                errorMessage = "Connection timed out. Your internet is too slow or VTOP is overloaded.";
            } catch (java.net.ConnectException e) {
                Log.e(TAG, "Connection Refused", e);
                errorMessage = "Connection refused. VTOP servers might be completely offline.";
            } catch (Exception e) {
                Log.e(TAG, "Fatal sync error", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                if (msg.contains("UnknownHostException") || msg.contains("Unable to resolve host")) {
                    errorMessage = "No internet connection. Please turn on WiFi or Mobile Data.";
                } else if (msg.contains("SocketTimeoutException") || msg.contains("timeout")) {
                    errorMessage = "Connection timed out. Your internet is too slow or VTOP is overloaded.";
                } else if (msg.contains("ConnectException") || msg.contains("Failed to connect")) {
                    errorMessage = "Connection refused. VTOP servers might be completely offline.";
                } else if (msg.contains("Login/Password")) {
                    errorMessage = "Login failed. Please check your credentials.";
                } else if (msg.contains("Captcha")) {
                    errorMessage = "Login failed. Retrying Captcha";
                } else if (msg.contains("Attendance Session Rejected")) {
                    errorMessage = "VTOP Session Rejected. Try syncing again in a few minutes.";
                } else {
                    errorMessage = msg;
                }
            } finally {
                final boolean didSucceed = success;
                final String finalError = errorMessage;
                runOnUiThread(() -> {
                    isSyncing = false;

                    // 3. UPDATE STATE: BACK TO IDLE
                    com.vtop.ui.VtopAppBridge.INSTANCE.getSyncStatus().setValue("IDLE");

                    if (didSucceed) {
                        Vault.saveLastSyncTime(MainActivity.this);
                        Toast.makeText(MainActivity.this, "Sync successful!", Toast.LENGTH_SHORT).show();
                        refreshUI();
                        try {
                            com.vtop.widget.WidgetUpdater.INSTANCE.updateWidgetNow(MainActivity.this);
                        } catch (Exception ignored) {
                        }
                    } else {
                        com.vtop.ui.VtopAppBridge.showError(finalError);
                    }
                });
            }
            executor.shutdown();
        });
    }

    private void executeDynamicMarksFetch(final String semId, final boolean isForGrades) {
        final String typeLabel = isForGrades ? "Grades" : "Marks";
        final Toast loadingToast = Toast.makeText(this, "Fetching " + typeLabel + "...", Toast.LENGTH_LONG);
        loadingToast.show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String[] creds = Vault.getCredentials(MainActivity.this);
            VtopClient client = new VtopClient(MainActivity.this, creds[0], creds[1]);

            boolean loggedIn = false;
            for (int i = 1; i <= 5; i++) {
                try {
                    if (client.autoLogin(MainActivity.this, createLoginListener("VTOP_MARKS"))) {
                        loggedIn = true;
                        break;
                    }
                } catch (Exception e) {
                    Log.e("VTOP_MARKS", "Login Error: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Login/Password")) break;
                }
                try {
                    Thread.sleep(1500);
                } catch (Exception ignored) {
                }
            }

            if (loggedIn) {
                if (isForGrades) {
                    String html = client.fetchGradesRawHtml(semId, msg -> Log.d("VTOP_MARKS", msg));
                    if (html != null && html.length() > 500) {
                        final List<com.vtop.models.CourseGrade> parsedGrades = com.vtop.logic.MarksParser.INSTANCE.parseGrades(html);
                        Vault.saveGrades(MainActivity.this, parsedGrades);
                        runOnUiThread(() -> {
                            loadingToast.cancel();
                            if (parsedGrades.isEmpty())
                                Toast.makeText(MainActivity.this, "No Grades published yet.", Toast.LENGTH_SHORT).show();
                            else {
                                Toast.makeText(MainActivity.this, "Fetched " + parsedGrades.size() + " Grades!", Toast.LENGTH_SHORT).show();
                                com.vtop.ui.VtopAppBridge.updateGradesOnly(parsedGrades);
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            loadingToast.cancel();
                            com.vtop.ui.VtopAppBridge.showError("Failed to fetch Grades. VTOP response was empty.");
                        });
                    }
                } else {
                    String html = client.fetchMarksRawHtml(semId, msg -> Log.d("VTOP_MARKS", msg));
                    if (html != null && html.length() > 500) {
                        final List<com.vtop.models.CourseMark> parsedMarks = com.vtop.logic.MarksParser.INSTANCE.parseMarks(html);
                        Vault.saveMarks(MainActivity.this, parsedMarks);
                        runOnUiThread(() -> {
                            loadingToast.cancel();
                            if (parsedMarks.isEmpty())
                                Toast.makeText(MainActivity.this, "No Marks published yet.", Toast.LENGTH_SHORT).show();
                            else {
                                Toast.makeText(MainActivity.this, "Fetched " + parsedMarks.size() + " Marks!", Toast.LENGTH_SHORT).show();
                                com.vtop.ui.VtopAppBridge.updateMarksOnly(parsedMarks);
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            loadingToast.cancel();
                            com.vtop.ui.VtopAppBridge.showError("Failed to fetch Marks. VTOP response was empty.");
                        });
                    }
                }
            } else {
                runOnUiThread(() -> {
                    loadingToast.cancel();
                    com.vtop.ui.VtopAppBridge.showError("Marks Sync Failed: VTOP Captcha error or Portal is down.");
                });
            }
        });
        executor.shutdown();
    }

    private void executeFetchGeneralFormData(com.vtop.ui.FetchCallback callback) {
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String[] creds = Vault.getCredentials(MainActivity.this);
            VtopClient client = new VtopClient(MainActivity.this, creds[0], creds[1]);

            boolean loggedIn = false;
            for (int i = 1; i <= 3; i++) {
                try {
                    if (client.autoLogin(MainActivity.this, createLoginListener("VTOP_ACTION"))) {
                        loggedIn = true;
                        break;
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Login/Password")) break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            }

            if (loggedIn) {
                String html = client.fetchGeneralOutingRawHtml(creds[0], msg -> {
                });
                java.util.Map<String, String> data = com.vtop.logic.OutingParser.INSTANCE.parsePrefilledFormData(html);
                runOnUiThread(() -> callback.onResult(data));
            } else {
                runOnUiThread(() -> callback.onResult(null));
            }
        });
        executor.shutdown();
    }

    private void executeFetchWeekendFormData(com.vtop.ui.FetchCallback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String[] creds = Vault.getCredentials(MainActivity.this);
            VtopClient client = new VtopClient(MainActivity.this, creds[0], creds[1]);

            boolean loggedIn = false;
            for (int i = 1; i <= 3; i++) {
                try {
                    if (client.autoLogin(MainActivity.this, createLoginListener("VTOP_ACTION"))) {
                        loggedIn = true;
                        break;
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Login/Password")) break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            }

            if (loggedIn) {
                String html = client.fetchWeekendOutingFormHtml(creds[0], msg -> {
                });
                java.util.Map<String, String> data = com.vtop.logic.OutingParser.INSTANCE.parseWeekendFormData(html);
                runOnUiThread(() -> callback.onResult(data));
            } else {
                runOnUiThread(() -> callback.onResult(null));
            }
        });
        executor.shutdown();
    }

    private void executeViewPass(final String id, final boolean isWeekend, final kotlin.jvm.functions.Function1<? super java.io.File, kotlin.Unit> onReady) {
        Toast.makeText(this, "Fetching Outpass...", Toast.LENGTH_SHORT).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String[] creds = Vault.getCredentials(MainActivity.this);
            VtopClient client = new VtopClient(MainActivity.this, creds[0], creds[1]);

            boolean loggedIn = false;
            for (int i = 1; i <= 3; i++) {
                final int attempt = i;
                try {
                    if (client.autoLogin(MainActivity.this, createLoginListener("VTOP_ACTION"))) {
                        loggedIn = true;
                        break;
                    }
                } catch (Exception e) {
                    Log.e("VTOP_ACTION", "Login attempt " + attempt + " failed: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Login/Password")) break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            }

            if (loggedIn) {
                try {
                    java.io.File cachePath = new java.io.File(getCacheDir(), "outpasses");
                    if (!cachePath.exists()) cachePath.mkdirs();

                    final java.io.File pdfFile = new java.io.File(cachePath, id + ".pdf");
                    final boolean success = client.downloadAndCacheOutpass(id, isWeekend, creds[0], pdfFile);

                    runOnUiThread(() -> {
                        if (success) {
                            onReady.invoke(pdfFile);
                        } else {
                            onReady.invoke(null);
                            com.vtop.ui.VtopAppBridge.showError("Failed to fetch PDF. VTOP session expired.");
                        }
                    });
                } catch (Exception e) {
                    Log.e("VTOP_RESULT", "PDF fetch error", e);
                    final String safeErrorMsg = e.getMessage() != null ? e.getMessage() : "Unknown Network Error";
                    runOnUiThread(() -> {
                        onReady.invoke(null);
                        com.vtop.ui.VtopAppBridge.showError("Error downloading PDF: " + safeErrorMsg);
                    });
                }
            } else {
                runOnUiThread(() -> {
                    onReady.invoke(null);
                    com.vtop.ui.VtopAppBridge.showError("Authentication failed. Captcha error or VTOP is down.");
                });
            }
        });
        executor.shutdown();
    }

    private void executeWeekendSubmission(final String place, final String purpose, final String date, final String time, final String contact) {
        Toast.makeText(this, "Submitting to VTOP...", Toast.LENGTH_SHORT).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String[] creds = Vault.getCredentials(MainActivity.this);
            VtopClient client = new VtopClient(MainActivity.this, creds[0], creds[1]);

            boolean loggedIn = false;
            for (int i = 1; i <= 5; i++) {
                try {
                    if (client.autoLogin(MainActivity.this, createLoginListener("VTOP_ACTION"))) {
                        loggedIn = true;
                        break;
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Invalid Login/Password"))

                        break;
                }
                try {
                    Thread.sleep(1500);
                } catch (Exception ignored) {
                }
            }

            if (loggedIn) {
                final String result = client.submitWeekendOuting(creds[0], place, purpose, date, time, contact, msg -> Log.d("VTOP_ACTION", msg));
                runOnUiThread(() -> {
                    if (result != null && result.startsWith("SUCCESS")) {
                        Toast.makeText(MainActivity.this, "Outing Submitted!", Toast.LENGTH_LONG).show();
                        runSilentSync();
                    } else {
                        com.vtop.ui.VtopAppBridge.showError("Submission Failed: " + result);
                    }
                });
            } else {
                runOnUiThread(() -> com.vtop.ui.VtopAppBridge.showError("Submission Failed: Could not bypass Captcha."));
            }
        });
        executor.shutdown();
    }

    private void executeGeneralSubmission(final String place, final String purpose, final String fromDate, final String toDate, final String fromTime, final String toTime) {
        Toast.makeText(this, "Submitting General Leave to VTOP...", Toast.LENGTH_SHORT).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String[] creds = Vault.getCredentials(MainActivity.this);
            VtopClient client = new VtopClient(MainActivity.this, creds[0], creds[1]);

            boolean loggedIn = false;
            for (int i = 1; i <= 5; i++) {
                final int attempt = i;
                try {
                    if (client.autoLogin(MainActivity.this, createLoginListener("VTOP_ACTION"))) {
                        loggedIn = true;
                        break;
                    }
                } catch (Exception e) {
                    Log.e("VTOP_ACTION", "Submit Login Attempt " + attempt + " failed: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("Invalid Login/Password")) {
                        break;
                    }
                }
                try {
                    Thread.sleep(1500);
                } catch (Exception ignored) {
                }
            }

            if (loggedIn) {
                final String result = client.submitGeneralOuting(creds[0], place, purpose, fromDate, toDate, fromTime, toTime, msg -> Log.d("VTOP_ACTION", msg));
                runOnUiThread(() -> {
                    if (result != null && result.startsWith("SUCCESS")) {
                        Toast.makeText(MainActivity.this, "General Leave Submitted!", Toast.LENGTH_LONG).show();
                        runSilentSync();
                    } else {
                        com.vtop.ui.VtopAppBridge.showError("Submission Failed: " + result);
                    }
                });
            } else {
                runOnUiThread(() -> com.vtop.ui.VtopAppBridge.showError("Submission Failed: VTOP Captcha rejected 5 times. Try again."));
            }
        });
        executor.shutdown();
    }

    private void executeLogout() {
        Vault.clearAll(this);
        getSharedPreferences("VTOP_PREFS", android.content.Context.MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, "Logged out successfully. Data cleared.", Toast.LENGTH_LONG).show();

        android.content.Intent intent = new android.content.Intent(this, LoginActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // =========================================================================
    // HELPER: Reusable OTP UI Listener
    // =========================================================================
    private VtopClient.LoginListener createLoginListener(final String tag) {
        return new VtopClient.LoginListener() {
            @Override
            public void onStatusUpdate(String message) {
                Log.d(tag, message);
            }

            @Override
            public void onOtpRequired(VtopClient.OtpResolver resolver) {
                runOnUiThread(() -> {
                    // Triggers the Compose OTP Overlay in VtopMainScreen
                    com.vtop.ui.VtopAppBridge.INSTANCE.getCurrentOtpResolver().setValue(resolver);
                });
            }
        };
    }
}