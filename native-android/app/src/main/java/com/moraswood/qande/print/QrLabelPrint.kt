package com.moraswood.qande.print

import android.content.Context
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
import com.moraswood.qande.logic.QeQr
import java.io.FileOutputStream

/** Feature L — A4 page with one 60 mm job label (QR + job name + revision + id) at the top left, ready to cut out. */
object QrLabelPrint {
    fun print(context: Context, title: String, uuid: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        pm.print("Moras Wood Job Label", object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, c: CancellationSignal, cb: LayoutResultCallback, e: Bundle?) {
                if (c.isCanceled) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder("Moras Wood Job Label.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), old != new)
            }
            override fun onWrite(p: Array<out PageRange>, d: ParcelFileDescriptor, c: CancellationSignal, cb: WriteResultCallback) {
                try {
                    val doc = render(title, uuid)
                    try { FileOutputStream(d.fileDescriptor).use { doc.writeTo(it) } } finally { doc.close() }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) { cb.onWriteFailed(e.message) }
            }
        }, attrs)
    }

    fun render(title: String, uuid: String): PdfDocument {
        fun mm(v: Float) = v * 72f / 25.4f
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val cv = page.canvas
        val x0 = mm(15f); val y0 = mm(15f); val w = mm(60f)
        cv.drawRect(RectF(x0, y0, x0 + w, y0 + mm(80f)), Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 0.6f })
        val m = QeQr.matrix(QeQr.payload(uuid))
        val cell = (w - mm(6f)) / m.size
        val black = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (y in m.indices) for (x in m[y].indices) if (m[y][x])
            cv.drawRect(x0 + mm(3f) + x * cell, y0 + mm(3f) + y * cell, x0 + mm(3f) + (x + 1) * cell, y0 + mm(3f) + (y + 1) * cell, black)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = Color.BLACK }
        val l = StaticLayout.Builder.obtain(title, 0, title.length, tp, (w - mm(6f)).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        cv.save(); cv.translate(x0 + mm(3f), y0 + w); l.draw(cv); cv.restore()
        val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 5.5f; color = Color.DKGRAY }
        val id = "MORAS WOOD · $uuid"
        val l2 = StaticLayout.Builder.obtain(id, 0, id.length, sp, (w - mm(6f)).toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        cv.save(); cv.translate(x0 + mm(3f), y0 + w + l.height + mm(1.5f)); l2.draw(cv); cv.restore()
        doc.finishPage(page)
        return doc
    }
}
