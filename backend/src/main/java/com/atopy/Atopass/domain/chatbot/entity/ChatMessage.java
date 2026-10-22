package com.atopy.Atopass.domain.chatbot.entity;

import com.atopy.Atopass.domain.record.entity.SkinSign;
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
import org.hibernate.annotations.ColumnDefault;

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

	/** 선택지 버튼으로 보낸 메시지. LLM을 부르지 않으므로 이용 한도에 세지 않는다. */
	@ColumnDefault("false")
	@Column(name = "via_choice", nullable = false)
	private boolean viaChoice;

	/** 챗봇이 이 말과 함께 보여 준 증상 예시 사진. 사용자가 보여 달라고 했을 때만 있다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "image_sign", length = 30)
	private SkinSign imageSign;

	public static ChatMessage of(ChatSession session, ChatRole role, String content) {
		return of(session, role, content, false);
	}

	public static ChatMessage of(ChatSession session, ChatRole role, String content, boolean viaChoice) {
		ChatMessage message = new ChatMessage();
		message.session = session;
		message.role = role;
		message.content = content;
		message.viaChoice = viaChoice;
		return message;
	}

	/** 증상 예시 사진을 함께 보여 주는 챗봇의 말 */
	public static ChatMessage withImage(ChatSession session, String content, SkinSign imageSign) {
		ChatMessage message = of(session, ChatRole.ASSISTANT, content);
		message.imageSign = imageSign;
		return message;
	}

}
