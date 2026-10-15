package com.atopy.Atopass.domain.chatbot.repository;

import com.atopy.Atopass.domain.chatbot.entity.ChatMode;
import com.atopy.Atopass.domain.chatbot.entity.ChatSession;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

	/** 다른 회원의 세션은 존재하지 않는 것처럼 조회되지 않는다. */
	Optional<ChatSession> findByIdAndMemberId(String id, Long memberId);

	/** 같은 날짜·모드에서 아직 열려 있는 세션 */
	Optional<ChatSession> findFirstByMemberIdAndRecordDateAndModeAndClosedFalseOrderByCreatedAtDesc(
		Long memberId, LocalDate recordDate, ChatMode mode);

}
