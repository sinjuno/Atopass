package com.atopy.Atopass.domain.medication.dto.response;

import com.atopy.Atopass.domain.medication.entity.Medication;
import com.atopy.Atopass.domain.medication.entity.MedicationTime;
import java.time.LocalDate;
import java.util.List;

/** 약 한 개. id는 공통 약속대로 숫자를 문자열로 준다. */
public record MedicationResponse(
	String id,
	String name,
	String type,
	String dose,
	List<String> times,
	String timesText,
	LocalDate startDate,
	LocalDate endDate,
	String memo,
	boolean isOtherCondition,
	String source
) {

	public static MedicationResponse from(Medication medication) {
		return new MedicationResponse(
			String.valueOf(medication.getId()),
			medication.getName(),
			medication.getType().code(),
			medication.getDose(),
			medication.timeList().stream().map(MedicationTime::code).toList(),
			medication.getTimesText(),
			medication.getStartDate(),
			medication.getEndDate(),
			medication.getMemo(),
			medication.isOtherCondition(),
			medication.getSource().code());
	}

}
