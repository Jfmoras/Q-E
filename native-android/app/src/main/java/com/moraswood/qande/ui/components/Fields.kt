package com.moraswood.qande.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.ui.theme.QeColors

/**
 * `<input type=number class="cell|input">`: decimal keyboard, single line, Enter = Next.
 * Keeps its own selection so focusing via Enter selects the whole value (V10.1 `n.select()`).
 */
@Composable
fun QeNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    compact: Boolean = false,
    placeholder: String = "",
    onEnter: (() -> Unit)? = null,
    /** V10.1 `.input-error`: red border + light red outline. */
    error: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    var tfv by remember { mutableStateOf(TextFieldValue(value)) }
    if (tfv.text != value) tfv = TextFieldValue(value, TextRange(value.length))
    val shape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    BasicTextField(
        value = tfv,
        onValueChange = { next ->
            val filtered = next.text.filter { it.isDigit() || it == '.' || it == '-' || it == 'e' || it == 'E' }
            tfv = next.copy(text = filtered)
            if (filtered != value) onValueChange(filtered)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onEnter?.invoke() ?: defaultKeyboardAction(ImeAction.Next) }),
        textStyle = TextStyle(color = QeColors.InputText, fontSize = if (compact) 15.sp else 16.sp),
        cursorBrush = SolidColor(QeColors.Accent),
        modifier = modifier
            .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
            .onFocusChanged {
                if (it.isFocused && !focused) tfv = tfv.copy(selection = TextRange(0, tfv.text.length))
                focused = it.isFocused
            }
            .let { if (error) it.border(2.dp, QeColors.ErrorOutline, shape).padding(3.dp) else it }
            .clip(shape)
            .background(QeColors.InputBg)
            .border(
                if (focused || error) 2.dp else 1.dp,
                if (error) QeColors.ErrorBorder else if (focused) QeColors.Accent else QeColors.InputBorder, shape,
            )
            .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = if (compact) 8.dp else 10.dp),
        decorationBox = { inner ->
            Box {
                if (tfv.text.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = QeColors.Placeholder, fontSize = 15.sp)
                inner()
            }
        },
    )
}

/** `<select class="input|cell">` with V10.1 input styling. */
@Composable
fun <T> QeSelect(
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    bold: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(QeColors.InputBg).border(1.dp, QeColors.InputBorder, shape)
                .clickable { open = true }
                .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second ?: value.toString(),
                color = QeColors.InputText, fontSize = if (compact) 15.sp else 16.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1,
            )
            Text("▾", color = QeColors.Muted, fontSize = 12.sp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(QeColors.InputBg)) {
            options.forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label, color = QeColors.InputText) }, onClick = { open = false; onSelect(key) })
            }
        }
    }
}

/** `<label class="field">Label<input></label>` */
@Composable
fun QeFieldLabel(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(bottom = 10.dp)) {
        Text(label, color = QeColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
        content()
    }
}

@Composable
fun QeCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Checkbox(
        checked, onChange, modifier = Modifier.size(32.dp),
        colors = CheckboxDefaults.colors(checkedColor = QeColors.Accent, checkmarkColor = QeColors.OnAccent, uncheckedColor = QeColors.InputBorder),
    )
}

/** JavaScript `alert()` replacement: one-button dialog. */
@Composable
fun QeAlert(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = QeColors.Accent) } },
        text = { Text(message, color = QeColors.Text) },
        containerColor = QeColors.Card,
    )
}

/** JavaScript `confirm()` replacement. */
@Composable
fun QeConfirm(message: String?, onResult: (Boolean) -> Unit) {
    if (message == null) return
    AlertDialog(
        onDismissRequest = { onResult(false) },
        confirmButton = { TextButton(onClick = { onResult(true) }) { Text("OK", color = QeColors.Accent) } },
        dismissButton = { TextButton(onClick = { onResult(false) }) { Text("Cancel", color = QeColors.Muted) } },
        text = { Text(message, color = QeColors.Text) },
        containerColor = QeColors.Card,
    )
}

/** Table header cell (`th`): #343d40, uppercase 12sp #e0e7e5. */
@Composable
fun QeTh(text: String, width: Dp) {
    Text(
        text.uppercase(), color = QeColors.ThText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = Modifier.size(width = width, height = 38.dp).background(QeColors.ThBg).padding(horizontal = 10.dp, vertical = 11.dp),
    )
}
