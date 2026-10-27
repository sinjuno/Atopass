package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftResponse;
import org.springframework.stereotype.Component;

/** P04: 잠을 적게 잤고(설정값 이하) 불편함 지수도 높으면(설정값 이상) 왜 깼는지 묻는다. */
@Component
class ShortSleepRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	ShortSleepRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P04";
	}

	public int priority() {
		return properties.priority().p04();
	}

	public boolean matches(SituationalQuestionContext context) {
		ChatDraftResponse today = context.draft().toResponse();
		return today.sleepHours() != null && today.painIndex() != null
			&& today.sleepHours().compareTo(properties.shortSleepMaxHours()) <= 0
			&& today.painIndex() >= properties.shortSleepMinPainIndex();
	}

}
