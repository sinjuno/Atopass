package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.entity.SituationalAnswer;
import com.atopy.Atopass.domain.chatbot.repository.SituationalAnswerRepository;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.repository.RecordRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 상황 질문 조건 판정에 쓰는 지난 기록(오늘은 빼고 최근 며칠)을 읽는다. */
@Component
@RequiredArgsConstructor
class SituationalHistoryLoader {

	private final RecordRepository recordRepository;
	private final SituationalAnswerRepository answerRepository;
	private final JsonMapper jsonMapper;

	/** today 전날부터 days일 전까지의 기록. 날짜 오름차순이고, 기록하지 않은 날은 없다. */
	@Transactional(readOnly = true)
	List<SituationalDay> recentDays(Long memberId, LocalDate today, int days) {
		return recordRepository.findByMemberIdAndRecordDateBetweenOrderByRecordDateAsc(
				memberId, today.minusDays(Math.max(days, 1)), today.minusDays(1))
			.stream()
			.map(SituationalDay::from)
			.toList();
	}

	/**
	 * from~to(양 끝 포함) 사이에 재진 날 "처방이나 연고가 바뀌었다"(P08)고 답한 가장 최근 날짜. 없으면 null이다.
	 */
	@Transactional(readOnly = true)
	LocalDate prescriptionChangedOn(Long memberId, LocalDate from, LocalDate to) {
		return answerRepository.findByMemberIdAndQuestionIdAndRecordDateBetween(memberId, "P08", from, to).stream()
			.filter(answer -> "CHANGED".equals(answerCode(answer)))
			.map(SituationalAnswer::getRecordDate)
			.max(Comparator.naturalOrder())
			.orElse(null);
	}

	private String answerCode(SituationalAnswer answer) {
		try {
			return jsonMapper.readTree(answer.getAnswerJson()).path("answer").asString(null);
		} catch (RuntimeException e) {
			// 형식이 깨진 답은 처방이 바뀐 것으로 보지 않는다.
			return null;
		}
	}

	/** today 전 가장 최근 기록의 날짜. 기간과 상관없이 찾고, 처음 기록하는 날이면 null이다. */
	@Transactional(readOnly = true)
	LocalDate previousRecordDate(Long memberId, LocalDate today) {
		return recordRepository.findFirstByMemberIdAndRecordDateBeforeOrderByRecordDateDesc(memberId, today)
			.map(SkinRecord::getRecordDate)
			.orElse(null);
	}

}
