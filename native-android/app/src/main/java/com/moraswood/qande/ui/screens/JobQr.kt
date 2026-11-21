package com.moraswood.qande.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.logic.QeQr
import com.moraswood.qande.ui.theme.QeColors

object JobQrBitmap {
    fun of(text: String, scale: Int = 8): Bitmap {
        val m = QeQr.matrix(text)
        val n = m.size
        val bmp = Bitmap.createBitmap(n * scale, n * scale, Bitmap.Config.ARGB_8888)
        for (y in 0 until n) for (x in 0 until n) {
            val c = if (m[y][x]) AColor.BLACK else AColor.WHITE
            for (dy in 0 until scale) for (dx in 0 until scale) bmp.setPixel(x * scale + dx, y * scale + dy, c)
        }
        return bmp
    }
}

/** Feature L — show a job's QR and print it as a label. */
@Composable
fun JobQrDialog(title: String, uuid: String, onPrint: () -> Unit, onDismiss: () -> Unit) {
    val bmp = remember(uuid) { JobQrBitmap.of(QeQr.payload(uuid)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QeColors.Card,
        title = { Text(title, color = QeColors.Text) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bmp.asImageBitmap(), "Job QR code", Modifier.size(220.dp).background(Color.White).padding(6.dp), filterQuality = FilterQuality.None)
                Text("Scan with “Scan job QR” on the Home screen to open this job.", color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Text(uuid, color = QeColors.Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onPrint) { Text("Print label", color = QeColors.Accent) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close", color = QeColors.Muted) } },
    )
}

/** Feature L — Google code scanner (Play services UI, no CAMERA permission needed by the app). */
object JobQrScanner {
    fun scan(context: Context, onResult: (String?) -> Unit, onError: (String) -> Unit) {
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE).build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { onResult(it.rawValue) }
            .addOnCanceledListener { }
            .addOnFailureListener { onError("Scanner not available: ${it.message ?: "unknown error"}") }
    }
}
