package com.vtop.portal

import android.annotation.SuppressLint
import android.content.ContentValues
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vtop.network.VtopClient
import com.vtop.utils.*
import kotlinx.coroutines.CoroutineScope
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

@Composable
fun premiumSurfaceColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF141414) else Color(0xFFFFFFFF)

@Composable
fun premiumBorderColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)

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
    val scope = rememberCoroutineScope()

    val currentDarkTheme by rememberUpdatedState(vtopThemeDark)
    val currentDesktopMode by rememberUpdatedState(desktopMode)
    var activeDownloads by remember { mutableStateOf<Map<String, Triple<String, Float, String>>>(emptyMap()) }
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
                val originalFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val downloadId = url.hashCode().toString()
                // Instant UI Feedback
                activeDownloads = activeDownloads + (downloadId to Triple(originalFileName, -1f, "Starting..."))

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
                    // Detach from Compose scope so download survives temporary recompositions
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val cleanResult = jsResult?.trim('"') ?: ""
                            var finalName = originalFileName

                            // Use exact UA currently active in WebView
                            val targetUa = if (currentDesktopMode) DESKTOP_UA else MOBILE_UA
                            val request = okhttp3.Request.Builder()
                                .url(url)
                                .addHeader("Referer", "https://vtop.vitap.ac.in")
                                .addHeader("User-Agent", targetUa)
                                .build()

                            val response = activeClient.client.newCall(request).execute()
                            val body = response.body

                            if (response.isSuccessful && body != null) {
                                val contentLength = body.contentLength()
                                val inputStream = body.byteStream()
                                val outputStream = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8 * 1024)
                                var bytesRead: Int
                                var totalRead = 0L
                                var lastUpdate = System.currentTimeMillis()

                                // Stream the file to track progress in real-time
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    val formatSize = { bytes: Long ->
                                        if (bytes < 1024 * 1024) "${bytes / 1024} KB" else String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
                                    }

                                    // Stream the file to track progress in real-time
                                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                        outputStream.write(buffer, 0, bytesRead)
                                        totalRead += bytesRead

                                        val now = System.currentTimeMillis()
                                        if (now - lastUpdate > 100) {
                                            val progress = if (contentLength > 0) totalRead.toFloat() / contentLength else -1f
                                            val pct = if (progress >= 0) (progress * 100).toInt() else -1

                                            val progressString = if (contentLength > 0) {
                                                "${formatSize(totalRead)} / ${formatSize(contentLength)}"
                                            } else {
                                                "${formatSize(totalRead)} downloaded"
                                            }

                                            withContext(Dispatchers.Main) {
                                                activeDownloads = activeDownloads + (downloadId to Triple(originalFileName, progress, progressString))
                                            }

                                            NotificationHelper.showDownloadProgressNotification(
                                                context = context,
                                                notificationId = originalFileName.hashCode(),
                                                fileName = originalFileName,
                                                progress = pct,
                                                progressText = if (pct >= 0) "$progressString ($pct%)" else progressString
                                            )

                                            lastUpdate = now
                                        }
                                    }
                                }

                                val bytes = outputStream.toByteArray()

                                var originalExt = ""
                                val cdMatch = Regex("filename=\"?([^\";]+)\"?").find(contentDisposition ?: "")
                                if (cdMatch != null) {
                                    originalExt = cdMatch.groupValues[1].substringAfterLast('.', "")
                                }
                                if (originalExt.isBlank()) {
                                    originalExt = originalFileName.substringAfterLast('.', "")
                                }
                                originalExt = originalExt.lowercase().trim()

                                val magic = bytes.take(4).toByteArray()
                                var actualExt = originalExt

                                if (magic.size >= 4 && magic[0] == 0x25.toByte() && magic[1] == 0x50.toByte() && magic[2] == 0x44.toByte() && magic[3] == 0x46.toByte()) {
                                    actualExt = "pdf"
                                } else if (magic.size >= 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() && magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()) {
                                    actualExt = "zip"
                                    try {
                                        val zis = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes))
                                        var entry = zis.nextEntry
                                        while (entry != null) {
                                            if (entry.name.startsWith("ppt/")) { actualExt = "pptx"; break }
                                            if (entry.name.startsWith("word/")) { actualExt = "docx"; break }
                                            if (entry.name.startsWith("xl/")) { actualExt = "xlsx"; break }
                                            entry = zis.nextEntry
                                        }
                                        zis.close()
                                    } catch (e: Exception) { }
                                    if (actualExt == "zip" && originalExt in listOf("docx", "pptx", "xlsx")) {
                                        actualExt = originalExt
                                    }
                                } else if (magic.size >= 4 && magic[0] == 0xD0.toByte() && magic[1] == 0xCF.toByte() && magic[2] == 0x11.toByte() && magic[3] == 0xE0.toByte()) {
                                    actualExt = if (originalExt in listOf("doc", "ppt", "xls")) originalExt else "doc"
                                } else if (magic.size >= 4 && magic[0] == 0x89.toByte() && magic[1] == 0x50.toByte() && magic[2] == 0x4E.toByte() && magic[3] == 0x47.toByte()) {
                                    actualExt = "png"
                                } else if (magic.size >= 3 && magic[0] == 0xFF.toByte() && magic[1] == 0xD8.toByte() && magic[2] == 0xFF.toByte()) {
                                    actualExt = "jpg"
                                }

                                val safeExt = if (actualExt.startsWith(".")) actualExt else ".$actualExt"

                                val correctedMimeType = when (actualExt) {
                                    "pdf" -> "application/pdf"
                                    "zip" -> "application/zip"
                                    "doc" -> "application/msword"
                                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    "ppt" -> "application/vnd.ms-powerpoint"
                                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                    "xls" -> "application/vnd.ms-excel"
                                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "txt" -> "text/plain"
                                    else -> mimeType ?: "application/octet-stream"
                                }

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

                                val resolver = context.contentResolver
                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, correctedMimeType)
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VTOP_Materials")
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
                                    // 1. Explicitly kill the progress notification using its original ID
                                    NotificationHelper.dismissNotification(context, originalFileName.hashCode())

                                    // 2. Show the success notification with the smart-renamed ID
                                    NotificationHelper.showDownloadNotificationFromUri(
                                        context = context,
                                        uri = uri,
                                        fileName = finalName,
                                        mimeType = correctedMimeType,
                                        title = "Download Complete",
                                        description = "Tap to open $finalName"
                                    )
                                }
                            } else {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Download rejected by server", Toast.LENGTH_SHORT).show() }
                                NotificationHelper.dismissNotification(context, originalFileName.hashCode())
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Download Failed", Toast.LENGTH_SHORT).show() }
                        } finally {
                            withContext(Dispatchers.Main) {
                                activeDownloads = activeDownloads - downloadId
                                // Ensure the progress notification is ALWAYS cleared if the coroutine dies
                                NotificationHelper.dismissNotification(context, originalFileName.hashCode())
                            }
                        }
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    onPageLoading(true)
                    com.vtop.portal.PortalController.updateCurrentUrl(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    onPageLoading(false)

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

                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    Log.e(TAG, "Chromium Render Process crashed! Rebooting session...")
                    Toast.makeText(context, "Renderer crashed. Reloading VTOP...", Toast.LENGTH_SHORT).show()
                    onSessionExpired()
                    return true
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

    LaunchedEffect(activeClient, sessionKey) {
        syncCookies(webView, activeClient)
        webView.loadUrl(VTOP_OPEN_PAGE)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        // UI Downloads Tracker Overlay
        if (activeDownloads.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeDownloads.forEach { (_, data) ->
                        val (name, prog, progText) = data
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
                                    Text(
                                        text = name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    if (prog >= 0f) {
                                        Text(
                                            text = "${(prog * 100).toInt()}%",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (progText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = progText,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (prog < 0f) {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                } else {
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { prog },
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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