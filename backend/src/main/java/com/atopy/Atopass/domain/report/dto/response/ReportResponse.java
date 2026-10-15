package com.atopy.Atopass.domain.report.dto.response;

import com.atopy.Atopass.domain.report.entity.Report;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportResponse(
	Long reportId,
	LocalDate startDate,
	LocalDate endDate,
	boolean includePhotos,
	LocalDateTime createdAt
) {

	public static ReportResponse from(Report report) {
		return new ReportResponse(report.getId(), report.getStartDate(), report.getEndDate(),
			report.isIncludePhotos(), report.getCreatedAt());
	}

}
