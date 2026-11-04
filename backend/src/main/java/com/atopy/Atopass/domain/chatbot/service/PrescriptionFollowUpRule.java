package com.atopy.Atopass.domain.chatbot.service;

import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * P10: 재진 날(P08)에 처방이나 연고가 바뀌었다고 답한 지 며칠(설정값 범위) 지났으면 "그동안 피부는 어땠어요?"를 묻는다.
 * 약의 효과를 묻는 게 아니라 그동안 피부가 어땠는지를 기록해 두기 위한 질문이다.
 * 범위를 같은 질문의 제외 기간(3일)보다 짧게 둬서 처방이 한 번 바뀔 때마다 한 번만 나오게 한다.
 */
@Component
class PrescriptionFollowUpRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	PrescriptionFollowUpRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P10";
	}

	public int priority() {
		return properties.priority().p10();
	}

	public boolean matches(SituationalQuestionContext context) {
		if (context.prescriptionChangedOn() == null) {
			return false;
		}
		long days = ChronoUnit.DAYS.between(context.prescriptionChangedOn(), context.today());
		return days >= properties.prescriptionFollowUpMinDays() && days <= properties.prescriptionFollowUpMaxDays();
	}

}
