package com.vtop.portal

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vtop.network.VtopClient
import com.vtop.utils.NotificationHelper
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val VTOP_BASE = "https://vtop.vitap.ac.in"
private const val VTOP_OPEN_PAGE = "$VTOP_BASE/vtop/open/page"
private const val VTOP_CONTENT = "$VTOP_BASE/vtop/content"

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Mobile Safari/537.36"
private const val TAG = "PORTAL_HOST"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalHost(
    activeClient: VtopClient,
    desktopMode: Boolean,
    onPageLoading: (Boolean) -> Unit,
    onTitleUpdate: (String) -> Unit,
    onSessionExpired: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = WebView.OVER_SCROLL_ALWAYS
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            setOnTouchListener { view, _ -> view.parent?.requestDisallowInterceptTouchEvent(true); false }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                    val newWebView = WebView(context)
                    newWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            loadUrl(request?.url.toString())
                            return true
                        }
                    }
                    val transport = resultMsg?.obj as WebView.WebViewTransport
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    return true
                }
            }

            setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                val fileName: String = URLUtil.guessFileName(url, contentDisposition, mimeType)
                Toast.makeText(context, "Downloading $fileName...", Toast.LENGTH_SHORT).show()
                scope.launch(Dispatchers.IO) {
                    try {
                        val request = okhttp3.Request.Builder().url(url).addHeader("Referer", VTOP_BASE).build()
                        val response = activeClient.client.newCall(request).execute()
                        val bytes = response.body?.bytes()

                        if (response.isSuccessful && bytes != null) {
                            val resolver = context.contentResolver
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/pdf")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }

                            val collection = if (Build.VERSION.SDK_INT >= 29) {
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            } else {
                                MediaStore.Files.getContentUri("external")
                            }

                            val uri: Uri = resolver.insert(collection, values) ?: throw Exception("Failed creating download entry")

                            val os = resolver.openOutputStream(uri)
                            if (os != null) {
                                os.use { outputStream -> outputStream.write(bytes) }
                            }

                            withContext(Dispatchers.Main) {
                                NotificationHelper.showDownloadNotificationFromUri(
                                    context = context,
                                    uri = uri,
                                    fileName = fileName,
                                    title = "Download Complete",
                                    description = "Tap to open $fileName"
                                )
                                Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Download rejected by server", Toast.LENGTH_SHORT).show() }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    onPageLoading(true)
                    PortalController.updateCurrentUrl(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    onPageLoading(false)
                    view.evaluateJavascript(
                        """
                        (function() {
                            document.documentElement.style.overflowY = 'auto';
                            document.body.style.overflowY = 'auto';
                            document.documentElement.style.height = 'auto';
                            document.body.style.height = 'auto';
                            var scrollContainers = document.querySelectorAll('.content-wrapper, .main-sidebar, .sidebar, .sidebar-menu');
                            scrollContainers.forEach(function(element) {
                                element.style.overflowY = 'auto';
                                element.style.maxHeight = 'none';
                                element.style.height = 'auto';
                                element.style.webkitOverflowScrolling = 'touch';
                            });
                        })();
                        """.trimIndent(), null
                    )

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
                                                     '<input type="hidden" name="nocache" value="'+(new Date().getTime())+'">';
                                    document.body.appendChild(form);
                                    form.submit();
                                }
                            })()
                        """.trimIndent()
                        view.evaluateJavascript(jsCode, null)
                    } else if (url?.contains("content") == true) {
                        val t = view.title
                        val titleText = if (!t.isNullOrBlank()) {
                            if (t.length > 30) t.substring(0, 30) else t
                        } else {
                            "VTOP Dashboard"
                        }
                        onTitleUpdate(titleText)
                    } else if (url?.endsWith("vtop/login") == true || url?.contains("vtop/login/error") == true) {
                        Log.w(TAG, "Portal session expired")
                        onSessionExpired()
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) { handler?.proceed() }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !request.url.toString().startsWith(VTOP_BASE)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        PortalController.commands.collect { command ->
            when (command) {
                is PortalCommand.Reload -> webView.reload()
                is PortalCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                is PortalCommand.LoadUrl -> webView.loadUrl(command.url)
                is PortalCommand.ExecuteJs -> webView.evaluateJavascript(command.script, null)
            }
        }
    }

    LaunchedEffect(desktopMode) {
        val settings = webView.settings
        if (desktopMode) {
            settings.userAgentString = DESKTOP_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            webView.setInitialScale(1)
        } else {
            settings.userAgentString = MOBILE_UA
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            webView.setInitialScale(100)
        }
        webView.reload()
    }

    LaunchedEffect(activeClient) {
        syncCookies(webView, activeClient)
        webView.loadUrl(VTOP_OPEN_PAGE)
    }

    AndroidView(
        factory = { webView },
        modifier = Modifier.fillMaxSize()
    )
}

private fun syncCookies(webView: WebView, vtopClient: VtopClient) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)
    cookieManager.removeAllCookies(null)

    val extractionUrl = "https://vtop.vitap.ac.in/vtop/login".toHttpUrl()
    val cookies = vtopClient.client.cookieJar.loadForRequest(extractionUrl)

    Log.i(TAG, "Synchronizing ${cookies.size} cookies")
    val targetUrl = "https://vtop.vitap.ac.in/vtop"
    cookies.forEach { cookie ->
        val cookieStr = "${cookie.name}=${cookie.value}; Domain=.vitap.ac.in; Path=/vtop; Secure"
        cookieManager.setCookie(targetUrl, cookieStr)
    }
    cookieManager.flush()
    Log.i(TAG, "Cookie synchronization complete")
}