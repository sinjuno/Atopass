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

/**
 * 상황 질문(P01 등)·마무리 질문(C02)의 답. 메인 기록({@code SkinRecord})과 별도로 저장한다.
 * answerJson의 모양은 questionId마다 다르다 (예: P01은 {"answer":"TODAY"}, C02는 {"productNote":"..."}).
 */
@Getter
@Entity
@Table(name = "chatbot_situational_answers", uniqueConstraints = @UniqueConstraint(
	name = "uk_situational_answer_member_date_question", columnNames = {"member_id", "record_date", "question_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SituationalAnswer extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Column(nullable = false, length = 10)
	private String questionId;

	@Column(nullable = false, columnDefinition = "text")
	private String answerJson;

	public static SituationalAnswer create(Member member, LocalDate recordDate, String questionId, String answerJson) {
		SituationalAnswer answer = new SituationalAnswer();
		answer.member = member;
		answer.recordDate = recordDate;
		answer.questionId = questionId;
		answer.answerJson = answerJson;
		return answer;
	}

	/** 같은 날 같은 질문에 다시 답하면(예: 그날 기록을 지우고 다시 기록한 경우) 새로 만들지 않고 이 답으로 바꾼다. */
	public void replaceAnswer(String answerJson) {
		this.answerJson = answerJson;
	}

}
