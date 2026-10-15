package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.Symptom;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import com.atopy.Atopass.domain.report.service.ReportContent.LifestyleSummary;
import com.atopy.Atopass.domain.report.service.ReportContent.SymptomSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 보고서 테스트용 예시 데이터 */
final class ReportSamples {

	/** 1x1 크기의 투명 PNG */
	static final byte[] TINY_PNG = Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

	private ReportSamples() {
	}

	static ReportContent content(boolean includePhotos, String notes, List<ReportPhoto> photos) {
		Map<BodyPart, Map<Symptom, Integer>> day1 = new EnumMap<>(BodyPart.class);
		day1.put(BodyPart.ARM, new EnumMap<>(Map.of(Symptom.ITCHING, 4, Symptom.REDNESS, 3)));
		Map<BodyPart, Map<Symptom, Integer>> day2 = new EnumMap<>(BodyPart.class);
		day2.put(BodyPart.ARM, new EnumMap<>(Map.of(Symptom.ITCHING, 6)));
		day2.put(BodyPart.FACE, new EnumMap<>(Map.of(Symptom.DRYNESS, 2)));

		List<DayEntry> days = List.of(
			new DayEntry(LocalDate.of(2026, 3, 1), day1, new BigDecimal("6.0"), false, OutdoorActivity.MEDIUM,
				null, List.of()),
			new DayEntry(LocalDate.of(2026, 3, 2), day2, null, true, null, notes, photos));
		List<SymptomSummary> summaries = List.of(
			new SymptomSummary(BodyPart.ARM, Symptom.ITCHING, 2, 5.0, 4, 6,
				LocalDate.of(2026, 3, 1), 4, LocalDate.of(2026, 3, 2), 6),
			new SymptomSummary(BodyPart.FACE, Symptom.DRYNESS, 1, 2.0, 2, 2,
				LocalDate.of(2026, 3, 2), 2, LocalDate.of(2026, 3, 2), 2));
		LifestyleSummary lifestyle = new LifestyleSummary(6.0, 1, 1, 2,
			new EnumMap<>(Map.of(OutdoorActivity.MEDIUM, 1)));
		return new ReportContent(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 1),
			2, includePhotos, summaries, days, lifestyle);
	}

}
