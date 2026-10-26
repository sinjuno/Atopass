package com.atopy.Atopass.domain.record.repository;

import com.atopy.Atopass.domain.record.entity.SkinRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordRepository extends JpaRepository<SkinRecord, Long> {

	/** 다른 회원의 기록은 존재하지 않는 것처럼 조회되지 않는다. */
	Optional<SkinRecord> findByIdAndMemberId(Long id, Long memberId);

	Optional<SkinRecord> findByMemberIdAndRecordDate(Long memberId, LocalDate recordDate);

	boolean existsByMemberIdAndRecordDate(Long memberId, LocalDate recordDate);

	Page<SkinRecord> findByMemberIdAndRecordDateBetween(
		Long memberId, LocalDate startDate, LocalDate endDate, Pageable pageable);

	/** 기간 내 기록을 날짜 오름차순으로 모두 조회한다. (분석·보고서에서 사용) */
	List<SkinRecord> findByMemberIdAndRecordDateBetweenOrderByRecordDateAsc(
		Long memberId, LocalDate startDate, LocalDate endDate);

	/** date 전(그날은 빼고) 가장 최근 기록. (챗봇 상황 질문에서 며칠 만에 기록하는지 볼 때 사용) */
	Optional<SkinRecord> findFirstByMemberIdAndRecordDateBeforeOrderByRecordDateDesc(Long memberId, LocalDate date);

	long countByMemberIdAndRecordDateBetween(Long memberId, LocalDate startDate, LocalDate endDate);

}
