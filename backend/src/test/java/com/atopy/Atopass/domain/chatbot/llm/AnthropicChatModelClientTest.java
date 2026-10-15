package com.atopy.Atopass.domain.chatbot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atopy.Atopass.domain.chatbot.entity.ChatRole;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 실제 Claude API는 호출하지 않는다. API 키가 없을 때의 동작만 확인한다. */
class AnthropicChatModelClientTest {

	@Test
	void API_키가_없으면_네트워크를_타지_않고_CHATBOT_UNAVAILABLE을_던진다() {
		AnthropicChatModelClient client = new AnthropicChatModelClient(
			new LlmProperties("", "claude-opus-5-5", "low", 2048, 30, 100));

		assertThatThrownBy(() -> client.complete("시스템", List.of(new ChatTurn(ChatRole.USER, "안녕"))))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHATBOT_UNAVAILABLE));
	}

	@Test
	void API_키가_null이어도_CHATBOT_UNAVAILABLE을_던진다() {
		AnthropicChatModelClient client = new AnthropicChatModelClient(
			new LlmProperties(null, "claude-opus-5-5", "low", 2048, 30, 100));

		assertThatThrownBy(() -> client.complete("시스템", List.of(new ChatTurn(ChatRole.USER, "안녕"))))
			.isInstanceOf(BusinessException.class);
	}

}
