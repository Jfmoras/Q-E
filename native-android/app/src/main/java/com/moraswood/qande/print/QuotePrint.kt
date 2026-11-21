package com.moraswood.qande.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.moraswood.qande.R
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeFormat
import com.moraswood.qande.logic.QeMath.num
import com.moraswood.qande.logic.QeQuote
import java.io.FileOutputStream

/**
 * Native replacement for the V10.1 `window.print()` of `#printSheet` (styles.css `@media print`). Draws the same
 * A4 sheet (190 mm content width, 9 mm / 10 mm margins, Arial-like sans) with PdfDocument; Android's print dialog
 * offers both a printer and "Save as PDF". No WebView.
 */
object QuotePrint {
    /** V10.1 `printWebPage`: job "Moras Wood Q&E Quotation", document "Moras Wood Quotation". */
    fun print(context: Context, state: WorkspaceState) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        manager.print("Moras Wood Q&E Quotation", Adapter(context.applicationContext, state), attrs)
    }

    private class Adapter(private val context: Context, private val state: WorkspaceState) : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, cb: LayoutResultCallback, extras: Bundle?) {
            if (cancel.isCanceled) { cb.onLayoutCancelled(); return }
            val info = PrintDocumentInfo.Builder("Moras Wood Quotation.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
            cb.onLayoutFinished(info, old != new)
        }

        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, cb: WriteResultCallback) {
            try {
                val doc = QuoteSheet(context, state).render()
                try {
                    if (cancel.isCanceled) { cb.onWriteCancelled(); return }
                    FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                } finally { doc.close() }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                cb.onWriteFailed(e.message)
            }
        }
    }
}

/** Lays out and draws the V10.1 print sheet. Units are PDF points; [mm] converts CSS millimetres. */
class QuoteSheet(private val context: Context, private val state0: WorkspaceState) {
    private fun mm(v: Float) = v * 72f / 25.4f
    private val pageW = 595; private val pageH = 842
    private val left = (pageW - mm(190f)) / 2f
    private val contentW = mm(190f)
    private val top = mm(9f); private val bottom = pageH - mm(9f)

    private val ink = Color.parseColor("#1c1917")
    private val brown = Color.parseColor("#6a4029")
    private val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = 0f
    private var pageNo = 0

    private fun tp(size: Float, bold: Boolean = false, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; typeface = if (bold) sansBold else sans; this.color = color
    }

    private fun layout(text: String, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL, spacing: Float = 1f) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(1, width.toInt())).setAlignment(align)
            .setLineSpacing(0f, spacing).setIncludePad(false).build()

    private fun draw(l: StaticLayout, x: Float, yTop: Float) { canvas.save(); canvas.translate(x, yTop); l.draw(canvas); canvas.restore() }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        canvas = page!!.canvas
        y = top
    }

    /** CSS `break-inside:avoid`: start a new page when [h] does not fit (unless already at the top). */
    private fun ensure(h: Float): Boolean {
        if (y + h > bottom && y > top + 0.5f) { newPage(); return true }
        return false
    }

    /** `white-space:pre-line` */
    private fun preLine(s: String) = s.split("\n").joinToString("\n") { it.replace(Regex("[ \\t\\r\\f]+"), " ").trim() }
    /** Normal white-space collapsing. */
    private fun collapse(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private fun decode(dataUrl: String): Bitmap? = runCatching {
        val b64 = dataUrl.substringAfter("base64,", "")
        if (b64.isEmpty()) return null
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()

    fun render(): PdfDocument {
        val state = QeQuote.syncActiveOption(state0) // renderPrintSheet() calls syncActiveOption()
        newPage()
        header()
        titleRow(state)
        customerBar(state)
        // Feature D: the active option is the official quotation; others only as an opt-in comparison appendix.
        val order = com.moraswood.qande.logic.QeSpecies.printOrder(state, state.printComparison == true)
        order.forEachIndexed { k, (oi, opt) ->
            if (k == 1) appendixHeading()
            option(oi, opt)
        }
        notes(state.quote.notes.filter { it.isNotBlank() })
        footer()
        page?.let { doc.finishPage(it) }
        return doc
    }

    private fun header() {
        val h = mm(31f)
        val logo = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.mwi_logo) }.getOrNull()
        val logoSize = mm(27f)
        if (logo != null) {
            val scale = minOf(logoSize / logo.width, logoSize / logo.height)
            val w = logo.width * scale; val lh = logo.height * scale
            val lx = left + (logoSize - w) / 2; val ly = y + (h - lh) / 2
            canvas.drawBitmap(logo, null, RectF(lx, ly, lx + w, ly + lh), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val gap = mm(4f)
        val brandX = left + mm(30f) + gap
        val name = layout("MORAS WOOD INDUSTRIES", tp(15f, true), contentW)
        val tag = layout("SAWMILL | CUSTOM FURNITURE | WOODWORKS", tp(7.4f, true), contentW)
        val brandH = name.height + mm(2f) + tag.height
        val by = y + (h - brandH) / 2
        draw(name, brandX, by); draw(tag, brandX, by + name.height + mm(2f))
        val detailsW = mm(73f)
        val dx = left + contentW - detailsW
        val details = layout(
            "Moorukaveri, Kinnigoli, Mangaluru - 574150\n+91 63616 95416  |  info@moraswood.com\nGSTIN: 29AFPPM1242L1Z1",
            tp(7.5f), detailsW, spacing = 1.18f,
        )
        draw(details, dx, y + (h - details.height) / 2)
        y += h
    }

    private fun titleRow(state: WorkspaceState) {
        val title = layout("QUOTATION · R${QeQuote.jsNumber(state.revision.toDouble())}", tp(20f, true), contentW * 0.7f)
        val date = layout("Date: ${QeQuote.longDate()}", tp(8f, true), contentW * 0.4f, Layout.Alignment.ALIGN_OPPOSITE)
        val padT = mm(2.4f); val padB = mm(1.8f)
        val h = padT + title.height + padB
        canvas.drawLine(left, y + mm(0.25f), left + contentW, y + mm(0.25f), stroke(brown, mm(0.5f)))
        draw(title, left, y + padT)
        draw(date, left + contentW - contentW * 0.4f, y + padT + title.height - date.height - mm(1f))
        canvas.drawLine(left, y + h, left + contentW, y + h, stroke(Color.parseColor("#d6c8bb"), mm(0.25f)))
        y += h
    }

    private fun customerBar(state: WorkspaceState) {
        y += mm(3f)
        val padX = mm(2.5f); val padY = mm(2f)
        val label = layout("CUSTOMER DETAILS", tp(8f, true), mm(46f))
        val colW = contentW - padX * 2 - mm(46f)
        val nameL = layout(collapse(state.customer.name.ifEmpty { "-" }), tp(9.5f, true), colW)
        val locL = layout(collapse(state.customer.location.ifEmpty { "-" }), tp(9.5f), colW)
        val inner = nameL.height + locL.height
        val h = maxOf(mm(12f), inner + padY * 2)
        canvas.drawRect(left, y, left + contentW, y + h, fill(Color.parseColor("#f4f0eb")))
        canvas.drawRect(left, y, left + contentW, y + h, stroke(Color.parseColor("#ded5cb"), mm(0.25f)))
        draw(label, left + padX, y + (h - label.height) / 2)
        val cy = y + (h - inner) / 2
        draw(nameL, left + padX + mm(46f), cy); draw(locL, left + padX + mm(46f), cy + nameL.height)
        y += h
    }

    private val colW = floatArrayOf(14f, 90f, 21f, 31f, 34f)

    private fun tableHeader() {
        val p = tp(8f, true, Color.WHITE)
        val cells = listOf("Sl. No.", "Description", "Units", "Unit Price", "Total Price")
        val padX = mm(2f); val padY = mm(2.2f)
        val ls = cells.mapIndexed { i, s -> layout(s, p, mm(colW[i]) - padX * 2, Layout.Alignment.ALIGN_CENTER) }
        val h = ls.maxOf { it.height } + padY * 2
        var x = left
        ls.forEachIndexed { i, l ->
            val w = mm(colW[i])
            canvas.drawRect(x, y, x + w, y + h, fill(Color.parseColor("#5c351f")))
            canvas.drawRect(x, y, x + w, y + h, stroke(Color.parseColor("#86644f"), mm(0.25f)))
            draw(l, x + padX, y + padY)
            x += w
        }
        y += h
    }

    private fun tableHeaderHeight(): Float = layout("Sl. No.", tp(8f, true), mm(10f), Layout.Alignment.ALIGN_CENTER).height + mm(4.4f)

    private fun appendixHeading() {
        val l = layout("COMPARISON APPENDIX — other options for reference (not part of the quoted total)", tp(10f, true, brown), contentW)
        ensure(l.height + mm(20f))
        y += mm(4f)
        draw(l, left, y)
        y += l.height + mm(1f)
    }

    private fun option(oi: Int, opt: com.moraswood.qande.data.QuoteOption) {
        y += mm(18f * 0.75f * 25.4f / 72f) // .print-option margin 18px
        val t = QeQuote.totalsForOption(opt)
        val h3 = layout("OPTION ${oi + 1}: ${collapse(com.moraswood.qande.logic.QeSpecies.optionLabel(opt, oi))}", tp(11.25f, true), contentW)
        val h3Block = mm(3.7f) + h3.height + mm(1.3f) + mm(3.2f)
        ensure(h3Block + tableHeaderHeight() + mm(12f)) // h3 break-after:avoid
        y += mm(3.7f)
        draw(h3, left, y)
        y += h3.height + mm(1.3f)
        canvas.drawLine(left, y, left + contentW, y, stroke(Color.parseColor("#cccccc"), 0.75f))
        y += mm(3.2f)

        val imgs = QeQuote.optionImages(opt).mapNotNull(::decode)
        if (imgs.isNotEmpty()) images(imgs)

        y += mm(2f)
        ensure(tableHeaderHeight() + mm(10f))
        tableHeader()
        val padX = mm(2f); val padY = mm(2.6f)
        val cell = tp(9f); val bold = tp(9f, true)
        // Feature J: optional category headings (Furniture first); numbering runs on across categories.
        var n = 0
        com.moraswood.qande.logic.QeCategories.printOrder(opt.items, { it.category }, state0.quoteGroupByCategory == true).forEach { (heading, lines) ->
          if (heading != null) {
            val hl = layout(heading, tp(9.5f, true, brown), contentW - padX * 2)
            val hh = hl.height + padY * 2
            if (ensure(hh + mm(10f))) tableHeader()
            canvas.drawRect(left, y, left + contentW, y + hh, fill(Color.parseColor("#f4f0eb")))
            canvas.drawRect(left, y, left + contentW, y + hh, stroke(Color.parseColor("#ddd3ca"), mm(0.25f)))
            draw(hl, left + padX, y + padY)
            y += hh
          }
          lines.forEach { (_, x) ->
            val i = n++
            val texts = listOf(
                layout("${i + 1}", cell, mm(colW[0]) - padX * 2, Layout.Alignment.ALIGN_CENTER),
                layout(preLine(x.description.ifEmpty { "-" }) + (x.species?.takeIf { it.isNotBlank() && it != opt.species }?.let { "\nSpecies: $it" } ?: "") + (if (state0.printArea == true) x.area?.takeIf { it.isNotBlank() }?.let { "\nArea: $it sq.ft" } ?: "" else ""), bold, mm(colW[1]) - padX * 2),
                layout(collapse(QeQuote.unitsText(x)), cell, mm(colW[2]) - padX * 2, Layout.Alignment.ALIGN_OPPOSITE),
                layout(QeFormat.rs(num(x.unitPrice)), cell, mm(colW[3]) - padX * 2, Layout.Alignment.ALIGN_OPPOSITE),
                layout(QeFormat.rs(num(x.qty) * num(x.unitPrice)), cell, mm(colW[4]) - padX * 2, Layout.Alignment.ALIGN_OPPOSITE),
            )
            val h = texts.maxOf { it.height } + padY * 2
            if (ensure(h)) tableHeader() // tr break-inside:avoid; the header repeats like a printed <thead>
            var cx = left
            texts.forEachIndexed { c, l ->
                val w = mm(colW[c])
                canvas.drawRect(cx, y, cx + w, y + h, stroke(Color.parseColor("#ddd3ca"), mm(0.25f)))
                draw(l, cx + padX, y + padY)
                cx += w
            }
            y += h
          }
        }

        // .print-totals (58 mm, right) with the 86 mm GRAND TOTAL row.
        val rows = buildList {
            add("Total" to QeFormat.rs(t.subtotal))
            if (t.discount > 0) add("Discount" to "- " + QeFormat.rs(t.discount))
            add("GST (${QeQuote.jsNumber(num(opt.gstPercent))}%)" to QeFormat.rs(t.gst))
        }
        val rowP = tp(9f); val rowB = tp(9f, true)
        val grandP = tp(10f, true)
        val rowH = layout("X", rowP, 100f).height + mm(2.4f)
        val grandH = layout("X", grandP, 100f).height + mm(2.4f)
        y += mm(2f)
        ensure(rowH * rows.size + grandH)
        val tx = left + contentW - mm(58f)
        rows.forEach { (label, value) ->
            draw(layout(label, rowP, mm(30f)), tx + mm(2f), y + mm(1.2f))
            draw(layout(value, rowB, mm(54f), Layout.Alignment.ALIGN_OPPOSITE), left + contentW - mm(2f) - mm(54f), y + mm(1.2f))
            y += rowH
        }
        val gx = left + contentW - mm(86f)
        canvas.drawRect(gx, y, left + contentW, y + grandH, fill(Color.parseColor("#f4f0eb")))
        canvas.drawLine(gx, y + mm(0.25f), left + contentW, y + mm(0.25f), stroke(brown, mm(0.5f)))
        draw(layout("GRAND TOTAL", grandP, mm(40f)), gx + mm(2f), y + mm(1.2f))
        draw(layout(QeFormat.rs(t.grand), grandP, mm(80f), Layout.Alignment.ALIGN_OPPOSITE), left + contentW - mm(2f) - mm(80f), y + mm(1.2f))
        y += grandH
        y += mm(28f * 0.75f * 25.4f / 72f) // .print-option margin-bottom 28px
    }

    /** `.print-images`: one image up to 165×82 mm, otherwise a 2-column grid with max height 62 mm. */
    private fun images(imgs: List<Bitmap>) {
        val cssPx = mm(25.4f / 96f) // an image is never drawn larger than its natural CSS size
        y += mm(5f)
        if (imgs.size == 1) {
            val b = imgs[0]
            val s = minOf(mm(165f) / b.width, mm(82f) / b.height, cssPx)
            val w = b.width * s; val h = b.height * s
            val blockH = maxOf(mm(82f), h)
            ensure(blockH)
            val x = left + (contentW - w) / 2; val iy = y + (blockH - h) / 2
            canvas.drawBitmap(b, null, RectF(x, iy, x + w, iy + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += blockH
        } else {
            val gap = mm(3f)
            val cellW = (contentW - gap) / 2
            imgs.chunked(2).forEachIndexed { r, pair ->
                val sized = pair.map { b -> val s = minOf(cellW / b.width, mm(62f) / b.height, cssPx); Triple(b, b.width * s, b.height * s) }
                val rowH = maxOf(sized.maxOf { it.third }, if (imgs.size <= 2) mm(44f) else 0f)
                if (r > 0) y += gap
                ensure(rowH)
                sized.forEachIndexed { c, (b, w, h) ->
                    val cx = left + c * (cellW + gap) + (cellW - w) / 2
                    val cy = y + (rowH - h) / 2
                    canvas.drawBitmap(b, null, RectF(cx, cy, cx + w, cy + h), Paint(Paint.FILTER_BITMAP_FLAG))
                }
                y += rowH
            }
        }
        y += mm(4f)
    }

    private fun notes(notes: List<String>) {
        if (notes.isEmpty()) return
        val p = tp(8.2f); val title = tp(8.2f, true)
        val pad = mm(2f)
        val tl = layout("NOTES", title, contentW - pad * 2)
        val body = layout(notes.mapIndexed { i, x -> "${i + 1}. ${collapse(x)}" }.joinToString("\n"), p, contentW - pad * 2, spacing = 1.18f)
        val h = maxOf(mm(17f), pad * 2 + tl.height + mm(1f) + body.height)
        y += mm(2f)
        ensure(h)
        canvas.drawRect(left, y, left + contentW, y + h, stroke(Color.parseColor("#ddd3ca"), mm(0.25f)))
        draw(tl, left + pad, y + pad)
        draw(body, left + pad, y + pad + tl.height + mm(1f))
        y += h
    }

    private fun footer() {
        val s = layout("MORAS WOOD INDUSTRIES", tp(8.5f, true), contentW / 2)
        val r = layout("Digital copy - no signature required.", tp(8f), contentW / 2, Layout.Alignment.ALIGN_OPPOSITE)
        val h = maxOf(s.height, r.height).toFloat()
        y += mm(4.5f)
        ensure(h)
        draw(s, left, y + h - s.height)
        draw(r, left + contentW / 2, y + h - r.height)
        y += h
    }
}
