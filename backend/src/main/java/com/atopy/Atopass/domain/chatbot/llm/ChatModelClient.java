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

}
