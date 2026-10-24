package com.atopy.Atopass.domain.chatbot.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 상황 질문(P01~P07) 중 오늘 물을 것을 고른다. 조건이 맞는 것 중 순위가 가장 높은 것 하나,
 * 그마저도 최근({@link SituationalQuestionProperties#sameQuestionCooldownDays}일 이내)에 나왔으면 다음 순위로 넘긴다.
 */
@Component
class SituationalQuestionSelector {

	private final List<SituationalQuestionRule> rules;
	private final SituationalQuestionProperties properties;

	SituationalQuestionSelector(List<SituationalQuestionRule> rules, SituationalQuestionProperties properties) {
		this.rules = rules;
		this.properties = properties;
	}

	Optional<String> select(SituationalQuestionContext context, Map<String, LocalDate> lastShown, LocalDate today) {
		return rules.stream()
			.sorted(Comparator.comparingInt(SituationalQuestionRule::priority))
			.filter(rule -> rule.matches(context))
			.filter(rule -> eligible(lastShown.get(rule.id()), today))
			.map(SituationalQuestionRule::id)
			.findFirst();
	}

	private boolean eligible(LocalDate lastShownDate, LocalDate today) {
		return lastShownDate == null
			|| ChronoUnit.DAYS.between(lastShownDate, today) >= properties.sameQuestionCooldownDays();
	}

}
