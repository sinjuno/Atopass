package com.atopy.Skinlog.domain.report.service;

import com.atopy.Skinlog.domain.member.repository.MemberRepository;
import com.atopy.Skinlog.domain.record.entity.SkinRecord;
import com.atopy.Skinlog.domain.record.repository.RecordRepository;
import com.atopy.Skinlog.domain.report.dto.request.ReportCreateRequest;
import com.atopy.Skinlog.domain.report.dto.response.ReportResponse;
import com.atopy.Skinlog.domain.report.entity.Report;
import com.atopy.Skinlog.domain.report.repository.ReportRepository;
import com.atopy.Skinlog.domain.report.storage.ReportFileStorage;
import com.atopy.Skinlog.global.exception.BusinessException;
import com.atopy.Skinlog.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진료 참고용 보고서 생성과 PDF 제공.
 * PDF는 서버에서 동기로 만들고(기간이 길어도 최대 1년치라 수 초 이내), 30일 동안 파일로 보관한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	/** PDF 파일 보존 기간(일). 메타데이터는 계속 보존한다. */
	static final int FILE_RETENTION_DAYS = 30;

	/** 한 보고서에 담을 수 있는 최대 기간(일) */
	static final int MAX_PERIOD_DAYS = 366;

	private final ReportRepository reportRepository;
	private final RecordRepository recordRepository;
	private final MemberRepository memberRepository;
	private final ReportFileStorage fileStorage;
	private final ReportContentFactory contentFactory;
	private final ReportHtmlBuilder htmlBuilder;
	private final ReportPdfGenerator pdfGenerator;
	private final Clock clock;

	/** 내려줄 PDF와 파일 이름 */
	public record ReportFile(byte[] content, String fileName) {
	}

	@Transactional
	public ReportResponse create(Long memberId, ReportCreateRequest request) {
		validatePeriod(request.startDate(), request.endDate());
		boolean includePhotos = Boolean.TRUE.equals(request.includePhotos());
		List<SkinRecord> records = recordsIn(memberId, request.startDate(), request.endDate());

		Report report = reportRepository.save(Report.builder()
			.member(memberRepository.getReferenceById(memberId))
			.startDate(request.startDate())
			.endDate(request.endDate())
			.includePhotos(includePhotos)
			.build());
		storePdf(report, records);
		return ReportResponse.from(report);
	}

	public List<ReportResponse> list(Long memberId) {
		return reportRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
			.map(ReportResponse::from)
			.toList();
	}

	public ReportResponse get(Long memberId, Long reportId) {
		Report report = reportRepository.findByIdAndMemberId(reportId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
		return ReportResponse.from(report);
	}

	/**
	 * 소유자에게 PDF를 준다. 저장된 파일이 없으면(보존 기간이 지나 삭제된 경우 등) 현재 기록으로 다시 만든다.
	 * 다른 회원의 보고서는 존재 여부가 드러나지 않도록 404로 처리한다.
	 */
	@Transactional
	public ReportFile download(Long memberId, Long reportId) {
		Report report = reportRepository.findByIdAndMemberId(reportId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));

		Optional<byte[]> stored = report.getFileName() == null
			? Optional.empty() : fileStorage.load(report.getFileName());
		byte[] pdf = stored.orElseGet(() -> storePdf(report,
			recordsIn(memberId, report.getStartDate(), report.getEndDate())));
		return new ReportFile(pdf, downloadName(report));
	}

	/** 보존 기간이 지난 PDF 파일을 지운다. 보고서 정보는 남긴다. 지운 개수를 돌려준다. */
	@Transactional
	public int deleteExpiredFiles() {
		List<Report> expired = reportRepository
			.findByFileNameIsNotNullAndFileExpiresAtBefore(LocalDateTime.now(clock));
		for (Report report : expired) {
			fileStorage.delete(report.getFileName());
			report.detachFile();
		}
		return expired.size();
	}

	private byte[] storePdf(Report report, List<SkinRecord> records) {
		ReportContent content = contentFactory.create(records, report.getStartDate(), report.getEndDate(),
			report.isIncludePhotos(), LocalDate.now(clock));
		byte[] pdf = pdfGenerator.generate(htmlBuilder.build(content));
		report.attachFile(fileStorage.store(pdf),
			LocalDateTime.now(clock).plusDays(FILE_RETENTION_DAYS));
		return pdf;
	}

	/** 기간 안의 기록만 가져온다. 하나도 없으면 보고서를 만들 수 없다. */
	private List<SkinRecord> recordsIn(Long memberId, LocalDate startDate, LocalDate endDate) {
		List<SkinRecord> records = recordRepository
			.findByMemberIdAndRecordDateBetweenOrderByRecordDateAsc(memberId, startDate, endDate);
		if (records.isEmpty()) {
			throw new BusinessException(ErrorCode.NO_RECORDS_IN_PERIOD);
		}
		return records;
	}

	private void validatePeriod(LocalDate startDate, LocalDate endDate) {
		if (startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
		}
		if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_PERIOD_DAYS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
				"보고서 기간은 최대 " + MAX_PERIOD_DAYS + "일까지 선택할 수 있습니다.");
		}
	}

	private String downloadName(Report report) {
		return "skinlog-report-" + report.getStartDate() + "_" + report.getEndDate() + ".pdf";
	}

}
