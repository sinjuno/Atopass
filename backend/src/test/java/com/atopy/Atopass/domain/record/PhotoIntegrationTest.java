package com.atopy.Atopass.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.domain.record.repository.PhotoRepository;
import com.atopy.Atopass.support.ApiTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

class PhotoIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;

	@Autowired
	PhotoRepository photoRepository;
	private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
	private static final byte[] PNG_BYTES =
		{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

	@Test
	void 사진을_업로드하고_조회한다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer, "2026-10-01");

		String response = mockMvc.perform(photoUpload(recordId, bearer, JPEG_BYTES, "photo.jpg"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.photoId").isNumber())
			.andReturn().getResponse().getContentAsString();
		long photoId = jsonMapper.readTree(response).get("data").get("photoId").asLong();

		MvcResult result = mockMvc.perform(get(photoPath(recordId, photoId)).header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_JPEG))
			.andReturn();
		assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(JPEG_BYTES);
	}

	@Test
	void PNG도_업로드할_수_있다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer, "2026-10-02");

		mockMvc.perform(photoUpload(recordId, bearer, PNG_BYTES, "photo.png"))
			.andExpect(status().isCreated());
	}

	@Test
	void 매직바이트가_이미지가_아니면_400을_반환한다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer, "2026-10-03");

		mockMvc.perform(photoUpload(recordId, bearer, "not-an-image".getBytes(), "fake.jpg"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
	}

	@Test
	void 기록당_5장을_초과하면_409를_반환한다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer, "2026-10-04");

		for (int i = 0; i < 5; i++) {
			mockMvc.perform(photoUpload(recordId, bearer, JPEG_BYTES, "photo" + i + ".jpg"))
				.andExpect(status().isCreated());
		}

		mockMvc.perform(photoUpload(recordId, bearer, JPEG_BYTES, "photo6.jpg"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("PHOTO_LIMIT_EXCEEDED"));
	}

	@Test
	void 다른_회원의_기록에는_업로드_조회_삭제할_수_없다() throws Exception {
		String owner = newMemberBearer();
		long recordId = createRecord(owner, "2026-10-05");
		long photoId = uploadPhoto(recordId, owner);
		String other = newMemberBearer();

		mockMvc.perform(photoUpload(recordId, other, JPEG_BYTES, "intruder.jpg"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
		mockMvc.perform(get(photoPath(recordId, photoId)).header(AUTH, other))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
		mockMvc.perform(delete(photoPath(recordId, photoId)).header(AUTH, other))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
	}

	@Test
	void 사진을_삭제하면_다시_조회되지_않는다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer, "2026-10-06");
		long photoId = uploadPhoto(recordId, bearer);

		mockMvc.perform(delete(photoPath(recordId, photoId)).header(AUTH, bearer))
			.andExpect(status().isNoContent());
		mockMvc.perform(get(photoPath(recordId, photoId)).header(AUTH, bearer))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RECORD_NOT_FOUND"));
	}

	@Test
	void 기록을_삭제하면_사진_행도_함께_삭제된다() throws Exception {
		String bearer = newMemberBearer();
		long recordId = createRecord(bearer, "2026-10-07");
		long photoId = uploadPhoto(recordId, bearer);

		mockMvc.perform(delete("/api/v1/records/" + recordId).header(AUTH, bearer))
			.andExpect(status().isNoContent());

		assertThat(photoRepository.existsById(photoId)).isFalse();
	}

	private long uploadPhoto(long recordId, String bearer) throws Exception {
		String response = mockMvc.perform(photoUpload(recordId, bearer, JPEG_BYTES, "photo.jpg"))
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("photoId").asLong();
	}

	private MockMultipartHttpServletRequestBuilder photoUpload(
		long recordId, String bearer, byte[] content, String filename) {
		MockMultipartFile file = new MockMultipartFile("file", filename, null, content);
		return multipart("/api/v1/records/" + recordId + "/photos").file(file).header(AUTH, bearer);
	}

	private String photoPath(long recordId, long photoId) {
		return "/api/v1/records/" + recordId + "/photos/" + photoId;
	}

	private long createRecord(String bearer, String date) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("recordDate", date);
		body.put("skinConditions", List.of(
			Map.of("bodyPart", "ARM", "symptoms", Map.of("itching", 4))));
		String response = mockMvc.perform(withJson(post("/api/v1/records").header(AUTH, bearer), body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(response).get("data").get("recordId").asLong();
	}

}
