package com.atopy.Atopass.domain.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 사용자가 증상을 물으면 쉬운 말로 설명하고, 예시 사진을 보여 줘도 되는지 먼저 묻는다.
 * 보여 달라고 답했을 때만 답에 사진(imageSign)이 붙고, 화면은 GET /chatbot/skin-signs/{sign}/image 로 사진을 불러온다.
 */
@Import(FakeLlmConfig.class)
class ChatbotSkinSignImageIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final String PAIN_QUESTION = "오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)";
	private static final String LICHENIFICATION_OFFER = "태선화 예시 사진을 보여 드려도 될까요? 실제 피부 사진이 나와요.";

	@Autowired
	FakeChatModelClient llm;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	@Test
	void 증상을_물으면_LLM을_부르지_않고_설명한_뒤_사진을_보여_줘도_되는지_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);

		mockMvc.perform(sendMessage(bearer, session, "태선화가 뭐예요?"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.understood").value(true))
			.andExpect(jsonPath("$.data.reply").value(
				"'태선화'는 오래 긁어서 피부가 두꺼워지고 거칠어짐, 주름이 깊어짐을 뜻해요.\n\n" + LICHENIFICATION_OFFER))
			.andExpect(jsonPath("$.data.askingField").value("signImage"))
			.andExpect(jsonPath("$.data.imageSign").doesNotExist());
		assertThat(llm.callCount()).isZero();

		// 묻는 동안에는 다른 항목을 고를 수 없다.
		mockMvc.perform(choice(bearer, session, "painIndex", 5)).andExpect(status().isBadRequest());
		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.askingField").value("signImage"));
	}

	@Test
	void 보여_달라고_답하면_사진을_붙이고_원래_묻던_질문을_이어서_한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);
		mockMvc.perform(sendMessage(bearer, session, "태선화는 어떻게 생겼어요?"));

		mockMvc.perform(choice(bearer, session, "signImage", true))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.imageSign").value("LICHENIFICATION"))
			.andExpect(jsonPath("$.data.reply").value("태선화 예시 사진이에요. 사람마다 모양과 정도가 다를 수 있어요.\n\n" + PAIN_QUESTION))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"));

		// 새로고침해도 사진을 보여 준 말에는 사진이 붙어 있다.
		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages[2].content").value("보여 주세요"))
			.andExpect(jsonPath("$.data.messages[3].imageSign").value("LICHENIFICATION"))
			.andExpect(jsonPath("$.data.messages[1].imageSign").doesNotExist());
		assertThat(llm.callCount()).isZero();
	}

	@Test
	void 괜찮다고_답하면_사진_없이_원래_묻던_질문을_이어서_한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);
		mockMvc.perform(choice(bearer, session, "painIndex", 4));
		mockMvc.perform(sendMessage(bearer, session, "홍반이 무슨 뜻이에요?"))
			.andExpect(jsonPath("$.data.askingField").value("signImage"));

		mockMvc.perform(choice(bearer, session, "signImage", false))
			.andExpect(jsonPath("$.data.imageSign").doesNotExist())
			.andExpect(jsonPath("$.data.reply").value(
				"알겠어요. 사진은 보여 드리지 않을게요.\n\n오늘 나타난 증상을 모두 골라 주세요. 하나도 없으면 '없어요'를 눌러 주세요."))
			.andExpect(jsonPath("$.data.askingField").value("skinSigns"));
	}

	@Test
	void 증상을_알려_주는_말은_질문으로_보지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);

		mockMvc.perform(sendMessage(bearer, session, "팔에 진물이 나요"))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"));
		assertThat(llm.callCount()).isOne();
	}

	@Test
	void 사진을_묻는_중에_다른_말을_하면_묻던_것을_끝내고_그_말을_받는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);
		mockMvc.perform(sendMessage(bearer, session, "태선화가 뭐예요?"));
		llm.enqueue("{\"understood\": true, \"reply\": \"네\", \"updates\": {\"painIndex\": 3}}");

		mockMvc.perform(sendMessage(bearer, session, "고통은 3점이에요"))
			.andExpect(jsonPath("$.data.draft.painIndex").value(3))
			.andExpect(jsonPath("$.data.askingField").value("skinSigns"));
		mockMvc.perform(choice(bearer, session, "signImage", true)).andExpect(status().isBadRequest());
	}

	@Test
	void LLM이_일상_표현으로_물은_증상을_설명하면_사진을_보여_줘도_되는지_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);
		llm.enqueue("""
			{"understood": true, "reply": "오래 긁어서 피부가 두꺼워지는 걸 태선화라고 해요.", "updates": {},
			 "explain": true, "sign": "LICHENIFICATION"}""");

		mockMvc.perform(sendMessage(bearer, session, "피부가 코끼리처럼 두꺼워지는 건 왜 그래?"))
			.andExpect(jsonPath("$.data.reply").value("오래 긁어서 피부가 두꺼워지는 걸 태선화라고 해요.\n\n" + LICHENIFICATION_OFFER))
			.andExpect(jsonPath("$.data.askingField").value("signImage"));
	}

	@Test
	void 사진을_묻지_않은_때에_사진_답을_보내면_400이다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer);

		mockMvc.perform(choice(bearer, session, "signImage", true))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 증상_예시_사진은_로그인한_사용자만_받을_수_있다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(get("/api/v1/chatbot/skin-signs/ERYTHEMA/image").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, private"));
		mockMvc.perform(get("/api/v1/chatbot/skin-signs/ERYTHEMA/image")).andExpect(status().isUnauthorized());
		// 가려움은 예시 사진이 없다.
		mockMvc.perform(get("/api/v1/chatbot/skin-signs/ITCHING/image").header(AUTH, bearer))
			.andExpect(status().isNotFound());
	}

	private String createSession(String bearer) throws Exception {
		mockMvc.perform(post("/api/v1/chatbot/consent").header(AUTH, bearer)).andExpect(status().isOk());
		String body = mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer),
				Map.of("mode", "RECORD")))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("data").get("sessionId").asString();
	}

	private static String url(String session, String path) {
		return "/api/v1/chatbot/sessions/" + session + "/" + path;
	}

	private MockHttpServletRequestBuilder choice(String bearer, String session, String field, Object value) {
		return withJson(post(url(session, "choices")).header(AUTH, bearer), Map.of("field", field, "value", value));
	}

	private MockHttpServletRequestBuilder sendMessage(String bearer, String session, String message) {
		return withJson(post(url(session, "messages")).header(AUTH, bearer), Map.of("message", message));
	}

}
