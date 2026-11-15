package dev.carevisit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable internal fun VisitCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
    }
}
@Composable internal fun SectionTitle(text: String) {
    Text(text, style = VisitType.Section, modifier = Modifier.semantics { heading() })
}
@Composable internal fun SupportingText(text: String) {
    Text(text, style = VisitType.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun StatusLabel(text: String, driving: Boolean = false) {
    Text(text, modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer, style = if (driving) VisitType.Strong else VisitType.Small)
}
@Composable internal fun VisitButton(
    label: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true,
    driving: Boolean = false, onClick: () -> Unit,
) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = if (driving) 100.dp else 80.dp),
        shape = RoundedCornerShape(if (driving) 32.dp else 24.dp),
        colors = if (primary) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(label, style = if (driving) VisitType.Section else VisitType.Strong, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
@Composable internal fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, role: Role = Role.RadioButton, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.heightIn(min = 80.dp).clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape)
        .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
        .selectable(selected = selected, role = role, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(if (selected) "✓ $label" else label, style = VisitType.Strong, maxLines = 2)
    }
}
@Composable internal fun DocumentMark(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onPrimary
    Box(modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            drawRoundRect(color, Offset(size.width * .17f, size.height * .08f), Size(size.width * .66f, size.height * .84f), CornerRadius(5.dp.toPx()), style = Stroke(3.dp.toPx()))
            for (fraction in listOf(.35f, .52f, .69f)) drawLine(color, Offset(size.width * .32f, size.height * fraction), Offset(size.width * .68f, size.height * fraction), 3.dp.toPx())
        }
    }
}
@Composable internal fun Confirmation(
    title: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(32.dp).widthIn(max = 808.dp).fillMaxWidth().fillMaxHeight(.9f), shape = RoundedCornerShape(48.dp)) {
            Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SectionTitle(title)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
                VisitButton(confirm, Modifier.fillMaxWidth(), primary = true, onClick = onConfirm)
                VisitButton("취소", Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }
}
