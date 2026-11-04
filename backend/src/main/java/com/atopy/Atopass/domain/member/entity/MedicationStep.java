package com.atopy.Atopass.domain.member.entity;

import java.util.Arrays;
import java.util.Optional;

/** 약 등록 화면을 어디까지 거쳤는지. 기존 회원을 포함해 처음에는 모두 PENDING이다. */
public enum MedicationStep {

	PENDING("pending"),
	DONE("done"),
	SKIPPED("skipped");

	private final String code;

	MedicationStep(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static Optional<MedicationStep> fromCode(String code) {
		return Arrays.stream(values()).filter(step -> step.code.equals(code)).findFirst();
	}

}
