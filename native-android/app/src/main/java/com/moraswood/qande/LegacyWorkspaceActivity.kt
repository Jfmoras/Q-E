package com.moraswood.qande

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * Compatibility workspace: the complete legacy calculator, quotation and operations modules.
 * Uses WebView storage, NOT the native Room database. Do not treat this as data migration.
 */
class LegacyWorkspaceActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingBackup: String? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }
    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { it.write((pendingBackup ?: "").toByteArray()) }
        } catch (e: Exception) { toast("Backup failed: ${e.message}") }
        pendingBackup = null
    }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val data = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            web.evaluateJavascript("window.QEImportBackupData(${JSONObject.quote(data)})", null)
        } catch (e: Exception) { toast("Import failed: ${e.message}") }
    }
    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()
    private val loader by lazy {
        WebViewAssetLoader.Builder().setDomain("appassets.androidplatform.net")
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this)).build()
    }
    inner class Bridge {
        @JavascriptInterface fun printQuotation() = runOnUiThread {
            (getSystemService(Context.PRINT_SERVICE) as PrintManager).print(
                "MWI Quotation", web.createPrintDocumentAdapter("MWI Quotation"),
                PrintAttributes.Builder().build()
            )
        }
        @JavascriptInterface fun exportBackup(content: String) = runOnUiThread {
            pendingBackup = content
            exportBackup.launch("mwi-backup.json")
        }
        @JavascriptInterface fun importBackup() = runOnUiThread {
            importBackup.launch(arrayOf("application/json", "text/plain"))
        }
    }
    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.addJavascriptInterface(Bridge(), "QENative")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                loader.shouldInterceptRequest(request.url) ?: super.shouldInterceptRequest(view, request)
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    filePicker.launch(params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    callback?.onReceiveValue(null)
                    toast("File picker failed: ${e.message}")
                    false
                }
            }
        }
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/index.html")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })
    }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
