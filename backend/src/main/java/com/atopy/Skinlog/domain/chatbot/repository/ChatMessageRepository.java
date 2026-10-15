package com.atopy.Skinlog.domain.chatbot.repository;

import com.atopy.Skinlog.domain.chatbot.entity.ChatMessage;
import com.atopy.Skinlog.domain.chatbot.entity.ChatRole;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	List<ChatMessage> findBySessionIdOrderByIdAsc(String sessionId);

	/** LLM에 넘길 최근 대화. 최신순으로 받아오므로 호출한 쪽에서 뒤집어 쓴다. */
	List<ChatMessage> findTop20BySessionIdOrderByIdDesc(String sessionId);

	/** 회원이 보낸(USER) 메시지 수. 성공한 LLM 호출만 저장되므로 호출 횟수로 본다. */
	@Query("select count(m) from ChatMessage m where m.session.member.id = :memberId and m.role = :role and m.createdAt >= :from")
	long countByMemberSince(@Param("memberId") Long memberId, @Param("role") ChatRole role, @Param("from") LocalDateTime from);

	@Modifying
	@Query("delete from ChatMessage m where m.createdAt < :limit")
	int deleteOlderThan(@Param("limit") LocalDateTime limit);

}
