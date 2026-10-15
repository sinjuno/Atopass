package com.atopy.Atopass.domain.chatbot.dto.response;

import com.atopy.Atopass.domain.chatbot.entity.ChatMessage;
import com.atopy.Atopass.domain.chatbot.entity.ChatRole;
import java.time.LocalDateTime;
import java.util.List;

public record ChatMessagesResponse(List<Message> messages) {

	public record Message(ChatRole role, String content, LocalDateTime createdAt) {

		public static Message from(ChatMessage message) {
			return new Message(message.getRole(), message.getContent(), message.getCreatedAt());
		}

	}

	public static ChatMessagesResponse from(List<ChatMessage> messages) {
		return new ChatMessagesResponse(messages.stream().map(Message::from).toList());
	}

}
