package com.atopy.Atopass.domain.record.entity;

import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.global.entity.BaseTimeEntity;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 한 건의 피부 기록. 기록하지 않은 값(수면, 음주, 야외활동, 메모)은 null이다.
 * 이름이 java.lang.Record와 겹치지 않도록 SkinRecord로 둔다.
 */
@Getter
@Entity
@Table(name = "records", uniqueConstraints = @UniqueConstraint(
	name = "uk_records_member_date", columnNames = {"member_id", "record_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinRecord extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false)
	private LocalDate recordDate;

	@Column(precision = 3, scale = 1)
	private BigDecimal sleepHours;

	private Boolean alcohol;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private OutdoorActivity outdoorActivity;

	@Column(columnDefinition = "text")
	private String notes;

	@OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SkinCondition> conditions = new ArrayList<>();

	@Builder
	private SkinRecord(Member member, LocalDate recordDate, BigDecimal sleepHours, Boolean alcohol,
		OutdoorActivity outdoorActivity, String notes) {
		this.member = member;
		this.recordDate = recordDate;
		this.sleepHours = sleepHours;
		this.alcohol = alcohol;
		this.outdoorActivity = outdoorActivity;
		this.notes = notes;
	}

	public List<SkinCondition> getConditions() {
		return Collections.unmodifiableList(conditions);
	}

	/** 날짜와 생활 정보를 바꾼다. (PUT은 전체 내용을 받으므로 null도 그대로 반영한다.) */
	public void update(LocalDate recordDate, BigDecimal sleepHours, Boolean alcohol,
		OutdoorActivity outdoorActivity, String notes) {
		this.recordDate = recordDate;
		this.sleepHours = sleepHours;
		this.alcohol = alcohol;
		this.outdoorActivity = outdoorActivity;
		this.notes = notes;
	}

	/**
	 * 부위별 증상을 통째로 교체한다. 같은 부위는 점수만 바꾸고, 빠진 부위는 지우고, 새 부위는 추가한다.
	 * (지우고 다시 넣으면 unique 제약에 걸릴 수 있어 제자리에서 갱신한다.)
	 */
	public void replaceConditions(Map<BodyPart, Map<Symptom, Integer>> incoming) {
		conditions.removeIf(c -> !incoming.containsKey(c.getBodyPart()));
		incoming.forEach((bodyPart, scores) -> {
			Optional<SkinCondition> existing = conditions.stream()
				.filter(c -> c.getBodyPart() == bodyPart).findFirst();
			if (existing.isPresent()) {
				existing.get().replaceScores(scores);
			} else {
				conditions.add(new SkinCondition(this, bodyPart, scores));
			}
		});
	}

}
