package com.moraswood.qande.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Port of the V10.1 MainActivity AI bridge (same SharedPreferences file/keys and AndroidKeyStore alias, so a key
 * saved by V10.1 keeps working after the update). The Settings form for these values is ported in milestone 7.
 */
class AiService(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    companion object {
        private const val AI_KEY_NAME = "openai_api_key"
        private const val AI_KEY_ALIAS = "com.moraswood.qande.openai"
        private const val AI_ENDPOINT_NAME = "api_endpoint"
        private const val AI_MODEL_NAME = "model"
        private const val AI_PROVIDER_NAME = "provider"
        const val DEFAULT_AI_ENDPOINT = "https://api.openai.com/v1"
        const val DEFAULT_AI_MODEL = "gpt-4o-mini"
    }

    fun hasApiKey(): Boolean = prefs.contains(AI_KEY_NAME)
    fun endpoint(): String = prefs.getString(AI_ENDPOINT_NAME, DEFAULT_AI_ENDPOINT) ?: DEFAULT_AI_ENDPOINT
    fun model(): String = prefs.getString(AI_MODEL_NAME, DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
    fun provider(): String = prefs.getString(AI_PROVIDER_NAME, "openai") ?: "openai"

    /** `saveAiSettings` bridge: returns "" on success or the V10.1 error text. */
    fun saveSettings(endpoint: String, model: String, apiKey: String, provider: String): String = try {
        val endpointUrl = URL(endpoint.trim())
        require(endpointUrl.protocol == "https" && endpointUrl.host.isNotBlank()) { "The API endpoint must use HTTPS." }
        require(model.trim().isNotEmpty()) { "Enter a model name for your AI provider." }
        val editor = prefs.edit().putString(AI_ENDPOINT_NAME, endpoint.trim()).putString(AI_MODEL_NAME, model.trim()).putString(AI_PROVIDER_NAME, provider.trim())
        if (apiKey.trim().isNotEmpty()) editor.putString(AI_KEY_NAME, encryptApiKey(apiKey.trim()))
        if (editor.commit()) "" else "Could not save AI settings to this device."
    } catch (error: Exception) {
        "Could not securely save AI settings: ${error.message ?: error.javaClass.simpleName}"
    }

    /** `clearApiKey` bridge. */
    fun clearApiKey(): String = try {
        if (prefs.edit().remove(AI_KEY_NAME).commit()) "" else "Could not remove the API key from this device."
    } catch (error: Exception) {
        "Could not remove the API key: ${error.message ?: error.javaClass.simpleName}"
    }

    /** `improveDescription(text,style)` with the V10.1 45 s timeout. Throws with the V10.1 messages. */
    suspend fun improveDescription(text: String, style: String): String = try {
        withTimeout(45_000) {
            withContext(Dispatchers.IO) {
                val key = prefs.getString(AI_KEY_NAME, null)?.let(::decryptApiKey)
                    ?: throw IOException("Add an AI API key in Settings to use this feature.")
                requestDescription(key, provider(), endpoint(), model(), text, style)
            }
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw IOException("AI request timed out. Check your connection and try again.")
    }

    private fun requestDescription(apiKey: String, provider: String, endpoint: String, model: String, text: String, style: String): String {
        val styleName = when (style) { "concise" -> "short and clear"; "detailed" -> "detailed"; else -> "professional" }
        val prompt =
            "Rewrite the user's woodworking product description in a $styleName style for a customer quotation. Preserve all supplied facts, measurements, materials, and meaning. Do not invent specifications. Return only the improved description."
        val anthropic = provider == "anthropic"
        val google = provider == "google"
        val body = when {
            anthropic -> JSONObject().put("model", model).put("max_tokens", 350).put("system", prompt)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
            google -> JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))))
                .put("generationConfig", JSONObject().put("maxOutputTokens", 350))
            else -> JSONObject().put("model", model).put("max_tokens", 350).put(
                "messages",
                JSONArray().put(JSONObject().put("role", "system").put("content", prompt)).put(JSONObject().put("role", "user").put("content", text)),
            )
        }
        val apiUrl = when { anthropic -> messagesUrl(endpoint); google -> geminiUrl(endpoint, model); else -> chatCompletionsUrl(endpoint) }
        val connection = apiUrl.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            when {
                anthropic -> { connection.setRequestProperty("x-api-key", apiKey); connection.setRequestProperty("anthropic-version", "2023-06-01") }
                google -> connection.setRequestProperty("x-goog-api-key", apiKey)
                else -> connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8).use { it?.readText().orEmpty() }
            if (status !in 200..299) {
                val providerMessage = try { JSONObject(response).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
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
                    detail ?: if (response.isBlank()) "AI provider returned an empty HTTP $status response from $apiUrl." else "AI provider request failed ($status) at $apiUrl.",
                )
            }
            if (response.isBlank()) throw IOException("AI provider returned an empty response.")
            val data = try { JSONObject(response) } catch (_: Exception) {
                throw IOException("AI provider returned an unreadable response. Verify the endpoint supports OpenAI-compatible chat completions.")
            }
            val result = when {
                anthropic -> data.optJSONArray("content")?.let { content ->
                    (0 until content.length()).mapNotNull { i -> content.optJSONObject(i)?.takeIf { it.optString("type") == "text" }?.optString("text") }.joinToString("\n")
                }
                google -> data.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.let { parts ->
                    (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }.joinToString("")
                }
                else -> data.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            }?.trim()
            return result?.takeIf(String::isNotEmpty) ?: throw IOException("AI provider returned an empty description.")
        } finally {
            connection.disconnect()
        }
    }

    private fun rebuild(configured: URL, path: String): URL =
        java.net.URI(configured.protocol, configured.userInfo, configured.host, configured.port, path, configured.query, configured.ref).toURL()

    private fun chatCompletionsUrl(endpoint: String): URL {
        val configured = URL(endpoint)
        if (configured.protocol != "https") throw IOException("The API endpoint must use HTTPS.")
        val path = configured.path.trimEnd('/')
        return rebuild(configured, if (path.endsWith("/chat/completions")) path else "$path/chat/completions")
    }

    private fun messagesUrl(endpoint: String): URL {
        val configured = URL(endpoint)
        if (configured.protocol != "https") throw IOException("The API endpoint must use HTTPS.")
        val path = configured.path.trimEnd('/')
        return rebuild(configured, if (path.endsWith("/messages")) path else "$path/messages")
    }

    private fun geminiUrl(endpoint: String, model: String): URL {
        val configured = URL(endpoint)
        if (configured.protocol != "https") throw IOException("The API endpoint must use HTTPS.")
        val basePath = configured.path.trimEnd('/').removeSuffix("/models")
        return rebuild(configured, "$basePath/models/${java.net.URLEncoder.encode(model, "UTF-8")}:generateContent")
    }

    private fun encryptApiKey(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAiKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptApiKey(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val ivLength = 12
        require(payload.size > ivLength) { "Saved API key data is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateAiKey(), GCMParameterSpec(128, payload.copyOfRange(0, ivLength)))
        return String(cipher.doFinal(payload.copyOfRange(ivLength, payload.size)), Charsets.UTF_8)
    }

    private fun getOrCreateAiKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(AI_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(AI_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }
}
