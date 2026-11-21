package com.moraswood.qande.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.ui.theme.QeColors

/** The inline SVG brand logo from index.html (viewBox 108, rounded #111315 tile, ring, "MWI"). */
@Composable
fun MwiLogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(QeColors.LogoTile)) {
        val s = size.width / 108f
        drawCircle(QeColors.LogoRing, radius = 38f * s, center = Offset(54f * s, 54f * s), style = Stroke(width = 5f * s))
        fun p(vararg pts: Float) = Path().apply {
            moveTo(pts[0] * s, pts[1] * s)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
            close()
        }
        // M22 67V43h6l10 13 10-13h6v24h-6V52L38 64 28 52v15z
        drawPath(p(22f, 67f, 22f, 43f, 28f, 43f, 38f, 56f, 48f, 43f, 54f, 43f, 54f, 67f, 48f, 67f, 48f, 52f, 38f, 64f, 28f, 52f, 28f, 67f), QeColors.LogoInk)
        // M53 67V52h5l6 7 6-7h5v15h-6v-6l-5 6-5-6v6z
        drawPath(p(53f, 67f, 53f, 52f, 58f, 52f, 64f, 59f, 70f, 52f, 75f, 52f, 75f, 67f, 69f, 67f, 69f, 61f, 64f, 67f, 59f, 61f, 59f, 67f), QeColors.LogoInk)
        // M79 43h6v24h-6z
        drawRect(QeColors.LogoInk, topLeft = Offset(79f * s, 43f * s), size = Size(6f * s, 24f * s))
    }
}

/** Sticky `.topbar`: logo, "MORAS WOOD INDUSTRIES", subtitle, optional Home button. */
@Composable
fun QeTopBar(title: String = "MORAS WOOD INDUSTRIES", subtitle: String? = "Dashboard", homeLabel: String = "Home", showHome: Boolean, onHome: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(QeColors.Header).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MwiLogoMark()
            Column(Modifier.weight(1f)) {
                Text(title, color = QeColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, lineHeight = 21.sp)
                if (subtitle != null) Text(subtitle, color = QeColors.Muted, fontSize = 13.sp)
            }
            if (showHome) {
                OutlinedButton(
                    onClick = onHome,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, QeColors.HomeBtnBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = QeColors.HomeBtnBg, contentColor = QeColors.HomeBtnText),
                    modifier = Modifier.heightIn(min = 42.dp).widthIn(min = 72.dp),
                ) { Text(homeLabel, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.fillMaxWidth().size(1.dp).background(QeColors.HeaderLine))
    }
}

private val ButtonShape = RoundedCornerShape(11.dp)
private val ButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)

@Composable
fun QePrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = ButtonShape, contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(containerColor = QeColors.Accent, contentColor = QeColors.OnAccent),
        modifier = modifier.heightIn(min = 42.dp),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun QeSecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = ButtonShape, contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, QeColors.SecondaryBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = QeColors.SecondaryBg, contentColor = QeColors.SecondaryText),
        modifier = modifier.heightIn(min = 42.dp),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun QeDangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, shape = ButtonShape, contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, QeColors.DangerBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = QeColors.DangerBg, contentColor = QeColors.DangerText),
        modifier = modifier.heightIn(min = 42.dp),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

/** `.card` — #292f32, 1px --line border, radius 16. */
@Composable
fun QeCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(QeColors.Card)
            .border(1.dp, QeColors.Line, RoundedCornerShape(16.dp)).padding(padding),
        content = content,
    )
}

/** `.page-heading` h2 + p. */
@Composable
fun PageHeading(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(title, color = QeColors.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        if (subtitle != null) Text(subtitle, color = QeColors.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Temporary body for screens that later milestones port. */
@Composable
fun PortPendingCard(milestone: String) {
    QeCard {
        Text("Native port in progress", color = QeColors.Text, fontWeight = FontWeight.Bold)
        Text("This V10.1 screen is ported in the $milestone milestone.", color = QeColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** `.input` — #1e2427 bg, #515d60 border, radius 10, accent focus outline, placeholder #99a4a7. */
@Composable
fun QeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = androidx.compose.ui.text.TextStyle(color = QeColors.InputText, fontSize = 16.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(QeColors.Accent),
        modifier = modifier
            .onFocusChangedCompat { focused = it }
            .clip(RoundedCornerShape(10.dp))
            .background(QeColors.InputBg)
            .border(if (focused) 2.dp else 1.dp, if (focused) QeColors.Accent else QeColors.InputBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = QeColors.Placeholder, fontSize = 16.sp)
                inner()
            }
        },
    )
}

private fun Modifier.onFocusChangedCompat(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onChange(it.isFocused) }
