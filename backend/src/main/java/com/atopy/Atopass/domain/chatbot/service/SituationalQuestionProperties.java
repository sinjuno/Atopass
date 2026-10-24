package com.atopy.Atopass.domain.chatbot.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 상황 질문(P01~P07) 선택에 쓰는 숫자 조건.
 *
 * @param painIndexJumpThreshold 오늘 고통 지수가 어제보다 이만큼 이상 오르면 P01 조건을 만족한다.
 * @param sameQuestionCooldownDays 같은 상황 질문이 마지막으로 나온 지 이 일수 이상 지나야 다시 고를 수 있다.
 */
@ConfigurationProperties(prefix = "app.chatbot.situational")
public record SituationalQuestionProperties(
	@DefaultValue("2") int painIndexJumpThreshold,
	@DefaultValue("3") int sameQuestionCooldownDays
) {
}
