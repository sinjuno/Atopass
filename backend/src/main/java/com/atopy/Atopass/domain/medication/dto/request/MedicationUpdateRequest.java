package com.atopy.Atopass.domain.medication.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 약 수정 요청. 바꿀 필드만 보낸다. 보내지 않은(null) 필드는 그대로 두고,
 * 용량·복용 시간 원문·메모를 비우려면 빈 문자열("")을 보낸다. 종료일은 중단 API로 정한다.
 */
public record MedicationUpdateRequest(
	@Size(max = 100, message = "약 이름은 100자 이하로 입력해 주세요.")
	String name,

	@Pattern(regexp = "oral|topical|other", message = "약 종류는 oral, topical, other 중 하나여야 합니다.")
	String type,

	@Size(max = 50, message = "용량은 50자 이하로 입력해 주세요.")
	String dose,

	List<@NotNull(message = "복용 시간대 항목이 비어 있습니다.")
		@Pattern(regexp = "morning|lunch|evening|bedtime", message = "복용 시간대는 morning, lunch, evening, bedtime 중에서 골라 주세요.")
		String> times,

	@Size(max = 100, message = "복용 시간 원문은 100자 이하로 입력해 주세요.")
	String timesText,

	LocalDate startDate,

	@Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
	String memo,

	Boolean isOtherCondition
) {

	public boolean isEmpty() {
		return name == null && type == null && dose == null && times == null && timesText == null
			&& startDate == null && memo == null && isOtherCondition == null;
	}

}
