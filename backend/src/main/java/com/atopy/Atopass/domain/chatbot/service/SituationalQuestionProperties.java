package com.atopy.Atopass.domain.chatbot.service;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 상황 질문(P01~P07) 선택에 쓰는 숫자 조건과 순위.
 *
 * @param painIndexJumpThreshold 오늘 고통 지수가 어제보다 이만큼 이상 오르면 P01 조건을 만족한다.
 * @param sameQuestionCooldownDays 같은 상황 질문이 마지막으로 나온 지 이 일수 이상 지나야 다시 고를 수 있다.
 * @param shortSleepMaxHours 수면 시간이 이 시간 이하이고
 * @param shortSleepMinPainIndex 고통 지수가 이 값 이상이면 P04 조건을 만족한다.
 * @param medicationLookbackDays 오늘을 빼고 최근 이 일수 중
 * @param medicationMinTakenDays 약을 먹은 날이 이 일수 이상인데 오늘 "안 함"이면 P05 조건을 만족한다.
 * @param highPainMinIndex 고통 지수가 이 값 이상인 날이
 * @param highPainStreakDays 오늘을 포함해 이 일수만큼 연속이면 P03 조건을 만족한다.
 * @param longGapDays 직전 기록이 이 일수 이상 전이면 P06 조건을 만족한다.
 * @param painIndexDropThreshold 오늘 고통 지수가 어제보다 이만큼 이상 내리면 P07 조건을 만족한다.
 * @param priority 질문별 순위. 낮을수록 먼저 고른다.
 */
@ConfigurationProperties(prefix = "app.chatbot.situational")
public record SituationalQuestionProperties(
	@DefaultValue("2") int painIndexJumpThreshold,
	@DefaultValue("3") int sameQuestionCooldownDays,
	@DefaultValue("5") BigDecimal shortSleepMaxHours,
	@DefaultValue("4") int shortSleepMinPainIndex,
	@DefaultValue("7") int medicationLookbackDays,
	@DefaultValue("4") int medicationMinTakenDays,
	@DefaultValue("6") int highPainMinIndex,
	@DefaultValue("3") int highPainStreakDays,
	@DefaultValue("3") int longGapDays,
	@DefaultValue("3") int painIndexDropThreshold,
	@DefaultValue Priority priority
) {

	/** 지난 기록을 며칠까지 읽어야 하는지. 어제 기록이 필요한 규칙이 있으므로 최소 1일. */
	int historyDays() {
		return Math.max(1, Math.max(medicationLookbackDays, highPainStreakDays - 1));
	}

	/** 상황 질문별 순위. 조건이 여러 개 맞으면 값이 가장 낮은 것 하나만 낸다. */
	public record Priority(
		@DefaultValue("1") int p01,
		@DefaultValue("2") int p02,
		@DefaultValue("3") int p03,
		@DefaultValue("4") int p04,
		@DefaultValue("5") int p05,
		@DefaultValue("6") int p06,
		@DefaultValue("7") int p07
	) {
	}

}
