package com.atopy.Atopass.domain.record.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기록 하나에 속한 신체 부위별 증상 */
@Getter
@Entity
@Table(name = "skin_conditions", uniqueConstraints = @UniqueConstraint(
	name = "uk_skin_conditions_record_part", columnNames = {"record_id", "body_part"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinCondition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "record_id", nullable = false)
	private SkinRecord record;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private BodyPart bodyPart;

	@OneToMany(mappedBy = "skinCondition", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SymptomScore> scores = new ArrayList<>();

	SkinCondition(SkinRecord record, BodyPart bodyPart, Map<Symptom, Integer> scores) {
		this.record = record;
		this.bodyPart = bodyPart;
		replaceScores(scores);
	}

	public List<SymptomScore> getScores() {
		return Collections.unmodifiableList(scores);
	}

	/**
	 * 증상 점수를 통째로 교체한다. 같은 증상은 값만 바꾸고, 빠진 증상은 지우고, 새 증상은 추가한다.
	 * (지우고 다시 넣으면 unique 제약에 걸릴 수 있어 제자리에서 갱신한다.)
	 */
	void replaceScores(Map<Symptom, Integer> incoming) {
		scores.removeIf(s -> !incoming.containsKey(s.getSymptom()));
		incoming.forEach((symptom, value) -> {
			Optional<SymptomScore> existing = scores.stream()
				.filter(s -> s.getSymptom() == symptom).findFirst();
			if (existing.isPresent()) {
				existing.get().changeScore(value);
			} else {
				scores.add(new SymptomScore(this, symptom, value));
			}
		});
	}

}
