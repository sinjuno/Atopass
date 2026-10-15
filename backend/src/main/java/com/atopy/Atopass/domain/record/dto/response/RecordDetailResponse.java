package com.atopy.Atopass.domain.record.dto.response;

import com.atopy.Atopass.domain.record.entity.SkinRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record RecordDetailResponse(
	Long recordId,
	LocalDate recordDate,
	List<SkinConditionResponse> skinConditions,
	BigDecimal sleepHours,
	LifestyleResponse lifestyle,
	String notes,
	List<PhotoSummary> photos,
	LocalDateTime createdAt,
	LocalDateTime updatedAt
) {

	public static RecordDetailResponse from(SkinRecord record, List<PhotoSummary> photos) {
		return new RecordDetailResponse(
			record.getId(),
			record.getRecordDate(),
			record.getConditions().stream()
				.sorted(Comparator.comparing(c -> c.getBodyPart().ordinal()))
				.map(SkinConditionResponse::from)
				.toList(),
			record.getSleepHours(),
			LifestyleResponse.from(record),
			record.getNotes(),
			photos,
			record.getCreatedAt(),
			record.getUpdatedAt());
	}

}
