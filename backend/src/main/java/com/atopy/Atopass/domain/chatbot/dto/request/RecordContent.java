package com.atopy.Atopass.domain.chatbot.dto.request;

import com.atopy.Atopass.domain.record.dto.request.LifestyleRequest;
import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.request.SkinConditionRequest;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 사용자가 확인한 기록 내용. 피부 기록 생성의 본문에서 날짜만 뺀 구조이며 같은 검증 규칙을 쓴다.
 * 단, 특이사항은 채팅으로 입력받으므로 채팅 메시지와 같은 500자로 제한한다.
 */
public record RecordContent(
	@Valid
	List<@NotNull(message = "부위별 증상 항목이 비어 있습니다.") @Valid SkinConditionRequest> skinConditions,

	@Min(value = 0, message = "불편함 지수는 0~10 범위여야 합니다.")
	@Max(value = 10, message = "불편함 지수는 0~10 범위여야 합니다.")
	Integer painIndex,

	@DecimalMin(value = "0.0", message = "수면 시간은 0 이상이어야 합니다.")
	@DecimalMax(value = "24.0", message = "수면 시간은 24 이하여야 합니다.")
	@Digits(integer = 2, fraction = 1, message = "수면 시간은 소수점 한 자리까지 입력할 수 있습니다.")
	BigDecimal sleepHours,

	Boolean medication,

	List<@NotNull(message = "증상 항목이 비어 있습니다.") SkinSign> skinSigns,

	@Valid
	LifestyleRequest lifestyle,

	@Size(max = 500, message = "특이사항은 500자 이하여야 합니다.")
	String notes
) {

	/** 일반 피부 기록 생성과 같은 로직을 쓰기 위해 날짜를 붙여 변환한다. */
	public RecordRequest toRecordRequest(LocalDate recordDate) {
		return new RecordRequest(
			recordDate, skinConditions, painIndex, sleepHours, medication, skinSigns, lifestyle, notes);
	}

	@JsonIgnore
	@AssertTrue(message = "부위별 증상이나 불편함 지수를 하나 이상 입력해 주세요.")
	public boolean isSkinConditionsOrPainIndexPresent() {
		return painIndex != null || (skinConditions != null && !skinConditions.isEmpty());
	}

	@JsonIgnore
	@AssertTrue(message = "같은 부위를 중복해서 입력할 수 없습니다.")
	public boolean isBodyPartsUnique() {
		if (skinConditions == null) {
			return true;
		}
		Set<Object> seen = new HashSet<>();
		return skinConditions.stream()
			.filter(Objects::nonNull)
			.map(SkinConditionRequest::bodyPart)
			.filter(Objects::nonNull)
			.allMatch(seen::add);
	}

}
