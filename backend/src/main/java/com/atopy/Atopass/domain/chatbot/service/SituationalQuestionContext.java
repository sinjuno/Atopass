package com.atopy.Atopass.domain.chatbot.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 상황 질문 조건을 판정하는 데 필요한 정보. 오늘 초안과 최근 기록(오늘은 빼고, 날짜 오름차순).
 * recentDays에는 기록한 날만 들어 있다.
 */
record SituationalQuestionContext(ChatDraft draft, LocalDate today, List<SituationalDay> recentDays) {

	/** 어제 기록. 어제 기록하지 않았으면 비어 있다. */
	Optional<SituationalDay> yesterday() {
		LocalDate yesterday = today.minusDays(1);
		return recentDays.stream().filter(day -> day.date().equals(yesterday)).findFirst();
	}

	/** today 전날부터 days일 전까지의 기록 */
	List<SituationalDay> lastDays(int days) {
		LocalDate from = today.minusDays(days);
		return recentDays.stream().filter(day -> !day.date().isBefore(from)).toList();
	}

}
