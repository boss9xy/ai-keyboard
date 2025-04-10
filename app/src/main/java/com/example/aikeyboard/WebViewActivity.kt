package com.example.aikeyboard

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class WebBrowserActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_RESULT_CODE = 1001
    private val handler = Handler(Looper.getMainLooper())

    // JavaScript Interface để xử lý tải xuống Blob
    private inner class DownloadJavaScriptInterface {
        @JavascriptInterface
        fun processBlobData(base64Data: String, suggestedFileName: String) {
            try {
                val extension = getExtensionFromMimeType(base64Data)
                val fileName = if (suggestedFileName.isNotEmpty() && suggestedFileName.contains(".")) {
                    suggestedFileName
                } else if (suggestedFileName.isNotEmpty()) {
                    "$suggestedFileName.$extension"
                } else {
                    "download_${System.currentTimeMillis()}.$extension"
                }
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                val dataStartIndex = base64Data.indexOf(",") + 1
                val decodedData = android.util.Base64.decode(base64Data.substring(dataStartIndex), android.util.Base64.DEFAULT)
                FileOutputStream(file).use { it.write(decodedData) }
                Logger.log("Blob saved successfully: ${file.absolutePath}")
                notifyDownloadComplete(file)
            } catch (e: Exception) {
                showToast("Lỗi xử lý tệp: ${e.message}")
                Logger.log("Error processing blob data", e)
            }
        }

        @JavascriptInterface
        fun onError(error: String) {
            showToast(error)
            Logger.log("JavaScript error: $error")
        }

        private fun getExtensionFromMimeType(base64Data: String): String {
            val mimeType = try {
                base64Data.substring(base64Data.indexOf(":") + 1, base64Data.indexOf(";"))
            } catch (e: Exception) {
                "txt" // Mặc định là txt nếu không xác định được
            }
            return when {
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("png") -> "png"
                mimeType.contains("pdf") -> "pdf"
                mimeType.contains("mp4") -> "mp4"
                mimeType.contains("text") -> "txt"
                else -> "bin"
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.initialize(this)
        setupUI()
        setupWebView()
        registerDownloadReceiver()

        val prefs = getSharedPreferences("AIKeyboardPrefs", MODE_PRIVATE)
        val webUrl = prefs.getString("web_url", "https://real-time-gpt-translator-45.lovable.app")
        webView.loadUrl(webUrl!!)
    }

    private fun setupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            max = 100
            visibility = View.GONE
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        layout.addView(progressBar)
        layout.addView(webView)
        setContentView(layout)
    }

    private fun setupWebView() {
        webView.apply {
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = 0
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    injectDownloadListener()
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    showToast("Lỗi tải trang: ${error?.description}")
                    Logger.log("WebView error: ${error?.description}")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    if (newProgress == 100) progressBar.visibility = View.GONE
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@WebBrowserActivity.filePathCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent()
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE)
                    return true
                }
            }

            addJavascriptInterface(DownloadJavaScriptInterface(), "downloadHandler")

            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                Logger.log("Download requested: $url, MIME: $mimeType, Content-Disposition: $contentDisposition")
                when {
                    url.startsWith("blob:") -> {
                        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType) ?: "file_${System.currentTimeMillis()}"
                        Logger.log("Blob URL detected: $url, name from Content-Disposition: $fileName")
                        // Truyền tên tệp từ contentDisposition vào JavaScript
                        webView.evaluateJavascript("""
                            (function() {
                                if (window.downloadHandler) {
                                    window.downloadHandler.onError('Using filename from contentDisposition: $fileName');
                                }
                            })();
                        """.trimIndent(), null)
                    }
                    url.startsWith("data:") -> {
                        val fileName = "data_${System.currentTimeMillis()}.${getExtensionFromDataUrl(url)}"
                        DownloadJavaScriptInterface().processBlobData(url, fileName)
                    }
                    else -> {
                        startDownload(url, contentDisposition, mimeType)
                    }
                }
            }
        }
    }

    // Tiêm JavaScript để xử lý Blob với tên tệp gốc và tránh trùng lặp
    private fun injectDownloadListener() {
        val script = """
            (function() {
                // Hàng đợi để xử lý nhiều Blob
                const downloadQueue = [];
                let isProcessing = false;
                const processedBlobs = new Set(); // Theo dõi các Blob đã xử lý để tránh trùng lặp

                // Hàm xử lý hàng đợi
                function processQueue() {
                    if (isProcessing || downloadQueue.length === 0) return;
                    isProcessing = true;
                    const { blob, fileName } = downloadQueue.shift();
                    
                    // Kiểm tra xem Blob đã được xử lý chưa
                    const blobId = blob.size + '_' + blob.type; // Định danh đơn giản cho Blob
                    if (processedBlobs.has(blobId)) {
                        console.log('Duplicate Blob detected, skipping: ' + fileName);
                        isProcessing = false;
                        processQueue();
                        return;
                    }
                    processedBlobs.add(blobId);

                    const reader = new FileReader();
                    reader.onloadend = function() {
                        if (reader.result) {
                            window.downloadHandler.processBlobData(reader.result, fileName);
                        }
                        isProcessing = false;
                        processQueue(); // Tiếp tục xử lý mục tiếp theo
                    };
                    reader.onerror = function() {
                        window.downloadHandler.onError('Error reading blob: ' + reader.error);
                        isProcessing = false;
                        processQueue();
                    };
                    reader.readAsDataURL(blob);
                }

                // Ghi đè window.URL.createObjectURL để bắt Blob và lấy tên tệp
                const originalCreateObjectURL = window.URL.createObjectURL;
                window.URL.createObjectURL = function(blob) {
                    let fileName = 'download_' + Date.now();
                    const activeElement = document.activeElement;
                    if (activeElement && activeElement.tagName === 'A' && activeElement.download) {
                        fileName = activeElement.download;
                    }
                    downloadQueue.push({ blob, fileName });
                    processQueue();
                    return originalCreateObjectURL(blob);
                };

                // Bắt sự kiện click trên thẻ <a> để debug (không xử lý Blob trực tiếp)
                document.addEventListener('click', function(e) {
                    const link = e.target.closest('a[href]');
                    if (link && link.href && link.href.startsWith('blob:')) {
                        e.preventDefault();
                        const fileName = link.download || link.href.split('/').pop() || 'download';
                        console.log('Blob link clicked: ' + link.href + ', name: ' + fileName);
                    }
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
        Logger.log("Injected Blob download listener script with original filename and deduplication")
    }

    private fun startDownload(url: String, contentDisposition: String?, mimeType: String?) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                setDescription("Đang tải xuống...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType))
            }
            val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            showToast("Đang tải xuống bằng DownloadManager...")
            Logger.log("Download started via DownloadManager: $url")
        } catch (e: Exception) {
            showToast("Lỗi tải xuống: ${e.message}")
            Logger.log("Download error", e)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                showToast("Không thể mở tệp")
                Logger.log("Failed to open file after download error", e)
            }
        }
    }

    private fun registerDownloadReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                if (id != -1L) {
                    val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    val query = DownloadManager.Query().setFilterById(id)
                    downloadManager.query(query).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uri = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)))
                                val file = File(uri.path!!)
                                notifyDownloadComplete(file)
                            }
                        }
                    }
                }
            }
        }
        registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun notifyDownloadComplete(file: File) {
        showToast("Tải xuống hoàn tất: ${file.name} tại Downloads")
        Logger.log("Download completed: ${file.absolutePath}")
    }

    private fun getMimeType(file: File): String? {
        val extension = file.extension.lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun getExtensionFromDataUrl(dataUrl: String): String {
        val mimeType = dataUrl.substring(dataUrl.indexOf(":") + 1, dataUrl.indexOf(";"))
        return when {
            mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
            mimeType.contains("png") -> "png"
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("text") -> "txt"
            else -> "bin"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            filePathCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            filePathCallback = null
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun showToast(message: String) {
        handler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}