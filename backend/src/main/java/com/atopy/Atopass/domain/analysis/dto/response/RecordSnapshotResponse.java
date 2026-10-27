package com.atopy.Atopass.domain.analysis.dto.response;

import com.atopy.Atopass.domain.record.dto.response.LifestyleResponse;
import com.atopy.Atopass.domain.record.dto.response.PhotoSummary;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** 날짜 비교 한쪽에 내려주는 기록 전체 내용 */
public record RecordSnapshotResponse(
	LocalDate date,
	Long recordId,
	List<SkinConditionResponse> skinConditions,
	/** 불편함 지수 0~10. 기록하지 않았으면 null */
	Integer painIndex,
	BigDecimal sleepHours,
	/** 수면 점수 1~5. 수면 시간을 기록하지 않았으면 null */
	Integer sleepScore,
	/** 약 복용 여부. 기록하지 않았으면 null */
	Boolean medication,
	/** 나타난 증상. 없으면 빈 목록 */
	List<SkinSign> skinSigns,
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
			record.getPainIndex(),
			record.getSleepHours(),
			record.getSleepScore(),
			record.getMedication(),
			record.getSkinSigns().stream().sorted().toList(),
			LifestyleResponse.from(record),
			record.getNotes(),
			photos);
	}

}
