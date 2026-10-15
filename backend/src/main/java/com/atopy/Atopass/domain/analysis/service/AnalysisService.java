package com.atopy.Atopass.domain.analysis.service;

import com.atopy.Atopass.domain.analysis.dto.response.AnalysisSummaryResponse;
import com.atopy.Atopass.domain.analysis.dto.response.ComparisonResponse;
import com.atopy.Atopass.domain.analysis.dto.response.InsightResponse;
import com.atopy.Atopass.domain.analysis.dto.response.RecordSnapshotResponse;
import com.atopy.Atopass.domain.analysis.dto.response.TrendResponse;
import com.atopy.Atopass.domain.analysis.dto.response.TrendResponse.TrendPoint;
import com.atopy.Atopass.domain.analysis.service.ChangePointDetector.Direction;
import com.atopy.Atopass.domain.analysis.service.ChangePointDetector.Point;
import com.atopy.Atopass.domain.analysis.service.ChangePointDetector.Range;
import com.atopy.Atopass.domain.record.dto.response.PhotoSummary;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinCondition;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.Symptom;
import com.atopy.Atopass.domain.record.entity.SymptomScore;
import com.atopy.Atopass.domain.record.repository.PhotoRepository;
import com.atopy.Atopass.domain.record.repository.RecordRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisService {

	/** 기간 내 기록이 이 건수 미만이면 비교·변화점 분석을 하지 않는다. */
	public static final int MIN_RECORDS_FOR_ANALYSIS = 7;

	/** 화면에 보여줄 인사이트 최대 개수. 평균에서 가장 크게 벗어난 구간부터 보여준다. */
	private static final int MAX_INSIGHTS = 5;

	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("M/d", Locale.KOREA);

	private final RecordRepository recordRepository;
	private final PhotoRepository photoRepository;

	public AnalysisSummaryResponse summary(Long memberId, LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
		}
		List<SkinRecord> records = recordRepository
			.findByMemberIdAndRecordDateBetweenOrderByRecordDateAsc(memberId, startDate, endDate);

		boolean available = records.size() >= MIN_RECORDS_FOR_ANALYSIS;
		List<InsightResponse> insights = available ? detectInsights(records) : List.of();
		return new AnalysisSummaryResponse(
			new AnalysisSummaryResponse.Period(startDate, endDate), records.size(), available, insights);
	}

	public TrendResponse trends(
		Long memberId, BodyPart bodyPart, Symptom symptom, LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
		}
		List<SkinRecord> records = recordRepository
			.findByMemberIdAndRecordDateBetweenOrderByRecordDateAsc(memberId, startDate, endDate);

		List<TrendPoint> points = new ArrayList<>();
		for (SkinRecord record : records) {
			record.getConditions().stream()
				.filter(condition -> condition.getBodyPart() == bodyPart)
				.findFirst()
				.flatMap(condition -> condition.getScores().stream()
					.filter(score -> score.getSymptom() == symptom)
					.findFirst())
				.ifPresent(score -> points.add(
					new TrendPoint(record.getRecordDate(), record.getId(), score.getScore())));
		}
		return new TrendResponse(points);
	}

	public ComparisonResponse comparison(Long memberId, LocalDate dateA, LocalDate dateB) {
		return new ComparisonResponse(snapshotOf(memberId, dateA), snapshotOf(memberId, dateB));
	}

	private RecordSnapshotResponse snapshotOf(Long memberId, LocalDate date) {
		return recordRepository.findByMemberIdAndRecordDate(memberId, date)
			.map(record -> RecordSnapshotResponse.from(record, photosOf(record)))
			.orElse(null);
	}

	private List<PhotoSummary> photosOf(SkinRecord record) {
		return photoRepository.findByRecordIdOrderByCreatedAtAsc(record.getId()).stream()
			.map(PhotoSummary::from)
			.toList();
	}

	/** 부위·증상별 점수 시계열마다 변화점을 찾아 인사이트로 만든다. */
	private List<InsightResponse> detectInsights(List<SkinRecord> records) {
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

		record Found(BodyPart bodyPart, Symptom symptom, Range range) {
		}
		List<Found> found = new ArrayList<>();
		series.forEach((bodyPart, bySymptom) -> bySymptom.forEach((symptom, points) ->
			ChangePointDetector.detect(points).forEach(range -> found.add(new Found(bodyPart, symptom, range)))));

		return found.stream()
			.sorted(Comparator.comparingDouble((Found f) -> f.range().maxDeviation()).reversed()
				.thenComparing(f -> f.range().startDate()))
			.limit(MAX_INSIGHTS)
			.map(f -> toInsight(f.bodyPart(), f.symptom(), f.range()))
			.toList();
	}

	private InsightResponse toInsight(BodyPart bodyPart, Symptom symptom, Range range) {
		String comparison = range.direction() == Direction.HIGH ? "높게" : "낮게";
		String period = range.startDate().equals(range.endDate())
			? DAY_FORMAT.format(range.startDate())
			: DAY_FORMAT.format(range.startDate()) + "~" + DAY_FORMAT.format(range.endDate());
		String message = String.format(Locale.ROOT, "%s %s 점수가 평소(평균 %.1f)보다 %s 기록된 구간이 있습니다. (%s)",
			bodyPart.label(), symptom.label(), range.mean(), comparison, period);
		return new InsightResponse(InsightResponse.SYMPTOM_CHANGE, message, bodyPart, symptom,
			range.direction().name(), range.startDate(), range.endDate());
	}

}
