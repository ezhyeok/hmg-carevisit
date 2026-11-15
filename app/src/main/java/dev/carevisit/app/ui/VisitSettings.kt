package dev.carevisit.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.carevisit.app.AppCommand
import dev.carevisit.app.VisitStore
import dev.carevisit.core.*
import java.security.SecureRandom

@Composable internal fun HealthConsentCard(state: ViewState) {
    val prompt = state.archive.session.prompt?.takeIf { it.kind == AskKind.HEALTH }
    VisitCard {
        StatusLabel("처음 한 번 · 별도 동의")
        SectionTitle("말씀을 이 기기에 보관할까요?")
        Text("진료 준비와 기록을 위해 건강 정보를 보관합니다.", style = VisitType.Body)
        RecordRows(listOf(
            MemoRow("무엇을", "말씀을 글로 옮긴 내용, 진료 메모, 브리핑, 다음 예약"),
            MemoRow("어디에", "이 차량 기기의 앱 전용 공간"),
            MemoRow("얼마 동안", "현재 ${state.archive.settings.retentionDays}일 · 기본 180일\n기간이 지나면 자동으로 지워요."),
            MemoRow("그만 보관하려면", "설정에서 전체 삭제하거나 동의를 철회하세요. 기록과 동의를 함께 지워요."),
        ))
        SupportingText("가족 공유는 별도로 동의하고, 보낼 때마다 확인해요.")
        if (prompt == null) {
            VisitButton("동의 안내 듣기", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.RequestConsent) }
            SupportingText("동의한 뒤에 진료 메모를 시작할 수 있어요.")
        } else {
            Text(when (state.voice) {
                VoiceState.LISTENING -> "듣는 중"
                VoiceState.SPEAKING -> "동의 안내를 읽는 중"
                else -> "안내를 확인하고 선택해 주세요"
            }, style = VisitType.Strong)
            VisitButton("수집·보관에 동의", Modifier.fillMaxWidth(), primary = true) { VisitStore.send(Event.Answer(prompt.id, "네", true)) }
            VisitButton("동의하지 않음", Modifier.fillMaxWidth()) { VisitStore.send(Event.Answer(prompt.id, "아니요", true)) }
        }
    }
}

private enum class SettingsConfirm { DELETE, WITHDRAW, FAMILY_ON, FAMILY_OFF }

@Composable internal fun SettingsScreen(state: ViewState) {
    val settings = state.archive.settings
    val health = state.archive.consent.healthAt != null
    val family = health && state.archive.consent.familyAt != null
    var confirm by remember { mutableStateOf<SettingsConfirm?>(null) }
    var relay by remember(settings.relayUrl) { mutableStateOf(settings.relayUrl) }
    var publicBase by remember { mutableStateOf("") }
    var hospital by remember { mutableStateOf("") }
    var relayError by remember { mutableStateOf(false) }
    var hospitalError by remember { mutableStateOf(false) }
    // Disposed with the entire parked branch; input and confirmations are never restored while driving.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1280.dp).fillMaxSize().imePadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            VisitCard {
                SectionTitle("어떻게 불러 드릴까요?")
                SupportingText("실명 대신 호칭을 사용해요.")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    UiCopy.HONORIFICS.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { title ->
                                Choice(title, settings.honorific == title, Modifier.weight(1f)) {
                                    VisitStore.send(Event.Configure(settings.copy(honorific = title)))
                                }
                            }
                        }
                    }
                }
            }
            VisitCard {
                SectionTitle("정기 진료 병원")
                SupportingText("저장한 병원을 다음 동행에서도 알아볼 수 있어요.")
                settings.hospitals.forEach { saved ->
                    Text(saved.name, style = VisitType.Strong)
                    SupportingText(if (saved.routable) "길안내 위치 저장됨\n${saved.latitude}, ${saved.longitude}" else "위치 없음 · 병원 이름만 저장됨")
                    VisitButton("목록에서 삭제", Modifier.fillMaxWidth()) { VisitStore.send(Event.Configure(settings.copy(hospitals = settings.hospitals.filterNot { it == saved }))) }
                    HorizontalDivider()
                }
                Field("병원 이름", hospital, { value -> hospital = value.take(40); hospitalError = false }, hospitalError)
                if (hospitalError) SupportingText("병원 이름을 입력해 주세요.")
                VisitButton("병원 이름 저장", Modifier.fillMaxWidth()) {
                    val value = SettingsInput.hospital(hospital)
                    hospitalError = value == null
                    if (value != null) {
                        if (settings.hospitals.none { it.name == value.name }) VisitStore.send(Event.Configure(settings.copy(hospitals = settings.hospitals + value)))
                        hospital = ""
                    }
                }
                state.archive.session.visit?.hospital?.takeIf { it.name != "병원" }?.let { destination ->
                    VisitButton("현재 병원 저장", Modifier.fillMaxWidth()) {
                        VisitStore.send(Event.Configure(settings.copy(hospitals = settings.hospitals.filterNot { it.name == destination.name } + destination)))
                    }
                    SupportingText(if (destination.routable) "현재 병원의 이름과 길안내 위치를 함께 저장해요." else "현재 병원에 위치 정보가 없어 이름만 저장해요.")
                }
            }
            VisitCard {
                SectionTitle("보관과 삭제")
                Text("${settings.retentionDays}일 뒤 자동 삭제", style = VisitType.Strong)
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    UiCopy.RETENTION.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { days ->
                                Choice("${days}일", settings.retentionDays == days, Modifier.weight(1f)) {
                                    VisitStore.send(Event.Configure(settings.copy(retentionDays = days)))
                                }
                            }
                        }
                    }
                }
                SupportingText("기간을 줄이면 오래된 기록은 바로 지워져요.")
                if (health) {
                    StatusLabel("수집·보관 동의함")
                    SupportingText("동의일 · ${visitDay(state.archive.consent.healthAt!!)}")
                    VisitButton("동의 철회", Modifier.fillMaxWidth()) { confirm = SettingsConfirm.WITHDRAW }
                } else VisitButton("수집·보관 동의 안내", Modifier.fillMaxWidth()) { VisitStore.send(Event.RequestConsent) }
                VisitButton("전체 삭제", Modifier.fillMaxWidth()) { confirm = SettingsConfirm.DELETE }
            }
            if (state.demo) VisitCard {
                SectionTitle("시연 가족 연결")
                StatusLabel("시연용 메모리 연결 · 외부 전송 없음")
                SupportingText("실제 가족 주소는 사용하지 않아요. 시연을 끝내면 연결과 답장이 사라져요.")
                if (family) {
                    StatusLabel("시연 공유 동의함")
                    VisitButton("시연 가족 연결 끄기", Modifier.fillMaxWidth()) { confirm = SettingsConfirm.FAMILY_OFF }
                } else {
                    VisitButton("시연 가족 공유 동의", Modifier.fillMaxWidth(), enabled = health) { confirm = SettingsConfirm.FAMILY_ON }
                    if (!health) SupportingText("먼저 민감정보 수집·보관에 동의해 주세요.")
                }
            } else VisitCard {
                SectionTitle("가족 연결")
                StatusLabel(if (family) "별도 동의함" else "공유 꺼짐")
                SupportingText("확인한 기록과 정문·주차 상태를 전해요.")
                Field("차에서 연결할 주소", relay, { value -> relay = value; relayError = false }, relayError)
                SupportingText("예: http://10.0.2.2:8765")
                if (relayError) Text("주소를 확인해 주세요. http://주소:포트 형식이에요.", color = MaterialTheme.colorScheme.error, style = VisitType.Small)
                VisitButton("연결 주소 저장", Modifier.fillMaxWidth(), enabled = !family) {
                    relayError = !SettingsInput.validBaseUrl(relay)
                    if (!relayError) VisitStore.send(Event.Configure(settings.copy(relayUrl = relay.trim().trimEnd('/'))))
                }
                if (family) {
                    SupportingText("주소를 바꾸려면 가족 연결을 먼저 꺼 주세요.")
                    Text("연결 확인 코드", style = VisitType.Strong)
                    Text(SettingsInput.pairingCode(settings.familyToken), style = VisitType.Hero)
                    SupportingText("짧은 코드는 연결을 구별하는 표시예요. 접속에는 아래 전체 주소를 사용해 주세요.")
                    Field("가족이 접속할 노트북 주소", publicBase, { value -> publicBase = value })
                    SupportingText("같은 Wi-Fi의 노트북 주소를 넣어 주세요. 예: http://192.168.0.10:8765")
                    val familyUrl = SettingsInput.familyUrl(publicBase, settings.familyToken)
                    if (familyUrl != null) {
                        Text("가족에게 알려 줄 주소", style = VisitType.Strong)
                        Text(familyUrl, style = VisitType.Small)
                        SupportingText("주소를 가진 사람이 기록을 볼 수 있어요.")
                    } else if (publicBase.isNotBlank()) SupportingText("노트북 주소를 확인해 주세요.")
                    VisitButton("가족 연결 끄기", Modifier.fillMaxWidth()) { confirm = SettingsConfirm.FAMILY_OFF }
                } else {
                    Text(Policy.FAMILY_LINE, style = VisitType.Body)
                    VisitButton("공유 동의하고 연결", Modifier.fillMaxWidth(), primary = true, enabled = health && relay.trim().trimEnd('/') == settings.relayUrl.trimEnd('/')) { confirm = SettingsConfirm.FAMILY_ON }
                    if (!health) SupportingText("먼저 민감정보 수집·보관에 동의해 주세요.")
                    else if (relay.trim().trimEnd('/') != settings.relayUrl.trimEnd('/')) SupportingText("입력한 연결 주소를 먼저 저장해 주세요.")
                }
                SupportingText("가족 화면의 기록은 24시간 뒤 지워져요. 다시 사용하려면 연결을 새로 만들어 주세요.")
            }
            VisitCard {
                SectionTitle("문장 다듬기")
                Text("말씀의 내용은 그대로, 표현만 정리해요.", style = VisitType.Body)
                SupportingText("기본은 꺼짐입니다. 켜면 기기 안의 AI가 문장부호를 정리해요. 늦거나 내용이 달라지면 원래 기록을 사용해요.")
                Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).toggleable(value = settings.polish, role = Role.Switch, onValueChange = { VisitStore.send(Event.Configure(settings.copy(polish = it))) }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(if (settings.polish) "문장 다듬기 켜짐" else "문장 다듬기 꺼짐", Modifier.weight(1f), style = VisitType.Strong)
                    Switch(checked = settings.polish, onCheckedChange = null)
                }
            }
            if (state.demo) VisitCard {
                SectionTitle("시연 중")
                Text(UiCopy.DEMO, style = VisitType.Body)
                SupportingText("가상의 대답을 사용해요. 시연 기록은 앱을 끝내면 지워져요.")
                VisitButton("시연 끝내기", Modifier.fillMaxWidth()) { VisitStore.commands.trySend(AppCommand.Demo(false)) }
            }
        }
    }
    when (confirm) {
        SettingsConfirm.DELETE, SettingsConfirm.WITHDRAW -> {
            val withdraw = confirm == SettingsConfirm.WITHDRAW
            Confirmation(if (withdraw) "동의를 철회할까요?" else "기록을 모두 지울까요?", if (withdraw) "철회하고 삭제" else "모두 삭제", { confirm = null }, {
                confirm = null; VisitStore.send(if (withdraw) Event.Withdraw else Event.DeleteAll)
            }) {
                Text("진료 메모, 브리핑, 대화 원문, 예약과 동의를 이 기기에서 모두 지웁니다.")
                Text("가족 연결도 끄고 가족 화면의 기록 삭제를 요청해요. 이미 따로 저장한 내용은 지울 수 없어요.")
                Text("지운 기록은 되돌릴 수 없어요.", style = VisitType.Strong)
            }
        }
        SettingsConfirm.FAMILY_ON -> if (state.demo) DemoFamilyConsentDialog(
            onDismiss = { confirm = null },
            onConfirm = { confirm = null; VisitStore.commands.trySend(AppCommand.DemoFamilyConsent) },
        ) else Confirmation("가족 공유에 동의할까요?", "별도로 동의하고 연결", { confirm = null }, {
            confirm = null
            val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
            VisitStore.send(Event.FamilyConsent(true, token))
        }) {
            Text(Policy.FAMILY_LINE)
            Text("가족 화면에는 24시간 보관해요. 보낼 때마다 내용을 확인하고 다시 동의해 주세요.")
            Text("설정에서 연결을 끄면 가족 화면의 기록 삭제를 요청해요.")
        }
        SettingsConfirm.FAMILY_OFF -> Confirmation("가족 연결을 끌까요?", "연결 끄기", { confirm = null }, {
            confirm = null; VisitStore.send(Event.FamilyConsent(false))
        }) {
            Text(if (state.demo) "시연 공유 동의를 철회하고 시연 가족 화면의 기록을 지웁니다." else "가족 공유 동의를 철회하고 연결된 가족 화면의 기록 삭제를 요청합니다.")
            Text("이 기기의 진료 기록은 그대로 남아요.")
        }
        null -> Unit
    }
}

@Composable private fun Field(label: String, value: String, onChange: (String) -> Unit, error: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, style = VisitType.Small) },
        singleLine = true, isError = error, textStyle = VisitType.Body,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp))
}


@Composable internal fun DemoFamilyConsentDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Confirmation("시연 가족 공유에 동의할까요?", "시연 공유에 동의", onDismiss, onConfirm) {
        Text("시연용 메모리 연결 · 외부 전송 없음", style = VisitType.Strong)
        Text(Policy.FAMILY_LINE)
        Text("실제 가족에게 보내지 않아요. 시연용 가족 화면에서 ‘잘 받았어’, ‘나왔어’ 답장을 보내요.")
        Text("시연을 끝내면 연결과 기록이 사라져요. 보낼 내용은 따로 확인한 뒤에만 전달합니다.")
    }
}
