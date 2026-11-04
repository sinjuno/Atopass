package com.atopy.Atopass.domain.chatbot.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * P11: 불편함 지수가 낮은 날(설정값 이하)이 오늘을 포함해 며칠(설정값) 연속이면 "요즘 생활에서 달라진 게 있어요?"를 묻는다(P03의 반대).
 * 사이에 기록하지 않은 날이 있으면 연속이 아니다.
 */
@Component
class LowPainStreakRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	LowPainStreakRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P11";
	}

	public int priority() {
		return properties.priority().p11();
	}

	public boolean matches(SituationalQuestionContext context) {
		if (!isLow(context.draft().toResponse().painIndex())) {
			return false;
		}
		Map<LocalDate, SituationalDay> byDate = context.recentDays().stream()
			.collect(Collectors.toMap(SituationalDay::date, Function.identity()));
		for (int daysAgo = 1; daysAgo < properties.lowPainStreakDays(); daysAgo++) {
			SituationalDay day = byDate.get(context.today().minusDays(daysAgo));
			if (day == null || !isLow(day.painIndex())) {
				return false;
			}
		}
		return true;
	}

	private boolean isLow(Integer painIndex) {
		return painIndex != null && painIndex <= properties.lowPainMaxIndex();
	}

}
