package com.moraswood.qande.logic

/** Feature J — estimation categories. Items without a category (all V10.1 data) are Furniture. */
object QeCategories {
    const val FURNITURE = "furniture"
    const val DW = "dw"
    val ALL = listOf(FURNITURE to "Furniture", DW to "Doors & Windows")
    fun of(category: String?): String = if (category == DW) DW else FURNITURE
    fun label(category: String?): String = if (of(category) == DW) "Doors & Windows" else "Furniture"
}
