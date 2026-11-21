package com.moraswood.qande.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.data.SignatureEntity
import com.moraswood.qande.logic.QeSignature
import com.moraswood.qande.logic.QeSignature.P
import com.moraswood.qande.ui.components.QeDangerButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.components.QeTextField
import com.moraswood.qande.ui.theme.QeColors
import java.io.ByteArrayOutputStream

/** Feature Q: render strokes into the exported white PNG (base64, no wrap). */
fun signaturePng(strokes: List<List<P>>): String {
    val b = QeSignature.bounds(strokes) ?: return ""
    val f = QeSignature.fit(b)
    val bmp = Bitmap.createBitmap(QeSignature.OUT_W, QeSignature.OUT_H, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE; strokeWidth = 5f
        strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.filter { it.isNotEmpty() }.forEach { s ->
        val path = android.graphics.Path()
        path.moveTo(s[0].x * f.scale + f.dx, s[0].y * f.scale + f.dy)
        if (s.size == 1) path.lineTo(s[0].x * f.scale + f.dx + 0.1f, s[0].y * f.scale + f.dy)
        s.drop(1).forEach { path.lineTo(it.x * f.scale + f.dx, it.y * f.scale + f.dy) }
        c.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

fun signatureBitmap(png: String?): Bitmap? = png?.takeIf { it.isNotBlank() }?.let {
    runCatching { val bytes = Base64.decode(it, Base64.DEFAULT); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}

/** Quotation › Client signature row: thumbnail + Re-sign / Clear, or "Add client signature". */
@Composable
fun SignatureRow(signature: SignatureEntity?, defaultSigner: String, onSave: (String, String) -> Unit, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val bmp = remember(signature?.id) { signatureBitmap(signature?.png) }
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Client signature:", color = QeColors.Muted, fontSize = 14.sp)
        if (signature != null && bmp != null) {
            Image(bmp.asImageBitmap(), "Client signature", Modifier.width(120.dp).height(40.dp).background(Color.White).border(1.dp, QeColors.Line))
            QeSecondaryButton("Re-sign", onClick = { open = true })
            QeDangerButton("Clear", onClick = onClear)
        } else QeSecondaryButton("Add client signature", onClick = { open = true })
    }
    if (open) SignaturePadDialog(signature?.signer?.ifBlank { null } ?: defaultSigner, onDismiss = { open = false }) { png, signer -> onSave(png, signer); open = false }
}

@Composable
fun SignaturePadDialog(initialSigner: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val strokes = remember { mutableStateListOf<List<P>>() }
    var signer by remember { mutableStateOf(initialSigner) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Client signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(3f).background(Color.White).border(1.dp, QeColors.Line)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { o -> strokes.add(listOf(P(o.x, o.y))); error = "" },
                                onDrag = { ch, _ ->
                                    val p = ch.position
                                    if (strokes.isNotEmpty()) strokes[strokes.lastIndex] = strokes.last() + P(p.x, p.y)
                                    ch.consume()
                                },
                            )
                        },
                ) {
                    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    strokes.forEach { s ->
                        if (s.isEmpty()) return@forEach
                        val path = Path().apply { moveTo(s[0].x, s[0].y); s.drop(1).forEach { lineTo(it.x, it.y) } }
                        if (s.size == 1) drawCircle(Color.Black, 1.5.dp.toPx(), Offset(s[0].x, s[0].y)) else drawPath(path, Color.Black, style = stroke)
                    }
                }
                Text("Sign inside the box.", color = QeColors.Muted, fontSize = 12.sp)
                QeTextField(signer, { signer = it }, Modifier.fillMaxWidth(), placeholder = "Signed by (name)")
                if (error.isNotEmpty()) Text(error, color = Color(0xFFE5484D), fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val snap = strokes.toList()
                if (!QeSignature.isSigned(snap)) error = QeSignature.EMPTY else onSave(signaturePng(snap), signer.trim())
            }) { Text("Save", color = QeColors.Accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { strokes.clear() }) { Text("Clear pad", color = QeColors.Muted) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = QeColors.Muted) }
            }
        },
    )
}
