package com.atopy.Atopass.domain.chatbot.service;

import org.springframework.stereotype.Component;

/** P07: 오늘 고통 지수가 어제보다 많이(설정값 이상) 내리면 "달라진 게 있었어요?"를 묻는다. 어제 기록이 없으면 묻지 않는다. */
@Component
class PainDropRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	PainDropRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P07";
	}

	public int priority() {
		return properties.priority().p07();
	}

	public boolean matches(SituationalQuestionContext context) {
		Integer todayPain = context.draft().toResponse().painIndex();
		if (todayPain == null) {
			return false;
		}
		return context.yesterday()
			.map(SituationalDay::painIndex)
			.filter(yesterdayPain -> yesterdayPain != null)
			.filter(yesterdayPain -> yesterdayPain - todayPain >= properties.painIndexDropThreshold())
			.isPresent();
	}

}
