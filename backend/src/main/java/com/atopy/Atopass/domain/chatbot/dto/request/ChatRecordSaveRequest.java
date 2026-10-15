package com.atopy.Atopass.domain.chatbot.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 챗봇이 정리한 초안을 사용자가 확인한 뒤 기록으로 저장하는 요청.
 * confirmed가 true가 아니면 저장하지 않는다.
 */
public record ChatRecordSaveRequest(
	@NotNull(message = "기록 날짜를 입력해 주세요.")
	LocalDate recordDate,

	@NotNull(message = "확인 여부를 입력해 주세요.")
	Boolean confirmed,

	@NotNull(message = "저장할 기록 내용을 입력해 주세요.")
	@Valid
	RecordContent record
) {
}
