package com.atopy.Atopass.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.support.ApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class MemberIntegrationTest extends ApiTestSupport {

	private static final String AUTH = HttpHeaders.AUTHORIZATION;

	@Test
	void 내_정보를_조회한다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(get("/api/v1/members/me").header(AUTH, bearer))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.memberId").isNumber())
			.andExpect(jsonPath("$.data.nickname").value("테스터"));
	}

	@Test
	void 인증_없이_조회하면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/api/v1/members/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void 닉네임만_수정한다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(withJson(patch("/api/v1/members/me").header(AUTH, bearer), Map.of("nickname", "새별명")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("새별명"));

		mockMvc.perform(get("/api/v1/members/me").header(AUTH, bearer))
			.andExpect(jsonPath("$.data.nickname").value("새별명"));
	}

	@Test
	void 비밀번호를_변경한다() throws Exception {
		String email = "pw-change-" + System.nanoTime() + "@example.com";
		String bearer = newMemberBearer(email);

		mockMvc.perform(withJson(patch("/api/v1/members/me").header(AUTH, bearer),
				Map.of("password", "new-password1")))
			.andExpect(status().isOk());

		mockMvc.perform(withJson(post("/api/v1/auth/login"), Map.of("email", email, "password", "new-password1")))
			.andExpect(status().isOk());
	}

	@Test
	void 비밀번호_길이가_범위를_벗어나면_400을_반환한다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(withJson(patch("/api/v1/members/me").header(AUTH, bearer), Map.of("password", "short")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

	@Test
	void 수정할_내용이_없으면_400을_반환한다() throws Exception {
		String bearer = newMemberBearer();

		mockMvc.perform(withJson(patch("/api/v1/members/me").header(AUTH, bearer), Map.of()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
	}

}
