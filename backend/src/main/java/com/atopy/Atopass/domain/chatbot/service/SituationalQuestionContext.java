package com.atopy.Atopass.domain.chatbot.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 상황 질문 조건을 판정하는 데 필요한 정보. 오늘 초안과 최근 기록(오늘은 빼고, 날짜 오름차순).
 * recentDays에는 기록한 날만 들어 있다. previousRecordDate는 기간과 상관없이 오늘 전 가장 최근 기록의 날짜이고,
 * 처음 기록하는 날이면 null이다. revisitToday는 오늘이 등록해 둔 재진 날짜인지(P08)이고,
 * prescriptionChangedOn은 최근에 재진 날 "처방이나 연고가 바뀌었다"고 답한 날짜(P10)로, 없으면 null이다.
 * hasActiveMedication은 지금 복용 중으로 등록해 둔 약이 있는지(P05)다.
 */
record SituationalQuestionContext(
	ChatDraft draft, LocalDate today, List<SituationalDay> recentDays, LocalDate previousRecordDate,
	boolean revisitToday, LocalDate prescriptionChangedOn, boolean hasActiveMedication) {

	/** 등록해 둔 약이 없는 경우(테스트 등) */
	SituationalQuestionContext(
		ChatDraft draft, LocalDate today, List<SituationalDay> recentDays, LocalDate previousRecordDate,
		boolean revisitToday, LocalDate prescriptionChangedOn) {
		this(draft, today, recentDays, previousRecordDate, revisitToday, prescriptionChangedOn, false);
	}

	/** 처방이 바뀐 날이 없는 경우(테스트 등) */
	SituationalQuestionContext(
		ChatDraft draft, LocalDate today, List<SituationalDay> recentDays, LocalDate previousRecordDate,
		boolean revisitToday) {
		this(draft, today, recentDays, previousRecordDate, revisitToday, null);
	}

	/** 재진 날이 아닌 날(테스트 등) */
	SituationalQuestionContext(
		ChatDraft draft, LocalDate today, List<SituationalDay> recentDays, LocalDate previousRecordDate) {
		this(draft, today, recentDays, previousRecordDate, false);
	}

	/** 직전 기록이 recentDays 안에 있을 때(테스트 등). recentDays가 비어 있으면 처음 기록하는 날로 본다. */
	SituationalQuestionContext(ChatDraft draft, LocalDate today, List<SituationalDay> recentDays) {
		this(draft, today, recentDays, recentDays.isEmpty() ? null : recentDays.get(recentDays.size() - 1).date());
	}

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
