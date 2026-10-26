package com.atopy.Atopass.domain.chatbot.entity;

import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상황 질문(P01 등)이 회원에게 마지막으로 나온 날짜. 3일 제외 규칙에 쓴다. */
@Getter
@Entity
@Table(name = "chatbot_situational_question_logs", uniqueConstraints = @UniqueConstraint(
	name = "uk_situational_log_member_question", columnNames = {"member_id", "question_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SituationalQuestionLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false, length = 10)
	private String questionId;

	@Column(nullable = false)
	private LocalDate lastShownDate;

	public static SituationalQuestionLog create(Member member, String questionId, LocalDate shownDate) {
		SituationalQuestionLog log = new SituationalQuestionLog();
		log.member = member;
		log.questionId = questionId;
		log.lastShownDate = shownDate;
		return log;
	}

	public void markShown(LocalDate date) {
		this.lastShownDate = date;
	}

}
