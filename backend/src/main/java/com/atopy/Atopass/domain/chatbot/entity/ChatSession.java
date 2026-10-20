package com.atopy.Atopass.domain.chatbot.entity;

import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챗봇 대화 세션. 날짜 하나당 하나이며, 기록을 저장(확정)하면 닫힌다.
 * draft에는 확정 전 기록 초안(JSON)을 보관한다.
 * 그날 기록이 이미 있으면 그 기록을 초안으로 불러온 수정 세션(editingRecordId)으로 연다.
 */
@Getter
@Entity
@Table(name = "chatbot_sessions",
	indexes = @Index(name = "idx_chatbot_sessions_member_date", columnList = "member_id, record_date"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseTimeEntity {

	@Id
	@Column(length = 36)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false)
	private LocalDate recordDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ChatMode mode;

	/** 확정 전 기록 초안(JSON). 질의 모드에서는 비어 있다. */
	@Column(columnDefinition = "text")
	private String draft;

	/** 직접 입력을 연속으로 해석하지 못한 횟수. 해석에 성공하면 0으로 돌아간다. */
	@Column(nullable = false)
	private int failCount;

	/** 마지막 응답에서 기록 초안 확인(총정리) 단계로 넘어가도록 알렸는지. 새로고침 뒤에도 같은 값을 주기 위해 저장한다. */
	@Column(nullable = false)
	private boolean confirmationReady;

	/** 기록을 저장해 더 이상 대화할 수 없으면 true */
	@Column(nullable = false)
	private boolean closed;

	/** 이미 저장된 그날 기록을 고치는 대화이면 그 기록의 ID. 저장하면 새로 만들지 않고 이 기록을 수정한다. */
	private Long editingRecordId;

	public static ChatSession open(Member member, LocalDate recordDate, ChatMode mode) {
		ChatSession session = new ChatSession();
		session.id = UUID.randomUUID().toString();
		session.member = member;
		session.recordDate = recordDate;
		session.mode = mode;
		return session;
	}

	/** 이미 저장된 기록을 초안으로 불러와 고치는 세션 */
	public static ChatSession openForEdit(Member member, LocalDate recordDate, Long recordId, String draft) {
		ChatSession session = open(member, recordDate, ChatMode.RECORD);
		session.editingRecordId = recordId;
		session.draft = draft;
		return session;
	}

	public void updateTurn(String draft, int failCount, boolean confirmationReady) {
		this.draft = draft;
		this.failCount = failCount;
		this.confirmationReady = confirmationReady;
	}

	public void close() {
		this.closed = true;
	}

	/** 저장 뒤에도 그날 대화를 이어 가도록 다시 연다. */
	public void reopen() {
		this.closed = false;
	}

	/** 이 대화로 만든(또는 고치는) 기록. 이후 저장은 이 기록을 수정한다. */
	public void startEditing(Long recordId) {
		this.editingRecordId = recordId;
	}

}
