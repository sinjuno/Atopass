package com.atopy.Atopass.domain.medication.entity;

import java.util.Arrays;
import java.util.Optional;

/** 복용 시간대. API에서는 소문자 코드(morning, lunch, evening, bedtime)로 주고받는다. */
public enum MedicationTime {

	MORNING("morning"),
	LUNCH("lunch"),
	EVENING("evening"),
	BEDTIME("bedtime");

	private final String code;

	MedicationTime(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static Optional<MedicationTime> fromCode(String code) {
		return Arrays.stream(values()).filter(time -> time.code.equals(code)).findFirst();
	}

}
