package com.atopy.Atopass.domain.chatbot.dto.response;

import java.util.List;

/**
 * 챗봇 응답.
 *
 * @param understood           입력을 해석했는지. false이면 같은 내용을 다시 묻는 응답이다.
 * @param pendingFields        아직 입력되지 않은 항목 (painIndex, symptoms, sleepHours, medication, skinSigns, notes)
 * @param askingField          지금 묻는 항목. 화면은 이 값에 맞춰 선택지를 보여 준다. 묻는 것이 없거나 질의 모드면 null
 * @param requiresConfirmation true이면 기록 초안을 확인(일일 총정리)하는 단계로 넘어간다.
 * @param draft                지금까지 모은 기록 초안. 질의 모드에서는 null
 * @param editing              이미 저장된 그날 기록을 고치는 대화인지. true이면 확인 단계에서 챗봇 화면에서 바로 저장한다.
 */
public record ChatReplyResponse(
	String reply,
	boolean understood,
	List<String> pendingFields,
	String askingField,
	boolean requiresConfirmation,
	ChatDraftResponse draft,
	boolean editing
) {
}
