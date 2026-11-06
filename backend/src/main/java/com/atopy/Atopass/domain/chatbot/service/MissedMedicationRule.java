package com.atopy.Atopass.domain.chatbot.service;

import org.springframework.stereotype.Component;

/**
 * P05: 오늘 약을 "안 함"이고, 복용 중으로 등록해 둔 약이 있거나 최근(오늘 빼고 설정 일수) 약을 꾸준히 먹었으면(설정 일수 이상)
 * 어떤 약을 못 먹었는지와 이유를 묻는다. 이유만 기록하고, 복용을 권하거나 판단하지 않는다.
 */
@Component
class MissedMedicationRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	MissedMedicationRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P05";
	}

	public int priority() {
		return properties.priority().p05();
	}

	public boolean matches(SituationalQuestionContext context) {
		if (!Boolean.FALSE.equals(context.draft().toResponse().medication())) {
			return false;
		}
		if (context.hasActiveMedication()) {
			return true;
		}
		long takenDays = context.lastDays(properties.medicationLookbackDays()).stream()
			.filter(day -> Boolean.TRUE.equals(day.medication()))
			.count();
		return takenDays >= properties.medicationMinTakenDays();
	}

}
