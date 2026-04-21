package com.vtop.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.vtop.network.VtopClient;
import com.vtop.utils.Vault;
import com.vtop.models.*;

import java.util.List;
import java.util.Map;

@SuppressWarnings("SpellCheckingInspection")
public class LoginActivity extends AppCompatActivity {

    private VtopClient activeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Vault.getTimetable(this) != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setTag("vtop_root");
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        String[] savedCreds = Vault.getCredentials(this);

        com.vtop.ui.VtopLoginBridge.launchAuth(root, savedCreds[0], savedCreds[1], new com.vtop.ui.AuthActionCallback() {
            @Override
            public void onLoginSubmit(@org.jetbrains.annotations.NotNull String regNo, @org.jetbrains.annotations.NotNull String pass) {
                executeLoginSequence(regNo, pass);
            }

            @Override
            public void onSemesterSelect(@org.jetbrains.annotations.NotNull String semId, @org.jetbrains.annotations.NotNull String semName) {
                Vault.saveSelectedSemester(LoginActivity.this, semId, semName);
                executeDataDownload(semId);
            }
        });
    }

    private void executeLoginSequence(String reg, String pwd) {
        com.vtop.ui.VtopLoginBridge.INSTANCE.getCurrentState().setValue(com.vtop.ui.AuthState.LOADING_SEMESTERS);
        com.vtop.ui.VtopLoginBridge.INSTANCE.getLoginError().setValue("Login succeeded but couldn't load semesters. Please try again.");

        new Thread(() -> {
            activeClient = new VtopClient(LoginActivity.this, reg, pwd);
            boolean isSuccess = false;
            int attempts = 0;
            String finalError = "Network timeout or portal down. Please try again.";

            while (attempts < 3 && !isSuccess) {
                attempts++;
                try {
                    isSuccess = activeClient.autoLogin(this, new VtopClient.LoginListener() {
                        @Override
                        public void onStatusUpdate(String message) {
                            Log.d("VTOP_LOGIN_STATUS", message);
                        }

                        @Override
                        public void onOtpRequired(VtopClient.OtpResolver resolver) {
                            Log.d("VTOP_OTP", "onOtpRequired called — showing OTP form");
                            runOnUiThread(() -> {
                                Log.d("VTOP_OTP", "Adding OTP overlay to root");
                                FrameLayout root = findViewById(android.R.id.content);
                                com.vtop.ui.VtopTimetableComposeUIKt.showOtpOverlay(root, LoginActivity.this, resolver);
                            });
                        }
                    });
                } catch (Exception e) {
                    Log.e("VTOP_LOGIN", "Attempt " + attempts + " failed", e);
                    if (e.getMessage() != null) {
                        finalError = e.getMessage().replace("java.lang.Exception:", "").trim();
                    } else {
                        finalError = "Unknown Authentication Error";
                    }

                    // Fixed: Checking for "Canceled" with one 'L'
                    if (finalError.contains("Login/Password") || finalError.contains("Invalid Login") || finalError.contains("Canceled")) {
                        break;
                    }
                }

                if (!isSuccess && attempts < 3 && !finalError.contains("Login/Password") && !finalError.contains("Canceled")) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
            }

            if (isSuccess) {
                Vault.saveCredentials(this, reg, pwd);
                List<Map<String, String>> semesters = activeClient.fetchSemesters();

                android.content.SharedPreferences prefs = getSharedPreferences("VTOP_PREFS", android.content.Context.MODE_PRIVATE);
                org.json.JSONArray jsonArray = new org.json.JSONArray();
                for (Map<String, String> sem : semesters) {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    try {
                        obj.put("id", sem.get("id"));
                        obj.put("name", sem.get("name"));
                        jsonArray.put(obj);
                    } catch (org.json.JSONException ignored) {}
                }
                prefs.edit().putString("SEMESTERS_CACHE", jsonArray.toString()).apply();

                runOnUiThread(() -> {
                    com.vtop.ui.VtopLoginBridge.INSTANCE.getFetchedSemesters().setValue(semesters);
                    com.vtop.ui.VtopLoginBridge.INSTANCE.getCurrentState().setValue(com.vtop.ui.AuthState.SELECT_SEMESTER);
                });
            } else {
                final String displayError = finalError;
                runOnUiThread(() -> {
                    com.vtop.ui.VtopLoginBridge.INSTANCE.getCurrentState().setValue(com.vtop.ui.AuthState.FORM);
                    com.vtop.ui.VtopLoginBridge.INSTANCE.getLoginError().setValue(displayError);
                });
            }
        }).start();
    }

    private void executeDataDownload(String semId) {
        com.vtop.ui.VtopLoginBridge.INSTANCE.getCurrentState().setValue(com.vtop.ui.AuthState.DOWNLOADING_DATA);

        new Thread(() -> {
            boolean criticalFailure = false;
            try {
                try {
                    String ttHtml = activeClient.fetchTimetableRawHtml(semId, msg -> {});
                    if (ttHtml != null) {
                        Vault.saveTimetable(this, com.vtop.logic.TimetableParser.parse(ttHtml));
                    } else criticalFailure = true;
                } catch (Exception e) {
                    Log.e("VTOP_SYNC", "Timetable failed", e);
                    criticalFailure = true;
                }

                try {
                    String attHtml = activeClient.fetchAttendanceRawHtml(semId, msg -> {});
                    if (attHtml != null) {
                        List<AttendanceModel> attSummary = com.vtop.logic.AttendanceParser.parseSummary(attHtml);
                        String regNo = Vault.getCredentials(LoginActivity.this)[0];

                        for (AttendanceModel course : attSummary) {
                            try {
                                String detailHtml = activeClient.fetchAttendanceDetailRawHtml(
                                        semId,
                                        course.classId,
                                        course.courseType,
                                        regNo,
                                        msg -> {}
                                );
                                if (detailHtml != null) {
                                    com.vtop.logic.AttendanceParser.parseDetailAndUpdate(detailHtml, course);
                                }
                            } catch (Exception detailEx) {
                                Log.e("VTOP_SYNC", "Failed to fetch detailed history for: " + course.courseCode, detailEx);
                            }
                        }
                        Vault.saveAttendance(LoginActivity.this, attSummary);
                    } else criticalFailure = true;
                } catch (Exception e) {
                    Log.e("VTOP_SYNC", "Attendance global fetch failed", e);
                    criticalFailure = true;
                }

                try {
                    String examsHtml = activeClient.fetchExamScheduleRawHtml(semId, msg -> {});
                    if (examsHtml != null) {
                        Vault.saveExamSchedule(this, com.vtop.logic.ExamScheduleParser.INSTANCE.parse(examsHtml));
                    }
                } catch (Exception e) { Log.e("VTOP_SYNC", "Exams failed", e); }

                try {
                    String marksHtml = activeClient.fetchMarksRawHtml(semId, msg -> {});
                    if (marksHtml != null) {
                        Vault.saveMarks(this, com.vtop.logic.MarksParser.INSTANCE.parseMarks(marksHtml));
                    }
                } catch (Exception e) { Log.e("VTOP_SYNC", "Marks failed", e); }

                try {
                    String gradesHtml = activeClient.fetchGradesRawHtml(semId, msg -> {});
                    if (gradesHtml != null) {
                        Vault.saveGrades(this, com.vtop.logic.MarksParser.INSTANCE.parseGrades(gradesHtml));
                    }
                } catch (Exception e) { Log.e("VTOP_SYNC", "Grades failed", e); }

                try {
                    String historyHtml = activeClient.fetchHistoryRawHtml(msg -> {});
                    if (historyHtml != null) {
                        kotlin.Pair<CGPASummary, java.util.List<GradeHistoryItem>> historyData = com.vtop.logic.MarksParser.INSTANCE.parseHistory(historyHtml);

                        if (historyData.getFirst() != null) Vault.saveCGPASummary(this, historyData.getFirst());
                        if (historyData.getSecond() != null) Vault.saveHistory(this, historyData.getSecond());
                    }
                } catch (Exception e) { Log.e("VTOP_SYNC", "History failed", e); }

                if (criticalFailure) {
                    throw new Exception("Core academic data failed to fetch. Portal may be down.");
                }

                Vault.saveLastSyncTime(this);

                runOnUiThread(() -> {
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
            } catch (Exception e) {
                Log.e("VTOP_SYNC", "Critical Sync Failure", e);
                final String safeError = e.getMessage() != null ? e.getMessage() : "Unknown Critical Error";
                runOnUiThread(() -> {
                    com.vtop.ui.VtopLoginBridge.INSTANCE.getCurrentState().setValue(com.vtop.ui.AuthState.FORM);
                    Toast.makeText(this, "Sync Error: " + safeError, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}