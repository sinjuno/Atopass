package com.atopy.Atopass.domain.chatbot.dto.response;

import java.util.List;

/**
 * 세션의 현재 기록 초안과 진행 상태. 새로고침·재진입 뒤 일일 총정리 화면이 초안을 다시 채울 때 쓴다.
 * 값은 마지막 메시지 응답의 draft, pendingFields, askingField, requiresConfirmation, editing과 같다.
 */
public record ChatDraftStateResponse(
	ChatDraftResponse draft,
	List<String> pendingFields,
	String askingField,
	boolean requiresConfirmation,
	boolean editing
) {
}
