package com.atopy.Atopass.domain.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.domain.chatbot.service.ChatMessageCleaner;
import com.atopy.Atopass.domain.chatbot.service.ChatSessionService;
import com.atopy.Atopass.support.ApiTestSupport;
import com.atopy.Atopass.support.FakeChatModelClient;
import com.atopy.Atopass.support.FakeLlmConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;

@Import(FakeLlmConfig.class)
class ChatbotIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;

	@Autowired
	FakeChatModelClient llm;
	@Autowired
	ChatSessionService sessionService;
	@Autowired
	ChatMessageCleaner cleaner;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	// ── 세션 ──

	@Test
	void 세션을_만들면_201과_세션_ID를_준다() throws Exception {
		String bearer = newMemberBearer();
		grantConsent(bearer);

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer), Map.of("mode", "RECORD")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.sessionId").isNotEmpty())
			.andExpect(jsonPath("$.data.mode").value("RECORD"));
	}

	@Test
	void 같은_날_열려_있는_같은_모드의_세션은_다시_쓰고_모드가_다르면_새로_만든다() throws Exception {
		String bearer = newMemberBearer();

		String first = createSession(bearer, "RECORD");
		String again = createSession(bearer, "RECORD");
		String query = createSession(bearer, "QUERY");

		assertThat(again).isEqualTo(first);
		assertThat(query).isNotEqualTo(first);
		assertThat(createSession(newMemberBearer(), "RECORD")).isNotEqualTo(first);
	}

	@Test
	void 모드가_없거나_잘못되면_400이다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer), Map.of()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer), Map.of("mode", "CHAT")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/chatbot/sessions/abc/messages")
				.contentType("application/json").content("{\"message\":\"안녕\"}"))
			.andExpect(status().isUnauthorized());
	}

	// ── 기록 모드 대화 ──

	@Test
	void 문장에서_뽑은_값을_초안에_반영하고_빠진_것을_되묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "어제 몇 시간 주무셨나요?",
			 "updates": {"painIndex": 6, "conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 4}}]}, "finished": false}""");

		mockMvc.perform(sendMessage(bearer, session, "고통은 6점이고 팔 가려움이 4점이에요"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reply").value("어젯밤 몇 시간 주무셨나요?"))
			.andExpect(jsonPath("$.data.askingField").value("sleepHours"))
			.andExpect(jsonPath("$.data.understood").value(true))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("sleepHours"))
			.andExpect(jsonPath("$.data.draft.painIndex").value(6))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(4));
	}

	@Test
	void LLM이_질문을_주지_않으면_서버가_빠진_항목을_대신_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"updates\": {}}");

		mockMvc.perform(sendMessage(bearer, session, "안녕하세요"))
			.andExpect(jsonPath("$.data.reply").value("오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)"))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("painIndex"));
	}

	@Test
	void 필수와_선택_항목이_모두_끝나면_확인_단계로_넘어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"painIndex": 4}}""");
		llm.enqueue("""
			{"understood": true, "reply": "다른 건요?",
			 "updates": {"sleepHours": 6, "medication": false, "skinSigns": [], "skipped": ["bodyParts", "notes"]}}""");

		mockMvc.perform(sendMessage(bearer, session, "고통은 4점"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false));
		mockMvc.perform(sendMessage(bearer, session, "6시간 잤고 약은 안 먹었고 증상도 특이사항도 없어요"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.pendingFields").isEmpty())
			.andExpect(jsonPath("$.data.reply").value(org.hamcrest.Matchers.allOf(
				org.hamcrest.Matchers.startsWith("오늘 기록을 저장했어요. 오늘의 총정리예요.\n- 고통 지수: 4점"),
				org.hamcrest.Matchers.endsWith("오늘 하루도 정말 수고 많으셨어요! 바꿀 내용이 생기면 언제든 여기에 글로 적어 주세요. 바로 고쳐 둘게요."))))
			.andExpect(jsonPath("$.data.draft.sleepHours").value(6.0));
	}

	@Test
	void 사용자가_마치겠다고_하면_선택_항목이_남아_있어도_확인_단계로_넘어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?", "finished": true,
			 "updates": {"painIndex": 3}}""");

		mockMvc.perform(sendMessage(bearer, session, "고통은 3점이고 그만할게요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.pendingFields").isEmpty());
	}

	@Test
	void 필수_항목이_없으면_마치겠다고_해도_확인_단계로_넘어가지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"고통은 몇 점인가요?\", \"finished\": true, \"updates\": {}}");

		mockMvc.perform(sendMessage(bearer, session, "그만할래요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false))
			.andExpect(jsonPath("$.data.reply").value("오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)"));
	}

	@Test
	void 이전_대화와_현재_초안을_LLM에_함께_보낸다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 4}}]}}""");
		mockMvc.perform(sendMessage(bearer, session, "팔이 4점"));

		llm.enqueue("{\"understood\": true, \"reply\": \"생활은요?\", \"updates\": {\"sleepHours\": 6}}");
		mockMvc.perform(sendMessage(bearer, session, "6시간 잤어요"));

		// 두 번째 호출: 사용자 → 챗봇 → 사용자 순서의 대화가 전달되고, 프롬프트에 초안이 들어 있다.
		assertThat(llm.lastMessages()).extracting(t -> t.role().name()).containsExactly("USER", "ASSISTANT", "USER");
		assertThat(llm.lastMessages().get(0).content()).isEqualTo("팔이 4점");
		assertThat(llm.lastMessages().get(2).content()).isEqualTo("6시간 잤어요");
		assertThat(llm.lastSystemPrompt()).contains("\"ARM\":{\"pain\":4}");
		assertThat(llm.lastSystemPrompt()).doesNotContain("@example.com");
	}

	@Test
	void 부위를_키로_쓴_증상_점수도_같은_뜻으로_반영한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "다른 증상도 있으신가요?",
			 "updates": {"conditions": {"FACE": {"pain": 8}}}}""");

		mockMvc.perform(sendMessage(bearer, session, "8"))
			.andExpect(jsonPath("$.data.understood").value(true))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].bodyPart").value("FACE"))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(8));
	}

	@Test
	void 프롬프트가_부위별_점수의_출력_형식을_안내한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"고통은 몇 점인가요?\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, session, "안녕하세요"));

		assertThat(llm.lastSystemPrompt())
			.contains("\"conditions\": [{\"bodyPart\": \"FACE\", \"pain\": 8, \"skinSigns\": [\"ITCHING\", \"ERYTHEMA\"]}]");
	}

	@Test
	void 프롬프트에_새_증상_목록과_쉬운_말_설명이_들어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		mockMvc.perform(sendMessage(bearer, session, "진물이 나요"));

		assertThat(llm.lastSystemPrompt())
			.contains("- OOZING 진물/딱지 (상처에서 진물이 나오거나 딱지가 앉음 / 진물이 나요")
			.contains("- EDEMA 부종/구진(결절)")
			.contains("- ITCHING 가려움")
			.contains("일상적인 말로 증상을 이야기합니다")
			.doesNotContain("PAPULE")
			.doesNotContain("SCALING");
	}

	@Test
	void 프롬프트가_부위_고통_점수를_일정한_문구로_하나씩_묻도록_안내한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		llm.enqueue("{\"understood\": true, \"reply\": \"가려움 정도를 점수로 나타내면 0~10점 중 몇 점인가요?\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, session, "팔이 가려워요"));

		assertThat(llm.lastSystemPrompt())
			.contains("{부위}의 고통 정도를 점수로 나타내면 0~10점 중 몇 점인가요?")
			.contains("한 번에 하나씩 차례로 묻습니다")
			.contains("followUp을 true로");
	}

	@Test
	void 점수가_범위를_벗어나면_초안에_반영하지_않고_다시_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "알겠어요",
			 "updates": {"sleepHours": 6, "conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 11}}]}}""");

		mockMvc.perform(sendMessage(bearer, session, "가려움 11점"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.understood").value(false))
			.andExpect(jsonPath("$.data.reply").value("점수는 0~10 사이 숫자로 알려 주세요. (0은 없음, 10은 매우 심함)"))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("painIndex"))
			.andExpect(jsonPath("$.data.draft.sleepHours").doesNotExist());
	}

	@Test
	void 고통_지수가_범위를_벗어나면_반영하지_않고_다시_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"알겠어요\", \"updates\": {\"painIndex\": 11}}");

		mockMvc.perform(sendMessage(bearer, session, "고통 11점"))
			.andExpect(jsonPath("$.data.understood").value(false))
			.andExpect(jsonPath("$.data.reply").value("고통 지수는 0~10 사이 숫자로 알려 주세요. (0은 없음, 10은 매우 심함)"))
			.andExpect(jsonPath("$.data.draft.painIndex").doesNotExist());
	}

	@Test
	void 질문은_고통_지수_부위_수면_약_복용_증상_특이사항_순서로_이어진다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		llm.enqueue("{\"understood\": true, \"updates\": {\"painIndex\": 5}}");
		mockMvc.perform(sendMessage(bearer, session, "5점"))
			.andExpect(jsonPath("$.data.reply").value("어느 부위가 그렇게 불편했나요? 해당하는 부위를 모두 골라 주세요."));
		llm.enqueue("{\"understood\": true, \"updates\": {\"conditions\": [{\"bodyPart\": \"FACE\", \"pain\": 5}]}}");
		mockMvc.perform(sendMessage(bearer, session, "얼굴이요"))
			.andExpect(jsonPath("$.data.reply").value("어젯밤 몇 시간 주무셨나요?"));
		llm.enqueue("{\"understood\": true, \"updates\": {\"sleepHours\": 8}}");
		mockMvc.perform(sendMessage(bearer, session, "8시간"))
			.andExpect(jsonPath("$.data.draft.sleepScore").value(5))
			.andExpect(jsonPath("$.data.reply").value("오늘 약을 복용하셨나요?"));
		llm.enqueue("{\"understood\": true, \"updates\": {\"medication\": true}}");
		mockMvc.perform(sendMessage(bearer, session, "예"))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("skinSigns"));
		llm.enqueue("{\"understood\": true, \"updates\": {\"skinSigns\": [\"ERYTHEMA\", \"EDEMA\"]}}");
		mockMvc.perform(sendMessage(bearer, session, "2, 4"))
			.andExpect(jsonPath("$.data.reply").value("평소와 다른 점이 있으면 500자 이내로 적어 주세요. 없으면 '없어요'를 눌러 주세요."));
		llm.enqueue("{\"understood\": true, \"updates\": {\"notes\": \"새 보습제를 썼어요\"}}");
		mockMvc.perform(sendMessage(bearer, session, "새 보습제를 썼어요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.draft.skinSigns.length()").value(2))
			.andExpect(jsonPath("$.data.draft.notes").value("새 보습제를 썼어요"));
	}

	@Test
	void 해석하지_못하면_오류가_아니라_다시_묻고_세_번_이어지면_입력_예시를_안내한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		for (int i = 1; i <= 2; i++) {
			llm.enqueue("JSON이 아닌 일반 문장입니다.");
			mockMvc.perform(sendMessage(bearer, session, "음... 글쎄요"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.understood").value(false))
				.andExpect(jsonPath("$.data.reply").value("죄송해요, 잘 이해하지 못했어요. 다시 한 번 말씀해 주시겠어요?"));
		}
		llm.enqueue("{\"understood\": false, \"reply\": \"어떤 증상인지 알려 주세요.\"}");
		mockMvc.perform(sendMessage(bearer, session, "그냥 그래요"))
			.andExpect(jsonPath("$.data.understood").value(false))
			.andExpect(jsonPath("$.data.reply").value(
				"어떤 증상인지 알려 주세요. 예: \"고통은 6점이고 잠은 6시간 잤어요.\""));

		// 한 번 해석에 성공하면 다시 처음부터 센다.
		llm.enqueue("{\"understood\": true, \"reply\": \"수면은요?\", \"updates\": {\"conditions\": [{\"bodyPart\": \"ARM\", \"symptoms\": {\"pain\": 3}}]}}");
		mockMvc.perform(sendMessage(bearer, session, "팔 3점"))
			.andExpect(jsonPath("$.data.understood").value(true));
		llm.enqueue("엉뚱한 응답");
		mockMvc.perform(sendMessage(bearer, session, "음"))
			.andExpect(jsonPath("$.data.reply").value("죄송해요, 잘 이해하지 못했어요. 다시 한 번 말씀해 주시겠어요?"));
	}

	@Test
	void 알_수_없는_부위_이름이_오면_반영하지_않고_다시_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "네",
			 "updates": {"conditions": [{"bodyPart": "TAIL", "symptoms": {"pain": 4}}]}}""");

		mockMvc.perform(sendMessage(bearer, session, "꼬리가 가려워요"))
			.andExpect(jsonPath("$.data.understood").value(false))
			.andExpect(jsonPath("$.data.draft.skinConditions").isEmpty());
	}

	@Test
	void LLM_장애면_503을_주고_사용자_입력은_저장하지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueueFailure();

		mockMvc.perform(sendMessage(bearer, session, "팔이 가려워요"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error.code").value("CHATBOT_UNAVAILABLE"));

		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.messages").isEmpty());

		// 같은 요청을 다시 보내면 정상 처리된다.
		llm.enqueue("{\"understood\": true, \"reply\": \"몇 점인가요?\", \"updates\": {\"conditions\": [{\"bodyPart\": \"ARM\"}]}}");
		mockMvc.perform(sendMessage(bearer, session, "팔이 가려워요"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pendingFields[0]").value("painIndex"))
			.andExpect(jsonPath("$.data.pendingFields[1]").value("symptoms"));
	}

	@Test
	void 메시지가_비었거나_너무_길면_400이고_LLM을_호출하지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(sendMessage(bearer, session, "   "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(sendMessage(bearer, session, "가".repeat(501)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		assertThat(llm.callCount()).isZero();
	}

	@Test
	void 대화_내역을_시간순으로_조회한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"고통은 몇 점인가요?\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, session, "기록할게요"));
		llm.enqueue("{\"understood\": true, \"reply\": \"몇 점인가요?\", \"updates\": {\"conditions\": [{\"bodyPart\": \"ARM\"}]}}");
		mockMvc.perform(sendMessage(bearer, session, "팔이요"));

		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.messages.length()").value(4))
			.andExpect(jsonPath("$.data.messages[0].role").value("USER"))
			.andExpect(jsonPath("$.data.messages[0].content").value("기록할게요"))
			.andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
			.andExpect(jsonPath("$.data.messages[1].content").value("오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)"))
			.andExpect(jsonPath("$.data.messages[3].content").value("몇 점인가요?"))
			.andExpect(jsonPath("$.data.messages[0].createdAt").isNotEmpty());
	}

	// ── 기록 저장 ──

	@Test
	void 확인한_초안을_기록으로_저장하면_세션이_닫힌다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		String response = mockMvc.perform(withJson(
				post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-10", true, Map.of("pain", 4), 6)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.recordDate").value("2026-10-10"))
			.andReturn().getResponse().getContentAsString();
		long recordId = jsonMapper.readTree(response).get("data").get("recordId").asLong();

		// 일반 기록 API로도 조회된다.
		mockMvc.perform(get("/api/v1/records/" + recordId).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.pain").value(4))
			.andExpect(jsonPath("$.data.sleepHours").value(6.0));

		// 닫힌 세션: 메시지·재저장은 409, 대화 조회는 가능하다.
		mockMvc.perform(sendMessage(bearer, session, "또 말할래요"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("SESSION_CLOSED"));
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-11", true, Map.of("pain", 4), 6)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("SESSION_CLOSED"));
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(status().isOk());

		// 닫힌 뒤에는 새 세션이 만들어진다.
		assertThat(createSession(bearer, "RECORD")).isNotEqualTo(session);
	}

	@Test
	void 확인하지_않은_초안은_저장하지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-12", false, Map.of("pain", 4), 6)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DRAFT_NOT_CONFIRMED"));

		// 저장되지 않았으므로 같은 날짜에 다시 확인해서 저장할 수 있다.
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-12", true, Map.of("pain", 4), 6)))
			.andExpect(status().isCreated());
	}

	@Test
	void 같은_날짜의_기록이_이미_있으면_409이고_세션은_닫히지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-13", true, Map.of("pain", 4), 6)))
			.andExpect(status().isCreated());
		String second = createSession(bearer, "RECORD");

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + second + "/records").header(AUTH, bearer),
				saveBody("2026-10-13", true, Map.of("pain", 2), 5)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RECORD_ALREADY_EXISTS"));

		// 실패했으므로 세션은 그대로 열려 있다.
		llm.enqueue("{\"understood\": true, \"reply\": \"네\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, second, "다른 날짜로 할게요"))
			.andExpect(status().isOk());
	}

	// ── 저장된 기록 고치기 ──

	@Test
	void 그날_기록이_있으면_저장된_내용을_불러와_수정할_내용이_있는지_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
		String first = createSession(bearer, "RECORD");
		saveRecord(bearer, first, saveBody(today, true, Map.of("pain", 4), 6)).andExpect(status().isCreated());

		String second = createSession(bearer, "RECORD");

		// 하루에 대화 하나를 이어 간다: 저장한 대화를 다시 열어 고치는 대화로 쓴다.
		assertThat(second).isEqualTo(first);
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + second + "/messages").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages.length()").value(1))
			.andExpect(jsonPath("$.data.messages[0].role").value("ASSISTANT"))
			.andExpect(jsonPath("$.data.messages[0].content").value(org.hamcrest.Matchers.allOf(
				org.hamcrest.Matchers.containsString("오늘 기록이 이미 저장되어 있어요."),
				org.hamcrest.Matchers.containsString("- 팔: 고통 4점"),
				org.hamcrest.Matchers.containsString("- 수면 시간: 6시간"),
				org.hamcrest.Matchers.containsString("수정할 내용이 있나요?"))));
		// 저장된 값이 초안으로 들어오고, 빈 항목을 다시 묻지 않는다.
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + second + "/draft").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(4))
			.andExpect(jsonPath("$.data.draft.sleepHours").value(6.0))
			.andExpect(jsonPath("$.data.askingField").value("painIndex"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false));
		// 나갔다 들어와도 같은 수정 세션을 이어서 쓴다.
		assertThat(createSession(bearer, "RECORD")).isEqualTo(second);
	}

	@Test
	void 수정_세션에서_바꾼_내용을_저장하면_기존_기록을_수정한다() throws Exception {
		String bearer = newMemberBearer();
		String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
		String first = createSession(bearer, "RECORD");
		Map<String, Object> body = saveBody(today, true, Map.of("pain", 4), 6);
		((Map<String, Object>) body.get("record")).put("painIndex", 5);
		String created = saveRecord(bearer, first, body).andReturn().getResponse().getContentAsString();
		long recordId = jsonMapper.readTree(created).get("data").get("recordId").asLong();

		String session = createSession(bearer, "RECORD");
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/draft").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.askingField").doesNotExist())
			.andExpect(jsonPath("$.data.pendingFields").isEmpty());
		llm.enqueue("{\"understood\": true, \"reply\": \"수면 시간을 8시간으로 바꿨어요.\", "
			+ "\"updates\": {\"sleepHours\": 8}, \"finished\": false, \"followUp\": false}");
		mockMvc.perform(sendMessage(bearer, session, "잠은 8시간 잤어요"))
			.andExpect(jsonPath("$.data.draft.sleepHours").value(8.0))
			.andExpect(jsonPath("$.data.draft.painIndex").value(5))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.editing").value(true))
			.andExpect(jsonPath("$.data.recordId").value(recordId))
			.andExpect(jsonPath("$.data.reply").value(
				"수면 시간을 8시간으로 바꿨어요.\n바로 반영해서 저장했어요. 지금 기록은 아래와 같아요.\n- 고통 지수: 5점\n- 팔: 고통 4점\n- 수면 시간: 8시간\n- 나타난 증상: 없음"));
		assertThat(llm.lastSystemPrompt()).contains("[기록 수정]");
		// 글로 고치면 저장 버튼 없이 바로 기존 기록이 수정된다.
		mockMvc.perform(get("/api/v1/records/" + recordId).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.sleepHours").value(8.0))
			.andExpect(jsonPath("$.data.painIndex").value(5));

		// 바꿀 게 없다고 하면 다시 저장하지 않고 답만 한다.
		llm.enqueue("{\"understood\": true, \"reply\": \"알겠어요. 오늘 하루도 수고 많으셨어요!\", \"updates\": {}, \"finished\": true}");
		mockMvc.perform(sendMessage(bearer, session, "없어요"))
			.andExpect(jsonPath("$.data.reply").value("알겠어요. 오늘 하루도 수고 많으셨어요!"));

		// 나갔다 들어와도 같은 대화가 이어지고, 내용이 남아 있다.
		assertThat(createSession(bearer, "RECORD")).isEqualTo(session);
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages.length()").value(5));
	}

	@Test
	void 수정_세션에서는_증상과_부위를_글로_지울_수_있다() throws Exception {
		String bearer = newMemberBearer();
		String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
		Map<String, Object> body = saveBody(today, true, Map.of("pain", 4), 6);
		Map<String, Object> record = (Map<String, Object>) body.get("record");
		record.put("painIndex", 5);
		record.put("skinSigns", List.of("ITCHING", "XEROSIS"));
		saveRecord(bearer, createSession(bearer, "RECORD"), body).andExpect(status().isCreated());

		String session = createSession(bearer, "RECORD");
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/draft").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.editing").value(true));
		llm.enqueue("{\"understood\": true, \"reply\": \"가려움과 팔을 뺐어요.\", "
			+ "\"updates\": {\"removeSkinSigns\": [\"ITCHING\"], \"removeBodyParts\": [\"ARM\"]}}");
		mockMvc.perform(sendMessage(bearer, session, "가려움은 없어졌고 팔은 빼 주세요"))
			.andExpect(jsonPath("$.data.draft.skinSigns.length()").value(1))
			.andExpect(jsonPath("$.data.draft.skinSigns[0]").value("XEROSIS"))
			.andExpect(jsonPath("$.data.draft.skinConditions").isEmpty())
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true));
	}

	@Test
	void 총정리에서_기록을_만들거나_고치면_대화로_불러오고_지우면_새로_시작한다() throws Exception {
		String bearer = newMemberBearer();
		String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
		String chatting = createSession(bearer, "RECORD");

		// 챗봇 대화 중에 총정리에서 직접 기록을 만들면, 다시 들어올 때 그 기록을 고치는 세션이 열린다.
		Map<String, Object> direct = new LinkedHashMap<>((Map<String, Object>) saveBody(today, true, Map.of("pain", 3), 7).get("record"));
		direct.put("recordDate", today);
		String created = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), direct))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long recordId = jsonMapper.readTree(created).get("data").get("recordId").asLong();
		String editing = createSession(bearer, "RECORD");
		assertThat(editing).isEqualTo(chatting);
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + editing + "/draft").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.recordId").value(recordId))
			.andExpect(jsonPath("$.data.draft.painIndex").doesNotExist())
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.pain").value(3));

		// 총정리에서 기록을 고치면 다시 들어올 때 최신 내용을 불러오고 알려 준다.
		direct.put("sleepHours", 9);
		mockMvc.perform(withJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.put("/api/v1/records/" + recordId).header(AUTH, bearer), direct))
			.andExpect(status().isOk());
		assertThat(createSession(bearer, "RECORD")).isEqualTo(editing);
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + editing + "/draft").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.draft.sleepHours").value(9.0));
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + editing + "/messages").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages[-1].content").value(
				org.hamcrest.Matchers.startsWith("다른 화면에서 바뀐 기록을 불러왔어요.")));

		// 기록을 지우면 빈 세션으로 새로 시작한다.
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/v1/records/" + recordId).header(AUTH, bearer))
			.andExpect(status().is2xxSuccessful());
		String fresh = createSession(bearer, "RECORD");
		assertThat(fresh).isNotEqualTo(editing);
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + fresh + "/messages").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages").isEmpty());
	}

	@Test
	void 저장할_내용이_규칙에_어긋나면_일반_기록_생성과_같은_오류를_준다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-14", true, Map.of("pain", 11), 6)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_SCORE_RANGE"));
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				Map.of("recordDate", "2026-10-14", "confirmed", true)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	// ── 질의 모드 ──

	@Test
	void 질의_모드는_본인의_기록만_근거로_LLM에_보내고_답을_그대로_돌려준다() throws Exception {
		String bearer = newMemberBearer();
		LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
		createRecord(bearer, yesterday, 4, "밤에 많이 긁음");
		createRecord(newMemberBearer(), yesterday, 9, "다른 사람의 비밀 메모");
		String session = createSession(bearer, "QUERY");
		llm.enqueue("어제는 팔 가려움이 4점으로 기록되어 있어요.");

		mockMvc.perform(sendMessage(bearer, session, "어제 가려움 어땠어?"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reply").value("어제는 팔 가려움이 4점으로 기록되어 있어요."))
			.andExpect(jsonPath("$.data.understood").value(true))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false))
			.andExpect(jsonPath("$.data.pendingFields").isEmpty())
			.andExpect(jsonPath("$.data.draft").doesNotExist());

		assertThat(llm.lastSystemPrompt())
			.contains(yesterday + ": 팔(고통 4)")
			.contains("메모: 밤에 많이 긁음")
			.doesNotContain("다른 사람의 비밀 메모")
			.doesNotContain("@example.com");
	}

	@Test
	void 질의_모드에서_기록이_없으면_없다고_알려_준다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "QUERY");
		llm.enqueue("기록이 없어요.");

		mockMvc.perform(sendMessage(bearer, session, "지난주는 어땠어?")).andExpect(status().isOk());

		assertThat(llm.lastSystemPrompt()).contains("(기록이 없습니다.)");
	}

	@Test
	void 질의_모드_세션에는_기록을_저장할_수_없다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "QUERY");

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-15", true, Map.of("pain", 4), 6)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	// ── 권한 · 보존 기간 ──

	@Test
	void 다른_회원의_세션은_모든_API에서_404이다() throws Exception {
		String owner = newMemberBearer();
		String session = createSession(owner, "RECORD");
		String other = newMemberBearer();
		grantConsent(other); // 동의한 회원이어도 남의 세션은 404여야 한다.

		mockMvc.perform(sendMessage(other, session, "안녕"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, other))
			.andExpect(status().isNotFound());
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, other),
				saveBody("2026-10-16", true, Map.of("pain", 4), 6)))
			.andExpect(status().isNotFound());
		assertThat(llm.callCount()).isZero();
	}

	@Test
	void 없는_세션은_404이다() throws Exception {
		mockMvc.perform(get("/api/v1/chatbot/sessions/no-such-session/messages").header(AUTH, newMemberBearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
	}

	@Test
	void 보존_기간이_지난_메시지만_삭제한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"고통은 몇 점인가요?\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, session, "기록할게요"));

		// 방금 만든 메시지는 보존 기간 안이라 정기 삭제로 지워지지 않는다.
		cleaner.deleteExpiredMessages();
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages.length()").value(2));

		// 기준 시각을 지난 메시지는 삭제된다.
		int deleted = sessionService.deleteMessagesOlderThan(LocalDateTime.now().plusDays(1));
		assertThat(deleted).isGreaterThanOrEqualTo(2);
		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.messages").isEmpty());
	}

	// ── 헬퍼 ──

	/** 챗봇은 이용 동의가 있어야 쓸 수 있으므로, 테스트에서는 먼저 동의(멱등)하고 세션을 만든다. */
	private String createSession(String bearer, String mode) throws Exception {
		grantConsent(bearer);
		String body = mockMvc.perform(withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer),
				Map.of("mode", mode)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("data").get("sessionId").asString();
	}

	private void grantConsent(String bearer) throws Exception {
		mockMvc.perform(post("/api/v1/chatbot/consent").header(AUTH, bearer)).andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sendMessage(
		String bearer, String session, String message) {
		return withJson(post("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer),
			Map.of("message", message));
	}

	private org.springframework.test.web.servlet.ResultActions saveRecord(
		String bearer, String session, Map<String, Object> body) throws Exception {
		return mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer), body));
	}

	private Map<String, Object> saveBody(String date, boolean confirmed, Map<String, Object> symptoms, int sleepHours) {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", symptoms);
		Map<String, Object> record = new LinkedHashMap<>();
		record.put("skinConditions", List.of(condition));
		record.put("sleepHours", sleepHours);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date);
		body.put("confirmed", confirmed);
		body.put("record", record);
		return body;
	}

	private void createRecord(String bearer, LocalDate date, int itching, String notes) throws Exception {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", Map.of("pain", itching));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("skinConditions", List.of(condition));
		body.put("notes", notes);
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

}
