package com.atopy.Atopass.domain.chatbot.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 특이사항 질문 문구(S01)를 고르는 숫자 조건. 위에서부터 먼저 맞는 문구 하나만 쓴다.
 *
 * @param clinicVisitWithinDays 다음 재진일이 오늘부터 이 일수 안이면 의사 선생님께 전할 말을 묻는다.
 * @param highPainMinIndex      그렇지 않고 불편함 지수가 이 값 이상이면 가장 불편했던 순간을 묻는다.
 */
@ConfigurationProperties(prefix = "app.chatbot.notes")
public record NotesPromptProperties(
	@DefaultValue("7") int clinicVisitWithinDays,
	@DefaultValue("6") int highPainMinIndex
) {
}
