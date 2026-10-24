package com.atopy.Atopass.domain.record.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 챗봇 마무리 질문(C02)에서 "진료 때 물어볼 것"으로 남긴 재진 질문. 쌓이는 목록이라 고치거나 지우지 않는다. */
@Getter
@Entity
@Table(name = "clinic_question_notes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicQuestionNote extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	public static ClinicQuestionNote create(Member member, LocalDate recordDate, String content) {
		ClinicQuestionNote note = new ClinicQuestionNote();
		note.member = member;
		note.recordDate = recordDate;
		note.content = content;
		return note;
	}

}
