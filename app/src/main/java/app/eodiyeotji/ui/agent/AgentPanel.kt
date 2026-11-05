package app.eodiyeotji.ui.agent

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 */
@Composable
fun AgentEntryButton(onClick: () -> Unit, modifier: Modifier = Modifier, driving: Boolean = false) = BoxWithConstraints(modifier) {
    // 받은 폭이 좁으면(좁은 창의 머리) 짧은 이름 "물어보기"로 → 글자가 잘리지 않는다
    val label = when {
        driving -> R.string.agent_entry_driving
        maxWidth < EntryFullWidth -> R.string.agent_entry_short
        else -> R.string.agent_entry
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 2.dp,
        modifier = Modifier.heightIn(min = 88.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(AgentIcons.Sparkle, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(24.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(AgentIcons.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }
        }
    }
}

/**
 * 기억 에이전트 화면 전체 (화면을 꽉 채운다). 정차 중이면 질문 입력 + 답 + 추천 카드 + 행동 버튼,
 * 주행 중이면 답 한 장과 큰 버튼 두 개(안내 / 취소)만 보인다.
 *
 * @param onClose 닫기(×) 눌렀을 때. null이면 닫기 버튼을 숨긴다.
 * @param onVoiceRequest 마이크를 눌렀을 때. TODO(통합): SpeechToText로 받아 [AgentViewModel.ask]에 넘긴다.
 */
@Composable
fun AgentPanel(
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onVoiceRequest: () -> Unit = {},
    viewModel: AgentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AgentPanelContent(
        state = state,
        onAsk = viewModel::ask,
        onConfirm = viewModel::confirm,
        onSelectStop = viewModel::selectStop,
        onRetry = viewModel::retry,
        onClear = viewModel::clear,
        onClose = onClose,
        onVoiceRequest = onVoiceRequest,
        modifier = modifier,
    )
}

/** 상태만 받아 그리는 화면 (미리보기·디버그 화면용). */
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
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Crossfade(targetState = state.isDriving, label = "agentMode") { driving ->
            if (driving) {
                DrivingAgent(state.turn, onConfirm, onClear, onClose, onVoiceRequest)
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
                            RecommendationsBlock(turn, today, onSelectStop, onAsk, mapHeight = 400.dp)
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
                        RecommendationsBlock(turn, today, onSelectStop, onAsk, mapHeight = 260.dp, onlyFirst = true)
                        ActionBlock(turn, onConfirm, onAsk)
                    }
                    OtherDays(turn, today, onAsk)
                }
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
            textStyle = MaterialTheme.typography.headlineSmall,
            placeholder = {
                Text(
                    stringResource(R.string.agent_field_hint),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(
                    AgentIcons.Sparkle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp).size(32.dp),
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    if (text.isNotBlank()) {
                        RoundIconButton(AgentIcons.Send, stringResource(R.string.agent_send), onClick = submit, filled = false)
                    }
                    RoundIconButton(AgentIcons.Mic, stringResource(R.string.agent_mic), onClick = onVoiceRequest, filled = true)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 88.dp),
        )
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(AgentTouchMin)) {
                Icon(AgentIcons.Close, contentDescription = stringResource(R.string.agent_close), modifier = Modifier.size(32.dp))
            }
        }
    }
}

/** 원형 아이콘 버튼 (지름 72dp, 터치 영역 76dp). */
@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, filled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) colors.primary else Color.Transparent,
        contentColor = if (filled) colors.onPrimary else colors.primary,
        modifier = Modifier.size(AgentTouchMin).padding(2.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp))
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(AgentIcons.Sparkle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.agent_empty_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.agent_empty_body),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(R.string.agent_examples),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            examples.forEach { q ->
                Surface(
                    onClick = { onAsk(q) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.heightIn(min = AgentTouchMin),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp)) {
                        Text("“$q”", style = MaterialTheme.typography.titleLarge)
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
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.align(Alignment.End).widthIn(max = 640.dp),
        ) {
            Text(turn.question, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(AgentIcons.Sparkle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                when (turn.phase) {
                    AnswerPhase.Thinking -> Thinking()
                    AnswerPhase.Failed -> {
                        if (turn.answer.isNotEmpty()) AnswerText(turn.answer, streaming = false, large = false)
                        Text(
                            stringResource(R.string.agent_failed),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onRetry, modifier = Modifier.heightIn(min = AgentTouchMin)) {
                            Text(stringResource(R.string.agent_retry), style = MaterialTheme.typography.titleLarge)
                        }
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
    val caretColor = MaterialTheme.colorScheme.primary
    val text: AnnotatedString = remember(answer, streaming, caretColor) {
        buildAnnotatedString {
            append(answer)
            if (streaming) withStyle(SpanStyle(color = caretColor)) { append(" ▍") }
        }
    }
    val base = if (large) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium
    Text(
        text,
        // 한국어를 글자 단위가 아니라 어절 단위로 줄바꿈 (Android 13+, 그 아래는 글자 단위)
        style = base.copy(lineHeight = base.fontSize * 1.45f, lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)),
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
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
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.agent_thinking),
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        icon = AgentIcons.Pin,
                        label = stringResource(R.string.agent_only_here),
                        detail = selectedStop.name,
                        onClick = { onAsk(onlyHereQuestion) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showOtherDays) {
                    SecondaryAction(
                        icon = AgentIcons.Calendar,
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
    val content: @Composable RowScope.() -> Unit = {
        if (running) {
            CircularProgressIndicator(strokeWidth = 3.dp, color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
        } else {
            Icon(if (primary) AgentIcons.Navigate else AgentIcons.Fast, contentDescription = null, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.description.isNotBlank()) {
                Text(p.description, style = AgentSubStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.8f))
            }
        }
    }
    val modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
    val padding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
    val shape = RoundedCornerShape(24.dp)
    if (primary) {
        Button(onClick = { onConfirm(p) }, shape = shape, contentPadding = padding, modifier = modifier, content = content)
    } else {
        FilledTonalButton(onClick = { onConfirm(p) }, shape = shape, contentPadding = padding, modifier = modifier, content = content)
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 88.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = AgentSubStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 안내 요청 결과 한 줄 (보내는 중 / 시작함 / 실패). */
@Composable
private fun ActionStatus(action: ActionState, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (text, container, content) = when (action) {
        ActionState.Idle -> return
        is ActionState.Running -> Triple(stringResource(R.string.agent_action_running), colors.surfaceContainerHigh, colors.onSurface)
        is ActionState.Done -> Triple(stringResource(R.string.agent_action_done), colors.primaryContainer, colors.onPrimaryContainer)
        is ActionState.Failed -> Triple(stringResource(R.string.agent_action_failed, action.message), colors.errorContainer, colors.onErrorContainer)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp, vertical = if (large) 24.dp else 18.dp)) {
            if (action is ActionState.Done) {
                Icon(AgentIcons.Check, contentDescription = null, modifier = Modifier.size(if (large) 44.dp else 30.dp))
                Spacer(Modifier.width(16.dp))
            }
            Text(text, style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 추천 카드들. 첫 카드는 지도와 들른 곳까지 크게, 나머지는 "다른 날" 줄로. */
@Composable
private fun RecommendationsBlock(
    turn: AgentTurn,
    today: LocalDate,
    onSelectStop: (Int) -> Unit,
    onAsk: (String) -> Unit,
    mapHeight: Dp,
    onlyFirst: Boolean = false,
) {
    val first = turn.recommendations.firstOrNull() ?: return
    RecommendationCard(first, today, turn.selectedStop, onSelectStop, mapHeight)
    if (!onlyFirst) OtherDays(turn, today, onAsk)
}

@Composable
private fun OtherDays(turn: AgentTurn, today: LocalDate, onAsk: (String) -> Unit) {
    val others = turn.recommendations.drop(1)
    if (others.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.agent_more_days),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(PagePadding)) {
        Surface(
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().padding(48.dp)) {
                if (turn == null) {
                    DrivingPrompt(onVoiceRequest, Modifier.weight(1f).fillMaxWidth())
                    if (onClose != null) {
                        BigButton(stringResource(R.string.agent_close), null, primary = false, enabled = true, onClick = onClose, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text(
                        "“${turn.question}”",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(28.dp))
                    // 추천 코스: 주행 중에는 지금 제안한 코스 하나만 크게
                    turn.courses.getOrNull(turn.courseFocus)?.let {
                        DrivingCourseCard(it)
                        Spacer(Modifier.height(24.dp))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (turn.phase) {
                            AnswerPhase.Thinking -> Thinking(large = true)
                            AnswerPhase.Failed -> Text(
                                stringResource(R.string.agent_failed),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            else -> AnswerText(turn.answer, streaming = turn.phase == AnswerPhase.Streaming, large = true, maxLines = 6)
                        }
                    }
                    ActionStatus(turn.action, large = true)
                    Spacer(Modifier.height(24.dp))
                    val proposal = turn.proposals.firstOrNull()
                    val running = turn.action is ActionState.Running
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
                        BigButton(
                            label = stringResource(R.string.agent_navigate),
                            icon = AgentIcons.Navigate,
                            primary = true,
                            enabled = proposal != null && !running && turn.action !is ActionState.Done,
                            onClick = { proposal?.let(onConfirm) },
                            modifier = Modifier.weight(1.4f),
                        )
                        BigButton(
                            label = stringResource(R.string.agent_cancel),
                            icon = AgentIcons.Close,
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = modifier) {
        Surface(
            onClick = onVoiceRequest,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(180.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(AgentIcons.Mic, contentDescription = stringResource(R.string.agent_mic), modifier = Modifier.size(88.dp))
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.agent_entry_driving),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BigButton(
    label: String,
    icon: ImageVector?,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(20.dp))
        }
        Text(label, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
    }
    val shape = RoundedCornerShape(32.dp)
    val m = modifier.height(144.dp)
    if (primary) {
        Button(onClick = onClick, enabled = enabled, shape = shape, modifier = m, content = content)
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled, shape = shape, modifier = m, content = content)
    }
}
