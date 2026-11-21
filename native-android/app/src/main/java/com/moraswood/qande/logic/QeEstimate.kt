package com.moraswood.qande.logic

import com.moraswood.qande.data.CalcItem
import com.moraswood.qande.data.Costing
import com.moraswood.qande.data.RateGroup
import com.moraswood.qande.data.Row
import com.moraswood.qande.data.WorkspaceState
import com.moraswood.qande.logic.QeMath.num

/** Exact ports of the V10.1 app.js estimation functions (labourAreaSqft … estimateGrand). */
object QeEstimate {
    /** V10.1 timber rate-group colours, in order. */
    val GroupColors = listOf("#ef4444", "#3b82f6", "#22c55e", "#f59e0b", "#a855f7")

    /** `labourAreaSqft(c)`: both labour measurements converted to feet × quantity. */
    fun labourAreaSqft(c: Costing): Double =
        QeMath.toFeet(c.labourLength, c.labourLengthUnit.ifEmpty { "ft" }) * QeMath.toFeet(c.labourWidth, c.labourWidthUnit.ifEmpty { "ft" }) * num(c.labourQty)

    /** `rateGroupForRow(c,j)`: first group containing row j (any mode). */
    fun rateGroupForRow(c: Costing, j: Int): RateGroup? = c.rateGroups.firstOrNull { j in it.rows }

    /** `effectiveTimberRate(c,row,j)` */
    fun effectiveTimberRate(c: Costing, row: Row, j: Int): String {
        val group = rateGroupForRow(c, j)
        if (group != null) return group.rate
        return if (row.mode == "CFT") c.cftRate else c.kolRate
    }

    data class GroupSummary(val volume: Double, val amount: Double)

    /** `groupSummary(item,g)`: only rows of the group's own mode count. */
    fun groupSummary(item: CalcItem, g: RateGroup): GroupSummary {
        val volume = g.rows.fold(0.0) { total, j ->
            val r = item.rows.getOrNull(j)
            total + (if (r != null && r.mode == g.mode) QeMath.calcRow(r).value else 0.0)
        }
        return GroupSummary(volume, volume * num(g.rate))
    }

    data class Result(
        val tot: QeMath.Totals,
        val surface: Double,
        val polishArea: Double,
        val wood: Double,
        val groupedWood: Double,
        val ungroupedWood: Double,
        val labourArea: Double,
        val labour: Double,
        val polish: Double,
        val cost: Double,
        val selling: Double,
        val profit: Double,
        val markup: Double,
        val margin: Double,
        /** The costing after the JS side effect on sellingPrice / sellingAuto. */
        val costing: Costing,
    )

    /** JS `String(Math.round(x))` for finite values. */
    fun jsRoundString(x: Double): String {
        val r = Math.floor(x + 0.5)
        return if (r == 0.0) "0" else if (Math.abs(r) < 1e21) java.math.BigDecimal(r).toPlainString() else r.toString()
    }

    /**
     * `costingResult(i)`. V10.1 mutates the costing: when the selling price is blank or still automatic it is
     * replaced by `cost ? String(Math.round(cost)) : ''` and `sellingAuto` becomes true. That updated costing is
     * returned in [Result.costing] so callers can store it exactly like the JS state.
     */
    fun costingResult(item: CalcItem, c0: Costing?): Result {
        var c = c0 ?: Costing.blank()
        val tot = QeMath.itemTotals(item)
        val surface = QeMath.itemSurface(item)
        val polishArea = num(c.polishAreaOverride).let { if (it != 0.0) it else surface }
        var grouped = 0.0
        var ungrouped = 0.0
        item.rows.forEachIndexed { j, row ->
            val amount = QeMath.calcRow(row).value * num(effectiveTimberRate(c, row, j))
            if (rateGroupForRow(c, j) != null) grouped += amount else ungrouped += amount
        }
        val wood = grouped + ungrouped
        val labourArea = labourAreaSqft(c)
        val labour = if (c.labourMode.ifEmpty { "sqft" } == "lumpsum") num(c.labourLumpsum) else labourArea * num(c.labourRate)
        val polish = if (c.polishMode == "sqft") polishArea * num(c.polishRate) else num(c.polishLumpsum)
        val cost = wood + labour + polish + num(c.consumables) + num(c.other)
        val sellingBlank = c.sellingPrice.trim().isEmpty()
        if (sellingBlank || c.sellingAuto == true) {
            c = c.copy(sellingPrice = if (cost != 0.0 && !cost.isNaN()) jsRoundString(cost) else "", sellingAuto = true)
        }
        val selling = num(c.sellingPrice)
        val profit = selling - cost
        return Result(
            tot, surface, polishArea, wood, grouped, ungrouped, labourArea, labour, polish, cost, selling, profit,
            markup = if (cost != 0.0 && !cost.isNaN()) profit / cost * 100 else 0.0,
            margin = if (selling != 0.0) profit / selling * 100 else 0.0,
            costing = c,
        )
    }

    data class Grand(val cost: Double, val selling: Double, val profit: Double)

    /** `estimateGrand()` */
    fun estimateGrand(s: WorkspaceState): Grand =
        s.estimationItems.foldIndexed(Grand(0.0, 0.0, 0.0)) { i, a, item ->
            val r = costingResult(item, s.costings.getOrNull(i))
            Grand(a.cost + r.cost, a.selling + r.selling, a.profit + r.profit)
        }

    /**
     * What renderEstimation() leaves in `state`: a costing for every estimation item
     * (`state.costings[i] = blankCosting()` when missing) with the selling-price side effect applied.
     */
    fun syncCostings(s: WorkspaceState): WorkspaceState {
        if (s.estimationItems.isEmpty()) return s
        val costings = s.costings.toMutableList()
        s.estimationItems.forEachIndexed { i, item ->
            val c = costings.getOrNull(i) ?: Costing.blank()
            val synced = costingResult(item, c).costing
            if (i < costings.size) costings[i] = synced else costings.add(synced)
        }
        return if (costings == s.costings) s else s.copy(costings = costings)
    }

    /** `originalMeasure(value,unit)`: "7 ft" or "-". */
    fun originalMeasure(value: String, unit: String): String = if (value.isNotEmpty()) "$value $unit" else "-"
}
