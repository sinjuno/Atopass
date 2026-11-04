package com.atopy.Atopass.domain.medication.entity;

import com.atopy.Atopass.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 먹거나 바르는 약. 지우지 않고 종료일(endDate)로 복용 중단을 나타낸다.
 * 약 봉투 사진 원본은 저장하지 않는다.
 */
@Getter
@Entity
@Table(name = "medications", indexes = @Index(name = "idx_medications_member_start", columnList = "memberId, startDate"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MedicationType type;

	/** 1회 용량 (예: 1정, 10mg) */
	@Column(length = 50)
	private String dose;

	/** 복용 시간대. morning,lunch처럼 쉼표로 이은 값이고, 비어 있을 수 있다. */
	@Column(nullable = false, length = 50)
	private String times;

	/** 약 봉투에 적힌 복용 시간 원문 */
	@Column(length = 100)
	private String timesText;

	@Column(nullable = false)
	private LocalDate startDate;

	/** 값이 있으면 그날까지 복용한 약 */
	private LocalDate endDate;

	@Column(length = 500)
	private String memo;

	@Column(name = "is_other_condition", nullable = false)
	private boolean otherCondition;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MedicationSource source;

	@Builder
	private Medication(Long memberId, String name, MedicationType type, String dose, List<MedicationTime> times,
		String timesText, LocalDate startDate, LocalDate endDate, String memo, boolean otherCondition,
		MedicationSource source) {
		this.memberId = memberId;
		this.name = name;
		this.type = type;
		this.dose = dose;
		this.times = join(times);
		this.timesText = timesText;
		this.startDate = startDate;
		this.endDate = endDate;
		this.memo = memo;
		this.otherCondition = otherCondition;
		this.source = source;
	}

	public List<MedicationTime> timeList() {
		if (times.isBlank()) {
			return List.of();
		}
		return Arrays.stream(times.split(","))
			.map(code -> MedicationTime.fromCode(code).orElseThrow())
			.toList();
	}

	public void rename(String name) {
		this.name = name;
	}

	public void changeType(MedicationType type) {
		this.type = type;
	}

	public void changeDose(String dose) {
		this.dose = dose;
	}

	public void changeTimes(List<MedicationTime> times) {
		this.times = join(times);
	}

	public void changeTimesText(String timesText) {
		this.timesText = timesText;
	}

	public void changeStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public void changeEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public void changeMemo(String memo) {
		this.memo = memo;
	}

	public void changeOtherCondition(boolean otherCondition) {
		this.otherCondition = otherCondition;
	}

	/** 같은 시간대를 중복 없이, 아침에서 취침 순서로 쉼표로 잇는다. */
	private static String join(List<MedicationTime> times) {
		if (times == null) {
			return "";
		}
		return times.stream().distinct().sorted().map(MedicationTime::code).collect(Collectors.joining(","));
	}

}
