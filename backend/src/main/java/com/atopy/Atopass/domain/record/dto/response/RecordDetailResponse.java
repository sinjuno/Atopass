package com.atopy.Atopass.domain.record.dto.response;

import com.atopy.Atopass.domain.chatbot.dto.response.SituationalAnswerItem;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record RecordDetailResponse(
	Long recordId,
	LocalDate recordDate,
	List<SkinConditionResponse> skinConditions,
	Integer painIndex,
	BigDecimal sleepHours,
	/** 수면 점수 1~5 (7~8시간이 5점, 2시간 벗어날 때마다 1점 감점) */
	Integer sleepScore,
	Boolean medication,
	List<SkinSign> skinSigns,
	LifestyleResponse lifestyle,
	String notes,
	List<PhotoSummary> photos,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	/** 그날 챗봇 상황 질문·마무리 질문 답. 메인 기록과 따로 저장되며, 없으면 빈 목록 */
	List<SituationalAnswerItem> situationalAnswers
) {

	public static RecordDetailResponse from(SkinRecord record, List<PhotoSummary> photos,
		List<SituationalAnswerItem> situationalAnswers) {
		return new RecordDetailResponse(
			record.getId(),
			record.getRecordDate(),
			record.getConditions().stream()
				.sorted(Comparator.comparing(c -> c.getBodyPart().ordinal()))
				.map(SkinConditionResponse::from)
				.toList(),
			record.getPainIndex(),
			record.getSleepHours(),
			record.getSleepScore(),
			record.getMedication(),
			record.getSkinSigns().stream().sorted().toList(),
			LifestyleResponse.from(record),
			record.getNotes(),
			photos,
			record.getCreatedAt(),
			record.getUpdatedAt(),
			situationalAnswers);
	}

}
