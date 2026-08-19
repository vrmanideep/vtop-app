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
    sessionKey: Int,
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
                Toast.makeText(context, "Preparing download...", Toast.LENGTH_SHORT).show()

                val originalFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

                // Inject JavaScript to extract metadata AND the specific clicked link text
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
                    scope.launch(Dispatchers.IO) {
                        try {
                            val cleanResult = jsResult?.trim('"') ?: ""
                            var finalName = originalFileName

                            val request = okhttp3.Request.Builder().url(url).addHeader("Referer", "https://vtop.vitap.ac.in").build()
                            val response = activeClient.client.newCall(request).execute()
                            val bytes = response.body?.bytes()

                            if (response.isSuccessful && bytes != null) {

                                // 1. Try to extract the original extension from the header
                                var originalExt = ""
                                val cdMatch = Regex("filename=\"?([^\";]+)\"?").find(contentDisposition ?: "")
                                if (cdMatch != null) {
                                    originalExt = cdMatch.groupValues[1].substringAfterLast('.', "")
                                }
                                if (originalExt.isBlank()) {
                                    originalExt = originalFileName.substringAfterLast('.', "")
                                }
                                originalExt = originalExt.lowercase().trim()

                                // 2. Check Magic Bytes and deep-inspect ZIPs for Office Docs
                                val magic = bytes.take(4).toByteArray()
                                var actualExt = originalExt

                                if (magic.size >= 4 && magic[0] == 0x25.toByte() && magic[1] == 0x50.toByte() && magic[2] == 0x44.toByte() && magic[3] == 0x46.toByte()) {
                                    actualExt = "pdf"
                                } else if (magic.size >= 4 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() && magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()) {
                                    actualExt = "zip" // Default to zip
                                    try {
                                        // Deep inspect the ZIP in-memory to see if it's an OOXML Document
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

                                    // If peek failed but VTOP header claimed it was office, trust the header
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

                                // 3. Force exact Android MIME type
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

                                // 4. Construct file name
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

                                // 5. Register with MediaStore
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
                                    NotificationHelper.showDownloadNotificationFromUri(
                                        context = context,
                                        uri = uri,
                                        fileName = finalName,
                                        mimeType = correctedMimeType,
                                        title = "Download Complete",
                                        description = "Tap to open $finalName"
                                    )
                                    Toast.makeText(context, "Saved $finalName", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Download rejected by server", Toast.LENGTH_SHORT).show() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
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

    LaunchedEffect(activeClient, sessionKey) {
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