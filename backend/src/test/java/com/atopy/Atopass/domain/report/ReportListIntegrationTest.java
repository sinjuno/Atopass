package com.atopy.Atopass.domain.report;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ReportListIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final LocalDate START = LocalDate.of(2026, 6, 1);

	@Test
	void 목록을_최신순_배열로_준다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4);
		createReport(bearer, START, START.plusDays(1));
		createReport(bearer, START, START.plusDays(2));
		createReport(bearer, START, START.plusDays(3));

		mockMvc.perform(list(bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].reportId").isNumber())
			.andExpect(jsonPath("$.data[0].endDate").value("2026-06-04"))
			.andExpect(jsonPath("$.data[1].endDate").value("2026-06-03"))
			.andExpect(jsonPath("$.data[2].endDate").value("2026-06-02"));
	}

	@Test
	void 다른_회원의_보고서는_섞이지_않는다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4);
		createReport(bearer, START, START.plusDays(1));

		createRecord(newMemberBearer(), START, 4);

		mockMvc.perform(list(bearer))
			.andExpect(jsonPath("$.data.length()").value(1));
	}

	@Test
	void 상세를_조회한다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4);
		long reportId = createReportAndGetId(bearer, START, START.plusDays(5));

		mockMvc.perform(get("/api/v1/reports/" + reportId).header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.reportId").value(reportId))
			.andExpect(jsonPath("$.data.startDate").value("2026-06-01"))
			.andExpect(jsonPath("$.data.endDate").value("2026-06-06"));
	}

	@Test
	void 다른_회원의_보고서_상세는_404이다() throws Exception {
		String owner = newMemberBearer();
		createRecord(owner, START, 4);
		long reportId = createReportAndGetId(owner, START, START.plusDays(5));

		mockMvc.perform(get("/api/v1/reports/" + reportId).header(AUTH, newMemberBearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
	}

	@Test
	void 없는_보고서는_404이다() throws Exception {
		mockMvc.perform(get("/api/v1/reports/99999999").header(AUTH, newMemberBearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(get("/api/v1/reports")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/reports/1")).andExpect(status().isUnauthorized());
	}

	private MockHttpServletRequestBuilder list(String bearer) {
		return get("/api/v1/reports").header(AUTH, bearer);
	}

	private void createReport(String bearer, LocalDate start, LocalDate end) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startDate", start.toString());
		body.put("endDate", end.toString());
		mockMvc.perform(withJson(post("/api/v1/reports").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

	private long createReportAndGetId(String bearer, LocalDate start, LocalDate end) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startDate", start.toString());
		body.put("endDate", end.toString());
		String response = mockMvc.perform(withJson(post("/api/v1/reports").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("reportId").asLong();
	}

	private void createRecord(String bearer, LocalDate date, int itching) throws Exception {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", Map.of("itching", itching));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("skinConditions", List.of(condition));
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

}
