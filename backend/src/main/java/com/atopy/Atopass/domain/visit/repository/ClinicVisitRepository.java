package com.atopy.Atopass.domain.visit.repository;

import com.atopy.Atopass.domain.visit.entity.ClinicVisit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicVisitRepository extends JpaRepository<ClinicVisit, Long> {

	List<ClinicVisit> findByMemberIdOrderByVisitDateAsc(Long memberId);

	boolean existsByMemberIdAndVisitDate(Long memberId, LocalDate visitDate);

	/** 기간 안(양 끝 포함)에 재진 날짜가 있는지. (챗봇 특이사항 질문에서 재진일이 가까운지 볼 때 사용) */
	boolean existsByMemberIdAndVisitDateBetween(Long memberId, LocalDate startDate, LocalDate endDate);

	/** 다른 회원의 재진 날짜는 존재하지 않는 것처럼 조회되지 않는다. */
	Optional<ClinicVisit> findByIdAndMemberId(Long id, Long memberId);

}
