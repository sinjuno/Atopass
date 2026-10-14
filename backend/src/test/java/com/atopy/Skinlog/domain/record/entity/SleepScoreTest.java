package com.atopy.Skinlog.domain.record.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SleepScoreTest {

	@ParameterizedTest
	@CsvSource({
		"7, 5", "8, 5", "9, 5",
		"6.9, 4", "5, 4",
		"4.9, 3", "3, 3",
		"2.9, 2", "1, 2",
		"0.9, 1", "0, 1",
		"9.1, 4", "11, 4",
		"11.1, 3", "13, 3",
		"24, 1"
	})
	void 칠에서_구시간이_5점이고_2시간_벗어날_때마다_1점씩_깎인다(String hours, int expected) {
		assertThat(SleepScore.of(new BigDecimal(hours))).isEqualTo(expected);
	}

	@Test
	void 수면_시간을_기록하지_않으면_점수도_없다() {
		assertThat(SleepScore.of(null)).isNull();
	}

}
