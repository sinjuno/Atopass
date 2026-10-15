package com.atopy.Skinlog.domain.analysis.dto.response;

import java.time.LocalDate;
import java.util.List;

public record TrendResponse(List<TrendPoint> points) {

	public record TrendPoint(LocalDate date, Long recordId, int score) {
	}

}
