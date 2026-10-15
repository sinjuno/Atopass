package com.atopy.Atopass.domain.record.dto.request;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 피부 기록 생성·수정 요청. PUT은 전체 내용을 보낸다. */
public record RecordRequest(
	@NotNull(message = "기록 날짜를 입력해 주세요.")
	LocalDate recordDate,

	@NotEmpty(message = "부위별 증상을 하나 이상 입력해 주세요.")
	@Valid
	List<@NotNull(message = "부위별 증상 항목이 비어 있습니다.") @Valid SkinConditionRequest> skinConditions,

	@DecimalMin(value = "0.0", message = "수면 시간은 0 이상이어야 합니다.")
	@DecimalMax(value = "24.0", message = "수면 시간은 24 이하여야 합니다.")
	@Digits(integer = 2, fraction = 1, message = "수면 시간은 소수점 한 자리까지 입력할 수 있습니다.")
	BigDecimal sleepHours,

	@Valid
	LifestyleRequest lifestyle,

	@Size(max = 2000, message = "특이사항은 2000자 이하여야 합니다.")
	String notes
) {

	@JsonIgnore
	@AssertTrue(message = "같은 부위를 중복해서 입력할 수 없습니다.")
	public boolean isBodyPartsUnique() {
		if (skinConditions == null) {
			return true;
		}
		Set<BodyPart> seen = new HashSet<>();
		return skinConditions.stream()
			.filter(Objects::nonNull)
			.map(SkinConditionRequest::bodyPart)
			.filter(Objects::nonNull)
			.allMatch(seen::add);
	}

}
