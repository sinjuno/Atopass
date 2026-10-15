package com.atopy.Atopass.domain.record.dto.request;

import com.atopy.Atopass.domain.record.entity.Symptom;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.EnumMap;
import java.util.Map;

/** 증상별 점수. 기록하지 않은 증상은 생략하거나 null로 보낸다. */
public record SymptomsRequest(
	@Score Integer itching,
	@Score Integer stinging,
	@Score Integer redness,
	@Score Integer dryness,
	@Score Integer flaking,
	@Score Integer swelling,
	@Score Integer pain
) {

	/** 입력된(null이 아닌) 증상만 모은다. */
	public Map<Symptom, Integer> toScores() {
		Map<Symptom, Integer> scores = new EnumMap<>(Symptom.class);
		put(scores, Symptom.ITCHING, itching);
		put(scores, Symptom.STINGING, stinging);
		put(scores, Symptom.REDNESS, redness);
		put(scores, Symptom.DRYNESS, dryness);
		put(scores, Symptom.FLAKING, flaking);
		put(scores, Symptom.SWELLING, swelling);
		put(scores, Symptom.PAIN, pain);
		return scores;
	}

	@JsonIgnore
	@AssertTrue(message = "증상 점수를 하나 이상 입력해 주세요.")
	public boolean isAtLeastOneSymptom() {
		return !toScores().isEmpty();
	}

	private static void put(Map<Symptom, Integer> scores, Symptom symptom, Integer value) {
		if (value != null) {
			scores.put(symptom, value);
		}
	}

}
