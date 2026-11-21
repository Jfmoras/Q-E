package com.moraswood.qande.logic

import kotlin.math.hypot

/**
 * Feature Q — pure part of the signature pad: strokes are lists of points in pad pixels. Validation rejects an accidental
 * tap; [fit] maps the inked bounding box into the exported bitmap with a margin, keeping the aspect ratio.
 */
object QeSignature {
    data class P(val x: Float, val y: Float)
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }
    data class Fit(val scale: Float, val dx: Float, val dy: Float)

    const val OUT_W = 900
    const val OUT_H = 300
    const val MIN_INK = 40f // pad px of total stroke length
    const val EMPTY = "Please sign in the box first."

    fun inkLength(strokes: List<List<P>>): Float =
        strokes.sumOf { s -> s.zipWithNext { a, b -> hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }.sum() }.toFloat()

    fun isSigned(strokes: List<List<P>>): Boolean = inkLength(strokes) >= MIN_INK

    fun bounds(strokes: List<List<P>>): Box? {
        val pts = strokes.flatten()
        if (pts.isEmpty()) return null
        return Box(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    /** Scale + offset that centre [b] inside [w]×[h] leaving [margin] on every side; a flat line never divides by 0. */
    fun fit(b: Box, w: Int = OUT_W, h: Int = OUT_H, margin: Float = 16f): Fit {
        val aw = w - 2 * margin; val ah = h - 2 * margin
        val s = minOf(aw / maxOf(b.width, 1f), ah / maxOf(b.height, 1f))
        return Fit(s, margin + (aw - b.width * s) / 2 - b.left * s, margin + (ah - b.height * s) / 2 - b.top * s)
    }

    /** Print caption under the image. */
    fun caption(signer: String, signedAt: String): String =
        listOf(signer.trim().ifEmpty { null }, signedAt.take(10).ifEmpty { null }).filterNotNull().joinToString(" · ")
}
