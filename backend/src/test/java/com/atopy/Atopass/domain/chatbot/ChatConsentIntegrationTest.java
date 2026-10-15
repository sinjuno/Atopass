package com.atopy.Atopass.domain.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 챗봇 이용 동의: 대화·기록 내용이 외부 LLM으로 나가므로, 동의한 회원만 쓸 수 있고 서버가 이를 확인한다. */
@Import(FakeLlmConfig.class)
class ChatConsentIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final String CONSENT = "/api/v1/chatbot/consent";

	@Autowired
	FakeChatModelClient llm;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	// ── 조회 · 동의 · 철회 ──

	@Test
	void 처음에는_동의하지_않은_상태다() throws Exception {
		mockMvc.perform(get(CONSENT).header(AUTH, newMemberBearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.agreed").value(false))
			.andExpect(jsonPath("$.data.agreedAt").doesNotExist());
	}

	@Test
	void 동의하면_동의_상태와_시각이_저장된다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(post(CONSENT).header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.agreed").value(true))
			.andExpect(jsonPath("$.data.agreedAt").isNotEmpty());

		mockMvc.perform(get(CONSENT).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.agreed").value(true))
			.andExpect(jsonPath("$.data.agreedAt").isNotEmpty());
	}

	@Test
	void 다시_동의해도_처음_동의한_시각을_유지한다() throws Exception {
		String bearer = newMemberBearer();
		String first = agreedAt(mockMvc.perform(post(CONSENT).header(AUTH, bearer))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		String again = agreedAt(mockMvc.perform(post(CONSENT).header(AUTH, bearer))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		assertThat(again).isEqualTo(first);
	}

	@Test
	void 철회하면_동의하지_않은_상태로_돌아간다() throws Exception {
		String bearer = newMemberBearer();
		mockMvc.perform(post(CONSENT).header(AUTH, bearer));

		mockMvc.perform(delete(CONSENT).header(AUTH, bearer)).andExpect(status().isNoContent());

		mockMvc.perform(get(CONSENT).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.agreed").value(false))
			.andExpect(jsonPath("$.data.agreedAt").doesNotExist());
		// 동의하지 않은 상태에서 철회해도 오류가 아니다.
		mockMvc.perform(delete(CONSENT).header(AUTH, bearer)).andExpect(status().isNoContent());
	}

	@Test
	void 동의는_회원마다_따로다() throws Exception {
		String agreed = newMemberBearer();
		String other = newMemberBearer();
		mockMvc.perform(post(CONSENT).header(AUTH, agreed));

		mockMvc.perform(get(CONSENT).header(AUTH, other))
			.andExpect(jsonPath("$.data.agreed").value(false));
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get(CONSENT)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(CONSENT)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(CONSENT)).andExpect(status().isUnauthorized());
	}

	// ── 동의가 없으면 챗봇을 쓸 수 없다 ──

	@Test
	void 동의하지_않으면_대화를_만들_수_없다() throws Exception {
		mockMvc.perform(createSession(newMemberBearer(), "RECORD"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
		mockMvc.perform(createSession(newMemberBearer(), "QUERY"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
	}

	@Test
	void 동의한_뒤에는_대화를_만들_수_있다() throws Exception {
		String bearer = newMemberBearer();
		mockMvc.perform(post(CONSENT).header(AUTH, bearer));

		mockMvc.perform(createSession(bearer, "RECORD")).andExpect(status().isCreated());
	}

	@Test
	void 동의_철회_뒤에는_메시지를_보낼_수_없고_LLM도_호출하지_않는다() throws Exception {
		String bearer = newMemberBearer();
		mockMvc.perform(post(CONSENT).header(AUTH, bearer));
		String session = sessionId(mockMvc.perform(createSession(bearer, "RECORD"))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		llm.enqueue("{\"understood\": true, \"reply\": \"어느 부위인가요?\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, session, "기록할게요")).andExpect(status().isOk());
		int callsBefore = llm.callCount();

		mockMvc.perform(delete(CONSENT).header(AUTH, bearer)).andExpect(status().isNoContent());

		mockMvc.perform(sendMessage(bearer, session, "계속할게요"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
		mockMvc.perform(createSession(bearer, "RECORD"))
			.andExpect(status().isForbidden());
		assertThat(llm.callCount()).isEqualTo(callsBefore);

		// 다시 동의하면 이어서 쓸 수 있다.
		mockMvc.perform(post(CONSENT).header(AUTH, bearer));
		llm.enqueue("{\"understood\": true, \"reply\": \"몇 점인가요?\", \"updates\": {\"conditions\": [{\"bodyPart\": \"ARM\"}]}}");
		mockMvc.perform(sendMessage(bearer, session, "팔이요")).andExpect(status().isOk());
	}

	@Test
	void 동의하지_않은_회원의_메시지_전송은_세션이_있어도_403이다() throws Exception {
		// 동의한 회원의 세션 ID를 알아도, 동의하지 않은 다른 회원은 403(CONSENT_REQUIRED)으로 막힌다.
		String owner = newMemberBearer();
		mockMvc.perform(post(CONSENT).header(AUTH, owner));
		String session = sessionId(mockMvc.perform(createSession(owner, "RECORD"))
			.andReturn().getResponse().getContentAsString());

		mockMvc.perform(sendMessage(newMemberBearer(), session, "안녕"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
		assertThat(llm.callCount()).isZero();
	}

	@Test
	void 동의를_철회해도_이미_쌓인_대화와_초안은_볼_수_있고_확인한_초안은_저장할_수_있다() throws Exception {
		String bearer = newMemberBearer();
		mockMvc.perform(post(CONSENT).header(AUTH, bearer));
		String session = sessionId(mockMvc.perform(createSession(bearer, "RECORD"))
			.andReturn().getResponse().getContentAsString());
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}}""");
		mockMvc.perform(sendMessage(bearer, session, "팔 4점")).andExpect(status().isOk());

		mockMvc.perform(delete(CONSENT).header(AUTH, bearer)).andExpect(status().isNoContent());

		// 내 데이터를 읽는 것은 외부 LLM 이용이 아니므로 막지 않는다.
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.messages.length()").value(2));
		// 이미 받은 응답을 사용자가 확인해 저장하는 것도 LLM 호출이 없으므로 가능하다.
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", Map.of("itching", 4));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", "2026-10-10");
		body.put("confirmed", true);
		body.put("record", Map.of("skinConditions", List.of(condition)));
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

	// ── 헬퍼 ──

	private MockHttpServletRequestBuilder createSession(String bearer, String mode) {
		return withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer), Map.of("mode", mode));
	}

	private MockHttpServletRequestBuilder sendMessage(String bearer, String session, String message) {
		return withJson(post("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer),
			Map.of("message", message));
	}

	private String sessionId(String responseBody) {
		return jsonMapper.readTree(responseBody).get("data").get("sessionId").asString();
	}

	private String agreedAt(String responseBody) {
		return jsonMapper.readTree(responseBody).get("data").get("agreedAt").asString();
	}

}
