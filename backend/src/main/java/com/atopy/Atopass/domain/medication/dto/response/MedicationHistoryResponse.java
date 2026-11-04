package com.atopy.Atopass.domain.medication.dto.response;

import com.atopy.Atopass.domain.medication.entity.MedicationHistory;
import java.time.LocalDateTime;

/** 약 변경 이력 한 줄. 값은 모두 문자열이다. */
public record MedicationHistoryResponse(LocalDateTime changedAt, String field, String before, String after) {

	public static MedicationHistoryResponse from(MedicationHistory history) {
		return new MedicationHistoryResponse(
			history.getChangedAt(), history.getField(), history.getOldValue(), history.getNewValue());
	}

}
