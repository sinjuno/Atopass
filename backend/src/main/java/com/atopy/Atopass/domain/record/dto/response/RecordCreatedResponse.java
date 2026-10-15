package com.atopy.Atopass.domain.record.dto.response;

import com.atopy.Atopass.domain.record.entity.SkinRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record RecordCreatedResponse(Long recordId, LocalDate recordDate, LocalDateTime createdAt) {

	public static RecordCreatedResponse from(SkinRecord record) {
		return new RecordCreatedResponse(record.getId(), record.getRecordDate(), record.getCreatedAt());
	}

}
