package com.atopy.Atopass.domain.chatbot.dto.response;

import com.atopy.Atopass.domain.record.dto.response.LifestyleResponse;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import java.math.BigDecimal;
import java.util.List;

/** 챗봇이 대화로 모은 기록 초안. 사용자가 확인한 뒤 기록 저장 API로 보낸다. */
public record ChatDraftResponse(
	List<SkinConditionResponse> skinConditions,
	BigDecimal sleepHours,
	LifestyleResponse lifestyle,
	String notes
) {
}
