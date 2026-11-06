package com.chanelbox.l1

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val FILE_REQ = 7
    private val BG = Color.parseColor("#14161c")

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var setup: LinearLayout
    private lateinit var input: EditText
    private lateinit var msg: TextView
    private lateinit var chrome: WebChromeClient

    private var baseHost: String? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        buildWeb()
        buildSetup()
        root.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(setup, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)

        val saved = prefs.getString("url", null)
        if (saved == null) showSetup(null) else openUrl(saved)
    }

    // ---------- WebView ----------
    private fun buildWeb() {
        web = WebView(this)
        web.setBackgroundColor(BG)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false   // cho phép tự phát bài kế tiếp
            allowFileAccess = false
        }
        CookieManager.getInstance().setAcceptCookie(true)   // giữ phiên đăng nhập

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == baseHost) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) {}
                return true                                  // link ra ngoài → mở bằng trình duyệt
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showSetup("Không kết nối được máy chủ.\n${error.description}")
            }
        }

        chrome = object : WebChromeClient() {
            // chọn file để upload
            override fun onShowFileChooser(
                view: WebView, cb: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try {
                    startActivityForResult(params.createIntent(), FILE_REQ); true
                } catch (e: Exception) { fileCb = null; false }
            }
            // video toàn màn hình
            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                customView = view; customCb = callback
                web.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
            override fun onHideCustomView() {
                customView?.let { root.removeView(it) }
                customCb?.onCustomViewHidden()
                customView = null; customCb = null
                web.visibility = View.VISIBLE
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        web.webChromeClient = chrome

        // tải file về thư mục Downloads (gửi kèm cookie đăng nhập)
        web.setDownloadListener { url, userAgent, contentDisposition, mime, _ ->
            val name = URLUtil.guessFileName(url, contentDisposition, mime)
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Đang tải: $name", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- màn hình nhập địa chỉ ----------
    private fun buildSetup() {
        setup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(BG)
        }
        fun label(t: String, size: Float, color: String) = TextView(this).apply {
            text = t; textSize = size; gravity = Gravity.CENTER
            setTextColor(Color.parseColor(color)); setPadding(0, dp(6), 0, dp(6))
        }
        setup.addView(label("Chanelbox L1", 26f, "#e8e9ed"))
        setup.addView(label("Nhập địa chỉ máy chủ (IP:cổng)", 14f, "#8b909b"))

        input = EditText(this).apply {
            hint = "192.168.1.10:8082"
            setHintTextColor(Color.parseColor("#565c72"))
            setTextColor(Color.parseColor("#e8e9ed"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> connect(); true }
        }
        setup.addView(input, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })
        setup.addView(Button(this).apply {
            text = "Kết nối"
            setOnClickListener { connect() }
        }, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        msg = label("", 13f, "#f0575b")
        setup.addView(msg)
        setup.addView(label("Có thể gõ cả đường dẫn trang, ví dụ 192.168.1.10:8082/xem_video.html", 12f, "#565c72"))
    }

    private fun showSetup(error: String?) {
        web.stopLoading()
        web.visibility = View.GONE
        setup.visibility = View.VISIBLE
        input.setText(prefs.getString("url", "") ?: "")
        input.setSelection(input.text.length)
        msg.text = error ?: ""
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty() || s.contains(' ')) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return if (Uri.parse(s).host.isNullOrEmpty()) null else s
    }

    private fun connect() {
        val url = normalize(input.text.toString())
        if (url == null) { msg.text = "Địa chỉ không hợp lệ"; return }
        prefs.edit().putString("url", url).apply()
        openUrl(url)
    }

    private fun openUrl(url: String) {
        baseHost = Uri.parse(url).host
        setup.visibility = View.GONE
        web.visibility = View.VISIBLE
        web.loadUrl(url)
    }

    // ---------- vòng đời & phím Back ----------
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQ) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCb = null
        } else {
            @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            customView != null -> chrome.onHideCustomView()
            setup.visibility == View.VISIBLE ->
                prefs.getString("url", null)?.let { openUrl(it) } ?: finish()
            web.canGoBack() -> web.goBack()
            else -> AlertDialog.Builder(this)
                .setItems(arrayOf("Đổi địa chỉ máy chủ", "Thoát ứng dụng")) { _, i ->
                    if (i == 0) showSetup(null) else finish()
                }.show()
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()   // lưu phiên đăng nhập; KHÔNG pause WebView để nhạc không bị ngắt
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
