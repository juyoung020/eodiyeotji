package app.eodiyeotji.ui.community

import app.eodiyeotji.ui.common.PressSurface
import app.eodiyeotji.ui.common.agoText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eodiyeotji.R
import app.eodiyeotji.core.BoardComment
import app.eodiyeotji.core.BoardPost
import app.eodiyeotji.ui.theme.EodiIcons
import app.eodiyeotji.ui.theme.EodiTheme

/** 목록·글 본문이 너무 넓게 퍼지지 않게 (넓은 창에서 가운데 정렬). */
private val ReadWidth = 1240.dp

/** 커뮤니티 > 게시판: 질문·이야기 글 목록, 글 + 댓글, 글 쓰기. */
@Composable
fun BoardPane(viewModel: BoardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.start() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortChoice(state.sort, viewModel::setSort)
                Spacer(Modifier.weight(1f))
                PrimaryPill(stringResource(R.string.board_write), CommunityIcons.Pen, viewModel::startWriting)
            }
            when (val l = state.list) {
                Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorState(stringResource(R.string.community_failed, l.message), viewModel::reload)
                is Load.Loaded ->
                    if (l.items.isEmpty()) {
                        CenterMessage(stringResource(R.string.board_empty), icon = CommunityIcons.Chat)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                            LazyColumn(
                                Modifier.widthIn(max = ReadWidth).fillMaxSize(),
                                state = rememberTopListState(l.items.first().id),
                                contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(l.items, key = { it.id }, contentType = { "post" }) { p ->
                                    PostCard(p, onOpen = { viewModel.open(p) }, onHeart = { viewModel.togglePostHeart(p.id) })
                                }
                            }
                        }
                    }
            }
        }
        state.thread?.let { t ->
            BackHandler(onBack = viewModel::close)
            ThreadScreen(
                t,
                onBack = viewModel::close,
                onHeart = { viewModel.togglePostHeart(t.post.id) },
                onCommentHeart = viewModel::toggleCommentHeart,
                onSend = viewModel::sendComment,
            )
        }
        if (state.writing) {
            BackHandler(onBack = viewModel::stopWriting)
            WritePostSheet(posting = state.posting, onPost = viewModel::post, onDismiss = viewModel::stopWriting)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostCard(p: BoardPost, onOpen: () -> Unit, onHeart: () -> Unit) {
    val c = EodiTheme.colors
    PressSurface(onClick = onOpen, color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(p.region, p.author, agoText(p.createdMs)).joinToString(" · "),
                    style = EodiTheme.type.caption,
                    color = c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (p.mine) MineBadge()
            }
            Text(p.title, style = EodiTheme.type.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.body.isNotBlank()) {
                Text(p.body, style = EodiTheme.type.body, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), maxLines = 1) {
                    p.tags.forEach { InfoChip("#$it", tone = ChipTone.Accent) }
                }
                CommentCount(p.commentCount)
                Spacer(Modifier.width(16.dp))
                HeartButton(p.hearts, p.heartedByMe, onHeart, compact = true)
            }
        }
    }
}

@Composable
private fun CommentCount(n: Int) {
    val c = EodiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(CommunityIcons.Chat, null, tint = c.textTertiary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.board_comments, n), style = EodiTheme.type.label, color = c.textSecondary)
    }
}

/** 글 하나 + 댓글 + 아래 댓글 입력 줄. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThreadScreen(
    t: BoardThread,
    onBack: () -> Unit,
    onHeart: () -> Unit,
    onCommentHeart: (String) -> Unit,
    onSend: (String, () -> Unit) -> Unit,
) {
    val c = EodiTheme.colors
    val p = t.post
    var draft by rememberSaveable(p.id) { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = c.background, contentColor = c.textPrimary) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ReadWidth).fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 24.dp)) {
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(EodiIcons.Back, stringResource(R.string.community_back), onBack)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "post") {
                        Surface(color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusL), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    listOfNotNull(p.region, p.author, agoText(p.createdMs)).joinToString(" · "),
                                    style = EodiTheme.type.caption,
                                    color = c.textTertiary,
                                )
                                Text(p.title, style = EodiTheme.type.title, modifier = Modifier.semantics { heading() })
                                if (p.body.isNotBlank()) Text(p.body, style = EodiTheme.type.body)
                                if (p.tags.isNotEmpty()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        p.tags.forEach { InfoChip("#$it", tone = ChipTone.Accent) }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CommentCount(p.commentCount)
                                    Spacer(Modifier.weight(1f))
                                    HeartButton(p.hearts, p.heartedByMe, onHeart)
                                }
                            }
                        }
                    }
                    when (val cs = t.comments) {
                        Load.Loading -> item(key = "loading") { Box(Modifier.fillMaxWidth().height(160.dp)) { LoadingBox() } }
                        is Load.Failed -> item(key = "failed") { Text(stringResource(R.string.community_failed, cs.message), color = c.error) }
                        is Load.Loaded ->
                            if (cs.items.isEmpty()) {
                                item(key = "none") {
                                    Text(
                                        stringResource(R.string.board_no_comments),
                                        style = EodiTheme.type.body,
                                        color = c.textTertiary,
                                        modifier = Modifier.padding(16.dp),
                                    )
                                }
                            } else {
                                items(cs.items, key = { it.id }) { cm -> CommentRow(cm) { onCommentHeart(cm.id) } }
                            }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(1000) },
                        placeholder = { Text(stringResource(R.string.board_comment_hint), style = EodiTheme.type.body) },
                        textStyle = EodiTheme.type.body,
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    PrimaryPill(stringResource(R.string.board_comment_send), null, { onSend(draft) { draft = "" } }, enabled = draft.isNotBlank() && !t.sending)
                }
            }
        }
    }
}

@Composable
private fun CommentRow(cm: BoardComment, onHeart: () -> Unit) {
    val c = EodiTheme.colors
    Surface(color = c.surface, shape = RoundedCornerShape(EodiTheme.space.radiusM), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cm.author, style = EodiTheme.type.label, color = if (cm.mine) c.accent else c.textPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(agoText(cm.createdMs), style = EodiTheme.type.caption, color = c.textTertiary)
                }
                Text(cm.body, style = EodiTheme.type.body, color = c.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            HeartButton(cm.hearts, cm.heartedByMe, onHeart, compact = true)
        }
    }
}

/** 글 쓰기 (제목 · 내용 · 태그). */
@Composable
private fun WritePostSheet(posting: Boolean, onPost: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    val type = EodiTheme.type
    OverlayCard(onDismiss = { if (!posting) onDismiss() }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.board_write_title), style = type.title, modifier = Modifier.weight(1f))
                RoundIconButton(EodiIcons.Close, stringResource(R.string.community_close), onDismiss)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(100) },
                label = { Text(stringResource(R.string.board_title_label), style = type.caption) },
                textStyle = type.bodyStrong,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(2000) },
                label = { Text(stringResource(R.string.board_body_label), style = type.caption) },
                textStyle = type.body,
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it.take(100) },
                label = { Text(stringResource(R.string.board_tags_label), style = type.caption) },
                textStyle = type.body,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryPill(
                stringResource(R.string.board_submit),
                CommunityIcons.Upload,
                { onPost(title, body, tags) },
                Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && !posting,
            )
        }
    }
}
