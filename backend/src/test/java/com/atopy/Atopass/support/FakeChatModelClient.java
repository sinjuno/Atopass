package com.atopy.Atopass.support;

import com.atopy.Atopass.domain.chatbot.llm.ChatModelClient;
import com.atopy.Atopass.domain.chatbot.llm.ChatTurn;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 테스트용 가짜 LLM. 미리 넣어 둔 응답을 순서대로 돌려주고, 마지막으로 받은 프롬프트와 대화를 기억한다.
 * 실제 API를 호출하지 않으므로 비용이 들지 않고 결과가 항상 같다.
 */
public class FakeChatModelClient implements ChatModelClient {

	private static final String DEFAULT_RESPONSE = "{\"understood\": true, \"reply\": \"네, 알겠어요.\", \"updates\": {}}";

	private final Deque<Object> responses = new ArrayDeque<>();
	private String lastSystemPrompt;
	private List<ChatTurn> lastMessages;
	private int callCount;

	public synchronized void reset() {
		responses.clear();
		lastSystemPrompt = null;
		lastMessages = null;
		callCount = 0;
	}

	/** 다음 호출에서 돌려줄 응답 텍스트 */
	public synchronized void enqueue(String response) {
		responses.add(response);
	}

	/** 다음 호출에서 LLM 장애(CHATBOT_UNAVAILABLE)가 나도록 한다. */
	public synchronized void enqueueFailure() {
		responses.add(new BusinessException(ErrorCode.CHATBOT_UNAVAILABLE));
	}

	@Override
	public synchronized String complete(String systemPrompt, List<ChatTurn> messages) {
		callCount++;
		lastSystemPrompt = systemPrompt;
		lastMessages = List.copyOf(messages);
		Object next = responses.isEmpty() ? DEFAULT_RESPONSE : responses.poll();
		if (next instanceof BusinessException e) {
			throw e;
		}
		return (String) next;
	}

	public synchronized String lastSystemPrompt() {
		return lastSystemPrompt;
	}

	public synchronized List<ChatTurn> lastMessages() {
		return lastMessages;
	}

	public synchronized int callCount() {
		return callCount;
	}

}
