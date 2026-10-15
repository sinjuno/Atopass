package com.atopy.Atopass.domain.chatbot.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
	@NotBlank(message = "메시지를 입력해 주세요.")
	@Size(max = 500, message = "메시지는 500자 이하여야 합니다.")
	String message
) {
}
