package com.atopy.Skinlog.domain.record.dto.response;

import com.atopy.Skinlog.domain.record.entity.Photo;
import java.time.LocalDateTime;

/** 기록에 연결된 사진 요약. */
public record PhotoSummary(Long photoId, LocalDateTime uploadedAt) {

	public static PhotoSummary from(Photo photo) {
		return new PhotoSummary(photo.getId(), photo.getCreatedAt());
	}

}
