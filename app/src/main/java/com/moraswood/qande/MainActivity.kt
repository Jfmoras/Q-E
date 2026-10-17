package com.moraswood.qande

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.net.Uri
import android.app.Activity
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequest = 7024
    private val home = "https://appassets.androidplatform.net/index.html"
    private val aiExecutor = Executors.newSingleThreadExecutor()
    private val aiPreferences by lazy { getSharedPreferences("ai_settings", Context.MODE_PRIVATE) }

    companion object {
        private const val AI_KEY_NAME = "openai_api_key"
        private const val AI_KEY_ALIAS = "com.moraswood.qande.openai"
        private const val AI_ENDPOINT_NAME = "api_endpoint"
        private const val AI_MODEL_NAME = "model"
        private const val AI_PROVIDER_NAME = "provider"
        private const val DEFAULT_AI_ENDPOINT = "https://api.openai.com/v1"
        private const val DEFAULT_AI_MODEL = "gpt-4o-mini"
    }

    private val loader by lazy {
        WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun printQuotation() {
            runOnUiThread {
                val manager = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = web.createPrintDocumentAdapter("Moras Wood Quotation")
                manager.print("Moras Wood Q&E Quotation", adapter, PrintAttributes.Builder().build())
            }
        }

        @JavascriptInterface
        fun hasApiKey(): Boolean = aiPreferences.contains(AI_KEY_NAME)

        @JavascriptInterface
        fun getAiSettings(): String = JSONObject()
            .put("endpoint", aiPreferences.getString(AI_ENDPOINT_NAME, DEFAULT_AI_ENDPOINT))
            .put("model", aiPreferences.getString(AI_MODEL_NAME, DEFAULT_AI_MODEL))
            .put("provider", aiPreferences.getString(AI_PROVIDER_NAME, "openai"))
            .put("hasApiKey", hasApiKey())
            .toString()

        @JavascriptInterface
        fun saveAiSettings(settingsJson: String): String = try {
            val settings = JSONObject(settingsJson)
            val endpoint = settings.optString("endpoint").trim()
            val model = settings.optString("model").trim()
            val apiKey = settings.optString("apiKey").trim()
            val provider = settings.optString("provider", "generic").trim()
            val endpointUrl = URL(endpoint)
            require(endpointUrl.protocol == "https" && endpointUrl.host.isNotBlank()) {
                "The API endpoint must use HTTPS."
            }
            require(model.isNotEmpty()) { "Enter a model name for your AI provider." }
            val editor = aiPreferences.edit()
                .putString(AI_ENDPOINT_NAME, endpoint)
                .putString(AI_MODEL_NAME, model)
                .putString(AI_PROVIDER_NAME, provider)
            if (apiKey.isNotEmpty()) {
                editor.putString(AI_KEY_NAME, encryptApiKey(apiKey))
            }
            if (editor.commit()) "" else "Could not save AI settings to this device."
        } catch (error: Exception) {
            "Could not securely save AI settings: ${error.message ?: error.javaClass.simpleName}"
        }

        @JavascriptInterface
        fun clearApiKey(): String = try {
            val removed = aiPreferences.edit().remove(AI_KEY_NAME).commit()
            if (removed) "" else "Could not remove the API key from this device."
        } catch (error: Exception) {
            "Could not remove the API key: ${error.message ?: error.javaClass.simpleName}"
        }

        @JavascriptInterface
        fun improveDescription(text: String, style: String, requestId: String, provider: String) {
            aiExecutor.execute {
                try {
                    val key = aiPreferences.getString(AI_KEY_NAME, null)
                        ?.let(::decryptApiKey)
                        ?: throw IOException("Add an AI API key in Settings to use this feature.")
                    val endpoint = aiPreferences.getString(AI_ENDPOINT_NAME, DEFAULT_AI_ENDPOINT)
                        ?: DEFAULT_AI_ENDPOINT
                    val model = aiPreferences.getString(AI_MODEL_NAME, DEFAULT_AI_MODEL)
                        ?: DEFAULT_AI_MODEL
                    val result = requestDescription(key, provider, endpoint, model, text, style)
                    sendAiResult(requestId, true, result)
                } catch (error: Exception) {
                    sendAiResult(
                        requestId,
                        false,
                        error.message ?: "Could not improve the description."
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        web = WebView(this)
        web.setBackgroundColor(Color.rgb(47, 33, 24))
        setContentView(web)

        // Prevent Android 15+ edge-to-edge layout from overlapping the web header.
        window.statusBarColor = Color.rgb(47, 33, 24)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(web) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(web)

        setupWebView()

        if (savedInstanceState == null) {
            web.loadUrl(home)
        } else {
            web.restoreState(savedInstanceState)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) {
                    web.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @Deprecated("Used for the Android WebView file chooser result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileChooserRequest) return
        val callback = pendingFileChooser ?: return
        pendingFileChooser = null
        callback.onReceiveValue(
            if (resultCode == Activity.RESULT_OK)
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            else null
        )
    }

    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            databaseEnabled = true
        }

        web.addJavascriptInterface(NativeBridge(), "QENative")
        
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                // Allow internal asset domain
                if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net") {
                    return false
                }
                // Open external links in the system browser
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    view.context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback or log error
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView, 
                request: WebResourceRequest
            ): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(
                    "window.print = function() { QENative.printQuotation(); };", 
                    null
                )
            }
        }
        
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                pendingFileChooser?.onReceiveValue(null)
                pendingFileChooser = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams.createIntent(), fileChooserRequest)
                    true
                } catch (error: Exception) {
                    pendingFileChooser = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }
        }
    }

    private fun sendAiResult(requestId: String, success: Boolean, message: String) {
        if (!::web.isInitialized || isDestroyed) return
        val script = "window.QENativeAiResult(${JSONObject.quote(requestId)},$success,${JSONObject.quote(message)});"
        web.post { web.evaluateJavascript(script, null) }
    }

    private fun requestDescription(
        apiKey: String,
        provider: String,
        endpoint: String,
        model: String,
        text: String,
        style: String
    ): String {
        val styleName = when (style) {
            "concise" -> "short and clear"
            "detailed" -> "detailed"
            else -> "professional"
        }
        val prompt =
            "Rewrite the user's woodworking product description in a $styleName style for a customer quotation. Preserve all supplied facts, measurements, materials, and meaning. Do not invent specifications. Return only the improved description."
        val anthropic = provider == "anthropic"
        val google = provider == "google"
        val body = when {
            anthropic -> JSONObject()
                .put("model", model)
                .put("max_tokens", 350)
                .put("system", prompt)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
            google -> JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 350))
            else -> JSONObject()
                .put("model", model)
                .put("max_tokens", 350)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", prompt))
                        .put(JSONObject().put("role", "user").put("content", text))
                )
        }

        val apiUrl = when {
            anthropic -> messagesUrl(endpoint)
            google -> geminiUrl(endpoint, model)
            else -> chatCompletionsUrl(endpoint)
        }
        val connection = apiUrl.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            when {
                anthropic -> {
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                }
                google -> connection.setRequestProperty("x-goog-api-key", apiKey)
                else -> connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8).use { it?.readText().orEmpty() }
            
            if (status !in 200..299) {
                val providerMessage = try {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                val detail = providerMessage?.takeIf(String::isNotBlank)
                if (status == 404) {
                    val formatHint = when {
                        anthropic -> "Check that the Anthropic Messages API endpoint and model are valid."
                        google -> "Check that the Gemini generateContent endpoint and model are valid."
                        else -> "The app appends /chat/completions to base URLs; check that the provider supports OpenAI-compatible chat completions."
                    }
                    throw IOException("AI provider returned HTTP 404 from $apiUrl. $formatHint" + (detail?.let { " $it" } ?: ""))
                }
                throw IOException(
                    detail ?: if (response.isBlank()) {
                        "AI provider returned an empty HTTP $status response from $apiUrl."
                    } else {
                        "AI provider request failed ($status) at $apiUrl."
                    }
                )
            }
            if (response.isBlank()) throw IOException("AI provider returned an empty response.")
            val data = try {
                JSONObject(response)
            } catch (_: Exception) {
                throw IOException("AI provider returned an unreadable response. Verify the endpoint supports OpenAI-compatible chat completions.")
            }
            val result = when {
                anthropic -> data.optJSONArray("content")
                    ?.let { content ->
                        (0 until content.length()).mapNotNull { index ->
                            content.optJSONObject(index)
                                ?.takeIf { it.optString("type") == "text" }
                                ?.optString("text")
                        }.joinToString("\n")
                    }
                google -> data.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.let { parts ->
                        (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }
                            .joinToString("")
                    }
                else -> data.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
            }?.trim()
            return result
                ?.takeIf(String::isNotEmpty)
                ?: throw IOException("AI provider returned an empty description.")
        } finally {
            connection.disconnect()
        }
    }

    private fun chatCompletionsUrl(endpoint: String): URL {
        val configured = URL(endpoint)
        if (configured.protocol != "https") throw IOException("The API endpoint must use HTTPS.")
        val path = configured.path.trimEnd('/')
        val chatPath = if (path.endsWith("/chat/completions")) {
            path
        } else {
            "$path/chat/completions"
        }
        val uri = java.net.URI(
            configured.protocol,
            configured.userInfo,
            configured.host,
            configured.port,
            chatPath,
            configured.query,
            configured.ref
        )
        return uri.toURL()
    }

    private fun messagesUrl(endpoint: String): URL {
        val configured = URL(endpoint)
        if (configured.protocol != "https") throw IOException("The API endpoint must use HTTPS.")
        val path = configured.path.trimEnd('/')
        val messagesPath = if (path.endsWith("/messages")) path else "$path/messages"
        return java.net.URI(
            configured.protocol, configured.userInfo, configured.host, configured.port,
            messagesPath, configured.query, configured.ref
        ).toURL()
    }

    private fun geminiUrl(endpoint: String, model: String): URL {
        val configured = URL(endpoint)
        if (configured.protocol != "https") throw IOException("The API endpoint must use HTTPS.")
        val basePath = configured.path.trimEnd('/').removeSuffix("/models")
        val modelPath = "$basePath/models/${java.net.URLEncoder.encode(model, "UTF-8")}:generateContent"
        return java.net.URI(
            configured.protocol, configured.userInfo, configured.host, configured.port,
            modelPath, configured.query, configured.ref
        ).toURL()
    }

    private fun encryptApiKey(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAiKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decryptApiKey(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val ivLength = 12
        require(payload.size > ivLength) { "Saved API key data is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateAiKey(),
            GCMParameterSpec(128, payload.copyOfRange(0, ivLength))
        )
        return String(cipher.doFinal(payload.copyOfRange(ivLength, payload.size)), Charsets.UTF_8)
    }

    private fun getOrCreateAiKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(AI_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        AI_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) {
            web.saveState(outState)
        }
    }

    override fun onDestroy() {
        aiExecutor.shutdownNow()
        if (::web.isInitialized) {
            web.destroy()
        }
        super.onDestroy()
    }
}
