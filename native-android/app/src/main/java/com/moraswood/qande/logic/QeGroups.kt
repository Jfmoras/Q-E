package com.moraswood.qande.logic

import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.ItemGroup
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeMath.num

/**
 * Feature F — item groups in Estimation.
 *
 * Decisions (PLAN.md):
 * - Group Rate/CFT and Rate/KOL are defaults: a member item's own rate wins when it is filled; V10.1 row rate groups
 *   inside an item still win over both.
 * - Group labour / polish / consumables / other are lumpsums kept at group level in the estimate. The group selling price
 *   (blank = members' selling + group lumpsums at cost) is distributed onto the quotation lines at Send to Quotation:
 *   each line = item selling + (group selling − Σ item selling) × item cost / Σ item cost (equal shares when every cost is 0),
 *   rounded to rupees with the rounding difference put on the largest-cost line, so the lines add up to the group price.
 * - No groups → every number is exactly V10.1's (same functions, same strings).
 */
object QeGroups {
    data class Sub(val cost: Double, val selling: Double, val profit: Double)
    data class GroupTotals(val itemsCost: Double, val extras: Double, val itemsSelling: Double, val cost: Double, val selling: Double, val profit: Double)

    fun groups(s: WorkspaceState): List<ItemGroup> = s.itemGroups.orEmpty()

    fun groupOf(s: WorkspaceState, i: Int): ItemGroup? = groups(s).firstOrNull { i in it.items }

    /** Costing with the group's default rates filled into blank item rates. */
    fun effective(c: Costing?, g: ItemGroup?): Costing {
        val x = c ?: Costing.blank()
        if (g == null) return x
        return x.copy(cftRate = x.cftRate.ifBlank { g.cftRate }, kolRate = x.kolRate.ifBlank { g.kolRate })
    }

    fun effectiveCosting(s: WorkspaceState, i: Int): Costing = effective(s.costings.getOrNull(i), groupOf(s, i))

    fun itemResult(s: WorkspaceState, i: Int): QeEstimate.Result = QeEstimate.costingResult(s.estimationItems[i], effectiveCosting(s, i))

    /** renderEstimation() side effect, group-aware: the automatic selling price follows the effective (group-default) cost. */
    fun syncCostings(s: WorkspaceState): WorkspaceState {
        if (groups(s).isEmpty()) return QeEstimate.syncCostings(s)
        if (s.estimationItems.isEmpty()) return s
        val costings = s.costings.toMutableList()
        s.estimationItems.forEachIndexed { i, item ->
            val c = costings.getOrNull(i) ?: Costing.blank()
            val r = QeEstimate.costingResult(item, effective(c, groupOf(s, i))).costing
            val synced = c.copy(sellingPrice = r.sellingPrice, sellingAuto = r.sellingAuto)
            if (i < costings.size) costings[i] = synced else costings.add(synced)
        }
        return if (costings == s.costings) s else s.copy(costings = costings)
    }

    fun groupTotals(s: WorkspaceState, g: ItemGroup): GroupTotals {
        val members = g.items.filter { it in s.estimationItems.indices }
        val rs = members.map { itemResult(s, it) }
        val itemsCost = rs.sumOf { it.cost }
        val itemsSelling = rs.sumOf { it.selling }
        val extras = num(g.labour) + num(g.polish) + num(g.consumables) + num(g.other)
        val cost = itemsCost + extras
        val selling = if (g.selling.isNotBlank()) num(g.selling) else itemsSelling + extras
        return GroupTotals(itemsCost, extras, itemsSelling, cost, selling, selling - cost)
    }

    fun ungroupedSub(s: WorkspaceState): Sub {
        var cost = 0.0; var selling = 0.0; var profit = 0.0
        s.estimationItems.indices.filter { groupOf(s, it) == null }.forEach { i ->
            val r = itemResult(s, i); cost += r.cost; selling += r.selling; profit += r.profit
        }
        return Sub(cost, selling, profit)
    }

    /** Grand total; with no groups it is `QeEstimate.estimateGrand` exactly. */
    fun grand(s: WorkspaceState): Sub {
        if (groups(s).isEmpty()) return QeEstimate.estimateGrand(s).let { Sub(it.cost, it.selling, it.profit) }
        val u = ungroupedSub(s)
        return groups(s).map { groupTotals(s, it) }.fold(u) { a, t -> Sub(a.cost + t.cost, a.selling + t.selling, a.profit + t.profit) }
    }

    /** Price of every estimation item as sent to the quotation (ungrouped: the V10.1 selling price string). */
    fun quotePrices(s: WorkspaceState): List<String> {
        val out = s.estimationItems.indices.map { s.costings.getOrNull(it)?.sellingPrice.orEmpty() }.toMutableList()
        for (g in groups(s)) {
            val members = g.items.filter { it in s.estimationItems.indices }
            if (members.isEmpty()) continue
            val t = groupTotals(s, g)
            val extra = t.selling - t.itemsSelling
            if (extra == 0.0) continue
            val rs = members.map { itemResult(s, it) }
            val totalCost = rs.sumOf { it.cost }
            val raw = members.indices.map { k ->
                val w = if (totalCost > 0) rs[k].cost / totalCost else 1.0 / members.size
                rs[k].selling + extra * w
            }
            val rounded = raw.map { Math.floor(it + 0.5) }.toMutableList()
            val diff = Math.floor(t.selling + 0.5) - rounded.sum()
            if (diff != 0.0) {
                val k = rs.indices.maxByOrNull { rs[it].cost } ?: 0
                rounded[k] += diff
            }
            members.forEachIndexed { k, i -> out[i] = QeEstimate.jsRoundString(rounded[k]) }
        }
        return out
    }

    // ---------------------------------------------------------------- editing

    private fun withGroups(s: WorkspaceState, gs: List<ItemGroup>): WorkspaceState = s.copy(itemGroups = gs.filter { it.items.isNotEmpty() }.ifEmpty { null })

    /** Group the selected items (moved out of any other group). Returns null when nothing valid is selected. */
    fun group(s: WorkspaceState, selected: Collection<Int>): WorkspaceState? {
        val sel = selected.filter { it in s.estimationItems.indices }.distinct().sorted()
        if (sel.isEmpty()) return null
        val existing = groups(s).map { g -> g.copy(items = g.items.filter { it !in sel }) }
        var n = groups(s).size + 1
        val used = groups(s).map { it.id }.toSet() + groups(s).map { it.name }.toSet()
        while ("ig-$n" in used || "Group $n" in used) n++
        return withGroups(s, existing + ItemGroup(id = "ig-$n", name = "Group $n", items = sel))
    }

    fun ungroup(s: WorkspaceState, id: String): WorkspaceState = withGroups(s, groups(s).filter { it.id != id })

    fun update(s: WorkspaceState, id: String, change: (ItemGroup) -> ItemGroup): WorkspaceState =
        withGroups(s, groups(s).map { if (it.id == id) change(it) else it })

    fun setField(g: ItemGroup, key: String, v: String): ItemGroup = when (key) {
        "name" -> g.copy(name = v); "cftRate" -> g.copy(cftRate = v); "kolRate" -> g.copy(kolRate = v)
        "labour" -> g.copy(labour = v); "polish" -> g.copy(polish = v); "consumables" -> g.copy(consumables = v)
        "other" -> g.copy(other = v); "selling" -> g.copy(selling = v)
        else -> g
    }
}
