package com.atopy.Atopass.domain.chatbot.entity;

import com.atopy.Atopass.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대화 메시지. 생성 30일 뒤 삭제한다. */
@Getter
@Entity
@Table(name = "chatbot_messages",
	indexes = @Index(name = "idx_chatbot_messages_session_created", columnList = "session_id, created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private ChatSession session;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ChatRole role;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	public static ChatMessage of(ChatSession session, ChatRole role, String content) {
		ChatMessage message = new ChatMessage();
		message.session = session;
		message.role = role;
		message.content = content;
		return message;
	}

}
