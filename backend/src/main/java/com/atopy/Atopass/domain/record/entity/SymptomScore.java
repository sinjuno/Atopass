package com.atopy.Atopass.domain.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 부위별 증상 하나의 점수(0~10). 기록하지 않은 증상은 행을 만들지 않는다. */
@Getter
@Entity
@Table(name = "symptom_scores", uniqueConstraints = @UniqueConstraint(
	name = "uk_symptom_scores_condition_symptom", columnNames = {"skin_condition_id", "symptom"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SymptomScore {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "skin_condition_id", nullable = false)
	private SkinCondition skinCondition;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Symptom symptom;

	@Column(nullable = false)
	private int score;

	SymptomScore(SkinCondition skinCondition, Symptom symptom, int score) {
		this.skinCondition = skinCondition;
		this.symptom = symptom;
		this.score = score;
	}

	void changeScore(int score) {
		this.score = score;
	}

}
