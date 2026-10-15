package com.atopy.Skinlog.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Skinlog.domain.record.entity.Photo;
import com.atopy.Skinlog.domain.record.repository.PhotoRepository;
import com.atopy.Skinlog.domain.record.storage.PhotoStorage;
import com.atopy.Skinlog.domain.report.service.ReportPhoto;
import com.atopy.Skinlog.domain.report.service.ReportPhotoSource;
import com.atopy.Skinlog.support.ApiTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

class ReportPhotoIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;
	private static final String DATE = "2026-03-05";

	@Autowired
	ReportPhotoSource photoSource;
	@Autowired
	PhotoRepository photoRepository;
	@Autowired
	PhotoStorage photoStorage;

	@Test
	void 업로드한_사진을_보고서용으로_읽어온다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer);
		byte[] png = pngBytes();
		uploadPhoto(recordId, bearer, png);

		List<ReportPhoto> photos = photoSource.photosOf(recordId);

		assertThat(photos).hasSize(1);
		assertThat(photos.get(0).contentType()).isEqualTo("image/png");
		assertThat(photos.get(0).data()).isEqualTo(png);
	}

	@Test
	void 파일이_사라진_사진은_건너뛴다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer);
		uploadPhoto(recordId, bearer, pngBytes());
		Photo photo = photoRepository.findByRecordIdOrderByCreatedAtAsc(recordId).get(0);
		photoStorage.delete(photo.getStorageKey());

		assertThat(photoSource.photosOf(recordId)).isEmpty();
	}

	@Test
	void 사진을_포함해_보고서를_만들면_PDF에_사진이_들어간다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer);
		uploadPhoto(recordId, bearer, pngBytes());

		long reportId = createReport(bearer, true);

		String text = pdfText(bearer, reportId);
		assertThat(text).doesNotContain("이 기간에 등록된 사진이 없습니다.");
		assertThat(text).contains("2026.03.05");
	}

	@Test
	void 사진을_포함하지_않으면_사진이_있어도_넣지_않는다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer);
		uploadPhoto(recordId, bearer, pngBytes());

		long reportId = createReport(bearer, false);

		assertThat(pdfText(bearer, reportId)).doesNotContain("이 기간에 등록된 사진이 없습니다.");
	}

	// ── 헬퍼 ──

	private byte[] pngBytes() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}

	private void uploadPhoto(long recordId, String bearer, byte[] content) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "skin.png", null, content);
		mockMvc.perform(multipart("/api/v1/records/" + recordId + "/photos").file(file).header(AUTH, bearer))
			.andExpect(status().isCreated());
	}

	private long createRecord(String bearer) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", DATE);
		body.put("skinConditions", List.of(Map.of("bodyPart", "ARM", "symptoms", Map.of("itching", 4))));
		String response = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("recordId").asLong();
	}

	private long createReport(String bearer, boolean includePhotos) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("startDate", "2026-03-01");
		body.put("endDate", "2026-03-31");
		body.put("includePhotos", includePhotos);
		String response = mockMvc.perform(withJson(post("/api/v1/reports").header(AUTH, bearer), body))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("reportId").asLong();
	}

	private String pdfText(String bearer, long reportId) throws Exception {
		byte[] pdf = mockMvc.perform(get("/api/v1/reports/" + reportId + "/file").header(AUTH, bearer))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		try (PDDocument document = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

}
