package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.visit.repository.ClinicVisitRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 특이사항 질문 문구와 입력칸 예시(S01)를 고른다. 재진일이 가까우면 의사 선생님께 전할 말, 아니면 불편함 지수가 높으면
 * 가장 불편했던 순간, 그 외에는 남겨둘 일을 묻는다. 저장하는 값(특이사항)은 어느 문구든 같다.
 */
@Component
class NotesPromptSelector {

	static final NotesPrompt CLINIC_VISIT_SOON = new NotesPrompt(
		"오늘 상태를 의사 선생님께 한 문장으로 전한다면?", "밤에 가려워서 3일째 잠을 설치고 있어요");
	static final NotesPrompt HIGH_PAIN = new NotesPrompt(
		"오늘 가장 불편했던 순간을 적어주세요.", "샤워하고 나서 목이 화끈거리고 가려웠어요");
	static final NotesPrompt DEFAULT = new NotesPrompt(
		"오늘 특별히 남겨둘 일이 있으면 적어주세요.", "바디워시를 바꿨어요");

	private final ClinicVisitRepository clinicVisitRepository;
	private final NotesPromptProperties properties;

	NotesPromptSelector(ClinicVisitRepository clinicVisitRepository, NotesPromptProperties properties) {
		this.clinicVisitRepository = clinicVisitRepository;
		this.properties = properties;
	}

	NotesPrompt select(Long memberId, Integer painIndex, LocalDate today) {
		if (clinicVisitRepository.existsByMemberIdAndVisitDateBetween(
			memberId, today, today.plusDays(properties.clinicVisitWithinDays()))) {
			return CLINIC_VISIT_SOON;
		}
		if (painIndex != null && painIndex >= properties.highPainMinIndex()) {
			return HIGH_PAIN;
		}
		return DEFAULT;
	}

	/**
	 * @param question    챗봇이 묻는 말. 뒤에 "없으면 '없어요'를 눌러 주세요." 안내가 붙는다.
	 * @param placeholder 글 입력칸에 흐리게 보여 줄 예시
	 */
	record NotesPrompt(String question, String placeholder) {
	}

}
