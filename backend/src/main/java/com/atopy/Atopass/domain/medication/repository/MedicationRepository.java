package com.atopy.Atopass.domain.medication.repository;

import com.atopy.Atopass.domain.medication.entity.Medication;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

	/** 다른 회원의 약은 존재하지 않는 것처럼 조회되지 않는다. */
	Optional<Medication> findByIdAndMemberId(Long id, Long memberId);

	/** 복용 중: 종료일이 없거나 오늘보다 뒤인 약 */
	@Query("""
		select m from Medication m
		where m.memberId = :memberId and (m.endDate is null or m.endDate > :today)
		order by m.startDate desc, m.id desc""")
	List<Medication> findActive(@Param("memberId") Long memberId, @Param("today") LocalDate today);

	/** 중단: 종료일이 오늘이거나 그 이전인 약 */
	@Query("""
		select m from Medication m
		where m.memberId = :memberId and m.endDate is not null and m.endDate <= :today
		order by m.startDate desc, m.id desc""")
	List<Medication> findStopped(@Param("memberId") Long memberId, @Param("today") LocalDate today);

	/** 기간(periodStart~periodEnd)과 겹치는 약. 시작일이 기간 끝 이전이고, 종료일이 없거나 기간 시작 이후인 약 */
	@Query("""
		select m from Medication m
		where m.memberId = :memberId and m.startDate <= :periodEnd
		and (m.endDate is null or m.endDate >= :periodStart)
		order by m.startDate asc, m.id asc""")
	List<Medication> findOverlapping(@Param("memberId") Long memberId, @Param("periodStart") LocalDate periodStart,
		@Param("periodEnd") LocalDate periodEnd);

}
