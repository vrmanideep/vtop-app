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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val VTOP_BASE = "https://vtop.vitap.ac.in"
private const val VTOP_OPEN_PAGE = "$VTOP_BASE/vtop/open/page"
private const val VTOP_CONTENT = "$VTOP_BASE/vtop/content"
private const val VTOP_LOGIN = "$VTOP_BASE/vtop/login"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VtopPortalScreen(
    vtopClient: VtopClient,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageTitle by remember { mutableStateOf("VTOP") }
    var isLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var sessionError by remember { mutableStateOf<String?>(null) }

    suspend fun forceLogin() {
        isLoading = true
        sessionError = null
        try {
            val success = withContext(Dispatchers.IO) {
                vtopClient.autoLogin(context, object : VtopClient.LoginListener {
                    override fun onStatusUpdate(message: String) {}
                    override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                        resolver.cancel()
                    }
                })
            }

            if (success) {
                withContext(Dispatchers.Main) {
                    webViewRef?.syncCookies(vtopClient)
                    webViewRef?.loadUrl(VTOP_OPEN_PAGE)
                }
            } else {
                sessionError = "Session expired. Please sync from settings."
            }
        } catch (e: Exception) {
            sessionError = e.message
        } finally {
            isLoading = false
        }
    }

    if (sessionError != null) {
        VtopWebViewLoading(error = sessionError, onRetry = { scope.launch { forceLogin() } })
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(pageTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0f0f0f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true

                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)

                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                        }

                        // THE FIX: Use OkHttp to bypass the SSL block and save natively
                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                            Toast.makeText(context, "Downloading $fileName...", Toast.LENGTH_SHORT).show()

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val request = okhttp3.Request.Builder()
                                        .url(url)
                                        .addHeader("Referer", VTOP_BASE)
                                        .build()

                                    // vtopClient already holds your session cookies and SSL bypass
                                    val response = vtopClient.client.newCall(request).execute()
                                    val bytes = response.body?.bytes()

                                    if (response.isSuccessful && bytes != null) {
                                        // Save to Public Downloads folder (Android 10+)
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            val resolver = context.contentResolver
                                            val values = android.content.ContentValues().apply {
                                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                                            }
                                            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                            uri?.let { resolver.openOutputStream(it)?.use { out -> out.write(bytes) } }
                                        } else {
                                            // Fallback for older Androids (Saves to App's private Download folder to avoid permission crashes)
                                            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                            java.io.File(dir, fileName).writeBytes(bytes)
                                        }

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Saved $fileName to Downloads", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Download rejected by server", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                val newWebView = WebView(context)
                                newWebView.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        webViewRef?.loadUrl(request?.url.toString())
                                        return true
                                    }
                                }
                                val transport = resultMsg?.obj as WebView.WebViewTransport
                                transport.webView = newWebView
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading = false

                                if (url?.contains("open/page") == true) {
                                    val regNo = Vault.getCredentials(context)[0] ?: ""
                                    val jsCode = """
                                      (function() {
                                        var csrfInput = document.querySelector('input[name="_csrf"]');
                                        var token = csrfInput ? csrfInput.value : '';
                                        if (token) {
                                            var form = document.createElement('form');
                                            form.method = 'POST';
                                            form.action = '$VTOP_CONTENT';
                                            form.innerHTML = '<input type="hidden" name="_csrf" value="'+token+'">' +
                                                             '<input type="hidden" name="authorizedID" value="$regNo">' +
                                                             '<input type="hidden" name="verifyMenu" value="true">' +
                                                             '<input type="hidden" name="nocache" value="@(new Date().getTime())">';
                                            document.body.appendChild(form);
                                            form.submit();
                                        }
                                      })()
                                    """.trimIndent()
                                    view.evaluateJavascript(jsCode, null)
                                }
                                else if (url?.contains("content") == true) {
                                    pageTitle = view.title?.take(30) ?: "VTOP Dashboard"
                                }
                                else if (url?.contains(VTOP_LOGIN) == true || url?.contains("securityOtpPending") == true) {
                                    scope.launch { forceLogin() }
                                }
                            }

                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.proceed()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val urlStr = request.url.toString()
                                return !urlStr.startsWith(VTOP_BASE)
                            }
                        }
                        webViewRef = this

                        syncCookies(vtopClient)
                        postDelayed({ loadUrl(VTOP_OPEN_PAGE) }, 500)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

private fun WebView.syncCookies(vtopClient: VtopClient) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(this, true)
    cookieManager.removeAllCookies(null)

    val extractionUrl = "https://vtop.vitap.ac.in/vtop/login".toHttpUrl()
    val cookies = vtopClient.client.cookieJar.loadForRequest(extractionUrl)

    val targetUrl = "https://vtop.vitap.ac.in/vtop"
    cookies.forEach { cookie ->
        val cookieStr = "${cookie.name}=${cookie.value}; Domain=.vitap.ac.in; Path=/vtop; Secure"
        cookieManager.setCookie(targetUrl, cookieStr)
    }

    cookieManager.flush()
}

@Composable
fun VtopWebViewLoading(error: String?, onRetry: (() -> Unit)?) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFf87171), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Could not open VTOP", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color.Gray, fontSize = 12.sp)
                if (onRetry != null) {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onRetry, shape = RoundedCornerShape(10.dp)) {
                        Text("Retry")
                    }
                }
            } else {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Opening VTOP...", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("Injecting your session securely", color = Color(0xFF555555), fontSize = 11.sp)
            }
        }
    }
}