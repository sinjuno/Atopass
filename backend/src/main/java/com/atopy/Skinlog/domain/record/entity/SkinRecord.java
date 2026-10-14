package com.atopy.Skinlog.domain.record.entity;

import com.atopy.Skinlog.domain.member.entity.Member;
import com.atopy.Skinlog.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하루 한 건의 피부 기록. 기록하지 않은 값(고통 지수, 수면, 약 복용, 음주, 야외활동, 메모)은 null이다.
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

	/** 고통 지수 1~10 (사용자가 느끼는 주관적인 정도) */
	private Integer painIndex;

	@Column(precision = 3, scale = 1)
	private BigDecimal sleepHours;

	/** 약 복용 여부 */
	private Boolean medication;

	private Boolean alcohol;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private OutdoorActivity outdoorActivity;

	@Column(columnDefinition = "text")
	private String notes;

	@OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SkinCondition> conditions = new ArrayList<>();

	/** 나타난 증상. 없으면 비어 있다. */
	@ElementCollection
	@CollectionTable(name = "record_skin_signs", joinColumns = @JoinColumn(name = "record_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "skin_sign", nullable = false, length = 20)
	private Set<SkinSign> skinSigns = EnumSet.noneOf(SkinSign.class);

	@Builder
	private SkinRecord(Member member, LocalDate recordDate, Integer painIndex, BigDecimal sleepHours,
		Boolean medication, Boolean alcohol, OutdoorActivity outdoorActivity, String notes) {
		this.member = member;
		this.recordDate = recordDate;
		this.painIndex = painIndex;
		this.sleepHours = sleepHours;
		this.medication = medication;
		this.alcohol = alcohol;
		this.outdoorActivity = outdoorActivity;
		this.notes = notes;
	}

	public List<SkinCondition> getConditions() {
		return Collections.unmodifiableList(conditions);
	}

	public Set<SkinSign> getSkinSigns() {
		return Collections.unmodifiableSet(skinSigns);
	}

	/** 수면 점수(1~5). 수면 시간을 기록하지 않았으면 null */
	public Integer getSleepScore() {
		return SleepScore.of(sleepHours);
	}

	/** 날짜와 생활 정보를 바꾼다. (PUT은 전체 내용을 받으므로 null도 그대로 반영한다.) */
	public void update(LocalDate recordDate, Integer painIndex, BigDecimal sleepHours, Boolean medication,
		Boolean alcohol, OutdoorActivity outdoorActivity, String notes) {
		this.recordDate = recordDate;
		this.painIndex = painIndex;
		this.sleepHours = sleepHours;
		this.medication = medication;
		this.alcohol = alcohol;
		this.outdoorActivity = outdoorActivity;
		this.notes = notes;
	}

	/** 나타난 증상을 통째로 교체한다. */
	public void replaceSkinSigns(Collection<SkinSign> incoming) {
		skinSigns.retainAll(incoming);
		skinSigns.addAll(incoming);
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
