package com.vtop.network;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

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
import android.os.SystemClock;

import com.vtop.models.*;
import com.vtop.telemetry.Telemetry;
import com.vtop.telemetry.model.TelemetryModule;
import com.vtop.telemetry.model.TelemetryStatus;

@SuppressWarnings({"unused", "SpellCheckingInspection"})
public class VtopClient {

    private static final String TAG = "VTOP_CLIENT";
    private static final String BASE_URL = "https://vtop.vitap.ac.in/vtop";
    private static final long OTP_WAIT_TIMEOUT_SEC = 120;

    // The OkHttpClient is shared to reuse the connection pool and threads.
    // However, since CookieJar is immutable per OkHttpClient instance, we use
    // sharedClient.newBuilder().cookieJar(...).build() for each VtopClient.
    // This provides isolated cookies while sharing the underlying network resources.[cite: 24]
    private static OkHttpClient sharedClient = null;

    private final OkHttpClient client;
    private final String username;
    private final String password;
    private String csrfToken;
    private String authorizedId;

    private final SharedPrefsCookieJar cookieJarInstance;
    private final String sessionTag;

    public String getCsrfToken() { return this.csrfToken; }
    public String getUsername() { return this.username; }
    public String getAuthorizedId() { return this.authorizedId; }
    public OkHttpClient getClient() { return this.client; }

    public interface OtpResolver { void submit(String otpCode); void cancel(); }
    public interface StatusListener { void onStatusUpdate(String message); }
    public interface LoginListener extends StatusListener { void onOtpRequired(OtpResolver resolver); }

    public static class SharedPrefsCookieJar implements CookieJar {
        private static final String SEP = "|||";
        private final SharedPreferences prefs;
        private final Map<String, Cookie> memory = new ConcurrentHashMap<>();

        private static String cookieKey(Cookie c) { return c.name() + "@" + c.domain() + c.path(); }

        public SharedPrefsCookieJar(Context ctx, String preferenceName) {
            prefs = ctx.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                try {
                    String val = (String) entry.getValue();
                    if (val == null) continue;
                    String[] p = val.split(Pattern.quote(SEP));
                    if (p.length >= 8) {
                        Cookie.Builder builder = new Cookie.Builder().name(p[0]).value(p[1]).path(p[3]);
                        if (Boolean.parseBoolean(p[7])) builder.hostOnlyDomain(p[2]); else builder.domain(p[2]);
                        if (Boolean.parseBoolean(p[4])) builder.secure();
                        if (Boolean.parseBoolean(p[5])) builder.httpOnly();
                        long expiresAt = Long.parseLong(p[6]);
                        if (expiresAt != Long.MIN_VALUE) builder.expiresAt(expiresAt);
                        Cookie c = builder.build();
                        if (expiresAt == Long.MIN_VALUE || expiresAt >= System.currentTimeMillis()) memory.put(cookieKey(c), c);
                    }
                } catch (Exception ignored) {}
            }
        }

        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            SharedPreferences.Editor ed = prefs.edit();
            for (Cookie c : cookies) {
                memory.put(cookieKey(c), c);
                String s = c.name() + SEP + c.value() + SEP + c.domain() + SEP + c.path() + SEP + c.secure() + SEP + c.httpOnly() + SEP + c.expiresAt() + SEP + c.hostOnly();
                ed.putString(cookieKey(c), s);
            }
            ed.apply();
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> valid = new ArrayList<>();
            for (Cookie c : memory.values()) {
                if ((c.expiresAt() == Long.MIN_VALUE || c.expiresAt() >= System.currentTimeMillis()) && c.matches(url)) valid.add(c);
            }
            return valid;
        }

        public void clear() {
            memory.clear();
            prefs.edit().clear().apply();
        }
    }

    public VtopClient(Context context, String username, String password) {
        this(context, username, password, "DEFAULT");
    }

    public VtopClient(Context context, String username, String password, String cookieNamespace) {
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password.trim();
        this.authorizedId = this.username;
        this.sessionTag = cookieNamespace == null || cookieNamespace.trim().isEmpty() ? "DEFAULT" : cookieNamespace.trim().toUpperCase();

        String prefsName = "DEFAULT".equals(this.sessionTag) ? "VTOP_COOKIES" : "VTOP_COOKIES_" + this.sessionTag;
        this.cookieJarInstance = new SharedPrefsCookieJar(context.getApplicationContext(), prefsName);

        if (sharedClient == null) {
            sharedClient = getUnsafeOkHttpClientBuilder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build();
        }

        // Isolate the CookieJar per instance while sharing network resources[cite: 24]
        this.client = sharedClient.newBuilder()
                .cookieJar(this.cookieJarInstance)
                .build();

        this.csrfToken = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE).getString(getCsrfPrefKey(), "");
    }

    private String getCsrfPrefKey() {
        return "DEFAULT".equals(sessionTag) ? "CSRF_TOKEN" : "CSRF_TOKEN_" + sessionTag;
    }

    public void setAuthorizedId(String authorizedId) {
        if (authorizedId != null && !authorizedId.trim().isEmpty()) {
            this.authorizedId = authorizedId.trim();
        }
    }

    public void reinitializeSession(Context context) {
        if (cookieJarInstance != null) cookieJarInstance.clear();
        csrfToken = "";
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE).edit().remove(getCsrfPrefKey()).apply();
    }

    private void persistCsrf(Context ctx, String token) {
        if (token == null || token.isEmpty()) return;
        csrfToken = token;
        ctx.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE).edit().putString(getCsrfPrefKey(), token).apply();
    }

    private void extractAndSetAuthorizedId(String html) {
        if (html == null) return;
        Matcher m1 = Pattern.compile("id=\"authorizedIDX\" value=\"([^\"]+)\"").matcher(html);
        if (m1.find()) { this.authorizedId = m1.group(1).trim().toUpperCase(); return; }

        Matcher m2 = Pattern.compile("(?:let|var)\\s+id\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html);
        if (m2.find()) { this.authorizedId = m2.group(1).trim().toUpperCase(); return; }

        Matcher m3 = Pattern.compile("\\b\\d{2}[a-zA-Z]{3}\\d{4}\\b").matcher(html);
        if (m3.find()) { this.authorizedId = m3.group().toUpperCase(); }
    }

    public boolean autoLogin(Context context, LoginListener listener) throws Exception {
        if (listener != null) listener.onStatusUpdate("Checking session...");
        Request req = new Request.Builder().url(BASE_URL + "/content").get().build();
        try (Response res = client.newCall(req).execute()) {
            String body = res.body() != null ? res.body().string() : "";
            if (res.isSuccessful() && body.contains("Sign out")) {
                String token = extractToken(body);
                if (!token.isEmpty()) {
                    persistCsrf(context, token);
                    extractAndSetAuthorizedId(body);
                    if (listener != null) listener.onStatusUpdate("Session restored");
                    return true;
                }
            }
        } catch (Exception e) { throw new VtopException.SessionExpired("Network error."); }
        return performLogin(context, listener);
    }

    private boolean performLogin(Context context, LoginListener listener) throws Exception {
        if (listener != null) listener.onStatusUpdate("Opening VTOP...");
        Request initReq = new Request.Builder().url(BASE_URL + "/open/page").get().build();
        try (Response res = client.newCall(initReq).execute()) {
            String initHtml = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) throw new VtopException.SessionExpired("VTOP Server Error on init");
            csrfToken = extractToken(initHtml);
            if (csrfToken.isEmpty()) throw new VtopException.SessionExpired("CSRF token not found on open/page");
            persistCsrf(context, csrfToken);
        }

        if (listener != null) listener.onStatusUpdate("Fetching captcha...");
        RequestBody setupBody = new FormBody.Builder().add("_csrf", csrfToken).add("flag", "VTOP").build();
        Request setupReq = new Request.Builder().url(BASE_URL + "/prelogin/setup").post(setupBody).build();
        String loginHtml;
        try (Response res = client.newCall(setupReq).execute()) {
            loginHtml = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) throw new VtopException.SessionExpired("Prelogin setup failed");
        }

        String base64Captcha = "";
        String newCsrf = "";
        int attempts = 0;
        while (attempts < 5) {
            base64Captcha = extractCaptchaBase64(loginHtml);
            newCsrf = extractToken(loginHtml);
            if (base64Captcha != null && !base64Captcha.isEmpty()) break;
            attempts++;
            Thread.sleep(1000);
            try (Response retryRes = client.newCall(new Request.Builder().url(BASE_URL + "/login").get().build()).execute()) {
                loginHtml = retryRes.body() != null ? retryRes.body().string() : "";
            }
        }
        if (base64Captcha == null || base64Captcha.isEmpty()) throw new VtopException.SessionExpired("Captcha not found");
        if (newCsrf != null && !newCsrf.isEmpty()) persistCsrf(context, newCsrf);

        byte[] captchaBytes = android.util.Base64.decode(base64Captcha, android.util.Base64.DEFAULT);
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(captchaBytes, 0, captchaBytes.length);
        String captchaAnswer = new com.vtop.logic.CaptchaSolver(context).solve(bitmap);
        if (listener != null) listener.onStatusUpdate("Captcha solved");

        RequestBody loginBody = new FormBody.Builder().add("_csrf", this.csrfToken).add("username", username).add("password", password).add("captchaStr", captchaAnswer).build();
        Request loginReq = new Request.Builder().url(BASE_URL + "/login").post(loginBody).header("Referer", BASE_URL + "/login").header("Origin", "https://vtop.vitap.ac.in").build();

        try (Response res = client.newCall(loginReq).execute()) {
            String resp = res.body() != null ? res.body().string() : "";
            String finalUrl = res.request().url().toString();

            if (finalUrl.contains("/content") || resp.contains("Sign out")) {
                if (resp.contains("Unable to process") || resp.length() < 1500) throw new VtopException.WafBlocked("Session blocked by VTOP Firewall");
                persistCsrf(context, extractToken(resp));
                extractAndSetAuthorizedId(resp);
                if (listener != null) listener.onStatusUpdate("LOGIN_SUCCESS");
                return true;
            }

            if (resp.contains("Invalid Login") || resp.contains("User Id Not Available")) throw new VtopException.InvalidCredentials("Invalid username or password");
            if (resp.contains("maximum invalid log-in") || resp.contains("locked")) throw new VtopException.AuthenticationFailed("Account locked");

            boolean isOtpActive = Pattern.compile("var\\s+securityOtpPending\\s*=\\s*'?true'?").matcher(resp).find();
            if (isOtpActive) {
                if (listener != null) listener.onStatusUpdate("OTP_REQUIRED");
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                final String[] otpHolder = new String[1];
                listener.onOtpRequired(new OtpResolver() {
                    public void submit(String otp) { otpHolder[0] = otp; latch.countDown(); }
                    public void cancel() { latch.countDown(); }
                });
                if (!latch.await(OTP_WAIT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)) throw new VtopException.SessionExpired("OTP timed out.");
                String otpCode = otpHolder[0];
                if (otpCode == null || otpCode.trim().isEmpty()) throw new VtopException.SessionExpired("OTP cancelled");

                String otpCsrf = extractToken(resp);
                if (otpCsrf.isEmpty()) otpCsrf = this.csrfToken;
                RequestBody otpBody = new FormBody.Builder().add("_csrf", otpCsrf).add("otpCode", otpCode.trim()).build();
                Request otpReq = new Request.Builder().url(BASE_URL + "/validateSecurityOtp").post(otpBody).header("X-Requested-With", "XMLHttpRequest").header("Referer", BASE_URL + "/login").build();

                try (Response otpRes = client.newCall(otpReq).execute()) {
                    JSONObject json = new JSONObject(otpRes.body() != null ? otpRes.body().string() : "");
                    if (!"SUCCESS".equals(json.optString("status"))) throw new VtopException.LoginOtpIncorrect("OTP failed: " + json.optString("message"));
                    String redirectUrl = json.optString("redirectUrl");
                    if (redirectUrl.startsWith("/")) redirectUrl = redirectUrl.startsWith("/vtop/") ? "https://vtop.vitap.ac.in" + redirectUrl : BASE_URL + redirectUrl;
                    try (Response finalRes = client.newCall(new Request.Builder().url(redirectUrl).get().build()).execute()) {
                        String contentHtml = finalRes.body() != null ? finalRes.body().string() : "";
                        if (!contentHtml.contains("Sign out")) throw new VtopException.SessionExpired("Session not established after OTP");
                        persistCsrf(context, extractToken(contentHtml));
                        extractAndSetAuthorizedId(contentHtml);
                        if (listener != null) listener.onStatusUpdate("LOGIN_SUCCESS");
                        return true;
                    }
                }
            }
            throw new VtopException.CaptchaFailed("Captcha incorrect or session expired");
        }
    }

    public String fetchContentPageRawHtml() {
        try {
            Request request = new Request.Builder().url(BASE_URL + "/content").get().build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) return response.body().string();
            }
        } catch (Exception e) {}
        return "";
    }

    private String executeWafFetch(String endpoint, RequestBody body, String refererPath) {
        Request req = new Request.Builder().url(BASE_URL + endpoint).post(body).header("X-Requested-With", "XMLHttpRequest").header("Referer", BASE_URL + refererPath).header("Origin", "https://vtop.vitap.ac.in").build();
        try (Response res = client.newCall(req).execute()) {
            String html = res.body() != null ? res.body().string() : "";
            if (html.contains("VTOP Login") || html.contains("captchaStr")) return null;
            return html;
        } catch (Exception e) { return null; }
    }

    private String getGmtTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    private void activateMenu(String menuEndpoint) {
        try {
            RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).add("verifyMenu", "true").add("authorizedID", authorizedId).add("nocache", "@(" + System.currentTimeMillis() + ")").build();
            executeWafFetch(menuEndpoint, body, "/content");
        } catch (Exception ignored) {}
    }

    // ================= FETCH METHODS (WAF Compliant) =================

    public String fetchProfileRawHtml(StatusListener listener) {
        // Direct fetch without activateMenu (Menu click IS the fetch)
        Log.d(TAG, "[FETCH] Student Profile for ID: " + authorizedId);
        RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).add("verifyMenu", "true").add("authorizedID", authorizedId).add("nocache", "@(" + System.currentTimeMillis() + ")").build();
        return executeWafFetch("/studentsRecord/StudentProfileAllView", body, "/content");
    }

    public List<Map<String, String>> fetchSemesters() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            // Direct fetch without activateMenu
            RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).add("verifyMenu", "true").add("authorizedID", authorizedId).add("nocache", "@(" + System.currentTimeMillis() + ")").build();
            String html = executeWafFetch("/academics/common/StudentTimeTable", body, "/content");
            if (html != null) {
                Matcher m = Pattern.compile("<option\\s+value=\"([^\"]+)\"[^>]*>([^<]+)</option>").matcher(html);
                while (m.find()) {
                    String id = m.group(1); String name = m.group(2);
                    if (name != null && !name.toLowerCase().contains("choose") && !id.trim().isEmpty()) {
                        Map<String, String> map = new HashMap<>(); map.put("id", id); map.put("name", name.trim()); list.add(map);
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public String fetchTimetableRawHtml(String semId, StatusListener listener) {
        activateMenu("/academics/common/StudentTimeTable");
        RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).add("semesterSubId", semId).add("authorizedID", authorizedId).add("x", getGmtTimestamp()).build();
        return executeWafFetch("/processViewTimeTable", body, "/academics/common/StudentTimeTable");
    }

    public String fetchAttendanceRawHtml(String semId, StatusListener listener) {
        activateMenu("/academics/common/StudentAttendance");
        RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).add("semesterSubId", semId).add("authorizedID", authorizedId).add("x", getGmtTimestamp()).build();
        return executeWafFetch("/processViewStudentAttendance", body, "/academics/common/StudentAttendance");
    }

    public String fetchMarksRawHtml(String semId, StatusListener listener) {
        activateMenu("/examinations/StudentMarkView");
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", authorizedId).addFormDataPart("semesterSubId", semId).addFormDataPart("_csrf", csrfToken).build();
        return executeWafFetch("/examinations/doStudentMarkView", body, "/examinations/StudentMarkView");
    }

    public String fetchGradesRawHtml(String semId, StatusListener listener) {
        activateMenu("/examinations/examGradeView/StudentGradeView");
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", authorizedId).addFormDataPart("semesterSubId", semId).addFormDataPart("_csrf", csrfToken).build();
        return executeWafFetch("/examinations/examGradeView/doStudentGradeView", body, "/examinations/examGradeView/StudentGradeView");
    }

    public String fetchHistoryRawHtml(StatusListener listener) {
        activateMenu("/examinations/examGradeView/StudentGradeHistory");
        RequestBody body = new FormBody.Builder().add("verifyMenu", "true").add("authorizedID", authorizedId).add("_csrf", csrfToken).build();
        return executeWafFetch("/examinations/examGradeView/StudentGradeHistory", body, "/content");
    }

    public String fetchExamScheduleRawHtml(String semId, StatusListener listener) {
        activateMenu("/examinations/StudExamSchedule");
        RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", authorizedId).addFormDataPart("semesterSubId", semId).addFormDataPart("_csrf", csrfToken).build();
        return executeWafFetch("/examinations/doSearchExamScheduleForStudent", body, "/examinations/StudExamSchedule");
    }

    public String fetchAttendanceDetailRawHtml(String semId, String courseId, String courseType, String regNo, StatusListener listener) {
        RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).add("semesterSubId", semId).add("courseId", courseId).add("courseType", courseType).add("authorizedID", regNo).add("x", getGmtTimestamp()).build();
        return executeWafFetch("/processViewAttendanceDetail", body, "/processViewStudentAttendance");
    }

    public String fetchGeneralOutingRawHtml(String regNo, StatusListener listener) { return postSimpleWithAuth("/hostel/StudentGeneralOuting", regNo); }
    public String fetchWeekendOutingRawHtml(String regNo, StatusListener listener) { return postSimpleWithAuth("/hostel/StudentWeekendOuting", regNo); }

    private String postSimpleWithAuth(String endpoint, String authId) {
        try {
            RequestBody body = new FormBody.Builder().add("_csrf", csrfToken).add("verifyMenu", "true").add("authorizedID", authId).add("nocache", "@(" + System.currentTimeMillis() + ")").build();
            Request req = new Request.Builder().url(BASE_URL + endpoint).post(body).build();
            try (Response res = client.newCall(req).execute()) { return res.body() != null ? res.body().string() : null; }
        } catch (Exception e) { return null; }
    }

    public boolean submitGeneralOuting(String place, String purpose, String fromDate, String toDate, String fromTime, String toTime) {
        try {
            RequestBody applyBody = new FormBody.Builder().add("_csrf", csrfToken).add("verifyMenu", "true").add("authorizedID", authorizedId).build();
            String html = executeWafFetch("/hostel/StudentGeneralOuting", applyBody, "/content");
            Document doc = Jsoup.parse(html != null ? html : "");
            String name = doc.select("input#name").attr("value");
            String appNo = doc.select("input#applicationNo").attr("value");
            String gender = doc.select("input#gender").attr("value");
            String block = doc.select("input#hostelBlock").attr("value");
            String room = doc.select("input#roomNo").attr("value");

            if (appNo == null || appNo.isEmpty()) return false;
            String[] outParts = fromTime.split(":"); String[] inParts = toTime.split(":");
            String oh = String.format(Locale.US, "%02d", Integer.parseInt(outParts[0].trim())); String om = String.format(Locale.US, "%02d", Integer.parseInt(outParts[1].trim()));
            String ih = String.format(Locale.US, "%02d", Integer.parseInt(inParts[0].trim())); String im = String.format(Locale.US, "%02d", Integer.parseInt(inParts[1].trim()));

            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", authorizedId).addFormDataPart("LeaveId", "").addFormDataPart("regNo", authorizedId).addFormDataPart("name", name).addFormDataPart("applicationNo", appNo).addFormDataPart("gender", gender).addFormDataPart("hostelBlock", block).addFormDataPart("roomNo", room).addFormDataPart("placeOfVisit", place).addFormDataPart("purposeOfVisit", purpose).addFormDataPart("outDate", fromDate).addFormDataPart("outTimeHr", oh).addFormDataPart("outTimeMin", om).addFormDataPart("inDate", toDate).addFormDataPart("inTimeHr", ih).addFormDataPart("inTimeMin", im).addFormDataPart("_csrf", csrfToken).addFormDataPart("x", getGmtTimestamp()).addFormDataPart("upload_file", "", RequestBody.create(null, new byte[0])).build();
            String res = executeWafFetch("/hostel/saveGeneralOutingForm", body, "/content?");
            if (res != null) {
                Document resDoc = Jsoup.parse(res);
                if (resDoc.selectFirst("input#jsonBom") != null && !resDoc.selectFirst("input#jsonBom").attr("value").isEmpty()) return false;
                if (resDoc.selectFirst("input#success") != null && !resDoc.selectFirst("input#success").attr("value").isEmpty()) return true;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    public boolean submitWeekendOuting(String place, String purpose, String date, String time, String contact) {
        try {
            RequestBody applyBody = new FormBody.Builder().add("_csrf", csrfToken).add("verifyMenu", "true").add("authorizedID", authorizedId).build();
            String html = executeWafFetch("/hostel/StudentWeekendOuting", applyBody, "/content");
            Document doc = Jsoup.parse(html != null ? html : "");
            String name = doc.select("input#name").attr("value");
            String appNo = doc.select("input#applicationNo").attr("value");
            String gender = doc.select("input#gender").attr("value");
            String block = doc.select("input#hostelBlock").attr("value");
            String room = doc.select("input#roomNo").attr("value");
            String parentContact = doc.select("input#parentContactNumber").attr("value");

            if (appNo == null || appNo.isEmpty()) return false;
            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("authorizedID", authorizedId).addFormDataPart("BookingId", "").addFormDataPart("regNo", authorizedId).addFormDataPart("name", name).addFormDataPart("applicationNo", appNo).addFormDataPart("gender", gender).addFormDataPart("hostelBlock", block).addFormDataPart("roomNo", room).addFormDataPart("outPlace", place).addFormDataPart("purposeOfVisit", purpose).addFormDataPart("outingDate", date).addFormDataPart("outTime", time).addFormDataPart("contactNumber", contact).addFormDataPart("parentContactNumber", parentContact).addFormDataPart("_csrf", csrfToken).addFormDataPart("x=", getGmtTimestamp()).build();
            String res = executeWafFetch("/hostel/saveOutingForm", body, "/content?");
            if (res != null) {
                Document resDoc = Jsoup.parse(res);
                if (resDoc.selectFirst("input#jsonBom") != null && !resDoc.selectFirst("input#jsonBom").attr("value").isEmpty()) return false;
                if (resDoc.selectFirst("input#success") != null && !resDoc.selectFirst("input#success").attr("value").isEmpty()) return true;
            }
            return false;
        } catch (Exception e) { return false; }
    }

    public boolean downloadAndCacheOutpass(String bookingId, boolean isWeekend, String regNo, File outputFile) {
        try {
            String endpoint = isWeekend ? "/hostel/downloadOutingForm/" : "/hostel/downloadLeavePass/";
            okhttp3.HttpUrl parsedUrl = okhttp3.HttpUrl.parse(BASE_URL + endpoint + bookingId);
            if (parsedUrl == null) return false;

            // 1. Use FormBody (POST) to avoid the strict GET URL-encoding traps
            okhttp3.FormBody.Builder formBuilder = new okhttp3.FormBody.Builder()
                    .add("authorizedID", regNo)
                    .add("_csrf", csrfToken);

            okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(parsedUrl);

            // 2. Set the specific headers matching your Python script
            if (isWeekend) {
                // Weekend pass requires standard form submission origins
                requestBuilder.header("Referer", BASE_URL + "/hostel/StudentWeekendOuting")
                        .header("Origin", "https://vtop.vitap.ac.in");
            } else {
                // General pass requires the GMT timestamp and AJAX header
                formBuilder.add("x", getGmtTimestamp()); // Using your existing helper method!
                requestBuilder.header("X-Requested-With", "XMLHttpRequest");
                // Note: Your global interceptor will detect this header and automatically fix the 'Accept' and 'Sec-Fetch' headers for you!
            }

            requestBuilder.post(formBuilder.build());

            // 3. Execute and verify the PDF signature
            try (okhttp3.Response res = client.newCall(requestBuilder.build()).execute()) {
                if (res.isSuccessful() && res.body() != null) {
                    byte[] bytes = res.body().bytes();

                    if (bytes.length > 10) {
                        String header = new String(bytes, 0, 10);
                        if (header.contains("%PDF")) {
                            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
                                fos.write(bytes);
                            }
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    public boolean deleteOuting(String id, boolean isWeekend) {
        try {
            FormBody.Builder formBuilder = new FormBody.Builder().add("_csrf", csrfToken).add("authorizedID", authorizedId).add("x", getGmtTimestamp());
            if (isWeekend) { formBuilder.add("BookingId", id); } else { formBuilder.add("LeaveId", id); }
            String endpoint = isWeekend ? "/hostel/deleteBookingInfo" : "/hostel/deleteGeneralOutingInfo";
            String res = executeWafFetch(endpoint, formBuilder.build(), "/content?");
            return res != null;
        } catch (Exception e) { return false; }
    }
    public String fetchCalendarSemestersRawHtml() {
        try {
            Log.d(TAG, "[CALENDAR] Fetching dedicated semester list HTML...");
            RequestBody previewBody = new FormBody.Builder()
                    .add("verifyMenu", "true")
                    .add("authorizedID", authorizedId)
                    .add("_csrf", csrfToken)
                    .add("nocache", "@(new Date().getTime())")
                    .build();

            return executeWafFetch("/academics/common/CalendarPreview", previewBody, "/content?");
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch calendar semesters HTML", e);
            return null;
        }
    }

    public String fetchCalendarMonthsRawHtml(String semId, String classGroupId) {
        try {
            RequestBody body = new FormBody.Builder()
                    .add("_csrf", csrfToken)
                    .add("paramReturnId", "getListForSemester")
                    .add("semSubId", semId)
                    .add("classGroupId", classGroupId)
                    .add("authorizedID", authorizedId)
                    .add("x", getGmtTimestamp())
                    .build();

            return executeWafFetch("/getListForSemester", body, "/content?");
        } catch (Exception e) {
            Log.e(TAG, "Month HTML fetch failed", e);
            return null;
        }
    }
    public String fetchCalendarRawHtml(
            String semId,
            String calDate,
            String classGroupId
    ) {

        try {

            Log.d(TAG, "================================================");
            Log.d(TAG, "[CAL STEP 3] POST /academics/common/CalendarPreview");

            RequestBody previewBody = new FormBody.Builder()
                    .add("verifyMenu", "true")
                    .add("authorizedID", authorizedId)
                    .add("_csrf", csrfToken)
                    .add("nocache", "@(new Date().getTime())")
                    .build();

            String previewHtml = executeWafFetch(
                    "/academics/common/CalendarPreview",
                    previewBody,
                    "/content?"
            );

            Log.d(
                    TAG,
                    "[CAL STEP 3] Result: "
                            + (previewHtml != null ? "SUCCESS" : "FAILED")
            );

            Log.d(TAG, "[CAL STEP 4] POST /processViewCalendar");
            Log.d(TAG, "[CAL STEP 4] semId       = " + semId);
            Log.d(TAG, "[CAL STEP 4] calDate     = " + calDate);
            Log.d(TAG, "[CAL STEP 4] classGroup  = " + classGroupId);
            Log.d(TAG, "[CAL STEP 4] authId      = " + authorizedId);

            RequestBody calendarBody =
                    new FormBody.Builder()
                            .add("_csrf", csrfToken)
                            .add("calDate", calDate)
                            .add("semSubId", semId)
                            .add("classGroupId", classGroupId)
                            .add("authorizedID", authorizedId)
                            .add("x", getGmtTimestamp())
                            .build();

            String html = executeWafFetch(
                    "/processViewCalendar",
                    calendarBody,
                    "/content?"
            );

            if (html == null) {

                Log.e(
                        TAG,
                        "[CAL STEP 4] NULL RESPONSE"
                );

                Log.d(TAG, "================================================");

                return null;
            }

            Log.d(
                    TAG,
                    "[CAL STEP 4] Response Length: "
                            + html.length()
            );

            if (html.contains("calendar-table")) {

                Log.d(
                        TAG,
                        "[CAL STEP 5] SUCCESS: calendar-table detected"
                );

                Log.d(TAG, "================================================");

                return html;
            }

            Log.e(
                    TAG,
                    "[CAL STEP 5] INVALID HTML"
            );

            Log.e(
                    TAG,
                    "[CAL STEP 5] First 1000 chars:"
            );

            Log.e(
                    TAG,
                    html.substring(
                            0,
                            Math.min(1000, html.length())
                    )
            );

            Log.d(TAG, "================================================");

            return null;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "[CALENDAR FETCH FAILED]",
                    e
            );

            return null;
        }
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

    @SuppressLint("CustomX509TrustManager")
    private static OkHttpClient.Builder getUnsafeOkHttpClientBuilder() {
        try {
            final javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        @SuppressLint("TrustAllX509TrustManager") public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        @SuppressLint("TrustAllX509TrustManager") public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
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

                Request request = rb.build();

                long start = SystemClock.elapsedRealtime();

                if (Telemetry.INSTANCE.isEnabled()) {
                    Telemetry.INSTANCE.log(
                            TelemetryStatus.INFO,
                            "HTTP",
                            request.method() + " " + request.url().encodedPath(),
                            TelemetryModule.NETWORK,
                            java.util.Map.of(
                                    "method", request.method(),
                                    "url", request.url().toString()
                            )
                    );
                }

                try {

                    Response response = chain.proceed(request);

                    long duration = SystemClock.elapsedRealtime() - start;

                    if (Telemetry.INSTANCE.isEnabled()) {
                        Telemetry.INSTANCE.log(
                                response.isSuccessful()
                                        ? TelemetryStatus.SUCCESS
                                        : TelemetryStatus.WARNING,
                                "HTTP",
                                request.method() + " " + request.url().encodedPath(),
                                TelemetryModule.NETWORK,
                                java.util.Map.of(
                                        "method", request.method(),
                                        "url", request.url().toString(),
                                        "status", response.code(),
                                        "durationMs", duration
                                )
                        );
                    }

                    return response;

                } catch (Exception e) {

                    long duration = SystemClock.elapsedRealtime() - start;

                    if (Telemetry.INSTANCE.isEnabled()) {
                        Telemetry.INSTANCE.log(
                                TelemetryStatus.ERROR,
                                "HTTP",
                                e.getClass().getSimpleName(),
                                TelemetryModule.NETWORK,
                                java.util.Map.of(
                                        "method", request.method(),
                                        "url", request.url().toString(),
                                        "durationMs", duration,
                                        "exception", e.getClass().getSimpleName()
                                )
                        );
                    }

                    throw e;
                }
            });
            return builder;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}