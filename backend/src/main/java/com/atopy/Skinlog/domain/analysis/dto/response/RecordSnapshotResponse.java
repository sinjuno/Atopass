package com.atopy.Skinlog.domain.analysis.dto.response;

import com.atopy.Skinlog.domain.record.dto.response.LifestyleResponse;
import com.atopy.Skinlog.domain.record.dto.response.PhotoSummary;
import com.atopy.Skinlog.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Skinlog.domain.record.entity.SkinRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 날짜 비교 한쪽에 내려주는 기록 전체 내용 */
public record RecordSnapshotResponse(
	LocalDate date,
	Long recordId,
	List<SkinConditionResponse> skinConditions,
	BigDecimal sleepHours,
	LifestyleResponse lifestyle,
	String notes,
	List<PhotoSummary> photos
) {

	public static RecordSnapshotResponse from(SkinRecord record, List<PhotoSummary> photos) {
		return new RecordSnapshotResponse(
			record.getRecordDate(),
			record.getId(),
			record.getConditions().stream()
				.sorted(Comparator.comparing(c -> c.getBodyPart().ordinal()))
				.map(SkinConditionResponse::from)
				.toList(),
			record.getSleepHours(),
			LifestyleResponse.from(record),
			record.getNotes(),
			photos);
	}

}
