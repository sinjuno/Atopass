package com.atopy.Atopass.domain.report.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ReportCreateRequest(
	@NotNull(message = "시작일을 입력해 주세요.")
	LocalDate startDate,

	@NotNull(message = "종료일을 입력해 주세요.")
	LocalDate endDate,

	/** 피부 사진 포함 여부. 생략하면 포함하지 않는다. */
	Boolean includePhotos
) {
}
