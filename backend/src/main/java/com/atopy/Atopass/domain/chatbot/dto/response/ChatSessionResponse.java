package com.atopy.Atopass.domain.chatbot.dto.response;

import com.atopy.Atopass.domain.chatbot.entity.ChatMode;
import com.atopy.Atopass.domain.chatbot.entity.ChatSession;

public record ChatSessionResponse(String sessionId, ChatMode mode) {

	public static ChatSessionResponse from(ChatSession session) {
		return new ChatSessionResponse(session.getId(), session.getMode());
	}

}
