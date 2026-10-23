package com.atopy.Atopass.domain.chatbot.repository;

import com.atopy.Atopass.domain.chatbot.entity.SituationalAnswer;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SituationalAnswerRepository extends JpaRepository<SituationalAnswer, Long> {

	List<SituationalAnswer> findByMemberIdAndRecordDate(Long memberId, LocalDate recordDate);

}
