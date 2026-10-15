package com.atopy.Atopass.domain.record.controller;

import com.atopy.Atopass.domain.record.dto.response.PhotoSummary;
import com.atopy.Atopass.domain.record.service.PhotoContent;
import com.atopy.Atopass.domain.record.service.PhotoService;
import com.atopy.Atopass.global.response.ApiResponse;
import com.atopy.Atopass.global.security.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/records/{recordId}/photos")
@RequiredArgsConstructor
public class PhotoController {

	private final PhotoService photoService;

	@PostMapping
	public ResponseEntity<ApiResponse<PhotoSummary>> upload(
		@CurrentMember Long memberId, @PathVariable Long recordId, @RequestParam MultipartFile file) {
		PhotoSummary summary = photoService.upload(memberId, recordId, file);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(summary, "사진이 업로드되었습니다."));
	}

	@GetMapping("/{photoId}")
	public ResponseEntity<byte[]> get(
		@CurrentMember Long memberId, @PathVariable Long recordId, @PathVariable Long photoId) {
		PhotoContent content = photoService.getContent(memberId, recordId, photoId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(content.contentType()))
			.body(content.bytes());
	}

	@DeleteMapping("/{photoId}")
	public ResponseEntity<Void> delete(
		@CurrentMember Long memberId, @PathVariable Long recordId, @PathVariable Long photoId) {
		photoService.delete(memberId, recordId, photoId);
		return ResponseEntity.noContent().build();
	}

}
