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
 * @param editing              그날 기록이 이미 저장되어, 이제 글로 고치면 바로 저장되는 대화인지
 * @param recordId             이 대화로 저장한(또는 고치는) 기록의 ID. 아직 저장 전이면 null
 */
public record ChatReplyResponse(
	String reply,
	boolean understood,
	List<String> pendingFields,
	String askingField,
	boolean requiresConfirmation,
	ChatDraftResponse draft,
	boolean editing,
	Long recordId
) {

	/** 기록을 저장한 뒤 그 ID를 붙인다. */
	public ChatReplyResponse withRecordId(Long id) {
		return new ChatReplyResponse(reply, understood, pendingFields, askingField, requiresConfirmation, draft,
			id != null || editing, id);
	}
}
