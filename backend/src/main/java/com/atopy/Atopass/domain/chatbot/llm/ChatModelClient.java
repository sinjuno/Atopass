package com.atopy.Atopass.domain.chatbot.llm;

import java.util.List;

/**
 * 챗봇이 사용하는 LLM 호출 창구. 구현체를 바꾸거나 테스트에서 가짜로 교체할 수 있다.
 */
public interface ChatModelClient {

	/**
	 * 시스템 프롬프트와 대화 내역(사용자 턴으로 시작해 사용자 턴으로 끝남)을 보내 응답 텍스트를 받는다.
	 *
	 * @throws com.atopy.Atopass.global.exception.BusinessException LLM을 사용할 수 없을 때(CHATBOT_UNAVAILABLE)
	 */
	String complete(String systemPrompt, List<ChatTurn> messages);

	/**
	 * 사진과 안내 글을 함께 보내 응답 텍스트를 받는다. (약 봉투 사진 읽기에 쓴다)
	 * 사진을 받지 못하는 구현체는 이 메서드를 재정의하지 않으면 LLM을 쓸 수 없는 것으로 본다.
	 *
	 * @throws com.atopy.Atopass.global.exception.BusinessException LLM을 사용할 수 없을 때(CHATBOT_UNAVAILABLE)
	 */
	default String completeWithImages(String systemPrompt, String userText, List<LlmImage> images) {
		throw new com.atopy.Atopass.global.exception.BusinessException(
			com.atopy.Atopass.global.exception.ErrorCode.CHATBOT_UNAVAILABLE);
	}

}
