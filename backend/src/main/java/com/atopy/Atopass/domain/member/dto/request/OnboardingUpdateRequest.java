package com.atopy.Atopass.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 약 등록 단계 변경 요청. 약 등록을 마쳤으면 done, 건너뛰었으면 skipped. pending으로 되돌릴 수는 없다. */
public record OnboardingUpdateRequest(
	@NotBlank(message = "약 등록 단계를 입력해 주세요.")
	@Pattern(regexp = "done|skipped", message = "약 등록 단계는 done 또는 skipped여야 합니다.")
	String medicationStep
) {
}
