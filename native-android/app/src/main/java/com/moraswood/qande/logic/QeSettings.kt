package com.moraswood.qande.logic

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.net.URI

/** app.js `aiProviders`, `detectAiProvider`, `updateDetectedProvider` texts and the `saveAiSettings` validation (V10.1). */
object QeAi {
    data class Provider(val label: String, val endpoint: String, val model: String)

    const val DEFAULT_ENDPOINT = "https://api.openai.com/v1"
    const val DEFAULT_MODEL = "gpt-4o-mini"
    const val DETECTION_IDLE = "Provider detection starts when you paste an API key."
    const val INTRO = "Paste a key and the app will recognize common provider key formats and suggest an endpoint and model. Keys that cannot be identified still work with a compatible API when you enter its endpoint and model. Model access depends on your provider account. Requests go directly to the configured provider; Android encrypts the key on this device."

    val PROVIDERS = linkedMapOf(
        "openai" to Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        "anthropic" to Provider("Anthropic", "https://api.anthropic.com/v1/messages", "claude-haiku-5-5"),
        "google" to Provider("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
        "groq" to Provider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        "openrouter" to Provider("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
        "xai" to Provider("xAI", "https://api.x.ai/v1", "grok-4.7"),
        "perplexity" to Provider("Perplexity", "https://api.perplexity.ai", "sonar"),
        "generic" to Provider("Unrecognized provider", "https://api.openai.com/v1", "gpt-4o-mini"),
    )

    fun detect(apiKey: String): String {
        val key = apiKey.trim()
        return when {
            key.startsWith("sk-or-v1-") -> "openrouter"
            key.startsWith("sk-ant-") -> "anthropic"
            key.startsWith("AIza") -> "google"
            key.startsWith("gsk_") -> "groq"
            key.startsWith("xai-") -> "xai"
            key.startsWith("pplx-") -> "perplexity"
            key.startsWith("sk-") -> "openai"
            key.isNotEmpty() -> "generic"
            else -> ""
        }
    }

    /** `#aiDetectionStatus` text after typing/pasting `apiKey` (untrimmed, as app.js tests the raw value). */
    fun detectionText(apiKey: String): String {
        val id = detect(apiKey)
        if (id.isEmpty()) return DETECTION_IDLE
        val p = PROVIDERS.getValue(id)
        return when {
            id == "generic" -> "Key format not recognized. You can still use it with a compatible provider; enter its endpoint and model below."
            id == "openai" && apiKey.startsWith("sk-") && !apiKey.startsWith("sk-proj-") && !apiKey.startsWith("sk-svcacct-") ->
                "This sk- prefix is shared by multiple providers. Using OpenAI-compatible defaults; verify the endpoint and model for your key."
            else -> "Detected ${p.label}. Suggested model: ${p.model}. Check your provider account’s model access."
        }
    }

    /** `loadAiSettingsFields()` detection text for the saved provider. */
    fun savedText(provider: String): String =
        PROVIDERS[provider]?.let { "Saved key is configured for ${it.label}." } ?: "Saved key provider is not recognized; configure its endpoint and model."

    fun statusText(hasKey: Boolean): String = if (hasKey) "API key saved on this device." else "No API key saved."

    /** `saveAiSettings()` front-end checks; returns the V10.1 error or null. */
    fun validate(endpoint: String, model: String): String? {
        val uri = runCatching { URI(endpoint).also { require(it.scheme != null && (it.host != null || it.scheme != "https")) } }.getOrNull()
            ?: return "Enter a valid API endpoint URL."
        if (uri.scheme.lowercase() != "https") return "The API endpoint must use HTTPS so your key is not sent insecurely."
        if (model.isEmpty()) return "Enter a model name for your AI provider."
        return null
    }
}

/** app.js `converterUnits` + `updateLengthConversion()`. */
object LengthConverter {
    data class Unit(val code: String, val label: String, val metres: Double)

    val UNITS = listOf(
        Unit("mm", "Millimetres (mm)", 0.001), Unit("cm", "Centimetres (cm)", 0.01), Unit("m", "Metres (m)", 1.0),
        Unit("in", "Inches (in)", 0.0254), Unit("ft", "Feet (ft)", 0.3048), Unit("yd", "Yards (yd)", 0.9144), Unit("mi", "Miles (mi)", 1609.344),
    )
    const val EMPTY = "Enter a value to convert."
    const val INTRO = "Convert freely between millimetres, centimetres, metres, inches, feet, yards and miles."

    /** `Number(value)` for an `<input type=number>` value, or null when not finite / empty. */
    fun number(value: String): Double? {
        if (value.isEmpty()) return null
        val t = value.trim()
        if (t.isEmpty()) return 0.0
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** `Number(converted.toPrecision(10))` */
    fun toPrecision10(x: Double): Double = if (x == 0.0 || !x.isFinite()) x else BigDecimal(x).round(MathContext(10, RoundingMode.HALF_UP)).toDouble()

    fun convert(value: Double, from: String, to: String): Double {
        val f = UNITS.first { it.code == from }; val t = UNITS.first { it.code == to }
        return value * f.metres / t.metres
    }

    /**
     * Result line. [localeFormat] = `Number.toLocaleString(undefined,{maximumFractionDigits:n})` for the device locale
     * (Android passes android.icu NumberFormat with half-up rounding, the same ICU data the V10.1 WebView used).
     */
    fun result(value: String, from: String, to: String, localeFormat: (Double, Int) -> String): String {
        val n = number(value) ?: return EMPTY
        val converted = toPrecision10(convert(n, from, to))
        return "${localeFormat(n, 3)} $from = ${localeFormat(converted, 8)} $to"
    }

    /** en-US `toLocaleString` (tests / fallback). */
    fun enUs(d: Double, maxFraction: Int): String {
        val bd = BigDecimal(d.toString()).setScale(maxFraction, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = bd.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val frac = plain.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        val neg = bd.signum() < 0
        return (if (neg) "-" else "") + grouped + (if (frac.isNotEmpty()) ".$frac" else "")
    }
}
