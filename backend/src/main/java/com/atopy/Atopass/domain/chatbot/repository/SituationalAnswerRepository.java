package com.atopy.Atopass.domain.chatbot.repository;

import com.atopy.Atopass.domain.chatbot.entity.SituationalAnswer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SituationalAnswerRepository extends JpaRepository<SituationalAnswer, Long> {

	List<SituationalAnswer> findByMemberIdAndRecordDate(Long memberId, LocalDate recordDate);

	/** 기간 안의 답. (재진 PDF에서 사용) */
	List<SituationalAnswer> findByMemberIdAndRecordDateBetween(Long memberId, LocalDate startDate, LocalDate endDate);

	Optional<SituationalAnswer> findByMemberIdAndRecordDateAndQuestionId(
		Long memberId, LocalDate recordDate, String questionId);

}
