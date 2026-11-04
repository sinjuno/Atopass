package com.atopy.Atopass.domain.member.dto.response;

import com.atopy.Atopass.domain.member.entity.MedicationStep;

/** 약 등록 단계. pending, done, skipped 중 하나 */
public record OnboardingResponse(String medicationStep) {

	public static OnboardingResponse of(MedicationStep step) {
		return new OnboardingResponse(step.code());
	}

}
