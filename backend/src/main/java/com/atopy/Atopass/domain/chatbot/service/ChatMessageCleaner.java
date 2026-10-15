package com.atopy.Atopass.domain.chatbot.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 대화 메시지는 30일 동안만 보존한다. 확정된 기록만 영구 보존한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageCleaner {

	static final int RETENTION_DAYS = 30;

	private final ChatSessionService sessionService;
	private final Clock clock;

	/** 매일 새벽 3시 30분(한국 시간)에 실행한다. */
	@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
	public void deleteExpiredMessages() {
		int deleted = sessionService.deleteMessagesOlderThan(LocalDateTime.now(clock).minusDays(RETENTION_DAYS));
		log.info("보존 기간이 지난 챗봇 메시지 {}건을 삭제했습니다.", deleted);
	}

}
