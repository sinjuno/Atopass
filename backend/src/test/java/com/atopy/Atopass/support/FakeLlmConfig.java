package com.atopy.Atopass.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 실제 Claude API 대신 가짜 LLM을 쓰게 하는 테스트 설정. 테스트 클래스에서 @Import 한다. */
@TestConfiguration
public class FakeLlmConfig {

	@Bean
	@Primary
	public FakeChatModelClient fakeChatModelClient() {
		return new FakeChatModelClient();
	}

}
