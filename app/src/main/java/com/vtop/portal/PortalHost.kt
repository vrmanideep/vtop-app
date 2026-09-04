package com.vtop.portal

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Message
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vtop.network.VtopClient
import com.vtop.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val VTOP_BASE = "https://vtop.vitap.ac.in"
private const val VTOP_OPEN_PAGE = "$VTOP_BASE/vtop/open/page"
private const val VTOP_CONTENT = "$VTOP_BASE/vtop/content"

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0 Mobile Safari/537.36"
private const val TAG = "PORTAL_HOST"

// Global download state decoupled from Compose lifecycle
object PortalDownloadManager {
    data class DownloadState(val name: String, val progress: Float, val status: String, val uri: Uri? = null, val mimeType: String = "")
    private val _activeDownloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadState>> = _activeDownloads.asStateFlow()

    fun updateDownload(id: String, name: String, progress: Float, status: String, uri: Uri? = null, mimeType: String = "") {
        _activeDownloads.update { it + (id to DownloadState(name, progress, status, uri, mimeType)) }
    }

    fun removeDownload(id: String) {
        _activeDownloads.update { it - id }
    }
}

@Composable
fun premiumSurfaceColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF141414) else Color(0xFFFFFFFF)

@Composable
fun premiumBorderColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PortalHost(
    activeClient: VtopClient,
    sessionKey: Int,
    desktopMode: Boolean,
    vtopThemeDark: Boolean,
    onPageLoading: (Boolean) -> Unit,
    onTitleUpdate: (String) -> Unit,
    onSessionExpired: () -> Unit
) {
    val context = LocalContext.current
    val currentDarkTheme by rememberUpdatedState(vtopThemeDark)
    val currentDesktopMode by rememberUpdatedState(desktopMode)
    val downloads by PortalDownloadManager.activeDownloads.collectAsState()

    var pdfUriToView by remember { mutableStateOf<Uri?>(null) }

    // Keyed by sessionKey to ensure a fresh, uncorrupted WebView instance upon re-auth
    val webView = remember(sessionKey) {
        WebView(context).apply {
            addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun notifySessionExpired() {
                    Log.w(TAG, "AJAX session expiration detected!")
                    onSessionExpired()
                }
            }, "AndroidInterface")

            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            overScrollMode = WebView.OVER_SCROLL_ALWAYS
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)

                // Set desktop/mobile properties natively on boot
                userAgentString = if (desktopMode) DESKTOP_UA else MOBILE_UA
                useWideViewPort = desktopMode
                loadWithOverviewMode = desktopMode
                layoutAlgorithm = if (desktopMode) WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING else WebSettings.LayoutAlgorithm.NORMAL
            }
            setInitialScale(if (desktopMode) 1 else 100)

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
                val originalFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val downloadId = url.hashCode().toString()

                PortalDownloadManager.updateDownload(downloadId, originalFileName, -1f, "Starting...")

                val safeUrl = url.replace("\"", "\\\"")
                val jsCode = """
                    (function() {
                        try {
                            var targetUrl = "$safeUrl"; 
                            var tables = document.querySelectorAll('table');
                            var code = "COURSE";
                            if(tables.length > 0 && tables[0].querySelectorAll('tr').length > 1) {
                                code = tables[0].querySelectorAll('tr')[1].querySelectorAll('td')[1].innerText.trim();
                            }
                            if (targetUrl.indexOf("allCourseMeterialDownload") !== -1) {
                                return code + "|||ALL|||||||||";
                            }
                            var links = document.querySelectorAll('a');
                            for(var i=0; i<links.length; i++) {
                                var href = links[i].getAttribute('href');
                                if(!href) continue;
                                var match = href.match(/vtopDownload\(['"](.*?)['"]\)/);
                                if(match && targetUrl.indexOf(match[1]) !== -1) {
                                    var tr = links[i].closest('tr');
                                    if(!tr) continue;
                                    var tds = tr.querySelectorAll('td');
                                    if(tds.length >= 4 && tds[0].innerText.trim().match(/^\d+$/)) {
                                        var sl = tds[0].innerText.trim();
                                        var dtText = tds[1].innerText.trim().split('\n')[0].trim(); 
                                        var topic = tds[3].innerText.trim();
                                        var linkText = links[i].innerText.trim();
                                        return code + "|||" + sl + "|||" + dtText + "|||" + topic + "|||" + linkText;
                                    }
                                }
                            }
                            return code + "|||GEN|||||||||"; 
                        } catch(e) { return "ERROR"; }
                    })();
                """.trimIndent()

                evaluateJavascript(jsCode) { jsResult ->
                    // Uses Application-level scope to survive screen navigation
                    CoroutineScope(Dispatchers.IO).launch {
                        var isSuccess = false
                        try {
                            val cleanResult = jsResult?.trim('"') ?: ""
                            val targetUa = if (currentDesktopMode) DESKTOP_UA else MOBILE_UA
                            val request = okhttp3.Request.Builder()
                                .url(url)
                                .addHeader("Referer", "https://vtop.vitap.ac.in")
                                .addHeader("User-Agent", targetUa)
                                .build()

                            val response = activeClient.client.newCall(request).execute()
                            val body = response.body

                            if (response.isSuccessful && body != null) {
                                // 1. Determine Extension Early (Relying on Content-Disposition)
                                var ext = ""
                                val cdMatch = Regex("filename=\"?([^\";]+)\"?").find(contentDisposition ?: "")
                                if (cdMatch != null) ext = cdMatch.groupValues[1].substringAfterLast('.', "")
                                if (ext.isBlank()) ext = originalFileName.substringAfterLast('.', "")
                                ext = ext.lowercase().trim()
                                val safeExt = if (ext.isNotEmpty() && !ext.startsWith(".")) ".$ext" else ext

                                // 2. Determine Final Name
                                var finalName = originalFileName
                                if (cleanResult.contains("|||")) {
                                    val parts = cleanResult.split("|||")
                                    val courseCode = parts[0].replace(Regex("[^a-zA-Z0-9]"), "")
                                    val slNo = parts[1]

                                    if (slNo == "ALL") {
                                        finalName = "${courseCode}_All_Materials$safeExt"
                                    } else if (slNo == "GEN") {
                                        finalName = "${courseCode}_${originalFileName.replace(Regex("[\\\\/*?:\"<>|]"), "")}"
                                        if (!finalName.endsWith(safeExt, true)) finalName += safeExt
                                    } else {
                                        val dateRaw = parts[2]
                                        val topic = parts[3].replace(Regex("[^a-zA-Z0-9]"), "_").trim('_')
                                        val linkText = if (parts.size > 4) parts[4] else ""
                                        val typeSuffix = when {
                                            linkText.contains("Reference Material I", ignoreCase = true) && !linkText.contains("II") -> "Ref1"
                                            linkText.contains("Reference Material II", ignoreCase = true) && !linkText.contains("III") -> "Ref2"
                                            linkText.contains("Reference Material III", ignoreCase = true) -> "Ref3"
                                            linkText.contains("Reference Material IV", ignoreCase = true) -> "Ref4"
                                            linkText.contains("Reference", ignoreCase = true) -> "Ref"
                                            else -> "Main"
                                        }

                                        val formattedDate = try {
                                            val inFormat = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.ENGLISH)
                                            val outFormat = java.text.SimpleDateFormat("dd-MMM-yy", java.util.Locale.ENGLISH)
                                            outFormat.format(inFormat.parse(dateRaw)!!)
                                        } catch(e: Exception) { dateRaw.replace("-", "") }

                                        val slPadded = slNo.padStart(2, '0')
                                        finalName = "${courseCode}_L${slPadded}_${formattedDate}_${topic}_$typeSuffix$safeExt"
                                    }
                                } else {
                                    finalName = "VTOP_${originalFileName.replace(Regex("[\\\\/*?:\"<>|]"), "")}"
                                    if (!finalName.endsWith(safeExt, true)) finalName += safeExt
                                }

                                val correctedMimeType = mimeType ?: "application/octet-stream"

                                // 3. Create MediaStore Entry FIRST
                                val resolver = context.contentResolver
                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, correctedMimeType)
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VTOP_Materials")
                                }

                                val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
                                val uri: Uri = resolver.insert(collection, values) ?: throw Exception("Failed creating download entry")

                                // Generate a unique Notification ID that won't collide
                                val uniqueNotificationId = (url + System.currentTimeMillis()).hashCode()

                                // 4. Stream Direct to Disk
                                resolver.openOutputStream(uri)?.use { outputStream ->
                                    val inputStream = body.byteStream()
                                    val buffer = ByteArray(8 * 1024)
                                    var bytesRead: Int
                                    var totalRead = 0L
                                    val contentLength = body.contentLength()
                                    var lastUpdate = System.currentTimeMillis()

                                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                        outputStream.write(buffer, 0, bytesRead)
                                        totalRead += bytesRead

                                        val now = System.currentTimeMillis()
                                        if (now - lastUpdate > 100) {
                                            val progress = if (contentLength > 0) totalRead.toFloat() / contentLength else -1f
                                            val pct = if (progress >= 0) (progress * 100).toInt() else -1

                                            val formatSize = { bytes: Long -> if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f)) }
                                            val progressString = if (contentLength > 0) "${formatSize(totalRead)} / ${formatSize(contentLength)}" else "${formatSize(totalRead)} downloaded"

                                            PortalDownloadManager.updateDownload(downloadId, originalFileName, progress, progressString)

                                            NotificationHelper.showDownloadProgressNotification(
                                                context = context,
                                                notificationId = uniqueNotificationId,
                                                fileName = finalName,
                                                progress = pct,
                                                progressText = if (pct >= 0) "$progressString ($pct%)" else progressString
                                            )
                                            lastUpdate = now
                                        }
                                    }
                                }

                                // 5. Finalize Success
                                isSuccess = true
                                withContext(Dispatchers.Main) {
                                    PortalDownloadManager.updateDownload(downloadId, finalName, 1f, "Download Complete", uri, correctedMimeType)
                                    NotificationHelper.dismissNotification(context, uniqueNotificationId)
                                    NotificationHelper.showDownloadNotificationFromUri(context, uri, finalName, correctedMimeType, "Download Complete", "Tap to open $finalName")
                                }
                            } else {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Download rejected by server", Toast.LENGTH_SHORT).show() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Download Failed", Toast.LENGTH_SHORT).show() }
                        } finally {
                            withContext(Dispatchers.Main) {
                                if (!isSuccess) PortalDownloadManager.removeDownload(downloadId)
                            }
                        }
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    Log.d(TAG, "WebView started loading: $url")
                    onPageLoading(true)
                    PortalController.updateCurrentUrl(url)
                    val ajaxInterceptor = """
                        (function() {
                            var originalXhrOpen = window.XMLHttpRequest.prototype.open;
                            window.XMLHttpRequest.prototype.open = function() {
                                this.addEventListener('load', function() {
                                    // Only trigger if unauthorized OR strictly redirected to a login URL
                                    if (this.status === 401 || (this.responseURL && this.responseURL.includes('/vtop/login'))) {
                                        window.AndroidInterface.notifySessionExpired();
                                    }
                                });
                                originalXhrOpen.apply(this, arguments);
                            };
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(ajaxInterceptor, null)
                    if (currentDarkTheme) {
                        val earlyScript = """
                            if (document.documentElement) {
                                document.documentElement.style.filter = 'invert(100%) hue-rotate(180deg) brightness(95%) contrast(105%)';
                                document.documentElement.style.backgroundColor = '#ffffff'; // Inverts to dark
                            }
                        """.trimIndent()
                        view.evaluateJavascript(earlyScript, null)
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    // VTOP often has broken SSL chains. We allow it ONLY for the university domain.
                    if (error.url.contains("vitap.ac.in")) {
                        Log.w(TAG, "Bypassing SSL error for VTOP domain: ${error.primaryError}")
                        handler.proceed()
                    } else {
                        Log.e(TAG, "Blocked SSL error for external domain: ${error.url}")
                        handler.cancel()
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    Log.d(TAG, "WebView finished loading: $url")
                    onPageLoading(false)

                    CookieManager.getInstance().flush()

                    var script = """
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
                    """.trimIndent()

                    if (currentDarkTheme) {
                        script += """
                            document.documentElement.style.filter = 'invert(100%) hue-rotate(180deg) brightness(95%) contrast(105%)';
                            var style = document.getElementById('vtop-dark-style');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'vtop-dark-style';
                                document.head.appendChild(style);
                            }
                            style.innerHTML = 'body, .content-wrapper, .wrapper, .main-sidebar, .sidebar, .panel, .box { background-color: #ffffff !important; } img, .logo, .navbar-brand, .img-circle { filter: invert(100%) hue-rotate(180deg) !important; }';
                        """.trimIndent()
                    } else {
                        script += """
                            document.documentElement.style.filter = 'none';
                            var style = document.getElementById('vtop-dark-style');
                            if (style) style.remove();
                        """.trimIndent()
                    }
                    view.evaluateJavascript("(function() { $script })();", null)

                    if (url?.contains("open/page") == true) {
                        val regNo = Vault.getCredentials(context)[0] ?: ""
                        val jsCode = """
                            (function() {
                                if (window.location.href.indexOf('login') !== -1) return;
                                var csrfInput = document.querySelector('input[name="_csrf"]');
                                if (csrfInput && csrfInput.value) {
                                    console.log("CSRF Token found, auto-posting to content dashboard...");
                                    var form = document.createElement('form');
                                    form.method = 'POST';
                                    form.action = '$VTOP_CONTENT';
                                    form.innerHTML = '<input type="hidden" name="_csrf" value="'+csrfInput.value+'">' +
                                                     '<input type="hidden" name="authorizedID" value="$regNo">' +
                                                     '<input type="hidden" name="verifyMenu" value="true">' +
                                                     '<input type="hidden" name="nocache" value="'+(new Date().getTime())+'">';
                                    document.body.appendChild(form);
                                    form.submit();
                                } else {
                                    console.error("CRITICAL: CSRF Token missing on open/page!");
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
                        Log.w(TAG, "Portal session expired or redirected to login")
                        onSessionExpired()
                    }
                }

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    Log.e(TAG, "Chromium Render Process crashed! Rebooting session...")
                    onSessionExpired()
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return !request.url.toString().startsWith(VTOP_BASE)
                }
            }
        }
    }

    // Reactive cookie syncing bound to Lifecycle so background token rotations apply immediately upon return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeClient) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncCookies(webView, activeClient)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        com.vtop.portal.PortalController.commands.collect { command ->
            when (command) {
                is com.vtop.portal.PortalCommand.Reload -> webView.reload()
                is com.vtop.portal.PortalCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                is com.vtop.portal.PortalCommand.LoadUrl -> webView.loadUrl(command.url)
                is com.vtop.portal.PortalCommand.ExecuteJs -> webView.evaluateJavascript(command.script, null)
            }
        }
    }

    LaunchedEffect(vtopThemeDark) {
        val script = if (vtopThemeDark) {
            """
            document.documentElement.style.filter = 'invert(100%) hue-rotate(180deg) brightness(95%) contrast(105%)';
            var style = document.getElementById('vtop-dark-style');
            if (!style) {
                style = document.createElement('style');
                style.id = 'vtop-dark-style';
                document.head.appendChild(style);
            }
            style.innerHTML = 'body, .content-wrapper, .wrapper, .main-sidebar, .sidebar, .panel, .box { background-color: #ffffff !important; } img, .logo, .navbar-brand, .img-circle { filter: invert(100%) hue-rotate(180deg) !important; }';
            """
        } else {
            """
            document.documentElement.style.filter = 'none';
            var style = document.getElementById('vtop-dark-style');
            if (style) style.remove();
            """
        }
        webView.evaluateJavascript("(function() { $script })();", null)
    }

    LaunchedEffect(activeClient, sessionKey) {
        syncCookies(webView, activeClient)
        webView.loadUrl(VTOP_OPEN_PAGE)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (downloads.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    downloads.forEach { (id, data) ->
                        val (name, prog, progText, uri, mime) = data

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                    PortalDownloadManager.removeDownload(id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    },
                                    label = "dismissColor"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            },
                            content = {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = premiumSurfaceColor()),
                                    border = BorderStroke(1.dp, premiumBorderColor()),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
                                            if (prog >= 0f && prog < 1f) Text(text = "${(prog * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        if (progText.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = progText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))

                                        if (prog < 1f) {
                                            if (prog < 0f) {
                                                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                            } else {
                                                androidx.compose.material3.LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                            }
                                        } else {
                                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                                if (mime == "application/pdf" && uri != null) {
                                                    TextButton(onClick = { pdfUriToView = uri }) { Text("View PDF") }
                                                }
                                                TextButton(onClick = { PortalDownloadManager.removeDownload(id) }) { Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // In-App Native PDF Rendering Overlay
    pdfUriToView?.let { uri ->
        NativePdfViewer(pdfUri = uri, onDismiss = { pdfUriToView = null })
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.i(TAG, "Destroying WebView to free RAM")
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativePdfViewer(pdfUri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val density = LocalDensity.current.density

    DisposableEffect(pdfUri) {
        try {
            fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (fileDescriptor != null) {
                pdfRenderer = PdfRenderer(fileDescriptor!!)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("PDF Viewer", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = premiumSurfaceColor())
                )
                if (pdfRenderer == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val pageCount = pdfRenderer!!.pageCount
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF222222)),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPage(pdfRenderer!!, index, density)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(pdfRenderer: PdfRenderer, pageIndex: Int, density: Float) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            val page = pdfRenderer.openPage(pageIndex)
            val width = (page.width * density).toInt()
            val height = (page.height * density).toInt()

            val renderedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            renderedBitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            bitmap = renderedBitmap
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF Page ${pageIndex + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
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
}