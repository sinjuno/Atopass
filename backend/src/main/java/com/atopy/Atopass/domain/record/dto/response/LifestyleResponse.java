package com.atopy.Atopass.domain.record.dto.response;

import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinRecord;

/** 생활습관. 기록하지 않은 항목은 null로 내려간다. */
public record LifestyleResponse(Boolean alcohol, OutdoorActivity outdoorActivity) {

	public static LifestyleResponse from(SkinRecord record) {
		return new LifestyleResponse(record.getAlcohol(), record.getOutdoorActivity());
	}

}
