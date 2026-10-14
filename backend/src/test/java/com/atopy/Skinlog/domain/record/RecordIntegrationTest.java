package com.atopy.Skinlog.domain.record;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Skinlog.support.ApiTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class RecordIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;

	@Test
	void 기록을_생성하고_상세로_조회한다() throws Exception {
		String bearer = newMemberBearer();

		long id = createRecord(bearer, "2026-10-10");

		mockMvc.perform(get("/api/v1/records/" + id).header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recordId").value(id))
			.andExpect(jsonPath("$.data.recordDate").value("2026-10-10"))
			.andExpect(jsonPath("$.data.skinConditions[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.itching").value(4))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.redness").value(3))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.pain").doesNotExist())
			.andExpect(jsonPath("$.data.sleepHours").value(6.0))
			.andExpect(jsonPath("$.data.lifestyle.alcohol").value(false))
			.andExpect(jsonPath("$.data.lifestyle.outdoorActivity").value("MEDIUM"))
			.andExpect(jsonPath("$.data.notes").value("저녁에 팔 부위가 가려웠음"))
			.andExpect(jsonPath("$.data.photos").isEmpty());
	}

	@Test
	void 생성_응답은_201과_기록_ID를_준다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), recordBody("2026-10-11")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.recordId").isNumber())
			.andExpect(jsonPath("$.data.recordDate").value("2026-10-11"))
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty());
	}

	@Test
	void 선택_항목을_생략해도_저장되고_null로_내려간다() throws Exception {
		String bearer = newMemberBearer();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", "2026-10-12");
		body.put("skinConditions", List.of(condition("FACE", Map.of("dryness", 2))));

		String response = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long id = jsonMapper.readTree(response).get("data").get("recordId").asLong();

		mockMvc.perform(get("/api/v1/records/" + id).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.sleepHours").doesNotExist())
			.andExpect(jsonPath("$.data.lifestyle.alcohol").doesNotExist())
			.andExpect(jsonPath("$.data.lifestyle.outdoorActivity").doesNotExist())
			.andExpect(jsonPath("$.data.notes").doesNotExist());
	}

	@Test
	void 같은_날짜의_기록이_있으면_409를_반환한다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, "2026-10-13");

		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), recordBody("2026-10-13")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RECORD_ALREADY_EXISTS"));
	}

	@Test
	void 다른_회원은_같은_날짜에_기록할_수_있다() throws Exception {
		createRecord(newMemberBearer(), "2026-10-14");

		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, newMemberBearer()), recordBody("2026-10-14")))
			.andExpect(status().isCreated());
	}

	@Test
	void 점수가_범위를_벗어나면_INVALID_SCORE_RANGE와_필드_상세를_반환한다() throws Exception {
		String bearer = newMemberBearer();
		Map<String, Object> body = recordBody("2026-10-15");
		body.put("skinConditions", List.of(condition("ARM", Map.of("itching", 11, "dryness", -1))));

		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_SCORE_RANGE"))
			.andExpect(jsonPath("$.error.details[?(@.field=='skinConditions[0].symptoms.itching')]").exists())
			.andExpect(jsonPath("$.error.details[?(@.field=='skinConditions[0].symptoms.dryness')]").exists());
	}

	@Test
	void 점수_0과_10은_허용된다() throws Exception {
		String bearer = newMemberBearer();
		Map<String, Object> body = recordBody("2026-10-16");
		body.put("skinConditions", List.of(condition("ARM", Map.of("itching", 0, "dryness", 10))));

		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

	@Test
	void 고통_지수와_약_복용과_나타난_증상을_저장하고_수면_점수를_계산한다() throws Exception {
		String bearer = newMemberBearer();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", "2026-10-13");
		body.put("painIndex", 6);
		body.put("sleepHours", 5.5);
		body.put("medication", true);
		body.put("skinSigns", List.of("EXCORIATION", "XEROSIS", "XEROSIS"));

		String response = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long id = jsonMapper.readTree(response).get("data").get("recordId").asLong();

		// 부위별 증상 없이 고통 지수만 있어도 저장된다. 증상은 중복 없이 정의된 순서로 내려간다.
		mockMvc.perform(get("/api/v1/records/" + id).header(AUTH, bearer))
			.andExpect(jsonPath("$.data.skinConditions").isEmpty())
			.andExpect(jsonPath("$.data.painIndex").value(6))
			.andExpect(jsonPath("$.data.sleepScore").value(4))
			.andExpect(jsonPath("$.data.medication").value(true))
			.andExpect(jsonPath("$.data.skinSigns.length()").value(2))
			.andExpect(jsonPath("$.data.skinSigns[0]").value("XEROSIS"))
			.andExpect(jsonPath("$.data.skinSigns[1]").value("EXCORIATION"));

		mockMvc.perform(get("/api/v1/records").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.content[0].painIndex").value(6))
			.andExpect(jsonPath("$.data.content[0].sleepScore").value(4));
	}

	@Test
	void 고통_지수는_1에서_10만_허용한다() throws Exception {
		String bearer = newMemberBearer();
		for (int painIndex : new int[] {0, 11}) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("recordDate", "2026-10-13");
			body.put("painIndex", painIndex);
			mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.details[?(@.field=='painIndex')]").exists());
		}
	}

	@Test
	void 입력이_잘못되면_INVALID_INPUT을_반환한다() throws Exception {
		String bearer = newMemberBearer();

		// 날짜 없음, 부위 목록과 고통 지수 모두 없음, 수면 시간 범위 초과
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("skinConditions", List.of());
		body.put("sleepHours", 25);
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[?(@.field=='recordDate')]").exists())
			.andExpect(jsonPath("$.error.details[?(@.field=='skinConditionsOrPainIndexPresent')]").exists())
			.andExpect(jsonPath("$.error.details[?(@.field=='sleepHours')]").exists());
	}

	@Test
	void 알_수_없는_부위나_증상이_없는_부위나_중복_부위는_400이다() throws Exception {
		String bearer = newMemberBearer();

		Map<String, Object> unknownPart = recordBody("2026-10-17");
		unknownPart.put("skinConditions", List.of(condition("TAIL", Map.of("itching", 3))));
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), unknownPart))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		Map<String, Object> noSymptom = recordBody("2026-10-17");
		noSymptom.put("skinConditions", List.of(condition("ARM", Map.of())));
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), noSymptom))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));

		Map<String, Object> duplicate = recordBody("2026-10-17");
		duplicate.put("skinConditions", List.of(
			condition("ARM", Map.of("itching", 3)), condition("ARM", Map.of("redness", 2))));
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), duplicate))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/records"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void 다른_회원의_기록은_조회_수정_삭제_모두_404이다() throws Exception {
		long id = createRecord(newMemberBearer(), "2026-10-18");
		String other = newMemberBearer();

		mockMvc.perform(get("/api/v1/records/" + id).header(AUTH, other))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
		mockMvc.perform(withJson(put("/api/v1/records/" + id).header(AUTH, other), recordBody("2026-10-18")))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/v1/records/" + id).header(AUTH, other))
			.andExpect(status().isNotFound());
	}

	@Test
	void 없는_기록은_404이다() throws Exception {
		mockMvc.perform(get("/api/v1/records/99999999").header(AUTH, newMemberBearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
	}

	@Test
	void 수정하면_부위와_증상이_통째로_교체된다() throws Exception {
		String bearer = newMemberBearer();
		long id = createRecord(bearer, "2026-10-19");

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", "2026-10-19");
		body.put("skinConditions", List.of(
			condition("ARM", Map.of("itching", 1, "pain", 7)),
			condition("LEG", Map.of("dryness", 5))));
		body.put("notes", "수정함");

		mockMvc.perform(withJson(put("/api/v1/records/" + id).header(AUTH, bearer), body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.skinConditions.length()").value(2))
			// 부위는 FACE, NECK, CHEST, BACK, ABDOMEN, ARM, HAND, LEG, FOOT 순으로 내려간다.
			.andExpect(jsonPath("$.data.skinConditions[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.itching").value(1))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.pain").value(7))
			.andExpect(jsonPath("$.data.skinConditions[1].bodyPart").value("LEG"))
			.andExpect(jsonPath("$.data.skinConditions[1].symptoms.dryness").value(5))
			// 이전에 있던 stinging·redness는 사라지고, 생략한 수면·생활 정보는 null이 된다.
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.stinging").doesNotExist())
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.redness").doesNotExist())
			.andExpect(jsonPath("$.data.sleepHours").doesNotExist())
			.andExpect(jsonPath("$.data.notes").value("수정함"));

		// 부위를 줄여서 다시 수정해도 제약 위반 없이 반영된다.
		Map<String, Object> shrink = new LinkedHashMap<>();
		shrink.put("recordDate", "2026-10-19");
		shrink.put("skinConditions", List.of(condition("ARM", Map.of("itching", 2))));
		mockMvc.perform(withJson(put("/api/v1/records/" + id).header(AUTH, bearer), shrink))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.skinConditions.length()").value(1))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.itching").value(2))
			.andExpect(jsonPath("$.data.skinConditions[0].symptoms.pain").doesNotExist());
	}

	@Test
	void 수정으로_다른_기록이_있는_날짜로_바꾸면_409이다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, "2026-10-20");
		long second = createRecord(bearer, "2026-10-21");

		mockMvc.perform(withJson(put("/api/v1/records/" + second).header(AUTH, bearer), recordBody("2026-10-20")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RECORD_ALREADY_EXISTS"));
	}

	@Test
	void 삭제하면_조회되지_않고_같은_날짜에_다시_기록할_수_있다() throws Exception {
		String bearer = newMemberBearer();
		long id = createRecord(bearer, "2026-10-22");

		mockMvc.perform(delete("/api/v1/records/" + id).header(AUTH, bearer))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/records/" + id).header(AUTH, bearer))
			.andExpect(status().isNotFound());
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), recordBody("2026-10-22")))
			.andExpect(status().isCreated());
	}

	@Test
	void 목록은_기간으로_거르고_최신_날짜부터_페이지로_준다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, "2026-09-01");
		createRecord(bearer, "2026-09-05");
		createRecord(bearer, "2026-09-10");
		createRecord(newMemberBearer(), "2026-09-07"); // 다른 회원의 기록은 섞이지 않는다.

		mockMvc.perform(get("/api/v1/records").header(AUTH, bearer)
				.param("startDate", "2026-09-02").param("endDate", "2026-09-30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.content[0].recordDate").value("2026-09-10"))
			.andExpect(jsonPath("$.data.content[1].recordDate").value("2026-09-05"))
			.andExpect(jsonPath("$.data.content[0].skinConditions[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.content[0].photoCount").value(0));

		mockMvc.perform(get("/api/v1/records").header(AUTH, bearer).param("page", "1").param("size", "2"))
			.andExpect(jsonPath("$.data.totalElements").value(3))
			.andExpect(jsonPath("$.data.totalPages").value(2))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].recordDate").value("2026-09-01"));
	}

	@Test
	void 목록_파라미터가_잘못되면_400이다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(get("/api/v1/records").header(AUTH, bearer)
				.param("startDate", "2026-10-10").param("endDate", "2026-10-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/records").header(AUTH, bearer).param("size", "1000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/records").header(AUTH, bearer).param("startDate", "10월 1일"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	// ── 헬퍼 ──

	private long createRecord(String bearer, String date) throws Exception {
		String response = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), recordBody(date)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("recordId").asLong();
	}

	private Map<String, Object> recordBody(String date) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date);
		body.put("skinConditions", List.of(
			condition("ARM", Map.of("itching", 4, "stinging", 2, "redness", 3))));
		body.put("sleepHours", 6);
		body.put("lifestyle", Map.of("alcohol", false, "outdoorActivity", "MEDIUM"));
		body.put("notes", "저녁에 팔 부위가 가려웠음");
		return body;
	}

	private Map<String, Object> condition(String bodyPart, Map<String, ?> symptoms) {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", bodyPart);
		condition.put("symptoms", symptoms);
		return condition;
	}

}
