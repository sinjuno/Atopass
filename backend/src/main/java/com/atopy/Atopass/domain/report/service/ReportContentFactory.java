package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinCondition;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.Symptom;
import com.atopy.Atopass.domain.record.entity.SymptomScore;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import com.atopy.Atopass.domain.report.service.ReportContent.LifestyleSummary;
import com.atopy.Atopass.domain.report.service.ReportContent.SymptomSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 선택한 기간의 기록을 보고서 내용으로 정리한다. 기간 안에 있는 기록만 사용한다. */
@Component
@RequiredArgsConstructor
class ReportContentFactory {

	private final ReportPhotoSource photoSource;

	/** @param records 기간 내 기록. 날짜 오름차순이어야 한다. */
	ReportContent create(List<SkinRecord> records, LocalDate startDate, LocalDate endDate,
		boolean includePhotos, LocalDate today) {
		List<DayEntry> days = records.stream().map(r -> dayEntry(r, includePhotos)).toList();
		return new ReportContent(startDate, endDate, today, records.size(), includePhotos,
			symptomSummaries(records), days, lifestyleSummary(records));
	}

	private DayEntry dayEntry(SkinRecord record, boolean includePhotos) {
		Map<BodyPart, Map<Symptom, Integer>> conditions = new EnumMap<>(BodyPart.class);
		for (SkinCondition condition : record.getConditions()) {
			Map<Symptom, Integer> scores = new EnumMap<>(Symptom.class);
			for (SymptomScore score : condition.getScores()) {
				scores.put(score.getSymptom(), score.getScore());
			}
			conditions.put(condition.getBodyPart(), scores);
		}
		List<ReportPhoto> photos = includePhotos ? photoSource.photosOf(record.getId()) : List.of();
		return new DayEntry(record.getRecordDate(), conditions, record.getSleepHours(),
			record.getAlcohol(), record.getOutdoorActivity(), record.getNotes(), photos);
	}

	/** 부위·증상별로 기간 내 점수의 개수, 평균, 최저, 최고, 처음과 마지막 값을 구한다. */
	private List<SymptomSummary> symptomSummaries(List<SkinRecord> records) {
		record Point(LocalDate date, int score) {
		}
		Map<BodyPart, Map<Symptom, List<Point>>> series = new EnumMap<>(BodyPart.class);
		for (SkinRecord record : records) {
			for (SkinCondition condition : record.getConditions()) {
				for (SymptomScore score : condition.getScores()) {
					series.computeIfAbsent(condition.getBodyPart(), k -> new EnumMap<>(Symptom.class))
						.computeIfAbsent(score.getSymptom(), k -> new ArrayList<>())
						.add(new Point(record.getRecordDate(), score.getScore()));
				}
			}
		}
		List<SymptomSummary> summaries = new ArrayList<>();
		series.forEach((bodyPart, bySymptom) -> bySymptom.forEach((symptom, points) -> {
			points.sort(Comparator.comparing(Point::date));
			Point first = points.get(0);
			Point last = points.get(points.size() - 1);
			summaries.add(new SymptomSummary(bodyPart, symptom, points.size(),
				points.stream().mapToInt(Point::score).average().orElse(0),
				points.stream().mapToInt(Point::score).min().orElse(0),
				points.stream().mapToInt(Point::score).max().orElse(0),
				first.date(), first.score(), last.date(), last.score()));
		}));
		return summaries;
	}

	private LifestyleSummary lifestyleSummary(List<SkinRecord> records) {
		List<BigDecimal> sleeps = records.stream().map(SkinRecord::getSleepHours).filter(h -> h != null).toList();
		Double averageSleep = sleeps.isEmpty() ? null
			: sleeps.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
		int alcoholRecorded = (int) records.stream().filter(r -> r.getAlcohol() != null).count();
		int alcoholDays = (int) records.stream().filter(r -> Boolean.TRUE.equals(r.getAlcohol())).count();
		Map<OutdoorActivity, Integer> outdoor = new EnumMap<>(OutdoorActivity.class);
		for (SkinRecord record : records) {
			if (record.getOutdoorActivity() != null) {
				outdoor.merge(record.getOutdoorActivity(), 1, Integer::sum);
			}
		}
		return new LifestyleSummary(averageSleep, sleeps.size(), alcoholDays, alcoholRecorded, outdoor);
	}

}
