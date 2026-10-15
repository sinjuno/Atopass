package com.atopy.Atopass.global;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atopy.Atopass.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 프론트(React) 빌드 결과를 서버가 함께 내려주는 설정. 테스트 리소스의 static/index.html을 빌드 결과 대신 쓴다.
 * 화면 주소는 index.html로 받고, /api 경로는 기존처럼 JSON으로 응답해야 한다.
 */
class SpaServingIntegrationTest extends ApiTestSupport {

	@Test
	void 루트와_화면_주소는_로그인_없이_index_html을_준다() throws Exception {
		// 루트는 시작 페이지 기능이 /index.html로 포워딩한다. (MockMvc는 포워딩을 따라가지 않아 대상만 확인한다)
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(forwardedUrl("index.html"));
		mockMvc.perform(get("/index.html"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
			.andExpect(content().string(containsString("SPA-INDEX")));

		// 파일이 없는 화면 주소(새로고침으로 바로 들어오는 경로)도 index.html
		mockMvc.perform(get("/calendar"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("SPA-INDEX")));
		mockMvc.perform(get("/record/2026-10-15"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("SPA-INDEX")));
	}

	@Test
	void index_html은_캐시하지_않고_assets는_오래_캐시한다() throws Exception {
		mockMvc.perform(get("/index.html"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-cache")));

		mockMvc.perform(get("/assets/app.js"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")));
	}

	@Test
	void 없는_assets_파일은_index_html이_아니라_404이다() throws Exception {
		mockMvc.perform(get("/assets/없는파일.js"))
			.andExpect(status().isNotFound());
	}

	@Test
	void api_경로는_화면으로_빠지지_않고_기존처럼_JSON_오류를_준다() throws Exception {
		// 인증이 필요한 API: 토큰이 없으면 401 JSON
		mockMvc.perform(get("/api/v1/records"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

		// 없는 API 경로: 로그인한 사용자에게는 404 JSON (index.html이 아님)
		String bearer = newMemberBearer();
		mockMvc.perform(get("/api/v1/없는경로").header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

}
