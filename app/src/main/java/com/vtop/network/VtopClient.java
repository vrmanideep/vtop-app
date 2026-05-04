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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class VtopClient {

    private static final String TAG = "VTOP_CLIENT";
    private static final String BASE_URL = "https://vtop.vitap.ac.in/vtop";
    private static final long OTP_WAIT_TIMEOUT_SEC = 120;
    private static final int DEFAULT_RETRIES = 3;

    private static OkHttpClient sharedClient = null;
    private static SharedPrefsCookieJar cookieJarInstance = null;

    private final OkHttpClient client;
    private final String username;
    private final String password;
    private String csrfToken;

    public String getCsrfToken() { return this.csrfToken; }
    public String getUsername() { return this.username; }

    public OkHttpClient getClient() {
        return this.client;
    }

    // ================= INTERFACES =================
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

    // ================= COOKIE JAR =================
    public static class SharedPrefsCookieJar implements CookieJar {
        private static final String SEP = "|||";
        private final SharedPreferences prefs;
        private final Map<String, Cookie> memory = new ConcurrentHashMap<>();

        private static String cookieKey(Cookie c) {
            return c.name() + "@" + c.domain() + c.path();
        }

        private static String cookieKey(String name, String domain, String path) {
            return name + "@" + domain + path;
        }

        public SharedPrefsCookieJar(Context ctx) {
            prefs = ctx.getSharedPreferences("VTOP_COOKIES", Context.MODE_PRIVATE);

            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                try {
                    String val = (String) entry.getValue();
                    if (val == null) continue;

                    String[] p = val.split(Pattern.quote(SEP));
                    if (p.length >= 8) {
                        Cookie.Builder builder = new Cookie.Builder()
                                .name(p[0])
                                .value(p[1])
                                .path(p[3]);

                        if (Boolean.parseBoolean(p[7])) builder.hostOnlyDomain(p[2]);
                        else builder.domain(p[2]);

                        if (Boolean.parseBoolean(p[4])) builder.secure();
                        if (Boolean.parseBoolean(p[5])) builder.httpOnly();

                        long expiresAt = Long.parseLong(p[6]);
                        if (expiresAt != Long.MIN_VALUE) {
                            builder.expiresAt(expiresAt);
                        }

                        Cookie c = builder.build();
                        boolean isExpired = expiresAt != Long.MIN_VALUE && expiresAt < System.currentTimeMillis();

                        if (!isExpired) {
                            memory.put(cookieKey(c), c);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            SharedPreferences.Editor ed = prefs.edit();
            for (Cookie c : cookies) {
                memory.put(cookieKey(c), c);
                String s = c.name() + SEP + c.value() + SEP + c.domain() + SEP
                        + c.path() + SEP + c.secure() + SEP + c.httpOnly()
                        + SEP + c.expiresAt() + SEP + c.hostOnly();
                ed.putString(cookieKey(c), s);
            }
            ed.apply();
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> valid = new ArrayList<>();
            for (Cookie c : memory.values()) {
                boolean isExpired = c.expiresAt() != Long.MIN_VALUE && c.expiresAt() < System.currentTimeMillis();
                if (!isExpired && c.matches(url)) {
                    valid.add(c);
                }
            }
            return valid;
        }

        public void clear() {
            memory.clear();
            prefs.edit().clear().apply();
        }
    }

    // ================= CONSTRUCTOR =================
    public VtopClient(Context context, String username, String password) {
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password.trim();

        if (sharedClient == null) {
            cookieJarInstance = new SharedPrefsCookieJar(context.getApplicationContext());
            sharedClient = getUnsafeOkHttpClientBuilder()
                    .cookieJar(cookieJarInstance)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        this.client = sharedClient;

        SharedPreferences prefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE);
        this.csrfToken = prefs.getString("CSRF_TOKEN", "");
    }

    public void reinitializeSession(Context context) {
        Log.d(TAG, "Re-initializing session: clearing cookies and CSRF token");
        if (cookieJarInstance != null) cookieJarInstance.clear();
        csrfToken = "";
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
                .edit()
                .remove("CSRF_TOKEN")
                .apply();
    }

    private void persistCsrf(Context ctx, String token) {
        if (token == null || token.isEmpty()) return;
        csrfToken = token;
        ctx.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
                .edit()
                .putString("CSRF_TOKEN", token)
                .apply();
    }

    // ================= AUTO LOGIN =================
    public boolean autoLogin(Context context, LoginListener listener) throws Exception {
        if (listener != null) listener.onStatusUpdate("Checking session...");
        Log.d(TAG, "[AUTO] Session check: GET /content (csrf_cached=" + (!csrfToken.isEmpty()) + ")");

        Request req = new Request.Builder()
                .url(BASE_URL + "/content")
                .get()
                .build();

        try (Response res = client.newCall(req).execute()) {
            String body = res.body() != null ? res.body().string() : "";
            Log.d(TAG, "[AUTO] /content http=" + res.code() + " ok=" + res.isSuccessful()
                    + " bytes=" + body.length() + " url=" + res.request().url());
            boolean success = res.isSuccessful() && body.contains("Sign out");

            if (success) {
                String token = extractToken(body);
                if (!token.isEmpty()) {
                    persistCsrf(context, token);
                    if (listener != null) listener.onStatusUpdate("Session restored");
                    Log.d(TAG, "[AUTO] Session restored. csrf_persisted=true token_len=" + token.length());
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Network error during session check", e);
            throw new VtopException.SessionExpired("Network error. Please try syncing again.");
        }

        if (listener != null) listener.onStatusUpdate("SESSION_EXPIRED");
        Log.d(TAG, "[AUTO] Session not valid; falling back to full login.");
        return performLogin(context, listener);
    }


    // ================= LOGIN =================
    private boolean performLogin(Context context, LoginListener listener) throws Exception {
        if (listener != null) listener.onStatusUpdate("Opening VTOP...");
        Log.d(TAG, "[LOGIN] Begin login. Aligning with Rust flow...");

        Request initReq = new Request.Builder()
                .url(BASE_URL + "/open/page")
                .get()
                .build();

        String csrfToken;
        try (Response res = client.newCall(initReq).execute()) {
            String initHtml = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) throw new VtopException.SessionExpired("VTOP Server Error on init");
            csrfToken = extractToken(initHtml);
            if (csrfToken.isEmpty()) throw new VtopException.SessionExpired("CSRF token not found on open/page");
            persistCsrf(context, csrfToken);
        }

        if (listener != null) listener.onStatusUpdate("Fetching captcha...");

        RequestBody setupBody = new FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("flag", "VTOP")
                .build();

        Request setupReq = new Request.Builder()
                .url(BASE_URL + "/prelogin/setup")
                .post(setupBody)
                .build();

        String loginHtml;
        try (Response res = client.newCall(setupReq).execute()) {
            loginHtml = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) throw new VtopException.SessionExpired("Prelogin setup failed");
        }

        String base64Captcha = "";
        String newCsrf = "";
        int attempts = 0;
        int maxRetries = 5;

        while (attempts < maxRetries) {
            base64Captcha = extractCaptchaBase64(loginHtml);
            newCsrf = extractToken(loginHtml);

            if (base64Captcha != null && !base64Captcha.isEmpty()) {
                break;
            }

            attempts++;
            if (attempts < maxRetries) {
                Log.d(TAG, "[LOGIN] Captcha not found. Retrying... (" + attempts + "/" + maxRetries + ")");
                Thread.sleep(1000);

                Request retryReq = new Request.Builder()
                        .url(BASE_URL + "/login")
                        .get()
                        .build();

                try (Response retryRes = client.newCall(retryReq).execute()) {
                    loginHtml = retryRes.body() != null ? retryRes.body().string() : "";
                }
            }
        }

        if (base64Captcha == null || base64Captcha.isEmpty()) {
            throw new VtopException.SessionExpired("Captcha not found after setup redirect (" + maxRetries + " attempts)");
        }

        if (newCsrf != null && !newCsrf.isEmpty()) {
            this.csrfToken = newCsrf;
            persistCsrf(context, this.csrfToken);
        }

        byte[] captchaBytes = android.util.Base64.decode(base64Captcha, android.util.Base64.DEFAULT);
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(captchaBytes, 0, captchaBytes.length);
        String captchaAnswer = new com.vtop.logic.CaptchaSolver(context).solve(bitmap);
        if (listener != null) listener.onStatusUpdate("Captcha solved");

        RequestBody loginBody = new FormBody.Builder()
                .add("_csrf", this.csrfToken)
                .add("username", username)
                .add("password", password)
                .add("captchaStr", captchaAnswer)
                .build();

        Request loginReq = new Request.Builder()
                .url(BASE_URL + "/login")
                .post(loginBody)
                .header("Referer", BASE_URL + "/login")
                .header("Origin", "https://vtop.vitap.ac.in")
                .build();

        try (Response res = client.newCall(loginReq).execute()) {
            String resp = res.body() != null ? res.body().string() : "";
            String finalUrl = res.request().url().toString();

            Log.d(TAG, "[LOGIN][5] POST /login http=" + res.code() + " finalUrl=" + finalUrl);

            // SUCCESS CHECK
            if (finalUrl.contains("/content") || resp.contains("Sign out")) {
                if (resp.contains("Unable to process") || resp.length() < 1500) {
                    Log.e(TAG, "WAF Block detected. Printing HTML...");
                    printLargeLog(TAG, resp);
                    throw new VtopException.WafBlocked("Session blocked by VTOP Firewall (Unidentified Browser)");
                }

                persistCsrf(context, extractToken(resp));
                if (listener != null) listener.onStatusUpdate("LOGIN_SUCCESS");
                return true;
            }

            // ==========================================================
            // 1. CRITICAL ERROR PARSING (Checked before generic Captcha)
            // ==========================================================
            if (resp.contains("Invalid Login") || resp.contains("User Id Not Available")) {
                throw new VtopException.InvalidCredentials("Invalid username or password");
            }
            if (resp.contains("maximum invalid log-in") || resp.contains("locked")) {
                throw new VtopException.AuthenticationFailed("Account locked due to maximum invalid attempts");
            }

            // ==========================================================
            // 2. OTP REQUIRED CHECK
            // ==========================================================
            boolean isOtpActive = Pattern.compile("var\\s+securityOtpPending\\s*=\\s*'?true'?").matcher(resp).find();

            if (isOtpActive) {
                if (listener != null) listener.onStatusUpdate("OTP_REQUIRED");
                Log.d(TAG, "[LOGIN][OTP] OTP required by server.");

                if (listener == null) throw new VtopException.LoginOtpRequired("OTP required but no UI listener is available");

                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                final String[] otpHolder = new String[1];

                listener.onOtpRequired(new OtpResolver() {
                    @Override public void submit(String otp) { otpHolder[0] = otp; latch.countDown(); }
                    @Override public void cancel() { latch.countDown(); }
                });

                boolean received = latch.await(OTP_WAIT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS);
                if (!received) throw new VtopException.SessionExpired("OTP timed out.");

                String otpCode = otpHolder[0];
                if (otpCode == null || otpCode.trim().isEmpty()) throw new VtopException.SessionExpired("OTP cancelled");

                String otpCsrf = extractToken(resp);
                if (otpCsrf.isEmpty()) otpCsrf = this.csrfToken;

                RequestBody otpBody = new FormBody.Builder()
                        .add("_csrf", otpCsrf)
                        .add("otpCode", otpCode.trim())
                        .build();

                Request otpReq = new Request.Builder()
                        .url(BASE_URL + "/validateSecurityOtp")
                        .post(otpBody)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", BASE_URL + "/login")
                        .build();

                try (Response otpRes = client.newCall(otpReq).execute()) {
                    String otpRespStr = otpRes.body() != null ? otpRes.body().string() : "";
                    JSONObject json = new JSONObject(otpRespStr);

                    if (!"SUCCESS".equals(json.optString("status"))) {
                        throw new VtopException.LoginOtpIncorrect("OTP failed: " + json.optString("message"));
                    }

                    String redirectUrl = json.optString("redirectUrl");
                    if (redirectUrl.startsWith("/")) {
                        redirectUrl = redirectUrl.startsWith("/vtop/")
                                ? "https://vtop.vitap.ac.in" + redirectUrl
                                : BASE_URL + redirectUrl;
                    }

                    Request contentReq = new Request.Builder().url(redirectUrl).get().build();
                    try (Response finalRes = client.newCall(contentReq).execute()) {
                        String contentHtml = finalRes.body() != null ? finalRes.body().string() : "";
                        if (!contentHtml.contains("Sign out")) throw new VtopException.SessionExpired("Session not established after OTP");
                        persistCsrf(context, extractToken(contentHtml));
                        if (listener != null) listener.onStatusUpdate("LOGIN_SUCCESS");
                        return true;
                    }
                }
            }

            // ==========================================================
            // 3. CAPTCHA / FALLBACK ERROR PARSING
            // ==========================================================
            // If the login wasn't successful, wasn't a bad password, and wasn't OTP...
            // It is safe to assume the Captcha was rejected or the session disconnected mid-flight.
            Log.e(TAG, "Captcha or unknown error. Final URL: " + finalUrl);
            throw new VtopException.CaptchaFailed("Captcha incorrect or session expired");
        }
    }

    private String fetchInitialCsrfWithRetries(int retries) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= retries; attempt++) {
            Log.d(TAG, "[LOGIN][1] Fetch CSRF attempt " + attempt + "/" + retries);
            try {
                Request baseReq = new Request.Builder().url(BASE_URL).get().build();
                try (Response res = client.newCall(baseReq).execute()) {
                    String html = res.body() != null ? res.body().string() : "";
                    Log.d(TAG, "[LOGIN][1] GET base http=" + res.code() + " bytes=" + html.length() + " url=" + res.request().url());
                    String csrf = extractToken(html);
                    if (!csrf.isEmpty()) {
                        Log.d(TAG, "[LOGIN][1] CSRF found on attempt=" + attempt + " len=" + csrf.length());
                        return csrf;
                    }
                    lastError = new Exception("Initial CSRF not found in response");
                }
            } catch (Exception e) {
                lastError = e;
                Log.e(TAG, "[LOGIN][1] CSRF fetch attempt failed: " + e.getMessage(), e);
            }
            if (attempt < retries) Thread.sleep(1000);
        }
        throw new VtopException.SessionExpired("Failed to fetch initial CSRF after " + retries + " attempts");
    }

    private void preLoginOrThrow(String csrfToken) throws Exception {
        RequestBody setupBody = new FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("flag", "VTOP")
                .build();
        Request setupReq = new Request.Builder()
                .url(BASE_URL + "/prelogin/setup")
                .post(setupBody)
                .header("Referer", BASE_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .build();
        try (Response res = client.newCall(setupReq).execute()) {
            String resp = res.body() != null ? res.body().string() : "";
            Log.d(TAG, "[LOGIN][2] POST prelogin/setup http=" + res.code() + " bytes=" + resp.length() + " success=" + res.isSuccessful());
            if (!res.isSuccessful()) {
                throw new VtopException.SessionExpired("Pre-login failed with status " + res.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "[LOGIN][2] Pre-login request failed: " + e.getMessage(), e);
            throw new VtopException.SessionExpired("Pre-login request failed: " + e.getMessage());
        }
    }

    private LoginPageData fetchLoginPageAndCaptchaWithRetries(int retries) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                Request loginPageReq = new Request.Builder()
                        .url(BASE_URL + "/login")
                        .get()
                        .header("Referer", BASE_URL)
                        .build();
                try (Response res = client.newCall(loginPageReq).execute()) {
                    String loginHtml = res.body() != null ? res.body().string() : "";
                    String captcha = extractCaptchaBase64(loginHtml);
                    Log.d(TAG, "[LOGIN][3] GET /login attempt=" + attempt + "/" + retries + " http=" + res.code()
                            + " bytes=" + loginHtml.length() + " captcha_present=" + (captcha != null && !captcha.isEmpty()));
                    if (captcha != null && !captcha.isEmpty()) {
                        return new LoginPageData(loginHtml, captcha);
                    }
                    lastError = new Exception("Captcha not found in login response");
                }
            } catch (Exception e) {
                lastError = e;
                Log.e(TAG, "[LOGIN][3] Captcha fetch attempt failed: " + e.getMessage(), e);
            }
            if (attempt < retries) Thread.sleep(1000);
        }
        throw new VtopException.SessionExpired("Failed to fetch captcha page after " + retries + " attempts");
    }

    private static class LoginPageData {
        final String loginHtml;
        final String base64Captcha;

        LoginPageData(String loginHtml, String base64Captcha) {
            this.loginHtml = loginHtml;
            this.base64Captcha = base64Captcha;
        }
    }

    // ================= FETCH METHODS =================
    private void initializeTimetableContext() {
        try {
            Request req = new Request.Builder()
                    .url(BASE_URL + "/academics/common/StudentTimeTable")
                    .get()
                    .build();
            client.newCall(req).execute().close();
        } catch (Exception ignored) {}
    }
    public String fetchProfileRawHtml(StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Profile...");
        Log.d(TAG, "[FETCH] Student Profile");

        RequestBody body = new FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("verifyMenu", "true")
                .add("authorizedID", username)
                .add("nocache", "@(" + System.currentTimeMillis() + ")")
                .build();

        return executeWafFetch("/studentsRecord/StudentProfileAllView", body, "/content");
    }

    public List<Map<String, String>> fetchSemesters() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            Log.d(TAG, "[FETCH] Fetching semesters... csrf_len=" + (csrfToken == null ? 0 : csrfToken.length()));

            // Add the mandatory VTOP menu parameters to bypass the 404
            RequestBody body = new FormBody.Builder()
                    .add("_csrf", csrfToken)
                    .add("verifyMenu", "true")
                    .add("authorizedID", this.username) // Your registration number
                    .add("nocache", "@(" + System.currentTimeMillis() + ")")
                    .build();

            Request req = new Request.Builder()
                    .url(BASE_URL + "/academics/common/StudentTimeTable")
                    .post(body)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", BASE_URL + "/content")
                    .build();

            try (Response res = client.newCall(req).execute()) {
                String html = res.body() != null ? res.body().string() : "";

                if (!res.isSuccessful()) {
                    Log.e(TAG, "[FETCH] Semesters request failed with status: " + res.code());
                }

                // Parse the options from the returned dropdown HTML
                Matcher m = Pattern.compile("<option\\s+value=\"([^\"]+)\"[^>]*>([^<]+)</option>").matcher(html);
                while (m.find()) {
                    String id = m.group(1);
                    String name = m.group(2);

                    // Exclude the default "Choose Semester" placeholder
                    if (name != null && !name.toLowerCase().contains("choose") && !id.trim().isEmpty()) {
                        Map<String, String> map = new HashMap<>();
                        map.put("id", id);
                        map.put("name", name.trim());
                        list.add(map);
                    }
                }
                Log.d(TAG, "[FETCH] Successfully parsed " + list.size() + " semesters.");
            }
        } catch (Exception e) {
            Log.e(TAG, "[FETCH] Error fetching semesters", e);
        }
        return list;
    }

    // =========================================================================
    // WAF-ARMORED FETCH HELPER
    // =========================================================================
    private String executeWafFetch(String endpoint, RequestBody body, String refererPath) {
        Request req = new Request.Builder()
                .url(BASE_URL + endpoint)
                .post(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", BASE_URL + refererPath)
                .header("Origin", "https://vtop.vitap.ac.in")
                .build();

        try (Response res = client.newCall(req).execute()) {
            String html = res.body() != null ? res.body().string() : "";

            if (html.contains("VTOP Login") || html.contains("captchaStr")) {
                Log.e(TAG, "[FETCH] Failed for " + endpoint + ": Session appears expired.");
                return null;
            }

            Log.d(TAG, "[FETCH] Success " + endpoint + " | Bytes: " + html.length());
            return html;
        } catch (Exception e) {
            Log.e(TAG, "[FETCH] Request failed for " + endpoint, e);
            return null;
        }
    }

    private String getGmtTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    // =========================================================================
    // REWRITTEN FETCH METHODS
    // =========================================================================

    public String fetchTimetableRawHtml(String semId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Timetable...");
        Log.d(TAG, "[FETCH] timetable sem=" + (semId == null ? "null" : semId));

        RequestBody body = new FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("semesterSubId", semId)
                .add("authorizedID", username)
                .add("x", getGmtTimestamp())
                .build();

        return executeWafFetch("/processViewTimeTable", body, "/academics/common/StudentTimeTable");
    }

    public String fetchAttendanceRawHtml(String semId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Attendance...");
        Log.d(TAG, "[FETCH] attendance sem=" + (semId == null ? "null" : semId));

        RequestBody body = new FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("semesterSubId", semId)
                .add("authorizedID", username)
                .add("x", getGmtTimestamp())
                .build();


        return executeWafFetch("/processViewStudentAttendance", body, "/academics/common/StudentAttendance");
    }

    public String fetchMarksRawHtml(String semId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Marks...");
        Log.d(TAG, "[FETCH] marks sem=" + (semId == null ? "null" : semId));

        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("authorizedID", username)
                .addFormDataPart("semesterSubId", semId)
                .addFormDataPart("_csrf", csrfToken)
                .build();

        return executeWafFetch("/examinations/doStudentMarkView", body, "/examinations/StudentMarkView");
    }

    public String fetchGradesRawHtml(String semId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Grades...");
        Log.d(TAG, "[FETCH] grades sem=" + (semId == null ? "null" : semId));

        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("authorizedID", username)
                .addFormDataPart("semesterSubId", semId)
                .addFormDataPart("_csrf", csrfToken)
                .build();

        return executeWafFetch("/examinations/examGradeView/doStudentGradeView", body, "/examinations/examGradeView/StudentGradeView");
    }

    public String fetchHistoryRawHtml(StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching History...");
        Log.d(TAG, "[FETCH] history");

        try {
            Request refreshReq = new Request.Builder()
                    .url(BASE_URL + "/content")
                    .get()
                    .build();

            try (Response res = client.newCall(refreshReq).execute()) {
                String html = res.body() != null ? res.body().string() : "";

                String newToken = extractToken(html);
                if (newToken != null && !newToken.isEmpty()) {
                    csrfToken = newToken;
                } else {
                    Log.e(TAG, "Failed to refresh CSRF for history");
                }
            }

            RequestBody body = new FormBody.Builder()
                    .add("verifyMenu", "true")
                    .add("authorizedID", username)
                    .add("_csrf", csrfToken)
                    .build();

            String html = executeWafFetch(
                    "/examinations/examGradeView/StudentGradeHistory",
                    body,
                    "/content"
            );

            return html;

        } catch (Exception e) {
            Log.e(TAG, "History fetch failed", e);
            return null;
        }
    }

    public String fetchExamScheduleRawHtml(String semId, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Exams...");
        Log.d(TAG, "[FETCH] exams sem=" + (semId == null ? "null" : semId));

        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("authorizedID", username)
                .addFormDataPart("semesterSubId", semId)
                .addFormDataPart("_csrf", csrfToken)
                .build();

        return executeWafFetch("/examinations/doSearchExamScheduleForStudent", body, "/examinations/common/ExamScheduleForStudent");
    }

    public String fetchAttendanceDetailRawHtml(String semId, String courseId, String courseType, String regNo, StatusListener listener) {
        if (listener != null) listener.onStatusUpdate("Fetching Details...");
        Log.d(TAG, "[FETCH] attendance detail sem=" + semId + " courseId=" + courseId);

        RequestBody body = new FormBody.Builder()
                .add("_csrf", csrfToken)
                .add("semesterSubId", semId)
                .add("courseId", courseId)
                .add("courseType", courseType)
                .add("authorizedID", regNo)
                .add("x", getGmtTimestamp())
                .build();

        return executeWafFetch("/processViewAttendanceDetail", body, "/processViewStudentAttendance");
    }

    public String fetchGeneralOutingRawHtml(String regNo, StatusListener listener) {
        return postSimpleWithAuth("/hostel/StudentGeneralOuting", regNo);
    }

    public String fetchWeekendOutingRawHtml(String regNo, StatusListener listener) {
        return postSimpleWithAuth("/hostel/StudentWeekendOuting", regNo);
    }

    public boolean submitGeneralOuting(String place, String purpose, String fromDate, String toDate, String fromTime, String toTime) {
        Log.d(TAG, "[SUBMIT] General Outing Request");
        try {
            RequestBody applyBody = new FormBody.Builder()
                    .add("_csrf", csrfToken)
                    .add("verifyMenu", "true")
                    .add("authorizedID", username)
                    .build();
            String html = executeWafFetch("/hostel/StudentGeneralOuting", applyBody, "/content");

            Document doc = Jsoup.parse(html != null ? html : "");
            String name = doc.select("input#name").attr("value");
            String appNo = doc.select("input#applicationNo").attr("value");
            String gender = doc.select("input#gender").attr("value");
            String block = doc.select("input#hostelBlock").attr("value");
            String room = doc.select("input#roomNo").attr("value");

            if (appNo == null || appNo.isEmpty()) {
                Log.e(TAG, "Application No not found for General Outing.");
                return false;
            }

            String[] outParts = fromTime.split(":");
            String[] inParts = toTime.split(":");
            String oh = String.format(Locale.US, "%02d", Integer.parseInt(outParts[0].trim()));
            String om = String.format(Locale.US, "%02d", Integer.parseInt(outParts[1].trim()));
            String ih = String.format(Locale.US, "%02d", Integer.parseInt(inParts[0].trim()));
            String im = String.format(Locale.US, "%02d", Integer.parseInt(inParts[1].trim()));

            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("authorizedID", username)
                    .addFormDataPart("LeaveId", "")
                    .addFormDataPart("regNo", username)
                    .addFormDataPart("name", name)
                    .addFormDataPart("applicationNo", appNo)
                    .addFormDataPart("gender", gender)
                    .addFormDataPart("hostelBlock", block)
                    .addFormDataPart("roomNo", room)
                    .addFormDataPart("placeOfVisit", place)
                    .addFormDataPart("purposeOfVisit", purpose)
                    .addFormDataPart("outDate", fromDate)
                    .addFormDataPart("outTimeHr", oh)
                    .addFormDataPart("outTimeMin", om)
                    .addFormDataPart("inDate", toDate)
                    .addFormDataPart("inTimeHr", ih)
                    .addFormDataPart("inTimeMin", im)
                    .addFormDataPart("_csrf", csrfToken)
                    .addFormDataPart("x", getGmtTimestamp())
                    .addFormDataPart("upload_file", "", RequestBody.create(null, new byte[0]))
                    .build();

            String res = executeWafFetch("/hostel/saveGeneralOutingForm", body, "/content?");

            if (res != null) {
                Document resDoc = Jsoup.parse(res);

                // 1. Check for Explicit Server Error
                Element errorMsg = resDoc.selectFirst("input#jsonBom");
                if (errorMsg != null && !errorMsg.attr("value").isEmpty()) {
                    Log.e(TAG, "VTOP Error: " + errorMsg.attr("value"));
                    return false;
                }

                // 2. Strict Check for Explicit Success
                Element successMsg = resDoc.selectFirst("input#success");
                if (successMsg != null && !successMsg.attr("value").isEmpty()) {
                    Log.d(TAG, "VTOP Accepted Submission: " + successMsg.attr("value"));
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            Log.e(TAG, "General Submit Error: " + e.getMessage());
            return false;
        }
    }

    public boolean submitWeekendOuting(String place, String purpose, String date, String time, String contact) {
        Log.d(TAG, "[SUBMIT] Weekend Outing Request");
        try {
            RequestBody applyBody = new FormBody.Builder()
                    .add("_csrf", csrfToken)
                    .add("verifyMenu", "true")
                    .add("authorizedID", username)
                    .build();
            String html = executeWafFetch("/hostel/StudentWeekendOuting", applyBody, "/content");

            Document doc = Jsoup.parse(html != null ? html : "");
            String name = doc.select("input#name").attr("value");
            String appNo = doc.select("input#applicationNo").attr("value");
            String gender = doc.select("input#gender").attr("value");
            String block = doc.select("input#hostelBlock").attr("value");
            String room = doc.select("input#roomNo").attr("value");
            String parentContact = doc.select("input#parentContactNumber").attr("value");

            if (appNo == null || appNo.isEmpty()) {
                Log.e(TAG, "Application No not found. Form blocked by VTOP?");
                return false;
            }

            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("authorizedID", username)
                    .addFormDataPart("BookingId", "")
                    .addFormDataPart("regNo", username)
                    .addFormDataPart("name", name)
                    .addFormDataPart("applicationNo", appNo)
                    .addFormDataPart("gender", gender)
                    .addFormDataPart("hostelBlock", block)
                    .addFormDataPart("roomNo", room)
                    .addFormDataPart("outPlace", place)
                    .addFormDataPart("purposeOfVisit", purpose)
                    .addFormDataPart("outingDate", date)
                    .addFormDataPart("outTime", time)
                    .addFormDataPart("contactNumber", contact)
                    .addFormDataPart("parentContactNumber", parentContact)
                    .addFormDataPart("_csrf", csrfToken)
                    .addFormDataPart("x=", getGmtTimestamp())
                    .build();

            String res = executeWafFetch("/hostel/saveOutingForm", body, "/content?");

            if (res != null) {
                Document resDoc = Jsoup.parse(res);

                // 1. Check for Explicit Server Error
                Element errorMsg = resDoc.selectFirst("input#jsonBom");
                if (errorMsg != null && !errorMsg.attr("value").isEmpty()) {
                    Log.e(TAG, "VTOP Error: " + errorMsg.attr("value"));
                    return false;
                }

                // 2. Strict Check for Explicit Success
                Element successMsg = resDoc.selectFirst("input#success");
                if (successMsg != null && !successMsg.attr("value").isEmpty()) {
                    Log.d(TAG, "VTOP Accepted Submission: " + successMsg.attr("value"));
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Weekend Submit Error: " + e.getMessage());
            return false;
        }
    }
    public boolean downloadAndCacheOutpass(String bookingId, boolean isWeekend, String regNo, File outputFile) {
        try {
            String endpoint = isWeekend ? "/hostel/downloadOutingForm/" : "/hostel/downloadLeavePass/";
            HttpUrl parsedUrl = HttpUrl.parse(BASE_URL + endpoint + bookingId);
            if (parsedUrl == null) return false;

            HttpUrl url = parsedUrl.newBuilder()
                    .addQueryParameter("authorizedID", regNo)
                    .addQueryParameter("_csrf", csrfToken)
                    .addQueryParameter("x", String.valueOf(System.currentTimeMillis()))
                    .build();

            Request req = new Request.Builder().url(url).get().build();
            try (Response res = client.newCall(req).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        fos.write(res.body().bytes());
                    }
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public boolean deleteOuting(String id, boolean isWeekend) {
        Log.d(TAG, "[DELETE] Outing Request: " + id + " Weekend: " + isWeekend);
        try {
            FormBody.Builder formBuilder = new FormBody.Builder()
                    .add("_csrf", csrfToken)
                    .add("authorizedID", username)
                    .add("x", getGmtTimestamp());

            String endpoint;
            if (isWeekend) {
                formBuilder.add("BookingId", id);
                endpoint = "/hostel/deleteBookingInfo";
            } else {
                formBuilder.add("LeaveId", id);
                endpoint = "/hostel/deleteGeneralOutingInfo";
            }

            RequestBody body = formBuilder.build();
            String res = executeWafFetch(endpoint, body, "/content?");

            if (res != null) {
                Log.d(TAG, "VTOP Accepted Delete Request");
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Delete Error: " + e.getMessage());
            return false;
        }
    }

    // ================= HELPERS =================
    private String postWithCsrf(String endpoint, String semId) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("_csrf", csrfToken)
                    .add("semesterSubId", semId)
                    .build();
            Request req = new Request.Builder().url(BASE_URL + endpoint).post(body).build();
            try (Response res = client.newCall(req).execute()) {
                String html = res.body() != null ? res.body().string() : "";
                Log.d(TAG, "[POST] " + endpoint + " http=" + res.code() + " bytes=" + html.length()
                        + " sessionExpired=" + (html.contains("VTOP Login") || html.contains("captchaStr")));
                if (html.contains("VTOP Login") || html.contains("captchaStr")) {
                    return null;
                }
                return html;
            }
        } catch (Exception e) { return null; }
    }

    private String postSimple(String endpoint) {
        try {
            RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).build();
            Request req = new Request.Builder().url(BASE_URL + endpoint).post(body).build();
            try (Response res = client.newCall(req).execute()) {
                return res.body() != null ? res.body().string() : null;
            }
        } catch (Exception e) { return null; }
    }

    private String postSimpleWithAuth(String endpoint, String authId) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("_csrf", csrfToken)
                    .add("verifyMenu", "true")
                    .add("authorizedID", authId)
                    .add("nocache", "@(" + System.currentTimeMillis() + ")")
                    .build();
            Request req = new Request.Builder().url(BASE_URL + endpoint).post(body).build();
            try (Response res = client.newCall(req).execute()) {
                return res.body() != null ? res.body().string() : null;
            }
        } catch (Exception e) { return null; }
    }

    private String extractToken(String html) {
        if (html == null) return "";
        Matcher m = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private String extractCaptchaBase64(String html) {
        if (html == null) return "";
        Matcher m = Pattern.compile("data:image/[^;]+;base64,([^\"']+)").matcher(html);
        return m.find() ? m.group(1) : "";
    }

    // ================= SSL & WAF BYPASS SHIELD =================
    @SuppressLint("CustomX509TrustManager")
    private static OkHttpClient.Builder getUnsafeOkHttpClientBuilder() {
        try {
            final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        @SuppressLint("TrustAllX509TrustManager")
                        public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        @SuppressLint("TrustAllX509TrustManager")
                        public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                    }
            };

            final javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            builder.addNetworkInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder rb = original.newBuilder()
                        .removeHeader("User-Agent")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        .addHeader("Accept-Language", "en-US,en;q=0.9")
                        .addHeader("Upgrade-Insecure-Requests", "1")
                        .addHeader("sec-ch-ua", "\"Google Chrome\";v=\"123\", \"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"123\"")
                        .addHeader("sec-ch-ua-mobile", "?0")
                        .addHeader("sec-ch-ua-platform", "\"Windows\"")
                        .addHeader("Sec-Fetch-Dest", "document")
                        .addHeader("Sec-Fetch-Mode", "navigate")
                        .addHeader("Sec-Fetch-Site", "same-origin")
                        .addHeader("Sec-Fetch-User", "?1")
                        .removeHeader("Connection")
                        .addHeader("Connection", "keep-alive");

                if ("XMLHttpRequest".equals(original.header("X-Requested-With"))) {
                    rb.removeHeader("Accept");
                    rb.addHeader("Accept", "*/*");
                    rb.header("Sec-Fetch-Mode", "cors");
                    rb.header("Sec-Fetch-Dest", "empty");
                }

                return chain.proceed(rb.build());
            });

            return builder;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void printLargeLog(String tag, String content) {
        if (content == null) return;
        if (content.length() > 4000) {
            Log.d(tag, content.substring(0, 4000));
            printLargeLog(tag, content.substring(4000));
        } else {
            Log.d(tag, content);
        }
    }
}