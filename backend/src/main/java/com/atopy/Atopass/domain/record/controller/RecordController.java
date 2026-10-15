package com.atopy.Atopass.domain.record.controller;

import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.response.RecordCreatedResponse;
import com.atopy.Atopass.domain.record.dto.response.RecordDetailResponse;
import com.atopy.Atopass.domain.record.dto.response.RecordPageResponse;
import com.atopy.Atopass.domain.record.service.RecordService;
import com.atopy.Atopass.global.response.ApiResponse;
import com.atopy.Atopass.global.security.CurrentMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/records")
@RequiredArgsConstructor
public class RecordController {

	private final RecordService recordService;

	@GetMapping
	public ApiResponse<RecordPageResponse> list(
		@CurrentMember Long memberId,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
		@RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
		@RequestParam(defaultValue = "10")
		@Min(value = 1, message = "size는 1 이상이어야 합니다.")
		@Max(value = 100, message = "size는 100 이하여야 합니다.") int size) {
		return ApiResponse.ok(recordService.list(memberId, startDate, endDate, page, size));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<RecordCreatedResponse>> create(
		@CurrentMember Long memberId, @Valid @RequestBody RecordRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(recordService.create(memberId, request), "피부 기록이 저장되었습니다."));
	}

	@GetMapping("/{recordId}")
	public ApiResponse<RecordDetailResponse> get(@CurrentMember Long memberId, @PathVariable Long recordId) {
		return ApiResponse.ok(recordService.get(memberId, recordId));
	}

	@PutMapping("/{recordId}")
	public ApiResponse<RecordDetailResponse> update(
		@CurrentMember Long memberId, @PathVariable Long recordId, @Valid @RequestBody RecordRequest request) {
		return ApiResponse.ok(recordService.update(memberId, recordId, request), "피부 기록이 수정되었습니다.");
	}

	@DeleteMapping("/{recordId}")
	public ResponseEntity<Void> delete(@CurrentMember Long memberId, @PathVariable Long recordId) {
		recordService.delete(memberId, recordId);
		return ResponseEntity.noContent().build();
	}

}
