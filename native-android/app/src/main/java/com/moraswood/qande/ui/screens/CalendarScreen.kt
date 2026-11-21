package com.moraswood.qande.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moraswood.qande.logic.QeCalendar
import com.moraswood.qande.ui.components.PageHeading
import com.moraswood.qande.ui.components.QeCard
import com.moraswood.qande.ui.components.QePrimaryButton
import com.moraswood.qande.ui.components.QeSecondaryButton
import com.moraswood.qande.ui.theme.QeColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Overdue = Color(0xFFE5484D)
private val Done = Color(0xFF46A758)
private val Due = Color(0xFFF5A524)
private val dayFmt = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH)

private fun dot(e: QeCalendar.Entry): Color = when (e.status) {
    QeCalendar.Status.OVERDUE -> Overdue
    QeCalendar.Status.DONE -> Done
    QeCalendar.Status.DUE_TODAY -> Due
    QeCalendar.Status.UPCOMING -> QeColors.Accent
}

/** Feature P — delivery calendar: custom month grid (Mon–Sun), tap a day for its jobs; overdue in red. */
@Composable
fun CalendarScreen(
    entries: List<QeCalendar.Entry>, today: LocalDate,
    onOpen: (String) -> Unit, onCompleted: (String, LocalDate?) -> Unit,
) = ToolPage(820) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var dayText by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthText)
    val day = LocalDate.parse(dayText)
    val byDay = QeCalendar.byDay(entries)
    PageHeading("Delivery Calendar", "Delivery and completion dates of saved jobs. Overdue deliveries are red.")

    val overdue = QeCalendar.overdue(entries)
    if (overdue.isNotEmpty()) QeCard(Modifier.padding(bottom = 12.dp)) {
        Text("Overdue (${overdue.size})", color = Overdue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        overdue.forEach { e -> Text("${e.date.format(dayFmt)} · ${e.name} · R${e.revision}", color = Overdue, fontSize = 13.sp, modifier = Modifier.clickable { dayText = e.date.toString(); monthText = YearMonth.from(e.date).toString() }.padding(vertical = 3.dp)) }
    }

    QeCard(Modifier.padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            QeSecondaryButton("‹") { monthText = month.minusMonths(1).toString() }
            Text(QeCalendar.monthTitle(month), color = QeColors.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            QeSecondaryButton("Today") { monthText = YearMonth.from(today).toString(); dayText = today.toString() }
            QeSecondaryButton("›") { monthText = month.plusMonths(1).toString() }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            QeCalendar.WEEKDAYS.forEach { Text(it, color = QeColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 4.dp)) }
        }
        QeCalendar.monthGrid(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    val shape = RoundedCornerShape(8.dp)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (d != null) {
                            val list = byDay[d].orEmpty()
                            val sel = d == day
                            Column(
                                Modifier.fillMaxWidth().clip(shape)
                                    .background(if (sel) QeColors.Soft else QeColors.Card)
                                    .border(if (d == today) 2.dp else 1.dp, if (d == today) QeColors.Accent else QeColors.Line, shape)
                                    .clickable { dayText = d.toString() }.padding(4.dp),
                            ) {
                                Text("${d.dayOfMonth}", color = if (list.any { it.status == QeCalendar.Status.OVERDUE }) Overdue else QeColors.Text, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    list.take(4).forEach { e -> Box(Modifier.size(6.dp).clip(CircleShape).background(dot(e))) }
                                }
                                if (list.size > 4) Text("+${list.size - 4}", color = QeColors.Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    QeCard {
        Text(day.format(dayFmt), color = QeColors.Text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        val list = byDay[day].orEmpty()
        if (list.isEmpty()) Text("No deliveries or completions on this day.", color = QeColors.Muted)
        list.forEach { e ->
            val color = if (e.status == QeCalendar.Status.OVERDUE && e.kind == QeCalendar.Kind.DELIVERY) Overdue else QeColors.Text
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("${e.name} · R${e.revision}", color = color, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        e.kind == QeCalendar.Kind.COMPLETED -> "Completed"
                        e.status == QeCalendar.Status.OVERDUE -> "Delivery overdue"
                        e.status == QeCalendar.Status.DUE_TODAY -> "Delivery due today"
                        e.status == QeCalendar.Status.DONE -> "Delivery (completed ${e.completedAt?.format(dayFmt)})"
                        else -> "Delivery"
                    },
                    color = color, fontSize = 13.sp,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QePrimaryButton("Open") { onOpen(e.rootId) }
                    if (e.jobUuid.isNotBlank()) {
                        if (e.completedAt == null) QeSecondaryButton("Mark completed") { onCompleted(e.jobUuid, today) }
                        else QeSecondaryButton("Not completed") { onCompleted(e.jobUuid, null) }
                    }
                }
            }
        }
    }
}
