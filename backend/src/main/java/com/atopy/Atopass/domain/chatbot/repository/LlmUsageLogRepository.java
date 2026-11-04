package com.atopy.Atopass.domain.chatbot.repository;

import com.atopy.Atopass.domain.chatbot.entity.LlmUsageLog;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLog, Long> {

	long countByMemberIdAndCreatedAtGreaterThanEqual(Long memberId, LocalDateTime from);

	@Modifying
	@Query("delete from LlmUsageLog l where l.createdAt < :limit")
	int deleteOlderThan(@Param("limit") LocalDateTime limit);

}
