package com.moraswood.qande.data

/** app.js `hasProjectWork()` — decides whether "Continue Current Project" is shown. */
fun WorkspaceState.hasProjectWork(): Boolean =
    projectName.isNotEmpty() ||
        items.any { item -> item.name.isNotEmpty() || item.rows.any { it.l.isNotEmpty() || it.w.isNotEmpty() || it.t.isNotEmpty() || it.q.isNotEmpty() } } ||
        estimationItems.isNotEmpty() ||
        customer.name.isNotEmpty() ||
        quoteItems.any { it.description.isNotEmpty() || it.unitPrice.isNotEmpty() || it.images.isNotEmpty() }

/** app.js `normalizeUnits()` applied after every load. */
fun WorkspaceState.normalizeUnits(): WorkspaceState {
    fun Row.norm() = copy(lUnit = lUnit.ifEmpty { "ft" }, wUnit = wUnit.ifEmpty { "in" }, tUnit = tUnit.ifEmpty { "in" })
    return copy(
        items = items.map { it.copy(rows = it.rows.map { r -> r.norm() }) },
        estimationItems = estimationItems.map { it.copy(rows = it.rows.map { r -> r.norm() }) },
        costings = costings.map { it.copy(labourLengthUnit = it.labourLengthUnit.ifEmpty { "ft" }, labourWidthUnit = it.labourWidthUnit.ifEmpty { "ft" }) },
    )
}
