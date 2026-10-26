package com.atopy.Atopass.domain.record.service;

import com.atopy.Atopass.domain.chatbot.service.SituationalAnswerReader;
import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.domain.member.repository.MemberRepository;
import com.atopy.Atopass.domain.record.dto.request.LifestyleRequest;
import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.request.SkinConditionRequest;
import com.atopy.Atopass.domain.record.dto.response.PhotoSummary;
import com.atopy.Atopass.domain.record.dto.response.RecordCreatedResponse;
import com.atopy.Atopass.domain.record.dto.response.RecordDetailResponse;
import com.atopy.Atopass.domain.record.dto.response.RecordPageResponse;
import com.atopy.Atopass.domain.record.dto.response.RecordSummaryResponse;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.record.entity.Symptom;
import com.atopy.Atopass.domain.record.event.RecordDeletedEvent;
import com.atopy.Atopass.domain.record.repository.PhotoRepository;
import com.atopy.Atopass.domain.record.repository.RecordRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordService {

	private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
	private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

	private final RecordRepository recordRepository;
	private final MemberRepository memberRepository;
	private final PhotoRepository photoRepository;
	private final SituationalAnswerReader situationalAnswerReader;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public RecordCreatedResponse create(Long memberId, RecordRequest request) {
		if (recordRepository.existsByMemberIdAndRecordDate(memberId, request.recordDate())) {
			throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS);
		}
		Member member = memberRepository.getReferenceById(memberId);
		LifestyleRequest lifestyle = request.lifestyle();
		SkinRecord record = SkinRecord.builder()
			.member(member)
			.recordDate(request.recordDate())
			.painIndex(request.painIndex())
			.sleepHours(scale(request.sleepHours()))
			.medication(request.medication())
			.alcohol(lifestyle == null ? null : lifestyle.alcohol())
			.outdoorActivity(lifestyle == null ? null : lifestyle.outdoorActivity())
			.notes(blankToNull(request.notes()))
			.build();
		record.replaceConditions(toConditions(request), toConditionSigns(request));
		record.replaceSkinSigns(toSkinSigns(request));
		try {
			return RecordCreatedResponse.from(recordRepository.saveAndFlush(record));
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 날짜로 저장한 경우
			throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS);
		}
	}

	public RecordDetailResponse get(Long memberId, Long recordId) {
		return toDetail(findOwned(memberId, recordId));
	}

	/** 기간 내 기록을 최신 날짜부터 조회한다. 기록이 없는 날짜는 포함되지 않는다. */
	public RecordPageResponse list(Long memberId, LocalDate startDate, LocalDate endDate, int page, int size) {
		LocalDate from = startDate == null ? MIN_DATE : startDate;
		LocalDate to = endDate == null ? MAX_DATE : endDate;
		if (from.isAfter(to)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
		}
		Page<RecordSummaryResponse> result = recordRepository
			.findByMemberIdAndRecordDateBetween(memberId, from, to,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "recordDate")))
			.map(r -> RecordSummaryResponse.from(r, photoCount(r)));
		return RecordPageResponse.from(result);
	}

	@Transactional
	public RecordDetailResponse update(Long memberId, Long recordId, RecordRequest request) {
		SkinRecord record = findOwned(memberId, recordId);
		boolean dateChanged = !record.getRecordDate().equals(request.recordDate());
		if (dateChanged && recordRepository.existsByMemberIdAndRecordDate(memberId, request.recordDate())) {
			throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS);
		}
		LifestyleRequest lifestyle = request.lifestyle();
		record.update(
			request.recordDate(),
			request.painIndex(),
			scale(request.sleepHours()),
			request.medication(),
			lifestyle == null ? null : lifestyle.alcohol(),
			lifestyle == null ? null : lifestyle.outdoorActivity(),
			blankToNull(request.notes()));
		record.replaceConditions(toConditions(request), toConditionSigns(request));
		record.replaceSkinSigns(toSkinSigns(request));
		try {
			recordRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.RECORD_ALREADY_EXISTS);
		}
		return toDetail(record);
	}

	@Transactional
	public void delete(Long memberId, Long recordId) {
		SkinRecord record = findOwned(memberId, recordId);
		recordRepository.delete(record);
		eventPublisher.publishEvent(new RecordDeletedEvent(recordId, memberId));
	}

	/** 다른 회원의 기록은 존재 여부가 드러나지 않도록 없는 기록과 같게 404로 처리한다. */
	private SkinRecord findOwned(Long memberId, Long recordId) {
		return recordRepository.findByIdAndMemberId(recordId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
	}

	private Map<BodyPart, Map<Symptom, Integer>> toConditions(RecordRequest request) {
		Map<BodyPart, Map<Symptom, Integer>> conditions = new EnumMap<>(BodyPart.class);
		if (request.skinConditions() == null) {
			return conditions;
		}
		for (SkinConditionRequest condition : request.skinConditions()) {
			conditions.put(condition.bodyPart(), condition.symptoms().toScores());
		}
		return conditions;
	}

	private Map<BodyPart, Set<SkinSign>> toConditionSigns(RecordRequest request) {
		Map<BodyPart, Set<SkinSign>> signs = new EnumMap<>(BodyPart.class);
		if (request.skinConditions() == null) {
			return signs;
		}
		for (SkinConditionRequest condition : request.skinConditions()) {
			Set<SkinSign> partSigns = EnumSet.noneOf(SkinSign.class);
			if (condition.skinSigns() != null) {
				partSigns.addAll(condition.skinSigns());
			}
			signs.put(condition.bodyPart(), partSigns);
		}
		return signs;
	}

	/** 하루의 나타난 증상 = 직접 고른 증상 + 부위마다 고른 증상 */
	private Set<SkinSign> toSkinSigns(RecordRequest request) {
		Set<SkinSign> signs = EnumSet.noneOf(SkinSign.class);
		if (request.skinSigns() != null) {
			signs.addAll(request.skinSigns());
		}
		toConditionSigns(request).values().forEach(signs::addAll);
		return signs;
	}

	private RecordDetailResponse toDetail(SkinRecord record) {
		return RecordDetailResponse.from(record, photosOf(record),
			situationalAnswerReader.forDay(record.getMember().getId(), record.getRecordDate()));
	}

	private List<PhotoSummary> photosOf(SkinRecord record) {
		return photoRepository.findByRecordIdOrderByCreatedAtAsc(record.getId()).stream()
			.map(PhotoSummary::from)
			.toList();
	}

	private int photoCount(SkinRecord record) {
		return photosOf(record).size();
	}

	private BigDecimal scale(BigDecimal value) {
		return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
