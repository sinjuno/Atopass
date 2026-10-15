package com.atopy.Atopass.domain.record.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record RecordPageResponse(
	List<RecordSummaryResponse> content,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public static RecordPageResponse from(Page<RecordSummaryResponse> page) {
		return new RecordPageResponse(
			page.getContent(), page.getNumber(), page.getSize(),
			page.getTotalElements(), page.getTotalPages());
	}

}
