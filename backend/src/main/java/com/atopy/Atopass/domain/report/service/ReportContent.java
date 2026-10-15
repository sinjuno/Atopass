package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.Symptom;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * PDF에 들어갈 내용. DB 엔티티와 분리해 두어 PDF 만드는 부분을 DB 없이 테스트할 수 있다.
 * 사용자가 기록하지 않은 값은 null이며, 임의의 값으로 채우지 않는다.
 *
 * @param includePhotos 사진 포함을 요청했는지
 */
record ReportContent(
	LocalDate startDate,
	LocalDate endDate,
	LocalDate generatedDate,
	int recordCount,
	boolean includePhotos,
	List<SymptomSummary> symptomSummaries,
	List<DayEntry> days,
	LifestyleSummary lifestyle
) {

	/** 한 부위·증상의 기간 내 점수 요약 */
	record SymptomSummary(
		BodyPart bodyPart,
		Symptom symptom,
		int count,
		double average,
		int min,
		int max,
		LocalDate firstDate,
		int firstScore,
		LocalDate lastDate,
		int lastScore
	) {
	}

	/** 하루의 기록 */
	record DayEntry(
		LocalDate date,
		Map<BodyPart, Map<Symptom, Integer>> conditions,
		BigDecimal sleepHours,
		Boolean alcohol,
		OutdoorActivity outdoorActivity,
		String notes,
		List<ReportPhoto> photos
	) {
	}

	/** 기간 내 수면·생활 기록 요약. 기록한 날만 센다. */
	record LifestyleSummary(
		Double averageSleepHours,
		int sleepRecordedDays,
		int alcoholDays,
		int alcoholRecordedDays,
		Map<OutdoorActivity, Integer> outdoorDays
	) {
	}

}
