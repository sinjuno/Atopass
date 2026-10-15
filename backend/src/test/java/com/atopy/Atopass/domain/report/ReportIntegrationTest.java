package com.atopy.Atopass.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.domain.report.entity.Report;
import com.atopy.Atopass.domain.report.repository.ReportRepository;
import com.atopy.Atopass.domain.report.service.ReportFileCleaner;
import com.atopy.Atopass.domain.report.storage.ReportFileStorage;
import com.atopy.Atopass.support.ApiTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ReportIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final LocalDate START = LocalDate.of(2026, 3, 1);

	@Autowired
	ReportRepository reportRepository;
	@Autowired
	ReportFileStorage fileStorage;
	@Autowired
	ReportFileCleaner cleaner;

	@Test
	void 보고서를_만들면_201과_보고서_정보를_준다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, "저녁에 팔이 가려웠음");

		mockMvc.perform(createReport(bearer, START, START.plusDays(30), true))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.reportId").isNumber())
			.andExpect(jsonPath("$.data.startDate").value("2026-03-01"))
			.andExpect(jsonPath("$.data.endDate").value("2026-03-31"))
			.andExpect(jsonPath("$.data.includePhotos").value(true))
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty());
	}

	@Test
	void 사진_포함_여부를_생략하면_포함하지_않는다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, null);

		mockMvc.perform(withJson(post("/api/v1/reports").header(AUTH, bearer),
				Map.of("startDate", "2026-03-01", "endDate", "2026-03-31")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.includePhotos").value(false));
	}

	@Test
	void PDF를_inline으로_내려주고_한글_내용이_들어_있다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, "저녁에 팔이 가려웠음");
		createRecord(bearer, START.plusDays(1), 7, null);
		long reportId = createReportAndGetId(bearer, START, START.plusDays(30));

		MvcResult result = mockMvc.perform(get("/api/v1/reports/" + reportId + "/file").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
		assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
			.startsWith("inline")
			.contains("atopass-report-2026-03-01_2026-03-31.pdf");
		assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

		byte[] pdf = result.getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		try (PDDocument document = PDDocument.load(pdf)) {
			String text = new PDFTextStripper().getText(document);
			assertThat(text).contains("진료 참고용 피부 기록").contains("가려움").contains("저녁에 팔이 가려웠음");
		}
	}

	@Test
	void 선택한_기간_밖의_기록은_보고서에_넣지_않는다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, "기간 안의 메모");
		createRecord(bearer, START.plusDays(40), 9, "기간 밖의 메모");
		long reportId = createReportAndGetId(bearer, START, START.plusDays(10));

		String text = pdfText(bearer, reportId);

		assertThat(text).contains("기간 안의 메모").doesNotContain("기간 밖의 메모");
	}

	@Test
	void 다른_회원의_기록은_보고서에_넣지_않는다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, "내 메모");
		createRecord(newMemberBearer(), START.plusDays(1), 9, "다른 사람의 비밀 메모");
		long reportId = createReportAndGetId(bearer, START, START.plusDays(10));

		assertThat(pdfText(bearer, reportId)).contains("내 메모").doesNotContain("다른 사람의 비밀 메모");
	}

	@Test
	void 메모의_특수문자가_있어도_PDF가_만들어진다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, "<b>굵게</b> & \"따옴표\"\n둘째 줄");
		long reportId = createReportAndGetId(bearer, START, START.plusDays(10));

		assertThat(pdfText(bearer, reportId)).contains("<b>굵게</b>").contains("& \"따옴표\"");
	}

	@Test
	void 기간에_기록이_없으면_400_NO_RECORDS_IN_PERIOD이다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, null);

		mockMvc.perform(createReport(bearer, START.plusDays(100), START.plusDays(130), false))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("NO_RECORDS_IN_PERIOD"));
	}

	@Test
	void 기간이_잘못되면_400이다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, null);

		// 시작일이 종료일보다 늦음
		mockMvc.perform(createReport(bearer, START.plusDays(10), START, false))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		// 366일 초과
		mockMvc.perform(createReport(bearer, START, START.plusDays(366), false))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
		// 정확히 366일은 허용
		mockMvc.perform(createReport(bearer, START, START.plusDays(365), false))
			.andExpect(status().isCreated());
		// 날짜 누락
		mockMvc.perform(withJson(post("/api/v1/reports").header(AUTH, bearer), Map.of("startDate", "2026-03-01")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.details[?(@.field=='endDate')]").exists());
	}

	@Test
	void 토큰이_없으면_401이다() throws Exception {
		mockMvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-31\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/reports/1/file")).andExpect(status().isUnauthorized());
	}

	@Test
	void 다른_회원의_보고서_파일은_404이다() throws Exception {
		String owner = newMemberBearer();
		createRecord(owner, START, 4, null);
		long reportId = createReportAndGetId(owner, START, START.plusDays(10));

		mockMvc.perform(get("/api/v1/reports/" + reportId + "/file").header(AUTH, newMemberBearer()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/reports/99999999/file").header(AUTH, owner))
			.andExpect(status().isNotFound());
	}

	@Test
	void 저장된_파일이_사라져도_다시_내려받으면_새로_만든다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, "다시 만들기 확인");
		long reportId = createReportAndGetId(bearer, START, START.plusDays(10));
		Report before = reportRepository.findById(reportId).orElseThrow();
		fileStorage.delete(before.getFileName());
		assertThat(fileStorage.load(before.getFileName())).isEmpty();

		assertThat(pdfText(bearer, reportId)).contains("다시 만들기 확인");

		Report after = reportRepository.findById(reportId).orElseThrow();
		assertThat(after.getFileName()).isNotEqualTo(before.getFileName());
		assertThat(fileStorage.load(after.getFileName())).isPresent();
	}

	@Test
	void 보존_기간이_지난_파일만_삭제하고_보고서_정보는_남긴다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, "보존 기간 확인");
		long fresh = createReportAndGetId(bearer, START, START.plusDays(10));
		long old = createReportAndGetId(bearer, START, START.plusDays(5));
		Report oldReport = reportRepository.findById(old).orElseThrow();
		String oldFile = oldReport.getFileName();
		oldReport.attachFile(oldFile, LocalDateTime.now().minusDays(1)); // 보존 기간이 지난 것으로 만든다.
		reportRepository.save(oldReport);

		cleaner.deleteExpiredFiles();

		assertThat(fileStorage.load(oldFile)).isEmpty();
		assertThat(reportRepository.findById(old)).isPresent();
		assertThat(reportRepository.findById(old).orElseThrow().getFileName()).isNull();
		assertThat(fileStorage.load(reportRepository.findById(fresh).orElseThrow().getFileName())).isPresent();

		// 삭제된 뒤에도 다시 내려받을 수 있다.
		assertThat(pdfText(bearer, old)).contains("보존 기간 확인");
	}

	@Test
	void 사진_포함을_요청했지만_사진이_없으면_없다고_표시한다() throws Exception {
		String bearer = newMemberBearer();
		createRecord(bearer, START, 4, null);
		MvcResult created = mockMvc.perform(createReport(bearer, START, START.plusDays(10), true))
			.andExpect(status().isCreated()).andReturn();
		long reportId = jsonMapper.readTree(created.getResponse().getContentAsString())
			.get("data").get("reportId").asLong();

		assertThat(pdfText(bearer, reportId)).contains("이 기간에 등록된 사진이 없습니다.");
	}

	// ── 헬퍼 ──

	private MockHttpServletRequestBuilder createReport(String bearer, LocalDate start, LocalDate end,
		boolean includePhotos) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startDate", start.toString());
		body.put("endDate", end.toString());
		body.put("includePhotos", includePhotos);
		return withJson(post("/api/v1/reports").header(AUTH, bearer), body);
	}

	private long createReportAndGetId(String bearer, LocalDate start, LocalDate end) throws Exception {
		String body = mockMvc.perform(createReport(bearer, start, end, false))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body).get("data").get("reportId").asLong();
	}

	private String pdfText(String bearer, long reportId) throws Exception {
		byte[] pdf = mockMvc.perform(get("/api/v1/reports/" + reportId + "/file").header(AUTH, bearer))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		try (PDDocument document = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	private void createRecord(String bearer, LocalDate date, int itching, String notes) throws Exception {
		Map<String, Object> condition = new LinkedHashMap<>();
		condition.put("bodyPart", "ARM");
		condition.put("symptoms", Map.of("itching", itching));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date.toString());
		body.put("skinConditions", List.of(condition));
		body.put("sleepHours", 6);
		if (notes != null) {
			body.put("notes", notes);
		}
		mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated());
	}

}
