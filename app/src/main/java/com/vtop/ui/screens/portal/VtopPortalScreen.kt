package com.vtop.ui.screens.portal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vtop.network.VtopClient
import com.vtop.ui.core.AppBridge
import com.vtop.utils.GmailOtpExtractor
import com.vtop.utils.NotificationHelper
import com.vtop.utils.Vault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

private const val VTOP_BASE = "https://vtop.vitap.ac.in"
private const val VTOP_OPEN_PAGE = "$VTOP_BASE/vtop/open/page"
private const val VTOP_CONTENT = "$VTOP_BASE/vtop/content"
private const val VTOP_LOGIN = "$VTOP_BASE/vtop/login"

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Mobile Safari/537.36"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VtopPortalScreen(
    vtopClient: VtopClient,
    onBack: () -> Unit
    ){
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageTitle by remember { mutableStateOf("VTOP") }
    var isLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var sessionError by remember { mutableStateOf<String?>(null) }

    // =========================================================
    // DESKTOP MODE
    // =========================================================

    var desktopMode by remember {
        mutableStateOf(true)
    }

    var expandedMenu by remember {
        mutableStateOf(false)
    }

    // =========================================================
    // APPLY DESKTOP MODE
    // =========================================================

    fun applyDesktopMode(webView: WebView?) {

        if (webView == null) return

        val settings = webView.settings

        if (desktopMode) {

            // =========================================
            // CHROME-LIKE DESKTOP MODE
            // =========================================

            settings.userAgentString = DESKTOP_UA

            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            settings.layoutAlgorithm =
                WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)

            webView.setInitialScale(1)

        } else {

            // =========================================
            // MOBILE MODE
            // =========================================

            settings.userAgentString = MOBILE_UA

            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false

            settings.layoutAlgorithm =
                WebSettings.LayoutAlgorithm.NORMAL

            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)

            webView.setInitialScale(100)
        }

        webView.reload()
    }

    // =========================================================
    // LOGIN FLOW
    // =========================================================

    suspend fun forceLogin() {

        isLoading = true
        sessionError = null

        try {

            val success = withContext(Dispatchers.IO) {

                var loginSuccess = false
                var attempts = 0
                val maxRetries = 3

                while (attempts < maxRetries && !loginSuccess) {

                    try {

                        loginSuccess = vtopClient.autoLogin(
                            context,
                            object : VtopClient.LoginListener {

                                override fun onStatusUpdate(message: String) {}

                                override fun onOtpRequired(
                                    resolver: VtopClient.OtpResolver
                                ) {

                                    scope.launch(Dispatchers.IO) {
                                        // Capture the exact moment the WebView forced a login
                                        val otpRequestedTime = System.currentTimeMillis()

                                        val googleEmail =
                                            Vault.getGoogleEmail(context)

                                        var autoExtractedOtp: String? = null

                                        // =====================================
                                        // AUTO OTP EXTRACTION
                                        // =====================================

                                        if (googleEmail.isNotBlank()) {

                                            withContext(Dispatchers.Main) {

                                                Toast.makeText(
                                                    context,
                                                    "Reading OTP from Gmail...",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }

                                            try {

                                                delay(3000)

                                                autoExtractedOtp =
                                                    GmailOtpExtractor
                                                        .getLatestVtopOtp(
                                                            context,
                                                            googleEmail,
                                                            otpRequestedTime // <-- Pass the timestamp here
                                                        )

                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }

                                        // =====================================
                                        // SUBMIT OTP
                                        // =====================================

                                        if (!autoExtractedOtp.isNullOrBlank()) {

                                            withContext(Dispatchers.Main) {

                                                Toast.makeText(
                                                    context,
                                                    "OTP Auto-filled! Resuming...",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }

                                            resolver.submit(autoExtractedOtp)

                                        } else {

                                            withContext(Dispatchers.Main) {

                                                AppBridge.currentOtpResolver.value =
                                                    resolver
                                            }
                                        }
                                    }
                                }
                            }
                        )

                    } catch (e: Exception) {

                        if (e is CancellationException) throw e

                        loginSuccess = false
                    }

                    // =====================================
                    // RETRY
                    // =====================================

                    if (!loginSuccess) {

                        attempts++

                        if (attempts < maxRetries) {

                            vtopClient.reinitializeSession(context)
                        }
                    }
                }

                loginSuccess
            }

            // =====================================================
            // SUCCESS
            // =====================================================

            if (success) {

                withContext(Dispatchers.Main) {

                    webViewRef?.syncCookies(vtopClient)

                    webViewRef?.loadUrl(VTOP_OPEN_PAGE)
                }

            } else {

                sessionError =
                    "Failed to bypass Captcha after 3 attempts or OTP cancelled. Please retry."
            }

        } catch (e: Exception) {

            if (e !is CancellationException) {

                sessionError =
                    e.message ?: "Unknown error occurred"
            }

        } finally {

            isLoading = false
        }
    }

    // =========================================================
    // ERROR SCREEN
    // =========================================================

    if (sessionError != null) {

        VtopWebViewLoading(
            error = sessionError,
            onRetry = {
                scope.launch {
                    forceLogin()
                }
            }
        )

        return
    }

    // =========================================================
    // MAIN UI
    // =========================================================

    Scaffold(

        containerColor = Color.Black,

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        text = pageTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },

                navigationIcon = {

                    IconButton(onClick = onBack) {

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },

                actions = {

                    Box {

                        IconButton(
                            onClick = {
                                expandedMenu = true
                            }
                        ) {

                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu"
                            )
                        }

                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = {
                                expandedMenu = false
                            }
                        ) {

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        if (desktopMode)
                                            "Desktop Mode"
                                        else
                                            "Mobile Mode "
                                    )
                                },

                                onClick = {

                                    expandedMenu = false

                                    desktopMode = !desktopMode

                                    applyDesktopMode(webViewRef)

                                    Toast.makeText(
                                        context,
                                        if (desktopMode)
                                            "Mobile mode enabled"
                                        else
                                            "Desktop mode enabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            DropdownMenuItem(

                                text = {
                                    Text("Refresh")
                                },

                                onClick = {

                                    expandedMenu = false

                                    webViewRef?.reload()
                                }
                            )

                            DropdownMenuItem(

                                text = {
                                    Text("Force Refresh Session")
                                },

                                onClick = {

                                    expandedMenu = false

                                    scope.launch {
                                        forceLogin()
                                    }
                                }
                            )
                        }
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0f0f0f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }

    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            AndroidView(

                factory = { ctx ->

                    WebView(ctx).apply {

                        layoutParams =
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                        settings.apply {

                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true

                            mixedContentMode =
                                WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)

                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)

                            // =====================================
                            // INITIAL DESKTOP/MOBILE MODE
                            // =====================================

                            if (desktopMode) {

                                userAgentString = DESKTOP_UA

                                useWideViewPort = true
                                loadWithOverviewMode = true

                                layoutAlgorithm =
                                    WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

                            } else {

                                userAgentString = MOBILE_UA

                                useWideViewPort = false
                                loadWithOverviewMode = false

                                layoutAlgorithm =
                                    WebSettings.LayoutAlgorithm.NORMAL
                            }
                        }

                        webChromeClient = object : WebChromeClient() {

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {

                                val newWebView = WebView(context)

                                newWebView.webViewClient =
                                    object : WebViewClient() {

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {

                                            webViewRef?.loadUrl(
                                                request?.url.toString()
                                            )

                                            return true
                                        }
                                    }

                                val transport =
                                    resultMsg?.obj as WebView.WebViewTransport

                                transport.webView = newWebView

                                resultMsg.sendToTarget()

                                return true
                            }
                        }

                        // =========================================
                        // DOWNLOAD HANDLER
                        // =========================================

                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->

                            val fileName =
                                android.webkit.URLUtil.guessFileName(
                                    url,
                                    contentDisposition,
                                    mimeType
                                )

                            Toast.makeText(
                                context,
                                "Downloading $fileName...",
                                Toast.LENGTH_SHORT
                            ).show()

                            scope.launch(Dispatchers.IO) {

                                try {

                                    val request =
                                        okhttp3.Request.Builder()
                                            .url(url)
                                            .addHeader("Referer", VTOP_BASE)
                                            .build()

                                    val response =
                                        vtopClient.client
                                            .newCall(request)
                                            .execute()

                                    val bytes =
                                        response.body?.bytes()

                                    if (
                                        response.isSuccessful &&
                                        bytes != null
                                    ) {

                                        val resolver =
                                            context.contentResolver

                                        val values =
                                            android.content.ContentValues().apply {

                                                put(
                                                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                                                    fileName
                                                )

                                                put(
                                                    android.provider.MediaStore.MediaColumns.MIME_TYPE,
                                                    mimeType ?: "application/pdf"
                                                )

                                                put(
                                                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                                                    android.os.Environment.DIRECTORY_DOWNLOADS
                                                )
                                            }

                                        val collection =
                                            if (android.os.Build.VERSION.SDK_INT >= 29) {

                                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI

                                            } else {

                                                android.provider.MediaStore.Files.getContentUri("external")
                                            }

                                        val uri =
                                            resolver.insert(
                                                collection,
                                                values
                                            )

                                        if (uri == null) {

                                            throw Exception("Failed creating download entry")
                                        }

                                        resolver.openOutputStream(uri)?.use {

                                            it.write(bytes)
                                        }

                                        withContext(Dispatchers.Main) {

                                            NotificationHelper
                                                .showDownloadNotificationFromUri(
                                                    context = context,
                                                    uri = uri,
                                                    fileName = fileName,
                                                    title = "Download Complete",
                                                    description = "Tap to open $fileName"
                                                )

                                            Toast.makeText(
                                                context,
                                                "Saved to Downloads",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                    } else {

                                        withContext(Dispatchers.Main) {

                                            Toast.makeText(
                                                context,
                                                "Download rejected by server",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                } catch (e: Exception) {

                                    withContext(Dispatchers.Main) {

                                        Toast.makeText(
                                            context,
                                            "Failed: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }

                        // =========================================
                        // WEBVIEW CLIENT
                        // =========================================

                        webViewClient = object : WebViewClient() {

                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: Bitmap?
                            ) {

                                isLoading = true
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String?
                            ) {

                                isLoading = false

                                // =====================================
                                // AUTO NAVIGATION
                                // =====================================

                                if (url?.contains("open/page") == true) {

                                    val regNo =
                                        Vault.getCredentials(context)[0] ?: ""

                                    val jsCode = """
                                        (function() {

                                            var csrfInput =
                                                document.querySelector(
                                                    'input[name="_csrf"]'
                                                );

                                            var token =
                                                csrfInput
                                                    ? csrfInput.value
                                                    : '';

                                            if (token) {

                                                var form =
                                                    document.createElement('form');

                                                form.method = 'POST';

                                                form.action =
                                                    '$VTOP_CONTENT';

                                                form.innerHTML =
                                                    '<input type="hidden" name="_csrf" value="'+token+'">' +
                                                    '<input type="hidden" name="authorizedID" value="$regNo">' +
                                                    '<input type="hidden" name="verifyMenu" value="true">' +
                                                    '<input type="hidden" name="nocache" value="'+(new Date().getTime())+'">';

                                                document.body.appendChild(form);

                                                form.submit();
                                            }

                                        })()
                                    """.trimIndent()

                                    view.evaluateJavascript(jsCode, null)

                                } else if (url?.contains("content") == true) {

                                    pageTitle =
                                        view.title?.take(30)
                                            ?: "VTOP Dashboard"

                                } else if (
                                    url?.endsWith("vtop/login") == true ||
                                    url?.contains("vtop/login/error") == true
                                ) {

                                    scope.launch {
                                        forceLogin()
                                    }
                                }
                            }

                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {

                                handler?.proceed()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {

                                val urlStr =
                                    request.url.toString()

                                return !urlStr.startsWith(VTOP_BASE)
                            }
                        }

                        webViewRef = this

                        syncCookies(vtopClient)

                        postDelayed(
                            {
                                loadUrl(VTOP_OPEN_PAGE)
                            },
                            500
                        )
                    }
                },

                modifier = Modifier.fillMaxSize()
            )

            // =====================================================
            // LOADING BAR
            // =====================================================

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {

                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),

                    color = MaterialTheme.colorScheme.primary,

                    trackColor = Color.Transparent
                )
            }
        }
    }
}

// =========================================================
// COOKIE SYNC
// =========================================================

private fun WebView.syncCookies(
    vtopClient: VtopClient
) {

    val cookieManager =
        CookieManager.getInstance()

    cookieManager.setAcceptCookie(true)

    cookieManager.setAcceptThirdPartyCookies(
        this,
        true
    )

    cookieManager.removeAllCookies(null)

    val extractionUrl =
        "https://vtop.vitap.ac.in/vtop/login".toHttpUrl()

    val cookies =
        vtopClient.client.cookieJar
            .loadForRequest(extractionUrl)

    val targetUrl =
        "https://vtop.vitap.ac.in/vtop"

    cookies.forEach { cookie ->

        val cookieStr =
            "${cookie.name}=${cookie.value}; " +
                    "Domain=.vitap.ac.in; " +
                    "Path=/vtop; Secure"

        cookieManager.setCookie(
            targetUrl,
            cookieStr
        )
    }

    cookieManager.flush()
}

// =========================================================
// LOADING / ERROR SCREEN
// =========================================================

@Composable
fun VtopWebViewLoading(
    error: String?,
    onRetry: (() -> Unit)?
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),

        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (error != null) {

                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFf87171),
                    modifier = Modifier.size(48.dp)
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Could not open VTOP",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    error,
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                if (onRetry != null) {

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp)
                    ) {

                        Text("Retry")
                    }
                }

            } else {

                CircularProgressIndicator(
                    color = Color.White
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    "Opening VTOP...",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Injecting your session securely",
                    color = Color(0xFF555555),
                    fontSize = 11.sp
                )
            }
        }
    }
}