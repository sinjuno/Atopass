package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinCondition;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * 상황 질문 조건 판정에 쓰는 지난 기록 하루치. 엔티티의 지연 로딩 컬렉션(부위·증상)을 트랜잭션 안에서 미리 꺼내 둔다.
 * skinSigns는 부위 증상이 합쳐진 그날의 증상 전체다.
 */
record SituationalDay(
	LocalDate date,
	Integer painIndex,
	BigDecimal sleepHours,
	Boolean medication,
	Set<BodyPart> bodyParts,
	Set<SkinSign> skinSigns
) {

	static SituationalDay from(SkinRecord record) {
		Set<BodyPart> parts = EnumSet.noneOf(BodyPart.class);
		record.getConditions().stream().map(SkinCondition::getBodyPart).forEach(parts::add);
		Set<SkinSign> signs = EnumSet.noneOf(SkinSign.class);
		signs.addAll(record.getSkinSigns());
		return new SituationalDay(record.getRecordDate(), record.getPainIndex(), record.getSleepHours(),
			record.getMedication(), parts, signs);
	}

}
