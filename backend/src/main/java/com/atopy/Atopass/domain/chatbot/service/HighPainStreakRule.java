package com.atopy.Atopass.domain.chatbot.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * P03: 불편함 지수가 높은 날(설정값 이상)이 오늘을 포함해 며칠(설정값) 연속이면 "요즘 생활에서 달라진 게 있어요?"를 묻는다.
 * 사이에 기록하지 않은 날이 있으면 연속이 아니다.
 */
@Component
class HighPainStreakRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	HighPainStreakRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P03";
	}

	public int priority() {
		return properties.priority().p03();
	}

	public boolean matches(SituationalQuestionContext context) {
		if (!isHigh(context.draft().toResponse().painIndex())) {
			return false;
		}
		Map<LocalDate, SituationalDay> byDate = context.recentDays().stream()
			.collect(Collectors.toMap(SituationalDay::date, Function.identity()));
		for (int daysAgo = 1; daysAgo < properties.highPainStreakDays(); daysAgo++) {
			SituationalDay day = byDate.get(context.today().minusDays(daysAgo));
			if (day == null || !isHigh(day.painIndex())) {
				return false;
			}
		}
		return true;
	}

	private boolean isHigh(Integer painIndex) {
		return painIndex != null && painIndex >= properties.highPainMinIndex();
	}

}
