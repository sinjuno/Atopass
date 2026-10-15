package com.atopy.Atopass.domain.analysis.dto.response;

import java.time.LocalDate;
import java.util.List;

public record AnalysisSummaryResponse(
	Period period,
	int recordCount,
	boolean availableForComparison,
	List<InsightResponse> insights
) {

	public record Period(LocalDate startDate, LocalDate endDate) {
	}

}
