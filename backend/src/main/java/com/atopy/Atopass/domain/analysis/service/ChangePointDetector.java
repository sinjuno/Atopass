package com.atopy.Atopass.domain.analysis.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 변화점 탐지. 한 증상의 점수 시계열에서 개인 평균 ±1 표준편차를 벗어난 구간을 찾는다.
 * 연속된 날짜(하루 간격)에 같은 방향으로 벗어난 기록은 하나의 구간으로 묶는다.
 * 통계 계산만 하는 순수 클래스라 DB 없이 테스트할 수 있다.
 */
final class ChangePointDetector {

	/** 표준편차를 의미 있게 계산하려면 필요한 최소 기록 수 */
	static final int MIN_POINTS = 3;

	private ChangePointDetector() {
	}

	enum Direction {
		HIGH, LOW
	}

	record Point(LocalDate date, int score) {
	}

	/** 평균에서 벗어난 구간. maxDeviation은 구간 안에서 평균과의 차이가 가장 큰 값(절댓값)이다. */
	record Range(Direction direction, LocalDate startDate, LocalDate endDate,
		double mean, double maxDeviation) {
	}

	/** 날짜 오름차순으로 정렬된 점수 목록을 받아 평균에서 벗어난 구간을 돌려준다. */
	static List<Range> detect(List<Point> points) {
		if (points.size() < MIN_POINTS) {
			return List.of();
		}
		double mean = points.stream().mapToInt(Point::score).average().orElse(0);
		double variance = points.stream()
			.mapToDouble(p -> (p.score() - mean) * (p.score() - mean))
			.average().orElse(0);
		double std = Math.sqrt(variance);
		if (std == 0) {
			return List.of();
		}

		List<Range> ranges = new ArrayList<>();
		Direction currentDirection = null;
		LocalDate start = null;
		LocalDate end = null;
		double maxDeviation = 0;

		List<Point> sorted = points.stream().sorted(Comparator.comparing(Point::date)).toList();
		for (Point point : sorted) {
			double deviation = point.score() - mean;
			Direction direction = Math.abs(deviation) > std
				? (deviation > 0 ? Direction.HIGH : Direction.LOW)
				: null;

			boolean continues = direction != null && direction == currentDirection
				&& end.plusDays(1).equals(point.date());
			if (continues) {
				end = point.date();
				maxDeviation = Math.max(maxDeviation, Math.abs(deviation));
				continue;
			}
			if (currentDirection != null) {
				ranges.add(new Range(currentDirection, start, end, mean, maxDeviation));
			}
			currentDirection = direction;
			start = direction == null ? null : point.date();
			end = start;
			maxDeviation = direction == null ? 0 : Math.abs(deviation);
		}
		if (currentDirection != null) {
			ranges.add(new Range(currentDirection, start, end, mean, maxDeviation));
		}
		return ranges;
	}

}
