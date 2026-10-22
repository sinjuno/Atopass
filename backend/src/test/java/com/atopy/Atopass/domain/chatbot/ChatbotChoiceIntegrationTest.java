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
	private static final String TREND_QUESTION = "오늘 피부 상태는 어땠나요?";
	private static final String PAIN_QUESTION = "오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)";
	private static final String BODY_PARTS_QUESTION = "어디가 가장 불편했나요? 해당하는 부위를 모두 골라 주세요.";

	@Autowired
	FakeChatModelClient llm;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	@Test
	void 새_세션은_피부_상태를_먼저_묻고_선택지를_준다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.askingField").value("skinTrend"))
			.andExpect(jsonPath("$.data.choice.label").value("피부 상태"))
			.andExpect(jsonPath("$.data.choice.options[0]").value("좋아짐"))
			.andExpect(jsonPath("$.data.choice.options[2]").value("평소보다 나빠짐"))
			.andExpect(jsonPath("$.data.choice.multiple").value(false));
	}

	@Test
	void 선택지로_모두_답하면_LLM을_부르지_않고_확인_단계로_넘어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		// 나빠진 날은 원인을 묻는 연결 질문을 3개까지 끼워 묻는다.
		mockMvc.perform(choice(bearer, session, "skinTrend", "평소보다 나빠짐"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.understood").value(true))
			.andExpect(jsonPath("$.data.askingField").value("C01"))
			.andExpect(jsonPath("$.data.choice.label").value("불편 시간대"))
			.andExpect(jsonPath("$.data.reply").value("평소보다 안 좋았군요. 언제 제일 신경 쓰였어요?"));
		mockMvc.perform(choice(bearer, session, "C01", "자려고 누웠을 때"))
			.andExpect(jsonPath("$.data.askingField").value("itchScore"))
			.andExpect(jsonPath("$.data.choice").doesNotExist());
		mockMvc.perform(choice(bearer, session, "itchScore", 4))
			.andExpect(jsonPath("$.data.draft.dailyCheck.itchScore").value(4))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"));
		mockMvc.perform(choice(bearer, session, "painIndex", 6))
			.andExpect(jsonPath("$.data.draft.painIndex").value(6))
			.andExpect(jsonPath("$.data.askingField").value("bodyParts"))
			.andExpect(jsonPath("$.data.reply").value(BODY_PARTS_QUESTION));
		// 고른 부위에는 고통 지수가 그 부위의 점수로 들어가고, 부위 질문은 하나만 묻는다.
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of("FACE", "LEFT_HAND")))
			.andExpect(jsonPath("$.data.draft.skinConditions.length()").value(2))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(6))
			.andExpect(jsonPath("$.data.askingField").value("C04"));
		mockMvc.perform(choice(bearer, session, "C04", "없음"))
			.andExpect(jsonPath("$.data.askingField").value("skinShape"))
			.andExpect(jsonPath("$.data.choice.multiple").value(true))
			.andExpect(jsonPath("$.data.choice.noneLabel").value("괜찮음"));
		mockMvc.perform(choice(bearer, session, "skinShape", List.of("붉어짐", "하얗게 일어남")))
			.andExpect(jsonPath("$.data.askingField").value("C06"));
		// 보습 시점을 답하면 보습제는 미리 채워져 묻지 않는다. 연결 질문 3개를 다 물어 더 묻지 않는다.
		mockMvc.perform(choice(bearer, session, "C06", "바로"))
			.andExpect(jsonPath("$.data.draft.dailyCheck.moisturizer").value(true))
			.andExpect(jsonPath("$.data.askingField").value("swelling"));
		mockMvc.perform(choice(bearer, session, "swelling", "아니요"))
			.andExpect(jsonPath("$.data.askingField").value("scratch"));
		mockMvc.perform(choice(bearer, session, "scratch", "있음"))
			.andExpect(jsonPath("$.data.askingField").value("oozing"));
		mockMvc.perform(choice(bearer, session, "oozing", "없음"))
			.andExpect(jsonPath("$.data.askingField").value("sleepDisturbed"));
		mockMvc.perform(choice(bearer, session, "sleepDisturbed", "괜찮았음"))
			.andExpect(jsonPath("$.data.askingField").value("sleepHours"));
		mockMvc.perform(choice(bearer, session, "sleepHours", 7.5))
			.andExpect(jsonPath("$.data.draft.sleepHours").value(7.5))
			.andExpect(jsonPath("$.data.askingField").value("medicationUse"))
			.andExpect(jsonPath("$.data.reply").value("처방받은 연고나 약을 사용했나요?"));
		mockMvc.perform(choice(bearer, session, "medicationUse", "사용함"))
			.andExpect(jsonPath("$.data.draft.medication").value(true))
			.andExpect(jsonPath("$.data.askingField").value("notes"));
		String saved = mockMvc.perform(skip(bearer, session, "notes"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.askingField").doesNotExist())
			.andExpect(jsonPath("$.data.choice").doesNotExist())
			.andExpect(jsonPath("$.data.pendingFields").isEmpty())
			.andExpect(jsonPath("$.data.editing").value(true))
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.reply").value("오늘 기록을 저장했어요. 오늘의 총정리예요."
				+ "\n- 피부 상태: 평소보다 나빠짐\n- 가려움: 4점\n- 고통 지수: 6점\n- 얼굴: 고통 6점\n- 왼손: 고통 6점"
				+ "\n- 나타난 증상: 홍반, 긁은 자국(찰상), 건조증, 가려움\n- 수면 방해: 괜찮았음\n- 수면 시간: 7.5시간"
				+ "\n- 보습제: 발랐음\n- 연고·약: 사용함\n- 불편 시간대: 자려고 누웠을 때\n- 새 제품: 없음\n- 보습 시점: 바로"
				+ "\n\n오늘 하루도 정말 수고 많으셨어요! 바꿀 내용이 생기면 언제든 여기에 글로 적어 주세요. 바로 고쳐 둘게요."))
			.andReturn().getResponse().getContentAsString();

		// 마지막 항목에 답하면 저장 버튼 없이 그날 기록이 바로 저장된다. 하루 점검과 연결 질문 답도 함께 저장된다.
		long recordId = jsonMapper.readTree(saved).get("data").get("recordId").asLong();
		mockMvc.perform(get("/api/v1/records/" + recordId).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.painIndex").value(6))
			.andExpect(jsonPath("$.data.medication").value(true))
			.andExpect(jsonPath("$.data.dailyCheck.skinTrend").value("WORSE"))
			.andExpect(jsonPath("$.data.dailyCheck.itchScore").value(4))
			.andExpect(jsonPath("$.data.dailyCheck.sleepDisturbed").value(false))
			.andExpect(jsonPath("$.data.dailyCheck.moisturizer").value(true))
			.andExpect(jsonPath("$.data.dailyCheck.medicationUse").value("USED"))
			.andExpect(jsonPath("$.data.dailyCheck.linkedAnswers.C01").value("자려고 누웠을 때"))
			.andExpect(jsonPath("$.data.dailyCheck.linkedAnswers.C04").value("없음"))
			.andExpect(jsonPath("$.data.dailyCheck.linkedAnswers.C06").value("바로"));

		assertThat(llm.callCount()).isZero();
		// 대화 내역에는 고른 값이 사람이 읽는 말로 남는다.
		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages.length()").value(30))
			.andExpect(jsonPath("$.data.messages[0].content").value("평소보다 나빠짐"))
			.andExpect(jsonPath("$.data.messages[4].content").value("4점"))
			.andExpect(jsonPath("$.data.messages[8].content").value("얼굴, 왼손"))
			.andExpect(jsonPath("$.data.messages[12].content").value("붉어짐, 하얗게 일어남"))
			.andExpect(jsonPath("$.data.messages[24].content").value("7.5시간"))
			.andExpect(jsonPath("$.data.messages[28].content").value("없어요"));
		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.askingField").doesNotExist());
	}

	@Test
	void 선택_항목은_넘어갈_수_있고_피부_모양이_괜찮으면_빈_배열로_답한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(skip(bearer, session, "skinTrend"))
			.andExpect(jsonPath("$.data.askingField").value("itchScore"));
		mockMvc.perform(skip(bearer, session, "itchScore"))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"));
		mockMvc.perform(choice(bearer, session, "painIndex", 3));
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of("LEFT_THIGH")))
			.andExpect(jsonPath("$.data.askingField").value("skinShape"));
		mockMvc.perform(choice(bearer, session, "skinShape", List.of()))
			.andExpect(jsonPath("$.data.draft.skinSigns").doesNotExist())
			.andExpect(jsonPath("$.data.askingField").value("swelling"));
		mockMvc.perform(skip(bearer, session, "swelling"))
			.andExpect(jsonPath("$.data.askingField").value("scratch"));
		// 긁은 자국이 없으면 진물·피는 묻지 않는다.
		mockMvc.perform(choice(bearer, session, "scratch", "없음"))
			.andExpect(jsonPath("$.data.askingField").value("sleepDisturbed"));

		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages[0].content").value("넘어갈게요"))
			.andExpect(jsonPath("$.data.messages[8].content").value("괜찮음"))
			.andExpect(jsonPath("$.data.messages[12].content").value("없음"));
	}

	@Test
	void 직접_입력은_LLM을_부르지_않고_적은_글을_보여_주며_맞는지_확인한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		mockMvc.perform(choice(bearer, session, "skinTrend", "평소보다 나빠짐"));
		mockMvc.perform(skip(bearer, session, "C01"));
		mockMvc.perform(choice(bearer, session, "itchScore", 8))
			.andExpect(jsonPath("$.data.askingField").value("C02"));
		mockMvc.perform(choice(bearer, session, "C02", "밥 먹고"))
			.andExpect(jsonPath("$.data.askingField").value("C12"));

		mockMvc.perform(choice(bearer, session, "C12", "직접 입력"))
			.andExpect(jsonPath("$.data.askingField").value("C12_TEXT"))
			.andExpect(jsonPath("$.data.choice.text").value(true))
			.andExpect(jsonPath("$.data.reply").value("평소와 다르게 먹은 음식을 적어 주세요."));
		// 입력창에 적은 글은 LLM을 거치지 않고 그대로 답이 된다.
		mockMvc.perform(sendMessage(bearer, session, "매운 떡볶이"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.askingField").value("C12_CONFIRM"))
			.andExpect(jsonPath("$.data.choice.options[0]").value("맞아요"))
			.andExpect(jsonPath("$.data.reply").value("음식 메모: ‘매운 떡볶이’ 이렇게 기록할게요. 맞나요?"));
		mockMvc.perform(choice(bearer, session, "C12_CONFIRM", "맞아요"))
			.andExpect(jsonPath("$.data.draft.dailyCheck.linkedAnswers.C12").value("매운 떡볶이"))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"))
			.andExpect(jsonPath("$.data.reply").value(PAIN_QUESTION));

		assertThat(llm.callCount()).isZero();
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

		mockMvc.perform(choice(bearer, session, "skinTrend", "좋음")).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "skinTrend", 1)).andExpect(status().isBadRequest());
		mockMvc.perform(skip(bearer, session, "skinTrend"));
		mockMvc.perform(choice(bearer, session, "itchScore", 11)).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "itchScore", "6")).andExpect(status().isBadRequest());
		mockMvc.perform(withJson(post(url(session, "choices")).header(AUTH, bearer), Map.of("field", "itchScore")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(skip(bearer, session, "itchScore"));
		mockMvc.perform(choice(bearer, session, "painIndex", 11)).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "painIndex", 4));
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of("NOSE"))).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of())).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "bodyParts", List.of("LEFT_THIGH")));
		mockMvc.perform(choice(bearer, session, "skinShape", List.of("반짝임"))).andExpect(status().isBadRequest());
		mockMvc.perform(choice(bearer, session, "skinShape", "붉어짐")).andExpect(status().isBadRequest());

		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.draft.painIndex").value(4))
			.andExpect(jsonPath("$.data.draft.skinSigns").doesNotExist())
			.andExpect(jsonPath("$.data.askingField").value("skinShape"));
		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages.length()").value(8));
	}

	@Test
	void 고통_지수는_건너뛸_수_없고_특이사항은_선택지로_글을_보낼_수_없다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		mockMvc.perform(skip(bearer, session, "painIndex")).andExpect(status().isBadRequest());

		mockMvc.perform(skip(bearer, session, "skinTrend"));
		mockMvc.perform(skip(bearer, session, "itchScore"));
		mockMvc.perform(choice(bearer, session, "painIndex", 5));
		for (String field : List.of("bodyParts", "swelling", "scratch", "oozing", "sleepDisturbed", "sleepHours",
			"moisturizer", "medicationUse")) {
			mockMvc.perform(skip(bearer, session, field)).andExpect(status().isOk());
		}
		mockMvc.perform(get(url(session, "draft")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.askingField").value("notes"));
		mockMvc.perform(choice(bearer, session, "notes", "새 보습제를 썼어요")).andExpect(status().isBadRequest());
	}

	@Test
	void LLM이_다음_항목을_다른_문장으로_물어도_서버의_정해진_질문으로_바꾼다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"약은 드셨어요?\","
			+ " \"updates\": {\"skinTrend\": \"BETTER\", \"itchScore\": 3, \"painIndex\": 5}}");

		mockMvc.perform(sendMessage(bearer, session, "좀 나아졌어요. 가려움 3점, 고통 5점이요"))
			.andExpect(jsonPath("$.data.askingField").value("bodyParts"))
			.andExpect(jsonPath("$.data.reply").value(BODY_PARTS_QUESTION));
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
			.andExpect(jsonPath("$.data.askingField").value("skinTrend"))
			.andExpect(jsonPath("$.data.reply").value(TREND_QUESTION));
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
			.andExpect(jsonPath("$.data.askingField").value("skinTrend"))
			.andExpect(jsonPath("$.data.reply").value(TREND_QUESTION));

		mockMvc.perform(skip(bearer, session, "skinTrend"));
		mockMvc.perform(skip(bearer, session, "itchScore"));
		mockMvc.perform(choice(bearer, session, "painIndex", 2));
		mockMvc.perform(post(url(session, "finish")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.askingField").doesNotExist())
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.pendingFields").isEmpty());

		mockMvc.perform(get(url(session, "messages")).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages[8].content").value("기록을 마칠게요"));
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
