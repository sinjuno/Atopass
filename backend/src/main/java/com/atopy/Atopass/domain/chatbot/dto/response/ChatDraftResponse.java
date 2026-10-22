package com.atopy.Atopass.domain.chatbot.dto.response;

import com.atopy.Atopass.domain.record.dto.response.LifestyleResponse;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.math.BigDecimal;
import java.util.List;

/** 챗봇이 대화로 모은 기록 초안. 사용자가 확인한 뒤 기록 저장 API로 보낸다. */
public record ChatDraftResponse(
	List<SkinConditionResponse> skinConditions,
	Integer painIndex,
	BigDecimal sleepHours,
	/** 수면 점수 1~5. 수면 시간이 없으면 null */
	Integer sleepScore,
	Boolean medication,
	/** 나타난 증상. 아직 묻지 않았으면 null, "없음"이면 빈 배열 */
	List<SkinSign> skinSigns,
	LifestyleResponse lifestyle,
	String notes
) {
}
