package com.atopy.Atopass.domain.chatbot.dto.request;

import com.atopy.Atopass.domain.chatbot.entity.ChatMode;
import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
	@NotNull(message = "mode를 입력해 주세요. (RECORD 또는 QUERY)")
	ChatMode mode
) {
}
