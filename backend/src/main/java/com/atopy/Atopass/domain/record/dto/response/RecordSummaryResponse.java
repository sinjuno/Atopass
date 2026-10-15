package com.atopy.Atopass.domain.record.dto.response;

import com.atopy.Atopass.domain.record.entity.SkinRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 목록 항목. 홈 점수 차트에 필요한 값을 위해 부위별 증상을 포함한다. */
public record RecordSummaryResponse(
	Long recordId,
	LocalDate recordDate,
	List<SkinConditionResponse> skinConditions,
	BigDecimal sleepHours,
	int photoCount
) {

	public static RecordSummaryResponse from(SkinRecord record, int photoCount) {
		return new RecordSummaryResponse(
			record.getId(),
			record.getRecordDate(),
			record.getConditions().stream()
				.sorted(Comparator.comparing(c -> c.getBodyPart().ordinal()))
				.map(SkinConditionResponse::from)
				.toList(),
			record.getSleepHours(),
			photoCount);
	}

}
