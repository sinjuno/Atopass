package com.atopy.Skinlog.domain.analysis.dto.response;

/** 기록이 없는 날짜는 해당 키 값이 null이다. */
public record ComparisonResponse(RecordSnapshotResponse A, RecordSnapshotResponse B) {
}
