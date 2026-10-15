package com.atopy.Skinlog.domain.report.service;

import com.atopy.Skinlog.domain.record.repository.PhotoRepository;
import com.atopy.Skinlog.domain.record.storage.PhotoStorage;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기록에 올라간 사진을 사진 저장소에서 읽어 보고서에 넣는다.
 * 파일이 사라진 사진은 건너뛰어, 사진 한 장 때문에 보고서 전체가 실패하지 않게 한다.
 */
@Component
@RequiredArgsConstructor
class StoredReportPhotoSource implements ReportPhotoSource {

	private final PhotoRepository photoRepository;
	private final PhotoStorage photoStorage;

	@Override
	public List<ReportPhoto> photosOf(Long recordId) {
		return photoRepository.findByRecordIdOrderByCreatedAtAsc(recordId).stream()
			.map(photo -> photoStorage.load(photo.getStorageKey())
				.map(data -> new ReportPhoto(photo.getContentType(), data)))
			.flatMap(Optional::stream)
			.toList();
	}

}
