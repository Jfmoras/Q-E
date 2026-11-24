package com.moraswood.qande.projects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.moraswood.qande.data.QeJson
import com.moraswood.qande.data.SavedProjectEntity
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeOps
import com.moraswood.qande.logic.QeQuote
import com.moraswood.qande.logic.QeRevisions
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Quotation export reads the ACTUAL saved quotation snapshot, not editable project-item copies. */
object ProjectExports {
    private fun latest(saved: SavedProjectEntity): WorkspaceState =
        QeJson.decodeFromString(WorkspaceState.serializer(), saved.snapshotJson).let(QeQuote::syncActiveOption)

    fun quotationText(saved: SavedProjectEntity): String {
        val s = latest(saved)
        val options = s.quoteOptions
        return buildString {
            appendLine("MORAS WOOD INDUSTRIES")
            appendLine("QUOTATION · R${saved.revision}")
            appendLine("Project: ${s.projectName.ifBlank { saved.name }}")
            appendLine("Customer: ${s.customer.name}")
            appendLine()
            options.forEachIndexed { index, opt ->
                if (options.size > 1) appendLine("OPTION ${index + 1}${opt.name.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}")
                opt.items.forEachIndexed { row, item ->
                    appendLine("${row + 1}. ${item.description}")
                    appendLine("   ${item.qty} ${item.unit} × ${QeOps.fmt(item.unitPrice.toDoubleOrNull() ?: 0.0)} = ${QeOps.fmt((item.qty.toDoubleOrNull() ?: 0.0) * (item.unitPrice.toDoubleOrNull() ?: 0.0))}")
                    item.species?.takeIf { it.isNotBlank() }?.let { appendLine("   Wood: $it") }
                }
                val t = QeQuote.totalsForOption(opt)
                appendLine("Subtotal: ${QeOps.fmt(t.subtotal)}")
                appendLine("Discount: ${QeOps.fmt(t.discount)}")
                appendLine("GST: ${QeOps.fmt(t.gst)}")
                appendLine("TOTAL: ${QeOps.fmt(t.grand)}")
                appendLine()
            }
            if (s.quote.notes.isNotEmpty()) {
                appendLine("Notes / Terms")
                s.quote.notes.filter { it.isNotBlank() }.forEach { appendLine(it) }
            }
        }
    }

    fun quotationRows(saved: SavedProjectEntity): List<List<Any>> {
        val s = latest(saved)
        val rows = mutableListOf<List<Any>>(
            listOf("MORAS WOOD INDUSTRIES - QUOTATION R${saved.revision}"),
            listOf("Project", s.projectName.ifBlank { saved.name }),
            listOf("Customer", s.customer.name),
            emptyList()
        )
        s.quoteOptions.forEachIndexed { i, option ->
            rows += listOf("OPTION ${i + 1}", option.name)
            rows += listOf("Description", "Quantity", "Unit", "Unit price (INR)", "Amount (INR)", "Wood species")
            option.items.forEach { line ->
                val q = line.qty.toDoubleOrNull() ?: 0.0
                val rate = line.unitPrice.toDoubleOrNull() ?: 0.0
                rows += listOf(line.description, q, line.unit, rate, q * rate, line.species ?: "")
            }
            val t = QeQuote.totalsForOption(option)
            rows += listOf("Subtotal", t.subtotal)
            rows += listOf("Discount", t.discount)
            rows += listOf("GST", t.gst)
            rows += listOf("Grand total", t.grand)
            rows += emptyList()
        }
        rows += listOf("Notes / Terms")
        s.quote.notes.filter { it.isNotBlank() }.forEach { rows += listOf(it) }
        return rows
    }

    fun paymentText(list: List<PpView>): String {
        val live = list.filter { it.status != com.moraswood.qande.logic.QeProjects.DELETED }
        val total = live.sumOf { it.money.total }
        val paid = live.sumOf { it.money.paid }
        return buildString {
            appendLine("MORAS WOOD INDUSTRIES - PAYMENTS")
            appendLine("Total project value: ${QeOps.fmt(total)}")
            appendLine("Collected: ${QeOps.fmt(paid)}")
            appendLine("Pending: ${QeOps.fmt(total - paid)}")
            appendLine()
            live.forEach { v ->
                appendLine("${v.project.name} (${v.project.customer})")
                appendLine("Total ${QeOps.fmt(v.money.total)} | Received ${QeOps.fmt(v.money.paid)} | Pending ${QeOps.fmt(v.money.remaining)}")
            }
        }
    }

    /** Standalone Excel file with no new dependency; numeric cells are numbers, not string amounts. */
    fun excel(out: OutputStream, rows: List<List<Any>>, name: String) {
        val xmlName = name.take(31).ifBlank { "Sheet1" }
        fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
            .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
        fun cellColumn(index: Int): String {
            var n = index + 1
            var result = ""
            while (n > 0) { n--; result = ('A' + n % 26) + result; n /= 26 }
            return result
        }
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { rowIndex, row ->
                append("<row r=\"${rowIndex + 1}\">")
                row.forEachIndexed { colIndex, cell ->
                    append("<c r=\"${cellColumn(colIndex)}${rowIndex + 1}\"")
                    if (cell is Number) append("><v>$cell</v></c>")
                    else append(" t=\"inlineStr\"><is><t>${escape(cell.toString())}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        ZipOutputStream(out).use { zip ->
            fun add(path: String, data: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(data.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            add("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            add("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            add("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${escape(xmlName)}" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            add("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            add("xl/worksheets/sheet1.xml", sheet)
        }
    }

    fun paymentCsv(list: List<PpView>): String {
        fun csv(value: Any?): String = "\"" + value.toString().replace("\"", "\"\"") + "\""
        val rows = mutableListOf<List<Any>>(
            listOf("Project", "Customer", "Status", "Revision", "Total INR", "Collected INR", "Pending INR", "Payment date", "Payment INR", "Note")
        )
        list.filter { it.status != com.moraswood.qande.logic.QeProjects.DELETED }.forEach { v ->
            if (v.paymentRows.isEmpty()) rows += listOf(v.project.name, v.project.customer, v.status, v.revisionLabel, v.money.total, v.money.paid, v.money.remaining, "", "", "")
            v.paymentRows.forEach { p ->
                rows += listOf(v.project.name, v.project.customer, v.status, v.revisionLabel, v.money.total, v.money.paid, v.money.remaining, p.at.take(10), p.amount, p.note)
            }
        }
        return "\uFEFF" + rows.joinToString("\r\n") { row -> row.joinToString(",") { csv(it) } } + "\r\n"
    }

    fun copy(context: Context, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("Moras Q-E quotation", text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /** Android chooser includes WhatsApp when installed. Never sends without user confirmation. */
    fun shareText(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
