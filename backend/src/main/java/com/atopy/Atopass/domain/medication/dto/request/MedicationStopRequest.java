package com.atopy.Atopass.domain.medication.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** 복용 중단 요청. 종료일은 시작일보다 앞설 수 없다. */
public record MedicationStopRequest(
	@NotNull(message = "종료일을 입력해 주세요.")
	LocalDate endDate
) {
}
