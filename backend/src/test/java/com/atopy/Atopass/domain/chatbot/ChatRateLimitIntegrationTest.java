package com.atopy.Atopass.domain.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.support.ApiTestSupport;
import com.atopy.Atopass.support.FakeChatModelClient;
import com.atopy.Atopass.support.FakeLlmConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 챗봇 호출 횟수 제한. 비용 보호를 위해 한도를 넘으면 LLM을 부르지 않고 429를 준다. */
@Import(FakeLlmConfig.class)
@TestPropertySource(properties = "app.llm.daily-message-limit=3")
class ChatRateLimitIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;

	@Autowired
	FakeChatModelClient llm;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	@Test
	void 한도까지는_정상이고_넘으면_429이며_LLM을_부르지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);

		for (int i = 1; i <= 3; i++) {
			mockMvc.perform(send(bearer, session, "팔이 가려워요 " + i)).andExpect(status().isOk());
		}
		int callsBefore = llm.callCount();

		mockMvc.perform(send(bearer, session, "하나 더"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("CHAT_LIMIT_EXCEEDED"));

		assertThat(llm.callCount()).isEqualTo(callsBefore);
	}

	@Test
	void 다른_회원의_사용량은_섞이지_않는다() throws Exception {
		String heavy = newMemberBearer();
		String heavySession = createSession(heavy);
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(send(heavy, heavySession, "메시지 " + i)).andExpect(status().isOk());
		}

		String other = newMemberBearer();
		String otherSession = createSession(other);

		mockMvc.perform(send(other, otherSession, "처음 보내요")).andExpect(status().isOk());
	}

	@Test
	void 한도에_도달해도_대화_내역과_초안_조회는_된다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(send(bearer, session, "메시지 " + i)).andExpect(status().isOk());
		}

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(status().isOk());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/chatbot/sessions/" + session + "/draft").header(AUTH, bearer))
			.andExpect(status().isOk());
	}

	private String createSession(String bearer) throws Exception {
		mockMvc.perform(post("/api/v1/chatbot/consent").header(AUTH, bearer)).andExpect(status().isOk());
		String body = mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer),
				Map.of("mode", "RECORD")))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("data").get("sessionId").asString();
	}

	private MockHttpServletRequestBuilder send(String bearer, String session, String message) {
		return withJson(post("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer),
			Map.of("message", message));
	}

}
