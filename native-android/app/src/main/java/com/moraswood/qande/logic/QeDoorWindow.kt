package com.moraswood.qande.logic

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.DwDefaults
import com.moraswood.qande.data.DwStructure
import com.moraswood.qande.data.Row
import com.moraswood.qande.logic.QeMath.num
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * Feature I — door / window frame geometry (pure Kotlin, inches internally).
 *
 * Kinds: main door 2 long + 2 short; bedroom door 2 long + 1 short; other door 2 long + 2 short (counts overridable on every
 * kind); window 2 long + 2 short + 1 or 2 centre supports. Wood "W x T" = face width × thickness in inches.
 * Mounting:
 *  - JUTTU (default): longs run full height, shorts sit between them (width − 2 × dimension). Both get the
 *    "Juttu extension per end" (structure value, else the Settings default, placeholder 3 in) added at each end.
 *  - SCREW: shorts run full width, longs sit between them (height − 2 × dimension), no extension.
 *  The subtracted "dimension" defaults to the member thickness (3 in for 11x3) and can be overridden.
 * Window centre support(s) = height − 2 × dimension (no Juttu extension).
 * Wastage per cut (0, ¼ or ½ in; structure → job → Settings default) is added to every cut length before CFT.
 * Architrave (optional): door = 1 header (opening width + 2 × architrave width) + 2 jambs (opening height + architrave width);
 *  window = the same 3 pieces, plus a bottom piece (= header length) when "include bottom piece" is ticked.
 * Planking (optional): side supports 4/5 in (default 5); middle = width − 2 × side; count = ceil(middle ÷ plank width); the last
 *  plank is trimmed to what is left and listed as its own row; plank length = inner height between the shorts
 *  = height − (shorts at the ends, max 2) × dimension, both mounting styles; thickness defaults to the frame thickness.
 * Quantity per structure multiplies every piece. CFT = cut length (ft) × width (in) × thickness (in) ÷ 144 × qty (V10.1 calcRow).
 */
object QeDoorWindow {
    val KINDS = listOf("main_door" to "Main door", "bedroom_door" to "Bedroom door", "other_door" to "Other door", "window" to "Window")
    val WOODS = listOf("5x3" to "5 x 3", "11x2" to "11 x 2", "4.5x2.5" to "4.5 x 2.5", "custom" to "Custom")
    val WASTAGE = listOf("0" to "0", "0.25" to "1/4 in", "0.5" to "1/2 in")
    val PLANK_WIDTHS = listOf("4", "5", "6", "8", "10", "12", "custom")
    const val GROUP_FRAME = "frame"
    const val GROUP_PLANK = "plank"
    const val GROUP_ARCH = "architrave"
    const val GROUP_WINDOW = "window_support"
    val GROUP_ORDER = listOf(GROUP_FRAME, GROUP_PLANK, GROUP_ARCH, GROUP_WINDOW)

    data class Piece(
        val group: String, val label: String,
        val finishedIn: Double, val cutIn: Double, val widthIn: Double, val thicknessIn: Double,
        val qty: Int, val cft: Double,
    )

    data class Result(val pieces: List<Piece>, val totalCft: Double, val totalPieces: Int, val warnings: List<String>)

    fun kindLabel(kind: String) = KINDS.firstOrNull { it.first == kind }?.second ?: "Other door"
    fun isWindow(s: DwStructure) = s.kind == "window"

    fun defaultLongs(kind: String) = 2
    fun defaultShorts(kind: String) = if (kind == "bedroom_door") 1 else 2

    /** Member face width × thickness (inches). */
    fun wood(s: DwStructure): Pair<Double, Double> = when (s.wood) {
        "custom" -> num(s.woodW) to num(s.woodT)
        else -> s.wood.split("x").let { num(it.getOrNull(0)) to num(it.getOrNull(1)) }
    }

    fun longs(s: DwStructure) = s.longs.trim().toIntOrNull()?.coerceAtLeast(0) ?: defaultLongs(s.kind)
    fun shorts(s: DwStructure) = s.shorts.trim().toIntOrNull()?.coerceAtLeast(0) ?: defaultShorts(s.kind)
    fun quantity(s: DwStructure) = num(s.quantity).toInt().coerceAtLeast(0)
    fun dimension(s: DwStructure) = if (s.subtractDim.isBlank()) wood(s).second else num(s.subtractDim)
    fun juttuExt(s: DwStructure, d: DwDefaults) = if (s.juttuExt.isBlank()) num(d.juttuExt) else num(s.juttuExt)
    fun wastage(s: DwStructure, jobWastage: String?, d: DwDefaults) = when {
        s.wastage.isNotBlank() -> num(s.wastage)
        !jobWastage.isNullOrBlank() -> num(jobWastage)
        else -> num(d.wastage)
    }
    fun plankWidth(s: DwStructure) = if (s.plankWidth == "custom") num(s.plankWidthCustom) else num(s.plankWidth)

    fun cft(cutIn: Double, w: Double, t: Double, qty: Int) = cutIn / 12 * w * t / 144 * qty

    fun compute(s: DwStructure, jobWastage: String? = null, d: DwDefaults = DwDefaults()): Result {
        val h = QeMath.toInches(s.height, s.heightUnit.ifEmpty { "ft" })
        val w = QeMath.toInches(s.width, s.widthUnit.ifEmpty { "ft" })
        val (fw, ft) = wood(s)
        val dim = dimension(s)
        val qty = quantity(s)
        val waste = wastage(s, jobWastage, d)
        val warn = mutableListOf<String>()
        val out = mutableListOf<Piece>()
        fun add(group: String, label: String, len: Double, pw: Double, pt: Double, count: Int) {
            if (count <= 0) return
            if (len <= 0) { warn += "$label: length works out to ${QeFormat.toFixed(len, 2)} in — check the sizes."; return }
            val cut = len + waste
            out += Piece(group, label, len, cut, pw, pt, count * qty, cft(cut, pw, pt, count * qty))
        }
        if (h <= 0 || w <= 0) warn += "Enter the overall length (height) and width."
        if (fw <= 0 || ft <= 0) warn += "Enter the wood size (width × thickness)."
        if (h > 0 && w > 0 && fw > 0 && ft > 0) {
            val nl = longs(s); val ns = shorts(s)
            if (s.mounting == "screw") {
                add(GROUP_FRAME, "Long (side)", h - 2 * dim, fw, ft, nl)
                add(GROUP_FRAME, "Short (top/bottom)", w, fw, ft, ns)
            } else {
                val ext = juttuExt(s, d)
                add(GROUP_FRAME, "Long (side)", h + 2 * ext, fw, ft, nl)
                add(GROUP_FRAME, "Short (top/bottom)", w - 2 * dim + 2 * ext, fw, ft, ns)
            }
            if (isWindow(s)) add(GROUP_WINDOW, "Centre support", h - 2 * dim, fw, ft, if (s.supports.trim() == "2") 2 else 1)
            if (s.planking && !isWindow(s)) {
                val side = num(s.plankSide).takeIf { it > 0 } ?: 5.0
                val pw = plankWidth(s)
                val pt = if (s.plankT.isBlank()) ft else num(s.plankT)
                val middle = w - 2 * side
                val plankLen = h - min(ns, 2) * dim
                when {
                    pw <= 0 -> warn += "Enter the plank width."
                    pt <= 0 -> warn += "Enter the plank thickness."
                    middle <= 0 -> warn += "Planking: width minus side supports is ${QeFormat.toFixed(middle, 2)} in — no planks."
                    else -> {
                        val count = ceil(middle / pw - 1e-9).toInt()
                        val full = floor(middle / pw + 1e-9).toInt().coerceAtMost(count)
                        val rest = middle - full * pw
                        add(GROUP_PLANK, "Plank", plankLen, pw, pt, full)
                        if (count > full && rest > 1e-9) add(GROUP_PLANK, "Plank (trimmed)", plankLen, rest, pt, 1)
                    }
                }
            }
            if (s.architrave) {
                val aw = num(s.archW); val at = num(s.archT)
                if (aw <= 0 || at <= 0) warn += "Enter the architrave width and thickness."
                else {
                    add(GROUP_ARCH, "Architrave header", w + 2 * aw, aw, at, 1)
                    add(GROUP_ARCH, "Architrave jamb", h + aw, aw, at, 2)
                    if (isWindow(s) && s.archBottom) add(GROUP_ARCH, "Architrave bottom", w + 2 * aw, aw, at, 1)
                }
            }
        }
        return Result(out, out.sumOf { it.cft }, out.sumOf { it.qty }, warn)
    }

    /** "Geometry assumptions" panel lines for one structure. */
    fun assumptions(s: DwStructure, jobWastage: String?, d: DwDefaults): List<String> {
        val (fw, ft) = wood(s)
        val dim = dimension(s)
        return buildList {
            add("${kindLabel(s.kind)}: ${longs(s)} long + ${shorts(s)} short" + if (isWindow(s)) " + ${if (s.supports.trim() == "2") 2 else 1} centre support(s)" else "")
            add("Wood ${fmt(fw)} × ${fmt(ft)} in (face width × thickness)")
            if (s.mounting == "screw") add("SCREW: shorts run full width; longs = height − 2 × ${fmt(dim)} in; no extension")
            else add("JUTTU: longs = height + 2 × ${fmt(juttuExt(s, d))} in extension; shorts = width − 2 × ${fmt(dim)} in + 2 × extension" +
                if (s.juttuExt.isBlank()) " (extension from Settings default — placeholder, please confirm)" else "")
            add("Subtracted dimension ${fmt(dim)} in" + if (s.subtractDim.isBlank()) " (defaults to the thickness — please confirm)" else "")
            add("Wastage ${fmt(wastage(s, jobWastage, d))} in added to every cut")
            if (isWindow(s)) add("Centre support = height − 2 × ${fmt(dim)} in")
            if (s.planking && !isWindow(s)) add("Planks: middle = width − 2 × ${fmt(num(s.plankSide).takeIf { it > 0 } ?: 5.0)} in; length = height − ${min(shorts(s), 2)} × ${fmt(dim)} in (inner height between shorts — please confirm)")
            if (s.architrave) add("Architrave: jambs = height + width; header = opening width + 2 × width" + if (isWindow(s)) (if (s.archBottom) "; bottom piece included" else "; no bottom piece") else "")
            add("Quantity ${quantity(s)} multiplies every piece")
        }
    }

    /** "Add to calculator": one CFT calculator item per structure, one editable row per piece (length in inches). */
    fun toCalcItem(s: DwStructure, r: Result): CalcItem = CalcItem(
        name = title(s),
        rows = r.pieces.map { p -> Row(fmt(p.cutIn), "in", fmt(p.widthIn), "in", fmt(p.thicknessIn), "in", p.qty.toString(), "CFT") },
        tag = "",
    )

    fun title(s: DwStructure): String {
        val size = "${s.width.ifBlank { "?" }}${unitMark(s.widthUnit)} × ${s.height.ifBlank { "?" }}${unitMark(s.heightUnit)}"
        return (s.name.ifBlank { kindLabel(s.kind) } + " " + size).trim()
    }

    private fun unitMark(u: String) = when (u.ifEmpty { "ft" }) { "ft" -> "ft"; "in" -> "in"; else -> " $u" }

    fun fmt(x: Double): String = QeFormat.toFixed(x, 3).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
