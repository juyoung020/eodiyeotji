package app.eodiyeotji.agent.roadside

import app.eodiyeotji.BuildConfig
import app.eodiyeotji.core.rust.LlmConfig
import app.eodiyeotji.core.rust.roadsideLlmChat
import app.eodiyeotji.place.blockingWithTimeout
import org.json.JSONArray
import org.json.JSONObject

/** 모델이 부른 도구 하나. [arguments]는 모델이 쓴 JSON 문자열 그대로 (검증은 Rust `roadside_parse_call`). */
data class LlmToolCall(val id: String, val name: String, val arguments: String)

/** 채팅 한 번의 답: 글 또는 도구 호출. */
data class LlmReply(val content: String, val toolCalls: List<LlmToolCall>, val ms: Long)

/**
 * 도구를 쓰는 LLM 채팅 입구 (공급자 중립).
 * - [messages]: OpenAI 모양 메시지 (`role` system/user/assistant/tool, assistant의 `tool_calls`, tool의 `tool_call_id`).
 * - [tools]: 공급자 중립 도구 정의 JSON (`[{name, description, parameters(JSON Schema)}]`, Rust `roadside_tool_specs`).
 *   구현이 자기 모양으로 옮긴다 (OpenAI `tools[].function` · Gemini `functionDeclarations`).
 * 실패(연결 · 시간 초과 · 형식)는 null — 부르는 쪽이 규칙 길로 넘어간다.
 */
interface LlmChat {
    val available: Boolean
    suspend fun chat(messages: JSONArray, tools: String?, maxTokens: Int, timeoutMs: Long): LlmReply?
}

/**
 * OpenAI 호환 Chat Completions (`/v1/chat/completions`). 실제 HTTP · 응답 해석은 Rust(`roadside::llm_chat`, 기억 에이전트와 같은 클라이언트).
 * 주소 · 모델은 BuildConfig (중계 `/llm/v1` → 개발 PC Qwen). Gemini로 바꿀 때는 중계에 Gemini OpenAI 호환 경로를 두고
 * `LLM_BASE_URL` · `LLM_MODEL`만 바꾼다 (키는 중계에만, docs/소통/2026-11-13_길위_도구_에이전트.md).
 */
class OpenAiCompatChat(
    private val config: LlmConfig = LlmConfig(
        baseUrl = BuildConfig.LLM_BASE_URL,
        model = BuildConfig.LLM_MODEL,
        apiKey = BuildConfig.LLM_API_KEY,
        connectTimeoutMs = 1_500u,
        readTimeoutMs = 7_000u,
    ),
) : LlmChat {
    override val available: Boolean get() = config.baseUrl.isNotBlank() && config.model.isNotBlank()

    override suspend fun chat(messages: JSONArray, tools: String?, maxTokens: Int, timeoutMs: Long): LlmReply? {
        if (!available) return null
        val req = JSONObject()
            .put("messages", messages)
            .put("tools", if (tools.isNullOrBlank()) JSONArray() else JSONArray(tools))
            .put("max_tokens", maxTokens)
            .toString()
        val raw = runCatching { blockingWithTimeout(timeoutMs) { roadsideLlmChat(config, req) } }
            .onFailure { android.util.Log.w("EodiRoadside", "LLM 호출 실패: ${it.message}") }
            .getOrNull() ?: return null.also { android.util.Log.w("EodiRoadside", "LLM 호출 실패 또는 ${timeoutMs}ms 초과") }
        val o = JSONObject(raw)
        val calls = o.optJSONArray("tool_calls")?.let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).let { LlmToolCall(it.optString("id"), it.optString("name"), it.optString("arguments")) } }
        }.orEmpty()
        return LlmReply(o.optString("content"), calls, o.optLong("ms"))
    }
}
