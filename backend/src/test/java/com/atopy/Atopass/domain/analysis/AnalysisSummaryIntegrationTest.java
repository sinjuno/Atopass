package com.atopy.Atopass.domain.analysis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.support.ApiTestSupport;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class AnalysisSummaryIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final LocalDate START = LocalDate.of(2026, 3, 1);

	@Test
	void 기록이_7건_미만이면_비교할_수_없고_인사이트도_없다() throws Exception {
		String bearer = newMemberBearer();
		for (int day = 0; day < 6; day++) {
			createRecord(bearer, START.plusDays(day), day == 5 ? 10 : 1);
		}

		mockMvc.perform(summary(bearer, START, START.plusDays(30)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.period.startDate").value("2026-03-01"))
			.andExpect(jsonPath("$.data.period.endDate").value("2026-03-31"))
			.andExpect(jsonPath("$.data.recordCount").value(6))
			.andExpect(jsonPath("$.data.availableForComparison").value(false))
			.andExpect(jsonPath("$.data.insights").isEmpty());
	}

	@Test
	void 기록이_없어도_정상_응답한다() throws Exception {
		mockMvc.perform(summary(newMemberBearer(), START, START.plusDays(30)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recordCount").value(0))
			.andExpect(jsonPath("$.data.availableForComparison").value(false))
			.andExpect(jsonPath("$.data.insights").isEmpty());
	}

	@Test
	void 기록이_7건_이상이면_평균에서_크게_벗어난_구간을_알려준다() throws Exception {
		String bearer = newMemberBearer();
		// 고통 점수가 6일은 2점, 마지막 이틀은 9점
		for (int day = 0; day < 8; day++) {
			createRecord(bearer, START.plusDays(day), day >= 6 ? 9 : 2);
		}

		mockMvc.perform(summary(bearer, START, START.plusDays(30)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.recordCount").value(8))
			.andExpect(jsonPath("$.data.availableForComparison").value(true))
			.andExpect(jsonPath("$.data.insights.length()").value(1))
			.andExpect(jsonPath("$.data.insights[0].type").value("SYMPTOM_CHANGE"))
			.andExpect(jsonPath("$.data.insights[0].bodyPart").value("ARM"))
			.andExpect(jsonPath("$.data.insights[0].symptom").value("pain"))
			.andExpect(jsonPath("$.data.insights[0].direction").value("HIGH"))
			.andExpect(jsonPath("$.data.insights[0].startDate").value("2026-03-07"))
			.andExpect(jsonPath("$.data.insights[0].endDate").value("2026-03-08"))
			.andExpect(jsonPath("$.data.insights[0].message")
				.value("팔 고통 점수가 평소(평균 3.8)보다 높게 기록된 구간이 있습니다. (3/7~3/8)"));
	}

	@Test
	void 고통_지수가_평소보다_크게_오른_구간도_알려준다() throws Exception {
		String bearer = newMemberBearer();
		// 부위별 점수 없이 불편함 지수만 기록: 6일은 2점, 마지막 이틀은 9점
		for (int day = 0; day < 8; day++) {
			createPainRecord(bearer, START.plusDays(day), day >= 6 ? 9 : 2);
		}

		mockMvc.perform(summary(bearer, START, START.plusDays(30)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availableForComparison").value(true))
			.andExpect(jsonPath("$.data.insights.length()").value(1))
			.andExpect(jsonPath("$.data.insights[0].type").value("PAIN_INDEX_CHANGE"))
			.andExpect(jsonPath("$.data.insights[0].direction").value("HIGH"))
			.andExpect(jsonPath("$.data.insights[0].startDate").value("2026-03-07"))
			.andExpect(jsonPath("$.data.insights[0].endDate").value("2026-03-08"))
			.andExpect(jsonPath("$.data.insights[0].message")
				.value("불편함 지수가 평소(평균 3.8)보다 높게 기록된 구간이 있습니다. (3/7~3/8)"));
	}

	@Test
	void 고통_지수가_0인_날도_변화_분석에_포함한다() throws Exception {
		String bearer = newMemberBearer();
		// 0은 증상 없음이라는 값이다. 기록하지 않은 날로 빠지면 평균과 구간이 달라진다.
		for (int day = 0; day < 8; day++) {
			createPainRecord(bearer, START.plusDays(day), day >= 6 ? 8 : 0);
		}

		mockMvc.perform(summary(bearer, START, START.plusDays(30)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.insights[0].type").value("PAIN_INDEX_CHANGE"))
			.andExpect(jsonPath("$.data.insights[0].message")
				.value("불편함 지수가 평소(평균 2.0)보다 높게 기록된 구간이 있습니다. (3/7~3/8)"));
	}

	@Test
	void 변화가_없으면_충분한_기록이_있어도_인사이트가_없다() throws Exception {
		String bearer = newMemberBearer();
		for (int day = 0; day < 8; day++) {
			createRecord(bearer, START.plusDays(day), 4);
		}

		mockMvc.perform(summary(bearer, START, START.plusDays(30)))
			.andExpect(jsonPath("$.data.availableForComparison").value(true))
			.andExpect(jsonPath("$.data.insights").isEmpty());
	}

	@Test
	void 기간_밖의_기록과_다른_회원의_기록은_세지_않는다() throws Exception {
		String bearer = newMemberBearer();
		for (int day = 0; day < 10; day++) {
			createRecord(bearer, START.plusDays(day), 3);
		}
		String other = newMemberBearer();
		for (int day = 0; day < 10; day++) {
			createRecord(other, START.plusDays(day), 3);
		}

		// 앞 5일만 조회
		mockMvc.perform(summary(bearer, START, START.plusDays(4)))
			.andExpect(jsonPath("$.data.recordCount").value(5))
			.andExpect(jsonPath("$.data.availableForComparison").value(false));
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/analysis/summary")
				.param("startDate", "2026-03-01").param("endDate", "2026-03-31"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 파라미터가_없거나_잘못되면_400이다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(get("/api/v1/analysis/summary").header(AUTH, bearer).param("startDate", "2026-03-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/analysis/summary").header(AUTH, bearer)
				.param("startDate", "2026-03-31").param("endDate", "2026-03-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		mockMvc.perform(get("/api/v1/analysis/summary").header(AUTH, bearer)
				.param("startDate", "어제").param("endDate", "2026-03-01"))
			.andExpect(status().isBadRequest());
	}

	// ── 헬퍼 ──

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder summary(
		String bearer, LocalDate start, LocalDate end) {
		return get("/api/v1/analysis/summary").header(AUTH, bearer)
			.param("startDate", start.toString()).param("endDate", end.toString());
	}

	private void createPainRecord(String bearer, LocalDate date, int painIndex) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("painIndex", painIndex);
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

	private void createRecord(String bearer, LocalDate date, int itching) throws Exception {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", Map.of("pain", itching));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("skinConditions", List.of(condition));
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

}
