package dev.carevisit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.carevisit.app.VisitStore
import dev.carevisit.core.*

@Composable internal fun DocumentCards(
    state: ViewState, model: ParkedModel, recordsModifier: Modifier = Modifier,
    onShareBriefing: () -> Unit,
) {
    if (model.memo == null) {
        VisitCard {
            SectionTitle("진료 전에 함께 준비해요")
            Text("말씀해 주신 내용을 다섯 칸에 담아 드려요.", style = VisitType.Body)
            SupportingText("불편한 곳 · 언제부터 · 양상 · 약 · 물어볼 것")
        }
        return
    }
    val session = state.archive.session
    // Both window layouts render this tested order, including the rendezvous card.
    model.cards.forEach { card ->
        when (card) {
            TodayCard.BRIEFING -> VisitCard {
                Box(recordsModifier) { SectionTitle("진료 브리핑") }
                SupportingText("의사 선생님 말씀을 듣고 남긴 기록")
                RecordRows(model.briefing!!.rows)
                if (model.shareAction != BriefingShareAction.NONE) {
                    VisitButton("가족에게 보내기", Modifier.fillMaxWidth(), primary = true,
                        enabled = session.prompt == null, onClick = onShareBriefing)
                    SupportingText(if (state.demo) "시연용 가족 화면에만 전해요. 보낼 내용은 한 번 더 확인해요." else "보낼 내용을 확인한 뒤 한 번 더 여쭤봐요.")
                } else if (!model.consent.canShare) {
                    SupportingText("가족에게 보내려면 설정에서 별도로 동의해 주세요.")
                }
            }
            TodayCard.MEMO -> MemoCard(model.memo,
                modifier = if (model.briefing == null) recordsModifier else Modifier,
                onCheck = { VisitStore.send(Event.Checked(it)) }) {
                if (model.consent.canShare) VisitButton("진료 메모 보내기", Modifier.fillMaxWidth(), enabled = session.prompt == null && session.pendingHospital == null) { VisitStore.send(Event.ShareMemo) }
                session.visit?.hospital?.takeIf { h -> h.name != "병원" && state.archive.settings.hospitals.none { it.name == h.name } }?.let { hospital ->
                    VisitButton("정기 병원으로 저장", Modifier.fillMaxWidth()) {
                        VisitStore.send(Event.Configure(state.archive.settings.copy(hospitals = state.archive.settings.hospitals + hospital)))
                    }
                }
            }
            TodayCard.RENDEZVOUS -> RendezvousCard(model.rendezvous!!)
        }
    }
}

@Composable private fun MemoCard(model: MemoModel, modifier: Modifier = Modifier, onCheck: ((String) -> Unit)? = null, actions: @Composable ColumnScope.() -> Unit = {}) {
    VisitCard {
        Box(modifier) { SectionTitle("진료 메모") }
        SupportingText("${model.date} · ${model.hospital}")
        StatusLabel("의사 선생님께 보여 주세요")
        RecordRows(model.rows)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SectionTitle("물어볼 것")
        if (model.questions.isEmpty()) SupportingText("아직 적어 둔 질문이 없어요.")
        model.questions.forEach { item ->
            val base = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            Row(
                if (onCheck != null) base.toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { onCheck(item.id) }) else base,
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Checkbox(checked = item.checked, onCheckedChange = null, modifier = Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(item.text, style = VisitType.Body)
                    if (item.checked) SupportingText("물어봤어요")
                }
            }
        }
        SupportingText("말씀하신 그대로의 기록이에요.")
        actions()
    }
}

@Composable internal fun RecordRows(rows: List<MemoRow>) {
    rows.forEachIndexed { index, row ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(row.label, style = VisitType.Strong, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.confirmation) StatusLabel("확인 필요")
            Text(row.text, style = VisitType.Body)
        }
    }
}

@Composable internal fun HistoryScreen(groups: List<HospitalHistory>) {
    if (groups.isEmpty()) {
        VisitCard {
            SectionTitle("차곡차곡 모이는 진료 기록")
            Text("돌아오는 길에 말씀을 정리하면 병원별로 남아요.", style = VisitType.Body)
        }
        return
    }
    var selected by remember(groups.map { it.hospital }) { mutableStateOf(groups.first().hospital) }
    val current = groups.firstOrNull { it.hospital == selected } ?: groups.first()
    var expanded by remember(selected) { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            VisitCard {
                SectionTitle("병원별 진료 기록")
                groups.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { group -> Choice(group.hospital, selected == group.hospital, Modifier.weight(1f)) { selected = group.hospital } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            VisitCard {
                SectionTitle("다음 예약")
                // The latest visit is authoritative: an older appointment must not look like a new one.
                Text(current.entries.first().appointment ?: "아직 기록된 예약이 없어요", style = VisitType.Title)
                if (current.entries.first().reminder) SupportingText(UiCopy.REMINDER)
                SupportingText(current.hospital)
            }
        }
        items(current.entries, key = { it.id }) { entry ->
            TimelineEntry {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusLabel("기록")
                    Text(entry.date, style = VisitType.Section)
                }
                RecordRows(entry.briefing.rows)
                VisitButton(if (expanded == entry.id) "진료 메모 접기" else "진료 메모 보기", Modifier.fillMaxWidth()) { expanded = if (expanded == entry.id) null else entry.id }
                if (expanded == entry.id) {
                    HorizontalDivider()
                    RecordRows(entry.memo.rows)
                    SectionTitle("물어볼 것")
                    entry.memo.questions.forEach { Text("${if (it.checked) "✓ 물어봄" else "□ 질문"} · ${it.text}", style = VisitType.Body) }
                }
            }
        }
    }
}

/** A date-ordered spine makes the hospital's visits readable as a timeline. */
@Composable private fun TimelineEntry(content: @Composable ColumnScope.() -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val point = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(point, radius = 8.dp.toPx(), center = Offset(size.width / 2, 56.dp.toPx()))
        }
        VisitCard(Modifier.weight(1f), content)
    }
}

/** The service remains the only owner of sharing and per-document confirmation. */
internal object UiCommands {
    fun shareBriefing() { VisitStore.send(Event.ShareBriefing) }
}
