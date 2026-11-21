package com.moraswood.qande.logic

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Feature L — job QR codes: payload `qe://job/<uuid>`, generated with ZXing core (pure Java, no camera). */
object QeQr {
    const val PREFIX = "qe://job/"
    private val UUID_RE = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun payload(uuid: String) = PREFIX + uuid.lowercase()

    /** The job UUID in a scanned text (the qe:// payload or a bare UUID), else null. */
    fun parse(text: String?): String? {
        val t = text?.trim() ?: return null
        val id = if (t.startsWith(PREFIX, ignoreCase = true)) t.substring(PREFIX.length).trimEnd('/') else t
        return id.takeIf { UUID_RE.matches(it) }?.lowercase()
    }

    /** Square module grid (true = dark) incl. a 2-module quiet zone, error correction M. */
    fun matrix(text: String, size: Int = 0): Array<BooleanArray> {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        return Array(m.height) { y -> BooleanArray(m.width) { x -> m.get(x, y) } }
    }
}
