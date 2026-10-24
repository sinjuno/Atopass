package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.record.entity.SkinRecord;
import org.springframework.stereotype.Component;

/** P01: 오늘 고통 지수가 어제보다 많이(설정값 이상) 오르면 "언제부터 이랬어요?"를 묻는다. */
@Component
class PainJumpRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	PainJumpRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P01";
	}

	public int priority() {
		return 1;
	}

	public boolean matches(SituationalQuestionContext context) {
		Integer todayPain = context.draft().toResponse().painIndex();
		if (todayPain == null) {
			return false;
		}
		return context.yesterday()
			.map(SkinRecord::getPainIndex)
			.filter(yesterdayPain -> yesterdayPain != null)
			.filter(yesterdayPain -> todayPain - yesterdayPain >= properties.painIndexJumpThreshold())
			.isPresent();
	}

}
