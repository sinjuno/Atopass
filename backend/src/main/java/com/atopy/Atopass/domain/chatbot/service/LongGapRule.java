package com.atopy.Atopass.domain.chatbot.service;

import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** P06: 직전 기록이 며칠(설정값) 이상 전이면 "그동안 피부는 어땠어요?"를 묻는다. 처음 기록하는 날은 묻지 않는다. */
@Component
class LongGapRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	LongGapRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P06";
	}

	public int priority() {
		return properties.priority().p06();
	}

	public boolean matches(SituationalQuestionContext context) {
		return context.previousRecordDate() != null
			&& ChronoUnit.DAYS.between(context.previousRecordDate(), context.today()) >= properties.longGapDays();
	}

}
