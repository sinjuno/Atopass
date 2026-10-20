package com.atopy.Atopass.domain.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * POST /chatbot/sessions/{sessionId}/choices, /finish : 선택지 버튼으로 답하는 항목은 LLM을 거치지 않고 바로 반영한다.
 * 서버가 지금 묻는 항목(askingField)을 정하고, 그 항목에만 답할 수 있다.
 */
@Import(FakeLlmConfig.class)
class ChatbotChoiceIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final String PAIN_QUESTION = "오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)";
	private static final String BODY_PARTS_QUESTION = "어느 부위가 그렇게 불편했나요? 해당하는 부위를 모두 골라 주세요.";
	private static final String SIGN_PARTS_QUESTION = "그 증상이 어느 부위에 나타났나요? 해당하는 부위를 모두 골라 주세요.";
	private static final String SKIN_SIGNS_QUESTION = "오늘 나타난 증상을 모두 골라 주세요. 하나도 없으면 '없어요'를 눌러 주세요.";
	private static final String SLEEP_QUESTION = "어젯밤 몇 시간 주무셨나요?";

	@Autowired
	FakeChatModelClient llm;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	@Test
	void 새_세션은_고통_지수를_먼저_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"));
	}

	@Test
	void 선택지로_모두_답하면_LLM을_부르지_않고_확인_단계로_넘어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(choice(bearer, session, "painIndex", 6))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.understood").value(true))
			.andExpect(jsonPath("$.data.draft.painIndex").value(6))
			.andExpect(jsonPath("$.data.askingField").value("skinSigns"))
			.andExpect(jsonPath("$.data.reply").value(SKIN_SIGNS_QUESTION));
		mockMvc.perform(choice(bearer, session, "skinSigns", List.of("ERYTHEMA", "XEROSIS")))
			.andExpect(jsonPath("$.data.draft.skinSigns.length()").value(2))
			.andExpect(jsonPath("$.data.askingField").value("bodyParts"))
			.andExpect(jsonPath("$.data.reply").value(SIGN_PARTS_QUESTION));
		// 고통 지수·증상·부위는 한 묶음이라, 고른 부위에는 고통 지수가 점수로, 방금 고른 증상이 그 부위의 증상으로 들어간다.
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of("FACE", "LEFT_HAND")))
			.andExpect(jsonPath("$.data.draft.skinConditions.length()").value(2))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].bodyPart").value("FACE"))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(6))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].skinSigns[0]").value("ERYTHEMA"))
			.andExpect(jsonPath("$.data.draft.skinConditions[1].skinSigns.length()").value(2))
			.andExpect(jsonPath("$.data.askingField").value("sleepHours"))
			.andExpect(jsonPath("$.data.reply").value(SLEEP_QUESTION));
		mockMvc.perform(choice(bearer, session, "sleepHours", 7.5))
			.andExpect(jsonPath("$.data.draft.sleepHours").value(7.5))
			.andExpect(jsonPath("$.data.askingField").value("medication"))
			.andExpect(jsonPath("$.data.reply").value("오늘 약을 복용하셨나요?"));
		mockMvc.perform(choice(bearer, session, "medication", false))
			.andExpect(jsonPath("$.data.draft.medication").value(false))
			.andExpect(jsonPath("$.data.askingField").value("notes"));
		String saved = mockMvc.perform(skip(bearer, session, "notes"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.askingField").doesNotExist())
			.andExpect(jsonPath("$.data.pendingFields").isEmpty())
			.andExpect(jsonPath("$.data.editing").value(true))
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.reply").value(
				"오늘 기록을 저장했어요. 오늘의 총정리예요.\n- 고통 지수: 6점\n- 얼굴: 고통 6점, 홍반, 건조증\n- 왼손: 고통 6점, 홍반, 건조증\n- 수면 시간: 7.5시간\n- 약 복용: 아니오\n- 나타난 증상: 홍반, 건조증"
					+ "\n\n오늘 하루도 정말 수고 많으셨어요! 바꿀 내용이 생기면 언제든 여기에 글로 적어 주세요. 바로 고쳐 둘게요."))
			.andReturn().getResponse().getContentAsString();
		// 마지막 항목에 답하면 저장 버튼 없이 그날 기록이 바로 저장된다.
		mockMvc.perform(get("/api/v1/records").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.content[0].painIndex").value(6));
		// 저장된 기록에도 부위마다 그 부위의 증상이 함께 들어 있다.
		long recordId = jsonMapper.readTree(saved).get("data").get("recordId").asLong();
		mockMvc.perform(get("/api/v1/records/" + recordId).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.skinConditions[0].bodyPart").value("FACE"))
			.andExpect(jsonPath("$.data.skinConditions[0].skinSigns.length()").value(2))
			.andExpect(jsonPath("$.data.skinConditions[1].bodyPart").value("LEFT_HAND"))
			.andExpect(jsonPath("$.data.skinConditions[1].skinSigns.length()").value(2));

		assertThat(llm.callCount()).isZero();
		// 대화 내역에는 고른 값이 사람이 읽는 말로 남는다.
		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages.length()").value(12))
			.andExpect(jsonPath("$.data.messages[0].content").value("6점"))
			.andExpect(jsonPath("$.data.messages[2].content").value("홍반, 건조증"))
			.andExpect(jsonPath("$.data.messages[4].content").value("얼굴, 왼손"))
			.andExpect(jsonPath("$.data.messages[6].content").value("7.5시간"))
			.andExpect(jsonPath("$.data.messages[8].content").value("아니오"))
			.andExpect(jsonPath("$.data.messages[10].content").value("없어요"));
		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.askingField").doesNotExist());
	}

	@Test
	void 선택_항목은_넘어갈_수_있고_증상이_없으면_빈_배열이다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		mockMvc.perform(choice(bearer, session, "painIndex", 3));

		// 증상이 없어도 고통이 있으면 불편한 부위를 묻는다.
		mockMvc.perform(choice(bearer, session, "skinSigns", List.of()))
			.andExpect(jsonPath("$.data.draft.skinSigns").isEmpty())
			.andExpect(jsonPath("$.data.askingField").value("bodyParts"))
			.andExpect(jsonPath("$.data.reply").value(BODY_PARTS_QUESTION));
		mockMvc.perform(skip(bearer, session, "bodyParts"))
			.andExpect(jsonPath("$.data.draft.skinConditions").isEmpty())
			.andExpect(jsonPath("$.data.askingField").value("sleepHours"));
		mockMvc.perform(skip(bearer, session, "sleepHours"))
			.andExpect(jsonPath("$.data.draft.sleepHours").doesNotExist())
			.andExpect(jsonPath("$.data.askingField").value("medication"));
		mockMvc.perform(skip(bearer, session, "medication"))
			.andExpect(jsonPath("$.data.askingField").value("notes"));

		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages[2].content").value("없어요"))
			.andExpect(jsonPath("$.data.messages[4].content").value("없어요"))
			.andExpect(jsonPath("$.data.messages[6].content").value("넘어갈게요"));
	}

	@Test
	void 지금_묻는_항목이_아니면_400이다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(choice(bearer, session, "sleepHours", 7))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(choice(bearer, session, "alcohol", true))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 값이_규칙에_어긋나면_400이고_초안은_그대로다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(choice(bearer, session, "painIndex", 11)).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "painIndex", "6")).andExpect(status().isBadRequest());
		mockMvc.perform(withJson(post(url(session, "choices")).header(AUTH, bearer), Map.of("field", "painIndex")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "painIndex", 4));
		mockMvc.perform(choice(bearer, session, "skinSigns", List.of("RASH"))).andExpect(status().isBadRequest());
		mockMvc.perform(skip(bearer, session, "skinSigns"));
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of("NOSE"))).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of())).andExpect(status().isBadRequest());
		mockMvc.perform(skip(bearer, session, "bodyParts"));
		mockMvc.perform(choice(bearer, session, "sleepHours", 25)).andExpect(status().isBadRequest());
		mockMvc.perform(skip(bearer, session, "sleepHours"));
		mockMvc.perform(choice(bearer, session, "medication", "yes")).andExpect(status().isBadRequest());

		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.draft.painIndex").value(4))
			.andExpect(jsonPath("$.data.draft.skinSigns").doesNotExist())
			.andExpect(jsonPath("$.data.askingField").value("medication"));
		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages.length()").value(8));
	}

	@Test
	void 고통_지수는_건너뛸_수_없고_특이사항은_선택지로_글을_보낼_수_없다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		mockMvc.perform(skip(bearer, session, "painIndex")).andExpect(status().isBadRequest());

		mockMvc.perform(choice(bearer, session, "painIndex", 5));
		mockMvc.perform(skip(bearer, session, "skinSigns"));
		mockMvc.perform(skip(bearer, session, "bodyParts"));
		mockMvc.perform(skip(bearer, session, "sleepHours"));
		mockMvc.perform(skip(bearer, session, "medication"));
		mockMvc.perform(choice(bearer, session, "notes", "새 보습제를 썼어요")).andExpect(status().isBadRequest());
	}

	@Test
	void LLM이_다음_항목을_다른_문장으로_물어도_서버의_정해진_질문으로_바꾼다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"약은 드셨어요?\", \"updates\": {\"painIndex\": 5}}");

		mockMvc.perform(sendMessage(bearer, session, "오늘은 5점 정도예요"))
			.andExpect(jsonPath("$.data.askingField").value("skinSigns"))
			.andExpect(jsonPath("$.data.reply").value(SKIN_SIGNS_QUESTION));
	}

	@Test
	void LLM이_부위나_점수를_되물으면_글로_답하도록_symptoms를_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"어느 부위가 가려우신가요?\", \"updates\": {}, \"followUp\": true}");

		mockMvc.perform(sendMessage(bearer, session, "너무 가려워요"))
			.andExpect(jsonPath("$.data.askingField").value("symptoms"))
			.andExpect(jsonPath("$.data.reply").value("어느 부위가 가려우신가요?"));
		// 새로고침해도 같은 상태이고, 되묻는 동안에는 선택지로 답할 수 없다.
		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.askingField").value("symptoms"));
		mockMvc.perform(choice(bearer, session, "painIndex", 5)).andExpect(status().isBadRequest());

		llm.enqueue("{\"understood\": true, \"reply\": \"몇 점인가요?\","
			+ " \"updates\": {\"conditions\": [{\"bodyPart\": \"ARM\", \"symptoms\": {\"pain\": 6}}]}}");
		mockMvc.perform(sendMessage(bearer, session, "팔이요, 6점"))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"))
			.andExpect(jsonPath("$.data.reply").value(PAIN_QUESTION));
	}

	@Test
	void 부위만_말하고_점수가_없으면_LLM의_질문을_그대로_쓴다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "가려움 정도를 점수로 나타내면 0~10점 중 몇 점인가요?",
			 "updates": {"painIndex": 4, "conditions": [{"bodyPart": "ARM"}]}}""");

		mockMvc.perform(sendMessage(bearer, session, "고통 4점이고 팔이 가려워요"))
			.andExpect(jsonPath("$.data.askingField").value("symptoms"))
			.andExpect(jsonPath("$.data.reply").value("가려움 정도를 점수로 나타내면 0~10점 중 몇 점인가요?"));
	}

	@Test
	void 기록_마치기는_필수_항목이_채워졌을_때만_확인_단계로_넘어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(post(url(session, "finish")).header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"))
			.andExpect(jsonPath("$.data.reply").value(PAIN_QUESTION));

		mockMvc.perform(choice(bearer, session, "painIndex", 2));
		mockMvc.perform(post(url(session, "finish")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.askingField").doesNotExist())
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.pendingFields").isEmpty());

		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages[4].content").value("기록을 마칠게요"));
		assertThat(llm.callCount()).isZero();
	}

	@Test
	void 질의_모드에서는_선택지로_답할_수_없다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "QUERY");

		mockMvc.perform(choice(bearer, session, "painIndex", 5))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(post(url(session, "finish")).header(AUTH, bearer))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 저장된_세션에는_답할_수_없고_다른_회원의_세션은_404다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		String other = newMemberBearer();
		mockMvc.perform(post("/api/v1/chatbot/consent").header(AUTH, other)).andExpect(status().isOk());
		mockMvc.perform(choice(other, session, "painIndex", 5))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));

		mockMvc.perform(choice(bearer, session, "painIndex", 5));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString());
		body.put("confirmed", true);
		body.put("record", Map.of("painIndex", 5));
		mockMvc.perform(withJson(post(url(session, "records")).header(AUTH, bearer), body))
			.andExpect(status().isCreated());

		mockMvc.perform(skip(bearer, session, "sleepHours"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("SESSION_CLOSED"));
	}

	private String createSession(String bearer, String mode) throws Exception {
		mockMvc.perform(post("/api/v1/chatbot/consent").header(AUTH, bearer)).andExpect(status().isOk());
		String body = mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer),
				Map.of("mode", mode)))
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

	private MockHttpServletRequestBuilder skip(String bearer, String session, String field) {
		return withJson(post(url(session, "choices")).header(AUTH, bearer), Map.of("field", field, "skip", true));
	}

	private MockHttpServletRequestBuilder sendMessage(String bearer, String session, String message) {
		return withJson(post(url(session, "messages")).header(AUTH, bearer), Map.of("message", message));
	}

}
