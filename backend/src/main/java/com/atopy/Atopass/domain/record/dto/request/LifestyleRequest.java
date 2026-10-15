package com.atopy.Atopass.domain.record.dto.request;

import com.atopy.Atopass.domain.record.entity.OutdoorActivity;

/** 생활습관. 기록하지 않은 항목은 null. */
public record LifestyleRequest(Boolean alcohol, OutdoorActivity outdoorActivity) {
}
