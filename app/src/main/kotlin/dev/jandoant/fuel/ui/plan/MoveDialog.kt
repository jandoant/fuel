package dev.jandoant.fuel.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jandoant.fuel.core.domain.MealSlot
import dev.jandoant.fuel.core.domain.PlanEntry
import dev.jandoant.fuel.ui.components.FuelIcons
import dev.jandoant.fuel.ui.components.IconButton44
import dev.jandoant.fuel.ui.theme.FuelColors
import dev.jandoant.fuel.ui.theme.FuelShapes
import dev.jandoant.fuel.ui.theme.FuelType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH))
}

/** Move an entry to another day and/or meal slot. */
@Composable
fun MoveDialog(entry: PlanEntry, slots: List<MealSlot>, today: LocalDate, onMove: (LocalDate, String) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(entry.date) }
    var slot by remember { mutableStateOf(entry.slotId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FuelColors.Surface,
        title = { Text("Move ${entry.title}", style = FuelType.SectionHeading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton44(FuelIcons.BACK, "Previous day", { date = date.minusDays(1) })
                    Text(dayLabel(date, today), style = FuelType.BodyStrong, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton44(FuelIcons.FORWARD, "Next day", { date = date.plusDays(1) })
                }
                slots.filter { it.enabled || it.id == entry.slotId }.forEach { s ->
                    val on = s.id == slot
                    Text(
                        s.name, style = FuelType.Body, color = if (on) FuelColors.AccentDeep else FuelColors.Ink,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(FuelShapes.Item)
                            .background(if (on) FuelColors.AccentBg else Color.Transparent)
                            .clickable(role = Role.RadioButton) { slot = s.id }.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton({ onMove(date, slot) }) { Text("Move", color = FuelColors.AccentText, style = FuelType.BodyStrong) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = FuelColors.Muted, style = FuelType.Body) } },
    )
}
