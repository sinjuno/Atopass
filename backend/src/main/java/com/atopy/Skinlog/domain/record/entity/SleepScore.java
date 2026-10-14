package com.atopy.Skinlog.domain.record.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 수면 점수(1~5). 7~9시간을 최적(5점)으로 보고, 그 범위에서 2시간 벗어날 때마다 1점씩 깎는다.
 * 예: 5~7시간 4점, 3~5시간 3점, 9~11시간 4점. 최저 1점.
 */
public final class SleepScore {

	private static final BigDecimal OPTIMAL_MIN = BigDecimal.valueOf(7);
	private static final BigDecimal OPTIMAL_MAX = BigDecimal.valueOf(9);
	private static final BigDecimal STEP_HOURS = BigDecimal.valueOf(2);
	private static final int MAX_SCORE = 5;
	private static final int MIN_SCORE = 1;

	private SleepScore() {
	}

	/** 수면 시간을 기록하지 않았으면 null */
	public static Integer of(BigDecimal sleepHours) {
		if (sleepHours == null) {
			return null;
		}
		BigDecimal gap = sleepHours.compareTo(OPTIMAL_MIN) < 0 ? OPTIMAL_MIN.subtract(sleepHours)
			: sleepHours.compareTo(OPTIMAL_MAX) > 0 ? sleepHours.subtract(OPTIMAL_MAX)
			: BigDecimal.ZERO;
		// 벗어난 시간을 2시간 단위로 올림한다. (0.1시간만 벗어나도 1점 감점)
		int penalty = gap.divide(STEP_HOURS, 0, RoundingMode.CEILING).intValue();
		return Math.max(MIN_SCORE, MAX_SCORE - penalty);
	}

}
