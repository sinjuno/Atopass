package com.atopy.Atopass.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * API 통합 테스트 공통 기반. 회원가입·로그인을 거쳐 액세스 토큰을 얻는 헬퍼를 제공한다.
 * 테스트 DB(H2)는 클래스 간에 공유되므로 이메일은 매번 새로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

	protected static final String PASSWORD = "password1234";

	@Autowired
	protected MockMvc mockMvc;
	@Autowired
	protected JsonMapper jsonMapper;

	/** 새 회원을 가입시키고 로그인해서 "Bearer ..." 형태의 Authorization 헤더 값을 돌려준다. */
	protected String newMemberBearer() throws Exception {
		return newMemberBearer("user-" + UUID.randomUUID() + "@example.com");
	}

	/** 지정한 이메일로 회원을 가입시키고 로그인해서 "Bearer ..." 형태의 Authorization 헤더 값을 돌려준다. */
	protected String newMemberBearer(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(Map.of("email", email, "password", PASSWORD, "nickname", "테스터"))));
		String body = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("email", email, "password", PASSWORD))))
			.andReturn().getResponse().getContentAsString();
		return "Bearer " + jsonMapper.readTree(body).get("data").get("accessToken").asString();
	}

	protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, Object body) {
		return builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
	}

	protected String json(Object value) {
		return jsonMapper.writeValueAsString(value);
	}

}
