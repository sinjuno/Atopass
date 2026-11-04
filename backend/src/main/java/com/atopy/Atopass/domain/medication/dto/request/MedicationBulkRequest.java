package com.atopy.Atopass.domain.medication.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 약 여러 개 한 번에 저장 요청. 각 약의 source는 무시하고 아래 source를 모두에 똑같이 쓴다.
 */
public record MedicationBulkRequest(
	@NotEmpty(message = "저장할 약이 없습니다.")
	@Size(max = 20, message = "한 번에 20개까지 저장할 수 있습니다.")
	List<@NotNull(message = "약 항목이 비어 있습니다.") @Valid MedicationRequest> items,

	@NotBlank(message = "등록 경로를 입력해 주세요.")
	@Pattern(regexp = "signup|mypage|photo|revisit", message = "등록 경로는 signup, mypage, photo, revisit 중 하나여야 합니다.")
	String source
) {
}
