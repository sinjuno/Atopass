package com.atopy.Atopass.domain.medication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 약 추가 요청. 이름, 종류, 시작일은 꼭 있어야 하고 나머지는 비워도 된다.
 *
 * @param type 종류 코드 (oral, topical, other)
 * @param times 복용 시간대 코드 (morning, lunch, evening, bedtime). 비어 있을 수 있다.
 * @param source 등록 경로 코드 (signup, mypage, photo, revisit). 생략하면 mypage
 */
public record MedicationRequest(
	@NotBlank(message = "약 이름을 입력해 주세요.")
	@Size(max = 100, message = "약 이름은 100자 이하로 입력해 주세요.")
	String name,

	@NotBlank(message = "약 종류를 선택해 주세요.")
	@Pattern(regexp = "oral|topical|other", message = "약 종류는 oral, topical, other 중 하나여야 합니다.")
	String type,

	@Size(max = 50, message = "용량은 50자 이하로 입력해 주세요.")
	String dose,

	List<@NotNull(message = "복용 시간대 항목이 비어 있습니다.")
		@Pattern(regexp = "morning|lunch|evening|bedtime", message = "복용 시간대는 morning, lunch, evening, bedtime 중에서 골라 주세요.")
		String> times,

	@Size(max = 100, message = "복용 시간 원문은 100자 이하로 입력해 주세요.")
	String timesText,

	@NotNull(message = "시작일을 입력해 주세요.")
	LocalDate startDate,

	LocalDate endDate,

	@Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
	String memo,

	Boolean isOtherCondition,

	@Pattern(regexp = "signup|mypage|photo|revisit", message = "등록 경로는 signup, mypage, photo, revisit 중 하나여야 합니다.")
	String source
) {
}
