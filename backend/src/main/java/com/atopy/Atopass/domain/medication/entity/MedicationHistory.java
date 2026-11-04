package com.atopy.Atopass.domain.medication.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약 정보가 바뀐 이력. 바뀐 필드마다 한 줄이 쌓인다.
 * 값은 모두 문자열이고 시간대는 morning,evening처럼 쉼표로 잇는다.
 */
@Getter
@Entity
@Table(name = "medication_history", indexes = @Index(name = "idx_medication_history_med_changed", columnList = "medicationId, changedAt"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long medicationId;

	@Column(nullable = false)
	private LocalDateTime changedAt;

	@Column(nullable = false, length = 30)
	private String field;

	/** API에서는 before. before는 MySQL 예약어라 컬럼 이름으로 쓰지 않는다. */
	@Column(length = 500)
	private String oldValue;

	/** API에서는 after */
	@Column(length = 500)
	private String newValue;

	public static MedicationHistory of(
		Long medicationId, LocalDateTime changedAt, String field, String oldValue, String newValue) {
		MedicationHistory history = new MedicationHistory();
		history.medicationId = medicationId;
		history.changedAt = changedAt;
		history.field = field;
		history.oldValue = oldValue;
		history.newValue = newValue;
		return history;
	}

}
