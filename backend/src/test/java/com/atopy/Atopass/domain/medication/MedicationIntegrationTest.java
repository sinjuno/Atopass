package com.atopy.Atopass.domain.medication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.domain.medication.repository.MedicationRepository;
import com.atopy.Atopass.domain.record.repository.PhotoRepository;
import com.atopy.Atopass.support.ApiTestSupport;
import com.atopy.Atopass.support.FakeChatModelClient;
import com.atopy.Atopass.support.FakeLlmConfig;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** 복용 약 등록·수정·중단과 약 봉투 사진 읽기. AI는 가짜로 바꿔 쓴다. */
@Import(FakeLlmConfig.class)
@TestPropertySource(properties = "app.llm.daily-message-limit=3")
class MedicationIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
	private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

	@Autowired
	FakeChatModelClient llm;
	@Autowired
	MedicationRepository medicationRepository;
	@Autowired
	PhotoRepository photoRepository;

	@BeforeEach
	void resetLlm() {
		llm.reset();
	}

	// ── 추가·조회 ──

	@Test
	void 약을_추가하면_201과_함께_저장한_값을_돌려준다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(withJson(post("/api/v1/medications").header(AUTH, bearer), medication("아토케어정", "oral")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isString())
			.andExpect(jsonPath("$.data.name").value("아토케어정"))
			.andExpect(jsonPath("$.data.type").value("oral"))
			.andExpect(jsonPath("$.data.dose").value("1정"))
			.andExpect(jsonPath("$.data.times[0]").value("morning"))
			.andExpect(jsonPath("$.data.times[1]").value("evening"))
			.andExpect(jsonPath("$.data.timesText").value("1일 2회 식후 30분"))
			.andExpect(jsonPath("$.data.startDate").value(TODAY.toString()))
			.andExpect(jsonPath("$.data.endDate").isEmpty())
			.andExpect(jsonPath("$.data.isOtherCondition").value(false))
			.andExpect(jsonPath("$.data.source").value("mypage"));
	}

	@Test
	void 이름_종류_시작일이_없거나_값이_잘못되면_400이다() throws Exception {
		String bearer = newMemberBearer();

		Map<String, Object> noName = medication("", "oral");
		mockMvc.perform(withJson(post("/api/v1/medications").header(AUTH, bearer), noName))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(withJson(post("/api/v1/medications").header(AUTH, bearer), medication("약", "pill")))
			.andExpect(status().isBadRequest());
		Map<String, Object> noStart = medication("약", "oral");
		noStart.remove("startDate");
		mockMvc.perform(withJson(post("/api/v1/medications").header(AUTH, bearer), noStart))
			.andExpect(status().isBadRequest());
		Map<String, Object> badTime = medication("약", "oral");
		badTime.put("times", List.of("noon"));
		mockMvc.perform(withJson(post("/api/v1/medications").header(AUTH, bearer), badTime))
			.andExpect(status().isBadRequest());
		Map<String, Object> endBeforeStart = medication("약", "oral");
		endBeforeStart.put("endDate", TODAY.minusDays(1).toString());
		mockMvc.perform(withJson(post("/api/v1/medications").header(AUTH, bearer), endBeforeStart))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/medications").param("status", "active")).andExpect(status().isUnauthorized());
	}

	@Test
	void 목록은_복용_중과_중단을_나누고_내_약만_준다() throws Exception {
		String bearer = newMemberBearer();
		String other = newMemberBearer();
		long active = createMedication(bearer, "복용중약", "oral");
		long future = createMedicationWithEnd(bearer, "곧끝나는약", TODAY.plusDays(3));
		long stopped = createMedicationWithEnd(bearer, "끝난약", TODAY);
		createMedication(other, "남의약", "topical");

		mockMvc.perform(get("/api/v1/medications").param("status", "active").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[?(@.id=='" + active + "')]").exists())
			.andExpect(jsonPath("$.data[?(@.id=='" + future + "')]").exists());
		mockMvc.perform(get("/api/v1/medications").param("status", "stopped").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(String.valueOf(stopped)));
		mockMvc.perform(get("/api/v1/medications").param("status", "all").header(AUTH, bearer))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/v1/medications").header(AUTH, bearer)).andExpect(status().isBadRequest());
	}

	// ── 한꺼번에 저장 ──

	@Test
	void 여러_개를_한_번에_저장하면_요청한_경로를_모두에_쓴다() throws Exception {
		String bearer = newMemberBearer();
		Map<String, Object> body = Map.of(
			"items", List.of(medication("약A", "oral"), medication("약B", "topical")),
			"source", "photo");

		mockMvc.perform(withJson(post("/api/v1/medications/bulk").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].name").value("약A"))
			.andExpect(jsonPath("$.data[0].source").value("photo"))
			.andExpect(jsonPath("$.data[1].source").value("photo"));
	}

	@Test
	void 하나라도_잘못되면_아무것도_저장하지_않는다() throws Exception {
		String bearer = newMemberBearer();
		long before = medicationRepository.count();
		Map<String, Object> ok = medication("정상약", "oral");
		Map<String, Object> wrongDates = medication("날짜오류약", "oral");
		wrongDates.put("endDate", TODAY.minusDays(5).toString());

		mockMvc.perform(withJson(post("/api/v1/medications/bulk").header(AUTH, bearer),
				Map.of("items", List.of(ok, wrongDates), "source", "photo")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(withJson(post("/api/v1/medications/bulk").header(AUTH, bearer),
				Map.of("items", List.of(), "source", "photo")))
			.andExpect(status().isBadRequest());

		assertThat(medicationRepository.count()).isEqualTo(before);
	}

	// ── 수정·중단·이력 ──

	@Test
	void 수정하면_바뀐_필드만_이력에_남고_안_바뀐_값은_남기지_않는다() throws Exception {
		String bearer = newMemberBearer();
		long id = createMedication(bearer, "아토케어정", "oral");

		Map<String, Object> change = new LinkedHashMap<>();
		change.put("dose", "2정");
		change.put("name", "아토케어정"); // 그대로
		change.put("times", List.of("bedtime"));
		change.put("memo", "자기 전에");
		mockMvc.perform(withJson(patch("/api/v1/medications/" + id).header(AUTH, bearer), change))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.dose").value("2정"))
			.andExpect(jsonPath("$.data.times[0]").value("bedtime"))
			.andExpect(jsonPath("$.data.times.length()").value(1))
			.andExpect(jsonPath("$.data.memo").value("자기 전에"));

		mockMvc.perform(get("/api/v1/medications/" + id + "/history").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[?(@.field=='dose')].before").value("1정"))
			.andExpect(jsonPath("$.data[?(@.field=='dose')].after").value("2정"))
			.andExpect(jsonPath("$.data[?(@.field=='times')].before").value("morning,evening"))
			.andExpect(jsonPath("$.data[?(@.field=='times')].after").value("bedtime"))
			.andExpect(jsonPath("$.data[?(@.field=='name')]").isEmpty());
	}

	@Test
	void 빈_문자열을_보내면_용량과_메모를_비우고_수정할_내용이_없으면_400이다() throws Exception {
		String bearer = newMemberBearer();
		long id = createMedication(bearer, "아토케어정", "oral");

		mockMvc.perform(withJson(patch("/api/v1/medications/" + id).header(AUTH, bearer), Map.of("dose", "")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.dose").isEmpty());
		mockMvc.perform(withJson(patch("/api/v1/medications/" + id).header(AUTH, bearer), Map.of()))
			.andExpect(status().isBadRequest());
		mockMvc.perform(withJson(patch("/api/v1/medications/" + id).header(AUTH, bearer), Map.of("name", " ")))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 중단하면_종료일이_정해지고_이력이_남으며_중단_목록으로_옮겨간다() throws Exception {
		String bearer = newMemberBearer();
		long id = createMedication(bearer, "아토케어정", "oral");

		mockMvc.perform(withJson(post("/api/v1/medications/" + id + "/stop").header(AUTH, bearer),
				Map.of("endDate", TODAY.toString())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.endDate").value(TODAY.toString()));

		mockMvc.perform(get("/api/v1/medications").param("status", "active").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(get("/api/v1/medications").param("status", "stopped").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.length()").value(1));
		mockMvc.perform(get("/api/v1/medications/" + id + "/history").header(AUTH, bearer))
			.andExpect(jsonPath("$.data[0].field").value("endDate"))
			.andExpect(jsonPath("$.data[0].before").isEmpty())
			.andExpect(jsonPath("$.data[0].after").value(TODAY.toString()));
		assertThat(medicationRepository.findById(id)).isPresent();
	}

	@Test
	void 종료일이_시작일보다_앞서면_400이다() throws Exception {
		String bearer = newMemberBearer();
		long id = createMedication(bearer, "아토케어정", "oral");

		mockMvc.perform(withJson(post("/api/v1/medications/" + id + "/stop").header(AUTH, bearer),
				Map.of("endDate", TODAY.minusDays(1).toString())))
			.andExpect(status().isBadRequest());
		mockMvc.perform(withJson(post("/api/v1/medications/" + id + "/stop").header(AUTH, bearer), Map.of()))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 다른_회원의_약은_수정_중단_이력_모두_404이다() throws Exception {
		String owner = newMemberBearer();
		String stranger = newMemberBearer();
		long id = createMedication(owner, "아토케어정", "oral");

		mockMvc.perform(withJson(patch("/api/v1/medications/" + id).header(AUTH, stranger), Map.of("dose", "9정")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("MEDICATION_NOT_FOUND"));
		mockMvc.perform(withJson(post("/api/v1/medications/" + id + "/stop").header(AUTH, stranger),
				Map.of("endDate", TODAY.toString())))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/medications/" + id + "/history").header(AUTH, stranger))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/medications/999999/history").header(AUTH, owner)).andExpect(status().isNotFound());

		assertThat(medicationRepository.findById(id).orElseThrow().getDose()).isEqualTo("1정");
	}

	// ── 약 등록 단계 ──

	@Test
	void 약_등록_단계는_처음에는_pending이고_done_skipped로만_바꿀_수_있다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(get("/api/v1/members/me/onboarding").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.medicationStep").value("pending"));
		mockMvc.perform(withJson(patch("/api/v1/members/me/onboarding").header(AUTH, bearer),
				Map.of("medicationStep", "pending")))
			.andExpect(status().isBadRequest());
		mockMvc.perform(withJson(patch("/api/v1/members/me/onboarding").header(AUTH, bearer),
				Map.of("medicationStep", "skipped")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.medicationStep").value("skipped"));
		mockMvc.perform(withJson(patch("/api/v1/members/me/onboarding").header(AUTH, bearer),
				Map.of("medicationStep", "done")))
			.andExpect(jsonPath("$.data.medicationStep").value("done"));
		mockMvc.perform(get("/api/v1/members/me/onboarding").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.medicationStep").value("done"));
	}

	// ── 사진으로 읽기 ──

	@Test
	void 사진을_읽으면_약속한_칸만_돌려주고_저장하지_않는다() throws Exception {
		String email = "med-" + java.util.UUID.randomUUID() + "@example.com";
		String bearer = newMemberBearer(email);
		grantConsent(bearer);
		long medicationsBefore = medicationRepository.count();
		long photosBefore = photoRepository.count();
		llm.enqueue("""
			```json
			[{"name": "아토케어정", "type": "oral", "dose": "1정", "timesText": "1일 2회 식후 30분",
			  "times": ["morning", "evening"], "days": 7,
			  "patientName": "홍길동", "rrn": "900101-1234567", "hospital": "행복병원", "pharmacy": "건강약국"}]
			```""");

		mockMvc.perform(extract(bearer, JPEG_BYTES, JPEG_BYTES))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].id").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].name").value("아토케어정"))
			.andExpect(jsonPath("$.data.items[0].type").value("oral"))
			.andExpect(jsonPath("$.data.items[0].dose").value("1정"))
			.andExpect(jsonPath("$.data.items[0].timesText").value("1일 2회 식후 30분"))
			.andExpect(jsonPath("$.data.items[0].times[0]").value("morning"))
			.andExpect(jsonPath("$.data.items[0].times[1]").value("evening"))
			.andExpect(jsonPath("$.data.items[0].days").value(7))
			.andExpect(jsonPath("$.data.items[0].startDate").isEmpty())
			.andExpect(jsonPath("$.data.items[0].endDate").isEmpty())
			.andExpect(jsonPath("$.data.items[0].patientName").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].rrn").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].hospital").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].pharmacy").doesNotExist());

		// 읽기만 하고 저장하지 않는다. 사진 원본도 남기지 않는다.
		assertThat(medicationRepository.count()).isEqualTo(medicationsBefore);
		assertThat(photoRepository.count()).isEqualTo(photosBefore);
		// 사진 두 장이 AI로 갔고, 회원 식별 정보는 가지 않았다.
		assertThat(llm.lastImages()).hasSize(2);
		assertThat(llm.lastImages().get(0).mediaType()).isEqualTo("image/jpeg");
		assertThat(llm.lastSystemPrompt() + llm.lastUserText()).doesNotContain(email).doesNotContain("테스터");
		assertThat(llm.lastSystemPrompt()).contains("환자 이름", "주민등록번호", "병원 이름", "약국 이름");
	}

	@Test
	void 여러_장에서_같은_약이_나오면_하나로_합치고_알아볼_수_없는_값은_비운다() throws Exception {
		String bearer = newMemberBearer();
		grantConsent(bearer);
		llm.enqueue("""
			[{"name": "아토케어정", "type": "oral", "dose": null, "times": ["morning"], "days": 7},
			 {"name": "아토케어 정", "type": null, "dose": "1정", "times": ["evening", "noon"], "timesText": "아침 저녁"},
			 {"name": "보습크림", "type": "cream", "times": [], "days": 0},
			 {"name": "  ", "type": "oral"},
			 "약", 3]""");

		mockMvc.perform(extract(bearer, JPEG_BYTES))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].name").value("아토케어정"))
			.andExpect(jsonPath("$.data.items[0].type").value("oral"))
			.andExpect(jsonPath("$.data.items[0].dose").value("1정"))
			.andExpect(jsonPath("$.data.items[0].times.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].timesText").value("아침 저녁"))
			.andExpect(jsonPath("$.data.items[0].days").value(7))
			.andExpect(jsonPath("$.data.items[1].name").value("보습크림"))
			.andExpect(jsonPath("$.data.items[1].type").isEmpty())
			.andExpect(jsonPath("$.data.items[1].times.length()").value(0))
			.andExpect(jsonPath("$.data.items[1].days").isEmpty());
	}

	@Test
	void 약을_못_읽으면_422이다() throws Exception {
		String bearer = newMemberBearer();
		grantConsent(bearer);

		llm.enqueue("[]");
		mockMvc.perform(extract(bearer, JPEG_BYTES))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.error.code").value("MEDICATION_NOT_RECOGNIZED"));
		llm.enqueue("약 이름을 읽을 수 없어요");
		mockMvc.perform(extract(bearer, JPEG_BYTES)).andExpect(status().isUnprocessableContent());
		llm.enqueue("{\"name\": \"객체\"}");
		mockMvc.perform(extract(bearer, JPEG_BYTES)).andExpect(status().isUnprocessableContent());
	}

	@Test
	void 동의하지_않으면_AI를_부르지_않고_403이다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(extract(bearer, JPEG_BYTES))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));
		assertThat(llm.callCount()).isZero();
	}

	@Test
	void 사진이_없거나_형식이_틀리거나_3장을_넘으면_AI를_부르지_않는다() throws Exception {
		String bearer = newMemberBearer();
		grantConsent(bearer);

		mockMvc.perform(multipart("/api/v1/medications/extract").header(AUTH, bearer))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(extract(bearer, "GIF89a".getBytes()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
		mockMvc.perform(extract(bearer, JPEG_BYTES, JPEG_BYTES, JPEG_BYTES, JPEG_BYTES))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));

		assertThat(llm.callCount()).isZero();
	}

	@Test
	void AI를_쓸_수_없으면_503이고_이용량에_세지_않는다() throws Exception {
		String bearer = newMemberBearer();
		grantConsent(bearer);
		llm.enqueueFailure();

		mockMvc.perform(extract(bearer, JPEG_BYTES))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.error.code").value("CHATBOT_UNAVAILABLE"));

		// 한도가 3이므로, 실패가 이용량에 셌다면 세 번째 성공 호출이 429가 된다.
		for (int i = 0; i < 3; i++) {
			llm.enqueue(OK_RESPONSE);
			mockMvc.perform(extract(bearer, JPEG_BYTES)).andExpect(status().isOk());
		}
	}

	@Test
	void 하루_이용_한도는_챗봇_메시지와_합쳐서_센다() throws Exception {
		String bearer = newMemberBearer();
		grantConsent(bearer);

		for (int i = 0; i < 3; i++) {
			llm.enqueue(OK_RESPONSE);
			mockMvc.perform(extract(bearer, JPEG_BYTES)).andExpect(status().isOk());
		}
		int calls = llm.callCount();

		mockMvc.perform(extract(bearer, JPEG_BYTES))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.error.code").value("CHAT_LIMIT_EXCEEDED"));
		// 챗봇 메시지도 같은 한도에서 막힌다.
		String session = createChatSession(bearer);
		mockMvc.perform(withJson(post("/api/v1/chatbot/sessions/" + session + "/messages").header(AUTH, bearer),
				Map.of("message", "안녕하세요")))
			.andExpect(status().isTooManyRequests());
		assertThat(llm.callCount()).isEqualTo(calls);
	}

	// ── 도우미 ──

	private static final String OK_RESPONSE = "[{\"name\": \"아토케어정\", \"type\": \"oral\"}]";

	private Map<String, Object> medication(String name, String type) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", name);
		body.put("type", type);
		body.put("dose", "1정");
		body.put("times", List.of("evening", "morning"));
		body.put("timesText", "1일 2회 식후 30분");
		body.put("startDate", TODAY.toString());
		return body;
	}

	private long createMedication(String bearer, String name, String type) throws Exception {
		return createFrom(bearer, medication(name, type));
	}

	private long createMedicationWithEnd(String bearer, String name, LocalDate endDate) throws Exception {
		Map<String, Object> body = medication(name, "oral");
		body.put("startDate", TODAY.minusDays(10).toString());
		body.put("endDate", endDate.toString());
		return createFrom(bearer, body);
	}

	private long createFrom(String bearer, Map<String, Object> body) throws Exception {
		String response = mockMvc.perform(withJson(post("/api/v1/medications").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return Long.parseLong(jsonMapper.readTree(response).get("data").get("id").asString());
	}

	private MockMultipartHttpServletRequestBuilder extract(String bearer, byte[]... photos) {
		MockMultipartHttpServletRequestBuilder builder = multipart("/api/v1/medications/extract");
		for (byte[] photo : photos) {
			builder.file(new MockMultipartFile("files", "pill.jpg", "image/jpeg", photo));
		}
		builder.header(AUTH, bearer);
		return builder;
	}

	private void grantConsent(String bearer) throws Exception {
		mockMvc.perform(post("/api/v1/chatbot/consent").header(AUTH, bearer)).andExpect(status().isOk());
	}

	private String createChatSession(String bearer) throws Exception {
		MockHttpServletRequestBuilder request =
			withJson(post("/api/v1/chatbot/sessions").header(AUTH, bearer), Map.of("mode", "RECORD"));
		String body = mockMvc.perform(request).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("data").get("sessionId").asString();
	}

}
