package com.atopy.Atopass.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.analysis.service.ChangePointDetector.Direction;
import com.atopy.Atopass.domain.analysis.service.ChangePointDetector.Point;
import com.atopy.Atopass.domain.analysis.service.ChangePointDetector.Range;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangePointDetectorTest {

	private static final LocalDate DAY1 = LocalDate.of(2026, 10, 1);

	private static Point point(int dayOffset, int score) {
		return new Point(DAY1.plusDays(dayOffset), score);
	}

	@Test
	void 평균보다_크게_높은_연속_기록은_하나의_구간으로_묶는다() {
		// 2점이 6일, 9점이 이틀 연속 → 평균 3.75, 표준편차 약 3.03
		List<Point> points = List.of(
			point(0, 2), point(1, 2), point(2, 2), point(3, 2), point(4, 2), point(5, 2),
			point(6, 9), point(7, 9));

		List<Range> ranges = ChangePointDetector.detect(points);

		assertThat(ranges).hasSize(1);
		Range range = ranges.get(0);
		assertThat(range.direction()).isEqualTo(Direction.HIGH);
		assertThat(range.startDate()).isEqualTo(DAY1.plusDays(6));
		assertThat(range.endDate()).isEqualTo(DAY1.plusDays(7));
		assertThat(range.mean()).isEqualTo(3.75);
		assertThat(range.maxDeviation()).isEqualTo(5.25);
	}

	@Test
	void 평균보다_크게_낮은_기록은_LOW로_탐지한다() {
		List<Point> points = List.of(
			point(0, 8), point(1, 8), point(2, 8), point(3, 8), point(4, 8), point(5, 8),
			point(6, 1));

		List<Range> ranges = ChangePointDetector.detect(points);

		assertThat(ranges).hasSize(1);
		assertThat(ranges.get(0).direction()).isEqualTo(Direction.LOW);
		assertThat(ranges.get(0).startDate()).isEqualTo(DAY1.plusDays(6));
	}

	@Test
	void 날짜가_이어지지_않으면_같은_방향이어도_구간을_나눈다() {
		// 5일, 6일 / 9일에 높게 기록 (7·8일은 기록 없음)
		List<Point> points = List.of(
			point(0, 1), point(1, 1), point(2, 1), point(3, 1), point(4, 1), point(5, 1),
			point(6, 9), point(7, 9), point(10, 9));

		List<Range> ranges = ChangePointDetector.detect(points);

		assertThat(ranges).hasSize(2);
		assertThat(ranges.get(0).startDate()).isEqualTo(DAY1.plusDays(6));
		assertThat(ranges.get(0).endDate()).isEqualTo(DAY1.plusDays(7));
		assertThat(ranges.get(1).startDate()).isEqualTo(DAY1.plusDays(10));
		assertThat(ranges.get(1).endDate()).isEqualTo(DAY1.plusDays(10));
	}

	@Test
	void 점수가_모두_같으면_표준편차가_0이라_탐지하지_않는다() {
		List<Point> points = List.of(point(0, 4), point(1, 4), point(2, 4), point(3, 4));

		assertThat(ChangePointDetector.detect(points)).isEmpty();
	}

	@Test
	void 기록이_너무_적으면_탐지하지_않는다() {
		assertThat(ChangePointDetector.detect(List.of(point(0, 1), point(1, 10)))).isEmpty();
	}

	@Test
	void 정렬되지_않은_입력도_날짜순으로_처리한다() {
		List<Point> points = List.of(
			point(7, 9), point(0, 2), point(6, 9), point(1, 2), point(2, 2),
			point(3, 2), point(4, 2), point(5, 2));

		List<Range> ranges = ChangePointDetector.detect(points);

		assertThat(ranges).hasSize(1);
		assertThat(ranges.get(0).startDate()).isEqualTo(DAY1.plusDays(6));
		assertThat(ranges.get(0).endDate()).isEqualTo(DAY1.plusDays(7));
	}

}
