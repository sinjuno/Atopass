package com.atopy.Atopass.domain.record.dto.response;

import com.atopy.Atopass.domain.record.entity.MedicationUse;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinTrend;
import java.util.Map;
import java.util.TreeMap;

/** 하루 점검 항목. 기록하지 않은 항목은 null로 내려간다. 연결 질문 답은 질문 ID 순서다. */
public record DailyCheckResponse(
	SkinTrend skinTrend,
	Integer itchScore,
	Boolean sleepDisturbed,
	Boolean moisturizer,
	MedicationUse medicationUse,
	Map<String, String> linkedAnswers
) {

	public static DailyCheckResponse from(SkinRecord record) {
		return new DailyCheckResponse(record.getSkinTrend(), record.getItchScore(), record.getSleepDisturbed(),
			record.getMoisturizer(), record.getMedicationUse(), new TreeMap<>(record.getLinkedAnswers()));
	}

}
