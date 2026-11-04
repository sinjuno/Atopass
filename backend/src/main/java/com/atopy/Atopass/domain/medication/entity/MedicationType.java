package com.atopy.Atopass.domain.medication.entity;

import java.util.Arrays;
import java.util.Optional;

/** 약 종류. API에서는 소문자 코드(oral, topical, other)로 주고받는다. */
public enum MedicationType {

	ORAL("oral"),
	TOPICAL("topical"),
	OTHER("other");

	private final String code;

	MedicationType(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static Optional<MedicationType> fromCode(String code) {
		return Arrays.stream(values()).filter(type -> type.code.equals(code)).findFirst();
	}

}
