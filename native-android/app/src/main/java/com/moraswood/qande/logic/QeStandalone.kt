package com.moraswood.qande.logic

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Row
import com.moraswood.qande.with

/**
 * Standalone Timber Calculator (Home card, t40u): a quick one-off check with items, rows and CFT/KOL totals.
 * Pure, in-memory only — no Room / key-value writes, no project, no Send to Estimation. Same row edits as the project
 * calculator ([Row.with]) and the same [QeMath] totals; no math of its own.
 */
object QeStandalone {
    const val NOTE = "Quick check only — nothing here is saved. Use New Project to keep measurements."

    fun initial(): List<CalcItem> = listOf(CalcItem.blank())

    private fun item(items: List<CalcItem>, ii: Int, change: (CalcItem) -> CalcItem) =
        items.mapIndexed { i, it -> if (i == ii) change(it) else it }

    fun setName(items: List<CalcItem>, ii: Int, name: String) = item(items, ii) { it.copy(name = name) }
    fun setRowField(items: List<CalcItem>, ii: Int, ri: Int, key: String, value: String) =
        item(items, ii) { it.copy(rows = it.rows.mapIndexed { i, r -> if (i == ri) r.with(key, value) else r }) }
    fun addRow(items: List<CalcItem>, ii: Int) = item(items, ii) { it.copy(rows = it.rows + Row.blank()) }
    fun addItem(items: List<CalcItem>) = items + CalcItem.blank()

    fun totals(items: List<CalcItem>): QeMath.Totals = QeMath.grandTotals(items)
}
