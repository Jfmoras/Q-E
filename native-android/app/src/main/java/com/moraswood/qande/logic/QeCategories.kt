package com.moraswood.qande.logic

import com.moraswood.qande.data.CategoryDefaults
import com.moraswood.qande.data.WorkspaceState

/**
 * Feature J — estimation categories. Items without a category (all V10.1 data, every calculator item) are Furniture;
 * builder items are Doors & Windows. Each category has its own default rates (blank item / group rates fall back to them)
 * and its own subtotal; the grand total and Send to Quotation cover both. A Furniture-only job without category defaults
 * takes exactly the V10.1 path (QeGroups.plainV101).
 */
object QeCategories {
    const val FURNITURE = "furniture"
    const val DW = "dw"
    val ALL = listOf(FURNITURE to "Furniture", DW to "Doors & Windows")
    fun of(category: String?): String = if (category == DW) DW else FURNITURE
    fun label(category: String?): String = if (of(category) == DW) "Doors & Windows" else "Furniture"
    /** Stored form: null for Furniture so V10.1 JSON stays unchanged. */
    fun stored(category: String): String? = if (of(category) == DW) DW else null

    fun itemCategory(s: WorkspaceState, i: Int): String = of(s.estimationItems.getOrNull(i)?.category)

    fun defaultsFor(s: WorkspaceState, i: Int): CategoryDefaults? =
        s.categoryDefaults?.get(itemCategory(s, i))?.takeIf { it != CategoryDefaults() }

    fun indices(s: WorkspaceState, cat: String): List<Int> = s.estimationItems.indices.filter { itemCategory(s, it) == cat }

    data class Sub(val count: Int, val cost: Double, val selling: Double, val profit: Double)

    /** Category subtotal: items' cost + lumpsums of groups inside the category; selling = what goes to the quotation. */
    fun subtotal(s: WorkspaceState, cat: String): Sub {
        val idx = indices(s, cat)
        var cost = idx.sumOf { QeGroups.itemResult(s, it).cost }
        for (g in QeGroups.groups(s)) {
            val m = g.items.filter { it in s.estimationItems.indices }
            if (m.isNotEmpty() && itemCategory(s, m[0]) == cat) {
                val t = QeGroups.groupTotals(s, g); cost += t.extras
            }
        }
        val ungroupedSelling = idx.filter { QeGroups.groupOf(s, it) == null }.sumOf { QeGroups.itemResult(s, it).selling }
        val groupedSelling = QeGroups.groups(s).filter { g -> g.items.firstOrNull { it in s.estimationItems.indices }?.let { itemCategory(s, it) == cat } == true }
            .sumOf { QeGroups.groupTotals(s, it).selling }
        val selling = if (QeGroups.groups(s).isEmpty()) idx.sumOf { QeGroups.itemResult(s, it).selling } else ungroupedSelling + groupedSelling
        return Sub(idx.size, cost, selling, selling - cost)
    }

    /** Group members must share a category. */
    fun sameCategory(s: WorkspaceState, selected: Collection<Int>): Boolean = selected.map { itemCategory(s, it) }.distinct().size <= 1

    /** Move an item to another category; it leaves any item group (groups live within one category). */
    fun move(s: WorkspaceState, i: Int, cat: String): WorkspaceState {
        if (i !in s.estimationItems.indices) return s
        val items = s.estimationItems.mapIndexed { j, it -> if (j == i) it.copy(category = stored(cat)) else it }
        val groups = s.itemGroups?.map { g -> g.copy(items = g.items.filter { it != i }) }?.filter { it.items.isNotEmpty() }?.ifEmpty { null }
        return s.copy(estimationItems = items, itemGroups = groups)
    }

    fun setDefault(s: WorkspaceState, cat: String, key: String, v: String): WorkspaceState {
        val cur = s.categoryDefaults.orEmpty()[cat] ?: CategoryDefaults()
        val next = when (key) {
            "cftRate" -> cur.copy(cftRate = v); "kolRate" -> cur.copy(kolRate = v)
            "labourRate" -> cur.copy(labourRate = v); "polishRate" -> cur.copy(polishRate = v)
            else -> cur
        }
        val map = (s.categoryDefaults.orEmpty() + (cat to next)).filterValues { it != CategoryDefaults() }
        return s.copy(categoryDefaults = map.ifEmpty { null })
    }

    /** Quotation lines in print order: Furniture first, then Doors & Windows, each with its heading (only when grouping). */
    fun <T> printOrder(items: List<T>, category: (T) -> String?, group: Boolean): List<Pair<String?, List<Pair<Int, T>>>> {
        val indexed = items.mapIndexed { i, x -> i to x }
        if (!group || indexed.map { of(category(it.second)) }.distinct().size <= 1) return listOf(null to indexed)
        return ALL.mapNotNull { (cat, label) -> indexed.filter { of(category(it.second)) == cat }.takeIf { it.isNotEmpty() }?.let { label to it } }
    }
}
