package com.moraswood.qande.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** V10.1 on-screen "charcoal" palette (styles.css `@media screen` overrides). */
object QeColors {
    val Bg = Color(0xFF171B1E)          // --bg
    val Header = Color(0xFF202629)      // .topbar
    val TabBar = Color(0xFF22282B)      // .tabs, .project-toolbar
    val Card = Color(0xFF292F32)        // --card
    val Soft = Color(0xFF23292C)        // --soft (subcard, summary, metric)
    val Cream = Color(0xFF343E3C)       // --cream (length result)
    val Line = Color(0xFF40494B)        // --line
    val HeaderLine = Color(0xFF465154)  // topbar / home-action border
    val CellLine = Color(0xFF465054)    // td border
    val Text = Color(0xFFF0F3F2)
    val HomeCardText = Color(0xFFF1F4F2)
    val HomeCardSub = Color(0xFFB8C3C5)
    val Muted = Color(0xFFAEB8BA)       // --muted
    val TabInactive = Color(0xFFC5CECF)
    val InputBg = Color(0xFF1E2427)
    val InputText = Color(0xFFF2F4F3)
    val InputBorder = Color(0xFF515D60)
    val Placeholder = Color(0xFF99A4A7)
    val Accent = Color(0xFFB8E47A)      // --brown2 / .primary / active tab
    val OnAccent = Color(0xFF19211A)
    val SecondaryBg = Color(0xFF343D40)
    val SecondaryBorder = Color(0xFF576266)
    val SecondaryText = Color(0xFFEEF2F1)
    val DangerBg = Color(0xFF473034)
    val DangerBorder = Color(0xFF92545A)
    val DangerText = Color(0xFFFFDADD)
    val GrandBg = Color(0xFF303B35)
    val GrandBorder = Color(0xFF52644B)
    val GrandText = Color(0xFFF3F6F2)
    val ThBg = Color(0xFF343D40)
    val ThText = Color(0xFFE0E7E5)
    val IconTile = Color(0xFF3B4A3E)
    val HomeBtnBg = Color(0xFF35413A)
    val HomeBtnText = Color(0xFFD4F5B3)
    val HomeBtnBorder = Color(0xFF60735B)
    val LogoTile = Color(0xFF111315)
    val LogoInk = Color(0xFFF4F4F4)
    val LogoRing = Color(0xFFEEEEEE)
    val Pill = Color(0xFF3B4A3E)
    val PillText = Color(0xFFC9E9B0)
    val OpsNavBorder = Color(0xFF526064)
    val OpsMuted = Color(0xFFB4BFC1)
    val ErrorBorder = Color(0xFFDC2626)
    val ErrorOutline = Color(0xFFFECACA)
    val ErrorText = Color(0xFFB91C1C)
    /** Estimation rate-group colours, in V10.1 order. */
    val RateGroupColors = listOf(Color(0xFFEF4444), Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFA855F7))
}

private val QeScheme = darkColorScheme(
    primary = QeColors.Accent,
    onPrimary = QeColors.OnAccent,
    secondary = QeColors.SecondaryBg,
    onSecondary = QeColors.SecondaryText,
    background = QeColors.Bg,
    onBackground = QeColors.Text,
    surface = QeColors.Card,
    onSurface = QeColors.Text,
    surfaceVariant = QeColors.Soft,
    onSurfaceVariant = QeColors.Muted,
    outline = QeColors.Line,
    error = QeColors.ErrorBorder,
)

@Composable
fun QeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = QeScheme, content = content)
}
