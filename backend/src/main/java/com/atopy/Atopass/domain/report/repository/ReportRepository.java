package com.atopy.Atopass.domain.report.repository;

import com.atopy.Atopass.domain.report.entity.Report;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/** 다른 회원의 보고서는 존재하지 않는 것처럼 조회되지 않는다. */
	Optional<Report> findByIdAndMemberId(Long id, Long memberId);

	List<Report> findByMemberIdOrderByCreatedAtDesc(Long memberId);

	/** 보존 기간이 지났는데 아직 파일이 남아 있는 보고서 */
	List<Report> findByFileNameIsNotNullAndFileExpiresAtBefore(LocalDateTime limit);

}
