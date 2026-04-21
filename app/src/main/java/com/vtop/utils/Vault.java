package com.vtop.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.vtop.models.AttendanceModel;
import com.vtop.models.ExamScheduleModel;
import com.vtop.models.TimetableModel;

// --- NEW MARKS ECOSYSTEM IMPORTS ---
import com.vtop.models.SemesterOption;
import com.vtop.models.CourseMark;
import com.vtop.models.CourseGrade;
import com.vtop.models.GradeHistoryItem;
import com.vtop.models.CGPASummary;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Vault {

    private static final String TAG = "VTOP_VAULT";
    private static final String SECRET_PREFS = "secret_vtop_prefs";
    private static final String PUBLIC_PREFS = "VTOP_VAULT";

    // Storage Keys
    private static final String KEY_TIMETABLE = "OFFLINE_TIMETABLE";
    private static final String KEY_ATTENDANCE = "OFFLINE_ATTENDANCE";
    private static final String KEY_EXAMS = "OFFLINE_EXAMS_JSON";
    private static final String KEY_OUTINGS = "OFFLINE_OUTINGS";
    private static final String KEY_LAST_SYNC = "LAST_SYNC_TIME";
    private static final String KEY_SEM_ID = "SELECTED_SEM_ID";
    private static final String KEY_SEM_NAME = "SELECTED_SEM_NAME";

    // --- NEW MARKS ECOSYSTEM KEYS ---
    private static final String KEY_SEMESTER_OPTIONS = "OFFLINE_SEM_OPTIONS";
    private static final String KEY_MARKS = "OFFLINE_MARKS";
    private static final String KEY_GRADES = "OFFLINE_GRADES";
    private static final String KEY_HISTORY_ITEMS = "OFFLINE_HISTORY_ITEMS";
    private static final String KEY_HISTORY_SUMMARY = "OFFLINE_HISTORY_SUMMARY";

    // --- SECURE CREDENTIALS (ENCRYPTED) ---
    public static void setNavStyle(Context context, String style) {
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
                .edit().putString("NAV_STYLE", style).apply();
    }

    public static String getNavStyle(Context context) {
        return context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
                .getString("NAV_STYLE", "DOCK"); // Default to the new optimal bar
    }
    public static void saveCredentials(Context context, String regNo, String password) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences prefs = EncryptedSharedPreferences.create(context, SECRET_PREFS, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            prefs.edit().putString("reg_no", regNo.toUpperCase().trim()).putString("password", password.trim()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed, using fallback", e);
            context.getSharedPreferences("vtop_fallback", Context.MODE_PRIVATE).edit().putString("reg_no", regNo).putString("password", password).apply();
        }
    }

    public static String[] getCredentials(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences prefs = EncryptedSharedPreferences.create(context, SECRET_PREFS, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            String reg = prefs.getString("reg_no", null);
            String pwd = prefs.getString("password", null);
            if (reg == null) {
                SharedPreferences fall = context.getSharedPreferences("vtop_fallback", Context.MODE_PRIVATE);
                return new String[]{fall.getString("reg_no", null), fall.getString("password", null)};
            }
            return new String[]{reg, pwd};
        } catch (Exception e) { return new String[]{null, null}; }
    }

    // --- ACADEMIC DATA STORAGE (TIMETABLE, ATTENDANCE, EXAMS) ---
    @SuppressLint("ApplySharedPref")
    public static void saveTimetable(Context context, TimetableModel timetable) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TIMETABLE, new Gson().toJson(timetable)).commit();
    }
    public static TimetableModel getTimetable(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_TIMETABLE, null);
        return json == null ? null : new Gson().fromJson(json, TimetableModel.class);
    }

    @SuppressLint("ApplySharedPref")
    public static void saveAttendance(Context context, List<AttendanceModel> list) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ATTENDANCE, new Gson().toJson(list)).commit();
    }
    public static List<AttendanceModel> getAttendance(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_ATTENDANCE, null);
        if (json == null) return new ArrayList<>();
        return new Gson().fromJson(json, new TypeToken<ArrayList<AttendanceModel>>(){}.getType());
    }

    @SuppressLint("ApplySharedPref")
    public static void saveExamSchedule(Context context, List<ExamScheduleModel> exams) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EXAMS, new Gson().toJson(exams)).commit();
    }
    public static List<ExamScheduleModel> getExamSchedule(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_EXAMS, "[]");
        try {
            List<ExamScheduleModel> list = new Gson().fromJson(json, new TypeToken<ArrayList<ExamScheduleModel>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    @SuppressLint("ApplySharedPref")
    public static void saveOutings(Context context, List<com.vtop.models.OutingModel> outings) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_OUTINGS, new Gson().toJson(outings)).commit();
    }
    public static List<com.vtop.models.OutingModel> getOutings(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_OUTINGS, "[]");
        try {
            List<com.vtop.models.OutingModel> list = new Gson().fromJson(json, new TypeToken<ArrayList<com.vtop.models.OutingModel>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    // ==============================================================================
    // --- NEW: MARKS, GRADES & HISTORY STORAGE ---
    // ==============================================================================
    @SuppressLint("ApplySharedPref")
    public static void saveSemesterOptions(Context context, List<SemesterOption> list) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SEMESTER_OPTIONS, new Gson().toJson(list)).commit();
    }
    public static List<SemesterOption> getSemesterOptions(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_SEMESTER_OPTIONS, "[]");
        try {
            List<SemesterOption> list = new Gson().fromJson(json, new TypeToken<ArrayList<SemesterOption>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    @SuppressLint("ApplySharedPref")
    public static void saveMarks(Context context, List<CourseMark> list) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_MARKS, new Gson().toJson(list)).commit();
    }
    public static List<CourseMark> getMarks(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_MARKS, "[]");
        try {
            List<CourseMark> list = new Gson().fromJson(json, new TypeToken<ArrayList<CourseMark>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    @SuppressLint("ApplySharedPref")
    public static void saveGrades(Context context, List<CourseGrade> list) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_GRADES, new Gson().toJson(list)).commit();
    }
    public static List<CourseGrade> getGrades(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_GRADES, "[]");
        try {
            List<CourseGrade> list = new Gson().fromJson(json, new TypeToken<ArrayList<CourseGrade>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    @SuppressLint("ApplySharedPref")
    public static void saveHistory(Context context, List<GradeHistoryItem> list) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY_ITEMS, new Gson().toJson(list)).commit();
    }
    public static List<GradeHistoryItem> getHistory(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_HISTORY_ITEMS, "[]");
        try {
            List<GradeHistoryItem> list = new Gson().fromJson(json, new TypeToken<ArrayList<GradeHistoryItem>>(){}.getType());
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) { return new ArrayList<>(); }
    }

    @SuppressLint("ApplySharedPref")
    public static void saveCGPASummary(Context context, CGPASummary summary) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HISTORY_SUMMARY, new Gson().toJson(summary)).commit();
    }
    public static CGPASummary getCGPASummary(Context context) {
        String json = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_HISTORY_SUMMARY, null);
        return json == null ? null : new Gson().fromJson(json, CGPASummary.class);
    }

    // --- UTILS & SETTINGS ---
    public static void saveLastSyncTime(Context context) {
        String time = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date());
        long currentMillis = System.currentTimeMillis();
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_SYNC, time)
                .putLong("LAST_SYNC_TIMESTAMP", currentMillis) // <-- NEW: Save raw timestamp
                .apply();
    }

    public static String getLastSyncTime(Context context) {
        return context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_SYNC, "Never");
    }

    // <-- NEW: Retrieve raw timestamp for the live counter
    public static long getLastSyncTimestamp(Context context) {
        return context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).getLong("LAST_SYNC_TIMESTAMP", 0L);
    }
    public static void saveSelectedSemester(Context context, String id, String name) {
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SEM_ID, id).putString(KEY_SEM_NAME, name).apply();
    }
    public static String[] getSelectedSemester(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE);
        return new String[]{ prefs.getString(KEY_SEM_ID, "AP2025264"), prefs.getString(KEY_SEM_NAME, "Winter Semester 2025-26") };
    }
    public static void clearAll(Context context) {
        // 1. Clear normal academic data
        context.getSharedPreferences(PUBLIC_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        // 2. Clear fallback credentials
        context.getSharedPreferences("vtop_fallback", Context.MODE_PRIVATE).edit().clear().apply();
        // 3. Clear Encrypted Credentials
        context.getSharedPreferences(SECRET_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        Log.d(TAG, "All local vault data AND secure credentials cleared.");
    }
}