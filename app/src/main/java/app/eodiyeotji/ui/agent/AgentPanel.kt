package app.eodiyeotji.ui.agent

import app.eodiyeotji.ui.community.RetryButton
import app.eodiyeotji.ui.community.SecondaryPill
import app.eodiyeotji.ui.community.PrimaryPill
import app.eodiyeotji.ui.community.TwoLinePill
import app.eodiyeotji.ui.community.RoundIconButton
import app.eodiyeotji.ui.theme.EodiIcons
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.drawBehind
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.record.RecordingStatusChip
import app.eodiyeotji.ui.recap.RecallDrivingCard
import app.eodiyeotji.ui.theme.EodiTheme
import app.eodiyeotji.ui.theme.MemoryAlbumArt
import app.eodiyeotji.ui.theme.SparkleBadge
import java.time.LocalDate

/** 차량 터치 기준: 76dp 이상. */
internal val AgentTouchMin = 76.dp

/** 이 폭 이상이면 답(왼쪽)과 추천 카드(오른쪽)를 나란히 둔다. */
private val WideBreakpoint = 1100.dp

private val PagePadding = 32.dp

/** 입구 버튼이 "어디였지? 물어보세요"를 다 보여 줄 수 있는 폭. 이보다 좁으면 "물어보기". */
private val EntryFullWidth = 480.dp

// =====================================================================================
// 진입점
// =====================================================================================

/**
 * "어디였지? 물어보세요" 입구. 큰 알약 버튼 (높이 88dp, 글자 길이만큼만 넓어지고 마이크는 오른쪽 끝). 누르면 [AgentPanel]을 띄운다.
 * 글자는 줄이지 않고 다 보이게 한다 (좁은 창에서는 머리 쪽이 다른 것을 줄인다).
 * [driving]이면 문구가 "말로 물어보세요"로 바뀐다 (운전 중에는 음성으로만 묻는다).
 * [mic] false면 오른쪽 마이크를 뺀다 (옆 검색 칸에 말로 찾기 마이크가 있을 때 — 마이크가 둘로 보이지 않게).
 */
@Composable
fun AgentEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier, driving: Boolean = false, mic: Boolean = true) = BoxWithConstraints(modifier) {
    // 받은 폭이 좁으면(좁은 창의 머리) 짧은 이름 "물어보기"로 → 글자가 잘리지 않는다
    val label = when {
        driving -> R.string.agent_entry_driving
        maxWidth < EntryFullWidth -> R.string.agent_entry_short
        else -> R.string.agent_entry
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = EodiTheme.colors.accentSoft,
        contentColor = EodiTheme.colors.accent,
        shadowElevation = 2.dp,
        modifier = Modifier.heightIn(min = 88.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = if (mic) 12.dp else 28.dp, top = 12.dp, bottom = 12.dp),
        ) {
            // 반짝임은 기억 금색 (AI가 기억을 찾아 준다는 표시)
            Icon(EodiIcons.Sparkle, contentDescription = null, tint = EodiTheme.colors.memoryInk, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(label),
                style = EodiTheme.type.bodyStrong,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (mic) {
                Spacer(Modifier.width(24.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp).background(EodiTheme.colors.accent, CircleShape),
                ) {
                    Icon(AgentIcons.Mic, contentDescription = null, tint = EodiTheme.colors.onAccent, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/**
 * 기억 에이전트 화면 전체 (화면을 꽉 채운다). 정차 중이면 질문 입력 + 답 + 추천 카드 + 행동 버튼,
 * 주행 중이면 답 한 장과 큰 버튼 두 개(안내 / 취소)만 보인다.
 *
 * @param onClose 닫기(×) 눌렀을 때. null이면 닫기 버튼을 숨긴다.
 * @param onVoiceRequest 마이크를 눌렀을 때. null이면 Pleos SpeechToText로 듣고 알아들은 말을 [AgentViewModel.ask]에 넘긴다
 *   (운전 중이면 답을 TextToSpeech로 읽는다, [AgentViewModel] 설명).
 */
@Composable
fun AgentPanel(
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onVoiceRequest: (() -> Unit)? = null,
    viewModel: AgentViewModel = hiltViewModel(),
    compact: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Pleos 음성 인식이 없는 기기에서 Android 인식기로 대신 들을 때만 마이크 권한을 묻는다
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.voice() else viewModel.micPermissionDenied()
    }
    val voice = onVoiceRequest ?: {
        if (viewModel.needsMicPermission()) micPermission.launch(Manifest.permission.RECORD_AUDIO) else viewModel.voice()
    }
    // 답을 읽는 동안은 맨 위 자막(SpokenCaption)이 질문 줄을 가리지 않게 화면을 조금 내린다
    val captionSpace by animateDpAsState(if (state.voice.speaking && !state.voice.listening) CaptionSpace else 0.dp, label = "caption")
    Box(modifier) {
        AgentPanelContent(
            topSpace = captionSpace,
            state = state,
            onAsk = viewModel::ask,
            onConfirm = viewModel::confirm,
            onSelectStop = viewModel::selectStop,
            onRetry = viewModel::retry,
            onClear = viewModel::clear,
            onClose = onClose,
            onVoiceRequest = voice,
            compact = compact,
        )
        AgentVoiceLayer(
            voice = state.voice,
            driving = state.isDriving,
            hasTurn = state.turn != null,
            onMic = voice,
            onCancelListening = viewModel::cancelVoice,
        )
    }
}

/** 답을 읽는 동안 맨 위 자막 자리만큼 내리는 높이. */
private val CaptionSpace = 88.dp

/** 상태만 받아 그리는 화면 (미리보기·디버그 화면용). [topSpace]: 위쪽 비울 높이 (읽는 말 자막 자리). */
@Composable
fun AgentPanelContent(
    state: AgentUiState,
    onAsk: (String) -> Unit,
    onConfirm: (AgentEvent.ActionProposal) -> Unit,
    onSelectStop: (Int) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onClose: (() -> Unit)?,
    onVoiceRequest: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    topSpace: Dp = 0.dp,
) {
    Surface(modifier = modifier.fillMaxSize(), color = EodiTheme.colors.background) {
        Crossfade(targetState = state.isDriving, label = "agentMode", modifier = Modifier.padding(top = topSpace)) { driving ->
            if (driving) {
                DrivingAgent(state.turn, onConfirm, onClear, onClose, onVoiceRequest, onRetry)
            } else if (compact) {
                CompactParkedAgent(state.turn, onAsk, onConfirm, onSelectStop, onRetry, onVoiceRequest)
            } else {
                ParkedAgent(state.turn, onAsk, onConfirm, onSelectStop, onRetry, onClose, onVoiceRequest)
            }
        }
    }
}

// =====================================================================================
// 정차 중: 전체 화면
// =====================================================================================

@Composable
private fun ParkedAgent(
    turn: AgentTurn?,
    onAsk: (String) -> Unit,
    onConfirm: (AgentEvent.ActionProposal) -> Unit,
    onSelectStop: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: (() -> Unit)?,
    onVoiceRequest: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth >= WideBreakpoint
        Column(Modifier.fillMaxSize()) {
            QuestionBar(
                question = turn?.question,
                onAsk = onAsk,
                onVoiceRequest = onVoiceRequest,
                onClose = onClose,
                modifier = Modifier.fillMaxWidth().padding(start = PagePadding, end = PagePadding, top = 24.dp, bottom = 8.dp),
            )
            when {
                turn == null -> EmptyState(onAsk, Modifier.weight(1f).fillMaxWidth())
                wide -> Row(
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PagePadding),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier.weight(0.44f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    ) {
                        AnswerBlock(turn, onRetry)
                        ActionBlock(turn, onConfirm, onAsk)
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.weight(0.56f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    ) {
                        if (turn.courses.isNotEmpty()) {
                            CourseRecommendationList(turn, onAsk)
                        } else {
                            RecommendationsBlock(turn, today, onSelectStop, onAsk)
                        }
                    }
                }
                else -> Column(
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = PagePadding, vertical = 24.dp),
                ) {
                    AnswerBlock(turn, onRetry)
                    // 좁은 창: 카드를 먼저 보고 바로 아래에서 행동을 고른다
                    if (turn.courses.isNotEmpty()) {
                        // 좁은 창: 답 바로 아래에서 1번 안내를 고르고, 그 아래 순위 카드
                        ActionBlock(turn, onConfirm, onAsk)
                        CourseRecommendationList(turn, onAsk)
                    } else {
                        RecommendationsBlock(turn, today, onSelectStop, onAsk, onlyFirst = true)
                        ActionBlock(turn, onConfirm, onAsk)
                    }
                    OtherDays(turn, today, onAsk)
                }
            }
        }
    }
}

/**
 * 폰 세로 화면 (대화처럼): 위는 굴러가는 답 · 추천 카드(세로로 쌓음) · 행동 버튼, 맨 아래 엄지가 닿는 곳에 질문 칸과 큰 마이크.
 * 질문 칸은 키보드가 올라오면 같이 올라온다 (safeDrawing에 키보드가 들어 있다).
 */
@Composable
private fun CompactParkedAgent(
    turn: AgentTurn?,
    onAsk: (String) -> Unit,
    onConfirm: (AgentEvent.ActionProposal) -> Unit,
    onSelectStop: (Int) -> Unit,
    onRetry: () -> Unit,
    onVoiceRequest: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (turn == null) {
            EmptyState(onAsk, Modifier.weight(1f).fillMaxWidth())
        } else {
            // 새 질문이면 맨 위(내 질문)부터 보인다
            val scroll = remember(turn.id) { androidx.compose.foundation.ScrollState(0) }
            Column(
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)
                    .padding(horizontal = PagePadding, vertical = 24.dp),
            ) {
                AnswerBlock(turn, onRetry)
                if (turn.courses.isNotEmpty()) {
                    ActionBlock(turn, onConfirm, onAsk)
                    CourseRecommendationList(turn, onAsk)
                } else {
                    RecommendationsBlock(turn, today, onSelectStop, onAsk, onlyFirst = true)
                    ActionBlock(turn, onConfirm, onAsk)
                }
                OtherDays(turn, today, onAsk)
            }
        }
        CompactAskBar(
            question = turn?.question,
            onAsk = onAsk,
            onVoiceRequest = onVoiceRequest,
            modifier = Modifier
                .fillMaxWidth()
                .background(EodiTheme.colors.background)
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
        )
    }
}

/** 폰 질문 줄: [✦ 입력칸 … ➜] + 오른쪽 큰 마이크 (말로 묻기가 먼저 눈에 띄게). */
@Composable
private fun CompactAskBar(question: String?, onAsk: (String) -> Unit, onVoiceRequest: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable(question) { mutableStateOf(question.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        if (text.isNotBlank()) onAsk(text)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        TextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = EodiTheme.type.titleSmall,
            placeholder = {
                Text(stringResource(R.string.agent_field_hint), style = EodiTheme.type.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = {
                Icon(EodiIcons.Sparkle, contentDescription = null, tint = EodiTheme.colors.memoryInk, modifier = Modifier.padding(start = 8.dp).size(30.dp))
            },
            trailingIcon = if (text.isNotBlank()) {
                { RoundIconButton(AgentIcons.Send, stringResource(R.string.agent_send), submit, containerColor = Color.Transparent, contentColor = EodiTheme.colors.accent) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = EodiTheme.colors.surfaceHigh,
                unfocusedContainerColor = EodiTheme.colors.surfaceHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 96.dp),
        )
        Surface(
            onClick = onVoiceRequest,
            shape = CircleShape,
            color = EodiTheme.colors.accent,
            contentColor = EodiTheme.colors.onAccent,
            shadowElevation = 4.dp,
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AgentIcons.Mic, contentDescription = stringResource(R.string.agent_mic), modifier = Modifier.size(52.dp))
            }
        }
    }
}

/** 질문 입력 줄: [✦ 입력칸 ... ➜ 🎤] [×]. 정차 중에만 보인다 (운전 중에는 키보드를 띄우지 않는다). */
@Composable
private fun QuestionBar(
    question: String?,
    onAsk: (String) -> Unit,
    onVoiceRequest: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // 예시 칩·후속 버튼으로 질문이 바뀌면 입력칸도 그 질문을 보인다
    var text by rememberSaveable(question) { mutableStateOf(question.orEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onAsk(text)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        TextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = EodiTheme.type.drivingBody,
            placeholder = {
                Text(
                    stringResource(R.string.agent_field_hint),
                    style = EodiTheme.type.drivingBody,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    EodiIcons.Sparkle,
                    contentDescription = null,
                    tint = EodiTheme.colors.memoryInk,
                    modifier = Modifier.padding(start = 12.dp).size(32.dp),
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    if (text.isNotBlank()) {
                        RoundIconButton(AgentIcons.Send, stringResource(R.string.agent_send), submit, containerColor = Color.Transparent, contentColor = EodiTheme.colors.accent)
                    }
                    RoundIconButton(AgentIcons.Mic, stringResource(R.string.agent_mic), onVoiceRequest, filled = true)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = EodiTheme.colors.surfaceHigh,
                unfocusedContainerColor = EodiTheme.colors.surfaceHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 88.dp),
        )
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(AgentTouchMin)) {
                Icon(EodiIcons.Close, contentDescription = stringResource(R.string.agent_close), modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** 아직 묻지 않았을 때: 큰 제목 + 예시 질문 칩. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onAsk: (String) -> Unit, modifier: Modifier = Modifier) {
    val examples = listOf(
        stringResource(R.string.agent_example_1),
        stringResource(R.string.agent_example_2),
        stringResource(R.string.agent_example_3),
        stringResource(R.string.agent_example_4),
    )
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = PagePadding, vertical = 32.dp),
    ) {
        // 기억 앨범 그림: 지나온 점선 길 위의 사진 세 장 → 핀 + 반짝임
        MemoryAlbumArt(Modifier.widthIn(max = 520.dp))
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.agent_empty_title),
            style = EodiTheme.type.drivingTitle,
            fontWeight = FontWeight.Bold,
            color = EodiTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.agent_empty_body),
            style = EodiTheme.type.drivingBody,
            color = EodiTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(R.string.agent_examples),
            style = EodiTheme.type.eyebrow,
            color = EodiTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            examples.forEach { q ->
                Surface(
                    onClick = { onAsk(q) },
                    shape = CircleShape,
                    color = EodiTheme.colors.surface,
                    contentColor = EodiTheme.colors.textPrimary,
                    border = BorderStroke(2.dp, EodiTheme.colors.outline),
                    modifier = Modifier.heightIn(min = AgentTouchMin),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 22.dp, end = 28.dp, top = 18.dp, bottom = 18.dp)) {
                        Icon(EodiIcons.Sparkle, contentDescription = null, tint = EodiTheme.colors.memoryInk, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(q, style = EodiTheme.type.titleSmall)
                    }
                }
            }
        }
    }
}

/** 내 질문 말풍선 + AI 답 (글자가 오는 대로 이어진다). */
@Composable
private fun AnswerBlock(turn: AgentTurn, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 8.dp),
            color = EodiTheme.colors.surfaceHigh,
            contentColor = EodiTheme.colors.textPrimary,
            modifier = Modifier.align(Alignment.End).widthIn(max = 640.dp),
        ) {
            Text(turn.question, style = EodiTheme.type.titleSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            SparkleBadge(size = 52.dp)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                when (turn.phase) {
                    AnswerPhase.Thinking -> Thinking()
                    AnswerPhase.Failed -> {
                        if (turn.answer.isNotEmpty()) AnswerText(turn.answer, streaming = false, large = false)
                        Text(
                            turn.error ?: stringResource(R.string.agent_failed),
                            style = EodiTheme.type.titleSmall,
                            color = EodiTheme.colors.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        RetryButton(onRetry, label = stringResource(R.string.agent_retry))
                    }
                    else -> AnswerText(turn.answer, streaming = turn.phase == AnswerPhase.Streaming, large = false)
                }
            }
        }
    }
}

/** 답 글자. 오는 중에는 끝에 커서(▍)를 붙여 타자 치듯 보이게 한다. */
@Composable
private fun AnswerText(answer: String, streaming: Boolean, large: Boolean, maxLines: Int = Int.MAX_VALUE) {
    val caretColor = EodiTheme.colors.accent
    val text: AnnotatedString = remember(answer, streaming, caretColor) {
        buildAnnotatedString {
            append(answer)
            if (streaming) withStyle(SpanStyle(color = caretColor)) { append(" ▍") }
        }
    }
    val base = if (large) EodiTheme.type.display else EodiTheme.type.drivingHeadline
    Text(
        text,
        // 한국어를 글자 단위가 아니라 어절 단위로 줄바꿈 (Android 13+, 그 아래는 글자 단위)
        style = base.copy(lineHeight = base.fontSize * 1.45f, lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)),
        fontWeight = FontWeight.Medium,
        color = EodiTheme.colors.textPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** "기억을 찾는 중…" + 깜빡이는 점 세 개. */
@Composable
private fun Thinking(large: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "dots",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(
                Modifier.padding(end = 8.dp).size(if (large) 18.dp else 12.dp)
                    .alpha(if (on) 1f else 0.3f)
                    .background(EodiTheme.colors.accent, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.agent_thinking),
            style = if (large) EodiTheme.type.drivingTitle else EodiTheme.type.drivingBody,
            color = EodiTheme.colors.textSecondary,
        )
    }
}

/** 행동 버튼: 에이전트 제안(첫 번째가 기본) + 여기만 가기 + 다른 날도 보기. */
@Composable
private fun ActionBlock(turn: AgentTurn, onConfirm: (AgentEvent.ActionProposal) -> Unit, onAsk: (String) -> Unit) {
    val first = turn.recommendations.firstOrNull()
    val selectedStop = first?.stops?.getOrNull(turn.selectedStop)
    val showOnlyHere = first != null && first.stops.size > 1 && selectedStop != null
    val showOtherDays = first != null && turn.phase == AnswerPhase.Done && turn.recommendations.size == 1
    val onlyHereQuestion = selectedStop?.let { stringResource(R.string.agent_followup_only_here, it.name) }
    val otherDaysQuestion = stringResource(R.string.agent_followup_other_days)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        turn.proposals.forEachIndexed { i, p ->
            ProposalButton(p, primary = i == 0, running = (turn.action as? ActionState.Running)?.proposalId == p.id, onConfirm)
        }
        // 결과는 버튼 아래에 붙인다 (방금 누른 버튼이 밀려 움직이지 않게)
        ActionStatus(turn.action)
        if (showOnlyHere || showOtherDays) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (showOnlyHere && onlyHereQuestion != null) {
                    SecondaryAction(
                        icon = EodiIcons.Pin,
                        label = stringResource(R.string.agent_only_here),
                        detail = selectedStop.name,
                        onClick = { onAsk(onlyHereQuestion) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showOtherDays) {
                    SecondaryAction(
                        icon = EodiIcons.Calendar,
                        label = stringResource(R.string.agent_other_days),
                        detail = null,
                        onClick = { onAsk(otherDaysQuestion) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProposalButton(p: AgentEvent.ActionProposal, primary: Boolean, running: Boolean, onConfirm: (AgentEvent.ActionProposal) -> Unit) {
    TwoLinePill(
        title = p.title,
        detail = p.description,
        icon = if (primary) EodiIcons.Navi else AgentIcons.Fast,
        onClick = { onConfirm(p) },
        modifier = Modifier.fillMaxWidth(),
        primary = primary,
        running = running,
        large = true,
        detailLines = 2,
    )
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TwoLinePill(title = label, detail = detail, icon = icon, onClick = onClick, modifier = modifier)
}

/** 안내 요청 결과 한 줄 (보내는 중 / 시작함 / 실패). */
@Composable
private fun ActionStatus(action: ActionState, large: Boolean = false) {
    val colors = EodiTheme.colors
    val (text, container, content) = when (action) {
        ActionState.Idle -> return
        is ActionState.Running -> Triple(stringResource(R.string.agent_action_running), colors.surfaceHigh, colors.textPrimary)
        is ActionState.Done -> Triple(stringResource(R.string.agent_action_done), colors.accentSoft, colors.accent)
        is ActionState.Failed -> Triple(stringResource(R.string.agent_action_failed, action.message), colors.surfaceHigh, colors.error)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = if (large) 24.dp else 18.dp)) {
            if (action is ActionState.Done) {
                Icon(EodiIcons.Check, contentDescription = null, modifier = Modifier.size(if (large) 44.dp else 30.dp))
                Spacer(Modifier.width(16.dp))
            }
            Text(text, style = if (large) EodiTheme.type.drivingHeadline else EodiTheme.type.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 추천 카드들. 첫 카드는 사진과 들른 곳까지 크게, 나머지는 "다른 날" 줄로. */
@Composable
private fun RecommendationsBlock(
    turn: AgentTurn,
    today: LocalDate,
    onSelectStop: (Int) -> Unit,
    onAsk: (String) -> Unit,
    onlyFirst: Boolean = false,
) {
    val first = turn.recommendations.firstOrNull() ?: return
    RecommendationCard(first, today, turn.selectedStop, onSelectStop)
    if (!onlyFirst) OtherDays(turn, today, onAsk)
}

@Composable
private fun OtherDays(turn: AgentTurn, today: LocalDate, onAsk: (String) -> Unit) {
    val others = turn.recommendations.drop(1)
    if (others.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.agent_more_days),
            style = EodiTheme.type.titleSmall,
            color = EodiTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        others.forEach { rec -> OtherDayRow(rec, today, onAsk) }
    }
}

// =====================================================================================
// 주행 중: 큰 카드 1장 + 큰 버튼 2개 (목록·키보드 없음)
// =====================================================================================

@Composable
private fun DrivingAgent(
    turn: AgentTurn?,
    onConfirm: (AgentEvent.ActionProposal) -> Unit,
    onClear: () -> Unit,
    onClose: (() -> Unit)?,
    onVoiceRequest: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(PagePadding)) {
        Surface(
            shape = RoundedCornerShape(40.dp),
            color = EodiTheme.colors.surface,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().padding(48.dp)) {
                if (turn == null) {
                    // 지금 이 주행을 알아서 기록하고 있다는 표시 (누를 수 없는 작은 점 + 글)
                    RecordingStatusChip(interactive = false)
                    Spacer(Modifier.height(16.dp))
                    // 지나가며 떠오른 기억이 있으면 마이크 위에 큰 사진 카드로 (화면 위쪽 절반, ui/recap, 없으면 그리지 않는다)
                    RecallDrivingCard(Modifier.weight(1.3f))
                    DrivingPrompt(onVoiceRequest, Modifier.weight(1f).fillMaxWidth())
                    if (onClose != null) {
                        BigButton(stringResource(R.string.agent_close), null, primary = false, enabled = true, onClick = onClose, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text(
                        "“${turn.question}”",
                        style = EodiTheme.type.drivingHeadline,
                        color = EodiTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(28.dp))
                    // 추천 코스: 주행 중에는 지금 제안한 코스 하나만 크게
                    turn.courses.getOrNull(turn.courseFocus)?.let {
                        DrivingCourseCard(it)
                        Spacer(Modifier.height(24.dp))
                    }
                    // 기억 답: 그날 사진 한 장 + "1년 전" 도장 + 제목 (한눈에 "아, 그 바다" 하게)
                    if (turn.courses.isEmpty()) {
                        turn.recommendations.firstOrNull()?.let {
                            DrivingMemoryCard(it)
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (turn.phase) {
                            AnswerPhase.Thinking -> Thinking(large = true)
                            AnswerPhase.Failed -> Text(
                                turn.error ?: stringResource(R.string.agent_failed),
                                style = EodiTheme.type.drivingTitle,
                                color = EodiTheme.colors.error,
                            )
                            else -> AnswerText(turn.answer, streaming = turn.phase == AnswerPhase.Streaming, large = true, maxLines = 6)
                        }
                    }
                    ActionStatus(turn.action, large = true)
                    Spacer(Modifier.height(24.dp))
                    val proposal = turn.proposals.firstOrNull()
                    val running = turn.action is ActionState.Running
                    val failed = turn.phase == AnswerPhase.Failed
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
                        BigButton(
                            // 답을 못 받았으면 같은 자리 큰 버튼이 "다시 묻기" (한 번 누르면 된다)
                            label = stringResource(if (failed) R.string.agent_retry else R.string.agent_navigate),
                            icon = if (failed) null else EodiIcons.Navi,
                            primary = true,
                            enabled = failed || (proposal != null && !running && turn.action !is ActionState.Done),
                            onClick = { if (failed) onRetry() else proposal?.let(onConfirm) },
                            modifier = Modifier.weight(1.4f),
                        )
                        BigButton(
                            label = stringResource(R.string.agent_cancel),
                            icon = EodiIcons.Close,
                            primary = false,
                            enabled = true,
                            onClick = onClear,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 운전 중 처음 화면: 큰 마이크 하나. */
@Composable
private fun DrivingPrompt(onVoiceRequest: () -> Unit, modifier: Modifier = Modifier) {
    val ring = EodiTheme.colors.accent
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = modifier) {
        // 큰 마이크 + 옅은 고리 두 겹 (움직이지 않는 그림 → 운전 중 시선을 끌지 않는다)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(300.dp).drawBehind {
                drawCircle(ring.copy(alpha = 0.07f), radius = size.minDimension / 2)
                drawCircle(ring.copy(alpha = 0.12f), radius = size.minDimension * 0.4f)
            },
        ) {
            Surface(
                onClick = onVoiceRequest,
                shape = CircleShape,
                color = EodiTheme.colors.accent,
                contentColor = EodiTheme.colors.onAccent,
                modifier = Modifier.size(180.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(AgentIcons.Mic, contentDescription = stringResource(R.string.agent_mic), modifier = Modifier.size(88.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.agent_entry_driving),
            style = EodiTheme.type.drivingTitle,
            fontWeight = FontWeight.SemiBold,
            color = EodiTheme.colors.textPrimary,
        )
    }
}

/** 운전 중 큰 버튼 (높이 144dp): 공통 큰 알약 ([PrimaryPill] / [SecondaryPill]의 large). */
@Composable
private fun BigButton(
    label: String,
    icon: ImageVector?,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (primary) {
        PrimaryPill(label, icon, onClick, modifier, enabled = enabled, large = true)
    } else {
        SecondaryPill(label, icon, onClick, modifier, enabled = enabled, large = true)
    }
}
