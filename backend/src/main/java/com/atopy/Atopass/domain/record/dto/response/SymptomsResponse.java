package com.atopy.Atopass.domain.record.dto.response;

import com.atopy.Atopass.domain.record.entity.Symptom;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** 증상별 점수. 기록하지 않은 증상은 응답에서 생략한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SymptomsResponse(
	Integer itching,
	Integer stinging,
	Integer redness,
	Integer dryness,
	Integer flaking,
	Integer swelling,
	Integer pain
) {

	public static SymptomsResponse from(Map<Symptom, Integer> scores) {
		return new SymptomsResponse(
			scores.get(Symptom.ITCHING),
			scores.get(Symptom.STINGING),
			scores.get(Symptom.REDNESS),
			scores.get(Symptom.DRYNESS),
			scores.get(Symptom.FLAKING),
			scores.get(Symptom.SWELLING),
			scores.get(Symptom.PAIN));
	}

}
