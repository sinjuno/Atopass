package com.atopy.Atopass.domain.chatbot.dto.response;

import java.time.LocalDateTime;

/**
 * 챗봇 이용 동의 상태.
 *
 * @param agreed   동의했는지
 * @param agreedAt 동의한 시각. 동의하지 않았으면 null
 */
public record ChatConsentResponse(boolean agreed, LocalDateTime agreedAt) {
}
