package com.moraswood.qande.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.photos.ItemPhotos
import com.moraswood.qande.ui.theme.QeColors
import kotlinx.coroutines.launch

/**
 * Native addition: one photo per item. Camera (TakePicture into a FileProvider URI, no CAMERA permission
 * needed) or gallery (PickVisualMedia, no storage permission). Shows a downsampled thumbnail with Replace/Remove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemPhotoBox(photo: String, onPhoto: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf("") }
    var thumb by remember(photo) { mutableStateOf<Bitmap?>(null) }
    val px = with(LocalDensity.current) { 72.dp.roundToPx() }
    LaunchedEffect(photo) { thumb = if (photo.isEmpty()) null else ItemPhotos.thumbnail(context, photo, px) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val name = pending
        pending = ""
        if (name.isEmpty()) return@rememberLauncherForActivityResult
        if (ok && ItemPhotos.file(context, name).length() > 0) onPhoto(name) else ItemPhotos.file(context, name).delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ItemPhotos.importFrom(context, uri)?.let(onPhoto) }
    }

    FlowRow(
        modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(10.dp)
        Box(
            Modifier.size(72.dp).clip(shape).background(QeColors.InputBg).border(1.dp, QeColors.InputBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            val b = thumb
            if (b != null) Image(b.asImageBitmap(), contentDescription = "Item photo", contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
            else Text(if (photo.isEmpty()) "No photo" else "…", color = QeColors.Muted, fontSize = 11.sp)
        }
        QeSecondaryButton(if (photo.isEmpty()) "Take photo" else "Retake") {
            runCatching {
                val (name, uri) = ItemPhotos.newCaptureTarget(context)
                pending = name
                camera.launch(uri)
            }
        }
        QeSecondaryButton(if (photo.isEmpty()) "Choose photo" else "Replace") {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (photo.isNotEmpty()) QeDangerButton("Remove photo") { onPhoto("") }
    }
}
