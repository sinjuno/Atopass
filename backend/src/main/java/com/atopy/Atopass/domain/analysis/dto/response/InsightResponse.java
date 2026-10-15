package com.atopy.Atopass.domain.analysis.dto.response;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.Symptom;
import java.time.LocalDate;

/**
 * 기록에서 발견한 변화. 의료적 판단이 아니라 기록된 점수의 통계적 차이만 설명한다.
 * type, message 외의 필드는 화면에서 그래프 구간을 표시할 때 쓸 수 있다.
 */
public record InsightResponse(
	String type,
	String message,
	BodyPart bodyPart,
	Symptom symptom,
	String direction,
	LocalDate startDate,
	LocalDate endDate
) {

	public static final String SYMPTOM_CHANGE = "SYMPTOM_CHANGE";

}
