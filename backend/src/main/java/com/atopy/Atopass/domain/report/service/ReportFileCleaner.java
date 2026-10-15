package com.atopy.Atopass.domain.report.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 생성 30일이 지난 PDF 파일을 지운다. 보고서 정보는 남고, 다시 내려받으면 새로 만든다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportFileCleaner {

	private final ReportService reportService;

	/** 매일 새벽 4시(한국 시간)에 실행한다. */
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void deleteExpiredFiles() {
		int deleted = reportService.deleteExpiredFiles();
		log.info("보존 기간이 지난 보고서 PDF {}개를 삭제했습니다.", deleted);
	}

}
