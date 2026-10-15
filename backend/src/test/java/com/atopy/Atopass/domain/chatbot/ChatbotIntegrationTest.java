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
			 "updates": {"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}, "finished": false}""");

		mockMvc.perform(sendMessage(bearer, session, "팔이 가려워요. 4점이에요"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reply").value("어제 몇 시간 주무셨나요?"))
			.andExpect(jsonPath("$.data.understood").value(true))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("sleepHours"))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.draft.skinConditions[0].symptoms.itching").value(4));
	}

	@Test
	void LLM이_질문을_주지_않으면_서버가_빠진_항목을_대신_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"updates\": {}}");

		mockMvc.perform(sendMessage(bearer, session, "안녕하세요"))
			.andExpect(jsonPath("$.data.reply").value("어느 부위가 불편하신가요?"))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("bodyPart"));
	}

	@Test
	void 필수와_선택_항목이_모두_끝나면_확인_단계로_넘어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}}""");
		llm.enqueue("""
			{"understood": true, "reply": "다른 건요?",
			 "updates": {"sleepHours": 6, "skipped": ["lifestyle", "notes"]}}""");

		mockMvc.perform(sendMessage(bearer, session, "팔이 4점"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false));
		mockMvc.perform(sendMessage(bearer, session, "6시간 잤고 나머진 없어요"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.pendingFields").isEmpty())
			.andExpect(jsonPath("$.data.reply").value("입력하신 내용을 정리했어요. 아래 내용을 확인하고 저장해 주세요."))
			.andExpect(jsonPath("$.data.draft.sleepHours").value(6.0));
	}

	@Test
	void 사용자가_마치겠다고_하면_선택_항목이_남아_있어도_확인_단계로_넘어간다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?", "finished": true,
			 "updates": {"conditions": [{"bodyPart": "FACE", "symptoms": {"dryness": 3}}]}}""");

		mockMvc.perform(sendMessage(bearer, session, "얼굴 건조 3점이고 그만할게요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(true))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("sleepHours"));
	}

	@Test
	void 필수_항목이_없으면_마치겠다고_해도_확인_단계로_넘어가지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("{\"understood\": true, \"reply\": \"어느 부위인가요?\", \"finished\": true, \"updates\": {}}");

		mockMvc.perform(sendMessage(bearer, session, "그만할래요"))
			.andExpect(jsonPath("$.data.requiresConfirmation").value(false))
			.andExpect(jsonPath("$.data.reply").value("어느 부위인가요?"));
	}

	@Test
	void 이전_대화와_현재_초안을_LLM에_함께_보낸다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "수면은요?",
			 "updates": {"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}}""");
		mockMvc.perform(sendMessage(bearer, session, "팔이 4점"));

		llm.enqueue("{\"understood\": true, \"reply\": \"생활은요?\", \"updates\": {\"sleepHours\": 6}}");
		mockMvc.perform(sendMessage(bearer, session, "6시간 잤어요"));

		// 두 번째 호출: 사용자 → 챗봇 → 사용자 순서의 대화가 전달되고, 프롬프트에 초안이 들어 있다.
		assertThat(llm.lastMessages()).extracting(t -> t.role().name()).containsExactly("USER", "ASSISTANT", "USER");
		assertThat(llm.lastMessages().get(0).content()).isEqualTo("팔이 4점");
		assertThat(llm.lastMessages().get(2).content()).isEqualTo("6시간 잤어요");
		assertThat(llm.lastSystemPrompt()).contains("\"ARM\":{\"itching\":4}");
		assertThat(llm.lastSystemPrompt()).doesNotContain("@example.com");
	}

	@Test
	void 프롬프트가_증상_점수를_일정한_문구로_하나씩_묻도록_안내한다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		llm.enqueue("{\"understood\": true, \"reply\": \"가려움 정도를 점수로 나타내면 0~10점 중 몇 점인가요?\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, session, "팔이 가려워요"));

		assertThat(llm.lastSystemPrompt())
			.contains("{증상} 정도를 점수로 나타내면 0~10점 중 몇 점인가요?")
			.contains("한 번에 하나씩 차례로 묻습니다")
			.contains("다른 증상도 있으신가요?");
	}

	@Test
	void 점수가_범위를_벗어나면_초안에_반영하지_않고_다시_묻는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		llm.enqueue("""
			{"understood": true, "reply": "알겠어요",
			 "updates": {"sleepHours": 6, "conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 11}}]}}""");

		mockMvc.perform(sendMessage(bearer, session, "가려움 11점"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.understood").value(false))
			.andExpect(jsonPath("$.data.reply").value("점수는 0~10 사이 숫자로 알려 주세요. (0은 없음, 10은 매우 심함)"))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("bodyPart"))
			.andExpect(jsonPath("$.data.draft.sleepHours").doesNotExist());
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
				"어떤 증상인지 알려 주세요. 예: \"팔이 가려워요. 가려움은 4점이고 잠은 6시간 잤어요.\""));

		// 한 번 해석에 성공하면 다시 처음부터 센다.
		llm.enqueue("{\"understood\": true, \"reply\": \"수면은요?\", \"updates\": {\"conditions\": [{\"bodyPart\": \"ARM\", \"symptoms\": {\"itching\": 3}}]}}");
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
			 "updates": {"conditions": [{"bodyPart": "TAIL", "symptoms": {"itching": 4}}]}}""");

		mockMvc.perform(sendMessage(bearer, session, "꼬리가 가려워요"))
			.andExpect(jsonPath("$.data.understood").value(false))
			.andExpect(jsonPath("$.data.pendingFields[0]").value("bodyPart"));
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
			.andExpect(jsonPath("$.data.pendingFields[0]").value("symptoms"));
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
		llm.enqueue("{\"understood\": true, \"reply\": \"어느 부위인가요?\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, session, "기록할게요"));
		llm.enqueue("{\"understood\": true, \"reply\": \"몇 점인가요?\", \"updates\": {\"conditions\": [{\"bodyPart\": \"ARM\"}]}}");
		mockMvc.perform(sendMessage(bearer, session, "팔이요"));

		mockMvc.perform(get("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.messages.length()").value(4))
			.andExpect(jsonPath("$.data.messages[0].role").value("USER"))
			.andExpect(jsonPath("$.data.messages[0].content").value("기록할게요"))
			.andExpect(jsonPath("$.data.messages[1].role").value("ASSISTANT"))
			.andExpect(jsonPath("$.data.messages[1].content").value("어느 부위인가요?"))
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
				saveBody("2026-10-10", true, Map.of("itching", 4), 6)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.recordDate").value("2026-10-10"))
			.andReturn().getResponse().getContentAsString();
		long recordId = jsonMapper.readTree(response).get("data").get("recordId").asLong();

		// 일반 기록 API로도 조회된다.
		mockMvc.perform(get("/api/v1/records/" + recordId).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.itching").value(4))
			.andExpect(jsonPath("$.data.sleepHours").value(6.0));

		// 닫힌 세션: 메시지·재저장은 409, 대화 조회는 가능하다.
		mockMvc.perform(sendMessage(bearer, session, "또 말할래요"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("SESSION_CLOSED"));
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-11", true, Map.of("itching", 4), 6)))
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
				saveBody("2026-10-12", false, Map.of("itching", 4), 6)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("DRAFT_NOT_CONFIRMED"));

		// 저장되지 않았으므로 같은 날짜에 다시 확인해서 저장할 수 있다.
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-12", true, Map.of("itching", 4), 6)))
			.andExpect(status().isCreated());
	}

	@Test
	void 같은_날짜의_기록이_이미_있으면_409이고_세션은_닫히지_않는다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-13", true, Map.of("itching", 4), 6)))
			.andExpect(status().isCreated());
		String second = createSession(bearer, "RECORD");

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + second + "/records").header(AUTH, bearer),
				saveBody("2026-10-13", true, Map.of("itching", 2), 5)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RECORD_ALREADY_EXISTS"));

		// 실패했으므로 세션은 그대로 열려 있다.
		llm.enqueue("{\"understood\": true, \"reply\": \"네\", \"updates\": {}}");
		mockMvc.perform(sendMessage(bearer, second, "다른 날짜로 할게요"))
			.andExpect(status().isOk());
	}

	@Test
	void 저장할_내용이_규칙에_어긋나면_일반_기록_생성과_같은_오류를_준다() throws Exception {
		String bearer = newMemberBearer();
		String session = createSession(bearer, "RECORD");

		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/records").header(AUTH, bearer),
				saveBody("2026-10-14", true, Map.of("itching", 11), 6)))
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
			.contains(yesterday + ": 팔(가려움 4)")
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
				saveBody("2026-10-15", true, Map.of("itching", 4), 6)))
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
				saveBody("2026-10-16", true, Map.of("itching", 4), 6)))
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
		llm.enqueue("{\"understood\": true, \"reply\": \"어느 부위인가요?\", \"updates\": {}}");
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
		condition.put("symptoms", Map.of("itching", itching));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("skinConditions", List.of(condition));
		body.put("notes", notes);
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

}
