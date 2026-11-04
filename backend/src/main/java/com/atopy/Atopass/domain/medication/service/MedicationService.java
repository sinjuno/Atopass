package com.atopy.Atopass.domain.medication.service;

import com.atopy.Atopass.domain.medication.dto.request.MedicationBulkRequest;
import com.atopy.Atopass.domain.medication.dto.request.MedicationRequest;
import com.atopy.Atopass.domain.medication.dto.request.MedicationStopRequest;
import com.atopy.Atopass.domain.medication.dto.request.MedicationUpdateRequest;
import com.atopy.Atopass.domain.medication.dto.response.MedicationHistoryResponse;
import com.atopy.Atopass.domain.medication.dto.response.MedicationResponse;
import com.atopy.Atopass.domain.medication.entity.Medication;
import com.atopy.Atopass.domain.medication.entity.MedicationHistory;
import com.atopy.Atopass.domain.medication.entity.MedicationSource;
import com.atopy.Atopass.domain.medication.entity.MedicationTime;
import com.atopy.Atopass.domain.medication.entity.MedicationType;
import com.atopy.Atopass.domain.medication.repository.MedicationHistoryRepository;
import com.atopy.Atopass.domain.medication.repository.MedicationRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복용 약 등록·조회·수정·중단. 모든 조회는 로그인한 회원 본인의 약만 대상으로 하고,
 * 다른 회원의 약은 없는 약과 같게 404로 처리한다. 약을 지우지 않고 종료일로 중단을 나타낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicationService {

	private static final String STATUS_ACTIVE = "active";
	private static final String STATUS_STOPPED = "stopped";

	private final MedicationRepository medicationRepository;
	private final MedicationHistoryRepository historyRepository;
	private final Clock clock;

	public List<MedicationResponse> list(Long memberId, String status) {
		LocalDate today = LocalDate.now(clock);
		List<Medication> medications = switch (status) {
			case STATUS_ACTIVE -> medicationRepository.findActive(memberId, today);
			case STATUS_STOPPED -> medicationRepository.findStopped(memberId, today);
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "status는 active 또는 stopped여야 합니다.");
		};
		return medications.stream().map(MedicationResponse::from).toList();
	}

	@Transactional
	public MedicationResponse create(Long memberId, MedicationRequest request) {
		MedicationSource source = request.source() == null
			? MedicationSource.MYPAGE : MedicationSource.fromCode(request.source()).orElseThrow();
		return MedicationResponse.from(medicationRepository.save(toEntity(memberId, request, source)));
	}

	/** 한꺼번에 저장한다. 하나라도 값이 잘못되면 예외로 트랜잭션이 되돌아가 아무것도 저장되지 않는다. */
	@Transactional
	public List<MedicationResponse> createAll(Long memberId, MedicationBulkRequest request) {
		MedicationSource source = MedicationSource.fromCode(request.source()).orElseThrow();
		List<Medication> medications = request.items().stream()
			.map(item -> toEntity(memberId, item, source))
			.toList();
		return medicationRepository.saveAll(medications).stream().map(MedicationResponse::from).toList();
	}

	@Transactional
	public MedicationResponse update(Long memberId, Long medicationId, MedicationUpdateRequest request) {
		if (request.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 내용이 없습니다.");
		}
		Medication medication = find(memberId, medicationId);
		List<MedicationHistory> changes = new ArrayList<>();
		LocalDateTime now = now();

		if (request.name() != null) {
			String name = request.name().trim();
			if (name.isEmpty()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "약 이름을 비울 수 없습니다.");
			}
			track(changes, medication, now, "name", medication.getName(), name);
			medication.rename(name);
		}
		if (request.type() != null) {
			MedicationType type = MedicationType.fromCode(request.type()).orElseThrow();
			track(changes, medication, now, "type", medication.getType().code(), type.code());
			medication.changeType(type);
		}
		if (request.dose() != null) {
			String dose = blankToNull(request.dose());
			track(changes, medication, now, "dose", medication.getDose(), dose);
			medication.changeDose(dose);
		}
		if (request.times() != null) {
			String before = medication.getTimes();
			medication.changeTimes(toTimes(request.times()));
			track(changes, medication, now, "times", before, medication.getTimes());
		}
		if (request.timesText() != null) {
			String timesText = blankToNull(request.timesText());
			track(changes, medication, now, "timesText", medication.getTimesText(), timesText);
			medication.changeTimesText(timesText);
		}
		if (request.startDate() != null) {
			requireNotAfter(request.startDate(), medication.getEndDate());
			track(changes, medication, now, "startDate", text(medication.getStartDate()), text(request.startDate()));
			medication.changeStartDate(request.startDate());
		}
		if (request.memo() != null) {
			String memo = blankToNull(request.memo());
			track(changes, medication, now, "memo", medication.getMemo(), memo);
			medication.changeMemo(memo);
		}
		if (request.isOtherCondition() != null) {
			track(changes, medication, now, "isOtherCondition",
				String.valueOf(medication.isOtherCondition()), String.valueOf(request.isOtherCondition()));
			medication.changeOtherCondition(request.isOtherCondition());
		}

		historyRepository.saveAll(changes);
		return MedicationResponse.from(medication);
	}

	/** 복용을 중단한다. 이미 중단한 약이면 종료일을 새 값으로 바꾸고 이력을 남긴다. */
	@Transactional
	public MedicationResponse stop(Long memberId, Long medicationId, MedicationStopRequest request) {
		Medication medication = find(memberId, medicationId);
		requireNotAfter(medication.getStartDate(), request.endDate());

		List<MedicationHistory> changes = new ArrayList<>();
		track(changes, medication, now(), "endDate", text(medication.getEndDate()), text(request.endDate()));
		medication.changeEndDate(request.endDate());
		historyRepository.saveAll(changes);
		return MedicationResponse.from(medication);
	}

	public List<MedicationHistoryResponse> history(Long memberId, Long medicationId) {
		find(memberId, medicationId);
		return historyRepository.findByMedicationIdOrderByChangedAtDescIdDesc(medicationId).stream()
			.map(MedicationHistoryResponse::from)
			.toList();
	}

	private Medication find(Long memberId, Long medicationId) {
		return medicationRepository.findByIdAndMemberId(medicationId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEDICATION_NOT_FOUND));
	}

	private Medication toEntity(Long memberId, MedicationRequest request, MedicationSource source) {
		requireNotAfter(request.startDate(), request.endDate());
		return Medication.builder()
			.memberId(memberId)
			.name(request.name().trim())
			.type(MedicationType.fromCode(request.type()).orElseThrow())
			.dose(blankToNull(request.dose()))
			.times(toTimes(request.times()))
			.timesText(blankToNull(request.timesText()))
			.startDate(request.startDate())
			.endDate(request.endDate())
			.memo(blankToNull(request.memo()))
			.otherCondition(Boolean.TRUE.equals(request.isOtherCondition()))
			.source(source)
			.build();
	}

	/** 시작일이 종료일보다 늦으면 값 오류. 둘 중 하나가 비어 있으면 검사하지 않는다. */
	private static void requireNotAfter(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "종료일은 시작일보다 앞설 수 없습니다.");
		}
	}

	private static List<MedicationTime> toTimes(List<String> codes) {
		if (codes == null) {
			return List.of();
		}
		return codes.stream().map(code -> MedicationTime.fromCode(code).orElseThrow()).toList();
	}

	/** 값이 실제로 바뀌었을 때만 이력 한 줄을 쌓는다. */
	private static void track(
		List<MedicationHistory> changes, Medication medication, LocalDateTime now, String field, String before, String after) {
		if (!Objects.equals(before, after)) {
			changes.add(MedicationHistory.of(medication.getId(), now, field, before, after));
		}
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static String text(LocalDate date) {
		return date == null ? null : date.toString();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
	}

}
