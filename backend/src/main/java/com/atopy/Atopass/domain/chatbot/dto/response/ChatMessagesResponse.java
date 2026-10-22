package com.atopy.Atopass.domain.chatbot.dto.response;

import com.atopy.Atopass.domain.chatbot.entity.ChatMessage;
import com.atopy.Atopass.domain.chatbot.entity.ChatRole;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.time.LocalDateTime;
import java.util.List;

public record ChatMessagesResponse(List<Message> messages) {

	/** @param imageSign 이 말과 함께 보여 준 증상 예시 사진. 없으면 null */
	public record Message(ChatRole role, String content, LocalDateTime createdAt, SkinSign imageSign) {

		public static Message from(ChatMessage message) {
			return new Message(message.getRole(), message.getContent(), message.getCreatedAt(), message.getImageSign());
		}

	}

	public static ChatMessagesResponse from(List<ChatMessage> messages) {
		return new ChatMessagesResponse(messages.stream().map(Message::from).toList());
	}

}
