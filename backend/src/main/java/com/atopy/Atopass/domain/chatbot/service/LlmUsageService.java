package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.entity.ChatRole;
import com.atopy.Atopass.domain.chatbot.entity.LlmUsageLog;
import com.atopy.Atopass.domain.chatbot.llm.LlmProperties;
import com.atopy.Atopass.domain.chatbot.repository.ChatMessageRepository;
import com.atopy.Atopass.domain.chatbot.repository.LlmUsageLogRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 최근 24시간 동안 쓴 LLM 이용량. 글로 보낸 챗봇 메시지와 챗봇 밖의 LLM 호출(약 봉투 사진 읽기 등)을 합쳐서 센다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LlmUsageService {

	private final ChatMessageRepository messageRepository;
	private final LlmUsageLogRepository usageLogRepository;
	private final LlmProperties properties;
	private final Clock clock;

	/** 한도에 이미 도달했으면 CHAT_LIMIT_EXCEEDED(429)를 던진다. LLM을 부르기 전에 호출한다. */
	public void requireWithinDailyLimit(Long memberId) {
		LocalDateTime from = LocalDateTime.now(clock).minusHours(24);
		long used = messageRepository.countByMemberSince(memberId, ChatRole.USER, from)
			+ usageLogRepository.countByMemberIdAndCreatedAtGreaterThanEqual(memberId, from);
		if (used >= properties.dailyMessageLimit()) {
			throw new BusinessException(ErrorCode.CHAT_LIMIT_EXCEEDED);
		}
	}

	/** LLM 호출 한 번을 이용량에 센다. */
	@Transactional
	public void record(Long memberId, String kind) {
		usageLogRepository.save(LlmUsageLog.of(memberId, kind));
	}

	/** 이용 한도 계산에 쓰지 않는 오래된 기록을 지운다. */
	@Transactional
	public int deleteOlderThan(LocalDateTime limit) {
		return usageLogRepository.deleteOlderThan(limit);
	}

}
