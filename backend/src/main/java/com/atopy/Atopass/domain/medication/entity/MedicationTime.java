package com.atopy.Atopass.domain.medication.entity;

import java.util.Arrays;
import java.util.Optional;

/** 복용 시간대. API에서는 소문자 코드(morning, lunch, evening, bedtime)로 주고받는다. */
public enum MedicationTime {

	MORNING("morning", "아침"),
	LUNCH("lunch", "점심"),
	EVENING("evening", "저녁"),
	BEDTIME("bedtime", "자기 전");

	private final String code;
	private final String label;

	MedicationTime(String code, String label) {
		this.code = code;
		this.label = label;
	}

	/** 화면과 대화에 보여 주는 말 */
	public String label() {
		return label;
	}

	public String code() {
		return code;
	}

	public static Optional<MedicationTime> fromCode(String code) {
		return Arrays.stream(values()).filter(time -> time.code.equals(code)).findFirst();
	}

}
