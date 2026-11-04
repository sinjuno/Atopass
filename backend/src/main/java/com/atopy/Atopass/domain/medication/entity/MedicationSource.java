package com.atopy.Atopass.domain.medication.entity;

import java.util.Arrays;
import java.util.Optional;

/** 약을 등록한 경로. API에서는 소문자 코드(signup, mypage, photo, revisit)로 주고받는다. */
public enum MedicationSource {

	SIGNUP("signup"),
	MYPAGE("mypage"),
	PHOTO("photo"),
	REVISIT("revisit");

	private final String code;

	MedicationSource(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static Optional<MedicationSource> fromCode(String code) {
		return Arrays.stream(values()).filter(source -> source.code.equals(code)).findFirst();
	}

}
