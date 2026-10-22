package com.atopy.Atopass.domain.chatbot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.support.ApiTestSupport;
import com.atopy.Atopass.support.FakeChatModelClient;
import com.atopy.Atopass.support.FakeLlmConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** GET /chatbot/sessions/{sessionId}/draft : 새로고침 뒤에도 총정리 화면이 초안을 다시 채울 수 있어야 한다. */
@Import(FakeLlmConfig.class)
class ChatbotDraftIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;

	@Autowired
	FakeChatModelClient llm;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	@Test
	void 대화_전의_새_세션은_빈_초안과_고통_지수_대기_상태다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.draft.skinConditions").isEmpty())
			.andExpect(jsonPath("$.data.draft.sleepHours").doesNotExist())
			.andExpect(jsonPath("$.data.pendingFields[0]").value("painIndex"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false));
	}

	@Test
	void 마지막_메시지_응답과_같은_초안_대기_항목을_준다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"painIndex": 5, "conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 4}}], "alcohol": false}}""");
		mockMvc.perform(sendMessage(bearer, session, "고통은 5점, 팔 가려움 4점이고 술은 안 마셨어요"))
			.andExpect(status().isOk());

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.draft.skinConditions[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(4))
			.andExpect(jsonPath("$.data.draft.lifestyle.alcohol").value(false))
			.andExpect(jsonPath("$.data.draft.painIndex").value(5))
			.andExpect(jsonPath("$.data.pendingFields.length()").value(4))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("sleepHours"))
			.andExpect(jsonPath("$.data.pendingFields[1]").value("medication"))
			.andExpect(jsonPath("$.data.pendingFields[2]").value("skinSigns"))
			.andExpect(jsonPath("$.data.pendingFields[3]").value("notes"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false));
	}

	@Test
	void 확인_단계로_넘어간_상태도_새로고침_뒤에_같은_값이다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"painIndex": 4, "sleepHours": 6, "medication": true, "skinSigns": ["XEROSIS"],
			 "skipped": ["bodyParts", "notes"]}}""");
		mockMvc.perform(sendMessage(bearer, session, "고통 4점, 6시간 잤고 약 먹었어요. 건조증만 있고 나머진 없어요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true));

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.pendingFields").isEmpty())
			.andExpect(jsonPath("$.data.draft.sleepHours").value(6.0))
			.andExpect(jsonPath("$.data.draft.sleepScore").value(4))
			.andExpect(jsonPath("$.data.draft.medication").value(true))
			.andExpect(jsonPath("$.data.draft.skinSigns[0]").value("XEROSIS"));
	}

	@Test
	void 사용자가_마치겠다고_해서_넘어간_상태도_유지된다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		// 선택 항목(수면 등)이 남아 있지만 사용자가 마치겠다고 한 경우: 메시지 응답은 requiresConfirmation=true였다.
		llm.enqueue("""
			{"understood": true, "reply": "네", "finished": true,
			 "updates": {"painIndex": 3}}""");
		mockMvc.perform(sendMessage(bearer, session, "고통은 3점이고 그만할게요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true));

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.pendingFields").isEmpty());
	}

	@Test
	void 확인_단계가_아닌_응답이_오면_확인_상태가_풀린다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "네", "finished": true,
			 "updates": {"painIndex": 3, "conditions": [{"bodyPart": "FACE", "symptoms": {"pain": 3}}]}}""");
		mockMvc.perform(sendMessage(bearer, session, "고통 3점, 얼굴 건조 3점, 그만할게요"));
		// 총정리에서 되돌아와 새 내용을 말한 경우: 점수를 빠뜨린 새 부위가 생기면 필수 항목이 비게 된다.
		llm.enqueue("""
			{"understood": true, "reply": "팔은 몇 점인가요?",
			 "updates": {"conditions": [{"bodyPart": "ARM"}]}}""");
		mockMvc.perform(sendMessage(bearer, session, "팔도 있어요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false));

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("symptoms"));
	}

	@Test
	void 해석에_실패해도_초안은_그대로다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 4}}]}}""");
		mockMvc.perform(sendMessage(bearer, session, "팔 4점"));
		llm.enqueue("JSON이 아닌 응답");
		mockMvc.perform(sendMessage(bearer, session, "음..."))
			.andExpect(jsonPath("$.data.understood").value(false));

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(4));
	}

	@Test
	void 다른_회원의_세션은_404이다() throws Exception {
		String session = createSession(newMemberBearer(), "RECORD");

		mockMvc.perform(get(draftUrl(session)).header(AUTH, newMemberBearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
	}

	@Test
	void 기록을_저장해_닫힌_세션은_409이다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", Map.of("pain", 4));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", "2026-10-10");
		body.put("confirmed", true);
		body.put("record", Map.of("skinConditions", List.of(condition)));
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("SESSION_CLOSED"));
	}

	@Test
	void 질의_모드_세션에는_초안이_없어_400이다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "QUERY");

		mockMvc.perform(get(draftUrl(session)).header(AUTH, bearer))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 없는_세션은_404이고_토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get(draftUrl("no-such-session")).header(AUTH, newMemberBearer()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get(draftUrl("no-such-session")))
			.andExpect(status().isUnauthorized());
	}

	// ── 헬퍼 ──

	private String draftUrl(String session) {
		return "/api/v1/chatbot/sessions/" + session + "/draft";
	}

	/** 챗봇은 이용 동의가 있어야 쓸 수 있으므로, 테스트에서는 먼저 동의(멱등)하고 세션을 만든다. */
	private String createSession(String bearer, String mode) throws Exception {
		mockMvc.perform(post("/api/v1/chatbot/consent").header(AUTH, bearer)).andExpect(status().isOk());
		String body = mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer),
				Map.of("mode", mode)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("data").get("sessionId").asString();
	}

	private MockHttpServletRequestBuilder sendMessage(String bearer, String session, String message) {
		return withJson(post("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer),
			Map.of("message", message));
	}

}
