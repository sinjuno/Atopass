package com.atopy.Skinlog.domain.chatbot.dto.response;

import java.util.List;

/**
 * 챗봇 응답.
 *
 * @param understood           입력을 해석했는지. false이면 같은 내용을 다시 묻는 응답이다.
 * @param pendingFields        아직 입력되지 않은 항목 (painIndex, symptoms, sleepHours, medication, skinSigns, notes)
 * @param requiresConfirmation true이면 기록 초안을 확인(일일 총정리)하는 단계로 넘어간다.
 * @param draft                지금까지 모은 기록 초안. 질의 모드에서는 null
 */
public record ChatReplyResponse(
	String reply,
	boolean understood,
	List<String> pendingFields,
	boolean requiresConfirmation,
	ChatDraftResponse draft
) {
}
