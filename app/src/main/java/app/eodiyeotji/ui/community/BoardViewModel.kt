package app.eodiyeotji.ui.community

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eodiyeotji.core.BoardComment
import app.eodiyeotji.core.BoardPost
import app.eodiyeotji.core.CommunitySort
import app.eodiyeotji.core.EodiCore
import app.eodiyeotji.core.addBoardComment
import app.eodiyeotji.core.addBoardPost
import app.eodiyeotji.core.boardComments
import app.eodiyeotji.core.boardPost
import app.eodiyeotji.core.boardPosts
import app.eodiyeotji.core.toggleBoardCommentHeart
import app.eodiyeotji.core.toggleBoardPostHeart
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BoardThread(val post: BoardPost, val comments: Load<BoardComment> = Load.Loading, val sending: Boolean = false)

@Immutable
data class BoardUiState(
    val sort: CommunitySort = CommunitySort.POPULAR,
    val list: Load<BoardPost> = Load.Loading,
    val thread: BoardThread? = null,
    val writing: Boolean = false,
    val posting: Boolean = false,
)

/** 커뮤니티 > 게시판: 글 목록(인기순·최신순) · 글 + 댓글 · 글·댓글 쓰기 · 하트. 정렬·댓글 수 집계는 Rust. */
@HiltViewModel
class BoardViewModel @Inject constructor(private val core: EodiCore) : ViewModel() {
    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    private var started = false

    /** 목록 첫 읽기. 홈이 검색 결과 열기용으로 이 ViewModel을 일찍 만들어도 게시판 탭이 처음 보일 때만 읽는다. */
    fun start() {
        if (started) return
        started = true
        reload()
    }

    fun setSort(sort: CommunitySort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val sort = _state.value.sort
            val r = try {
                Load.Loaded(core.boardPosts(sort))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { it.copy(list = r) }
        }
    }

    fun open(post: BoardPost) {
        _state.update { it.copy(thread = BoardThread(post)) }
        loadComments(post.id)
    }

    fun close() {
        _state.update { it.copy(thread = null) }
    }

    private fun loadComments(postId: String) {
        viewModelScope.launch {
            val r = try {
                Load.Loaded(core.boardComments(postId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { s -> s.thread?.takeIf { it.post.id == postId }?.let { s.copy(thread = it.copy(comments = r)) } ?: s }
        }
    }

    fun sendComment(body: String, onSent: () -> Unit) {
        val t = _state.value.thread ?: return
        if (body.isBlank() || t.sending) return
        _state.update { it.copy(thread = t.copy(sending = true)) }
        viewModelScope.launch {
            try {
                core.addBoardComment(t.post.id, body)
                val post = core.boardPost(t.post.id) ?: t.post
                _state.update { s -> s.copy(thread = s.thread?.copy(post = post, sending = false), list = s.list.replace(post)) }
                loadComments(t.post.id)
                onSent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "댓글 실패", e)
                _state.update { s -> s.copy(thread = s.thread?.copy(sending = false)) }
            }
        }
    }

    fun startWriting() = _state.update { it.copy(writing = true) }
    fun stopWriting() = _state.update { it.copy(writing = false) }

    /** 글 올리기. 태그는 띄어쓰기로 나눠 넘긴다 ("#"·빈 칸 정리는 Rust). */
    fun post(title: String, body: String, tags: String) {
        if (title.isBlank() || _state.value.posting) return
        _state.update { it.copy(posting = true) }
        viewModelScope.launch {
            try {
                val p = core.addBoardPost(title, body, tags.split(' ', ',', '\n'))
                _state.update { it.copy(posting = false, writing = false, sort = CommunitySort.RECENT, thread = BoardThread(p, Load.Loaded(emptyList()))) }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "글 실패", e)
                _state.update { it.copy(posting = false) }
            }
        }
    }

    fun togglePostHeart(id: String) {
        _state.update { it.flipPost(id) }
        viewModelScope.launch {
            runCatching { core.toggleBoardPostHeart(id) }
                .onSuccess { n -> _state.update { it.setPostHearts(id, n) } }
                .onFailure { _state.update { s -> s.flipPost(id) } }
        }
    }

    fun toggleCommentHeart(id: String) {
        _state.update { it.flipComment(id) }
        viewModelScope.launch {
            runCatching { core.toggleBoardCommentHeart(id) }.onFailure { _state.update { s -> s.flipComment(id) } }
        }
    }

    private companion object {
        const val TAG = "EodiBoard"
    }
}

private fun Load<BoardPost>.replace(p: BoardPost): Load<BoardPost> =
    if (this is Load.Loaded) Load.Loaded(items.map { if (it.id == p.id) p else it }) else this

private fun BoardPost.flip() = copy(heartedByMe = !heartedByMe, hearts = hearts + if (heartedByMe) -1 else 1)

private fun BoardUiState.flipPost(id: String): BoardUiState {
    val l = list
    return copy(
        list = if (l is Load.Loaded) Load.Loaded(l.items.map { if (it.id == id) it.flip() else it }) else l,
        thread = thread?.let { if (it.post.id == id) it.copy(post = it.post.flip()) else it },
    )
}

private fun BoardUiState.setPostHearts(id: String, n: Int): BoardUiState {
    val l = list
    return copy(
        list = if (l is Load.Loaded) Load.Loaded(l.items.map { if (it.id == id) it.copy(hearts = n) else it }) else l,
        thread = thread?.let { if (it.post.id == id) it.copy(post = it.post.copy(hearts = n)) else it },
    )
}

private fun BoardUiState.flipComment(id: String): BoardUiState {
    val t = thread ?: return this
    val c = t.comments as? Load.Loaded ?: return this
    return copy(
        thread = t.copy(
            comments = Load.Loaded(
                c.items.map { if (it.id == id) it.copy(heartedByMe = !it.heartedByMe, hearts = it.hearts + if (it.heartedByMe) -1 else 1) else it },
            ),
        ),
    )
}
