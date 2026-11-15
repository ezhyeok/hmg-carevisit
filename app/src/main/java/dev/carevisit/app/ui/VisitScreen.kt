package dev.carevisit.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.carevisit.app.AppCommand
import dev.carevisit.app.AppIdentity
import dev.carevisit.app.VisitStore
import dev.carevisit.core.*
import kotlinx.coroutines.launch

@Composable fun VisitScreen(state: ViewState, platformRestricted: Boolean) {
    VisitTheme {
        val model = remember(state, platformRestricted) { ScreenModel.from(state, platformRestricted) }
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(model.glance) {
            if (model.glance) { focus.clearFocus(force = true); keyboard?.hide() }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // No crossfade: the parked branch, keyboard and every dialog disappear immediately on restriction.
            if (model.glance) GlanceScreen(model,
                onListen = { VisitStore.send(Event.CommandListen) },
                onAnswer = { answer -> model.promptId?.let { VisitStore.send(Event.Answer(it, answer, touch = true)) } },
            ) else ParkedScreen(state, model)
        }
    }
}

/** Deliberately cannot access health records, raw prompts, history, settings or text fields. */
@Composable private fun GlanceScreen(model: ScreenModel, onListen: () -> Unit, onAnswer: (String) -> Unit) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 1240.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DocumentMark()
                Text(stringResource(AppIdentity.NAME), style = VisitType.Section, modifier = Modifier.weight(1f))
                if (model.demoLabel != null) StatusLabel("시연", driving = true)
            }
            Spacer(Modifier.weight(1f))
            Text(listOf(model.title, model.progress).filter { it.isNotEmpty() }.joinToString(" "), style = VisitType.Strong)
            Text(model.question, style = VisitType.Hero, maxLines = DriveLimits.QUESTION_LINES, overflow = TextOverflow.Ellipsis)
            if (model.showVoiceStatus) Text(model.voice, style = VisitType.Strong, color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            model.demoLabel?.let { Text(it, style = VisitType.Body) }
            Spacer(Modifier.weight(1f))
            if (model.answers.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    model.answers.forEachIndexed { index, answer ->
                        VisitButton(answer.label, Modifier.weight(1f), primary = index == 0, enabled = answer.enabled, driving = true) { onAnswer(answer.answer) }
                    }
                }
            } else if (model.showVoiceStart) VisitButton("음성으로 말하기", Modifier.fillMaxWidth(), driving = true, onClick = onListen)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ParkedScreen(state: ViewState, model: ScreenModel) {
    var tab by remember { mutableIntStateOf(0) }
    val parked = model.parked ?: return
    val records = remember { BringIntoViewRequester() }
    val journey = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var demoConsent by remember { mutableStateOf(false) }
    var shareAfterConsent by remember { mutableStateOf(false) }
    var pendingShareVisit by remember { mutableStateOf<String?>(null) }
    val viewRecords: () -> Unit = { scope.launch { records.bringIntoView() } }
    val shareBriefing: () -> Unit = {
        when (parked.shareAction) {
            BriefingShareAction.CONNECT_DEMO -> { shareAfterConsent = true; demoConsent = true }
            BriefingShareAction.CONFIRM -> UiCommands.shareBriefing()
            BriefingShareAction.NONE -> Unit
        }
    }
    val showConfirmation: () -> Unit = {
        tab = 0
        scope.launch { withFrameNanos { }; journey.bringIntoView() }
    }
    LaunchedEffect(state, pendingShareVisit) {
        if (shouldRequestBriefingAfterDemoConsent(state, pendingShareVisit)) {
            pendingShareVisit = null
            UiCommands.shareBriefing()
        }
    }
    LaunchedEffect(state.archive.session.prompt?.id) {
        val kind = state.archive.session.prompt?.kind
        if (kind in setOf(AskKind.HEALTH, AskKind.SHARE_MEMO, AskKind.SHARE_BRIEFING)) {
            tab = 0
            if (kind != AskKind.HEALTH) { withFrameNanos { }; journey.bringIntoView() }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 1100.dp
        Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 48.dp else 40.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DocumentMark()
                Column(Modifier.weight(1f)) {
                    Text(stringResource(AppIdentity.NAME), style = VisitType.Title)
                    SupportingText("가는 길엔 준비, 오는 길엔 기억")
                }
                StatusLabel(if (state.demo) "시연 · 주차 중" else "주차 중")
            }
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UiCopy.TABS.forEachIndexed { index, label -> Choice(label, tab == index, Modifier.weight(1f), role = Role.Tab) { tab = index } }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    1 -> HistoryScreen(parked.history)
                    2 -> SettingsScreen(state)
                    else -> if (wide && !parked.consent.showHealth) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(Modifier.weight(.85f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                JourneyCard(state, model, viewRecords, Modifier.bringIntoViewRequester(journey))
                            }
                            Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                DocumentCards(state, parked, Modifier.bringIntoViewRequester(records), shareBriefing)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            if (parked.consent.showHealth) HealthConsentCard(state) else {
                                JourneyCard(state, model, viewRecords, Modifier.bringIntoViewRequester(journey))
                                DocumentCards(state, parked, Modifier.bringIntoViewRequester(records), shareBriefing)
                            }
                        }
                    }
                }
            }
            if (state.demo) DemoPanel(state, parked, wide,
                onConsent = { shareAfterConsent = false; demoConsent = true },
                onSend = shareBriefing, onShowConfirmation = showConfirmation)
        }
    }
    if (demoConsent && state.demo) DemoFamilyConsentDialog(
        onDismiss = { demoConsent = false },
        onConfirm = {
            demoConsent = false
            pendingShareVisit = state.archive.session.visit?.id.takeIf { shareAfterConsent }
            VisitStore.commands.trySend(AppCommand.DemoFamilyConsent)
        },
    )
}

@Composable private fun JourneyCard(state: ViewState, model: ScreenModel, onViewRecords: () -> Unit, headerModifier: Modifier = Modifier) {
    val session = state.archive.session
    val prompt = session.prompt
    val family = model.parked?.consent?.canShare == true
    VisitCard {
        Row(headerModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(model.title, style = VisitType.Section, modifier = Modifier.weight(1f))
            if (model.progress.isNotBlank()) StatusLabel(model.progress)
        }
        session.visit?.let { SupportingText(it.hospital.name) }
        Text(model.question, style = VisitType.Title)
        if (model.showVoiceStatus) Text(model.voice, style = VisitType.Strong, color = MaterialTheme.colorScheme.tertiary)
        model.parked?.notice?.let { SupportingText(it) }
        val completion = model.parked?.completion
        if (completion != null) {
            completion.appointment?.let {
                Text("다음 예약", style = VisitType.Strong)
                Text(it, style = VisitType.Body)
            }
            VisitButton(completion.actionLabel, Modifier.fillMaxWidth(), primary = true, onClick = onViewRecords)
        } else if (session.phase == Phase.RECOVERY) {
            VisitButton("기록 이어가기", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.Resume) }
            var abandon by remember { mutableStateOf(false) }
            VisitButton("중단한 기록 폐기", Modifier.fillMaxWidth()) { abandon = true }
            if (abandon) Confirmation("중단한 기록을 폐기할까요?", "폐기하기", { abandon = false }, { abandon = false; VisitStore.send(Event.Abandon) }) {
                Text("이번에 준비하던 기록을 지웁니다. 이전에 마친 진료 기록은 남아요.")
            }
        } else if (prompt != null) {
            when (prompt.kind) {
                AskKind.HEALTH -> HealthConsentCard(state)
                AskKind.SHARE_MEMO, AskKind.SHARE_BRIEFING -> if (family) {
                    StatusLabel("보내기 전 확인")
                    Text(if (prompt.kind == AskKind.SHARE_MEMO) Templates.memo(session.visit!!) else session.briefing, style = VisitType.Body)
                    SupportingText(if (state.demo) "이 내용만 시연 가족 화면에 보냅니다. 외부 전송은 없어요." else "이 내용만 연결된 가족 화면에 보냅니다.")
                    VisitButton("가족에게 보내기", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.Answer(prompt.id, "네", true)) }
                    VisitButton("보내지 않기", Modifier.fillMaxWidth()) { VisitStore.send(Event.Answer(prompt.id, "아니요", true)) }
                }
                AskKind.SLOT -> {
                    VisitButton("말로 답하기", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.RetryVoice) }
                    VisitButton("넘어가기", Modifier.fillMaxWidth()) { VisitStore.send(Event.Answer(prompt.id, "넘어가", true)) }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        VisitButton(if (prompt.kind == AskKind.MORE_QUESTIONS) "또 있어요" else "예", Modifier.weight(1f), primary = true) { VisitStore.send(Event.Answer(prompt.id, if (prompt.kind == AskKind.MORE_QUESTIONS) "또 있어" else "네", true)) }
                        VisitButton("아니요", Modifier.weight(1f)) { VisitStore.send(Event.Answer(prompt.id, "아니요", true)) }
                    }
                    VisitButton("다시 듣기", Modifier.fillMaxWidth()) { VisitStore.send(Event.RetryVoice) }
                }
            }
        } else when (session.phase) {
            Phase.IDLE -> {
                VisitButton("병원 가는 길", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.Manual(state.archive.settings.hospitals.firstOrNull() ?: Hospital("병원"))) }
                VisitButton("음성으로 말하기", Modifier.fillMaxWidth()) { VisitStore.send(Event.CommandListen) }
            }
            Phase.WAIT_SAFE -> VisitButton("진료 준비 시작", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.PrepareParked) }
            Phase.MEMO -> VisitButton("정문 도착", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.Arrived) }
            Phase.AT_HOSPITAL -> if (!session.parkingMode) VisitButton("내려 드리고 주차", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.ParkingMode) }
            else -> Unit
        }
    }
}

@Composable internal fun RendezvousCard(model: RendezvousModel) {
    VisitCard {
        SectionTitle("다시 만날 곳 · 정문")
        StatusLabel(model.label)
        Text(model.detail, style = VisitType.Body)
    }
}

@Composable private fun DemoPanel(
    state: ViewState, model: ParkedModel, wide: Boolean, onConsent: () -> Unit,
    onSend: () -> Unit, onShowConfirmation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusLabel("시연")
            Text(UiCopy.DEMO, style = VisitType.Small, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (model.consent.canPair) VisitButton("시연 가족 공유 동의", Modifier.weight(1f), onClick = onConsent)
            if (state.archive.consent.familyAt != null) StatusLabel("시연용 가족 연결")
            if (state.archive.session.phase == Phase.AT_HOSPITAL && state.archive.session.parkingMode &&
                state.archive.consent.familyAt != null && state.archive.session.rendezvous != Rendezvous.OUTSIDE) {
                VisitButton("시연 답장 · 나왔어", Modifier.weight(1f)) { VisitStore.commands.trySend(AppCommand.DemoFamilyOutside) }
            }
        }
        model.demoSteps.chunked(if (wide) 6 else 3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { step ->
                    VisitButton((if (step.complete) "✓ " else "") + step.label, Modifier.weight(1f), primary = step.current, enabled = step.enabled) {
                        when {
                            state.archive.session.prompt?.kind in setOf(AskKind.SHARE_MEMO, AskKind.SHARE_BRIEFING) -> onShowConfirmation()
                            state.archive.session.phase == Phase.DONE -> onSend()
                            else -> advanceDemo(state)
                        }
                    }
                }
            }
        }
    }
}

/** Uses real commands; never fabricates consent, vehicle signals, or completed records. */
private fun advanceDemo(state: ViewState) {
    if (!state.demo) return
    val prompt = state.archive.session.prompt
    when {
        state.archive.session.phase == Phase.RECOVERY -> VisitStore.send(Event.Resume)
        prompt != null -> VisitStore.send(Event.RetryVoice)
        else -> VisitStore.commands.trySend(AppCommand.Next)
    }
}
