package com.atopy.Atopass.domain.record.entity;

import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.global.entity.BaseTimeEntity;
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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

	/** 고통 지수 0~10 (사용자가 느끼는 주관적인 정도. 0은 없음) */
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

	// ── 하루 점검: 챗봇이 묻는 항목 중 위 칸에 없는 것. 기록하지 않았으면 null ──

	/** 오늘 피부 상태 (평소와 비교) */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private SkinTrend skinTrend;

	/** 가려움 정도 0~10 (사용자가 느끼는 주관적인 정도. 0은 없음) */
	private Integer itchScore;

	/** 가려워서 잠을 설쳤는지 */
	private Boolean sleepDisturbed;

	/** 보습제를 발랐는지 */
	private Boolean moisturizer;

	/** 처방받은 연고·약 사용 여부. 약 복용 여부(medication)와 맞을 때만 남긴다. */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 20)
	private MedicationUse medicationUse;

	/** 연결 질문(C01~C16)의 답. 질문 ID → 고른 선택지 또는 직접 쓴 글 */
	@ElementCollection
	@CollectionTable(name = "record_linked_answers", joinColumns = @JoinColumn(name = "record_id"))
	@MapKeyColumn(name = "question_id", length = 3)
	@Column(name = "answer", nullable = false, length = 200)
	private Map<String, String> linkedAnswers = new LinkedHashMap<>();

	@OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<SkinCondition> conditions = new ArrayList<>();

	/** 나타난 증상. 없으면 비어 있다. */
	@ElementCollection
	@CollectionTable(name = "record_skin_signs", joinColumns = @JoinColumn(name = "record_id"))
	// 값 목록이 바뀌어도 저장되도록 MySQL ENUM 대신 문자열 칸으로 둔다. (SymptomMigration 참고)
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "skin_sign", nullable = false, length = 30)
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

	public Map<String, String> getLinkedAnswers() {
		return Collections.unmodifiableMap(linkedAnswers);
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

	/**
	 * 하루 점검 항목을 통째로 바꾼다.
	 * 연고·약 사용 여부는 약 복용 여부와 맞아야 한다(사용함이면 복용 true, 그 밖에는 false). 다른 화면에서 약 복용 여부만
	 * 바꿔 서로 맞지 않으면, 사용자가 마지막에 고친 약 복용 여부를 따르고 연고·약 사용 여부는 지운다.
	 */
	public void updateDailyCheck(SkinTrend skinTrend, Integer itchScore, Boolean sleepDisturbed, Boolean moisturizer,
		MedicationUse medicationUse, Map<String, String> linkedAnswers) {
		this.skinTrend = skinTrend;
		this.itchScore = itchScore;
		this.sleepDisturbed = sleepDisturbed;
		this.moisturizer = moisturizer;
		boolean matches = medicationUse == null || Objects.equals(medication, medicationUse == MedicationUse.USED);
		this.medicationUse = matches ? medicationUse : null;
		this.linkedAnswers.keySet().retainAll(linkedAnswers.keySet());
		this.linkedAnswers.putAll(linkedAnswers);
	}

	/** 나타난 증상을 통째로 교체한다. */
	public void replaceSkinSigns(Collection<SkinSign> incoming) {
		skinSigns.retainAll(incoming);
		skinSigns.addAll(incoming);
	}

	/**
	 * 부위별 증상을 통째로 교체한다. 같은 부위는 점수·나타난 증상만 바꾸고, 빠진 부위는 지우고, 새 부위는 추가한다.
	 * (지우고 다시 넣으면 unique 제약에 걸릴 수 있어 제자리에서 갱신한다.)
	 */
	public void replaceConditions(Map<BodyPart, Map<Symptom, Integer>> incoming,
		Map<BodyPart, Set<SkinSign>> incomingSigns) {
		conditions.removeIf(c -> !incoming.containsKey(c.getBodyPart()));
		incoming.forEach((bodyPart, scores) -> {
			Set<SkinSign> signs = incomingSigns.getOrDefault(bodyPart, EnumSet.noneOf(SkinSign.class));
			Optional<SkinCondition> existing = conditions.stream()
				.filter(c -> c.getBodyPart() == bodyPart).findFirst();
			if (existing.isPresent()) {
				existing.get().replaceScores(scores);
				existing.get().replaceSkinSigns(signs);
			} else {
				conditions.add(new SkinCondition(this, bodyPart, scores, signs));
			}
		});
	}

}
