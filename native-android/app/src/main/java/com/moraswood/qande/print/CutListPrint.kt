package com.moraswood.qande.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.moraswood.qande.logic.QeCutList
import com.moraswood.qande.logic.QeDoorWindow
import com.moraswood.qande.logic.QeFormat
import java.io.FileOutputStream

/** Feature K — A4 bilingual cut list through PrintManager (same PdfDocument approach as the quotation sheet). */
object CutListPrint {
    fun print(context: Context, sheet: QeCutList.Sheet) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        manager.print("Moras Wood Cut List", object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel.isCanceled) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder("Moras Wood Cut List.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), old != new)
            }
            override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, cb: WriteResultCallback) {
                try {
                    val doc = CutListSheet(sheet).render()
                    try {
                        if (cancel.isCanceled) { cb.onWriteCancelled(); return }
                        FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                    } finally { doc.close() }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) { cb.onWriteFailed(e.message) }
            }
        }, attrs)
    }
}

class CutListSheet(private val sheet: QeCutList.Sheet) {
    private fun mm(v: Float) = v * 72f / 25.4f
    private val pageW = 595; private val pageH = 842
    private val contentW = mm(190f)
    private val left = (pageW - contentW) / 2f
    private val top = mm(10f); private val bottom = pageH - mm(12f)
    private val ink = Color.parseColor("#1c1917")
    private val brown = Color.parseColor("#6a4029")
    private val line = Color.parseColor("#b8a89a")
    private val shade = Color.parseColor("#f3ece6")
    private val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = 0f
    private var pageNo = 0

    /** Column widths (mm): piece, length, width, thickness, qty, species, CFT = 190. */
    private val colW = floatArrayOf(52f, 30f, 20f, 22f, 18f, 28f, 20f)

    private fun tp(size: Float, bold: Boolean = false, color: Int = ink) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = if (bold) sansBold else sans; this.color = color }
    private fun layout(text: String, paint: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(1, width.toInt())).setAlignment(align).setIncludePad(false).build()
    private fun draw(l: StaticLayout, x: Float, yTop: Float) { canvas.save(); canvas.translate(x, yTop); l.draw(canvas); canvas.restore() }
    private fun fill(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.FILL }
    private fun stroke(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; style = Paint.Style.STROKE; strokeWidth = w }

    private fun newPage() {
        page?.let { footer(); doc.finishPage(it) }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        canvas = page!!.canvas
        y = top
    }

    private fun footer() {
        val f = layout("${QeCutList.TITLE} · ${sheet.job} · ${pageNo}", tp(7f, color = brown), contentW, Layout.Alignment.ALIGN_CENTER)
        draw(f, left, pageH - mm(9f))
    }

    private fun ensure(h: Float): Boolean { if (y + h > bottom && y > top + 0.5f) { newPage(); return true }; return false }

    fun render(): PdfDocument {
        newPage()
        draw(layout("MORAS WOOD INDUSTRIES", tp(14f, true, brown), contentW), left, y); y += mm(7f)
        val title = layout(QeCutList.TITLE, tp(16f, true), contentW); draw(title, left, y); y += title.height + mm(3f)
        val meta = layout("${QeCutList.JOB}: ${sheet.job}\n${QeCutList.DATE}: ${sheet.date}\n${QeCutList.TOTAL_PIECES}: ${sheet.totalPieces}   ·   ${QeCutList.TOTAL_CFT}: ${QeFormat.toFixed(sheet.totalCft, 3)}", tp(9.5f), contentW)
        draw(meta, left, y); y += meta.height + mm(4f)
        if (sheet.entries.isEmpty()) {
            draw(layout(QeCutList.NONE, tp(10f), contentW), left, y)
        } else {
            val eh = layout(QeCutList.ENTRIES, tp(10.5f, true), contentW); draw(eh, left, y); y += eh.height + mm(1.5f)
            sheet.entries.forEach { e ->
                val l = layout("• $e", tp(9f), contentW); ensure(l.height + mm(1f)); draw(l, left, y); y += l.height + mm(1f)
            }
            y += mm(3f)
            sheet.sections.forEach { section(it) }
        }
        footer()
        page?.let { doc.finishPage(it) }
        return doc
    }

    private fun cells(texts: List<String>, paint: TextPaint): List<StaticLayout> = texts.mapIndexed { i, t ->
        layout(t, paint, mm(colW[i]) - mm(3f), if (i in 1..4 || i == 6) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
    }

    private fun rowHeight(ls: List<StaticLayout>) = ls.maxOf { it.height } + mm(3f)

    private fun drawRow(ls: List<StaticLayout>, h: Float, bg: Int? = null) {
        if (bg != null) canvas.drawRect(left, y, left + contentW, y + h, fill(bg))
        var x = left
        ls.forEachIndexed { i, l -> draw(l, x + mm(1.5f), y + mm(1.5f)); x += mm(colW[i]) }
        canvas.drawLine(left, y + h, left + contentW, y + h, stroke(line, 0.6f))
        y += h
    }

    private fun header(): Pair<List<StaticLayout>, Float> { val ls = cells(QeCutList.COLUMNS, tp(7.6f, true)); return ls to rowHeight(ls) }

    private fun section(s: QeCutList.Section) {
        val t = layout(s.title, tp(11f, true, brown), contentW)
        val (hl, hh) = header()
        val first = s.rows.firstOrNull()?.let { rowCells(it) }
        // keep the section title, header and first row together
        ensure(t.height + mm(2f) + hh + (first?.let { rowHeight(it) } ?: 0f))
        draw(t, left, y); y += t.height + mm(2f)
        drawRow(hl, hh, shade)
        s.rows.forEach { r ->
            val ls = rowCells(r); val h = rowHeight(ls)
            // rows never split across pages: move the whole row (and repeat the header)
            if (ensure(h)) { val (hl2, hh2) = header(); drawRow(hl2, hh2, shade) }
            drawRow(ls, h)
        }
        val tot = cells(listOf(QeCutList.SECTION_TOTAL, "", "", "", "${s.pieces}", QeCutList.PIECES, QeFormat.toFixed(s.cft, 3)), tp(8.5f, true))
        val th = rowHeight(tot); ensure(th); drawRow(tot, th, shade)
        y += mm(5f)
    }

    private fun rowCells(r: QeCutList.CutRow) = cells(listOf(
        "${r.piece}\n${r.source}",
        "${QeDoorWindow.fmt(r.lengthIn)} in",
        "${QeDoorWindow.fmt(r.widthIn)} in",
        "${QeDoorWindow.fmt(r.thicknessIn)} in",
        "${r.qty}",
        r.species.ifEmpty { "—" },
        QeFormat.toFixed(r.cft, 3),
    ), tp(8.5f))
}
