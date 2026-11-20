package com.moraswood.qande

/** Length in feet; cross section in inches. Preserve fractional results until final display. */
object TimberMath {
    fun cft(lengthFeet: Double, widthInches: Double, thicknessInches: Double, quantity: Int): Double {
        require(lengthFeet >= 0 && widthInches >= 0 && thicknessInches >= 0 && quantity >= 0)
        return lengthFeet * widthInches * thicknessInches * quantity / 144.0
    }
    fun kol(lengthFeet: Double, widthInches: Double, thicknessInches: Double, quantity: Int): Double {
        require(lengthFeet >= 0 && widthInches >= 0 && thicknessInches >= 0 && quantity >= 0)
        return lengthFeet * widthInches * thicknessInches * quantity / 37.5
    }
    fun volume(lengthFeet: Double, widthInches: Double, thicknessInches: Double, quantity: Int): Double =
        if (widthInches <= 6.0) cft(lengthFeet, widthInches, thicknessInches, quantity)
        else kol(lengthFeet, widthInches, thicknessInches, quantity)
}
