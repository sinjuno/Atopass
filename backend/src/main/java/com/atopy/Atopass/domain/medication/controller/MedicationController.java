package com.atopy.Atopass.domain.medication.controller;

import com.atopy.Atopass.domain.medication.dto.request.MedicationBulkRequest;
import com.atopy.Atopass.domain.medication.dto.request.MedicationRequest;
import com.atopy.Atopass.domain.medication.dto.request.MedicationStopRequest;
import com.atopy.Atopass.domain.medication.dto.request.MedicationUpdateRequest;
import com.atopy.Atopass.domain.medication.dto.response.MedicationExtractResponse;
import com.atopy.Atopass.domain.medication.dto.response.MedicationHistoryResponse;
import com.atopy.Atopass.domain.medication.dto.response.MedicationResponse;
import com.atopy.Atopass.domain.medication.service.MedicationExtractService;
import com.atopy.Atopass.domain.medication.service.MedicationService;
import com.atopy.Atopass.global.response.ApiResponse;
import com.atopy.Atopass.global.security.CurrentMember;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/medications")
@RequiredArgsConstructor
public class MedicationController {

	private final MedicationService medicationService;
	private final MedicationExtractService extractService;

	@GetMapping
	public ApiResponse<List<MedicationResponse>> list(@CurrentMember Long memberId, @RequestParam String status) {
		return ApiResponse.ok(medicationService.list(memberId, status));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<MedicationResponse>> create(
		@CurrentMember Long memberId, @Valid @RequestBody MedicationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(medicationService.create(memberId, request), "약이 추가되었습니다."));
	}

	@PostMapping("/bulk")
	public ResponseEntity<ApiResponse<List<MedicationResponse>>> createAll(
		@CurrentMember Long memberId, @Valid @RequestBody MedicationBulkRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(medicationService.createAll(memberId, request), "약이 저장되었습니다."));
	}

	/** 약 봉투 사진을 읽어 약 정보를 돌려준다. 저장하지 않는다. */
	@PostMapping(path = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<MedicationExtractResponse> extract(
		@CurrentMember Long memberId, @RequestParam(required = false) List<MultipartFile> files) {
		return ApiResponse.ok(extractService.extract(memberId, files));
	}

	@PatchMapping("/{medicationId}")
	public ApiResponse<MedicationResponse> update(
		@CurrentMember Long memberId, @PathVariable Long medicationId,
		@Valid @RequestBody MedicationUpdateRequest request) {
		return ApiResponse.ok(medicationService.update(memberId, medicationId, request), "약 정보가 수정되었습니다.");
	}

	@PostMapping("/{medicationId}/stop")
	public ApiResponse<MedicationResponse> stop(
		@CurrentMember Long memberId, @PathVariable Long medicationId, @Valid @RequestBody MedicationStopRequest request) {
		return ApiResponse.ok(medicationService.stop(memberId, medicationId, request), "복용을 중단했습니다.");
	}

	@GetMapping("/{medicationId}/history")
	public ApiResponse<List<MedicationHistoryResponse>> history(
		@CurrentMember Long memberId, @PathVariable Long medicationId) {
		return ApiResponse.ok(medicationService.history(memberId, medicationId));
	}

}
