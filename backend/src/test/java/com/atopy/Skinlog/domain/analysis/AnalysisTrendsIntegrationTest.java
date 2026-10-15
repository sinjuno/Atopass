package com.atopy.Skinlog.domain.analysis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Skinlog.support.ApiTestSupport;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AnalysisTrendsIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final LocalDate START = LocalDate.of(2026, 4, 1);

	@Test
	void 부위와_증상이_기록된_날짜만_포인트로_준다() throws Exception {
		String bearer = newMemberBearer();
		long firstId = createRecord(bearer, START, "ARM", Map.of("itching", 3));
		// 둘째 날은 기록 자체가 없음
		long secondId = createRecord(bearer, START.plusDays(2), "ARM", Map.of("itching", 5));

		mockMvc.perform(trends(bearer, "ARM", "itching", START, START.plusDays(5)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.bodyPart").doesNotExist())
			.andExpect(jsonPath("$.data.symptom").doesNotExist())
			.andExpect(jsonPath("$.data.points.length()").value(2))
			.andExpect(jsonPath("$.data.points[0].date").value("2026-04-01"))
			.andExpect(jsonPath("$.data.points[0].recordId").value(firstId))
			.andExpect(jsonPath("$.data.points[0].score").value(3))
			.andExpect(jsonPath("$.data.points[1].date").value("2026-04-03"))
			.andExpect(jsonPath("$.data.points[1].recordId").value(secondId))
			.andExpect(jsonPath("$.data.points[1].score").value(5));
	}

	@Test
	void 다른_부위나_증상은_섞이지_않는다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, "ARM", Map.of("itching", 3, "redness", 7));
		createRecord(bearer, START.plusDays(1), "LEG", Map.of("itching", 9));

		mockMvc.perform(trends(bearer, "ARM", "itching", START, START.plusDays(5)))
			.andExpect(jsonPath("$.data.points.length()").value(1))
			.andExpect(jsonPath("$.data.points[0].score").value(3));
	}

	@Test
	void 다른_회원의_기록은_섞이지_않는다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, "ARM", Map.of("itching", 3));
		createRecord(newMemberBearer(), START, "ARM", Map.of("itching", 9));

		mockMvc.perform(trends(bearer, "ARM", "itching", START, START.plusDays(5)))
			.andExpect(jsonPath("$.data.points.length()").value(1))
			.andExpect(jsonPath("$.data.points[0].score").value(3));
	}

	@Test
	void 시작일이_종료일보다_늦으면_400이다() throws Exception {
		mockMvc.perform(trends(newMemberBearer(), "ARM", "itching", START.plusDays(5), START))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 잘못된_부위나_증상_값은_400이다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(trends(bearer, "없는부위", "itching", START, START.plusDays(5)))
			.andExpect(status().isBadRequest());
		mockMvc.perform(trends(bearer, "ARM", "없는증상", START, START.plusDays(5)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/analysis/trends")
				.param("bodyPart", "ARM").param("symptom", "itching")
				.param("startDate", "2026-04-01").param("endDate", "2026-04-05"))
			.andExpect(status().isUnauthorized());
	}

	private MockHttpServletRequestBuilder trends(
		String bearer, String bodyPart, String symptom, LocalDate start, LocalDate end) {
		return get("/api/v1/analysis/trends").header(AUTH, bearer)
			.param("bodyPart", bodyPart).param("symptom", symptom)
			.param("startDate", start.toString()).param("endDate", end.toString());
	}

	private long createRecord(String bearer, LocalDate date, String bodyPart, Map<String, Object> symptoms)
		throws Exception {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", bodyPart);
		condition.put("symptoms", symptoms);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("skinConditions", List.of(condition));
		String response = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("recordId").asLong();
	}

}
