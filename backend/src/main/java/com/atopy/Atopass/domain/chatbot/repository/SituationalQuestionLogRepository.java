package com.atopy.Atopass.domain.chatbot.repository;

import com.atopy.Atopass.domain.chatbot.entity.SituationalQuestionLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SituationalQuestionLogRepository extends JpaRepository<SituationalQuestionLog, Long> {

	List<SituationalQuestionLog> findByMemberId(Long memberId);

	Optional<SituationalQuestionLog> findByMemberIdAndQuestionId(Long memberId, String questionId);

}
