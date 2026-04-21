package com.vtop.network;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.vtop.logic.CaptchaSolver;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VtopClient {

    private static final String TAG = "VTOP_CLIENT";
    private static OkHttpClient sharedClient = null;
    private static SharedPrefsCookieJar cookieJarInstance = null;

    private final OkHttpClient client;
    private final String username;
    private final String password;
    private final String baseUrl = "https://vtop.vitap.ac.in/vtop";
    private String csrfToken = "";

    public interface OtpResolver {
        void submit(String otpCode);
        void cancel();
    }

    public interface StatusListener {
        void onStatusUpdate(String message);
    }

    public interface LoginListener extends StatusListener {
        void onOtpRequired(OtpResolver resolver);
    }

    // =========================================================================
    // PERSISTENT COOKIE STORAGE
    // =========================================================================
    public static class SharedPrefsCookieJar implements CookieJar {
        private final SharedPreferences prefs;
        private final Map<String, Cookie> memoryCookies = new ConcurrentHashMap<>();

        public SharedPrefsCookieJar(Context context) {
            prefs = context.getSharedPreferences("VTOP_COOKIES", Context.MODE_PRIVATE);
            // Load saved cookies from disk into memory
            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                String serializedCookie = (String) entry.getValue();
                Cookie cookie = Cookie.parse(HttpUrl.parse("https://vtop.vitap.ac.in"), serializedCookie);
                if (cookie != null) {
                    memoryCookies.put(cookie.name(), cookie);
                }
            }
        }

        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            SharedPreferences.Editor editor = prefs.edit();
            for (Cookie cookie : cookies) {
                memoryCookies.put(cookie.name(), cookie);
                editor.putString(cookie.name(), cookie.toString());
            }
            editor.apply();
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> validCookies = new ArrayList<>();
            for (Cookie cookie : memoryCookies.values()) {
                // Long.MIN_VALUE means no expiry set (session cookie) — always include it
                if (cookie.expiresAt() == Long.MIN_VALUE || cookie.expiresAt() > System.currentTimeMillis()) {
                    validCookies.add(cookie);
                }
            }
            return validCookies;
        }

        public void clear() {
            memoryCookies.clear();
            prefs.edit().clear().apply();
        }
    }

    // UPDATE: Constructor now requires Context to access SharedPreferences
    public VtopClient(Context context, String username, String password) {
        this.username = (username != null) ? username.trim() : "";
        this.password = (password != null) ? password.trim() : "";

        if (sharedClient == null) {
            cookieJarInstance = new SharedPrefsCookieJar(context.getApplicationContext());
            sharedClient = getUnsafeOkHttpClientBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .cookieJar(cookieJarInstance)
                    .build();
        }
        this.client = sharedClient;
    }

    // =========================================================================
    // AUTHENTICATION & LOGIN
    // =========================================================================

    public boolean autoLogin(Context context, LoginListener listener) throws Exception {
        listener.onStatusUpdate("[*] Checking saved session...");

        // FAST LANE: Try to use existing cookies to bypass login entirely
        Request sessionCheckReq = new Request.Builder()
                .url(baseUrl + "/content")
                .header("Referer", baseUrl + "/home")
                .get()
                .build();

        try (Response response = client.newCall(sessionCheckReq).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (body.contains("Dashboard") || body.contains("StudentProfile")) {
                csrfToken = extractToken(body);
                if (!csrfToken.isEmpty()) {
                    listener.onStatusUpdate("[+] Session restored seamlessly!");
                    return true; // You are in! Skip Captcha and OTP!
                }
            }
        } catch (Exception ignored) { }

        // If we reach here, the session is dead. Clear old cookies and do a fresh login.
        listener.onStatusUpdate("[*] Session expired. Initiating fresh login...");
        if (cookieJarInstance != null) {
            cookieJarInstance.clear();
        }

        Request openPageReq = new Request.Builder().url(baseUrl + "/open/page").get().build();
        String openPageHtml = "";
        try (Response response = client.newCall(openPageReq).execute()) {
            if (response.body() != null) openPageHtml = response.body().string();
        }

        csrfToken = extractToken(openPageHtml);
        if (csrfToken.isEmpty()) throw new Exception("CSRF Extraction Failed");

        RequestBody setupBody = new FormBody.Builder().add("_csrf", csrfToken).add("flag", "VTOP").build();
        Request setupReq = new Request.Builder().url(baseUrl + "/prelogin/setup").post(setupBody).build();
        client.newCall(setupReq).execute().close();

        Request loginPageReq = new Request.Builder().url(baseUrl + "/login").get().build();
        String loginHtml = "";
        try (Response response = client.newCall(loginPageReq).execute()) {
            if (response.body() != null) loginHtml = response.body().string();
        }

        String base64Captcha = extractCaptchaBase64(loginHtml);
        if (base64Captcha.isEmpty()) throw new Exception("Captcha Missing");

        byte[] captchaBytes = Base64.decode(base64Captcha, Base64.DEFAULT);
        Bitmap captchaBitmap = BitmapFactory.decodeByteArray(captchaBytes, 0, captchaBytes.length);
        String prediction = new CaptchaSolver(context).solve(captchaBitmap);

        Log.d(TAG, "CAPTCHA SOLVED: " + prediction);
        listener.onStatusUpdate("[+] Captcha Solved: " + prediction);

        return performLogin(prediction, listener);
    }

    private boolean performLogin(String captcha, LoginListener listener) throws Exception {
        listener.onStatusUpdate("[*] Validating Credentials...");
        Log.d("VTOP_NETWORK", "[*] Sending Login POST Request...");

        RequestBody loginBody = new FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("username", username)
                .add("password", password)
                .add("captchaStr", captcha)
                .add("loginMethod", "V")
                .build();

        Request loginReq = new Request.Builder()
                .url(baseUrl + "/login")
                .post(loginBody)
                .header("Referer", baseUrl + "/login")
                .build();

        try (Response response = client.newCall(loginReq).execute()) {
            String finalUrl = response.request().url().toString();
            String body = (response.body() != null) ? response.body().string() : "";

            Log.d("VTOP_NETWORK", "Login Response URL: " + finalUrl);

            // 1. OTP REQUIRED (Check this FIRST to avoid false positive 'Invalid Login' errors on the OTP page)
            if (body.contains("var securityOtpPending = true;") || body.contains("registered email")) {
                listener.onStatusUpdate("[!] OTP Sent to Email...");
                Log.d("VTOP_NETWORK", "[!] OTP page detected. Pausing background thread to wait for UI popup...");

                CountDownLatch latch = new CountDownLatch(1);
                final String[] userOtp = new String[1];

                // This triggers the UI popup in MainActivity
                listener.onOtpRequired(new OtpResolver() {
                    @Override
                    public void submit(String otpCode) {
                        userOtp[0] = otpCode;
                        latch.countDown(); // Resumes the background thread
                    }

                    @Override
                    public void cancel() {
                        userOtp[0] = null;
                        latch.countDown(); // Resumes the background thread
                    }
                });

                // Pause this background thread until the user taps 'Verify' or 'Cancel' in the UI
                latch.await();

                if (userOtp[0] == null || userOtp[0].trim().isEmpty()) {
                    Log.e("VTOP_NETWORK", "OTP Verification Canceled by User");
                    throw new Exception("OTP Verification Canceled");
                }

                listener.onStatusUpdate("[*] Verifying OTP...");
                Log.d("VTOP_NETWORK", "[*] Submitting OTP: " + userOtp[0].trim());

                RequestBody otpBody = new FormBody.Builder()
                        .add("otpCode", userOtp[0].trim())
                        .add("_csrf", csrfToken)
                        .build();

                Request otpReq = new Request.Builder()
                        .url(baseUrl + "/validateSecurityOtp")
                        .post(otpBody)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", finalUrl)
                        .build();

                try (Response otpRes = client.newCall(otpReq).execute()) {
                    String otpJson = (otpRes.body() != null) ? otpRes.body().string() : "";
                    Log.d("VTOP_NETWORK", "OTP Verification Response: " + otpJson);

                    try {
                        JSONObject json = new JSONObject(otpJson);
                        if (json.optString("status").equals("SUCCESS")) {
                            String redirectUrl = json.optString("redirectUrl");
                            if (redirectUrl.startsWith("/")) {
                                redirectUrl = "https://vtop.vitap.ac.in" + redirectUrl;
                            }

                            Log.d("VTOP_NETWORK", "[*] OTP Success. Following redirect to: " + redirectUrl);
                            Request finalReq = new Request.Builder().url(redirectUrl).get().build();

                            try (Response finalRes = client.newCall(finalReq).execute()) {
                                if (finalRes.body() != null) {
                                    csrfToken = extractToken(finalRes.body().string());
                                }
                                listener.onStatusUpdate("[+] OTP Verified Successfully!");
                                Log.d("VTOP_NETWORK", "[+] Login complete via OTP!");
                                return true;
                            }
                        } else {
                            Log.e("VTOP_NETWORK", "OTP Rejected: " + json.optString("message"));
                            throw new Exception("OTP Error: " + json.optString("message", "Invalid OTP"));
                        }
                    } catch (org.json.JSONException e) {
                        // Fallback if VTOP returns raw HTML instead of JSON for OTP failure
                        if (otpJson.contains("Invalid") || otpJson.contains("incorrect")) {
                            Log.e("VTOP_NETWORK", "OTP Rejected (HTML response): Invalid OTP");
                            throw new Exception("Invalid OTP Entered");
                        }
                        Log.d("VTOP_NETWORK", "OTP didn't return JSON, assuming success via HTML redirect.");
                        return true;
                    }
                }
            }

            // 2. CHECK FOR INVALID CREDENTIALS (Moved AFTER OTP check)
            if (finalUrl.contains("login/error") || body.contains("Invalid Login") || body.contains("User Id Not Available")) {
                Log.e("VTOP_NETWORK", "Login Failed: Invalid Credentials");
                throw new Exception("Invalid Login/Password");
            }

            // 3. CHECK FOR CAPTCHA ERROR
            if (body.toLowerCase().contains("captcha")) {
                Log.e("VTOP_NETWORK", "Login Failed: Invalid Captcha");
                throw new Exception("Invalid captcha");
            }

            // 4. STANDARD SUCCESS (No OTP Required)
            if (finalUrl.contains("/content") || finalUrl.contains("/studentsRecord") || body.contains("Sign out")) {
                csrfToken = extractToken(body);
                listener.onStatusUpdate("[+] Login Successful");
                Log.d("VTOP_NETWORK", "[+] Direct Login Successful (No OTP needed)");
                return true;
            }

            Log.e("VTOP_NETWORK", "Unknown Login State. Final URL: " + finalUrl);
            throw new Exception("Authentication Failed. Unknown State.");
        }
    }
    private String extractToken(String html) {
        if (html == null) return "";
        Pattern p = Pattern.compile("name=\"_csrf\"\\s+value=\"([a-f0-9-]+)\"");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private String extractCaptchaBase64(String html) {
        if (html == null) return "";
        Pattern p = Pattern.compile("data:image/jpeg;base64,([^\"]+)");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }

    // =========================================================================
    // ACADEMIC FETCHERS
    // =========================================================================

    public String fetchAttendanceRawHtml(String semesterId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Attendance...");
        return executeStandardPost(baseUrl + "/processViewStudentAttendance",
                new FormBody.Builder().add("_csrf", csrfToken).add("semesterSubId", semesterId)
                        .add("authorizedID", username).add("x", getGmtTimestamp()).build(),
                baseUrl + "/academics/common/StudentAttendance");
    }

    public String fetchAttendanceDetailRawHtml(String semId, String courseId, String courseType, String regNo, StatusListener listener) {
        return executeStandardPost(baseUrl + "/processViewAttendanceDetail",
                new FormBody.Builder().add("_csrf", csrfToken).add("semesterSubId", semId)
                        .add("registerNumber", regNo).add("courseId", courseId).add("courseType", courseType)
                        .add("authorizedID", regNo).add("x", getGmtTimestamp()).build(),
                baseUrl + "/content?");
    }

    public String fetchTimetableRawHtml(String semesterId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Timetable...");
        return executeStandardPost(baseUrl + "/processViewTimeTable",
                new FormBody.Builder().add("_csrf", csrfToken).add("semesterSubId", semesterId)
                        .add("authorizedID", username).add("x", getGmtTimestamp()).build(),
                baseUrl + "/academics/common/StudentTimeTable");
    }

    public String fetchMarksRawHtml(String semesterId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Marks...");
        return executeStandardPost(baseUrl + "/examinations/doStudentMarkView",
                new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", username)
                        .addFormDataPart("semesterSubId", semesterId).addFormDataPart("_csrf", csrfToken).build(),
                baseUrl + "/examinations/StudentMarkView");
    }

    public String fetchGradesRawHtml(String semesterId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Grades...");
        return executeStandardPost(baseUrl + "/examinations/examGradeView/doStudentGradeView",
                new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", username)
                        .addFormDataPart("semesterSubId", semesterId).addFormDataPart("_csrf", csrfToken).build(),
                baseUrl + "/examinations/examGradeView/StudentGradeView");
    }

    public String fetchHistoryRawHtml(StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Grade History...");
        return executeStandardPost(baseUrl + "/examinations/examGradeView/StudentGradeHistory",
                new FormBody.Builder().add("verifyMenu", "true").add("authorizedID", username).add("_csrf", csrfToken).build(),
                baseUrl + "/content?");
    }

    public String fetchExamScheduleRawHtml(String semesterId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Exam Schedule...");
        return executeStandardPost(baseUrl + "/examinations/doSearchExamScheduleForStudent",
                new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", username)
                        .addFormDataPart("semesterSubId", semesterId).addFormDataPart("_csrf", csrfToken).build(),
                baseUrl + "/examinations/common/ExamScheduleForStudent");
    }

    // =========================================================================
    // OUTING MODULE
    // =========================================================================

    public String fetchGeneralOutingRawHtml(String regNo, StatusListener listener) {
        return executeStandardPost(baseUrl + "/hostel/StudentGeneralOuting",
                new FormBody.Builder().add("verifyMenu", "true").add("authorizedID", regNo)
                        .add("_csrf", csrfToken).add("nocache", "@(" + new Date().getTime() + ")").build(),
                baseUrl + "/content?");
    }

    public String fetchWeekendOutingRawHtml(String regNo, StatusListener listener) {
        return fetchWeekendOutingFormHtml(regNo, listener);
    }

    public String fetchWeekendOutingFormHtml(String regNo, StatusListener listener) {
        return executeStandardPost(baseUrl + "/hostel/StudentWeekendOuting",
                new FormBody.Builder().add("verifyMenu", "true").add("authorizedID", regNo)
                        .add("_csrf", csrfToken).add("nocache", "@(" + new Date().getTime() + ")").build(),
                baseUrl + "/content?");
    }

    public String submitGeneralOuting(String regNo, String place, String purpose, String fromDate, String toDate, String fromTime, String toTime, StatusListener listener) {
        try {
            String formHtml = fetchGeneralOutingRawHtml(regNo, listener);
            if (formHtml == null) return "FAILED: VTOP form not loaded.";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(formHtml);

            String name = doc.select("input#name").val();
            String appNo = doc.select("input#applicationNo").val();
            String gender = doc.select("input#gender").val();
            String hostelBlock = doc.select("input#hostelBlock").val();
            String roomNo = doc.select("input#roomNo").val();

            String[] startParts = fromTime.split(":");
            String[] endParts = toTime.split(":");

            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("authorizedID", regNo).addFormDataPart("LeaveId", "")
                    .addFormDataPart("regNo", regNo).addFormDataPart("name", name)
                    .addFormDataPart("applicationNo", appNo).addFormDataPart("gender", gender)
                    .addFormDataPart("hostelBlock", hostelBlock).addFormDataPart("roomNo", roomNo)
                    .addFormDataPart("placeOfVisit", place).addFormDataPart("purposeOfVisit", purpose)
                    .addFormDataPart("outDate", fromDate).addFormDataPart("inDate", toDate)
                    .addFormDataPart("outTimeHr", startParts[0]).addFormDataPart("outTimeMin", startParts[1])
                    .addFormDataPart("inTimeHr", endParts[0]).addFormDataPart("inTimeMin", endParts[1])
                    .addFormDataPart("_csrf", csrfToken).build();

            return processOutingResponse(body, "/hostel/saveGeneralOutingForm", place);
        } catch (Exception e) { return "FAILED: " + e.getMessage(); }
    }

    public String submitWeekendOuting(String regNo, String place, String purpose, String date, String time, String contact, StatusListener listener) {
        try {
            String formHtml = fetchWeekendOutingFormHtml(regNo, listener);
            if (formHtml == null) return "FAILED: Form not loaded.";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(formHtml);

            String name = doc.select("input#name").val();
            String appNo = doc.select("input#applicationNo").val();
            String gender = doc.select("input#gender").val();
            String hostelBlock = doc.select("input#hostelBlock").val();
            String roomNo = doc.select("input#roomNo").val();
            String parentContact = doc.select("input#parentContactNumber").val();

            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("authorizedID", regNo).addFormDataPart("BookingId", "")
                    .addFormDataPart("regNo", regNo).addFormDataPart("name", name)
                    .addFormDataPart("applicationNo", appNo).addFormDataPart("gender", gender)
                    .addFormDataPart("hostelBlock", hostelBlock).addFormDataPart("roomNo", roomNo)
                    .addFormDataPart("outPlace", place).addFormDataPart("purposeOfVisit", purpose)
                    .addFormDataPart("outingDate", date).addFormDataPart("outTime", time)
                    .addFormDataPart("contactNumber", contact).addFormDataPart("parentContactNumber", parentContact)
                    .addFormDataPart("_csrf", csrfToken).build();

            return processOutingResponse(body, "/hostel/saveOutingForm", place);
        } catch (Exception e) { return "FAILED: " + e.getMessage(); }
    }

    private String processOutingResponse(MultipartBody body, String endpoint, String matchText) throws Exception {
        Request req = new Request.Builder().url(baseUrl + endpoint).post(body).header("X-Requested-With", "XMLHttpRequest").build();
        try (Response res = client.newCall(req).execute()) {
            String html = (res.body() != null) ? res.body().string() : "";
            if (html.contains("jsonBom")) {
                String error = org.jsoup.Jsoup.parse(html).select("input#jsonBom").val();
                return "FAILED: " + error;
            }
            if (html.contains(matchText)) return "SUCCESS: Outing Applied.";
            return "FAILED: VTOP rejected submission.";
        }
    }

    public boolean downloadAndCacheOutpass(String bookingId, boolean isWeekend, String regNo, File outputFile) {
        try {
            String endpoint = isWeekend ? "/hostel/downloadOutingForm/" : "/hostel/downloadLeavePass/";
            HttpUrl url = HttpUrl.parse(baseUrl + endpoint + bookingId).newBuilder()
                    .addQueryParameter("authorizedID", regNo).addQueryParameter("_csrf", csrfToken)
                    .addQueryParameter("x", getGmtTimestamp()).build();

            Request req = new Request.Builder().url(url).get().header("Referer", baseUrl + "/content?").build();
            try (Response res = client.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        fos.write(res.body().bytes());
                    }
                    return true;
                }
            }
        } catch (Exception e) { Log.e(TAG, "PDF Download Failed", e); }
        return false;
    }

    // =========================================================================
    // UTILITIES & NETWORK
    // =========================================================================

    private String executeStandardPost(String url, RequestBody body, String referer) {
        try {
            Request request = new Request.Builder().url(url).post(body).header("Referer", referer)
                    .header("X-Requested-With", "XMLHttpRequest").build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) return response.body().string();
            }
        } catch (Exception e) { Log.e(TAG, "Request Failed: " + url, e); }
        return null;
    }

    private String getGmtTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    @SuppressLint("CustomX509TrustManager")
    private static OkHttpClient.Builder getUnsafeOkHttpClientBuilder() {
        try {
            final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        @SuppressLint("TrustAllX509TrustManager")
                        @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @SuppressLint("TrustAllX509TrustManager")
                        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                    }
            };
            final javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            builder.addInterceptor(chain -> {
                Request r = chain.request().newBuilder().header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").build();
                return chain.proceed(r);
            });
            return builder;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public List<Map<String, String>> fetchSemesters() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            RequestBody body = new FormBody.Builder()
                    .add("verifyMenu", "true")
                    .add("authorizedID", username)
                    .add("_csrf", csrfToken)
                    .build();

            Request req = new Request.Builder()
                    .url(baseUrl + "/academics/common/StudentAttendance")
                    .post(body)
                    .header("Referer", baseUrl + "/content")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build();

            try (Response res = client.newCall(req).execute()) {
                String html = (res.body() != null) ? res.body().string() : "";

                // Add a log so you can confirm what VTOP is returning
                Log.d("VTOP_SEMESTERS", "Response length: " + html.length());
                //Log.d("VTOP_SEMESTERS", "Response body: " + html);

                Matcher m = Pattern.compile("<option\\s+value=\"([A-Z0-9]+)\"[^>]*>([^<]+)</option>").matcher(html);
                while (m.find()) {
                    if (!m.group(2).contains("Choose")) {
                        Map<String, String> map = new HashMap<>();
                        map.put("id", m.group(1));
                        map.put("name", m.group(2).trim());
                        list.add(map);
                    }
                }

                Log.d("VTOP_SEMESTERS", "Parsed " + list.size() + " semesters");
            }
        } catch (Exception e) {
            Log.e("VTOP_SEMESTERS", "Failed to fetch semesters", e);
        }
        return list;
    }
}