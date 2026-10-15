package com.atopy.Skinlog.domain.analysis.controller;

import com.atopy.Skinlog.domain.analysis.dto.response.AnalysisSummaryResponse;
import com.atopy.Skinlog.domain.analysis.dto.response.ComparisonResponse;
import com.atopy.Skinlog.domain.analysis.dto.response.TrendResponse;
import com.atopy.Skinlog.domain.analysis.service.AnalysisService;
import com.atopy.Skinlog.domain.record.entity.BodyPart;
import com.atopy.Skinlog.domain.record.entity.Symptom;
import com.atopy.Skinlog.global.exception.BusinessException;
import com.atopy.Skinlog.global.exception.ErrorCode;
import com.atopy.Skinlog.global.response.ApiResponse;
import com.atopy.Skinlog.global.security.CurrentMember;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

	private final AnalysisService analysisService;

	@GetMapping("/summary")
	public ApiResponse<AnalysisSummaryResponse> summary(
		@CurrentMember Long memberId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return ApiResponse.ok(analysisService.summary(memberId, startDate, endDate));
	}

	@GetMapping("/trends")
	public ApiResponse<TrendResponse> trends(
		@CurrentMember Long memberId,
		@RequestParam BodyPart bodyPart,
		@RequestParam String symptom,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return ApiResponse.ok(analysisService.trends(memberId, bodyPart, parseSymptom(symptom), startDate, endDate));
	}

	/** symptom은 소문자 키(itching 등)로 받는다. 대문자 enum 이름과 달라 전용 변환이 필요하다. */
	private Symptom parseSymptom(String key) {
		try {
			return Symptom.fromKey(key);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "symptom 값이 올바르지 않습니다.");
		}
	}

	@GetMapping("/comparison")
	public ApiResponse<ComparisonResponse> comparison(
		@CurrentMember Long memberId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateA,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateB) {
		return ApiResponse.ok(analysisService.comparison(memberId, dateA, dateB));
	}

}
