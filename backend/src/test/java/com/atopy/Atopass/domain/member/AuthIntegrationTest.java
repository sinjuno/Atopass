package com.atopy.Atopass.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

	private static final String PASSWORD = "password1234";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JsonMapper jsonMapper;
	@Autowired
	JwtEncoder jwtEncoder;

	@Test
	void 회원가입에_성공한다() throws Exception {
		signup("signup-ok@example.com")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.email").value("signup-ok@example.com"))
			.andExpect(jsonPath("$.data.memberId").isNumber())
			.andExpect(jsonPath("$.data.password").doesNotExist());
	}

	@Test
	void 이미_가입된_이메일이면_409를_반환한다() throws Exception {
		signup("dup@example.com").andExpect(status().isCreated());

		signup("DUP@example.com")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
	}

	@Test
	void 입력값이_잘못되면_400과_필드별_오류를_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("email", "not-an-email", "password", "short", "nickname", ""))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[?(@.field=='email')]").exists())
			.andExpect(jsonPath("$.error.details[?(@.field=='password')]").exists())
			.andExpect(jsonPath("$.error.details[?(@.field=='nickname')]").exists());
	}

	@Test
	void 로그인하면_액세스_토큰은_본문으로_리프레시_토큰은_보안_쿠키로_받는다() throws Exception {
		signup("login-ok@example.com");

		MockHttpServletResponse response = login("login-ok@example.com", PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.data.expiresIn").value(1800))
			.andReturn().getResponse();

		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).startsWith("refreshToken=");
		assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");
	}

	@Test
	void 비밀번호가_틀리거나_없는_계정이면_같은_401을_반환한다() throws Exception {
		signup("login-fail@example.com");

		login("login-fail@example.com", "wrong-password")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
		login("nobody@example.com", PASSWORD)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void 토큰_없이_보호된_API를_호출하면_공통_형식의_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
	}

	@Test
	void 만료된_액세스_토큰이면_TOKEN_EXPIRED를_반환한다() throws Exception {
		Instant past = Instant.now().minusSeconds(3600);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("1").issuedAt(past.minusSeconds(60)).expiresAt(past).build();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(
			JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

		mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void 리프레시_쿠키로_새_액세스_토큰을_발급받는다() throws Exception {
		signup("refresh-ok@example.com");
		Cookie refreshCookie = refreshCookieOf(login("refresh-ok@example.com", PASSWORD));

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty());
	}

	@Test
	void 리프레시_쿠키가_없거나_엉뚱한_값이면_401을_반환한다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
		mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", "fake-token")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void 로그아웃하면_리프레시_토큰이_폐기되고_쿠키가_만료된다() throws Exception {
		signup("logout-ok@example.com");
		ResultActions loginResult = login("logout-ok@example.com", PASSWORD);
		Cookie refreshCookie = refreshCookieOf(loginResult);
		String accessToken = accessTokenOf(loginResult);

		MockHttpServletResponse logout = mockMvc.perform(post("/api/v1/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.cookie(refreshCookie))
			.andExpect(status().isNoContent())
			.andReturn().getResponse();
		assertThat(logout.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
	}

	@Test
	void 다른_회원의_리프레시_토큰은_로그아웃으로_폐기할_수_없다() throws Exception {
		signup("owner@example.com");
		signup("attacker@example.com");
		Cookie ownerCookie = refreshCookieOf(login("owner@example.com", PASSWORD));
		String attackerAccess = accessTokenOf(login("attacker@example.com", PASSWORD));

		mockMvc.perform(post("/api/v1/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + attackerAccess)
				.cookie(ownerCookie))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(ownerCookie))
			.andExpect(status().isOk());
	}

	@Test
	void 없는_경로는_공통_형식의_404를_반환한다() throws Exception {
		signup("notfound@example.com");
		String accessToken = accessTokenOf(login("notfound@example.com", PASSWORD));

		mockMvc.perform(get("/api/v1/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}

	private ResultActions signup(String email) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(Map.of("email", email, "password", PASSWORD, "nickname", "사용자"))));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(Map.of("email", email, "password", password))));
	}

	private Cookie refreshCookieOf(ResultActions loginResult) {
		Cookie cookie = loginResult.andReturn().getResponse().getCookie("refreshToken");
		assertThat(cookie).isNotNull();
		return new Cookie("refreshToken", cookie.getValue());
	}

	private String accessTokenOf(ResultActions loginResult) throws Exception {
		JsonNode body = jsonMapper.readTree(loginResult.andReturn().getResponse().getContentAsString());
		return body.get("data").get("accessToken").asString();
	}

	private String json(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

}
