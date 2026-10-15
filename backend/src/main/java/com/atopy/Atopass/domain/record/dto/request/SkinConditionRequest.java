package com.atopy.Atopass.domain.record.dto.request;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SkinConditionRequest(
	@NotNull(message = "신체 부위를 입력해 주세요.")
	BodyPart bodyPart,

	@NotNull(message = "증상 점수를 입력해 주세요.")
	@Valid
	SymptomsRequest symptoms
) {
}
