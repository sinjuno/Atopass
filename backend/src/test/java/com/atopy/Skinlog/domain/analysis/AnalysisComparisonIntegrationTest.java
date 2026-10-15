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

class AnalysisComparisonIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final LocalDate DATE_A = LocalDate.of(2026, 5, 1);
	private static final LocalDate DATE_B = LocalDate.of(2026, 5, 10);

	@Test
	void 양쪽_다_기록이_있으면_A와_B키로_기록_전체를_내려준다() throws Exception {
		String bearer = newMemberBearer();
		long recordIdA = createRecord(bearer, DATE_A, Map.of("itching", 2));
		long recordIdB = createRecord(bearer, DATE_B, Map.of("itching", 8));

		mockMvc.perform(comparison(bearer, DATE_A, DATE_B))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.A.date").value("2026-05-01"))
			.andExpect(jsonPath("$.data.A.recordId").value(recordIdA))
			.andExpect(jsonPath("$.data.A.skinConditions[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.A.skinConditions[0].symptoms.itching").value(2))
			.andExpect(jsonPath("$.data.A.sleepHours").value(6.0))
			.andExpect(jsonPath("$.data.A.lifestyle.outdoorActivity").value("MEDIUM"))
			.andExpect(jsonPath("$.data.A.notes").value("메모A"))
			.andExpect(jsonPath("$.data.A.photos").isEmpty())
			.andExpect(jsonPath("$.data.B.date").value("2026-05-10"))
			.andExpect(jsonPath("$.data.B.recordId").value(recordIdB))
			.andExpect(jsonPath("$.data.B.skinConditions[0].symptoms.itching").value(8));
	}

	@Test
	void 기록이_없는_날짜는_null이다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, DATE_A, Map.of("itching", 2));

		mockMvc.perform(comparison(bearer, DATE_A, DATE_B))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.A.date").value("2026-05-01"))
			.andExpect(jsonPath("$.data.B").doesNotExist());
	}

	@Test
	void 다른_회원의_기록은_가져오지_않는다() throws Exception {
		createRecord(newMemberBearer(), DATE_A, Map.of("itching", 2));

		mockMvc.perform(comparison(newMemberBearer(), DATE_A, DATE_B))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.A").doesNotExist())
			.andExpect(jsonPath("$.data.B").doesNotExist());
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/analysis/comparison")
				.param("dateA", "2026-05-01").param("dateB", "2026-05-10"))
			.andExpect(status().isUnauthorized());
	}

	private MockHttpServletRequestBuilder comparison(String bearer, LocalDate dateA, LocalDate dateB) {
		return get("/api/v1/analysis/comparison").header(AUTH, bearer)
			.param("dateA", dateA.toString()).param("dateB", dateB.toString());
	}

	private long createRecord(String bearer, LocalDate date, Map<String, Object> symptoms) throws Exception {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", symptoms);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("skinConditions", List.of(condition));
		body.put("sleepHours", 6);
		body.put("lifestyle", Map.of("outdoorActivity", "MEDIUM"));
		body.put("notes", "메모" + (date.equals(DATE_A) ? "A" : "B"));
		String response = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("recordId").asLong();
	}

}
