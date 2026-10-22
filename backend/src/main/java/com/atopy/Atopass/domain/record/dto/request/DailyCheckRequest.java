package com.atopy.Atopass.domain.record.dto.request;

import com.atopy.Atopass.domain.record.entity.MedicationUse;
import com.atopy.Atopass.domain.record.entity.SkinTrend;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 하루 점검 항목. 기록하지 않은 항목은 null.
 *
 * @param linkedAnswers 연결 질문의 답. 질문 ID(C01~C16) → 고른 선택지 또는 직접 쓴 글
 */
public record DailyCheckRequest(
	SkinTrend skinTrend,

	@Min(value = 0, message = "가려움 정도는 0~10 범위여야 합니다.")
	@Max(value = 10, message = "가려움 정도는 0~10 범위여야 합니다.")
	Integer itchScore,

	Boolean sleepDisturbed,

	Boolean moisturizer,

	MedicationUse medicationUse,

	@Size(max = 20, message = "연결 질문 답은 20개까지 저장할 수 있습니다.")
	Map<
		@Pattern(regexp = "C\\d{2}", message = "연결 질문 ID가 올바르지 않습니다.") String,
		@NotBlank(message = "연결 질문 답이 비어 있습니다.") @Size(max = 200, message = "연결 질문 답은 200자 이하여야 합니다.") String
	> linkedAnswers
) {
}
