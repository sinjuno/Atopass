package com.atopy.Atopass.domain.record.service;

import com.atopy.Atopass.domain.record.dto.response.PhotoSummary;
import com.atopy.Atopass.domain.record.entity.Photo;
import com.atopy.Atopass.domain.record.entity.PhotoType;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.event.RecordDeletedEvent;
import com.atopy.Atopass.domain.record.repository.PhotoRepository;
import com.atopy.Atopass.domain.record.repository.RecordRepository;
import com.atopy.Atopass.domain.record.storage.PhotoStorage;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoService {

	private static final int MAX_PHOTOS_PER_RECORD = 5;

	private final RecordRepository recordRepository;
	private final PhotoRepository photoRepository;
	private final PhotoStorage photoStorage;

	@Transactional
	public PhotoSummary upload(Long memberId, Long recordId, MultipartFile file) {
		findOwnedRecord(memberId, recordId);

		if (photoRepository.countByRecordId(recordId) >= MAX_PHOTOS_PER_RECORD) {
			throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
		}

		byte[] content = readBytes(file);
		PhotoType type = PhotoType.detect(content)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_FILE_TYPE));

		String storageKey = photoStorage.store(content, type.extension());
		Photo photo = Photo.builder()
			.recordId(recordId)
			.storageKey(storageKey)
			.originalFilename(file.getOriginalFilename())
			.contentType(type.contentType())
			.size(content.length)
			.build();
		return PhotoSummary.from(photoRepository.save(photo));
	}

	public PhotoContent getContent(Long memberId, Long recordId, Long photoId) {
		findOwnedRecord(memberId, recordId);
		Photo photo = findOwnedPhoto(recordId, photoId);
		byte[] content = photoStorage.load(photo.getStorageKey())
			.orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
		return new PhotoContent(content, photo.getContentType());
	}

	@Transactional
	public void delete(Long memberId, Long recordId, Long photoId) {
		findOwnedRecord(memberId, recordId);
		Photo photo = findOwnedPhoto(recordId, photoId);
		photoRepository.delete(photo);
		photoStorage.delete(photo.getStorageKey());
	}

	/** 기록이 삭제되면 그 기록에 달린 사진 행과 파일도 함께 정리한다. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void onRecordDeleted(RecordDeletedEvent event) {
		List<Photo> photos = photoRepository.findByRecordIdOrderByCreatedAtAsc(event.recordId());
		photoRepository.deleteAll(photos);
		photos.forEach(photo -> photoStorage.delete(photo.getStorageKey()));
	}

	/** 다른 회원의 기록은 존재 여부가 드러나지 않도록 없는 기록과 같게 404로 처리한다. */
	private SkinRecord findOwnedRecord(Long memberId, Long recordId) {
		return recordRepository.findByIdAndMemberId(recordId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
	}

	private Photo findOwnedPhoto(Long recordId, Long photoId) {
		return photoRepository.findByIdAndRecordId(photoId, recordId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
	}

	private byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("업로드된 파일을 읽지 못했습니다.", e);
		}
	}

}
