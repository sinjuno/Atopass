package com.atopy.Atopass.domain.chatbot.service;

import org.springframework.stereotype.Component;

/**
 * P08: 오늘이 등록해 둔 재진 날짜이면 "처방이나 연고가 바뀐 게 있어요?"를 묻는다.
 * 진료 결과를 기록해 두기 위한 질문이고, 약의 효과나 복용 방법은 말하지 않는다.
 */
@Component
class RevisitDayRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	RevisitDayRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P08";
	}

	public int priority() {
		return properties.priority().p08();
	}

	public boolean matches(SituationalQuestionContext context) {
		return context.revisitToday();
	}

}
